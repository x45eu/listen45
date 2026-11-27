// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.x45.listen45.R
import eu.x45.listen45.recognition.Fingerprint
import eu.x45.listen45.recognition.LandmarkMatcher
import eu.x45.listen45.recognition.ReferenceCatalog
import eu.x45.listen45.recognition.ReferenceTrack
import eu.x45.listen45.recognition.ReferenceAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    internal data class State(
        val tracks: List<ReferenceTrack> = emptyList(),
        val busy: Boolean = true,
        val message: Int? = null,
    )

    private val catalog = ReferenceCatalog.get(application)
    private val currentState = MutableStateFlow(State())
    internal val state = currentState.asStateFlow()

    init {
        updateCatalog { null }
    }

    fun add(uri: Uri, title: String, artist: String) {
        if (state.value.busy) return
        updateCatalog {
            val samples = try {
                ReferenceAudio.read(getApplication(), uri) { ensureActive() }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                return@updateCatalog R.string.catalog_import_error
            }
            val landmarks = Fingerprint.landmarks(samples) { ensureActive() }
            when {
                landmarks.size < LandmarkMatcher.MIN_SCORE -> R.string.catalog_import_error
                !catalog.add(title, artist, landmarks) -> R.string.catalog_limit
                else -> null
            }
        }
    }

    fun delete(id: Long) {
        if (!state.value.busy) updateCatalog {
            catalog.delete(id)
            null
        }
    }

    fun clearMessage() {
        currentState.update { it.copy(message = null) }
    }

    private fun updateCatalog(operation: suspend kotlinx.coroutines.CoroutineScope.() -> Int?) {
        currentState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            currentState.value = try {
                withContext(Dispatchers.IO) {
                    val message = operation()
                    State(catalog.tracks(), busy = false, message = message)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                state.value.copy(busy = false, message = R.string.catalog_storage_error)
            }
        }
    }
}
