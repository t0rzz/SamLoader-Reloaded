package app.samloader.common.version

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import app.samloader.common.network.provideEngine

object VersionFetch {
    fun normalize(vercode: String): String {
        val parts = vercode.split('/')
        val mod = parts.toMutableList()
        if (mod.size == 3) mod.add(mod[0])
        if (mod.size >= 3 && mod[2].isEmpty()) mod[2] = mod[0]
        return mod.joinToString("/")
    }

    suspend fun getLatest(model: String, region: String): String {
        // KMP HTTP client using Ktor; simple retries with 5s timeout
        val client = HttpClient(provideEngine()) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            }
            install(Logging)
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
                    val url = "https://fota-cloud-dn.ospserver.net/firmware/${region}/${model}/version.xml"
                    val text: String = client.get(url) {
                        header("User-Agent", "curl/7.87.0")
                        header("Accept", "text/xml, application/xml;q=0.9, */*;q=0.8")
                    }.body()
                    // More robust XML extraction similar to Python's structure.
                    val regex = Regex("(?s)<latest>\\s*([^<]+)\\s*</latest>") // DOTALL, trim inside
                    val verRaw = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
                    if (verRaw.isNullOrEmpty()) {
                        // If a <latest> tag exists but is empty/self-closing, treat as 'no latest' (align with Python)
                        val emptyLatestRegex = Regex("(?s)<latest(\n|\r|\t|\s)*(/>|>\s*</latest>)")
                        val hasEmptyLatest = emptyLatestRegex.containsMatchIn(text)
                        if (hasEmptyLatest) {
                            error("No latest firmware available")
                        }
                        // Try a more specific path in case of nested tags/newlines
                        val regex2 = Regex("(?s)<firmware>.*?<version>.*?<latest>\\s*([^<]+)\\s*</latest>")
                        val alt = regex2.find(text)?.groupValues?.getOrNull(1)?.trim()
                        if (alt.isNullOrEmpty()) {
                            throw IllegalStateException("Parse error: <latest> tag not found in version.xml; sample=" + text.take(200).replace("\n"," ").replace("\r"," "))
                        } else {
                            return normalize(alt)
                        }
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