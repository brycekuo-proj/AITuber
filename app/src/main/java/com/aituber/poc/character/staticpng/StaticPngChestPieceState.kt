package com.aituber.poc.character.staticpng

import com.aituber.poc.state.UniversalAiState

data class StaticPngChestPieceVisibility(
    val visible: Boolean,
    val activeState: String,
    val disabledReason: String
)

object StaticPngChestPieceState {
    const val DISABLED_REASON_NONE = "NONE"
    const val DISABLED_REASON_THINKING = "THINKING_STATE"

    fun resolve(universalState: UniversalAiState, thinkingDebugOverride: Boolean = false): StaticPngChestPieceVisibility {
        return if (universalState == UniversalAiState.THINKING || thinkingDebugOverride) {
            StaticPngChestPieceVisibility(
                visible = false,
                activeState = UniversalAiState.THINKING.name,
                disabledReason = DISABLED_REASON_THINKING
            )
        } else {
            StaticPngChestPieceVisibility(
                visible = true,
                activeState = universalState.name,
                disabledReason = DISABLED_REASON_NONE
            )
        }
    }
}
