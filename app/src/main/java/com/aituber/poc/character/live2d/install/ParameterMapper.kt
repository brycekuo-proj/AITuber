package com.aituber.poc.character.live2d.install

import com.aituber.poc.character.live2d.Live2DParameterMapping

/**
 * Works out which model parameters AITuber should drive (mouth, eyes, breath,
 * head) for a model it has never seen.
 *
 * Evidence, strongest first:
 *  1. VTube Studio settings (.vtube.json) — the creator's own mapping.
 *  2. model3.json Groups "LipSync" / "EyeBlink".
 *  3. Standard Cubism ids that are known to exist in this model (found in
 *     cdi3 / motions / physics / expressions, or inside the moc3 itself).
 *  4. Standard Cubism 3+ and Cubism 2-style ids as a last resort.
 *
 * The result lists several ids per role ("A|B|C"); the native runtime uses
 * the first one that the loaded model actually has.
 */
object ParameterMapper {
    private val MOUTH = listOf("ParamMouthOpenY", "PARAM_MOUTH_OPEN_Y", "ParamMouthOpen", "PARAM_MOUTH_OPEN", "ParamMouthA", "ParamA")
    private val EYE_L = listOf("ParamEyeLOpen", "PARAM_EYE_L_OPEN", "ParamEyeOpenL", "ParamEyeLeftOpen")
    private val EYE_R = listOf("ParamEyeROpen", "PARAM_EYE_R_OPEN", "ParamEyeOpenR", "ParamEyeRightOpen")
    private val BREATH = listOf("ParamBreath", "PARAM_BREATH")
    private val HEAD_X = listOf("ParamAngleX", "PARAM_ANGLE_X")
    private val HEAD_Y = listOf("ParamAngleY", "PARAM_ANGLE_Y")
    private val HEAD_Z = listOf("ParamAngleZ", "PARAM_ANGLE_Z")

    val DEFAULT_MOUTH = MOUTH.joinToString("|")
    val DEFAULT_EYE_L = EYE_L.joinToString("|")
    val DEFAULT_EYE_R = EYE_R.joinToString("|")
    val DEFAULT_BREATH = BREATH.joinToString("|")
    val DEFAULT_HEAD_X = HEAD_X.joinToString("|")
    val DEFAULT_HEAD_Y = HEAD_Y.joinToString("|")

    /** Every id the converter should look for inside the moc3 bytes. */
    val STANDARD_IDS: List<String> = MOUTH + EYE_L + EYE_R + BREATH + HEAD_X + HEAD_Y + HEAD_Z

    private const val MAX_CANDIDATES = 6

    data class Evidence(
        /** Ids known to exist in the model. */
        val knownIds: Set<String> = emptySet(),
        val lipSyncIds: List<String> = emptyList(),
        val eyeBlinkIds: List<String> = emptyList(),
        /** VTube Studio "Input" -> "OutputLive2D". */
        val vtubeStudio: Map<String, String> = emptyMap()
    )

    data class Result(val mapping: Live2DParameterMapping, val lipSyncFound: Boolean)

    fun map(evidence: Evidence): Result {
        val (groupLeft, groupRight) = splitEyes(evidence.eyeBlinkIds)
        val vts = evidence.vtubeStudio

        fun role(vtsInputs: List<String>, fromGroups: List<String>, standard: List<String>): String {
            val ordered = LinkedHashSet<String>()
            vtsInputs.mapNotNullTo(ordered) { vts[it]?.takeIf(::isPlausibleId) }
            fromGroups.filterTo(ordered, ::isPlausibleId)
            standard.filterTo(ordered) { it in evidence.knownIds }
            ordered += standard
            return ordered.take(MAX_CANDIDATES).joinToString("|")
        }

        val mouthFromGroups = evidence.lipSyncIds
        val lipSyncFound = vts["MouthOpen"] != null ||
            mouthFromGroups.isNotEmpty() ||
            MOUTH.any { it in evidence.knownIds }

        return Result(
            mapping = Live2DParameterMapping(
                mouthOpen = role(listOf("MouthOpen"), mouthFromGroups, MOUTH),
                eyeLeftOpen = role(listOf("EyeOpenLeft"), groupLeft, EYE_L),
                eyeRightOpen = role(listOf("EyeOpenRight"), groupRight, EYE_R),
                breath = role(emptyList(), emptyList(), BREATH),
                headX = role(listOf("FaceAngleX"), emptyList(), HEAD_X),
                headY = role(listOf("FaceAngleY"), emptyList(), HEAD_Y),
                headZ = role(listOf("FaceAngleZ"), emptyList(), HEAD_Z)
            ),
            lipSyncFound = lipSyncFound
        )
    }

    /** Splits EyeBlink group ids into left / right. */
    internal fun splitEyes(ids: List<String>): Pair<List<String>, List<String>> {
        val left = ids.filter { side(it) == 'L' }
        val right = ids.filter { side(it) == 'R' }
        if (left.isEmpty() && right.isEmpty() && ids.size == 2) {
            return listOf(ids[0]) to listOf(ids[1])
        }
        return left to right
    }

    private fun side(id: String): Char? {
        val lower = id.lowercase()
        return when {
            "left" in lower -> 'L'
            "right" in lower -> 'R'
            Regex("eye_?l(?![a-z])|eye_?l_?open|_l$|l$").containsMatchIn(lower) && !lower.endsWith("all") -> 'L'
            Regex("eye_?r(?![a-z])|eye_?r_?open|_r$|r$").containsMatchIn(lower) -> 'R'
            else -> null
        }
    }

    private fun isPlausibleId(id: String): Boolean =
        id.isNotBlank() && id.length <= 64 && '|' !in id

    /**
     * True if [id] is stored in the moc3. Cubism stores ids as zero-padded
     * fixed-size strings, so a hit is the id bytes followed by 0 and preceded
     * by 0 (or at a 64-byte boundary).
     */
    fun mocContainsId(moc: ByteArray, id: String): Boolean {
        val needle = id.toByteArray(Charsets.UTF_8)
        if (needle.isEmpty()) return false
        var from = 0
        while (true) {
            val at = indexOf(moc, needle, from)
            if (at < 0) return false
            val end = at + needle.size
            val terminated = end < moc.size && moc[end].toInt() == 0
            val startsSlot = at == 0 || moc[at - 1].toInt() == 0 || at % 64 == 0
            if (terminated && startsSlot) return true
            from = at + 1
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        val first = needle[0]
        val last = haystack.size - needle.size
        var i = from
        outer@ while (i <= last) {
            if (haystack[i] != first) {
                i++
                continue
            }
            for (j in 1 until needle.size) {
                if (haystack[i + j] != needle[j]) {
                    i++
                    continue@outer
                }
            }
            return i
        }
        return -1
    }
}
