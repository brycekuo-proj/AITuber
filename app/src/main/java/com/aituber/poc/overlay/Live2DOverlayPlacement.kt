package com.aituber.poc.overlay

import kotlin.math.max

object Live2DOverlayPlacement {
    const val DEFAULT_DISPLAY_SCALE = Live2DOverlayScaleMath.DEFAULT_SCALE
    const val BOTTOM_SAFE_ZONE_FRACTION = 0.18f
    const val TOP_SAFE_MARGIN_FRACTION = 0.03f
    const val RIGHT_MARGIN_FRACTION = 0.01f
    const val ANCHOR = "TOP_RIGHT"

    fun compute(
        screenWidth: Int,
        screenHeight: Int,
        systemTopInsetPx: Int = 0,
        topInsetMarginPx: Int = 0,
        density: Float = 1f,
        requestedScale: Float = DEFAULT_DISPLAY_SCALE
    ): Live2DOverlayPlacementFrame {
        val safeWidth = max(screenWidth, 1)
        val safeHeight = max(screenHeight, 1)
        val scaleRange = Live2DOverlayScaleMath.scaleRange(safeHeight, density)
        val displayScale = Live2DOverlayScaleMath.clampScale(requestedScale, scaleRange)
        val dimensions = Live2DOverlayScaleMath.dimensionsForScale(displayScale)
        val bottomSafeZonePx = (safeHeight * BOTTOM_SAFE_ZONE_FRACTION).toInt().coerceAtLeast(0)
        val topSafeMarginPx = (safeHeight * TOP_SAFE_MARGIN_FRACTION).toInt().coerceAtLeast(0)
        val rightMarginPx = (safeWidth * RIGHT_MARGIN_FRACTION).toInt().coerceAtLeast(0)
        val topInsetSafePx = (systemTopInsetPx + topInsetMarginPx).coerceAtLeast(0)
        val usableBottom = (safeHeight - bottomSafeZonePx).coerceAtLeast(1)
        val width = dimensions.width
        val height = dimensions.height
        val x = safeWidth - width - rightMarginPx
        val minY = max(topSafeMarginPx, topInsetSafePx).coerceAtMost((usableBottom - height).coerceAtLeast(0))
        val y = minY
        return Live2DOverlayPlacementFrame(
            width = width,
            height = height,
            offsetX = x,
            offsetY = y,
            displayScale = displayScale,
            minScale = scaleRange.minScale,
            defaultScale = scaleRange.defaultScale,
            maxScale = scaleRange.maxScale,
            visibleHeightPercent = Live2DOverlayScaleMath.visibleHeightPercent(height, safeHeight),
            anchor = ANCHOR,
            rightMarginFraction = RIGHT_MARGIN_FRACTION,
            rightMarginPx = rightMarginPx,
            topSafeMarginFraction = TOP_SAFE_MARGIN_FRACTION,
            topSafeMarginPx = topSafeMarginPx,
            bottomSafeZoneFraction = BOTTOM_SAFE_ZONE_FRACTION,
            bottomSafeZonePx = bottomSafeZonePx,
            usableBottom = usableBottom,
            minOffsetY = minY
        )
    }
}

data class Live2DOverlayPlacementFrame(
    val width: Int,
    val height: Int,
    val offsetX: Int,
    val offsetY: Int,
    val displayScale: Float,
    val minScale: Float,
    val defaultScale: Float,
    val maxScale: Float,
    val visibleHeightPercent: Double,
    val anchor: String,
    val rightMarginFraction: Float,
    val rightMarginPx: Int,
    val topSafeMarginFraction: Float,
    val topSafeMarginPx: Int,
    val bottomSafeZoneFraction: Float,
    val bottomSafeZonePx: Int,
    val usableBottom: Int,
    val minOffsetY: Int
)
