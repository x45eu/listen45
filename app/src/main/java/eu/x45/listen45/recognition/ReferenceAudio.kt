// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.recognition

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.io.BufferedInputStream
import java.nio.ByteOrder

internal object ReferenceAudio {
    private const val MAX_DECODE_SECONDS = 30L
    private const val CODEC_WAIT_US = 10_000L
    private const val PCM_ENCODING_KEY = "pcm-encoding"

    /** Read only files explicitly selected through the Android document picker. */
    fun read(context: Context, uri: Uri, checkpoint: () -> Unit = {}): ShortArray {
        require(uri.scheme == "content" || uri.scheme == "file")
        try {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { source ->
                if (source.isWave()) return WaveFile.read(source, checkpoint)
            }
        } catch (exception: IllegalArgumentException) {
            // Let the Android codec try WAV variants outside our PCM16 parser.
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, emptyMap())
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Missing audio track")
            val format = extractor.getTrackFormat(track)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(mime)
            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                return decode(extractor, decoder, checkpoint)
            } finally {
                decoder.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun BufferedInputStream.isWave(): Boolean {
        mark(12)
        val header = ByteArray(12)
        var bytesRead = 0
        while (bytesRead < header.size) {
            val count = read(header, bytesRead, header.size - bytesRead)
            if (count == -1) break
            bytesRead += count
        }
        reset()
        return bytesRead == header.size &&
            header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
            header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray(Charsets.US_ASCII))
    }

    private fun decode(extractor: MediaExtractor, decoder: MediaCodec, checkpoint: () -> Unit): ShortArray {
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + MAX_DECODE_SECONDS * 1000
        var inputFinished = false
        var outputFinished = false
        var pcm: DecodedPcm? = null
        while (!outputFinished && pcm?.samples?.isFull != true) {
            checkpoint()
            check(SystemClock.elapsedRealtime() < deadline) { "Audio decoding timed out" }
            if (!inputFinished) {
                val inputIndex = decoder.dequeueInputBuffer(CODEC_WAIT_US)
                if (inputIndex >= 0) {
                    val input = requireNotNull(decoder.getInputBuffer(inputIndex))
                    val size = extractor.readSampleData(input, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputFinished = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outputIndex = decoder.dequeueOutputBuffer(info, CODEC_WAIT_US)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = decoder.outputFormat
                    val previous = pcm
                    pcm = if (previous == null) DecodedPcm(format) else previous.apply { verifyFormat(format) }
                }
                outputIndex >= 0 -> {
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val output = requireNotNull(decoder.getOutputBuffer(outputIndex))
                            val current = pcm ?: DecodedPcm(decoder.getOutputFormat(outputIndex)).also { pcm = it }
                            output.order(ByteOrder.nativeOrder())
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val frames = output.asShortBuffer()
                            while (frames.remaining() >= current.channels && !current.samples.isFull) {
                                val left = frames.get().toInt()
                                current.samples.add(if (current.channels == 1) left else (left + frames.get()) / 2)
                            }
                        }
                    } finally {
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                    outputFinished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                }
            }
        }
        return pcm?.samples?.result() ?: ShortArray(0)
    }

    private class DecodedPcm(format: MediaFormat) {
        private val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val samples = PcmDownsampler(sampleRate, Fingerprint.SAMPLE_RATE * WaveFile.MAX_SECONDS)

        init {
            require(channels in 1..2)
            verifyFormat(format)
        }

        fun verifyFormat(format: MediaFormat) {
            require(format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == sampleRate)
            require(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == channels)
            require(!format.containsKey(PCM_ENCODING_KEY) ||
                format.getInteger(PCM_ENCODING_KEY) == AudioFormat.ENCODING_PCM_16BIT)
        }
    }
}
