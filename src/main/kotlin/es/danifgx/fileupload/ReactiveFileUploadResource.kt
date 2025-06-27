package es.danifgx.fileupload

import io.quarkus.runtime.annotations.RegisterForReflection
import io.smallrye.common.annotation.Blocking
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.resteasy.reactive.RestForm
import org.jboss.resteasy.reactive.multipart.FileUpload
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

@Path("/upload-reactive")
@ApplicationScoped
@RegisterForReflection
class ReactiveFileUploadResource {

    @ConfigProperty(name = "quarkus.http.body.uploads-directory", defaultValue = "uploads")
    lateinit var uploadsDirectory: String

    @POST
    @Path("/standard")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking // Indica que esta operación es bloqueante a pesar de estar en un controlador reactivo
    fun uploadFile(@RestForm("file") fileUpload: FileUpload): Response {
        val startTime = System.currentTimeMillis()
        
        try {
            val fileName = UUID.randomUUID().toString() + "_" + fileUpload.fileName
            val targetPath = Paths.get(uploadsDirectory, fileName)
            
            // Crear directorio si no existe
            Files.createDirectories(Paths.get(uploadsDirectory))
            
            // Copiar archivo subido a la ubicación final
            Files.copy(fileUpload.uploadedFile(), targetPath, StandardCopyOption.REPLACE_EXISTING)
            
            val endTime = System.currentTimeMillis()
            val fileSize = Files.size(targetPath)
            
            return Response.ok(mapOf(
                "fileName" to fileName,
                "size" to fileSize,
                "uploadTimeMs" to (endTime - startTime),
                "throughputMBps" to String.format("%.2f", (fileSize / 1024.0 / 1024.0) / ((endTime - startTime) / 1000.0)),
                "processingType" to "reactive"
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
        val directory = File(uploadsDirectory)
        if (!directory.exists()) {
            return Response.ok(mapOf(
                "totalFiles" to 0,
                "totalSize" to 0,
                "files" to emptyList<Any>()
            )).build()
        }
        
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
