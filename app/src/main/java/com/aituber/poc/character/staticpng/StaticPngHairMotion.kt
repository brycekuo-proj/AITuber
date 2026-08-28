package com.aituber.poc.character.staticpng

data class StaticPngHairKeyframe(
    val delayMs: Long,
    val shape: StaticPngHairShape
)

object StaticPngHairMotion {
    const val CYCLE_MS = 3_900L
    const val STEP_MS = CYCLE_MS / 4L
    const val TRANSITION_MS = 0L
    const val CROSSFADE_MS = 500L
    const val BRIDGE_TO_BASE_MS = 300L
    const val BRIDGE_BASE_HOLD_MS = 200L
    const val BRIDGE_FROM_BASE_MS = 300L

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

object StaticPngRuntimeTuning {
    const val CROSSFADE_MIN_MS = 50L
    const val CROSSFADE_MAX_MS = 800L
    const val DEFAULT_CROSSFADE_MS = StaticPngHairMotion.CROSSFADE_MS

    @Volatile
    var crossfadeMs: Long = DEFAULT_CROSSFADE_MS
        private set

    fun setCrossfadeMs(value: Long) {
        crossfadeMs = value.coerceIn(CROSSFADE_MIN_MS, CROSSFADE_MAX_MS)
    }
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
