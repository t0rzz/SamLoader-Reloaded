package app.samloader.common.prefs

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings

// Linux native: use in-memory MapSettings (non-persistent) to satisfy compile/runtime without extra I/O deps.
actual fun createPlatformSettings(): Settings = MapSettings()
