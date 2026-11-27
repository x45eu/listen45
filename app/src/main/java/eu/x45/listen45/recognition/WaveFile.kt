// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.recognition

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream

internal object WaveFile {
    const val MAX_SECONDS = 120
    private const val MAX_HEADER_BYTES = 8 * 1024 * 1024L

    /** Read mono or stereo RIFF/WAVE PCM16. Decode at most the first two minutes. */
    fun read(input: InputStream, checkpoint: () -> Unit = {}): ShortArray {
        val stream = DataInputStream(BufferedInputStream(input))
        require(stream.readInt() == 0x52494646) // RIFF
        var remaining = stream.littleInt().toLong() and 0xffffffffL
        require(stream.readInt() == 0x57415645) // WAVE
        remaining -= 4
        var headerBytes = 0L
        var channels = 0
        var sampleRate = 0
        while (remaining >= 8 && headerBytes < MAX_HEADER_BYTES) {
            checkpoint()
            val id = stream.readInt()
            val length = stream.littleInt().toLong() and 0xffffffffL
            val padded = length + (length and 1)
            remaining -= 8 + padded
            require(remaining >= 0)
            when (id) {
                0x666d7420 -> { // fmt
                    require(length >= 16)
                    require(stream.littleShort() == 1) // PCM
                    channels = stream.littleShort()
                    sampleRate = stream.littleInt()
                    stream.littleInt() // average bytes per second
                    require(channels in 1..2 && stream.littleShort() == channels * 2)
                    require(stream.littleShort() == 16)
                    require(sampleRate in Fingerprint.SAMPLE_RATE..192_000)
                    require(headerBytes + padded <= MAX_HEADER_BYTES)
                    stream.skipFully(padded - 16)
                }
                0x64617461 -> { // data
                    require(channels != 0 && length % (channels * 2) == 0L)
                    val frames = minOf(length / (channels * 2), sampleRate.toLong() * MAX_SECONDS)
                    val downsampler = PcmDownsampler(sampleRate, Fingerprint.SAMPLE_RATE * MAX_SECONDS)
                    repeat(frames.toInt()) { frame ->
                        if (frame % 8192 == 0) checkpoint()
                        val left = stream.littleShort()
                        val mono = if (channels == 2) (left + stream.littleShort()) / 2 else left
                        downsampler.add(mono)
                    }
                    return downsampler.result()
                }
                else -> {
                    require(headerBytes + padded <= MAX_HEADER_BYTES)
                    stream.skipFully(padded)
                }
            }
            headerBytes += 8 + padded
        }
        throw IllegalArgumentException("Missing WAVE audio data")
    }

    private fun DataInputStream.littleInt() = Integer.reverseBytes(readInt())

    private fun DataInputStream.littleShort() = java.lang.Short.reverseBytes(readShort()).toInt()

    private fun DataInputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped == 0L) {
                readByte()
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
