package app.samloader.common.version

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
        val client = io.ktor.client.HttpClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            }
            install(io.ktor.client.plugins.logging.Logging)
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 5000
                connectTimeoutMillis = 5000
                socketTimeoutMillis = 5000
            }
            expectSuccess = true
        }
        var last: Throwable? = null
        repeat(5) { attempt ->
            try {
                val url = "https://fota-cloud-dn.ospserver.net/firmware/${region}/${model}/version.xml"
                val text: String = client.get(url) {
                    header("User-Agent", "curl/7.87.0")
                }.body()
                // very small XML parsing: extract <latest>…</latest>
                val regex = Regex("<latest>(.*?)</latest>")
                val match = regex.find(text)
                val verRaw = match?.groupValues?.getOrNull(1) ?: error("No latest firmware available")
                client.close()
                return normalize(verRaw)
            } catch (t: Throwable) {
                last = t
                if (attempt < 4) return@repeat
            }
        }
        throw last ?: IllegalStateException("Failed to fetch latest version")
    }
}