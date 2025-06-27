package es.danifgx.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import es.danifgx.benchmark.model.BenchmarkReport
import es.danifgx.benchmark.model.BenchmarkResult
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists

/**
 * Generates and saves benchmark reports.
 */
@ApplicationScoped
class ReportGenerator {
    private val logger = Logger.getLogger(ReportGenerator::class.java)
    
    @ConfigProperty(name = "benchmark.output-directory", defaultValue = "benchmark-results")
    lateinit var outputDirectory: String
    
    private val objectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        enable(SerializationFeature.INDENT_OUTPUT)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
    
    /**
     * Generates a benchmark report from the given results and saves it to a file.
     * @param results The benchmark results
     * @param startTime The start time of the benchmark
     * @param endTime The end time of the benchmark
     * @return The path to the saved report file
     */
    fun generateAndSaveReport(
        results: List<BenchmarkResult>,
        startTime: Instant,
        endTime: Instant
    ): Path {
        // Create the report
        val report = BenchmarkReport(
            startTime = startTime,
            endTime = endTime,
            results = results,
            systemInfo = es.danifgx.benchmark.model.SystemInfo()
        )
        
        // Ensure output directory exists
        val outputDir = Paths.get(outputDirectory)
        if (!outputDir.exists()) {
            logger.info("Creating output directory: $outputDirectory")
            Files.createDirectories(outputDir)
        }
        
        // Generate filename with timestamp
        val timestamp = LocalDateTime.ofInstant(endTime, ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val jsonFilePath = outputDir.resolve("benchmark_report_$timestamp.json")
        val csvFilePath = outputDir.resolve("benchmark_report_$timestamp.csv")
        
        // Save JSON report
        objectMapper.writeValue(jsonFilePath.toFile(), report)
        logger.info("Saved JSON benchmark report to: $jsonFilePath")
        
        // Save CSV report for easy analysis
        generateCsvReport(report, csvFilePath.toFile())
        logger.info("Saved CSV benchmark report to: $csvFilePath")
        
        // Generate summary and log it
        val summary = generateSummary(report)
        logger.info("Benchmark Summary:\n$summary")
        
        return jsonFilePath
    }
    
    /**
     * Generates a CSV report from the benchmark report.
     * @param report The benchmark report
     * @param file The file to save the CSV report to
     */
    private fun generateCsvReport(report: BenchmarkReport, file: File) {
        file.bufferedWriter().use { writer ->
            // Write header
            writer.write("Strategy,Server,File Size Category,File Size (MB),Duration (ms),CPU Usage (%),Max Memory (MB),Throughput (MB/s),Success Rate (%)\n")
            
            // Write data rows
            report.results.forEach { result ->
                val fileSizeMB = result.fileSize.toDouble() / (1024 * 1024)
                val durationMs = result.metrics.totalDuration.toMillis()
                
                writer.write(
                    "${result.config.uploadStrategy},${result.config.serverConfig},${result.config.fileSizeCategory}," +
                    "%.2f,%d,%.2f,%d,%.2f,%.2f\n".format(
                        fileSizeMB,
                        durationMs,
                        result.metrics.cpuUsagePercent,
                        result.metrics.maxMemoryUsageMB,
                        result.metrics.throughputMBps,
                        result.metrics.successRate
                    )
                )
            }
        }
    }
    
    /**
     * Generates a human-readable summary of the benchmark report.
     * @param report The benchmark report
     * @return A string containing the summary
     */
    private fun generateSummary(report: BenchmarkReport): String {
        val sb = StringBuilder()
        
        sb.appendLine("=== BENCHMARK SUMMARY ===")
        sb.appendLine("Start time: ${formatInstant(report.startTime)}")
        sb.appendLine("End time: ${formatInstant(report.endTime)}")
        sb.appendLine("Total duration: ${formatDuration(report.endTime.toEpochMilli() - report.startTime.toEpochMilli())}")
        sb.appendLine("System: ${report.systemInfo.osName} ${report.systemInfo.osVersion} (${report.systemInfo.osArch})")
        sb.appendLine("Java: ${report.systemInfo.javaVersion}")
        sb.appendLine("Processors: ${report.systemInfo.availableProcessors}")
        sb.appendLine("Max memory: ${report.systemInfo.maxMemory} MB")
        sb.appendLine()
        
        sb.appendLine("=== RESULTS BY STRATEGY ===")
        report.results.groupBy { it.config.uploadStrategy }
            .forEach { (strategy, results) ->
                sb.appendLine("${strategy.name}: ${strategy.description}")
                sb.appendLine("  Average throughput: %.2f MB/s".format(results.map { it.metrics.throughputMBps }.average()))
                sb.appendLine("  Average success rate: %.2f%%".format(results.map { it.metrics.successRate }.average()))
                sb.appendLine()
            }
        
        sb.appendLine("=== RESULTS BY SERVER ===")
        report.results.groupBy { it.config.serverConfig }
            .forEach { (server, results) ->
                sb.appendLine("${server.name}: ${server.description}")
                sb.appendLine("  Average throughput: %.2f MB/s".format(results.map { it.metrics.throughputMBps }.average()))
                sb.appendLine("  Average success rate: %.2f%%".format(results.map { it.metrics.successRate }.average()))
                sb.appendLine()
            }
        
        sb.appendLine("=== RESULTS BY FILE SIZE ===")
        report.results.groupBy { it.config.fileSizeCategory }
            .forEach { (category, results) ->
                sb.appendLine("${category.name}: ${category.description}")
                sb.appendLine("  Average throughput: %.2f MB/s".format(results.map { it.metrics.throughputMBps }.average()))
                sb.appendLine("  Average success rate: %.2f%%".format(results.map { it.metrics.successRate }.average()))
                sb.appendLine()
            }
        
        return sb.toString()
    }
    
    /**
     * Formats an Instant as a human-readable date and time.
     * @param instant The Instant to format
     * @return A formatted string
     */
    private fun formatInstant(instant: Instant): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()))
    }
    
    /**
     * Formats a duration in milliseconds as a human-readable string.
     * @param millis The duration in milliseconds
     * @return A formatted string (e.g., "1h 2m 3s")
     */
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
