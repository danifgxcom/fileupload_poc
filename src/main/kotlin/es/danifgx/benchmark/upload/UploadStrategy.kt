package es.danifgx.benchmark.upload

import es.danifgx.benchmark.model.BenchmarkResult
import es.danifgx.benchmark.model.ServerConfig
import es.danifgx.benchmark.model.UploadStrategy
import java.nio.file.Path

/**
 * Interface for file upload strategies.
 * Each implementation represents a different way to upload files.
 */
interface FileUploadStrategy {
    /**
     * Gets the type of upload strategy.
     */
    val strategyType: UploadStrategy
    
    /**
     * Gets the server configuration this strategy works with.
     */
    val serverConfig: ServerConfig
    
    /**
     * Uploads a file using this strategy.
     * @param filePath The path to the file to upload
     * @return The result of the upload operation
     */
    suspend fun uploadFile(filePath: Path): BenchmarkResult
    
    /**
     * Gets the endpoint URL for this upload strategy.
     * @return The endpoint URL
     */
    fun getEndpointUrl(): String
}

/**
 * Factory for creating upload strategies.
 */
interface UploadStrategyFactory {
    /**
     * Creates all available upload strategies for the given server configuration.
     * @param serverConfig The server configuration
     * @return A list of upload strategies
     */
    fun createStrategies(serverConfig: ServerConfig): List<FileUploadStrategy>
}
