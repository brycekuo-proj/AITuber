package com.aituber.poc.character.live2d

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.aituber.poc.character.CharacterDiagnostics
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
                renderFps = snapshot.renderFps,
                nativeFrameCount = snapshot.nativeFrameCount,
                surfaceWidth = snapshot.surfaceWidth,
                surfaceHeight = snapshot.surfaceHeight,
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
                publishDiagnostics()
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            if (initialized) {
                bridge.onSurfaceChanged(width, height)
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
