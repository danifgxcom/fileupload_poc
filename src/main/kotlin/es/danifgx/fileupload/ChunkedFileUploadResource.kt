package es.danifgx.fileupload

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.Path as NioPath

@jakarta.ws.rs.Path("/upload")
@ApplicationScoped
class ChunkedFileUploadResource {
    
    private val logger = Logger.getLogger(ChunkedFileUploadResource::class.java)
    
    @ConfigProperty(name = "quarkus.http.body.uploads-directory", defaultValue = "/tmp/uploads")
    lateinit var uploadsDirectory: String
    
    // Almacén para las sesiones de carga activas
    private val uploadSessions = ConcurrentHashMap<String, UploadSession>()
    
    // Iniciar una nueva sesión de carga
    @POST
    @jakarta.ws.rs.Path("/chunked/init")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun initializeUpload(initRequest: InitUploadRequest): Response {
        try {
            // Crear directorio si no existe
            Files.createDirectories(Paths.get(uploadsDirectory))
            
            // Generar ID para la sesión de carga
            val sessionId = UUID.randomUUID().toString()
            val fileName = UUID.randomUUID().toString() + "_" + initRequest.fileName
            val filePath = Paths.get(uploadsDirectory, fileName)
            
            // Inicializar archivo con el tamaño total
            val file = RandomAccessFile(filePath.toFile(), "rw")
            file.setLength(initRequest.totalSize)
            file.close()
            
            // Guardar información de la sesión
            val session = UploadSession(
                id = sessionId,
                fileName = fileName,
                filePath = filePath,
                totalSize = initRequest.totalSize,
                receivedChunks = mutableSetOf(),
                startTime = System.currentTimeMillis()
            )
            uploadSessions[sessionId] = session
            
            logger.info("Iniciada sesión de carga: $sessionId para archivo ${initRequest.fileName} (${initRequest.totalSize} bytes)")
            
            return Response.ok(mapOf(
                "sessionId" to sessionId,
                "fileName" to fileName,
                "uploadPath" to "/upload/chunked/chunk/$sessionId"
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error al iniciar sesión de carga", e)
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
    
    // Subir un fragmento (chunk)
    @POST
    @jakarta.ws.rs.Path("/chunked/chunk/{sessionId}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    fun uploadChunk(
        @PathParam("sessionId") sessionId: String,
        @HeaderParam("X-Chunk-Number") chunkNumber: Int,
        @HeaderParam("X-Chunk-Start-Byte") startByte: Long,
        @HeaderParam("X-Chunk-Size") chunkSize: Int,
        chunkData: ByteArray
    ): Response {
        val session = uploadSessions[sessionId] ?: return Response.status(Response.Status.NOT_FOUND)
            .entity(mapOf("error" to "Sesión de carga no encontrada"))
            .build()
        
        try {
            // Verificar que el tamaño del chunk es correcto
            if (chunkData.size != chunkSize) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "Tamaño del chunk incorrecto"))
                    .build()
            }
            
            // Escribir el chunk en la posición correcta del archivo
            val file = RandomAccessFile(session.filePath.toFile(), "rw")
            file.seek(startByte)
            file.write(chunkData)
            file.close()
            
            // Marcar el chunk como recibido
            session.receivedChunks.add(chunkNumber)
            
            logger.debug("Recibido chunk $chunkNumber para sesión $sessionId (${chunkSize} bytes)")
            
            return Response.ok(mapOf(
                "sessionId" to sessionId,
                "chunkNumber" to chunkNumber,
                "received" to true
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error al procesar chunk $chunkNumber para sesión $sessionId", e)
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
    
    // Finalizar la carga
    @POST
    @jakarta.ws.rs.Path("/chunked/complete/{sessionId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun completeUpload(@PathParam("sessionId") sessionId: String): Response {
        val session = uploadSessions[sessionId] ?: return Response.status(Response.Status.NOT_FOUND)
            .entity(mapOf("error" to "Sesión de carga no encontrada"))
            .build()
        
        try {
            val endTime = System.currentTimeMillis()
            val uploadTime = endTime - session.startTime
            val fileSize = Files.size(session.filePath)
            
            // Verificar que el tamaño del archivo es correcto
            if (fileSize != session.totalSize) {
                logger.warn("Tamaño del archivo incorrecto: $fileSize vs ${session.totalSize}")
            }
            
            // Eliminar la sesión
            uploadSessions.remove(sessionId)
            
            logger.info("Completada sesión de carga: $sessionId (${fileSize} bytes en ${uploadTime}ms)")
            
            return Response.ok(mapOf(
                "fileName" to session.fileName,
                "size" to fileSize,
                "uploadTimeMs" to uploadTime,
                "throughputMBps" to String.format("%.2f", (fileSize / 1024.0 / 1024.0) / (uploadTime / 1000.0)),
                "processingType" to "chunked"
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error al completar sesión de carga $sessionId", e)
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
    
    // Cancelar la carga
    @DELETE
    @jakarta.ws.rs.Path("/chunked/cancel/{sessionId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun cancelUpload(@PathParam("sessionId") sessionId: String): Response {
        val session = uploadSessions[sessionId] ?: return Response.status(Response.Status.NOT_FOUND)
            .entity(mapOf("error" to "Sesión de carga no encontrada"))
            .build()
        
        try {
            // Eliminar el archivo
            Files.deleteIfExists(session.filePath)
            
            // Eliminar la sesión
            uploadSessions.remove(sessionId)
            
            logger.info("Cancelada sesión de carga: $sessionId")
            
            return Response.ok(mapOf(
                "sessionId" to sessionId,
                "canceled" to true
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error al cancelar sesión de carga $sessionId", e)
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
    
    // Método simple para upload directo con streaming (para usar con benchmark)
    @POST
    @jakarta.ws.rs.Path("/chunked")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    fun directUpload(inputStream: java.io.InputStream): Response {
        val startTime = System.currentTimeMillis()
        
        try {
            // Crear directorio si no existe
            Files.createDirectories(Paths.get(uploadsDirectory))
            
            val fileName = UUID.randomUUID().toString() + "_direct.bin"
            val filePath = Paths.get(uploadsDirectory, fileName)
            
            // Escribir el archivo con streaming directo
            Files.copy(inputStream, filePath)
            
            val endTime = System.currentTimeMillis()
            val fileSize = Files.size(filePath)
            
            return Response.ok(mapOf(
                "fileName" to fileName,
                "size" to fileSize,
                "uploadTimeMs" to (endTime - startTime),
                "throughputMBps" to String.format("%.2f", (fileSize / 1024.0 / 1024.0) / ((endTime - startTime) / 1000.0)),
                "processingType" to "chunked-direct"
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error en carga directa", e)
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
    
    // Clases para datos
    data class InitUploadRequest(
        val fileName: String,
        val totalSize: Long,
        val chunkSize: Int
    )
    
    data class UploadSession(
        val id: String,
        val fileName: String,
        val filePath: NioPath,
        val totalSize: Long,
        val receivedChunks: MutableSet<Int>,
        val startTime: Long
    )
}
