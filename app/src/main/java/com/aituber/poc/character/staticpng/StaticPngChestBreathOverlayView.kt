package com.aituber.poc.character.staticpng

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

class StaticPngChestBreathOverlayView(context: Context) : View(context) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val sourceRect = Rect()
    private val transformedBounds = RectF()
    private var pieceBitmap: Bitmap? = null
    private var pieceBounds = StaticPngChestBreathMotion.PIECE_BOUNDS
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var frame = StaticPngChestBreathMotion.frame(0L, amplitudePercent = 0)
    private var displayScale = StaticPngChestBreathMotion.REFERENCE_DISPLAY_SCALE

    init {
        visibility = GONE
    }

    fun setPieceBitmap(bitmap: Bitmap?, bounds: StaticPngSourceRect) {
        pieceBitmap = bitmap
        pieceBounds = bounds
        sourceWidth = bitmap?.width ?: 0
        sourceHeight = bitmap?.height ?: 0
        invalidate()
    }

    fun setDisplayScale(scale: Float) {
        if (scale == displayScale) return
        displayScale = scale
        invalidate()
    }

    fun setFrame(frame: StaticPngChestBreathFrame, active: Boolean) {
        this.frame = frame
        visibility = if (active && pieceBitmap != null) VISIBLE else GONE
        invalidate()
    }

    fun currentBaseBounds(): StaticPngViewRect {
        return computeBaseBounds(width, height, sourceWidth, sourceHeight)
    }

    fun currentTransformedBounds(): StaticPngViewRect {
        return frame.transformedBounds(currentBaseBounds(), resources.displayMetrics.density)
    }

    fun renderLayerTypeLabel(): String {
        return when (layerType) {
            LAYER_TYPE_SOFTWARE -> "SOFTWARE"
            LAYER_TYPE_HARDWARE -> "HARDWARE"
            else -> "DEFAULT"
        }
    }

    fun overlayBoundsString(): String = "${width}x${height}"

    fun currentDisplayScale(): Float = displayScale

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = pieceBitmap ?: return
        if (bitmap.width <= 0 || bitmap.height <= 0) return

        sourceRect.set(pieceBounds.x, pieceBounds.y, pieceBounds.right, pieceBounds.bottom)
        val base = computeBaseBounds(width, height, bitmap.width, bitmap.height)
        val transformed = frame.transformedBounds(base, resources.displayMetrics.density)
        transformedBounds.set(transformed.left, transformed.top, transformed.right, transformed.bottom)

        canvas.drawBitmap(bitmap, sourceRect, transformedBounds, bitmapPaint)
    }

    private fun computeBaseBounds(
        viewWidth: Int,
        viewHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int
    ): StaticPngViewRect {
        return StaticPngFitCenterTransform
            .from(viewWidth, viewHeight, sourceWidth, sourceHeight)
            .map(pieceBounds)
    }
}
