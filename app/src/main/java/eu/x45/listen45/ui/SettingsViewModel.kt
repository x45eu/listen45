package eu.x45.listen45.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.x45.listen45.repository.HistoryRepository
import eu.x45.listen45.repository.ImportExportRepository
import eu.x45.listen45.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        var autoStartHandled = false
    }

    private val historyRepository = HistoryRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val importExportRepository = ImportExportRepository(application)

    val autoStart = settingsRepository.autoStart

    val theme = settingsRepository.theme

    val systemColorScheme = settingsRepository.systemColorScheme

    fun setAutoStart(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoStart(value) }

    fun setTheme(value: String) = viewModelScope.launch { settingsRepository.setTheme(value) }

    fun setSystemColorScheme(value: Boolean) = viewModelScope.launch { settingsRepository.setSystemColorScheme(value) }

    suspend fun import(uri: Uri) = importExportRepository.import(uri)

    suspend fun export(uri: Uri) = importExportRepository.export(uri, historyRepository.getAll().first())
}
