package com.aituber.poc.character.live2d

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Whether the floating Live2D character (the overlay service) is on screen.
 *
 * The Cubism runtime and its GL shaders are process-wide, so two live Live2D
 * views cannot render at the same time. The overlay always wins: while it is
 * active the home screen does not attach its preview, and it re-attaches the
 * preview when the overlay goes away. Main thread only.
 */
object Live2DOverlayPresence {
    @Volatile
    var active: Boolean = false
        private set

    private val listeners = CopyOnWriteArraySet<(Boolean) -> Unit>()

    fun set(active: Boolean) {
        if (this.active == active) return
        this.active = active
        listeners.forEach { it(active) }
    }

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }
}
