package app.samloader.common.prefs

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

actual fun provideSettings(): Settings {
    val node = Preferences.userRoot().node("dev.t0rzz.samloaderreloaded")
    return PreferencesSettings(node)
}
