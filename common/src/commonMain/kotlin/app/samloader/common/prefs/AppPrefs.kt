package app.samloader.common.prefs

import com.russhwolf.settings.Settings

object AppPrefs {
    private val settings: Settings = provideSettings()

    private const val KEY_THREADS = "prefs.default_threads"
    private const val KEY_AUTO_DEC = "prefs.auto_decrypt"

    // Legacy single key kept for backward compatibility
    private const val KEY_ANDROID_TREE_URI = "prefs.android.tree_uri"

    // New explicit keys
    private const val KEY_ANDROID_OUT_TREE_URI = "prefs.android.out_tree_uri"
    private const val KEY_ANDROID_DEC_TREE_URI = "prefs.android.dec_tree_uri"

    fun getDefaultThreads(): Int = settings.getInt(KEY_THREADS, 1).coerceIn(1, 10)
    fun setDefaultThreads(value: Int) {
        settings.putInt(KEY_THREADS, value.coerceIn(1, 10))
    }

    fun getAutoDecrypt(): Boolean = settings.getBoolean(KEY_AUTO_DEC, false)
    fun setAutoDecrypt(value: Boolean) {
        settings.putBoolean(KEY_AUTO_DEC, value)
    }

    // Android-only: persisted SAF tree URIs (stored as a string). Safe to read/write on other platforms (no-op usage).
    // Backward compat: if new OUT key empty, fall back to old KEY_ANDROID_TREE_URI
    fun getAndroidOutTreeUri(): String {
        val v = settings.getString(KEY_ANDROID_OUT_TREE_URI, "")
        return if (v.isNotBlank()) v else settings.getString(KEY_ANDROID_TREE_URI, "")
    }
    fun setAndroidOutTreeUri(treeUri: String) {
        settings.putString(KEY_ANDROID_OUT_TREE_URI, treeUri)
    }
    fun clearAndroidOutTreeUri() {
        settings.remove(KEY_ANDROID_OUT_TREE_URI)
    }

    fun getAndroidDecTreeUri(): String = settings.getString(KEY_ANDROID_DEC_TREE_URI, "")
    fun setAndroidDecTreeUri(treeUri: String) {
        settings.putString(KEY_ANDROID_DEC_TREE_URI, treeUri)
    }
    fun clearAndroidDecTreeUri() {
        settings.remove(KEY_ANDROID_DEC_TREE_URI)
    }

}
