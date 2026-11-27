// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.recognition

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintTest {
    @Test
    fun matchesAnOffsetExcerptWithNoiseAndRejectsUnrelatedAudio() {
        val rate = Fingerprint.SAMPLE_RATE
        val reference = signal(14, seed = 45)
        val noise = Random(15)
        val query = ShortArray(8 * rate) { index ->
            (reference[index + rate * 3 + 319] * 0.48 + noise.nextInt(-650, 651)).toInt().toShort()
        }
        val matcher = LandmarkMatcher(Fingerprint.landmarks(query))
        val match = matcher.score(Fingerprint.landmarks(reference))
        val unrelated = matcher.score(Fingerprint.landmarks(signal(14, seed = 60)))
        assertTrue("Expected a confident match, got $match", match >= LandmarkMatcher.MIN_SCORE)
        assertTrue("Unrelated audio scored $unrelated", unrelated < LandmarkMatcher.MIN_SCORE)
    }

    @Test
    fun silenceAndShortClipsDoNotGenerateLandmarks() {
        assertTrue(Fingerprint.landmarks(ShortArray(8 * Fingerprint.SAMPLE_RATE)).isEmpty())
        assertTrue(Fingerprint.landmarks(signal(4, seed = 2)).isEmpty())
    }

    @Test
    fun readsStereoPcmAndResamplesToMono() {
        val wave = pcmWave(shortArrayOf(200, 400, 600, 800, -200, -400, -600, -800))
        val samples = WaveFile.read(ByteArrayInputStream(wave))
        assertArrayEquals(shortArrayOf(500, -500), samples)
        assertEquals(2, samples.size)
    }

    @Test(expected = EOFException::class)
    fun rejectsTruncatedWaveFile() {
        val wave = pcmWave(shortArrayOf(200, 400, 600, 800))
        WaveFile.read(ByteArrayInputStream(wave.copyOf(wave.size - 2)))
    }

    private fun pcmWave(interleaved: ShortArray): ByteArray {
        val dataBytes = interleaved.size * Short.SIZE_BYTES
        return ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(2)
            putInt(22_050)
            putInt(22_050 * 4)
            putShort(4)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
            interleaved.forEach { putShort(it) }
        }.array()
    }

    private fun signal(seconds: Int, seed: Int): ShortArray {
        val rate = Fingerprint.SAMPLE_RATE
        val random = Random(seed)
        val out = DoubleArray(rate * seconds)
        repeat(5) { band ->
            var start = random.nextInt(100, 1000)
            while (start + 3000 < out.size) {
                val length = random.nextInt(1400, 2950)
                val frequency = (120.0 + random.nextDouble() * 100.0) * (1 shl band)
                for (i in 0 until length) {
                    val envelope = sin(PI * i / length)
                    out[start + i] += sin(2 * PI * frequency * i / rate) * envelope * 4200
                }
                start += random.nextInt(1120, 2900)
            }
        }
        return ShortArray(out.size) { out[it].toInt().toShort() }
    }
}
