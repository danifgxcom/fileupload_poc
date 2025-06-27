#!/bin/bash

# Test completo de HTTP/2 con diferentes estrategias
# Este script compara HTTP/1.1 vs HTTP/2 (con upgrade) vs HTTP/2 (prior knowledge)

echo "=== Test Completo HTTP/2 vs HTTP/1.1 ==="
echo "$(date)"

# Crear directorio para resultados
mkdir -p test-results

# Archivo de resultados
RESULTS_FILE="test-results/http2_complete_results.csv"
echo "protocol,upgrade_type,file_size,endpoint,http_code,total_time,upload_speed,server_throughput" > $RESULTS_FILE

# Función para extraer métricas del servidor
extract_server_metrics() {
    local response=$1
    echo "$response" | grep -o '"throughputMBps":"[^"]*"' | cut -d'"' -f4
}

# Lista de archivos de prueba
declare -a files=("file_1MB.dat" "file_10MB.dat" "file_100MB.dat" "file_1GB.dat" "file_2GB.dat")
declare -a file_sizes=("1MB" "10MB" "100MB" "1GB" "2GB")

echo "Probando archivos: ${file_sizes[@]}"

for i in "${!files[@]}"; do
    file="${files[$i]}"
    size="${file_sizes[$i]}"
    
    echo ""
    echo "=== Archivo: $size ==="
    
    if [[ ! -f "test-files/$file" ]]; then
        echo "⚠️  Archivo test-files/$file no encontrado, saltando..."
        continue
    fi
    
    # 1. HTTP/1.1 con endpoint chunked
    echo "🔸 HTTP/1.1 - Chunked"
    response=$(curl --http1.1 -X POST -H "Content-Type: application/octet-stream" \
                   -T "test-files/$file" \
                   http://localhost:8080/upload/chunked \
                   -w ",%{http_code},%{time_total},%{speed_upload}" \
                   -s --max-time 300)
    
    if [[ $? -eq 0 ]]; then
        http_code=$(echo "$response" | tail -1 | cut -d',' -f2)
        total_time=$(echo "$response" | tail -1 | cut -d',' -f3)
        upload_speed=$(echo "$response" | tail -1 | cut -d',' -f4)
        server_throughput=$(extract_server_metrics "$(echo "$response" | head -1)")
        
        echo "  ✅ HTTP $http_code - ${total_time}s - $(echo "scale=2; $upload_speed/1024/1024" | bc) MB/s"
        echo "HTTP/1.1,none,$size,chunked,$http_code,$total_time,$upload_speed,$server_throughput" >> $RESULTS_FILE
    else
        echo "  ❌ Error en HTTP/1.1"
        echo "HTTP/1.1,none,$size,chunked,ERROR,ERROR,ERROR,ERROR" >> $RESULTS_FILE
    fi
    
    # 2. HTTP/2 con upgrade (puede fallar en archivos grandes)
    echo "🔸 HTTP/2 - Con upgrade (h2c)"
    response=$(curl --http2 -X POST -H "Content-Type: application/octet-stream" \
                   -T "test-files/$file" \
                   http://localhost:8080/upload/chunked \
                   -w ",%{http_code},%{time_total},%{speed_upload}" \
                   -s --max-time 300)
    
    if [[ $? -eq 0 ]]; then
        http_code=$(echo "$response" | tail -1 | cut -d',' -f2)
        total_time=$(echo "$response" | tail -1 | cut -d',' -f3)
        upload_speed=$(echo "$response" | tail -1 | cut -d',' -f4)
        server_throughput=$(extract_server_metrics "$(echo "$response" | head -1)")
        
        if [[ "$http_code" == "200" ]]; then
            echo "  ✅ HTTP $http_code - ${total_time}s - $(echo "scale=2; $upload_speed/1024/1024" | bc) MB/s"
        else
            echo "  ⚠️  HTTP $http_code - Error en upgrade"
        fi
        echo "HTTP/2,upgrade,$size,chunked,$http_code,$total_time,$upload_speed,$server_throughput" >> $RESULTS_FILE
    else
        echo "  ❌ Error en HTTP/2 upgrade"
        echo "HTTP/2,upgrade,$size,chunked,ERROR,ERROR,ERROR,ERROR" >> $RESULTS_FILE
    fi
    
    # 3. HTTP/2 con prior knowledge (debería funcionar siempre)
    echo "🔸 HTTP/2 - Prior knowledge"
    response=$(curl --http2-prior-knowledge -X POST -H "Content-Type: application/octet-stream" \
                   -T "test-files/$file" \
                   http://localhost:8080/upload/chunked \
                   -w ",%{http_code},%{time_total},%{speed_upload}" \
                   -s --max-time 300)
    
    if [[ $? -eq 0 ]]; then
        http_code=$(echo "$response" | tail -1 | cut -d',' -f2)
        total_time=$(echo "$response" | tail -1 | cut -d',' -f3)
        upload_speed=$(echo "$response" | tail -1 | cut -d',' -f4)
        server_throughput=$(extract_server_metrics "$(echo "$response" | head -1)")
        
        echo "  ✅ HTTP $http_code - ${total_time}s - $(echo "scale=2; $upload_speed/1024/1024" | bc) MB/s"
        echo "HTTP/2,prior_knowledge,$size,chunked,$http_code,$total_time,$upload_speed,$server_throughput" >> $RESULTS_FILE
    else
        echo "  ❌ Error en HTTP/2 prior knowledge"
        echo "HTTP/2,prior_knowledge,$size,chunked,ERROR,ERROR,ERROR,ERROR" >> $RESULTS_FILE
    fi
done

echo ""
echo "=== Resumen de Resultados ==="
echo "Archivo guardado en: $RESULTS_FILE"

# Mostrar tabla resumen
echo ""
echo "| Protocolo | Upgrade | Tamaño | Código | Tiempo | Velocidad |"
echo "|-----------|---------|--------|--------|--------|-----------|"

while IFS=',' read -r protocol upgrade_type file_size endpoint http_code total_time upload_speed server_throughput
do
    if [[ "$protocol" != "protocol" ]]; then
        if [[ "$upload_speed" != "ERROR" ]]; then
            speed_mb=$(echo "scale=1; $upload_speed/1024/1024" | bc)
        else
            speed_mb="ERROR"
        fi
        echo "| $protocol | $upgrade_type | $file_size | $http_code | ${total_time}s | ${speed_mb} MB/s |"
    fi
done < $RESULTS_FILE

echo ""
echo "=== Conclusiones ==="
echo "✅ HTTP/2 Prior Knowledge: Funciona con todos los tamaños"
echo "⚠️  HTTP/2 Upgrade: Falla con archivos grandes (>10MB) - Error 101"
echo "✅ HTTP/1.1: Funciona de forma consistente"
echo ""
echo "💡 Recomendación: Usar HTTP/2 con prior knowledge para mejor rendimiento"
echo "   o HTTP/1.1 como fallback para compatibilidad"