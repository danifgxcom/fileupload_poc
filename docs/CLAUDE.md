# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin/Java file upload proof-of-concept (POC) project built with Quarkus. The project explores different strategies for large file uploads, comparing traditional multipart, reactive, and chunked upload approaches. The codebase is structured to benchmark and analyze performance characteristics of different upload strategies.

## Architecture

The project implements three distinct file upload strategies:

### 1. Traditional Multipart Upload (`FileUploadResource.kt`)
- **Endpoint**: `/upload/standard`
- **Technology**: JAX-RS with RESTEasy Classic
- **Processing**: Synchronous/blocking approach using `MultipartFormDataInput`
- **Use case**: Standard file uploads, simpler implementation

### 2. Reactive Upload (`ReactiveFileUploadResource.kt`)
- **Endpoint**: `/upload-reactive/standard`
- **Technology**: RESTEasy Reactive with Netty
- **Processing**: Uses `@RestForm` and `FileUpload` with `@Blocking` annotation
- **Use case**: Better performance for concurrent uploads

### 3. Chunked Upload (`ChunkedFileUploadResource.kt`)
- **Endpoints**: 
  - `/upload/chunked/init` - Initialize upload session
  - `/upload/chunked/chunk/{sessionId}` - Upload individual chunks
  - `/upload/chunked/complete/{sessionId}` - Complete upload
  - `/upload/chunked/cancel/{sessionId}` - Cancel upload
  - `/upload/chunked` - Direct binary upload for benchmarking
- **Technology**: Session-based chunked uploads with `RandomAccessFile`
- **Use case**: Large files, resumable uploads, progress tracking

### Benchmarking System
- **Location**: `src/main/kotlin/es/danifgx/benchmark/`
- **Components**:
  - `BenchmarkResource.kt` - REST endpoints for running benchmarks
  - `BenchmarkService.kt` - Core benchmarking logic
  - `MetricsCollector.kt` - Performance metrics collection
  - `TestFileGenerator.kt` - Generate test files of various sizes
  - `ReportGenerator.kt` - Generate performance reports

## Development Commands

### Development Mode
```bash
./gradlew quarkusDev
```
This starts Quarkus in development mode with live reload. The application will be available at http://localhost:8080 with the Dev UI at http://localhost:8080/q/dev/.

### Build Commands
```bash
# Standard build
./gradlew build

# Build uber-jar
./gradlew build -Dquarkus.package.jar.type=uber-jar

# Native build (requires GraalVM)
./gradlew build -Dquarkus.native.enabled=true

# Native build with container
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

### Running Tests
```bash
# Run all tests
./gradlew test

# Run native tests (if built)
./gradlew testNative
```

### Running the Application
```bash
# After standard build
java -jar build/quarkus-app/quarkus-run.jar

# After uber-jar build
java -jar build/*-runner.jar

# Native executable (if built)
./build/fileupload-1.0-SNAPSHOT-runner
```

## Key Dependencies

The project uses multiple REST implementations to compare performance:
- **RESTEasy Classic**: Traditional blocking REST with Tomcat
- **RESTEasy Reactive**: Non-blocking REST with Netty
- **Vert.x**: For advanced reactive operations
- **Micrometer**: Metrics collection with Prometheus registry
- **SmallRye Mutiny**: Reactive programming library

## Configuration

- **Upload Directory**: Configurable via `quarkus.http.body.uploads-directory` (defaults to "uploads")
- **File Size Limits**: Managed by Quarkus HTTP configuration
- **Metrics**: Prometheus metrics available at `/q/metrics`
- **Health Checks**: Available at `/q/health`

## Performance Testing

### Available Test Scripts

1. **Comprehensive Bash Script**: `./test_uploads.sh`
   - Tests all upload strategies with files from 1MB to 2GB
   - Collects detailed metrics (CPU, memory, throughput)
   - Generates CSV reports with performance data

2. **Python Analysis Script**: `./analyze_results.py`
   - Analyzes test results and generates visualizations
   - Creates performance comparison charts
   - Generates markdown reports with recommendations

3. **Advanced Python Tester**: `./performance_test.py`
   - Automated test file creation and execution
   - Statistical analysis with multiple iterations
   - Professional charts and metrics

### Test Execution Workflow

```bash
# 1. Start the application
./gradlew quarkusDev

# 2. Run comprehensive tests (in another terminal)
./test_uploads.sh

# 3. Analyze results and generate charts
python3 analyze_results.py

# 4. View detailed results
cat test-results/detailed_results.csv
```

### Test File Sizes
- 1MB, 10MB, 100MB, 1GB, 2GB (if space available)
- Files are automatically created using `dd` command
- Results saved in `test-results/` directory

## Important Notes for Development

- The codebase is written in Kotlin with Java 17 compatibility
- All upload strategies save files to the `uploads/` directory (created automatically)
- Each upload endpoint returns performance metrics (upload time, throughput)
- The chunked upload implementation uses session management with `ConcurrentHashMap`
- Files are given UUID-based names to prevent conflicts
- The project includes comprehensive error handling and logging
- **Performance results show Upload Chunked is significantly faster for large files**

## Architecture Analysis Documents

The repository contains detailed analysis documents:
- `FileUploadStrategiesAnalysis.md` - Comprehensive analysis of upload strategies
- `STATE-OF-THE-ART.md` - State-of-the-art review of file upload techniques

These documents provide context for the architectural decisions and implementation approaches used in the codebase.