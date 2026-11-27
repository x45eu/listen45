// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.recognition

import kotlin.math.abs

internal class LandmarkMatcher(query: LongArray) {
    private val queryFrames = query.groupBy(Fingerprint::hash, Fingerprint::frame)

    fun score(reference: LongArray, checkpoint: () -> Unit = {}): Int {
        val offsets = HashMap<Int, Int>()
        for ((index, landmark) in reference.withIndex()) {
            if (index % 4096 == 0) checkpoint()
            val anchors = queryFrames[Fingerprint.hash(landmark)] ?: continue
            if (anchors.size > MAX_REPEATS) continue
            for (anchor in anchors) {
                val offset = Fingerprint.frame(landmark) - anchor
                offsets[offset] = (offsets[offset] ?: 0) + 1
            }
        }
        val best = offsets.keys.maxByOrNull { offset ->
            (offsets[offset - 1] ?: 0) + offsets.getValue(offset) + (offsets[offset + 1] ?: 0)
        } ?: return 0

        // Count distinct query anchors to prevent one burst of similar hashes from winning.
        val matchedFrames = HashSet<Int>()
        for ((index, landmark) in reference.withIndex()) {
            if (index % 4096 == 0) checkpoint()
            val anchors = queryFrames[Fingerprint.hash(landmark)] ?: continue
            if (anchors.size > MAX_REPEATS) continue
            val frame = Fingerprint.frame(landmark)
            for (anchor in anchors) {
                if (abs(frame - anchor - best) <= 1) matchedFrames.add(anchor)
            }
        }
        val span = (matchedFrames.maxOrNull() ?: 0) - (matchedFrames.minOrNull() ?: 0)
        return if (span >= MIN_SPAN) matchedFrames.size else 0
    }

    companion object {
        const val MIN_SCORE = 20
        private const val MAX_REPEATS = 12
        private const val MIN_SPAN = 60 // About 1.4 seconds at the fingerprint hop size.
    }
}
