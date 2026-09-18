package com.aituber.poc.ui

import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

internal object BroadwayPressSpec {
    const val PRESS_SCALE = 0.965f
    const val PRESS_OFFSET_DP = 4f
    const val PRESS_DOWN_MS = 60L
    const val RELEASE_MS = 120L
    const val RELEASE_TENSION = 1.4f
}

fun View.installBroadwayPressFeedback() {
    val pressOffsetPx = BroadwayPressSpec.PRESS_OFFSET_DP * resources.displayMetrics.density
    setOnTouchListener { view, event ->
        if (!view.isEnabled) return@setOnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.animate().cancel()
                view.animate()
                    .scaleX(BroadwayPressSpec.PRESS_SCALE)
                    .scaleY(BroadwayPressSpec.PRESS_SCALE)
                    .translationY(pressOffsetPx)
                    .setDuration(BroadwayPressSpec.PRESS_DOWN_MS)
                    .setInterpolator(null)
                    .start()
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                view.animate().cancel()
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(BroadwayPressSpec.RELEASE_MS)
                    .setInterpolator(OvershootInterpolator(BroadwayPressSpec.RELEASE_TENSION))
                    .start()
            }
        }
        false
    }
}
