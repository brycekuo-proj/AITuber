package com.aituber.poc.state

data class RoiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int = right - left
    val height: Int = bottom - top

    override fun toString(): String = "$left,$top,$right,$bottom"
}

data class VisualMotionSample(
    val elapsedTimestampMs: Long,
    val phase: String,
    val motionScore: Double,
    val average1s: Double,
    val average3s: Double
)

data class VisualMotionSnapshot(
    val active: String,
    val roiBounds: String,
    val motionAlgorithm: String,
    val currentMotionScore: Double,
    val average1s: Double,
    val average3s: Double,
    val peakMotionScore: Double,
    val validFrameCount: Int,
    val skippedFrameCount: Int,
    val currentTestPhase: String,
    val quietAverageMotion: Double,
    val userAverageMotion: Double,
    val aiAverageMotion: Double,
    val quietPeakMotion: Double,
    val userPeakMotion: Double,
    val aiPeakMotion: Double,
    val aiQuietRatio: Double,
    val aiUserRatio: Double,
    val history: List<VisualMotionSample>
) {
    companion object {
        fun empty() = VisualMotionSnapshot(
            active = "NO",
            roiBounds = "n/a",
            motionAlgorithm = "ROI grayscale mean absolute difference",
            currentMotionScore = 0.0,
            average1s = 0.0,
            average3s = 0.0,
            peakMotionScore = 0.0,
            validFrameCount = 0,
            skippedFrameCount = 0,
            currentTestPhase = "UNMARKED",
            quietAverageMotion = 0.0,
            userAverageMotion = 0.0,
            aiAverageMotion = 0.0,
            quietPeakMotion = 0.0,
            userPeakMotion = 0.0,
            aiPeakMotion = 0.0,
            aiQuietRatio = 0.0,
            aiUserRatio = 0.0,
            history = emptyList()
        )
    }
}
