package app.samloader.common.network

actual fun isHttpDebug(): Boolean {
    // Enable by setting system property or env var
    val prop = System.getProperty("DUOFROST_HTTP_DEBUG")
    val env = System.getenv("DUOFROST_HTTP_DEBUG")
    return (prop == "1" || prop?.equals("true", ignoreCase = true) == true
            || env == "1" || env?.equals("true", ignoreCase = true) == true)
}
