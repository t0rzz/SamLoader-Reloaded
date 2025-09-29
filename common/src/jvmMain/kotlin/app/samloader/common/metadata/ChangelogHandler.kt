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
import io.ktor.client.request.header
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
            val outerResp = http.get(outerUrl) {
                header("User-Agent", "curl/7.87.0")
            }
            val outerHtml = try { outerResp.bodyAsText() } catch (_: Throwable) { "" }
            if (outerHtml.isBlank()) return null
            val iframeUrl = parseDocUrl(outerHtml)
            val bodyHtml = if (iframeUrl != null) {
                val pageResp = http.get(iframeUrl) {
                    header("User-Agent", "curl/7.87.0")
                }
                val body = try { pageResp.bodyAsText() } catch (_: Throwable) { "" }
                if (body.isNotBlank()) body else outerHtml
            } else {
                // No language selector found, parse the page we already have
                outerHtml
            }
            return parseChangelogs(bodyHtml)
        } catch (_: Throwable) {
            return null
        } finally {
            try { http.close() } catch (_: Throwable) {}
        }
    }

    private fun parseDocUrl(body: String): String? {
        val doc = Ksoup.parse(body)
        val selector = doc.selectFirst("#sel_lang_hidden") ?: return null
        // Options can be in two forms:
        // 1) Visible: <option value='../../.../eng.html'>English</option>
        // 2) Hidden:  <option value='EN'>../../.../eng.html</option>
        val options = selector.select("option")
        fun optionPath(opt: com.fleeksoft.ksoup.nodes.Element): String {
            val v = (opt.attr("value") ?: "").trim()
            val t = (opt.text() ?: "").trim()
            // Prefer attribute if it looks like a path; otherwise fallback to inner text
            val candidate = if (v.contains(".html", ignoreCase = true) || v.startsWith("../")) v else t
            return candidate
        }
        // Prefer English explicitly
        val eng = options.firstOrNull { opt ->
            val v = (opt.attr("value") ?: "").trim()
            val t = (opt.text() ?: "").trim()
            v.equals("EN", ignoreCase = true) ||
                    v.contains("/eng/", ignoreCase = true) || v.endsWith("eng.html", ignoreCase = true) ||
                    t.contains("/eng/", ignoreCase = true) || t.endsWith("eng.html", ignoreCase = true)
        }
        val chosen = eng ?: options.firstOrNull() ?: return null
        val raw = optionPath(chosen).trim()
        if (raw.isEmpty()) return null
        // Build absolute URL from relative paths
        val absolute = when {
            raw.startsWith("../../") -> raw.replaceFirst("../../", "$DOMAIN_URL/")
            raw.startsWith("/") -> "$DOMAIN_URL$raw"
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            else -> "$DOMAIN_URL/$raw"
        }
        return absolute
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
                val rawAndroidVer = valueAt(1)
                val androidVer = rawAndroidVer?.let { Regex("\\(([^)]+)\\)").find(it)?.groupValues?.getOrNull(1) ?: it }?.trim()
                val relDate = valueAt(2)
                val secPatch = if (cols.size >= 4) valueAt(3) else null

                // Notes are in the following sibling <div> (not a .row), typically containing a <span>
                var notes: String? = null
                var sib = row.nextElementSibling()
                var guard = 0
                while (sib != null && guard < 6) { // small guard to avoid long loops
                    if (sib.hasClass("row")) break // stop at next meta section
                    val span = sib.select("span").firstOrNull()
                    if (span != null) {
                        notes = span.html()
                        break
                    }
                    sib = sib.nextElementSibling()
                    guard++
                }

                if (!build.isNullOrBlank()) {
                    out[build] = Changelog(build, androidVer, relDate, secPatch, notes)
                }
                // Advance i to the next .row after the notes block
                i += 1
                continue
            }
            i += 1
        }
        return out
    }
}
