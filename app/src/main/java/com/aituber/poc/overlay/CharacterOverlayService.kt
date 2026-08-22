package com.aituber.poc.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.aituber.poc.character.CharacterEngine
import com.aituber.poc.character.MinimalMouthCharacterAdapter
import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class CharacterOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val animationFrames = floatArrayOf(0.15f, 0.65f, 0.35f, 0.9f)
    private var frameIndex = 0
    private var mouthView: MouthOverlayView? = null
    private var characterEngine: CharacterEngine? = null
    private var windowManager: WindowManager? = null
    private var currentState = UniversalAiState.UNKNOWN

    private val stateListener: (UniversalStateSnapshot) -> Unit = { snapshot ->
        currentState = snapshot.state
        characterEngine?.bind(snapshot)
        if (snapshot.state == UniversalAiState.SPEAKING) {
            ensureAnimation()
        } else {
            stopAnimation()
        }
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (currentState != UniversalAiState.SPEAKING) return
            mouthView?.setMouthOpenRatio(animationFrames[frameIndex % animationFrames.size])
            frameIndex += 1
            handler.postDelayed(this, 130L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val view = MouthOverlayView(this)
        mouthView = view
        characterEngine = CharacterEngine(MinimalMouthCharacterAdapter(view))
        windowManager = getSystemService(WindowManager::class.java)
        windowManager?.addView(view, overlayLayoutParams())
        CaptureSessionState.subscribe(stateListener)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        CaptureSessionState.unsubscribe(stateListener)
        stopAnimation()
        mouthView?.let { view -> runCatching { windowManager?.removeView(view) } }
        mouthView = null
        characterEngine = null
        windowManager = null
        super.onDestroy()
    }

    private fun overlayLayoutParams() = WindowManager.LayoutParams(
        180,
        90,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 32
        y = 220
    }

    private fun ensureAnimation() {
        handler.removeCallbacks(animationRunnable)
        handler.post(animationRunnable)
    }

    private fun stopAnimation() {
        handler.removeCallbacks(animationRunnable)
        mouthView?.setMouthOpenRatio(0f)
        frameIndex = 0
    }
}
