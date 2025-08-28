package app.samloader.common.version

object VersionFetch {
    fun normalize(vercode: String): String {
        val parts = vercode.split('/')
        val mod = parts.toMutableList()
        if (mod.size == 3) mod.add(mod[0])
        if (mod.size >= 3 && mod[2].isEmpty()) mod[2] = mod[0]
        return mod.joinToString("/")
    }

    suspend fun getLatest(model: String, region: String): String {
        // TODO: Implement HTTP fetch of version.xml with timeout/retries
        throw NotImplementedError("getLatest not implemented")
    }
}