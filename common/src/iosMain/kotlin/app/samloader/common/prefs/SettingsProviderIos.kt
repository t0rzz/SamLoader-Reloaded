package app.samloader.common.prefs

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings

actual fun provideSettings(): Settings = MapSettings()
