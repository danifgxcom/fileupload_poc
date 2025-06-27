#!/bin/bash

# Demo rápida del proyecto File Upload POC

echo "🎬 DEMO - File Upload Performance Comparison"
echo "==========================================="
echo ""

# Verificar que la aplicación esté ejecutándose
if ! curl -s http://localhost:8080/q/health > /dev/null 2>&1; then
    echo "❌ La aplicación no está ejecutándose."
    echo "▶️  Para iniciar: ./gradlew quarkusDev"
    echo "⏸️  Presiona Enter cuando esté lista..."
    read
fi

echo "✅ Aplicación detectada en http://localhost:8080"
echo ""

# Verificar archivos de test
echo "🗃️ Verificando archivos de test..."
if [ ! -f "test-data/file_1MB.dat" ]; then
    echo "📁 Creando archivo de test 1MB..."
    mkdir -p test-data
    dd if=/dev/zero of=test-data/file_1MB.dat bs=1M count=1 2>/dev/null
fi
echo "✅ Archivo de test disponible: test-data/file_1MB.dat"
echo ""

# Demo de los 4 endpoints
echo "🚀 DEMO DE LAS 4 ESTRATEGIAS DE UPLOAD"
echo "======================================"
echo ""

echo "1️⃣ Upload Estándar (Multipart JAX-RS)..."
result1=$(curl -s -X POST -F "file=@test-data/file_1MB.dat" http://localhost:8080/upload/standard)
throughput1=$(echo "$result1" | grep -o '"throughputMBps":"[^"]*"' | cut -d'"' -f4)
echo "   Resultado: $throughput1 MB/s"
echo ""

echo "2️⃣ Upload Reactivo (RESTEasy Reactive)..."
result2=$(curl -s -X POST -F "file=@test-data/file_1MB.dat" http://localhost:8080/upload-reactive/standard)
throughput2=$(echo "$result2" | grep -o '"throughputMBps":"[^"]*"' | cut -d'"' -f4)
echo "   Resultado: $throughput2 MB/s"
echo ""

echo "3️⃣ Upload Chunked (Protocolo Binario)..."
result3=$(curl -s -X POST -H "Content-Type: application/octet-stream" --data-binary @test-data/file_1MB.dat http://localhost:8080/upload/chunked)
throughput3=$(echo "$result3" | grep -o '"throughputMBps":"[^"]*"' | cut -d'"' -f4)
echo "   Resultado: $throughput3 MB/s"
echo ""

echo "4️⃣ HTTP/2 Streaming (Frames nativos)..."
result4=$(curl -s --http2-prior-knowledge -X POST -H "Content-Type: application/octet-stream" -T test-data/file_1MB.dat http://localhost:8080/upload-http2/streaming)
throughput4=$(echo "$result4" | grep -o '"throughputMBps":"[^"]*"' | cut -d'"' -f4)
frames=$(echo "$result4" | grep -o '"framesProcessed":[0-9]*' | cut -d':' -f2)
echo "   Resultado: $throughput4 MB/s ($frames frames HTTP/2)"
echo ""

# Resumen
echo "📊 RESUMEN DE LA DEMO"
echo "===================="
echo "📁 Archivo probado: 1MB"
echo "⚡ Upload Estándar:   $throughput1 MB/s"
echo "⚡ Upload Reactivo:   $throughput2 MB/s"  
echo "⚡ Upload Chunked:    $throughput3 MB/s"
echo "⚡ HTTP/2 Streaming:  $throughput4 MB/s"
echo ""

# Determinar el ganador
echo "🏆 ANÁLISIS:"
declare -A results
results["Estándar"]=$throughput1
results["Reactivo"]=$throughput2
results["Chunked"]=$throughput3
results["HTTP/2"]=$throughput4

max_throughput=0
winner=""
for method in "${!results[@]}"; do
    value=${results[$method]}
    # Convertir coma a punto para comparación numérica
    value_clean=$(echo $value | sed 's/,/./g')
    if (( $(echo "$value_clean > $max_throughput" | bc -l) )); then
        max_throughput=$value_clean
        winner=$method
    fi
done

echo "🥇 Ganador para 1MB: $winner ($max_throughput MB/s)"
echo ""

# HTTP/2 demo específico
echo "🌐 DEMO ESPECÍFICO HTTP/2"
echo "========================"
echo ""

echo "🔸 Probando HTTP/2 con upgrade (puede fallar)..."
response_upgrade=$(curl -s --http2 -X POST -H "Content-Type: application/octet-stream" --data-binary @test-data/file_1MB.dat http://localhost:8080/upload/chunked -w "%{http_code}")
if [[ "$response_upgrade" == *"200"* ]]; then
    echo "   ✅ HTTP/2 upgrade funcionó para 1MB"
else
    echo "   ⚠️  HTTP/2 upgrade puede tener problemas con archivos grandes"
fi

echo ""
echo "🔸 Probando HTTP/2 con prior knowledge..."
response_pk=$(curl -s --http2-prior-knowledge -X POST -H "Content-Type: application/octet-stream" --data-binary @test-data/file_1MB.dat http://localhost:8080/upload/chunked -w "%{http_code}")
if [[ "$response_pk" == *"200"* ]]; then
    echo "   ✅ HTTP/2 prior knowledge funciona perfectamente"
else
    echo "   ❌ HTTP/2 prior knowledge falló"
fi

echo ""
echo "📋 PRÓXIMOS PASOS SUGERIDOS:"
echo "=========================="
echo ""
echo "🧪 Para tests completos:"
echo "   ./scripts/test_uploads.sh"
echo ""
echo "📊 Para análisis detallado:"
echo "   python3 scripts/analyze_simple.py"
echo ""  
echo "🌐 Para comparación HTTP/1.1 vs HTTP/2:"
echo "   ./scripts/test_http2_complete.sh"
echo ""
echo "📚 Para documentación completa:"
echo "   cat README.md"
echo "   cat docs/README.md"
echo ""
echo "🎉 DEMO COMPLETADA - ¡Proyecto listo para evaluación!"