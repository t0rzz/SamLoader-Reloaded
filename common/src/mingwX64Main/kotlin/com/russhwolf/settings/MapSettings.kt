package com.russhwolf.settings

/**
 * Minimal in-memory Settings implementation for Kotlin/Native mingwX64.
 */
class MapSettings : Settings {
    private val data: MutableMap<String, String> = mutableMapOf()

    override fun getString(key: String, defaultValue: String): String = data[key] ?: defaultValue
    override fun putString(key: String, value: String) { data[key] = value }

    override fun getInt(key: String, defaultValue: Int): Int = data[key]?.toIntOrNull() ?: defaultValue
    override fun putInt(key: String, value: Int) { data[key] = value.toString() }

    override fun getLong(key: String, defaultValue: Long): Long = data[key]?.toLongOrNull() ?: defaultValue
    override fun putLong(key: String, value: Long) { data[key] = value.toString() }

    override fun getFloat(key: String, defaultValue: Float): Float = data[key]?.toFloatOrNull() ?: defaultValue
    override fun putFloat(key: String, value: Float) { data[key] = value.toString() }

    override fun getDouble(key: String, defaultValue: Double): Double = data[key]?.toDoubleOrNull() ?: defaultValue
    override fun putDouble(key: String, value: Double) { data[key] = value.toString() }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = when (data[key]) {
        "true" -> true
        "false" -> false
        else -> defaultValue
    }
    override fun putBoolean(key: String, value: Boolean) { data[key] = value.toString() }

    override fun remove(key: String) { data.remove(key) }
    override fun hasKey(key: String): Boolean = data.containsKey(key)
    override fun clear() { data.clear() }

    override fun keys(): Set<String> = data.keys

    override fun getStringOrNull(key: String): String? = data[key]
    override fun getIntOrNull(key: String): Int? = data[key]?.toIntOrNull()
    override fun getLongOrNull(key: String): Long? = data[key]?.toLongOrNull()
    override fun getFloatOrNull(key: String): Float? = data[key]?.toFloatOrNull()
    override fun getDoubleOrNull(key: String): Double? = data[key]?.toDoubleOrNull()
    override fun getBooleanOrNull(key: String): Boolean? = when (data[key]) {
        "true" -> true
        "false" -> false
        else -> null
    }

    override fun putStringOrNull(key: String, value: String?) {
        if (value == null) data.remove(key) else data[key] = value
    }
}