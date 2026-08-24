package com.aituber.poc.overlay

import kotlin.math.max
import kotlin.math.min

object Live2DOverlayPlacement {
    const val DEFAULT_DISPLAY_SCALE = 2.25f
    const val BOTTOM_SAFE_ZONE_FRACTION = 0.18f
    const val TOP_SAFE_MARGIN_FRACTION = 0.03f
    const val RIGHT_MARGIN_FRACTION = 0.01f
    const val ANCHOR = "TOP_RIGHT"
    private const val BASE_WIDTH_PX = 360
    private const val BASE_HEIGHT_PX = 520
    private const val MAX_SCREEN_WIDTH_FRACTION = 0.75f
    private const val MAX_SCREEN_HEIGHT_FRACTION = 0.78f
    private const val MIN_WIDTH_PX = 320
    private const val MIN_HEIGHT_PX = 460

    fun compute(
        screenWidth: Int,
        screenHeight: Int,
        systemTopInsetPx: Int = 0,
        topInsetMarginPx: Int = 0
    ): Live2DOverlayPlacementFrame {
        val safeWidth = max(screenWidth, 1)
        val safeHeight = max(screenHeight, 1)
        val bottomSafeZonePx = (safeHeight * BOTTOM_SAFE_ZONE_FRACTION).toInt().coerceAtLeast(0)
        val topSafeMarginPx = (safeHeight * TOP_SAFE_MARGIN_FRACTION).toInt().coerceAtLeast(0)
        val rightMarginPx = (safeWidth * RIGHT_MARGIN_FRACTION).toInt().coerceAtLeast(0)
        val topInsetSafePx = (systemTopInsetPx + topInsetMarginPx).coerceAtLeast(0)
        val usableBottom = (safeHeight - bottomSafeZonePx).coerceAtLeast(1)
        val width = min(
            (BASE_WIDTH_PX * DEFAULT_DISPLAY_SCALE).toInt(),
            (safeWidth * MAX_SCREEN_WIDTH_FRACTION).toInt()
        ).coerceAtLeast(min(MIN_WIDTH_PX, safeWidth))
        val height = min(
            (BASE_HEIGHT_PX * DEFAULT_DISPLAY_SCALE).toInt(),
            (usableBottom * MAX_SCREEN_HEIGHT_FRACTION).toInt()
        ).coerceAtLeast(min(MIN_HEIGHT_PX, usableBottom))
        val x = (safeWidth - width - rightMarginPx).coerceAtLeast(0)
        val minY = max(topSafeMarginPx, topInsetSafePx).coerceAtMost((usableBottom - height).coerceAtLeast(0))
        val y = minY
        return Live2DOverlayPlacementFrame(
            width = width,
            height = height,
            offsetX = x,
            offsetY = y,
            displayScale = DEFAULT_DISPLAY_SCALE,
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
