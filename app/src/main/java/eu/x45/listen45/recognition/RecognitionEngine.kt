// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.recognition

import eu.x45.listen45.models.Music

/** Input is mono, signed PCM at [Fingerprint.SAMPLE_RATE]. */
internal interface RecognitionEngine {
    suspend fun recognize(samples: ShortArray): Music?
}
