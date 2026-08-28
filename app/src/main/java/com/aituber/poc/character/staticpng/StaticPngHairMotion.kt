package com.aituber.poc.character.staticpng

data class StaticPngHairKeyframe(
    val delayMs: Long,
    val shape: StaticPngHairShape
)

object StaticPngHairMotion {
    const val CYCLE_MS = 3_900L
    const val STEP_MS = CYCLE_MS / 4L
    const val TRANSITION_MS = 0L
    const val CROSSFADE_MS = 120L
    const val BRIDGE_TO_BASE_MS = 60L
    const val BRIDGE_BASE_HOLD_MS = 30L
    const val BRIDGE_FROM_BASE_MS = 100L

    val SEQUENCE = listOf(
        StaticPngHairKeyframe(delayMs = 0L, shape = StaticPngHairShape.BASE),
        StaticPngHairKeyframe(delayMs = STEP_MS, shape = StaticPngHairShape.FLOAT_A),
        StaticPngHairKeyframe(delayMs = STEP_MS, shape = StaticPngHairShape.BASE),
        StaticPngHairKeyframe(delayMs = STEP_MS, shape = StaticPngHairShape.FLOAT_B),
        StaticPngHairKeyframe(delayMs = STEP_MS, shape = StaticPngHairShape.BASE)
    )
}

enum class StaticPngHairTransitionMode {
    DIRECT,
    CROSSFADE,
    BRIDGE
}

class StaticPngHairLoopState {
    var motionEnabled: Boolean = false
        private set
    var transitionScheduled: Boolean = false
        private set
    var shape: StaticPngHairShape = StaticPngHairShape.BASE
        private set
    var scheduleCount: Int = 0
        private set

    fun start(): Boolean {
        motionEnabled = true
        shape = StaticPngHairShape.BASE
        return scheduleNext()
    }

    fun stop() {
        motionEnabled = false
        transitionScheduled = false
        shape = StaticPngHairShape.BASE
    }

    fun scheduleNext(): Boolean {
        if (!motionEnabled || transitionScheduled) return false
        transitionScheduled = true
        scheduleCount += 1
        return true
    }

    fun apply(shape: StaticPngHairShape) {
        if (!motionEnabled) return
        transitionScheduled = false
        this.shape = shape
    }
}
