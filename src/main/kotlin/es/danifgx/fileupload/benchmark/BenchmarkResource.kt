package es.danifgx.fileupload.benchmark

import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import java.io.File
import org.eclipse.microprofile.config.inject.ConfigProperty

@Path("/benchmark")
class BenchmarkResource {
    private val logger = Logger.getLogger(BenchmarkResource::class.java)
    
    @Inject
    lateinit var benchmarkService: BenchmarkService
    
    @ConfigProperty(name = "benchmark.output-directory", defaultValue = "benchmark-results")
    lateinit var outputDirectory: String
    
    @POST
    @Path("/run")
    @Produces(MediaType.APPLICATION_JSON)
    fun runBenchmark(): Response {
        logger.info("Iniciando benchmark manualmente")
        val result = benchmarkService.manualBenchmark()
        return Response.ok(result).build()
    }
    
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBenchmarkStatus(): Response {
        logger.info("Consultando estado del benchmark")
        
        val outputDir = File(outputDirectory)
        if (!outputDir.exists()) {
            return Response.ok(mapOf(
                "totalRuns" to 0,
                "latestRuns" to emptyList<Any>()
            )).build()
        }
        
        val directories = outputDir.listFiles()
        
        val results = directories?.filter { it.isDirectory }
            ?.map { dir ->
                val reportFile = File(dir, "benchmark_report.md")
                val resultsFile = File(dir, "benchmark_results.json")
                
                mapOf(
                    "name" to dir.name,
                    "date" to java.util.Date(dir.lastModified()),
                    "hasReport" to reportFile.exists(),
                    "hasResults" to resultsFile.exists()
                )
            }?.sortedByDescending { it["date"] as java.util.Date }
            ?: emptyList<Map<String, Any>>()
        
        return Response.ok(mapOf(
            "totalRuns" to results.size,
            "latestRuns" to results
        )).build()
    }
    
    @GET
    @Path("/report/latest")
    @Produces(MediaType.TEXT_PLAIN)
    fun getLatestReport(): Response {
        val outputDir = File(outputDirectory)
        if (!outputDir.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("No se encontraron informes de benchmark")
                .build()
        }
        
        val directories = outputDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyArray()
            
        if (directories.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("No se encontraron informes de benchmark")
                .build()
        }
        
        val latestReportFile = File(directories[0], "benchmark_report.md")
        if (!latestReportFile.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("No se encontró el informe más reciente")
                .build()
        }
        
        return Response.ok(latestReportFile.readText())
            .header("Content-Disposition", "attachment; filename=\"${latestReportFile.name}\"")
            .build()
    }
}
