package com.aituber.poc.overlay

object Live2DOverlayDragMath {
    const val MAX_OFFSCREEN_FRACTION = 0.5f
    const val MIN_VISIBLE_SCREEN_FRACTION_AT_BOTTOM = 0.5f

    fun clamp(position: Live2DOverlayPosition, bounds: Live2DOverlayBounds): Live2DOverlayPosition {
        val offscreenWidth = (bounds.overlayWidth * MAX_OFFSCREEN_FRACTION).toInt()
        val offscreenHeight = (bounds.overlayHeight * MAX_OFFSCREEN_FRACTION).toInt()
        val minX = -offscreenWidth
        val maxX = bounds.screenWidth - offscreenWidth
        val minY = -offscreenHeight
        val halfOverlayMaxY = bounds.screenHeight - offscreenHeight
        val largeOverlayMaxY = (bounds.screenHeight * (1f - MIN_VISIBLE_SCREEN_FRACTION_AT_BOTTOM)).toInt()
        val maxY = kotlin.math.max(halfOverlayMaxY, largeOverlayMaxY)
        return Live2DOverlayPosition(
            x = position.x.coerceIn(minX, maxX),
            y = position.y.coerceIn(minY, maxY)
        )
    }

    fun initialPosition(
        defaultPosition: Live2DOverlayPosition,
        savedPosition: Live2DOverlayPosition?,
        bounds: Live2DOverlayBounds
    ): Live2DOverlayPosition = clamp(savedPosition ?: defaultPosition, bounds)
}

data class Live2DOverlayPosition(
    val x: Int,
    val y: Int
)

data class Live2DOverlayBounds(
    val screenWidth: Int,
    val screenHeight: Int,
    val overlayWidth: Int,
    val overlayHeight: Int
)
