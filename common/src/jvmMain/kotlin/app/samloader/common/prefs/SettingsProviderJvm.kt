package app.samloader.common.prefs

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings

actual fun provideSettings(): Settings {
    // Use in-memory settings to be safe across Android and Desktop when consuming JVM variant.
    // Avoid java.util.prefs which is not supported on Android and can crash.
    return MapSettings()
}
