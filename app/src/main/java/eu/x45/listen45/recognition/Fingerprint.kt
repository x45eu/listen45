// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.recognition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object Fingerprint {
    const val SAMPLE_RATE = 11_025
    const val MIN_SECONDS = 5
    private const val WINDOW = 2048
    private const val HOP = 256
    private const val PEAK_RADIUS = 2
    private const val MIN_PAIR_DISTANCE = 3
    private const val MAX_PAIR_DISTANCE = 18
    private const val MAX_PAIRS_PER_PEAK = 10
    private const val TIME_BITS = 13
    private const val TIME_MASK = (1 shl TIME_BITS) - 1
    private val bands = intArrayOf(18, 38, 75, 150, 300, 740)
    private val window = DoubleArray(WINDOW) { (1 - cos(2 * PI * it / (WINDOW - 1))) / 2 }
    private val reversed = IntArray(WINDOW) { Integer.reverse(it) ushr 21 }
    private val cosines = DoubleArray(WINDOW / 2) { cos(-2 * PI * it / WINDOW) }
    private val sines = DoubleArray(WINDOW / 2) { sin(-2 * PI * it / WINDOW) }

    // In the private catalog each landmark is stored as a hash and its anchor frame.
    fun landmarks(samples: ShortArray, checkpoint: () -> Unit = {}): LongArray {
        if (samples.size < SAMPLE_RATE * MIN_SECONDS) return LongArray(0)
        val frames = (samples.size - WINDOW) / HOP + 1
        require(frames <= TIME_MASK)
        val spectra = Array(2 * PEAK_RADIUS + 1) { DoubleArray(bands.last() + 1) }
        val peaks = Array(frames) { IntArray(bands.size - 1) }
        val real = DoubleArray(WINDOW)
        val imaginary = DoubleArray(WINDOW)
        repeat(frames) { frame ->
            if (frame % 32 == 0) checkpoint()
            val spectrum = spectra[frame % spectra.size]
            imaginary.fill(0.0)
            for (i in 0 until WINDOW) real[reversed[i]] = samples[frame * HOP + i] * window[i]
            transform(real, imaginary)
            for (bin in 0..bands.last()) {
                spectrum[bin] = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            }
            if (frame >= 2 * PEAK_RADIUS) {
                val center = frame - PEAK_RADIUS
                findPeaks(spectra, center, peaks[center])
            }
        }
        val result = LongArray(frames * (bands.size - 1) * MAX_PAIRS_PER_PEAK)
        var size = 0
        for (anchor in peaks.indices) {
            for (first in peaks[anchor]) {
                if (first == 0) continue
                var pairs = 0
                for (distance in MIN_PAIR_DISTANCE..MAX_PAIR_DISTANCE) {
                    val targets = peaks.getOrNull(anchor + distance) ?: break
                    for (second in targets) {
                        if (second == 0 || pairs == MAX_PAIRS_PER_PEAK) continue
                        val hash = (((first / 2) * 512 + second / 2) shl 5) or distance
                        result[size++] = (hash.toLong() shl TIME_BITS) or anchor.toLong()
                        pairs++
                    }
                    if (pairs == MAX_PAIRS_PER_PEAK) break
                }
            }
        }
        return result.copyOf(size)
    }

    fun hash(landmark: Long) = (landmark ushr TIME_BITS).toInt()
    fun frame(landmark: Long) = (landmark and TIME_MASK.toLong()).toInt()

    private fun findPeaks(spectra: Array<DoubleArray>, center: Int, peaks: IntArray) {
        val spectrum = spectra[center % spectra.size]
        for (band in peaks.indices) {
            var total = 0.0
            var strongest = 0
            for (bin in bands[band] until bands[band + 1]) {
                val power = spectrum[bin]
                total += power
                if (power > spectrum[strongest] || strongest == 0) strongest = bin
            }
            val power = spectrum[strongest]
            if (power < 2e8 || power < total * 0.10) continue
            if (power <= spectrum[strongest - 1] || power < spectrum[strongest + 1]) continue
            val hasLouderNeighbor = (-PEAK_RADIUS..PEAK_RADIUS).any { delta ->
                if (delta == 0) return@any false
                val neighbor = spectra[(center + delta) % spectra.size]
                // A plateau only gets one anchor.
                val max = maxOf(neighbor[strongest - 1], neighbor[strongest], neighbor[strongest + 1])
                max > power || (max == power && delta < 0)
            }
            if (!hasLouderNeighbor) peaks[band] = strongest
        }
    }

    private fun transform(real: DoubleArray, imaginary: DoubleArray) {
        var length = 2
        while (length <= WINDOW) {
            val half = length / 2
            val stride = WINDOW / length
            for (start in 0 until WINDOW step length) {
                for (offset in 0 until half) {
                    val a = start + offset
                    val b = a + half
                    val phase = offset * stride
                    val re = real[b] * cosines[phase] - imaginary[b] * sines[phase]
                    val im = real[b] * sines[phase] + imaginary[b] * cosines[phase]
                    real[b] = real[a] - re
                    imaginary[b] = imaginary[a] - im
                    real[a] += re
                    imaginary[a] += im
                }
            }
            length *= 2
        }
    }
}
