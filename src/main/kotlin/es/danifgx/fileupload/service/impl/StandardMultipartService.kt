package es.danifgx.fileupload.service.impl

import es.danifgx.fileupload.service.FileUploadService
import es.danifgx.fileupload.service.FileUploadRegistry
import es.danifgx.fileupload.service.UploadResult
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

@ApplicationScoped
class StandardMultipartService : FileUploadService {
    
    @Inject
    lateinit var logger: Logger
    
    @Inject
    lateinit var registry: FileUploadRegistry
    
    @ConfigProperty(name = "file.upload.directory", defaultValue = "uploads/standard")
    lateinit var uploadDirectory: String
    
    @PostConstruct
    fun init() {
        // Crear directorio si no existe
        val directory = File(uploadDirectory)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        // Registrar esta estrategia
        registry.registerStrategy(this)
        
        logger.info("StandardMultipartService inicializado. Directorio: $uploadDirectory")
    }
    
    override val strategyName: String = "standard-multipart"
    
    override val strategyDescription: String = "Estrategia estándar de carga con multipart/form-data usando RESTEasy clásico"
    
    override val serverImplementation: String = "RESTEasy (Undertow)"
    
    override fun uploadFile(fileName: String, fileContent: InputStream, fileSize: Long): UploadResult {
        val startTime = System.currentTimeMillis()
        
        try {
            val actualFileName = UUID.randomUUID().toString() + "_" + fileName
            val filePath = Paths.get(uploadDirectory, actualFileName)
            
            // Guardar archivo
            Files.copy(fileContent, filePath, StandardCopyOption.REPLACE_EXISTING)
            
            val endTime = System.currentTimeMillis()
            val uploadTime = endTime - startTime
            val throughput = if (uploadTime > 0) 
                (fileSize / 1024.0 / 1024.0) / (uploadTime / 1000.0) 
            else 
                0.0
            
            logger.info("Archivo $actualFileName subido con éxito. Tamaño: $fileSize bytes, Tiempo: $uploadTime ms")
            
            return UploadResult(
                fileName = actualFileName,
                size = fileSize,
                uploadTimeMs = uploadTime,
                throughputMBps = throughput,
                uploadMethod = strategyName,
                serverImplementation = serverImplementation,
                success = true
            )
            
        } catch (e: Exception) {
            logger.error("Error al subir archivo: ${e.message}", e)
            
            return UploadResult(
                fileName = fileName,
                size = fileSize,
                uploadTimeMs = System.currentTimeMillis() - startTime,
                throughputMBps = 0.0,
                uploadMethod = strategyName,
                serverImplementation = serverImplementation,
                success = false,
                error = e.message
            )
        }
    }
    
    override fun getUploadedFilesInfo(): Map<String, Any> {
        val directory = File(uploadDirectory)
        val files = directory.listFiles() ?: emptyArray()
        
        val fileInfos = files.map { file ->
            mapOf(
                "name" to file.name,
                "size" to file.length(),
                "lastModified" to Date(file.lastModified())
            )
        }
        
        return mapOf(
            "strategyName" to strategyName,
            "totalFiles" to files.size,
            "totalSize" to files.sumOf { it.length() },
            "files" to fileInfos
        )
    }
}
