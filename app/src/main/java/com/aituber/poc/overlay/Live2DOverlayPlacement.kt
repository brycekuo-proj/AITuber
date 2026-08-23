package com.aituber.poc.overlay

import kotlin.math.max
import kotlin.math.min

object Live2DOverlayPlacement {
    const val DEFAULT_DISPLAY_SCALE = 1.9f
    private const val BASE_WIDTH_PX = 360
    private const val BASE_HEIGHT_PX = 520
    private const val MAX_SCREEN_WIDTH_FRACTION = 0.64f
    private const val MAX_SCREEN_HEIGHT_FRACTION = 0.72f
    private const val MIN_WIDTH_PX = 320
    private const val MIN_HEIGHT_PX = 460
    private const val RIGHT_MARGIN_FRACTION = 0.03f
    private const val BOTTOM_MARGIN_FRACTION = 0.02f

    fun compute(screenWidth: Int, screenHeight: Int): Live2DOverlayPlacementFrame {
        val safeWidth = max(screenWidth, 1)
        val safeHeight = max(screenHeight, 1)
        val width = min(
            (BASE_WIDTH_PX * DEFAULT_DISPLAY_SCALE).toInt(),
            (safeWidth * MAX_SCREEN_WIDTH_FRACTION).toInt()
        ).coerceAtLeast(min(MIN_WIDTH_PX, safeWidth))
        val height = min(
            (BASE_HEIGHT_PX * DEFAULT_DISPLAY_SCALE).toInt(),
            (safeHeight * MAX_SCREEN_HEIGHT_FRACTION).toInt()
        ).coerceAtLeast(min(MIN_HEIGHT_PX, safeHeight))
        val x = (safeWidth * RIGHT_MARGIN_FRACTION).toInt().coerceAtLeast(0)
        val y = (safeHeight - height - (safeHeight * BOTTOM_MARGIN_FRACTION).toInt())
            .coerceAtLeast(0)
        return Live2DOverlayPlacementFrame(
            width = width,
            height = height,
            offsetX = x,
            offsetY = y,
            displayScale = DEFAULT_DISPLAY_SCALE
        )
    }
}

data class Live2DOverlayPlacementFrame(
    val width: Int,
    val height: Int,
    val offsetX: Int,
    val offsetY: Int,
    val displayScale: Float
)
