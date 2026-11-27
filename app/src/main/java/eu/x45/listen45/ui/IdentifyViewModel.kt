// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.x45.listen45.R
import eu.x45.listen45.audio.AudioRecorder
import eu.x45.listen45.mappers.toHistoryItem
import eu.x45.listen45.models.Music
import eu.x45.listen45.recognition.LocalRecognitionEngine
import eu.x45.listen45.recognition.RecognitionEngine
import eu.x45.listen45.recognition.ReferenceCatalog
import eu.x45.listen45.repository.HistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IdentifyViewModel(application: Application) : AndroidViewModel(application) {
    internal sealed interface State {
        data object Idle : State
        data class Recording(val seconds: Int) : State
        data object Matching : State
        data class Message(val resource: Int) : State
        data class Found(val music: Music) : State
    }

    private val catalog = ReferenceCatalog.get(application)
    private val engine: RecognitionEngine = LocalRecognitionEngine(catalog)
    private val recorder = AudioRecorder()
    private val history = HistoryRepository(application)
    private var job: Job? = null
    private val currentState = MutableStateFlow<State>(State.Idle)
    internal val state = currentState.asStateFlow()
    private val request = MutableStateFlow(false)
    internal val startRequested = request.asStateFlow()

    fun requestStart() {
        request.value = true
    }

    fun clearStartRequest() {
        request.value = false
    }

    fun start() {
        if (job?.isCompleted == false) return
        currentState.value = State.Recording(0)
        job = viewModelScope.launch {
            try {
                if (!withContext(Dispatchers.IO) { catalog.hasTracks() }) {
                    currentState.value = State.Message(R.string.catalog_empty)
                    return@launch
                }
                val samples = recorder.capture { currentState.value = State.Recording(it) }
                currentState.value = State.Matching
                val music = engine.recognize(samples)
                if (music == null) {
                    currentState.value = State.Message(R.string.identify_no_match)
                } else {
                    history.insert(music.toHistoryItem())
                    currentState.value = State.Found(music)
                }
            } catch (exception: CancellationException) {
                currentState.value = State.Idle
                throw exception
            } catch (_: SecurityException) {
                currentState.value = State.Message(R.string.identify_record_permission_alert_dialog_not_available_title)
            } catch (_: Exception) {
                currentState.value = State.Message(R.string.identify_error)
            }
        }
    }

    fun stop() {
        job?.cancel()
        if (currentState.value is State.Recording || currentState.value == State.Matching) {
            currentState.value = State.Idle
        }
    }

    internal fun acknowledge(state: State) {
        currentState.compareAndSet(state, State.Idle)
    }
}
