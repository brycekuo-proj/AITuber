package com.aituber.poc.overlay

import kotlin.math.max
import kotlin.math.roundToInt

object Live2DOverlayScaleMath {
    const val BASE_WIDTH_PX = 360
    const val BASE_HEIGHT_PX = 520
    const val DEFAULT_SCALE = 2.25f
    const val STANDARD_APP_ICON_DP = 48f
    const val MIN_ICON_HEIGHT_COUNT = 2f
    const val MAX_UPPER_BODY_SCREEN_FRACTION = 0.50f
    const val UPPER_BODY_MODEL_HEIGHT_FRACTION = 0.45f

    fun scaleRange(screenHeight: Int, density: Float): Live2DOverlayScaleRange {
        val safeHeight = max(screenHeight, 1)
        val safeDensity = max(density, 0.1f)
        val minimumVisualHeightPx = STANDARD_APP_ICON_DP * MIN_ICON_HEIGHT_COUNT * safeDensity
        val closeUpModelHeightPx = safeHeight * MAX_UPPER_BODY_SCREEN_FRACTION / UPPER_BODY_MODEL_HEIGHT_FRACTION
        val minScale = (minimumVisualHeightPx / BASE_HEIGHT_PX).coerceAtMost(DEFAULT_SCALE)
        val maxScale = (closeUpModelHeightPx / BASE_HEIGHT_PX).coerceAtLeast(DEFAULT_SCALE)
        return Live2DOverlayScaleRange(
            minScale = minScale,
            defaultScale = DEFAULT_SCALE,
            maxScale = maxScale
        )
    }

    fun dimensionsForScale(scale: Float): Live2DOverlayDimensions {
        return Live2DOverlayDimensions(
            width = (BASE_WIDTH_PX * scale).roundToInt().coerceAtLeast(1),
            height = (BASE_HEIGHT_PX * scale).roundToInt().coerceAtLeast(1)
        )
    }

    fun clampScale(scale: Float, range: Live2DOverlayScaleRange): Float {
        return scale.coerceIn(range.minScale, range.maxScale)
    }

    fun visibleHeightPercent(height: Int, screenHeight: Int): Double {
        return if (screenHeight <= 0) 0.0 else height.toDouble() / screenHeight.toDouble() * 100.0
    }

    fun resizeAroundFocus(
        currentPosition: Live2DOverlayPosition,
        currentSize: Live2DOverlayDimensions,
        nextSize: Live2DOverlayDimensions,
        focusX: Float,
        focusY: Float,
        bounds: Live2DOverlayBounds
    ): Live2DOverlayPosition {
        val widthRatio = nextSize.width.toFloat() / currentSize.width.coerceAtLeast(1).toFloat()
        val heightRatio = nextSize.height.toFloat() / currentSize.height.coerceAtLeast(1).toFloat()
        val nextX = focusX - (focusX - currentPosition.x) * widthRatio
        val nextY = focusY - (focusY - currentPosition.y) * heightRatio
        return Live2DOverlayDragMath.clamp(
            position = Live2DOverlayPosition(nextX.roundToInt(), nextY.roundToInt()),
            bounds = bounds
        )
    }
}

data class Live2DOverlayScaleRange(
    val minScale: Float,
    val defaultScale: Float,
    val maxScale: Float
)

data class Live2DOverlayDimensions(
    val width: Int,
    val height: Int
)
