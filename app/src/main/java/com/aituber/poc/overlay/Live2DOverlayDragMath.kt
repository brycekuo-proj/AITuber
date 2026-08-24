package com.aituber.poc.overlay

import kotlin.math.max

object Live2DOverlayDragMath {
    fun clamp(position: Live2DOverlayPosition, bounds: Live2DOverlayBounds): Live2DOverlayPosition {
        val maxX = max(bounds.screenWidth - bounds.overlayWidth, 0)
        val maxY = max(bounds.usableBottom - bounds.overlayHeight, bounds.minY)
        return Live2DOverlayPosition(
            x = position.x.coerceIn(0, maxX),
            y = position.y.coerceIn(bounds.minY, maxY)
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
    val usableBottom: Int,
    val overlayWidth: Int,
    val overlayHeight: Int,
    val minY: Int
)
