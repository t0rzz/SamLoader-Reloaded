package app.samloader.common.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.samloader.common.network.provideEngine

object Regions {
    private const val REMOTE_URL = "https://raw.githubusercontent.com/t0rzz/SamLoader-Reloaded/master/samloader/data/regions.json"

    private val fallback = mapOf(
        "BTU" to "United Kingdom, no brand",
        "DBT" to "Germany, no brand",
        "ITV" to "Italy, no brand",
        "XEF" to "France, no brand"
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class RegionEntry(val code: String? = null, val name: String? = null)

    suspend fun getRegions(): Map<String, String> {
        // Remote (preferred)
        val client = HttpClient(provideEngine()) {
            install(Logging)
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
                connectTimeoutMillis = 5000
                socketTimeoutMillis = 5000
            }
            expectSuccess = true
        }
        try {
            // Load as raw text and parse as JSON object mapping codes to names
            val text: String = client.get(REMOTE_URL).body()
            // Try to parse into Map<String, String>
            val parsed = json.decodeFromString<Map<String, String>>(text)
            if (parsed.isNotEmpty()) return parsed
        } catch (_: Throwable) {
            // ignore, fallback below
        } finally {
            client.close()
        }
        // TODO: packaged/cached dataset can be added later if needed
        return fallback
    }
}