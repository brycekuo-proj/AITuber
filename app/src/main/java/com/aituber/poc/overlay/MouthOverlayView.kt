package com.aituber.poc.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.aituber.poc.state.UniversalAiState

class MouthOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 20, 20, 20)
        style = Paint.Style.FILL
    }
    private var state = UniversalAiState.UNKNOWN
    private var mouthOpenRatio = 0f

    fun render(nextState: UniversalAiState) {
        state = nextState
        invalidate()
    }

    fun setMouthOpenRatio(ratio: Float) {
        mouthOpenRatio = ratio.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val closedHeight = height * 0.12f
        val openHeight = height * 0.72f
        val currentHeight = if (state == UniversalAiState.SPEAKING) {
            closedHeight + (openHeight - closedHeight) * mouthOpenRatio
        } else {
            closedHeight
        }
        val rect = RectF(
            width * 0.12f,
            (height - currentHeight) / 2f,
            width * 0.88f,
            (height + currentHeight) / 2f
        )
        canvas.drawRoundRect(rect, currentHeight / 2f, currentHeight / 2f, paint)
    }
}
