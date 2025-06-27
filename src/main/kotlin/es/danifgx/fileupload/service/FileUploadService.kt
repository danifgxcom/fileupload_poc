package es.danifgx.fileupload.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput
import org.jboss.resteasy.reactive.multipart.FileUpload
import java.io.File
import java.io.InputStream
import java.util.*

/**
 * Resultados de una operación de carga de archivos
 */
data class UploadResult(
    val fileName: String,
    val size: Long,
    val uploadTimeMs: Long,
    val throughputMBps: Double,
    val uploadMethod: String,
    val serverImplementation: String,
    val success: Boolean,
    val error: String? = null
)

/**
 * Interfaz para servicios de carga de archivos
 * Permite implementar diferentes estrategias
 */
interface FileUploadService {
    /**
     * Nombre único de la estrategia de carga
     */
    val strategyName: String
    
    /**
     * Descripción de la estrategia de carga
     */
    val strategyDescription: String
    
    /**
     * Servidor/implementación utilizada
     */
    val serverImplementation: String
    
    /**
     * Procesa una carga de archivo
     */
    fun uploadFile(fileName: String, fileContent: InputStream, fileSize: Long): UploadResult
    
    /**
     * Verifica si la estrategia está habilitada
     */
    fun isEnabled(): Boolean = true
    
    /**
     * Retorna información sobre los archivos cargados
     */
    fun getUploadedFilesInfo(): Map<String, Any>
}

/**
 * Registro de todas las estrategias de carga disponibles
 */
@ApplicationScoped
class FileUploadRegistry {
    private val strategies = mutableListOf<FileUploadService>()
    
    fun registerStrategy(strategy: FileUploadService) {
        strategies.add(strategy)
    }
    
    fun getStrategies(): List<FileUploadService> = strategies.filter { it.isEnabled() }
    
    fun getStrategyByName(name: String): FileUploadService? = 
        strategies.find { it.strategyName == name && it.isEnabled() }
}
