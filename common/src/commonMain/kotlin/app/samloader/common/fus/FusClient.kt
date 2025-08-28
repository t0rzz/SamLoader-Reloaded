package app.samloader.common.fus

/** Placeholder FUS client API to be implemented. */
class FusClient {
    data class BinaryInfo(val path: String, val filename: String, val size: Long)

    suspend fun generateNonce(): String {
        // TODO: Implement FUS NONCE generation call
        return ""
    }

    suspend fun binaryInform(version: String, model: String, region: String, imei: String, nonce: String): BinaryInfo {
        // TODO: Implement BinaryInform parsing and result
        throw NotImplementedError("binaryInform not implemented")
    }

    suspend fun downloadBinary(pathAndName: String, start: Long = 0L, endInclusive: Long? = null): FlowChunked {
        // TODO: Implement ranged download as a flow/sequence of byte arrays
        throw NotImplementedError("downloadBinary not implemented")
    }
}

class FlowChunked(val contentLength: Long, val chunks: Sequence<ByteArray>)
