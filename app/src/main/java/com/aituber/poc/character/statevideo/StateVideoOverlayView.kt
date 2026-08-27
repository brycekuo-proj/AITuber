package com.aituber.poc.character.statevideo

import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.aituber.poc.character.CharacterDiagnostics
import com.aituber.poc.character.StateVideoDiagnosticsSnapshot
import com.aituber.poc.state.UniversalAiState

class StateVideoOverlayView(
    context: Context,
    private val characterPackage: StateVideoCharacterPackage = StateVideoCharacterPackage.WhitehairFemale
) : FrameLayout(context), StateVideoStateSink {
    private val textureView = TextureView(context)
    private var surface: Surface? = null
    private var player: MediaPlayer? = null
    private var currentState = UniversalAiState.UNKNOWN
    private var currentClip = "n/a"
    private var resolvedClipPath = "n/a"
    private var requestedTestState = "n/a"
    private var preparing = false
    private var prepared = false
    private var playing = false
    private var stateSwitchCount = 0L
    private var lastStateSwitchMs: Long? = null
    private var lastVideoError = "n/a"
    private var videoWidth = 0
    private var videoHeight = 0

    init {
        visibility = View.VISIBLE
        setBackgroundColor(Color.TRANSPARENT)
        textureView.isOpaque = false
        addView(
            textureView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                surface = Surface(surfaceTexture)
                record("SURFACE_READY")
                renderState(currentState)
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) = Unit

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                releasePlayer(status = "SURFACE_DESTROYED")
                surface?.release()
                surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
        }
        record("WAITING_FOR_SURFACE")
    }

    override fun renderState(state: UniversalAiState) {
        renderState(state, requestedTestState = null)
    }

    fun renderManualTestState(state: UniversalAiState) {
        renderState(state, requestedTestState = state.diagnosticName())
    }

    private fun renderState(state: UniversalAiState, requestedTestState: String?) {
        requestedTestState?.let { this.requestedTestState = it }
        val resolvedState = if (state == UniversalAiState.UNKNOWN) UniversalAiState.IDLE else state
        val nextClip = characterPackage.clipFor(resolvedState)
        if (nextClip == null) {
            currentState = resolvedState
            currentClip = "n/a"
            resolvedClipPath = "n/a"
            preparing = false
            lastVideoError = "STATE_VIDEO_CLIP_MISSING state=${resolvedState.name}"
            record("STATE_VIDEO_CLIP_MISSING")
            return
        }
        if (resolvedState == currentState && nextClip == currentClip && player != null) {
            record(if (prepared) "READY" else "LOADING")
            return
        }
        currentState = resolvedState
        currentClip = nextClip
        resolvedClipPath = "asset:///$nextClip"
        prepared = false
        playing = false
        preparing = false
        stateSwitchCount += 1L
        lastStateSwitchMs = elapsedRealtime()
        lastVideoError = "n/a"
        startClip(nextClip)
    }

    fun release() {
        releasePlayer(status = "RELEASED")
    }

    private fun startClip(assetPath: String) {
        val playbackSurface = surface
        if (playbackSurface == null) {
            preparing = false
            record("WAITING_FOR_SURFACE")
            return
        }

        releasePlayer(status = "SWITCHING_CLIP")
        val nextPlayer = MediaPlayer()
        player = nextPlayer
        preparing = true
        runCatching {
            context.assets.openFd(assetPath).use { afd ->
                nextPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            nextPlayer.setSurface(playbackSurface)
            nextPlayer.isLooping = true
            nextPlayer.setVolume(0f, 0f)
            nextPlayer.setOnPreparedListener { preparedPlayer ->
                preparing = false
                prepared = true
                playing = true
                videoWidth = preparedPlayer.videoWidth
                videoHeight = preparedPlayer.videoHeight
                lastVideoError = "n/a"
                preparedPlayer.start()
                record("READY")
            }
            nextPlayer.setOnErrorListener { _, what, extra ->
                preparing = false
                prepared = false
                playing = false
                lastVideoError = "STATE_VIDEO_PLAYBACK_FAILED what=$what extra=$extra"
                record("STATE_VIDEO_PLAYBACK_FAILED")
                true
            }
            nextPlayer.prepareAsync()
            record("LOADING")
        }.onFailure { error ->
            preparing = false
            prepared = false
            playing = false
            lastVideoError = "STATE_VIDEO_PLAYBACK_FAILED ${error.javaClass.simpleName}: ${error.message ?: "n/a"}"
            releasePlayer(status = "STATE_VIDEO_PLAYBACK_FAILED")
        }
    }

    private fun releasePlayer(status: String) {
        player?.let { existing ->
            runCatching {
                existing.setOnPreparedListener(null)
                existing.setOnErrorListener(null)
                existing.stop()
            }
            runCatching { existing.release() }
        }
        player = null
        preparing = false
        prepared = false
        playing = false
        record(status)
    }

    private fun record(status: String) {
        CharacterDiagnostics.recordStateVideo(
            StateVideoDiagnosticsSnapshot(
                status = status,
                requestedTestState = requestedTestState,
                currentState = currentState.name,
                currentClip = currentClip,
                resolvedClipPath = resolvedClipPath,
                playerPreparing = preparing,
                playerReady = prepared,
                playerPlaying = playing,
                loopEnabled = true,
                muted = true,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                stateSwitchCount = stateSwitchCount,
                lastStateSwitchMs = lastStateSwitchMs,
                lastVideoError = lastVideoError
            )
        )
    }

    private fun elapsedRealtime(): Long {
        return runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
    }

    private fun UniversalAiState.diagnosticName(): String = "AI_$name"
}
