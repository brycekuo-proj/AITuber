package com.aituber.poc.character.staticpng

data class StaticPngThinkingKeyframe(
    val frameId: StaticPngThinkingFrameId
)

object StaticPngThinkingMotion {
    const val FRAME_HOLD_MS = 520L
    const val CROSSFADE_MS = 220L
    const val BRIDGE_TO_A_MS = 120L
    const val BRIDGE_A_HOLD_MS = 40L
    const val BRIDGE_FROM_A_MS = 160L

    val ENTRY_SEQUENCE = listOf(
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.A),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.B),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.E),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.D),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.C)
    )

    val EXIT_SEQUENCE = listOf(
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.D),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.E),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.B),
        StaticPngThinkingKeyframe(StaticPngThinkingFrameId.A)
    )

    val DEBUG_LOOP_SEQUENCE = ENTRY_SEQUENCE + EXIT_SEQUENCE

    fun transitionDurationMs(mode: StaticPngThinkingTransitionMode): Long = when (mode) {
        StaticPngThinkingTransitionMode.DIRECT -> 0L
        StaticPngThinkingTransitionMode.CROSSFADE -> CROSSFADE_MS
        StaticPngThinkingTransitionMode.BRIDGE -> BRIDGE_TO_A_MS + BRIDGE_A_HOLD_MS + BRIDGE_FROM_A_MS
    }
}

class StaticPngThinkingLoopState {
    private var loopMode: Boolean = false
    var playbackEnabled: Boolean = false
        private set
    var transitionScheduled: Boolean = false
        private set
    var frameId: StaticPngThinkingFrameId = StaticPngThinkingFrameId.A
        private set
    var sequenceIndex: Int = 0
        private set
    var scheduleCount: Int = 0
        private set

    fun start(loop: Boolean = false): Boolean {
        loopMode = loop
        playbackEnabled = true
        frameId = StaticPngThinkingFrameId.A
        sequenceIndex = 0
        return scheduleNext()
    }

    fun stop() {
        playbackEnabled = false
        transitionScheduled = false
        loopMode = false
        frameId = StaticPngThinkingFrameId.A
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
