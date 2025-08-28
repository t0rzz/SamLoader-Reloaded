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

    // AES/MD5 placeholders
    fun v2Key(version: String, model: String, region: String): ByteArray {
        // TODO: Implement MD5(region + ':' + model + ':' + version)
        return ByteArray(16)
    }

    fun v4KeyFromServer(fwVersion: String, logicVal: String): ByteArray {
        // TODO: Implement MD5(logicCheck(fwVersion, logicVal))
        return ByteArray(16)
    }
}