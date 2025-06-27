#!/bin/bash

# Script para probar los diferentes métodos de upload y recopilar métricas

BASE_URL="http://localhost:8080"
TEST_DIR="test-files"
RESULTS_DIR="test-results"

# Crear directorios
mkdir -p $TEST_DIR
mkdir -p $RESULTS_DIR

echo "=== File Upload Performance Test ==="
echo "Fecha: $(date)"
echo "URL Base: $BASE_URL"
echo ""

# Función para crear archivos de prueba
create_test_files() {
    echo "Creando archivos de prueba..."
    
    # 1MB file
    if [ ! -f "$TEST_DIR/file_1MB.dat" ]; then
        echo "  Creando archivo 1MB..."
        dd if=/dev/zero of=$TEST_DIR/file_1MB.dat bs=1M count=1 2>/dev/null
    fi
    
    # 10MB file  
    if [ ! -f "$TEST_DIR/file_10MB.dat" ]; then
        echo "  Creando archivo 10MB..."
        dd if=/dev/zero of=$TEST_DIR/file_10MB.dat bs=1M count=10 2>/dev/null
    fi
    
    # 100MB file
    if [ ! -f "$TEST_DIR/file_100MB.dat" ]; then
        echo "  Creando archivo 100MB..."
        dd if=/dev/zero of=$TEST_DIR/file_100MB.dat bs=1M count=100 2>/dev/null
    fi
    
    # 1GB file
    if [ ! -f "$TEST_DIR/file_1GB.dat" ]; then
        echo "  Creando archivo 1GB... (esto puede tardar un momento)"
        dd if=/dev/zero of=$TEST_DIR/file_1GB.dat bs=1M count=1024 2>/dev/null
    fi
    
    # 2GB file (opcional, solo si hay espacio)
    if [ ! -f "$TEST_DIR/file_2GB.dat" ] && [ $(df . | tail -1 | awk '{print $4}') -gt 2500000 ]; then
        echo "  Creando archivo 2GB... (esto tardará varios minutos)"
        dd if=/dev/zero of=$TEST_DIR/file_2GB.dat bs=1M count=2048 2>/dev/null
    fi
    
    echo "Archivos de prueba creados:"
    ls -lh $TEST_DIR/
    echo ""
}

# Función para obtener métricas del sistema
get_system_metrics() {
    local phase=$1
    echo "=== Métricas del Sistema - $phase ==="
    
    # Memoria
    local memory_info=$(free -m | grep "Mem:")
    local memory_used=$(echo $memory_info | awk '{print $3}')
    local memory_total=$(echo $memory_info | awk '{print $2}')
    local memory_percent=$(echo "scale=2; $memory_used * 100 / $memory_total" | bc)
    echo "Memoria: ${memory_used}MB usado de ${memory_total}MB (${memory_percent}%)"
    
    # CPU
    local cpu_usage=$(top -bn1 | grep "Cpu(s)" | sed "s/.*, *\([0-9.]*\)%* id.*/\1/" | awk '{print 100 - $1}')
    echo "CPU: ${cpu_usage}% en uso"
    
    # Proceso Java/Quarkus
    local java_mem=$(ps aux | grep java | grep quarkus | awk '{print $6}' | head -1)
    if [ ! -z "$java_mem" ]; then
        local java_mem_mb=$(echo "scale=2; $java_mem / 1024" | bc)
        echo "Proceso Quarkus: ${java_mem_mb}MB"
    fi
    
    echo ""
}

# Función para probar endpoints HTTP/2 con métricas detalladas
test_endpoint_http2() {
    local endpoint=$1
    local file=$2
    local method=$3
    local description=$4
    
    echo "=== Probando: $description ==="
    echo "Endpoint: $endpoint"
    echo "Archivo: $file ($(du -h $file | cut -f1))"
    echo "Método: $method (HTTP/2 Prior Knowledge)"
    
    # Métricas antes
    get_system_metrics "ANTES"
    
    local start_time=$(date +%s.%N)
    
    # Upload con HTTP/2 prior knowledge para evitar error 101
    local response=$(curl -s -w "\n%{http_code}\n%{time_total}\n%{speed_upload}" \
        --http2-prior-knowledge \
        -X POST \
        -H "Content-Type: application/octet-stream" \
        -T "$file" \
        "$BASE_URL$endpoint" 2>/dev/null)
    
    local end_time=$(date +%s.%N)
    local total_time=$(echo "$end_time - $start_time" | bc)
    
    # Parseear respuesta de curl
    local response_body=$(echo "$response" | head -n -2)
    local http_code=$(echo "$response" | tail -n 2 | head -n 1)
    local curl_time=$(echo "$response" | tail -n 2 | tail -n 1 | head -n 1)
    local upload_speed=$(echo "$response" | tail -n 1)
    
    # Métricas después
    get_system_metrics "DESPUÉS"
    
    echo "Respuesta del servidor: $response_body"
    echo "Código HTTP: $http_code"
    echo "Tiempo total (bash): ${total_time}s"
    echo "Tiempo total (curl): ${curl_time}s"
    echo "Velocidad de upload: $(echo "scale=2; $upload_speed / 1024 / 1024" | bc) MB/s"
    
    # Calcular throughput
    local file_size_bytes=$(stat -c%s "$file")
    local file_size_mb=$(echo "scale=2; $file_size_bytes / 1024 / 1024" | bc)
    local throughput_mbps=$(echo "scale=2; $file_size_mb / $total_time" | bc)
    
    echo "Archivo: ${file_size_mb}MB"
    echo "Throughput calculado: ${throughput_mbps} MB/s"
    
    # Extraer información específica de HTTP/2 del response del servidor
    local frames_processed=""
    local avg_frame_size=""
    if [[ "$response_body" == *"framesProcessed"* ]]; then
        frames_processed=$(echo "$response_body" | grep -o '"framesProcessed":[0-9]*' | cut -d':' -f2)
        avg_frame_size=$(echo "$response_body" | grep -o '"avgFrameSize":[0-9]*' | cut -d':' -f2)
        echo "Frames HTTP/2 procesados: $frames_processed"
        echo "Tamaño promedio de frame: $avg_frame_size bytes"
    fi
    echo ""
    
    # Guardar resultados detallados con información HTTP/2
    echo "$description,$endpoint,$file,$file_size_mb,$total_time,$curl_time,$throughput_mbps,$upload_speed,$http_code,$(date),$frames_processed,$avg_frame_size" >> $RESULTS_DIR/detailed_results.csv
}

# Función para probar un endpoint con métricas detalladas
test_endpoint() {
    local endpoint=$1
    local file=$2
    local method=$3
    local description=$4
    
    echo "=== Probando: $description ==="
    echo "Endpoint: $endpoint"
    echo "Archivo: $file ($(du -h $file | cut -f1))"
    echo "Método: $method"
    
    # Métricas antes
    get_system_metrics "ANTES"
    
    local start_time=$(date +%s.%N)
    
    if [ "$method" == "multipart" ]; then
        # Upload con multipart form data
        local response=$(curl -s -w "\n%{http_code}\n%{time_total}\n%{speed_upload}" \
            -X POST \
            -F "file=@$file" \
            "$BASE_URL$endpoint" 2>/dev/null)
    else
        # Upload directo con binary data
        local response=$(curl -s -w "\n%{http_code}\n%{time_total}\n%{speed_upload}" \
            -X POST \
            -H "Content-Type: application/octet-stream" \
            --data-binary "@$file" \
            "$BASE_URL$endpoint" 2>/dev/null)
    fi
    
    local end_time=$(date +%s.%N)
    local total_time=$(echo "$end_time - $start_time" | bc)
    
    # Parseear respuesta de curl
    local response_body=$(echo "$response" | head -n -2)
    local http_code=$(echo "$response" | tail -n 2 | head -n 1)
    local curl_time=$(echo "$response" | tail -n 2 | tail -n 1 | head -n 1)
    local upload_speed=$(echo "$response" | tail -n 1)
    
    # Métricas después
    get_system_metrics "DESPUÉS"
    
    echo "Respuesta del servidor: $response_body"
    echo "Código HTTP: $http_code"
    echo "Tiempo total (bash): ${total_time}s"
    echo "Tiempo total (curl): ${curl_time}s"
    echo "Velocidad de upload: $(echo "scale=2; $upload_speed / 1024 / 1024" | bc) MB/s"
    
    # Calcular throughput
    local file_size_bytes=$(stat -c%s "$file")
    local file_size_mb=$(echo "scale=2; $file_size_bytes / 1024 / 1024" | bc)
    local throughput_mbps=$(echo "scale=2; $file_size_mb / $total_time" | bc)
    
    echo "Archivo: ${file_size_mb}MB"
    echo "Throughput calculado: ${throughput_mbps} MB/s"
    echo ""
    
    # Guardar resultados detallados (sin columnas HTTP/2)
    echo "$description,$endpoint,$file,$file_size_mb,$total_time,$curl_time,$throughput_mbps,$upload_speed,$http_code,$(date),," >> $RESULTS_DIR/detailed_results.csv
}

# Función para mostrar métricas de la aplicación
show_metrics() {
    echo "=== Métricas de la aplicación ==="
    curl -s $BASE_URL/q/metrics 2>/dev/null || echo "Métricas no disponibles"
    echo ""
    
    echo "=== Estado del health check ==="
    curl -s $BASE_URL/q/health 2>/dev/null || echo "Health check no disponible"
    echo ""
    
    echo "=== Información de uploads ==="
    curl -s $BASE_URL/upload/info 2>/dev/null || echo "Info de uploads no disponible"
    echo ""
}

# Función principal
main() {
    # Verificar que la aplicación esté corriendo
    if ! curl -s $BASE_URL/q/health > /dev/null 2>&1; then
        echo "⚠️  La aplicación no está disponible en $BASE_URL"
        echo "Asegúrate de que Quarkus esté ejecutándose con: ./gradlew quarkusDev"
        exit 1
    fi
    
    create_test_files
    
    # Inicializar archivos de resultados
    echo "Test,Endpoint,File,Time_Seconds,Timestamp" > $RESULTS_DIR/results.csv
    echo "Test,Endpoint,File,Size_MB,Time_Bash,Time_Curl,Throughput_MBps,Upload_Speed_Bytes,HTTP_Code,Timestamp,Frames_Processed,Avg_Frame_Size" > $RESULTS_DIR/detailed_results.csv
    
    # Archivos a probar
    local files=("file_1MB.dat" "file_10MB.dat" "file_100MB.dat" "file_1GB.dat")
    
    # Añadir 2GB si existe
    if [ -f "$TEST_DIR/file_2GB.dat" ]; then
        files+=("file_2GB.dat")
    fi
    
    for file in "${files[@]}"; do
        local filepath="$TEST_DIR/$file"
        
        # Probar upload estándar
        test_endpoint "/upload/standard" "$filepath" "multipart" "Upload Estándar - $file"
        
        # Probar upload reactivo
        test_endpoint "/upload-reactive/standard" "$filepath" "multipart" "Upload Reactivo - $file"
        
        # Probar upload chunked
        test_endpoint "/upload/chunked" "$filepath" "binary" "Upload Chunked - $file"
        
        # Probar HTTP/2 streaming (usando prior knowledge para evitar error 101)
        test_endpoint_http2 "/upload-http2/streaming" "$filepath" "http2-streaming" "HTTP/2 Streaming - $file"
        
        echo "----------------------------------------"
    done
    
    show_metrics
    
    echo "=== Resumen de resultados ==="
    echo "Resultados básicos: $RESULTS_DIR/results.csv"
    echo "Resultados detallados: $RESULTS_DIR/detailed_results.csv"
    echo ""
    echo "=== Resultados básicos ==="
    cat $RESULTS_DIR/results.csv
    echo ""
    echo "=== Análisis de throughput ==="
    echo "Mejores throughputs por método:"
    echo "HTTP/2 Streaming:"
    grep "HTTP/2 Streaming" $RESULTS_DIR/detailed_results.csv | sort -t',' -k7 -nr | head -3
    echo "Upload Chunked:"
    grep "Upload Chunked" $RESULTS_DIR/detailed_results.csv | sort -t',' -k7 -nr | head -3
    echo "Upload Reactivo:"
    grep "Upload Reactivo" $RESULTS_DIR/detailed_results.csv | sort -t',' -k7 -nr | head -3
    echo "Upload Estándar:"
    grep "Upload Estándar" $RESULTS_DIR/detailed_results.csv | sort -t',' -k7 -nr | head -3
    echo ""
    echo "=== Comparación HTTP/2 vs Chunked (archivos grandes) ==="
    echo "Chunked vs HTTP/2 para archivos de 1GB+:"
    grep -E "(Upload Chunked|HTTP/2 Streaming)" $RESULTS_DIR/detailed_results.csv | grep -E "(1GB|2GB)" | sort -t',' -k7 -nr
}

# Ejecutar script
main "$@"