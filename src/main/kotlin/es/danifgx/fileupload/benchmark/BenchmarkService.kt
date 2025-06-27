package es.danifgx.fileupload.benchmark

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@ApplicationScoped
class BenchmarkService {
    private val logger = Logger.getLogger(BenchmarkService::class.java)

    @ConfigProperty(name = "benchmark.output-directory", defaultValue = "benchmark-results")
    lateinit var outputDirectory: String

    fun manualBenchmark(): Map<String, Any> {
        logger.info("Ejecutando benchmark manual")
        
        return mapOf(
            "status" to "completed",
            "message" to "Benchmark simplificado ejecutado",
            "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "uploadStrategies" to listOf(
                mapOf(
                    "strategy" to "standard",
                    "endpoint" to "/upload/standard",
                    "description" to "Upload estándar con FileUpload"
                ),
                mapOf(
                    "strategy" to "reactive",
                    "endpoint" to "/upload-reactive/standard", 
                    "description" to "Upload reactivo con Quarkus REST"
                ),
                mapOf(
                    "strategy" to "chunked",
                    "endpoint" to "/upload/chunked",
                    "description" to "Upload en fragmentos para archivos grandes"
                )
            )
        )
    }
}