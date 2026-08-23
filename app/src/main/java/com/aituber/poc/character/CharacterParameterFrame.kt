package com.aituber.poc.character

data class CharacterParameterFrame(
    val mouthOpen: Float,
    val speaking: Boolean,
    val eyeBlinkLeft: Float? = null,
    val eyeBlinkRight: Float? = null,
    val breath: Float? = null,
    val headX: Float? = null,
    val headY: Float? = null,
    val bodyX: Float? = null
) {
    fun clamped() = copy(
        mouthOpen = mouthOpen.coerceIn(0f, 1f),
        eyeBlinkLeft = eyeBlinkLeft?.coerceIn(0f, 1f),
        eyeBlinkRight = eyeBlinkRight?.coerceIn(0f, 1f),
        breath = breath?.coerceIn(0f, 1f)
    )
}
