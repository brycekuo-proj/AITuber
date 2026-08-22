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

data class VisualMotionMetrics(
    val meanMotion: Double,
    val changedPixelRatio: Double,
    val highMotionPercentile: Double,
    val edgeMotion: Double,
    val colorMotion: Double
) {
    companion object {
        fun zero() = VisualMotionMetrics(
            meanMotion = 0.0,
            changedPixelRatio = 0.0,
            highMotionPercentile = 0.0,
            edgeMotion = 0.0,
            colorMotion = 0.0
        )
    }
}

data class VisualMotionSample(
    val elapsedTimestampMs: Long,
    val phase: String,
    val metrics: VisualMotionMetrics,
    val average1s: VisualMotionMetrics,
    val average3s: VisualMotionMetrics,
    val excludedFromSummary: Boolean
)

data class VisualMotionPhaseSummary(
    val meanAverage: Double,
    val changedPixelRatioAverage: Double,
    val highMotionPercentileAverage: Double,
    val edgeMotionAverage: Double,
    val colorMotionAverage: Double,
    val filteredPeakMeanMotion: Double,
    val rawPeakMeanMotion: Double
) {
    companion object {
        fun empty() = VisualMotionPhaseSummary(
            meanAverage = 0.0,
            changedPixelRatioAverage = 0.0,
            highMotionPercentileAverage = 0.0,
            edgeMotionAverage = 0.0,
            colorMotionAverage = 0.0,
            filteredPeakMeanMotion = 0.0,
            rawPeakMeanMotion = 0.0
        )
    }
}

data class VisualMotionSnapshot(
    val active: String,
    val roiBounds: String,
    val motionAlgorithm: String,
    val currentMetrics: VisualMotionMetrics,
    val average1s: VisualMotionMetrics,
    val average3s: VisualMotionMetrics,
    val filteredPeakMotionScore: Double,
    val rawPeakMotionScore: Double,
    val validFrameCount: Int,
    val skippedFrameCount: Int,
    val averageProcessingMs: Double,
    val currentTestPhase: String,
    val quietSummary: VisualMotionPhaseSummary,
    val userSummary: VisualMotionPhaseSummary,
    val aiSummary: VisualMotionPhaseSummary,
    val aiQuietMeanRatio: Double,
    val aiUserMeanRatio: Double,
    val aiQuietChangedPixelRatio: Double,
    val aiUserChangedPixelRatio: Double,
    val aiQuietHighMotionRatio: Double,
    val aiUserHighMotionRatio: Double,
    val aiQuietEdgeMotionRatio: Double,
    val aiUserEdgeMotionRatio: Double,
    val aiQuietColorMotionRatio: Double,
    val aiUserColorMotionRatio: Double,
    val history: List<VisualMotionSample>
) {
    companion object {
        fun empty() = VisualMotionSnapshot(
            active = "NO",
            roiBounds = "n/a",
            motionAlgorithm = "ROI 128x128 mean/changed/p95/edge/color motion",
            currentMetrics = VisualMotionMetrics.zero(),
            average1s = VisualMotionMetrics.zero(),
            average3s = VisualMotionMetrics.zero(),
            filteredPeakMotionScore = 0.0,
            rawPeakMotionScore = 0.0,
            validFrameCount = 0,
            skippedFrameCount = 0,
            averageProcessingMs = 0.0,
            currentTestPhase = "UNMARKED",
            quietSummary = VisualMotionPhaseSummary.empty(),
            userSummary = VisualMotionPhaseSummary.empty(),
            aiSummary = VisualMotionPhaseSummary.empty(),
            aiQuietMeanRatio = 0.0,
            aiUserMeanRatio = 0.0,
            aiQuietChangedPixelRatio = 0.0,
            aiUserChangedPixelRatio = 0.0,
            aiQuietHighMotionRatio = 0.0,
            aiUserHighMotionRatio = 0.0,
            aiQuietEdgeMotionRatio = 0.0,
            aiUserEdgeMotionRatio = 0.0,
            aiQuietColorMotionRatio = 0.0,
            aiUserColorMotionRatio = 0.0,
            history = emptyList()
        )
    }
}
