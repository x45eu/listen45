// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import eu.x45.listen45.recognition.Fingerprint
import eu.x45.listen45.recognition.PcmDownsampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class AudioRecorder {
    @SuppressLint("MissingPermission") // The Identify screen requests permission before calling this.
    suspend fun capture(progress: (Int) -> Unit): ShortArray = withContext(Dispatchers.IO) {
        val (record, rate) = openRecorder()
        try {
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            val capacity = Fingerprint.SAMPLE_RATE * CAPTURE_SECONDS
            val downsampler = PcmDownsampler(rate, capacity)
            val chunk = ShortArray(rate / 20)
            val deadline = SystemClock.elapsedRealtime() + (CAPTURE_SECONDS + 3) * 1000L
            var read = 0
            while (!downsampler.isFull) {
                ensureActive()
                check(SystemClock.elapsedRealtime() < deadline) { "Microphone capture timed out" }
                val size = record.read(chunk, 0, chunk.size)
                check(size > 0) { "Microphone read failed: $size" }
                repeat(size) { downsampler.add(chunk[it].toInt()) }
                read += size
                progress((read / rate).coerceAtMost(CAPTURE_SECONDS))
            }
            downsampler.result()
        } finally {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            } finally {
                record.release()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): Pair<AudioRecord, Int> {
        for (rate in intArrayOf(44_100, 48_000, 16_000)) {
            val channel = AudioFormat.CHANNEL_IN_MONO
            val format = AudioFormat.ENCODING_PCM_16BIT
            val minimum = AudioRecord.getMinBufferSize(rate, channel, format)
            if (minimum <= 0) continue
            val record = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, rate, channel, format, maxOf(minimum, rate / 5))
            } catch (_: IllegalArgumentException) {
                continue
            }
            if (record.state == AudioRecord.STATE_INITIALIZED) return record to rate
            record.release()
        }
        error("No supported microphone configuration")
    }

    companion object {
        const val CAPTURE_SECONDS = 8
    }
}
