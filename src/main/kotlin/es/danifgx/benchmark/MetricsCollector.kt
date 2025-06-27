package es.danifgx.benchmark

import es.danifgx.benchmark.model.BenchmarkMetrics
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import java.lang.management.ManagementFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Collects and calculates metrics during benchmark tests.
 */
@ApplicationScoped
class MetricsCollector {
    private val logger = Logger.getLogger(MetricsCollector::class.java)
    private val memoryMXBean = ManagementFactory.getMemoryMXBean()
    private val operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()
    
    // Track metrics during test
    private var startTime: Instant = Instant.now()
    private var endTime: Instant = Instant.now()
    private var maxMemoryUsage: AtomicLong = AtomicLong(0)
    private var successfulUploads: AtomicLong = AtomicLong(0)
    private var totalUploads: AtomicLong = AtomicLong(0)
    private var totalBytesProcessed: AtomicLong = AtomicLong(0)
    
    /**
     * Starts a new metrics collection session.
     */
    fun startCollection() {
        startTime = Instant.now()
        maxMemoryUsage.set(0)
        successfulUploads.set(0)
        totalUploads.set(0)
        totalBytesProcessed.set(0)
        logger.debug("Started metrics collection at $startTime")
    }
    
    /**
     * Records a successful upload.
     * @param bytes The number of bytes uploaded
     */
    fun recordSuccessfulUpload(bytes: Long) {
        successfulUploads.incrementAndGet()
        totalUploads.incrementAndGet()
        totalBytesProcessed.addAndGet(bytes)
        updateMemoryUsage()
        logger.debug("Recorded successful upload of $bytes bytes")
    }
    
    /**
     * Records a failed upload.
     */
    fun recordFailedUpload() {
        totalUploads.incrementAndGet()
        updateMemoryUsage()
        logger.debug("Recorded failed upload")
    }
    
    /**
     * Updates the maximum memory usage metric.
     */
    private fun updateMemoryUsage() {
        val currentMemoryUsage = memoryMXBean.heapMemoryUsage.used / (1024 * 1024) // in MB
        var currentMax = maxMemoryUsage.get()
        
        while (currentMemoryUsage > currentMax) {
            if (maxMemoryUsage.compareAndSet(currentMax, currentMemoryUsage)) {
                break
            }
            currentMax = maxMemoryUsage.get()
        }
    }
    
    /**
     * Ends the metrics collection session and returns the collected metrics.
     * @return The collected metrics
     */
    fun endCollection(): BenchmarkMetrics {
        endTime = Instant.now()
        val duration = Duration.between(startTime, endTime)
        logger.debug("Ended metrics collection at $endTime")
        
        // Calculate CPU usage (approximate)
        val cpuUsage = try {
            if (operatingSystemMXBean is com.sun.management.OperatingSystemMXBean) {
                (operatingSystemMXBean as com.sun.management.OperatingSystemMXBean).processCpuLoad * 100
            } else {
                // Fallback if the specific implementation is not available
                -1.0
            }
        } catch (e: Exception) {
            logger.warn("Failed to get CPU usage: ${e.message}")
            -1.0
        }
        
        // Calculate throughput in MB/s
        val durationSeconds = duration.seconds.toDouble().coerceAtLeast(0.001) // Avoid division by zero
        val totalMB = totalBytesProcessed.get().toDouble() / (1024 * 1024)
        val throughput = totalMB / durationSeconds
        
        // Calculate success rate
        val successRate = if (totalUploads.get() > 0) {
            successfulUploads.get().toDouble() / totalUploads.get().toDouble() * 100
        } else {
            0.0
        }
        
        return BenchmarkMetrics(
            totalDuration = duration,
            cpuUsagePercent = cpuUsage,
            maxMemoryUsageMB = maxMemoryUsage.get(),
            throughputMBps = throughput,
            successRate = successRate
        )
    }
}
