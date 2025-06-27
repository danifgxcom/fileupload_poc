#!/bin/bash

# Script para configurar el entorno de análisis con gráficos

echo "=== Configuración del Entorno de Análisis ==="
echo ""

# Verificar si Python está disponible
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 no está instalado"
    exit 1
fi

echo "✓ Python3 encontrado: $(python3 --version)"

# Opción 1: Usar virtual environment (recomendado)
echo ""
echo "🔧 Configurando entorno virtual de Python..."

if [ ! -d "analysis_env" ]; then
    echo "  Creando entorno virtual..."
    python3 -m venv analysis_env
fi

echo "  Activando entorno virtual..."
source analysis_env/bin/activate

echo "  Instalando dependencias..."
pip install pandas matplotlib seaborn

echo ""
echo "✅ Entorno configurado correctamente"
echo ""
echo "Para usar el análisis con gráficos:"
echo "  1. Activa el entorno: source analysis_env/bin/activate"
echo "  2. Ejecuta análisis: python3 analyze_results.py"
echo "  3. Desactiva cuando termines: deactivate"
echo ""
echo "Para análisis básico sin dependencias:"
echo "  python3 analyze_results_simple.py"