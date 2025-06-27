#!/bin/bash

# Script de limpieza optimizado - desde /scripts/

echo "🧹 Iniciando limpieza del proyecto..."

# Cambiar al directorio raíz del proyecto
cd "$(dirname "$0")/.."

# 1. ELIMINAR ARCHIVOS INNECESARIOS
echo "🗑️ Eliminando archivos innecesarios..."

# Eliminar duplicados y archivos obsoletos
rm -f analyze_results_simple.py
rm -f test_http2_features.sh
rm -f test_http_versions.sh 
rm -f test_uploads_quick.sh
rm -f performance_test.py

# Eliminar documentación redundante/borrador
rm -f FileUploadStrategiesAnalysis.md
rm -f DEPENDENCY_SOLUTION.md
rm -f FINAL_SOLUTION_SUMMARY.md
rm -f INTEGRATION_SUMMARY.md

# Eliminar logs y archivos temporales
rm -f quarkus.log quarkus_new.log
rm -rf uploads
rm -rf build/tmp

# 2. LIMPIAR RESULTADOS (opcional)
read -p "¿Eliminar todos los resultados de tests? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "📊 Eliminando resultados..."
    rm -f results/*.csv results/*.png results/*.txt results/*.md
else
    echo "📊 Conservando resultados existentes"
fi

# 3. LIMPIAR ARCHIVOS DE UPLOAD TEMPORALES
echo "🗃️ Limpiando uploads temporales..."
rm -rf /tmp/uploads/* 2>/dev/null || true

# 4. LIMPIAR ENTORNO DE ANÁLISIS (opcional)
read -p "¿Eliminar entorno virtual de análisis? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🔧 Eliminando entorno virtual..."
    rm -rf analysis_env
    rm -f analyze_results.py
fi

# 5. ACTUALIZAR PERMISOS
echo "🔐 Actualizando permisos..."
chmod +x scripts/*.sh

echo ""
echo "✅ Limpieza completada!"
echo ""
echo "📁 Estructura actual:"
echo "   ├── scripts/     - Scripts de testing y análisis"
echo "   ├── docs/        - Documentación técnica"  
echo "   ├── test-data/   - Archivos de prueba"
echo "   ├── results/     - Resultados de tests"
echo "   └── src/         - Código fuente"
echo ""
echo "🚀 Para usar:"
echo "   ./scripts/test_uploads.sh"
echo "   python3 scripts/analyze_simple.py"