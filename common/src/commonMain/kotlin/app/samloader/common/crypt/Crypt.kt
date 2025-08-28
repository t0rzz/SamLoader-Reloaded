package app.samloader.common.crypt

object Crypt {
    fun luhnChecksum(body: String): Int {
        var sum = 0
        val parity = (body.length + 1) % 2
        body.forEachIndexed { idx, c ->
            var d = c.digitToInt()
            if (idx % 2 == parity) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return (10 - (sum % 10)) % 10
    }

    fun fillImeiFromPrefix(prefix: String): String {
        val base = prefix.padEnd(14, '0').take(14)
        val checksum = luhnChecksum(base)
        return base + checksum.toString()
    }

    // Keys using Auth helpers
    fun v2Key(version: String, model: String, region: String): ByteArray =
        app.samloader.common.auth.Auth.v2Key(version, model, region)

    fun v4KeyFromServer(fwVersion: String, logicVal: String): ByteArray =
        app.samloader.common.auth.Auth.v4KeyFromServer(fwVersion, logicVal)
}

// Streaming decrypt with AES-ECB (PKCS#7) and progress callbacks
typealias ReadChunk = () -> ByteArray?
typealias WriteChunk = (ByteArray) -> Unit

data class DecryptProgress(val total: Long, var done: Long)

private fun pkcs7Unpad(data: ByteArray): ByteArray {
    if (data.isEmpty()) return data
    val pad = data.last().toInt() and 0xFF
    if (pad <= 0 || pad > 16) return data
    return data.copyOf(data.size - pad)
}

fun decryptProgress(read: ReadChunk, write: WriteChunk, key: ByteArray, totalLen: Long, onProgress: (Int) -> Unit = {}) {
    require(totalLen % 16L == 0L) { "invalid input block size" }
    var processed = 0L
    val blockSize = 4096
    while (processed < totalLen) {
        val enc = read() ?: break
        if (enc.isEmpty()) break
        // Decrypt ECB block
        val dec = korlibs.crypto.AES.decryptEcb(enc, key)
        val isLast = processed + enc.size >= totalLen
        if (isLast) {
            val unpadded = pkcs7Unpad(dec)
            write(unpadded)
            processed = totalLen
            onProgress(enc.size)
            break
        } else {
            write(dec)
            processed += enc.size
            onProgress(enc.size)
        }
    }
}
