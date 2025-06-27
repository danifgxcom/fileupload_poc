package es.danifgx.fileupload

import io.quarkus.runtime.annotations.RegisterForReflection
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import io.vertx.core.buffer.Buffer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.nio.file.Path as NioPath

@Path("/upload-http2")
@ApplicationScoped
@RegisterForReflection
class Http2StreamingUploadResource {
    
    private val logger = Logger.getLogger(Http2StreamingUploadResource::class.java)
    
    @ConfigProperty(name = "quarkus.http.body.uploads-directory", defaultValue = "/tmp/uploads")
    lateinit var uploadsDirectory: String
    
    // Almacén para las sesiones de streaming activas
    private val streamingSessions = ConcurrentHashMap<String, StreamingSession>()
    
    /**
     * Upload con HTTP/2 frames automáticos - versión simplificada
     * Demuestra cómo HTTP/2 maneja frames transparentemente
     */
    @POST
    @Path("/streaming")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    fun uploadWithHttp2Streaming(inputStream: java.io.InputStream): Response {
        val startTime = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()
        
        try {
            // Crear directorio si no existe
            Files.createDirectories(Paths.get(uploadsDirectory))
            
            val fileName = UUID.randomUUID().toString() + "_http2_stream.bin"
            val filePath = Paths.get(uploadsDirectory, fileName)
            val totalBytesReceived = AtomicLong(0)
            
            // Crear sesión de streaming
            val session = StreamingSession(
                id = sessionId,
                fileName = fileName,
                filePath = filePath,
                startTime = startTime,
                bytesReceived = totalBytesReceived
            )
            streamingSessions[sessionId] = session
            
            logger.info("Iniciando HTTP/2 streaming upload: $sessionId")
            
            // Simular procesamiento frame por frame
            // HTTP/2 divide automáticamente en frames de ~16KB
            val buffer = ByteArray(16384) // Tamaño típico de frame HTTP/2
            var bytesRead: Int
            var frameCount = 0
            
            Files.newOutputStream(filePath).use { outputStream ->
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesReceived.addAndGet(bytesRead.toLong())
                    frameCount++
                    
                    // Log cada ciertos frames para mostrar el progreso
                    if (frameCount % 100 == 0) {
                        logger.debug("HTTP/2 frame #$frameCount procesado, total: ${totalBytesReceived.get()} bytes")
                    }
                }
            }
            
            val endTime = System.currentTimeMillis()
            val fileSize = Files.size(filePath)
            val uploadTime = endTime - startTime
            
            streamingSessions.remove(sessionId)
            
            logger.info("Completado HTTP/2 streaming: $sessionId ($fileSize bytes en ${uploadTime}ms, $frameCount frames)")
            
            return Response.ok(mapOf(
                "sessionId" to sessionId,
                "fileName" to fileName,
                "size" to fileSize,
                "uploadTimeMs" to uploadTime,
                "throughputMBps" to String.format("%.2f", (fileSize / 1024.0 / 1024.0) / (uploadTime / 1000.0)),
                "processingType" to "http2-streaming",
                "framesProcessed" to frameCount,
                "avgFrameSize" to if (frameCount > 0) fileSize / frameCount else 0
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error en HTTP/2 streaming", e)
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
    
    /**
     * Upload con multiplexing HTTP/2 simulado
     * Permite múltiples streams paralelos en una sola conexión
     */
    @POST
    @Path("/multiplexed/{streamId}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    fun uploadMultiplexedStream(
        @PathParam("streamId") streamId: String,
        @HeaderParam("X-Total-Streams") totalStreams: Int?,
        @HeaderParam("X-Stream-Index") streamIndex: Int?,
        @HeaderParam("X-Session-Id") sessionId: String?,
        dataStream: Multi<Buffer>
    ): Uni<Response> {
        val actualSessionId = sessionId ?: UUID.randomUUID().toString()
        val actualStreamIndex = streamIndex ?: 0
        val actualTotalStreams = totalStreams ?: 1
        
        return Uni.createFrom().deferred {
            try {
                val multiplexedSession = getOrCreateMultiplexedSession(actualSessionId, actualTotalStreams)
                
                logger.info("HTTP/2 Multiplexed upload - Session: $actualSessionId, Stream: $streamId ($actualStreamIndex/$actualTotalStreams)")
                
                // Procesar este stream específico
                dataStream
                    .onItem().invoke { buffer ->
                        val bytes = buffer.bytes
                        writeStreamData(multiplexedSession, actualStreamIndex, bytes)
                    }
                    .collect().asList()
                    .onItem().transform { _ ->
                        // Marcar stream como completado
                        multiplexedSession.completedStreams.add(actualStreamIndex)
                        
                        // Verificar si todos los streams están completos
                        if (multiplexedSession.completedStreams.size == actualTotalStreams) {
                            finalizeMultiplexedUpload(multiplexedSession)
                        }
                        
                        Response.ok(mapOf(
                            "sessionId" to actualSessionId,
                            "streamId" to streamId,
                            "streamIndex" to actualStreamIndex,
                            "completedStreams" to multiplexedSession.completedStreams.size,
                            "totalStreams" to actualTotalStreams,
                            "processingType" to "http2-multiplexed"
                        )).build()
                    }
                    .onFailure().recoverWithItem { error ->
                        logger.error("Error en stream multiplexed $streamId", error)
                        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(mapOf("error" to error.message))
                            .build()
                    }
                    
            } catch (e: Exception) {
                logger.error("Error en upload multiplexed", e)
                Uni.createFrom().item(
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(mapOf("error" to e.message))
                        .build()
                )
            }
        }
    }
    
    /**
     * Server-Sent Events para progreso de upload en tiempo real
     * Aprovecha HTTP/2 server push conceptualmente
     */
    @GET
    @Path("/progress/{sessionId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    fun getUploadProgress(@PathParam("sessionId") sessionId: String): Multi<String> {
        return Multi.createFrom().ticks().every(java.time.Duration.ofMillis(500))
            .onItem().transform { _ ->
                val session = streamingSessions[sessionId]
                if (session != null) {
                    val bytesReceived = session.bytesReceived.get()
                    val elapsedTime = System.currentTimeMillis() - session.startTime
                    val throughput = if (elapsedTime > 0) {
                        (bytesReceived / 1024.0 / 1024.0) / (elapsedTime / 1000.0)
                    } else 0.0
                    
                    "data: {\"sessionId\":\"$sessionId\",\"bytesReceived\":$bytesReceived,\"throughputMBps\":${String.format("%.2f", throughput)},\"elapsedMs\":$elapsedTime}\n\n"
                } else {
                    "data: {\"sessionId\":\"$sessionId\",\"status\":\"completed\"}\n\n"
                }
            }
            .select().first(100) // Limitar a 50 segundos máximo
    }
    
    /**
     * Información de sesiones activas
     */
    @GET
    @Path("/sessions")
    @Produces(MediaType.APPLICATION_JSON)
    fun getActiveSessions(): Response {
        val sessions = streamingSessions.values.map { session ->
            mapOf(
                "sessionId" to session.id,
                "fileName" to session.fileName,
                "bytesReceived" to session.bytesReceived.get(),
                "elapsedMs" to (System.currentTimeMillis() - session.startTime)
            )
        }
        
        return Response.ok(mapOf(
            "activeSessions" to sessions.size,
            "sessions" to sessions
        )).build()
    }
    
    // Métodos auxiliares
    private fun cleanupSession(sessionId: String, file: RandomAccessFile?) {
        try {
            file?.close()
            val session = streamingSessions.remove(sessionId)
            session?.let {
                Files.deleteIfExists(it.filePath)
            }
        } catch (e: Exception) {
            logger.error("Error al limpiar sesión $sessionId", e)
        }
    }
    
    private fun getOrCreateMultiplexedSession(sessionId: String, totalStreams: Int): MultiplexedSession {
        return streamingSessions.computeIfAbsent(sessionId) { _ ->
            Files.createDirectories(Paths.get(uploadsDirectory))
            val fileName = UUID.randomUUID().toString() + "_multiplexed.bin"
            val filePath = Paths.get(uploadsDirectory, fileName)
            
            MultiplexedSession(
                id = sessionId,
                fileName = fileName,
                filePath = filePath,
                startTime = System.currentTimeMillis(),
                totalStreams = totalStreams,
                streamFiles = mutableMapOf(),
                completedStreams = mutableSetOf(),
                bytesReceived = AtomicLong(0)
            )
        } as MultiplexedSession
    }
    
    private fun writeStreamData(session: MultiplexedSession, streamIndex: Int, data: ByteArray) {
        // Cada stream escribe en un archivo temporal separado
        val streamFile = session.streamFiles.computeIfAbsent(streamIndex) { _ ->
            val tempFile = Paths.get(uploadsDirectory, "${session.id}_stream_$streamIndex.tmp")
            RandomAccessFile(tempFile.toFile(), "rw")
        }
        
        synchronized(streamFile) {
            streamFile.write(data)
        }
        
        session.bytesReceived.addAndGet(data.size.toLong())
    }
    
    private fun finalizeMultiplexedUpload(session: MultiplexedSession) {
        try {
            val finalFile = RandomAccessFile(session.filePath.toFile(), "rw")
            
            // Combinar todos los archivos de stream en orden
            for (i in 0 until session.totalStreams) {
                val streamFile = session.streamFiles[i]
                if (streamFile != null) {
                    streamFile.seek(0)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (streamFile.read(buffer).also { bytesRead = it } != -1) {
                        finalFile.write(buffer, 0, bytesRead)
                    }
                    streamFile.close()
                    
                    // Limpiar archivo temporal
                    val tempFile = Paths.get(uploadsDirectory, "${session.id}_stream_$i.tmp")
                    Files.deleteIfExists(tempFile)
                }
            }
            
            finalFile.close()
            streamingSessions.remove(session.id)
            
            logger.info("Finalizado upload multiplexed: ${session.id} (${Files.size(session.filePath)} bytes)")
            
        } catch (e: Exception) {
            logger.error("Error al finalizar upload multiplexed ${session.id}", e)
        }
    }
    
    // Clases de datos
    open class StreamingSession(
        val id: String,
        val fileName: String,
        val filePath: NioPath,
        val startTime: Long,
        val bytesReceived: AtomicLong
    )
    
    class MultiplexedSession(
        id: String,
        fileName: String,
        filePath: NioPath,
        startTime: Long,
        bytesReceived: AtomicLong,
        val totalStreams: Int,
        val streamFiles: MutableMap<Int, RandomAccessFile>,
        val completedStreams: MutableSet<Int>
    ) : StreamingSession(id, fileName, filePath, startTime, bytesReceived)
}