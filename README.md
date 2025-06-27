# File Upload Performance Comparison - Quarkus + HTTP/2

Implementación completa que compara **4 estrategias diferentes** para uploads de archivos grandes, incluyendo análisis detallado de HTTP/2 vs HTTP/1.1.

## 🚀 Quick Start

```bash
# 1. Iniciar aplicación
./gradlew quarkusDev

# 2. Ejecutar tests completos
./scripts/test_uploads.sh

# 3. Analizar resultados
python3 scripts/analyze_simple.py
```

## 📁 Estructura del Proyecto

```
├── scripts/           # Scripts de testing y análisis
├── docs/             # Documentación completa
├── test-data/        # Archivos de prueba
├── results/          # Resultados de tests
└── src/              # Código fuente Quarkus
```

## 📊 Estrategias Implementadas

1. **Upload Estándar** - Multipart tradicional
2. **Upload Reactivo** - RESTEasy Reactive + Netty  
3. **Upload Chunked** - Protocolo binario personalizado
4. **HTTP/2 Streaming** - Aprovecha frames HTTP/2 nativos

## 🏆 Resultados Clave

- **Upload Chunked:** 248.6 MB/s promedio (mejor overall)
- **HTTP/2 Streaming:** 277.8 MB/s para archivos medianos
- **Error 101 HTTP/2:** Resuelto con `--http2-prior-knowledge`

## 📚 Documentación

Ver `/docs/` para documentación técnica completa.

---

> 💡 **Prueba de concepto completa** con análisis de rendimiento basado en datos reales.
