package es.danifgx.benchmark.model

import es.danifgx.benchmark.TestFileGenerator.FileSizeCategory
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Represents a file upload strategy to be benchmarked.
 */
enum class UploadStrategy(val description: String) {
    MULTIPART_CLASSIC("Traditional multipart form upload using RESTEasy Classic"),
    MULTIPART_REACTIVE("Reactive multipart form upload using RESTEasy Reactive"),
    STREAMING_CLASSIC("Streaming upload using InputStream with RESTEasy Classic"),
    STREAMING_REACTIVE("Reactive streaming upload using Multi<Buffer> with RESTEasy Reactive"),
    CHUNKED_CLASSIC("Chunked upload using RESTEasy Classic"),
    CHUNKED_REACTIVE("Chunked upload using RESTEasy Reactive")
}

/**
 * Represents a server configuration to be benchmarked.
 */
enum class ServerConfig(val description: String, val configName: String) {
    UNDERTOW("Undertow (Tomcat-like) server", "undertow"),
    VERTX("Vert.x (Netty-based) server", "vertx")
}

/**
 * Represents a single benchmark test configuration.
 */
data class BenchmarkConfig(
    val uploadStrategy: UploadStrategy,
    val serverConfig: ServerConfig,
    val fileSizeCategory: FileSizeCategory
)

/**
 * Represents the metrics collected during a benchmark test.
 */
data class BenchmarkMetrics(
    val totalDuration: Duration,
    val cpuUsagePercent: Double,
    val maxMemoryUsageMB: Long,
    val throughputMBps: Double,
    val successRate: Double
)

/**
 * Represents the result of a single benchmark test.
 */
data class BenchmarkResult(
    val config: BenchmarkConfig,
    val metrics: BenchmarkMetrics,
    val testFile: Path,
    val fileSize: Long,
    val startTime: Instant,
    val endTime: Instant,
    val error: String? = null
)

/**
 * Represents a complete benchmark report with all test results.
 */
data class BenchmarkReport(
    val startTime: Instant,
    val endTime: Instant,
    val results: List<BenchmarkResult>,
    val systemInfo: SystemInfo
)

/**
 * Represents system information for the benchmark report.
 */
data class SystemInfo(
    val osName: String = System.getProperty("os.name"),
    val osVersion: String = System.getProperty("os.version"),
    val osArch: String = System.getProperty("os.arch"),
    val javaVersion: String = System.getProperty("java.version"),
    val availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
    val maxMemory: Long = Runtime.getRuntime().maxMemory() / (1024 * 1024) // in MB
)
