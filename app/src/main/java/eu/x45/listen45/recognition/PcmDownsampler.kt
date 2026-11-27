// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.recognition

/** A streaming box filter that accepts mono PCM and retains only the lower-rate output. */
internal class PcmDownsampler(private val sourceRate: Int, capacity: Int) {
    private val samples = ShortArray(capacity)
    private var phase = 0
    private var sum = 0
    private var count = 0
    private var size = 0
    val isFull get() = size == samples.size

    init {
        require(sourceRate in Fingerprint.SAMPLE_RATE..192_000)
    }

    fun add(sample: Int) {
        if (isFull) return
        sum += sample
        count++
        phase += Fingerprint.SAMPLE_RATE
        if (phase >= sourceRate) {
            samples[size++] = (sum / count).toShort()
            phase -= sourceRate
            sum = 0
            count = 0
        }
    }

    fun result(): ShortArray = if (isFull) samples else samples.copyOf(size)
}
