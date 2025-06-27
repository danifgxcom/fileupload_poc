package es.danifgx.upload

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * REST resource for classic (non-reactive) file uploads.
 */
@Path("/upload/classic")
@ApplicationScoped
class ClassicUploadResource {
    private val logger = Logger.getLogger(ClassicUploadResource::class.java)
    
    // Directory to store uploaded files
    private val uploadDir = Paths.get("uploads/classic")
    
    init {
        // Ensure upload directory exists
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir)
            logger.info("Created upload directory: $uploadDir")
        }
    }
    
    /**
     * Uploads a file using multipart form data.
     * @param input The multipart form data input
     * @return Response with upload result
     */
    @POST
    @Path("/multipart")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    fun uploadMultipart(input: MultipartFormDataInput): Response {
        val startTime = System.currentTimeMillis()
        
        try {
            val inputPart = input.getFormDataMap()["file"]?.get(0)
                ?: return Response.status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "No file part found"))
                    .build()
            
            // Use a simple UUID-based filename to avoid string manipulation issues
            val fileName = "file_${UUID.randomUUID()}"
            val targetPath = uploadDir.resolve(fileName)
            
            // Copy the file
            val fileSize: Long
            inputPart.body.use { body ->
                val inputStream = body as InputStream
                fileSize = Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            logger.info("Multipart upload completed: $fileName, size: $fileSize bytes, duration: $duration ms")
            
            return Response.ok(mapOf(
                "fileName" to fileName,
                "size" to fileSize,
                "path" to targetPath.toString(),
                "duration" to duration
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error during multipart upload", e)
            return Response.serverError()
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
    
    /**
     * Uploads a file using streaming.
     * @param inputStream The input stream containing the file data
     * @param fileName The name of the file (from query parameter)
     * @return Response with upload result
     */
    @POST
    @Path("/stream")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    fun uploadStream(
        inputStream: InputStream,
        @QueryParam("fileName") fileName: String?
    ): Response {
        val startTime = System.currentTimeMillis()
        
        try {
            val actualFileName = fileName ?: "file_${UUID.randomUUID()}"
            val targetPath = uploadDir.resolve(actualFileName)
            
            // Copy the file
            val fileSize = Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING)
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            logger.info("Stream upload completed: $actualFileName, size: $fileSize bytes, duration: $duration ms")
            
            return Response.ok(mapOf(
                "fileName" to actualFileName,
                "size" to fileSize,
                "path" to targetPath.toString(),
                "duration" to duration
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error during stream upload", e)
            return Response.serverError()
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
    
    /**
     * Initiates a chunked upload.
     * @return Response with upload ID
     */
    @POST
    @Path("/chunked/init")
    @Produces(MediaType.APPLICATION_JSON)
    fun initChunkedUpload(
        @QueryParam("fileName") fileName: String?,
        @QueryParam("totalChunks") totalChunks: Int?
    ): Response {
        try {
            val uploadId = UUID.randomUUID().toString()
            val actualFileName = fileName ?: "file_$uploadId"
            val chunksDir = uploadDir.resolve("chunks_$uploadId")
            Files.createDirectories(chunksDir)
            
            // Store metadata
            val metadataFile = chunksDir.resolve("metadata.properties")
            val metadata = listOf(
                "fileName=$actualFileName",
                "uploadId=$uploadId",
                "totalChunks=${totalChunks ?: 0}",
                "receivedChunks=0"
            )
            Files.write(metadataFile, metadata)
            
            logger.info("Chunked upload initiated: $uploadId, fileName: $actualFileName")
            
            return Response.ok(mapOf(
                "uploadId" to uploadId,
                "fileName" to actualFileName
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error initiating chunked upload", e)
            return Response.serverError()
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
    
    /**
     * Uploads a chunk of a file.
     * @param uploadId The upload ID from the init request
     * @param chunkIndex The index of this chunk
     * @param inputStream The input stream containing the chunk data
     * @return Response with chunk upload result
     */
    @POST
    @Path("/chunked/chunk/{uploadId}/{chunkIndex}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    fun uploadChunk(
        @PathParam("uploadId") uploadId: String,
        @PathParam("chunkIndex") chunkIndex: Int,
        inputStream: InputStream
    ): Response {
        val startTime = System.currentTimeMillis()
        
        try {
            val chunksDir = uploadDir.resolve("chunks_$uploadId")
            if (!Files.exists(chunksDir)) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Upload ID not found"))
                    .build()
            }
            
            // Save the chunk
            val chunkFile = chunksDir.resolve("chunk_$chunkIndex")
            val chunkSize = Files.copy(inputStream, chunkFile, StandardCopyOption.REPLACE_EXISTING)
            
            // Update metadata
            val metadataFile = chunksDir.resolve("metadata.properties")
            val metadata = Files.readAllLines(metadataFile).associate {
                val parts = it.split("=", limit = 2)
                parts[0] to (if (parts.size > 1) parts[1] else "")
            }.toMutableMap()
            
            val receivedChunks = (metadata["receivedChunks"]?.toIntOrNull() ?: 0) + 1
            metadata["receivedChunks"] = receivedChunks.toString()
            
            Files.write(metadataFile, metadata.map { "${it.key}=${it.value}" })
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            logger.info("Chunk uploaded: $uploadId, chunk: $chunkIndex, size: $chunkSize bytes, duration: $duration ms")
            
            return Response.ok(mapOf(
                "uploadId" to uploadId,
                "chunkIndex" to chunkIndex,
                "size" to chunkSize,
                "receivedChunks" to receivedChunks,
                "duration" to duration
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error uploading chunk", e)
            return Response.serverError()
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
    
    /**
     * Finalizes a chunked upload by combining all chunks.
     * @param uploadId The upload ID from the init request
     * @return Response with final upload result
     */
    @POST
    @Path("/chunked/finalize/{uploadId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun finalizeChunkedUpload(@PathParam("uploadId") uploadId: String): Response {
        val startTime = System.currentTimeMillis()
        
        try {
            val chunksDir = uploadDir.resolve("chunks_$uploadId")
            if (!Files.exists(chunksDir)) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Upload ID not found"))
                    .build()
            }
            
            // Read metadata
            val metadataFile = chunksDir.resolve("metadata.properties")
            val metadata = Files.readAllLines(metadataFile).associate {
                val parts = it.split("=", limit = 2)
                parts[0] to (if (parts.size > 1) parts[1] else "")
            }
            
            val fileName = metadata["fileName"] ?: "file_$uploadId"
            val totalChunks = metadata["totalChunks"]?.toIntOrNull() ?: 0
            val receivedChunks = metadata["receivedChunks"]?.toIntOrNull() ?: 0
            
            if (totalChunks > 0 && receivedChunks < totalChunks) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(mapOf(
                        "error" to "Not all chunks received",
                        "receivedChunks" to receivedChunks,
                        "totalChunks" to totalChunks
                    ))
                    .build()
            }
            
            // Combine chunks
            val targetPath = uploadDir.resolve(fileName)
            var totalSize = 0L
            
            Files.newOutputStream(targetPath).use { outputStream ->
                // Find all chunk files and sort them by index
                val chunkFiles = Files.list(chunksDir)
                    .filter { it.fileName.toString().startsWith("chunk_") }
                    .sorted { a, b ->
                        val aIndex = a.fileName.toString().substringAfter("chunk_").toIntOrNull() ?: Int.MAX_VALUE
                        val bIndex = b.fileName.toString().substringAfter("chunk_").toIntOrNull() ?: Int.MAX_VALUE
                        aIndex.compareTo(bIndex)
                    }
                    .toList()
                
                // Combine chunks
                for (chunkFile in chunkFiles) {
                    Files.newInputStream(chunkFile).use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalSize += bytesRead
                        }
                    }
                }
            }
            
            // Clean up chunks
            Files.walk(chunksDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.delete(it) }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            logger.info("Chunked upload finalized: $uploadId, fileName: $fileName, size: $totalSize bytes, duration: $duration ms")
            
            return Response.ok(mapOf(
                "uploadId" to uploadId,
                "fileName" to fileName,
                "size" to totalSize,
                "path" to targetPath.toString(),
                "chunks" to receivedChunks,
                "duration" to duration
            )).build()
            
        } catch (e: Exception) {
            logger.error("Error finalizing chunked upload", e)
            return Response.serverError()
                .entity(mapOf("error" to e.message))
                .build()
        }
    }
}
