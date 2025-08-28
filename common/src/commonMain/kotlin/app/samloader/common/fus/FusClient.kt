package app.samloader.common.fus

import app.samloader.common.request.RequestBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable

/** KMP FUS client (scaffold). */
class FusClient {
    data class BinaryInfo(val path: String, val filename: String, val size: Long)

    private val client = HttpClient {
        install(Logging)
        install(HttpTimeout) {
            requestTimeoutMillis = 5000
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 5000
        }
        expectSuccess = true
    }

    private var authSignature: String = ""
    private var jsessionId: String = ""
    private var serverNonceEncrypted: String = ""
    private var serverNonceDecrypted: String = "" // TODO: decrypt using AES-CBC like Python

    /** Call NF_DownloadGenerateNonce.do and capture NONCE + session cookie. */
    suspend fun generateNonce(): String {
        val resp: HttpResponse = client.post("https://neofussvr.sslcs.cdngc.net/NF_DownloadGenerateNonce.do") {
            header("Authorization", fusAuthHeader(nonceEnc = serverNonceEncrypted, signature = authSignature))
            header("User-Agent", "Kies2.0_FUS")
            if (jsessionId.isNotEmpty()) header("Cookie", "JSESSIONID=$jsessionId")
        }
        // Capture headers
        val nonceHeader = resp.headers["NONCE"]
        if (nonceHeader != null) {
            serverNonceEncrypted = nonceHeader
            serverNonceDecrypted = decryptNonceStub(nonceHeader) // stub for now
            authSignature = getAuthSignatureStub(serverNonceDecrypted) // stub for now
        }
        // Capture cookie (if present)
        resp.setCookieFromResponse()?.let { jsessionId = it }
        return serverNonceDecrypted
    }

    /** Build and send BinaryInform, return parsed minimal info. */
    suspend fun binaryInform(version: String, model: String, region: String, imei: String, useRegionLocalCode: Boolean = false): BinaryInfo {
        val xml = RequestBuilder.binaryInform(version, model, region, imei, serverNonceDecrypted, useRegionLocalCode)
        val resp: String = client.post("https://neofussvr.sslcs.cdngc.net/NF_DownloadBinaryInform.do") {
            header("Authorization", fusAuthHeader(nonceEnc = serverNonceEncrypted, signature = authSignature))
            header("User-Agent", "Kies2.0_FUS")
            if (jsessionId.isNotEmpty()) header("Cookie", "JSESSIONID=$jsessionId")
            setBodyXml(xml)
        }.body()
        // Minimal string parsing (avoid XML dependency for now)
        fun match(tag: String): String? = Regex("<$tag><Data>(.*?)</Data></$tag>").find(resp)?.groupValues?.getOrNull(1)
        val fname = match("BINARY_NAME") ?: error("No firmware bundle in response")
        val size = match("BINARY_BYTE_SIZE")?.toLongOrNull() ?: error("Invalid size")
        val path = match("MODEL_PATH") ?: ""
        return BinaryInfo(path, fname, size)
    }

    /** Download from cloud; supports optional byte range. */
    suspend fun downloadBinary(pathAndName: String, start: Long = 0L, endInclusive: Long? = null): FlowChunked {
        val url = "http://cloud-neofussvr.samsungmobile.com/NF_DownloadBinaryForMass.do"
        val resp: HttpResponse = client.get(url) {
            parameter("file", pathAndName)
            header("Authorization", fusAuthHeader(nonceEnc = serverNonceEncrypted, signature = authSignature))
            header("User-Agent", "Kies2.0_FUS")
            if (jsessionId.isNotEmpty()) header("Cookie", "JSESSIONID=$jsessionId")
            if (start > 0 || endInclusive != null) {
                val range = if (endInclusive != null) "bytes=$start-$endInclusive" else "bytes=$start-"
                header("Range", range)
            }
        }
        val contentLength = resp.headers["Content-Length"]?.toLongOrNull() ?: -1L
        val channel = resp.bodyAsChannel()
        val seq = sequence {
            val buf = ByteArray(64 * 1024)
            while (!channel.isClosedForRead) {
                val n = channel.readAvailable(buf, 0, buf.size)
                if (n <= 0) break
                yield(buf.copyOf(n))
            }
        }
        return FlowChunked(contentLength, seq)
    }

    private fun fusAuthHeader(nonceEnc: String, signature: String): String =
        "FUS nonce=\"$nonceEnc\", signature=\"$signature\", nc=\"\", type=\"\", realm=\"\", newauth=\"1\""

    private fun decryptNonceStub(enc: String): String {
        // TODO: Implement AES-CBC decrypt per Python auth.py decryptnonce
        return enc // placeholder: not decrypted
    }

    private fun getAuthSignatureStub(nonce: String): String {
        // TODO: Implement AES-CBC encrypt per Python auth.py getauth
        return authSignature // placeholder
    }
}

class FlowChunked(val contentLength: Long, val chunks: Sequence<ByteArray>)

// --- Helpers (internal) ---
private fun HttpResponse.setCookieFromResponse(): String? {
    // Extract JSESSIONID=... from Set-Cookie if present
    val setCookie = headers["Set-Cookie"] ?: return null
    val m = Regex("JSESSIONID=([^;]+)").find(setCookie)
    return m?.groupValues?.getOrNull(1)
}

private fun io.ktor.client.request.HttpRequestBuilder.setBodyXml(xml: String) {
    io.ktor.http.content.TextContent(xml, io.ktor.http.ContentType.Text.Xml)
    setBody(xml)
}
