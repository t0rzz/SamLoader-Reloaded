package app.samloader.common.fus

import app.samloader.common.logging.AppLogger // Crucial import
import app.samloader.common.network.provideEngine
import app.samloader.common.request.RequestBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** KMP FUS client (scaffold). */
class FusClient {
    // FIX 1: Provide the tag to the AppLogger constructor
    private val appLogger = AppLogger("SamloaderFusHttp")

    data class BinaryInfo(val path: String, val filename: String, val size: Long)

    private val client = HttpClient(provideEngine()) {
        // Line 22, where the error is reported
        install(Logging) {
            logger = object : Logger { // Line 23 (approx.)
                override fun log(message: String) {
                    // FIX 2: Use the '.d()' method and pass only the message
                    // The tag is now part of the appLogger instance.
                    appLogger.d("SAMLOADER_FUS_HTTP_TRACE: $message")
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
            serverNonceDecrypted = app.samloader.common.auth.Auth.decryptNonceBase64(nonceHeader)
            authSignature = app.samloader.common.auth.Auth.getAuthSignature(serverNonceDecrypted)
        }
        // Capture cookie (if present)
        resp.setCookieFromResponse()?.let { jsessionId = it }
        return serverNonceDecrypted
    }

    /** Build and send BinaryInform, with multi-CSC and latest-fallback handling. */
    suspend fun binaryInform(version: String, model: String, region: String, imei: String, useRegionLocalCode: Boolean = false): BinaryInfo {
        suspend fun call(versionNorm: String, forceRegionLocal: Boolean): String {
            val xml = RequestBuilder.binaryInform(versionNorm, model, region, imei, serverNonceDecrypted, forceRegionLocal)
            return client.post("https://neofussvr.sslcs.cdngc.net/NF_DownloadBinaryInform.do") {
                header("Authorization", fusAuthHeader(nonceEnc = serverNonceEncrypted, signature = authSignature))
                header("User-Agent", "Kies2.0_FUS")
                if (jsessionId.isNotEmpty()) header("Cookie", "JSESSIONID=$jsessionId")
                setBodyXml(xml)
            }.body()
        }
        suspend fun tryInform(versionNorm: String): String {
            val resp1 = call(versionNorm, false)
            val status1 = Regex("<Status>(\\d+)</Status>").find(resp1)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            if (status1 == 200) return resp1
            val effective = RequestBuilder.effectiveLocalCode(versionNorm, region)
            if (effective != region) {
                val resp2 = call(versionNorm, true)
                val status2 = Regex("<Status>(\\d+)</Status>").find(resp2)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                if (status2 == 200) return resp2
                error("BinaryInform failed: $status1 (local_code=$effective) and $status2 (local_code=$region)")
            } else {
                error("BinaryInform failed: $status1")
            }
        }
        // Normalize version: ensure 4-part
        val versionNorm = app.samloader.common.version.VersionFetch.normalize(version)
        val resp = try {
            tryInform(versionNorm)
        } catch (first: Throwable) {
            // Fallback: try latest available build
            val latest = app.samloader.common.version.VersionFetch.getLatest(model, region)
            try {
                tryInform(latest)
            } catch (_: Throwable) {
                throw first
            }
        }
        fun match(tag: String): String? = Regex("<$tag><Data>(.*?)</Data></$tag>").find(resp)?.groupValues?.getOrNull(1)
        val fname = match("BINARY_NAME") ?: error("No firmware bundle in response")
        val size = match("BINARY_BYTE_SIZE")?.toLongOrNull() ?: error("Invalid size")
        val path = match("MODEL_PATH") ?: ""
        return BinaryInfo(path, fname, size)
    }

    /** Retrieve V4 decrypt key by calling BinaryInform and using LOGIC_VALUE_FACTORY and LATEST_FW_VERSION. */
    suspend fun getV4Key(version: String, model: String, region: String, imei: String): ByteArray {
        suspend fun call(versionNorm: String, forceRegionLocal: Boolean): String {
            val xml = RequestBuilder.binaryInform(versionNorm, model, region, imei, serverNonceDecrypted, forceRegionLocal)
            return client.post("https://neofussvr.sslcs.cdngc.net/NF_DownloadBinaryInform.do") {
                header("Authorization", fusAuthHeader(nonceEnc = serverNonceEncrypted, signature = authSignature))
                header("User-Agent", "Kies2.0_FUS")
                if (jsessionId.isNotEmpty()) header("Cookie", "JSESSIONID=$jsessionId")
                setBodyXml(xml)
            }.body()
        }
        suspend fun tryInform(versionNorm: String): String {
            val resp1 = call(versionNorm, false)
            val status1 = Regex("<Status>(\\d+)</Status>").find(resp1)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            if (status1 == 200) return resp1
            val effective = RequestBuilder.effectiveLocalCode(versionNorm, region)
            if (effective != region) {
                val resp2 = call(versionNorm, true)
                val status2 = Regex("<Status>(\\d+)</Status>").find(resp2)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                if (status2 == 200) return resp2
                error("BinaryInform failed: $status1 (local_code=$effective) and $status2 (local_code=$region)")
            } else {
                error("BinaryInform failed: $status1")
            }
        }
        val versionNorm = app.samloader.common.version.VersionFetch.normalize(version)
        val resp = try {
            tryInform(versionNorm)
        } catch (first: Throwable) {
            val latest = app.samloader.common.version.VersionFetch.getLatest(model, region)
            try { tryInform(latest) } catch (_: Throwable) { throw first }
        }
        fun match(tag: String): String? = Regex("<$tag><Data>(.*?)</Data></$tag>").find(resp)?.groupValues?.getOrNull(1)
        val fwver = match("LATEST_FW_VERSION") ?: version
        val logicVal = match("LOGIC_VALUE_FACTORY") ?: error("Missing LOGIC_VALUE_FACTORY")
        return app.samloader.common.auth.Auth.v4KeyFromServer(fwver, logicVal)
    }

    /** Download from cloud; supports optional byte range. */
    suspend fun downloadBinary(pathAndName: String, start: Long = 0L, endInclusive: Long? = null): FlowChunked {
        // Build the full URL with an unencoded file= query, to match Python behavior
        val base = "http://cloud-neofussvr.samsungmobile.com/NF_DownloadBinaryForMass.do"
        val fullUrl = "$base?file=$pathAndName"
        // Trace outgoing request (without sensitive headers)
        appLogger.d("SAMLOADER_FUS_HTTP_TRACE: REQUEST: $fullUrl\nMETHOD: GET")
        val resp: HttpResponse = client.get(fullUrl) {
            // Relax timeouts for large streaming downloads only (per-request override)
            timeout {
                requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            }

            header("Authorization", fusAuthHeader(nonceEnc = serverNonceEncrypted, signature = authSignature))
            header("User-Agent", "Kies2.0_FUS")
            header("Accept-Encoding", "identity")
            if (jsessionId.isNotEmpty()) header("Cookie", "JSESSIONID=$jsessionId")
            if (start > 0 || endInclusive != null) {
                val range = if (endInclusive != null) "bytes=$start-$endInclusive" else "bytes=$start-"
                header("Range", range)
            }
        }
        val contentLength = resp.headers["Content-Length"]?.toLongOrNull() ?: -1L
        val channel = resp.bodyAsChannel()
        val flowChunks = flow {
            val buf = ByteArray(64 * 1024)
            while (!channel.isClosedForRead) {
                val n = channel.readAvailable(buf, 0, buf.size)
                if (n <= 0) break
                emit(buf.copyOf(n))
            }
        }
        return FlowChunked(contentLength, flowChunks)
    }

    private fun fusAuthHeader(nonceEnc: String, signature: String): String =
        "FUS nonce=\"$nonceEnc\", signature=\"$signature\", nc=\"\", type=\"\", realm=\"\", newauth=\"1\""

}

class FlowChunked(val contentLength: Long, val chunks: Flow<ByteArray>)

// --- Helpers (internal) ---
private fun HttpResponse.setCookieFromResponse(): String? {
    // Extract JSESSIONID=... from Set-Cookie if present
    val setCookie = headers["Set-Cookie"] ?: return null
    val m = Regex("JSESSIONID=([^;]+)").find(setCookie)
    return m?.groupValues?.getOrNull(1)
}

private fun io.ktor.client.request.HttpRequestBuilder.setBodyXml(xml: String) {
    header("Content-Type", "text/xml")
    setBody(xml)
}
