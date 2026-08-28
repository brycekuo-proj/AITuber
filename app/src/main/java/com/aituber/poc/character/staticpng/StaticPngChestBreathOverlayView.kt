package com.aituber.poc.character.staticpng

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

class StaticPngChestBreathOverlayView(context: Context) : View(context) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val sourceRect = Rect()
    private val transformedBounds = RectF()
    private val maskPath = Path()
    private var sourceBitmap: Bitmap? = null
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var frame = StaticPngChestBreathMotion.frame(0L, amplitudePercent = 0)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        visibility = GONE
    }

    fun setSourceBitmap(bitmap: Bitmap?) {
        sourceBitmap = bitmap
        sourceWidth = bitmap?.width ?: 0
        sourceHeight = bitmap?.height ?: 0
        invalidate()
    }

    fun setFrame(frame: StaticPngChestBreathFrame, active: Boolean) {
        this.frame = frame
        visibility = if (active && frame.inhale > 0f && sourceBitmap != null) VISIBLE else GONE
        invalidate()
    }

    fun currentBaseBounds(): StaticPngViewRect {
        return computeBaseBounds(width, height, sourceWidth, sourceHeight)
    }

    fun currentTransformedBounds(): StaticPngViewRect {
        return frame.transformedBounds(currentBaseBounds())
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = sourceBitmap ?: return
        if (frame.inhale <= 0f || bitmap.width <= 0 || bitmap.height <= 0) return

        val roi = StaticPngChestBreathMotion.ROI
        sourceRect.set(roi.x, roi.y, roi.right, roi.bottom)
        val base = computeBaseBounds(width, height, bitmap.width, bitmap.height)
        val transformed = frame.transformedBounds(base)
        transformedBounds.set(transformed.left, transformed.top, transformed.right, transformed.bottom)

        val saveCount = canvas.saveLayer(transformedBounds, null)
        canvas.drawBitmap(bitmap, sourceRect, transformedBounds, bitmapPaint)

        val featherPx = minOf(transformed.width, transformed.height) * StaticPngChestBreathMotion.FEATHER_FRACTION
        maskPaint.maskFilter = BlurMaskFilter(featherPx.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        maskPath.reset()
        maskPath.addRoundRect(
            transformedBounds,
            transformedBounds.width() * 0.34f,
            transformedBounds.height() * 0.42f,
            Path.Direction.CW
        )
        canvas.drawPath(maskPath, maskPaint)
        maskPaint.maskFilter = null
        canvas.restoreToCount(saveCount)
    }

    private fun computeBaseBounds(
        viewWidth: Int,
        viewHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int
    ): StaticPngViewRect {
        return StaticPngFitCenterTransform
            .from(viewWidth, viewHeight, sourceWidth, sourceHeight)
            .map(StaticPngChestBreathMotion.ROI)
    }
}
