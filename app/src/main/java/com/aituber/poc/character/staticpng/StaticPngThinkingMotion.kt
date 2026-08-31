package com.aituber.poc.character.staticpng

data class StaticPngThinkingKeyframe(
    val frameId: StaticPngThinkingFrameId
)

object StaticPngThinkingMotion {
    const val CROSSFADE_MS = 220L
    const val BRIDGE_TO_C_MS = 120L
    const val BRIDGE_C_HOLD_MS = 40L
    const val BRIDGE_FROM_C_MS = 160L

    val ENTRY_SEQUENCE = listOf(
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.C),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.B),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.A),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.D),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.E),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.D),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.A),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.B),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.C)
    )

    // Frame C is the neutral start/end pose in the approved phone-tested sequence.
    // Leaving THINKING should restore IDLE from C rather than replaying a second pose chain.
    val EXIT_SEQUENCE = listOf(
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.C)
    )

    val DEBUG_LOOP_SEQUENCE = ENTRY_SEQUENCE

    fun transitionDurationMs(mode: StaticPngThinkingTransitionMode): Long = when (mode) {
        StaticPngThinkingTransitionMode.DIRECT -> 0L
        StaticPngThinkingTransitionMode.CROSSFADE -> CROSSFADE_MS
        StaticPngThinkingTransitionMode.BRIDGE -> BRIDGE_TO_C_MS + BRIDGE_C_HOLD_MS + BRIDGE_FROM_C_MS
    }
}

object StaticPngThinkingRuntimeTuning {
    const val FRAME_HOLD_MIN_MS = 200L
    const val FRAME_HOLD_MAX_MS = 1_200L
    const val DEFAULT_FRAME_HOLD_MS = 520L

    @Volatile
    var frameHoldMs: Long = DEFAULT_FRAME_HOLD_MS
        private set

    fun setFrameHoldMs(value: Long) {
        frameHoldMs = value.coerceIn(FRAME_HOLD_MIN_MS, FRAME_HOLD_MAX_MS)
    }
}

class StaticPngThinkingLoopState {
    private var loopMode: Boolean = false
    var playbackEnabled: Boolean = false
        private set
    var transitionScheduled: Boolean = false
        private set
    var frameId: StaticPngThinkingFrameId = StaticPngThinkingFrameId.C
        private set
    var sequenceIndex: Int = 0
        private set
    var scheduleCount: Int = 0
        private set

    fun start(loop: Boolean = false): Boolean {
        loopMode = loop
        playbackEnabled = true
        frameId = StaticPngThinkingFrameId.C
        sequenceIndex = 0
        return scheduleNext()
    }

    fun stop() {
        playbackEnabled = false
        transitionScheduled = false
        loopMode = false
        frameId = StaticPngThinkingFrameId.C
        sequenceIndex = 0
    }

    fun scheduleNext(): Boolean {
        if (!playbackEnabled || transitionScheduled) return false
        transitionScheduled = true
        scheduleCount += 1
        return true
    }

    fun advance(): StaticPngThinkingFrameId? {
        if (!playbackEnabled || !transitionScheduled) return null
        transitionScheduled = false
        val sequence = if (loopMode) {
            StaticPngThinkingMotion.DEBUG_LOOP_SEQUENCE
        } else {
            StaticPngThinkingMotion.ENTRY_SEQUENCE
        }
        if (sequenceIndex >= sequence.lastIndex) {
            playbackEnabled = false
            frameId = StaticPngThinkingFrameId.C
            return null
        }
        sequenceIndex += 1
        frameId = sequence[sequenceIndex].frameId
        return frameId
    }
}
