package com.aituber.poc.character

import com.aituber.poc.character.live2d.Live2DCharacterAdapter
import com.aituber.poc.character.staticpng.StaticPngCharacterAdapter
import com.aituber.poc.overlay.MouthViewPort

object CharacterAdapterFactory {
    fun create(
        requestedMode: CharacterMode,
        mouthView: MouthViewPort,
        live2dAdapter: Live2DCharacterAdapter = Live2DCharacterAdapter(),
        staticPngAdapter: StaticPngCharacterAdapter? = null
    ): CharacterAdapterSelection {
        return when (requestedMode) {
            CharacterMode.MINIMAL_MOUTH -> CharacterAdapterSelection(
                requestedMode = requestedMode,
                adapter = MinimalMouthCharacterAdapter(mouthView),
                fallbackReason = "n/a"
            )
            CharacterMode.LIVE2D -> if (live2dAdapter.available) {
                CharacterAdapterSelection(
                    requestedMode = requestedMode,
                    adapter = live2dAdapter,
                    fallbackReason = "n/a"
                )
            } else {
                CharacterAdapterSelection(
                    requestedMode = requestedMode,
                    adapter = MinimalMouthCharacterAdapter(mouthView),
                    fallbackReason = "LIVE2D_UNAVAILABLE_FALLBACK_MINIMAL_MOUTH"
                )
            }
            CharacterMode.STATIC_PNG -> if (staticPngAdapter != null) {
                CharacterAdapterSelection(
                    requestedMode = requestedMode,
                    adapter = staticPngAdapter,
                    fallbackReason = "n/a"
                )
            } else {
                CharacterAdapterSelection(
                    requestedMode = requestedMode,
                    adapter = MinimalMouthCharacterAdapter(mouthView),
                    fallbackReason = "STATIC_PNG_UNAVAILABLE_FALLBACK_MINIMAL_MOUTH"
                )
            }
        }
    }
}

data class CharacterAdapterSelection(
    val requestedMode: CharacterMode,
    val adapter: CharacterAdapter,
    val fallbackReason: String
)
