package eu.x45.listen45

import android.app.Application
import eu.x45.listen45.repository.SettingsRepository

class Listen45 : Application() {
    override fun onCreate() {
        super.onCreate()

        // Apply current settings from SettingsRepository.
        SettingsRepository(this).apply()
    }
}
