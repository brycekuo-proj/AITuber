package com.aituber.poc.character

object CharacterCapabilities {
    const val BREATH_APPLIED = "APPLIED"
    const val BREATH_NOT_FOUND = "BREATH_NOT_FOUND"
    const val BREATH_UNAVAILABLE = "UNAVAILABLE"

    fun supportsBreath(parameterStatus: String): Boolean = parameterStatus == BREATH_APPLIED
}
