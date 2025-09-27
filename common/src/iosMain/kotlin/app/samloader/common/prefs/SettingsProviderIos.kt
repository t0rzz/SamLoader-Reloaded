package app.samloader.common.prefs

import com.russhwolf.settings.AppleSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

actual fun provideSettings(): Settings = AppleSettings(NSUserDefaults.standardUserDefaults())
