package es.danifgx.fileupload

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import jakarta.enterprise.context.ApplicationScoped

@Path("/upload")
@ApplicationScoped
class FileUploadResource {

    companion object {
        private const val UPLOAD_DIRECTORY = "uploads"
    }

    init {
        // Crear directorio de uploads si no existe
        val directory = File(UPLOAD_DIRECTORY)
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    @POST
    @Path("/standard")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    fun uploadFile(multipartInput: MultipartFormDataInput): Response {
        val startTime = System.currentTimeMillis()
        
        try {
            val inputPart = multipartInput.getFormDataMap()["file"]?.get(0)
                ?: return Response.status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "No file found in the request"))
                    .build()
            
            val fileName = UUID.randomUUID().toString() + "_" + inputPart.headers["Content-Disposition"]?.let {
                it.split("filename=")[1].replace("\"", "")
            } ?: "unknown_file"
            
            val filePath = Paths.get(UPLOAD_DIRECTORY, fileName)
            
            // Guardar archivo
            inputPart.body.use { inputStream ->
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING)
            }
            
            val endTime = System.currentTimeMillis()
            val fileSize = Files.size(filePath)
            
            return Response.ok(mapOf(
                "fileName" to fileName,
                "size" to fileSize,
                "uploadTimeMs" to (endTime - startTime),
                "throughputMBps" to String.format("%.2f", (fileSize / 1024.0 / 1024.0) / ((endTime - startTime) / 1000.0))
            )).build()
            
        } catch (e: Exception) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to e.message))
                .build()
        }
    }

    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    fun getUploadInfo(): Response {
        val directory = File(UPLOAD_DIRECTORY)
        val files = directory.listFiles() ?: emptyArray()
        
        val fileInfos = files.map { file ->
            mapOf(
                "name" to file.name,
                "size" to file.length(),
                "lastModified" to Date(file.lastModified())
            )
        }
        
        return Response.ok(mapOf(
            "totalFiles" to files.size,
            "totalSize" to files.sumOf { it.length() },
            "files" to fileInfos
        )).build()
    }
}
