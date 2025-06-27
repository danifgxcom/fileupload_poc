# Estado de la Cuestión: Métodos para File Upload de Ficheros Grandes

## Introducción

Este documento presenta un análisis de las diferentes estrategias, arquitecturas y consideraciones técnicas para implementar la carga de archivos grandes en aplicaciones web modernas, con enfoque específico en el ecosistema Quarkus. El objetivo es proporcionar una visión general de las opciones disponibles antes de implementar una prueba de concepto.

## Factores Clave a Considerar

### 1. Protocolo HTTP y Versiones

#### HTTP/1.1
- **Características**: Conexiones persistentes, pero con limitaciones en términos de paralelización (bloqueo head-of-line).
- **Ventajas**: Ampliamente soportado, compatible con casi todos los servidores y clientes.
- **Desventajas**: Menor rendimiento con archivos grandes debido a la naturaleza secuencial de las solicitudes.

#### HTTP/2
- **Características**: Multiplexación de streams, compresión de headers, priorización de streams.
- **Ventajas**: Mejor rendimiento para transferencias paralelas, menor latencia, más eficiente en ancho de banda.
- **Desventajas**: Complejidad adicional, aunque la mayoría de las bibliotecas modernas manejan esto de forma transparente.

#### HTTP/3 (QUIC)
- **Características**: Basado en UDP en lugar de TCP, reduciendo la latencia de establecimiento de conexión.
- **Ventajas**: Mejor rendimiento en redes inestables, menor latencia, mejor manejo de pérdida de paquetes.
- **Desventajas**: Soporte aún en desarrollo en algunas plataformas y servidores.

### 2. Servidores y Motores de Ejecución

#### Tomcat (Estándar en Quarkus RESTEasy Classic)
- **Características**: Servidor Java EE tradicional, modelo de threading por solicitud.
- **Ventajas**: Estable, maduro, bien documentado.
- **Desventajas**: Mayor consumo de memoria por conexión, menos eficiente para cargas masivas concurrentes.

#### Netty (Utilizado en Quarkus Reactive)
- **Características**: Framework de red asíncrono, modelo de eventos.
- **Ventajas**: Alta concurrencia, menor uso de memoria, excelente para operaciones de I/O intensivas.
- **Desventajas**: Modelo de programación más complejo, curva de aprendizaje más pronunciada.

#### Vert.x (Compatible con Quarkus)
- **Características**: Toolkit reactivo basado en Netty.
- **Ventajas**: Alto rendimiento, escalabilidad, API más amigable que Netty directo.
- **Desventajas**: Diferente modelo mental de programación si se viene de entornos sincronizados tradicionales.

### 3. Paradigmas de Programación

#### Programación Sincrónica/Bloqueante
- **Características**: Modelo tradicional de Java, un thread por solicitud.
- **Ventajas**: Código más simple y familiar, fácil de razonar.
- **Desventajas**: Menor escalabilidad, uso ineficiente de recursos para operaciones de I/O intensivas.

#### Programación Reactiva/No-Bloqueante
- **Características**: Basada en streams y eventos, sin bloqueo de threads.
- **Ventajas**: Mayor throughput, mejor utilización de recursos, más escalable.
- **Desventajas**: Código más complejo, debugging más difícil, necesidad de pensar de forma reactiva.

### 4. Estrategias de Implementación de File Upload

#### Multipart Form Data (Tradicional)
- **Características**: Método estándar HTTP para subir archivos.
- **Ventajas**: Amplio soporte en browsers y servidores, fácil de implementar.
- **Desventajas**: No óptimo para archivos muy grandes, consume memoria para el almacenamiento intermedio.
- **Implementación en Quarkus**: 
  - Con `@MultipartForm` y `@FormParam` en JAX-RS
  - Con `@MultipartConfig` en Servlets
  - Con `io.quarkus:quarkus-resteasy-multipart` o `io.quarkus:quarkus-rest` dependiendo del stack

#### Streaming Upload API
- **Características**: Manejo de datos en chunks a medida que se reciben.
- **Ventajas**: Menor uso de memoria, mejor para archivos grandes.
- **Desventajas**: Implementación más compleja, necesidad de manejar el estado de la transferencia.
- **Implementación en Quarkus**:
  - Uso de `InputStream` en REST clásico
  - `Publisher<Buffer>` o `Multi<Buffer>` en modo reactivo

#### Carga Fragmentada (Chunked Upload)
- **Características**: División del archivo en el cliente y envío por partes.
- **Ventajas**: Posibilidad de pausar/reanudar, mejor manejo de errores, paralelización.
- **Desventajas**: Requiere lógica en cliente y servidor para coordinar y combinar fragmentos.
- **Implementación**: Requiere endpoints específicos para inicio de carga, subida de fragmentos y finalización.

#### Resumable Uploads
- **Características**: Permite continuar una carga interrumpida.
- **Ventajas**: Resistencia a fallos de red, mejor UX, ahorro de ancho de banda.
- **Desventajas**: Complejidad adicional, necesidad de almacenar estado entre sesiones.
- **Implementación**: Protocolos como tus (resumable.js) o el protocolo TUS (tus.io).

### 5. Consideraciones para el Cliente

#### Navegador Web (JavaScript)
- **Bibliotecas populares**:
  - **Axios**: Soporte para progreso de carga y cancelación.
  - **Fetch API**: API nativa del navegador, más simple pero con menos funcionalidades.
  - **UploadJS/Uppy**: Bibliotecas especializadas para cargas robustas.
  - **Resumable.js/TUS Client**: Para cargas reanudables.
- **Consideraciones**:
  - Manejo del progreso (eventos `progress`)
  - Capacidad de cancelación
  - Fragmentación del lado del cliente

#### Aplicaciones Nativas
- **Ventajas**: Mayor control sobre el proceso de carga, posibilidad de compresión previa.
- **Opciones**:
  - Kotlin Multiplatform con Ktor Client
  - OkHttp (para Android)
  - Libraries nativas específicas de plataforma

### 6. Almacenamiento y Procesamiento

#### Almacenamiento Temporal
- **Opciones**:
  - Sistema de archivos
  - Bases de datos (BLOB)
  - Object storage (S3, MinIO)
- **Consideraciones**:
  - Ubicación (memoria vs. disco)
  - Limpieza de datos temporales
  - Manejo de cargas incompletas

#### Procesamiento Asíncrono
- **Patrones**:
  - Colas de mensajes (Kafka, RabbitMQ)
  - Trabajos en segundo plano
- **Ventajas**: No bloquea el thread principal, permite escalar el procesamiento.

## Recomendaciones según Escenarios

### Para Archivos Pequeños/Medianos (< 10MB)
- **Enfoque recomendado**: Multipart tradicional
- **Stack**: JAX-RS estándar con Tomcat
- **Razones**: Simplicidad, suficiente rendimiento, código más mantenible

### Para Archivos Grandes (10MB - 1GB)
- **Enfoque recomendado**: Streaming API con backend reactivo
- **Stack**: Quarkus Reactive (RESTEasy Reactive + Netty)
- **Razones**: Mejor utilización de memoria, mayor concurrencia

### Para Archivos Muy Grandes (> 1GB) o Entornos Inestables
- **Enfoque recomendado**: Carga fragmentada + resumable
- **Stack**: Quarkus Reactive + implementación TUS o similar
- **Cliente**: Biblioteca especializada (resumable.js, tus-js-client)
- **Razones**: Resistencia a fallos, mejor experiencia de usuario, posibilidad de pausar/reanudar

## Métricas de Evaluación para la Prueba de Concepto

Para una evaluación objetiva de las diferentes estrategias, se recomienda medir:

1. **Rendimiento**:
   - Tiempo total de carga
   - Uso de CPU durante la carga
   - Uso de memoria durante la carga
   - Throughput (MB/s)

2. **Escalabilidad**:
   - Número máximo de cargas concurrentes
   - Degradación de rendimiento bajo carga

3. **Confiabilidad**:
   - Comportamiento ante interrupciones de red
   - Tasa de éxito de cargas completas

4. **Experiencia de Usuario**:
   - Feedback de progreso
   - Capacidad de cancelar/pausar/reanudar

## Conclusión

La elección óptima para la implementación de carga de archivos grandes depende de varios factores, incluyendo el tamaño típico de los archivos, el volumen esperado de cargas concurrentes, y los requisitos de experiencia de usuario.

Para el contexto de Quarkus:
- El enfoque reactivo con Netty/Vert.x ofrece claras ventajas para archivos grandes
- La implementación de protocolos de carga reanudable proporciona mejor experiencia de usuario
- HTTP/2 puede mejorar significativamente el rendimiento en conexiones de alta latencia

La siguiente fase debería incluir la implementación de una prueba de concepto que compare al menos dos enfoques diferentes, midiendo las métricas mencionadas anteriormente para determinar la solución óptima para el caso de uso específico.
