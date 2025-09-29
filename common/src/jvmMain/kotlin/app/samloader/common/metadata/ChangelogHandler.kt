package app.samloader.common.metadata

import app.samloader.common.network.provideEngine
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

actual object ChangelogHandler {
    private const val DOMAIN_URL = "https://doc.samsungmobile.com:443"

    private fun client(): HttpClient = HttpClient(provideEngine()) {
        install(Logging) {
            logger = object : Logger { override fun log(message: String) {} }
            level = LogLevel.NONE
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 5000
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 5000
        }
        expectSuccess = false
    }

    actual suspend fun getChangelog(device: String, region: String): Map<String, Changelog>? {
        val http = client()
        try {
            val outerUrl = "$DOMAIN_URL/$device/$region/doc.html"
            val outerResp = http.get(outerUrl)
            if (!outerResp.status.isSuccess()) return null
            val outerHtml = outerResp.bodyAsText()
            val iframeUrl = parseDocUrl(outerHtml) ?: return null
            val pageResp = http.get(iframeUrl)
            if (!pageResp.status.isSuccess()) return null
            val body = pageResp.bodyAsText()
            return parseChangelogs(body)
        } catch (_: Throwable) {
            return null
        } finally {
            try { http.close() } catch (_: Throwable) {}
        }
    }

    private fun parseDocUrl(body: String): String? {
        val doc = Ksoup.parse(body)
        val selector = doc.selectFirst("#sel_lang_hidden") ?: return null
        // Prefer English entry explicitly (path contains "/eng/")
        val options = selector.children()
        val eng = options.firstOrNull { it.text().contains("/eng/", ignoreCase = true) }
        val chosen = eng ?: options.firstOrNull() ?: return null
        val relative = chosen.text()
        return if (relative.isNullOrBlank()) null else relative.replace("../../", "$DOMAIN_URL/")
    }

    private fun parseChangelogs(body: String): Map<String, Changelog> {
        val doc = try { Ksoup.parse(body) } catch (_: Throwable) { return emptyMap() }
        val rows = doc.select(".container .row").toList()
        val out = LinkedHashMap<String, Changelog>()
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            val cols = row.children()
            // Identify a metadata row with 3 or 4 columns that contain key:value pairs
            val isMetaRow = (cols.size in 3..4) && cols.all { it.text().contains(":") }
            if (isMetaRow) {
                fun valueAt(idx: Int): String? = cols.getOrNull(idx)?.text()?.substringAfter(":")?.trim()?.ifBlank { null }
                val build = valueAt(0)
                val androidVer = valueAt(1)
                val relDate = valueAt(2)
                val secPatch = if (cols.size >= 4) valueAt(3) else null
                // Notes are typically in the next row (rich HTML)
                val next = rows.getOrNull(i + 1)
                val notes = next?.children()?.firstOrNull()?.childNodes()?.joinToString(separator = "") { it.outerHtml() }
                if (!build.isNullOrBlank()) {
                    out[build] = Changelog(build, androidVer, relDate, secPatch, notes)
                }
                i += 2
                continue
            }
            i += 1
        }
        return out
    }
}
