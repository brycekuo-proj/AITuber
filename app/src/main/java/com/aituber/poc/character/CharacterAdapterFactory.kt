package com.aituber.poc.character

import com.aituber.poc.character.live2d.Live2DCharacterAdapter
import com.aituber.poc.character.statevideo.StateVideoCharacterAdapter
import com.aituber.poc.overlay.MouthViewPort

object CharacterAdapterFactory {
    fun create(
        requestedMode: CharacterMode,
        mouthView: MouthViewPort,
        live2dAdapter: Live2DCharacterAdapter = Live2DCharacterAdapter(),
        stateVideoAdapter: StateVideoCharacterAdapter? = null
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
            CharacterMode.STATE_VIDEO -> if (stateVideoAdapter != null) {
                CharacterAdapterSelection(
                    requestedMode = requestedMode,
                    adapter = stateVideoAdapter,
                    fallbackReason = "n/a"
                )
            } else {
                CharacterAdapterSelection(
                    requestedMode = requestedMode,
                    adapter = MinimalMouthCharacterAdapter(mouthView),
                    fallbackReason = "STATE_VIDEO_UNAVAILABLE_FALLBACK_MINIMAL_MOUTH"
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
