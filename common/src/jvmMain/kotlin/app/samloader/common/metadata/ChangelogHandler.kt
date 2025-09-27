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
        val selector = doc.selectFirst("#sel_lang_hidden")
        val option = selector?.children()?.firstOrNull() ?: return null
        val relative = option.text()
        return if (relative.isNullOrBlank()) null else relative.replace("../../", "$DOMAIN_URL/")
    }

    private fun parseChangelogs(body: String): Map<String, Changelog> {
        val doc = try { Ksoup.parse(body) } catch (_: Throwable) { return emptyMap() }
        val container = doc.selectFirst(".container") ?: return emptyMap()
        val divs = container.children().toMutableList()
        divs.removeAll { it.tagName() == "hr" }
        val out = LinkedHashMap<String, Changelog>()
        var i = 3
        while (i < divs.size) {
            val row = divs[i].children()
            val log = divs.getOrNull(i + 1)
            val (build, androidVer, relDate, secPatch) = when (row.count()) {
                4 -> listOf(
                    row.getOrNull(0)?.text()?.substringAfter(":")?.trim(),
                    row.getOrNull(1)?.text()?.substringAfter(":")?.trim(),
                    row.getOrNull(2)?.text()?.substringAfter(":")?.trim(),
                    row.getOrNull(3)?.text()?.substringAfter(":")?.trim(),
                )
                3 -> listOf(
                    row.getOrNull(0)?.text()?.substringAfter(":")?.trim(),
                    row.getOrNull(1)?.text()?.substringAfter(":")?.trim(),
                    row.getOrNull(2)?.text()?.substringAfter(":")?.trim(),
                    null,
                )
                else -> listOf(null, null, null, null)
            }
            val notes = log?.children()?.getOrNull(0)?.childNodes()?.joinToString(separator = "") { it.outerHtml() }
            val fw = build
            if (!fw.isNullOrBlank()) {
                out[fw] = Changelog(fw, androidVer, relDate, secPatch, notes)
            }
            i += 2
        }
        return out
    }
}
