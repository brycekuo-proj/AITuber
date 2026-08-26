package com.aituber.poc.character.statevideo

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import com.aituber.poc.character.CharacterDiagnostics
import com.aituber.poc.character.StateVideoDiagnosticsSnapshot
import com.aituber.poc.state.UniversalAiState

class StateVideoOverlayView(
    context: Context,
    private val characterPackage: StateVideoCharacterPackage = StateVideoCharacterPackage.WhitehairFemale
) : FrameLayout(context), StateVideoStateSink {
    private val videoView = VideoView(context)
    private var currentState = UniversalAiState.UNKNOWN
    private var currentClip = "n/a"
    private var prepared = false
    private var playing = false
    private var lastStateSwitchMs: Long? = null
    private var lastVideoError = "n/a"
    private var videoWidth = 0
    private var videoHeight = 0

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            videoView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        videoView.setOnPreparedListener { player ->
            prepared = true
            playing = true
            lastVideoError = "n/a"
            videoWidth = player.videoWidth
            videoHeight = player.videoHeight
            player.isLooping = true
            player.setVolume(0f, 0f)
            videoView.start()
            record("READY")
        }
        videoView.setOnErrorListener { _, what, extra ->
            prepared = false
            playing = false
            lastVideoError = "STATE_VIDEO_PLAYBACK_FAILED what=$what extra=$extra"
            record("STATE_VIDEO_PLAYBACK_FAILED")
            true
        }
        record("WAITING_FOR_STATE")
    }

    override fun renderState(state: UniversalAiState) {
        val resolvedState = if (state == UniversalAiState.UNKNOWN) UniversalAiState.IDLE else state
        val nextClip = characterPackage.clipFor(resolvedState)
        if (nextClip == null) {
            lastVideoError = "STATE_VIDEO_CLIP_MISSING state=${resolvedState.name}"
            record("STATE_VIDEO_CLIP_MISSING")
            return
        }
        if (resolvedState == currentState && nextClip == currentClip) {
            record(if (prepared) "READY" else "LOADING")
            return
        }
        currentState = resolvedState
        currentClip = nextClip
        prepared = false
        playing = false
        lastStateSwitchMs = elapsedRealtime()
        lastVideoError = "n/a"
        videoView.stopPlayback()
        videoView.setVideoURI(Uri.parse("file:///android_asset/$nextClip"))
        videoView.start()
        record("LOADING")
    }

    fun release() {
        runCatching { videoView.stopPlayback() }
        prepared = false
        playing = false
        record("RELEASED")
    }

    private fun record(status: String) {
        CharacterDiagnostics.recordStateVideo(
            StateVideoDiagnosticsSnapshot(
                status = status,
                currentState = currentState.name,
                currentClip = currentClip,
                playerReady = prepared,
                playerPlaying = playing,
                loopEnabled = true,
                muted = true,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                lastStateSwitchMs = lastStateSwitchMs,
                lastVideoError = lastVideoError
            )
        )
    }

    private fun elapsedRealtime(): Long {
        return runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
    }
}
