package app.samloader.common.prefs

import com.russhwolf.settings.Settings

/**
 * Very small persistent history store using Multiplatform Settings.
 * Items are stored as a newline-separated list under a single key.
 */
object AppHistory {
    private val settings: Settings = provideSettings()

    private const val KEY = "history.items"
    private const val MAX_ITEMS = 200

    fun getAll(): List<String> {
        val raw = settings.getString(KEY, "")
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    fun setAll(items: List<String>) {
        val trimmed = items.take(MAX_ITEMS)
        settings.putString(KEY, trimmed.joinToString("\n"))
    }

    fun add(item: String) {
        val list = getAll().toMutableList()
        list.add(0, item.replace('\n', ' ')) // newest first; sanitize newlines
        setAll(list)
    }

    fun removeFirst() {
        val list = getAll().toMutableList()
        if (list.isNotEmpty()) {
            list.removeAt(0)
            setAll(list)
        }
    }

    fun clear() {
        settings.remove(KEY)
    }
}
