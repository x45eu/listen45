// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.recognition

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import eu.x45.listen45.R
import eu.x45.listen45.mappers.toHistoryItem
import eu.x45.listen45.repository.HistoryRepository
import eu.x45.listen45.ui.MainActivity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAppTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application

    @Test
    fun launchesTheRecognitionScreen() {
        val intent = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val name = activity.findViewById<TextView>(R.id.wordmark)
                assertNotNull(name)
                assertEquals("Listen45", name.text.toString())
                assertTrue(activity.findViewById<View>(R.id.catalogButton).isShown)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun indexesAnMp3AndStoresTheOfflineMatch() = runBlocking(Dispatchers.IO) {
        val localFile = File.createTempFile("reference", ".mp3", app.cacheDir)
        val catalog = ReferenceCatalog.get(app)
        val history = HistoryRepository(app)
        val title = "Test reference"
        var insertedReference: ReferenceTrack? = null
        try {
            // This fixture contains only generated sine bursts, encoded as MP3.
            instrumentation.context.assets.open("reference-synth.mp3").use { input ->
                localFile.outputStream().use { output -> input.copyTo(output) }
            }
            val samples = ReferenceAudio.read(app, Uri.fromFile(localFile))
            assertTrue(samples.size in Fingerprint.SAMPLE_RATE * 13..Fingerprint.SAMPLE_RATE * 15)
            assertTrue(catalog.add(title, "Listen45", Fingerprint.landmarks(samples)))
            insertedReference = catalog.tracks().single { it.title == title }
            val offset = Fingerprint.SAMPLE_RATE * 3 + 177
            val query = samples.copyOfRange(offset, offset + Fingerprint.SAMPLE_RATE * 8)
            val music = requireNotNull(LocalRecognitionEngine(catalog).recognize(query))
            assertEquals(title, music.title)
            assertEquals("Listen45", music.artists)
            history.insert(music.toHistoryItem())
            assertTrue(history.getAll().first().any { it.title == title })
        } finally {
            insertedReference?.let { catalog.delete(it.id) }
            history.getAll().first().filter { it.title == title }.forEach { history.delete(it) }
            localFile.delete()
        }
    }
}
