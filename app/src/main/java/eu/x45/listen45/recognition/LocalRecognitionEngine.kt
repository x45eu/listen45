// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.recognition

import eu.x45.listen45.models.Music
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class LocalRecognitionEngine(private val catalog: ReferenceCatalog) : RecognitionEngine {
    override suspend fun recognize(samples: ShortArray): Music? = withContext(Dispatchers.IO) {
        val query = Fingerprint.landmarks(samples) { ensureActive() }
        if (query.isEmpty()) return@withContext null
        val matcher = LandmarkMatcher(query)
        var best: ReferenceTrack? = null
        var bestScore = 0
        var otherScore = 0
        catalog.forEachFingerprint { track, reference ->
            ensureActive()
            val score = matcher.score(reference) { ensureActive() }
            if (score > bestScore) {
                otherScore = bestScore
                bestScore = score
                best = track
            } else if (score > otherScore) {
                otherScore = score
            }
        }
        best?.takeIf { bestScore >= LandmarkMatcher.MIN_SCORE && bestScore >= otherScore * 1.25 }
            ?.let { Music(it.title, it.artist, "", null, null, null, null) }
    }
}
