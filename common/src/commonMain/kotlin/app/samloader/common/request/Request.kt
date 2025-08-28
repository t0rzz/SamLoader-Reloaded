package app.samloader.common.request

/** Build FUS XML requests & logic check */
object RequestBuilder {
    /** Kotlin port of Python getlogiccheck: for each char in nonce, pick input[char.code & 0xF]. */
    fun getLogicCheck(input: String, nonce: String): String {
        require(input.length >= 16) { "Input too short for logic check" }
        val sb = StringBuilder()
        for (c in nonce) {
            sb.append(input[c.code and 0xF])
        }
        return sb.toString()
    }

    /** Choose effective DEVICE_LOCAL_CODE based on CSC tokens in version. */
    fun effectiveLocalCode(version: String, region: String): String {
        return try {
            val cscPart = version.split('/').getOrNull(1).orEmpty()
            val tokens = listOf("OXM", "OXA", "OWO", "OMC", "EUX")
            tokens.firstOrNull { cscPart.contains(it) } ?: region
        } catch (_: Throwable) {
            region
        }
    }

    /** Build BinaryInform XML as a String. */
    fun binaryInform(
        fwVersion: String,
        model: String,
        region: String,
        imei: String,
        nonce: String,
        useRegionLocalCode: Boolean = false,
    ): String {
        val localCode = if (useRegionLocalCode) region else effectiveLocalCode(fwVersion, region)
        val logic = getLogicCheck(fwVersion, nonce)
        fun tag(name: String, value: String) = "<${name}><Data>${value}</Data></${name}>"
        val body = buildString {
            append("<FUSBody>")
            append("<Put>")
            append(tag("ACCESS_MODE", "2"))
            append(tag("BINARY_NATURE", "1"))
            append(tag("CLIENT_PRODUCT", "Smart Switch"))
            append(tag("CLIENT_VERSION", "4.3.23123_1"))
            append(tag("DEVICE_IMEI_PUSH", imei))
            append(tag("DEVICE_FW_VERSION", fwVersion))
            append(tag("DEVICE_LOCAL_CODE", localCode))
            append(tag("DEVICE_MODEL_NAME", model))
            append(tag("LOGIC_CHECK", logic))
            append("</Put>")
            append("</FUSBody>")
        }
        return "<FUSMsg><FUSHdr><ProtoVer>1.0</ProtoVer></FUSHdr>${body}</FUSMsg>"
    }
}
