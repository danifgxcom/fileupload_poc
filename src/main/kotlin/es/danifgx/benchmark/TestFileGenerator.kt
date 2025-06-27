package es.danifgx.benchmark

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Random
import kotlin.io.path.exists

/**
 * Utility class for generating test files of different sizes for benchmarking file uploads.
 */
@ApplicationScoped
class TestFileGenerator {
    private val logger = Logger.getLogger(TestFileGenerator::class.java)

    @ConfigProperty(name = "benchmark.test-files-directory", defaultValue = "test-files")
    lateinit var testFilesDirectory: String

    // File size categories
    enum class FileSizeCategory(val description: String) {
        SMALL("less than 10MB"),
        MEDIUM("10MB to 1000MB"),
        LARGE("more than 1GB")
    }

    // File size definitions in bytes
    companion object {
        const val KB = 1024L
        const val MB = 1024L * KB
        const val GB = 1024L * MB

        // Small files: 1MB, 5MB, 9MB
        val SMALL_FILE_SIZES = listOf(1 * MB, 5 * MB, 9 * MB)

        // Medium files: 10MB, 100MB, 500MB
        val MEDIUM_FILE_SIZES = listOf(10 * MB, 100 * MB, 500 * MB)

        // Large file: 1.1GB
        val LARGE_FILE_SIZES = listOf((1.1 * GB).toLong())
    }

    /**
     * Ensures that all test files exist, creating them if necessary.
     * @return A map of file size categories to lists of file paths
     */
    fun ensureTestFilesExist(): Map<FileSizeCategory, List<Path>> {
        val testFilesDir = Paths.get(testFilesDirectory)
        if (!testFilesDir.exists()) {
            logger.info("Creating test files directory: $testFilesDirectory")
            Files.createDirectories(testFilesDir)
        } else {
            logger.info("Test files directory already exists: $testFilesDirectory")
        }

        val result = mutableMapOf<FileSizeCategory, MutableList<Path>>()

        // Generate small files
        val smallFiles = generateFilesForCategory(testFilesDir, FileSizeCategory.SMALL, SMALL_FILE_SIZES)
        result[FileSizeCategory.SMALL] = smallFiles

        // Generate medium files
        val mediumFiles = generateFilesForCategory(testFilesDir, FileSizeCategory.MEDIUM, MEDIUM_FILE_SIZES)
        result[FileSizeCategory.MEDIUM] = mediumFiles

        // Generate large files
        val largeFiles = generateFilesForCategory(testFilesDir, FileSizeCategory.LARGE, LARGE_FILE_SIZES)
        result[FileSizeCategory.LARGE] = largeFiles

        return result
    }

    /**
     * Generates files for a specific category if they don't already exist.
     * @param directory The directory to create files in
     * @param category The file size category
     * @param sizes List of file sizes in bytes
     * @return List of file paths
     */
    private fun generateFilesForCategory(
        directory: Path, 
        category: FileSizeCategory, 
        sizes: List<Long>
    ): MutableList<Path> {
        val files = mutableListOf<Path>()

        sizes.forEachIndexed { index, size ->
            val fileName = "${category.name.lowercase()}_${index + 1}_${formatSize(size)}.bin"
            val filePath = directory.resolve(fileName)
            files.add(filePath)

            if (!filePath.exists()) {
                logger.info("Generating test file: $fileName (${formatSize(size)})")
                generateRandomFile(filePath.toFile(), size)
            } else {
                logger.info("Test file already exists: $fileName (${formatSize(size)})")
            }
        }

        return files
    }

    /**
     * Generates a file with random content of the specified size.
     * @param file The file to create
     * @param size The size in bytes
     */
    private fun generateRandomFile(file: File, size: Long) {
        val random = Random()
        val buffer = ByteArray(8 * KB.toInt()) // 8KB buffer

        FileOutputStream(file).use { fos ->
            var remaining = size

            while (remaining > 0) {
                val bytesToWrite = minOf(buffer.size.toLong(), remaining).toInt()
                random.nextBytes(buffer)
                fos.write(buffer, 0, bytesToWrite)
                remaining -= bytesToWrite

                // Log progress for large files
                if (size > 100 * MB && remaining % (100 * MB) < buffer.size) {
                    val progress = 100 - (remaining * 100 / size)
                    logger.info("Generating ${file.name}: $progress% complete")
                }
            }
        }
    }

    /**
     * Formats a file size in bytes to a human-readable string.
     * @param bytes The size in bytes
     * @return A formatted string (e.g., "5MB", "1.1GB")
     */
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < MB -> "${bytes / KB}KB"
            bytes < GB -> "${bytes / MB}MB"
            else -> String.format("%.1fGB", bytes.toDouble() / GB)
        }
    }
}
