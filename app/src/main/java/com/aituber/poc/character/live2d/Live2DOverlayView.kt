package com.aituber.poc.character.live2d

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.aituber.poc.character.CharacterDiagnostics
import com.aituber.poc.character.CharacterParameterFrame
import com.aituber.poc.character.Live2DDiagnosticsSnapshot
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class Live2DOverlayView(
    context: Context,
    val bridge: Live2DNativeBridge = Live2DNativeBridge(),
    private val onRuntimeFailure: (String) -> Unit = {}
) : GLSurfaceView(context) {
    @Volatile
    var initialized: Boolean = false
        private set

    @Volatile
    private var lastMouthOpen: Float = 0f
    @Volatile
    private var lastLeftEyeOpen: Float = 1f
    @Volatile
    private var lastRightEyeOpen: Float = 1f
    @Volatile
    private var lastBreath: Float = 0.5f
    @Volatile
    private var lastBreathIntensity: Float = 0.30f

    init {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = false
        setZOrderOnTop(true)
        initialized = bridge.initialize(context.applicationContext)
        setRenderer(Renderer())
        renderMode = RENDERMODE_CONTINUOUSLY
        publishDiagnostics()
    }

    fun setMouthOpenRatio(ratio: Float) {
        lastMouthOpen = ratio.coerceIn(0f, 1f)
        queueEvent {
            bridge.setMouthOpen(lastMouthOpen)
            publishDiagnostics()
        }
    }

    fun renderFrame(frame: CharacterParameterFrame) {
        val clamped = frame.clamped()
        lastMouthOpen = clamped.mouthOpen
        lastLeftEyeOpen = clamped.eyeLeftOpen ?: 1f
        lastRightEyeOpen = clamped.eyeRightOpen ?: 1f
        lastBreath = clamped.breath ?: 0.5f
        lastBreathIntensity = clamped.breathIntensity ?: 0.30f
        queueEvent {
            bridge.setMouthOpen(lastMouthOpen)
            bridge.setEyeOpen(lastLeftEyeOpen, lastRightEyeOpen)
            bridge.setBreath(lastBreath, lastBreathIntensity)
            publishDiagnostics()
        }
    }

    fun setEyeOpen(leftEyeOpen: Float, rightEyeOpen: Float) {
        lastLeftEyeOpen = leftEyeOpen.coerceIn(0f, 1f)
        lastRightEyeOpen = rightEyeOpen.coerceIn(0f, 1f)
        queueEvent {
            bridge.setEyeOpen(lastLeftEyeOpen, lastRightEyeOpen)
            publishDiagnostics()
        }
    }

    fun release() {
        queueEvent {
            bridge.release()
            publishDiagnostics()
        }
        onPause()
    }

    fun publishDiagnostics() {
        val snapshot = bridge.snapshot()
        CharacterDiagnostics.recordLive2D(
            Live2DDiagnosticsSnapshot(
                available = bridge.available,
                runtimeLoaded = snapshot.runtimeLoaded,
                coreLoaded = snapshot.coreLoaded,
                modelLoaded = snapshot.modelLoaded,
                modelName = snapshot.modelName,
                mouthParameterId = snapshot.mouthParameterId,
                mouthParameterValue = snapshot.appliedMouthOpen,
                inputMouthOpen = snapshot.inputMouthOpen,
                leftEyeParameterStatus = snapshot.leftEyeParameterStatus,
                rightEyeParameterStatus = snapshot.rightEyeParameterStatus,
                leftEyeOpen = snapshot.appliedLeftEyeOpen,
                rightEyeOpen = snapshot.appliedRightEyeOpen,
                breathParameterStatus = snapshot.breathParameterStatus,
                breathNormalized = snapshot.inputBreathNormalized,
                breathAppliedValue = snapshot.appliedBreathValue,
                breathMin = snapshot.breathMin,
                breathMax = snapshot.breathMax,
                breathDefault = snapshot.breathDefault,
                renderFps = snapshot.renderFps,
                nativeFrameCount = snapshot.nativeFrameCount,
                surfaceWidth = snapshot.surfaceWidth,
                surfaceHeight = snapshot.surfaceHeight,
                textureCount = snapshot.textureCount,
                texturesLoaded = snapshot.texturesLoaded,
                lastTexturePath = snapshot.lastTexturePath,
                lastTextureError = snapshot.lastTextureError,
                glTextureIds = snapshot.glTextureIds,
                poseFile = snapshot.poseFile,
                poseLoaded = snapshot.poseLoaded,
                poseActive = snapshot.poseActive,
                lifecycleState = lifecycleState(snapshot),
                fallbackReason = if (snapshot.lastError.isBlank()) "n/a" else snapshot.lastError,
                mouthParameterStatus = if (snapshot.mouthParameterFound) {
                    Live2DParameterStatus.APPLIED.name
                } else {
                    Live2DParameterStatus.NOT_FOUND.name
                },
                lastError = snapshot.lastError.ifBlank { "n/a" }
            )
        )
    }

    private fun lifecycleState(snapshot: Live2DNativeSnapshot): String {
        if (!initialized) return "DISABLED"
        if (snapshot.lastError.isNotBlank() && !snapshot.modelLoaded) return "FAILED"
        if (snapshot.modelLoaded) return "READY"
        if (snapshot.runtimeLoaded) return "INITIALIZING"
        return "WAITING_FOR_SURFACE"
    }

    private inner class Renderer : GLSurfaceView.Renderer {
        private var lastFrameMs = 0L

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            if (initialized) {
                if (!bridge.onSurfaceCreated()) {
                    val error = bridge.snapshot().lastError.ifBlank { "LIVE2D_SURFACE_MODEL_LOAD_FAILED" }
                    post { onRuntimeFailure(error) }
                    publishDiagnostics()
                    return
                }
                bridge.setMouthOpen(lastMouthOpen)
                bridge.setEyeOpen(lastLeftEyeOpen, lastRightEyeOpen)
                bridge.setBreath(lastBreath, lastBreathIntensity)
                publishDiagnostics()
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            if (initialized) {
                if (!bridge.onSurfaceChanged(width, height)) {
                    val error = bridge.snapshot().lastError.ifBlank { "LIVE2D_SURFACE_RESIZE_OR_MODEL_LOAD_FAILED" }
                    post { onRuntimeFailure(error) }
                    publishDiagnostics()
                    return
                }
                publishDiagnostics()
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            val now = SystemClock.elapsedRealtime()
            val sleepMs = 33L - (now - lastFrameMs)
            if (sleepMs > 0L) {
                Thread.sleep(sleepMs)
            }
            lastFrameMs = SystemClock.elapsedRealtime()
            bridge.drawFrame()
        }
    }
}
