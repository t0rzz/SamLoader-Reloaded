package app.samloader.common.network

import platform.Foundation.NSProcessInfo

actual fun isHttpDebug(): Boolean {
    val env = NSProcessInfo.processInfo.environment
    val raw = env.objectForKey("DUOFROST_HTTP_DEBUG") as? String
    return raw == "1" || (raw != null && raw.equals("true", ignoreCase = true))
}
