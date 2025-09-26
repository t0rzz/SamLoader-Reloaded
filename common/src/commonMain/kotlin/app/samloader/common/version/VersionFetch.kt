package app.samloader.common.version

// import android.util.Log // REMOVED
import app.samloader.common.logging.AppLogger // ADDED for expect/actual logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import app.samloader.common.network.provideEngine
import kotlinx.serialization.json.Json

object VersionFetch {
    data class RequestShape(val url: String, val headers: Map<String, String>)

    // Instantiate the common logger
    private val appLogger = AppLogger("SamloaderFusHttp")

    fun buildRequest(model: String, region: String): RequestShape {
        val url = "https://fota-cloud-dn.ospserver.net/firmware/${region}/${model}/version.xml"
        val headers = mapOf(
            "User-Agent" to "curl/7.87.0"
        )
        return RequestShape(url, headers)
    }

    fun normalize(vercode: String): String {
        val parts = vercode.split('/')
        val mod = parts.toMutableList()
        if (mod.size == 3) mod.add(mod[0])
        if (mod.size >= 3 && mod[2].isEmpty()) mod[2] = mod[0]
        return mod.joinToString("/")
    }

    fun extractLatest(text: String): String {
        val regex = Regex("""(?s)<latest(?:\s+[^>]*)?>\s*([^<]+)\s*</latest>""")
        val direct = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!direct.isNullOrEmpty()) return normalize(direct)

        val emptyLatestRegex = Regex("""(?s)<latest(?:\s+[^>]*)?\s*(?:/>\s*|>\s*</latest>)""")
        if (emptyLatestRegex.containsMatchIn(text)) error("No latest firmware available")

        val regex2 = Regex("""(?s)<firmware>.*?<version>.*?<latest(?:\s+[^>]*)?>\s*([^<]+)\s*</latest>""")
        val alt = regex2.find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!alt.isNullOrEmpty()) return normalize(alt)

        throw IllegalStateException(
            "Parse error: <latest> tag not found in version.xml; sample=" +
                    text.take(200).replace("\n", " ").replace("\r", " ")
        )
    }

    suspend fun getLatest(model: String, region: String): String {
        val client = HttpClient(provideEngine()) {
            install(ContentNegotiation) {
                Json { ignoreUnknownKeys = true }
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        // Forward to platform logger (debug level)
                        appLogger.d("[SamloaderVersionHttp] SAMLOADER_VERSION_HTTP_TRACE: $message")
                    }
                }
                level = LogLevel.ALL
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
                connectTimeoutMillis = 5000
                socketTimeoutMillis = 5000
            }
            expectSuccess = true
        }
        var last: Throwable? = null
        try {
            repeat(5) { attempt ->
                try {
                    val req = buildRequest(model, region)
                    val text: String = client.get(req.url) {
                        req.headers.forEach { (k, v) -> header(k, v) }
                    }.body()
                    val parsed = extractLatest(text)
                    return parsed
                } catch (t: Throwable) {
                    last = t
                    if (attempt < 4) return@repeat else throw t
                }
            }
            throw last ?: IllegalStateException("Unknown error while fetching latest version")
        } finally {
            try { client.close() } catch (_: Throwable) {}
        }
    }
}