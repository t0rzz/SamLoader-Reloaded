package app.samloader.common.data

object Regions {
    private val fallback = mapOf(
        "BTU" to "United Kingdom, no brand",
        "DBT" to "Germany, no brand",
        "ITV" to "Italy, no brand",
        "XEF" to "France, no brand"
        // TODO: Extend or load remotely, cache locally
    )

    suspend fun getRegions(): Map<String, String> {
        // TODO: Try remote -> cache -> packaged -> fallback
        return fallback
    }
}