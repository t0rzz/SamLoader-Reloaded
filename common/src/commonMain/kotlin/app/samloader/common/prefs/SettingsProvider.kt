package app.samloader.common.prefs

import com.russhwolf.settings.Settings

// Expect platform-specific Settings provider.
expect fun provideSettings(): Settings
