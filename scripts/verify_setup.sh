#!/bin/bash

# Script de verificación final del proyecto

echo "🔍 Verificando configuración del proyecto File Upload POC..."
echo ""

# Verificar estructura de directorios
echo "📁 Verificando estructura de directorios..."
dirs=("scripts" "docs" "test-data" "results" "src")
for dir in "${dirs[@]}"; do
    if [ -d "$dir" ]; then
        echo "  ✅ $dir/"
    else
        echo "  ❌ $dir/ - FALTA"
    fi
done

echo ""

# Verificar scripts principales
echo "📜 Verificando scripts principales..."
scripts=("scripts/test_uploads.sh" "scripts/analyze_simple.py" "scripts/test_http2_complete.sh")
for script in "${scripts[@]}"; do
    if [ -f "$script" ] && [ -x "$script" ]; then
        echo "  ✅ $script"
    else
        echo "  ❌ $script - FALTA O NO EJECUTABLE"
    fi
done

echo ""

# Verificar documentación
echo "📚 Verificando documentación..."
docs=("README.md" "docs/README.md" "docs/CLAUDE.md" "docs/HTTP2_INVESTIGATION_SUMMARY.md" "docs/STATE-OF-THE-ART.md")
for doc in "${docs[@]}"; do
    if [ -f "$doc" ]; then
        echo "  ✅ $doc"
    else
        echo "  ❌ $doc - FALTA"
    fi
done

echo ""

# Verificar archivos de test
echo "🗃️ Verificando archivos de test..."
test_files=("test-data/file_1MB.dat" "test-data/file_10MB.dat" "test-data/file_100MB.dat")
for file in "${test_files[@]}"; do
    if [ -f "$file" ]; then
        size=$(du -h "$file" | cut -f1)
        echo "  ✅ $file ($size)"
    else
        echo "  ⚠️  $file - Se creará automáticamente en el primer test"
    fi
done

echo ""

# Verificar configuración de Quarkus
echo "⚙️ Verificando configuración de Quarkus..."
if [ -f "src/main/resources/application.properties" ]; then
    echo "  ✅ application.properties encontrado"
    
    # Verificar propiedades clave
    if grep -q "quarkus.http.http2=true" src/main/resources/application.properties; then
        echo "  ✅ HTTP/2 habilitado"
    else
        echo "  ⚠️  HTTP/2 no configurado"
    fi
    
    if grep -q "uploads-directory=/tmp/uploads" src/main/resources/application.properties; then
        echo "  ✅ Directorio de uploads configurado"
    else
        echo "  ⚠️  Directorio de uploads no configurado"
    fi
else
    echo "  ❌ application.properties - FALTA"
fi

echo ""

# Verificar dependencias del proyecto
echo "🔧 Verificando dependencias..."
if [ -f "build.gradle.kts" ]; then
    echo "  ✅ build.gradle.kts encontrado"
    if command -v ./gradlew &> /dev/null; then
        echo "  ✅ Gradle wrapper disponible"
    else
        echo "  ❌ Gradle wrapper no encontrado"
    fi
else
    echo "  ❌ build.gradle.kts - FALTA"
fi

if command -v python3 &> /dev/null; then
    echo "  ✅ Python3 disponible: $(python3 --version)"
else
    echo "  ❌ Python3 no encontrado"
fi

echo ""

# Resumen de funcionalidades
echo "🚀 RESUMEN DE FUNCIONALIDADES DISPONIBLES:"
echo ""
echo "📊 TESTS Y ANÁLISIS:"
echo "  ./scripts/test_uploads.sh          # Tests completos de todas las estrategias"
echo "  python3 scripts/analyze_simple.py  # Análisis básico sin dependencias"
echo "  ./scripts/test_http2_complete.sh   # Comparación HTTP/1.1 vs HTTP/2"
echo ""
echo "🏗️ DESARROLLO:"
echo "  ./gradlew quarkusDev               # Modo desarrollo con live reload"
echo "  ./gradlew build                    # Build completo"
echo ""
echo "📚 DOCUMENTACIÓN:"
echo "  README.md                          # Guía principal"
echo "  docs/README.md                     # Documentación técnica completa"
echo "  docs/CLAUDE.md                     # Guía para Claude Code"
echo ""
echo "🧹 LIMPIEZA:"
echo "  ./cleanup.sh                       # Reorganizar proyecto"
echo "  ./verify_setup.sh                  # Este script de verificación"

echo ""

# Verificar si la aplicación está ejecutándose
echo "🔍 Verificando estado de la aplicación..."
if curl -s http://localhost:8080/q/health > /dev/null 2>&1; then
    echo "  ✅ Aplicación ejecutándose en http://localhost:8080"
    echo "  🎯 LISTO PARA EJECUTAR TESTS!"
else
    echo "  ⏸️  Aplicación no ejecutándose"
    echo "  ▶️  Para iniciar: ./gradlew quarkusDev"
fi

echo ""
echo "=" * 60
echo "🏆 PROYECTO FILE UPLOAD POC - VERIFICACIÓN COMPLETA"
echo "=" * 60
echo ""
echo "📋 PRÓXIMOS PASOS:"
echo "  1. Iniciar aplicación: ./gradlew quarkusDev"
echo "  2. Ejecutar tests: ./scripts/test_uploads.sh"
echo "  3. Analizar resultados: python3 scripts/analyze_simple.py"
echo ""
echo "💡 Ver docs/README.md para documentación técnica completa."