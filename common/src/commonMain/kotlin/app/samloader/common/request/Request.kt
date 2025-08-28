package app.samloader.common.request

/** Placeholder for building FUS XML requests & logic check */
object RequestBuilder {
    fun getLogicCheck(input: String, nonce: String): String {
        // TODO: Implement logic check (maps characters of input using nonce)
        if (input.length < 16) error("Input too short for logic check")
        return "" // placeholder
    }

    fun effectiveLocalCode(version: String, region: String): String {
        // Prefer multi-CSC tokens in CSC part when present
        return try {
            val cscPart = version.split('/').getOrNull(1).orEmpty()
            val tokens = listOf("OXM", "OXA", "OWO", "OMC", "EUX")
            tokens.firstOrNull { cscPart.contains(it) } ?: region
        } catch (t: Throwable) {
            region
        }
    }
}
