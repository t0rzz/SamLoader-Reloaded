package app.samloader.common.version

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import app.samloader.common.network.provideEngine

object VersionFetch {
    data class RequestShape(val url: String, val headers: Map<String, String>)

    fun buildRequest(model: String, region: String): RequestShape {
        val url = "https://fota-cloud-dn.ospserver.net/firmware/${region}/${model}/version.xml"
        val headers = mapOf(
            // Match Python exactly
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

    suspend fun getLatest(model: String, region: String): String {
        val client = HttpClient(provideEngine()) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            }
            if (app.samloader.common.network.isHttpDebug()) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            // Keep logs at DEBUG: on Android they appear in Logcat via stdout; redact sensitive headers
                            println(message)
                        }
                    }
                    level = LogLevel.ALL
                    redactHeaderNames = setOf("Authorization", "Cookie", "Set-Cookie")
                }
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
                    // XML extraction
                    val regex = Regex("""(?s)<latest>\s*([^<]+)\s*</latest>""")
                    val verRaw = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
                    if (verRaw.isNullOrEmpty()) {
                        val emptyLatestRegex = Regex("""(?s)<latest([\n\r\t\s])*(/\>|>\s*</latest>)""")
                        val hasEmptyLatest = emptyLatestRegex.containsMatchIn(text)
                        if (hasEmptyLatest) error("No latest firmware available")
                        val regex2 = Regex("""(?s)<firmware>.*?<version>.*?<latest>\s*([^<]+)\s*</latest>""")
                        val alt = regex2.find(text)?.groupValues?.getOrNull(1)?.trim()
                        if (alt.isNullOrEmpty()) {
                            throw IllegalStateException("Parse error: <latest> tag not found in version.xml; sample=" + text.take(200).replace("\n"," ").replace("\r"," "))
                        } else return normalize(alt)
                    }
                    return normalize(verRaw)
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