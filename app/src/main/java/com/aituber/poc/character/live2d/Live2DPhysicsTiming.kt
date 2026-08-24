package com.aituber.poc.character.live2d

object Live2DPhysicsTiming {
    const val MAX_DELTA_MS = 100f

    fun boundedDeltaMs(deltaMs: Float): Float = deltaMs.coerceIn(0f, MAX_DELTA_MS)
}
