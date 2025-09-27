package app.samloader.common.prefs

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings

// Windows native: use in-memory MapSettings for simplicity (non-persistent).
actual fun provideSettings(): Settings = MapSettings()
