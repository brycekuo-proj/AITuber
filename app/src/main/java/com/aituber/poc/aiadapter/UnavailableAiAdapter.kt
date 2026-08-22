package com.aituber.poc.aiadapter

import com.aituber.poc.state.UniversalStateReducer
import com.aituber.poc.state.UniversalStateSnapshot

class UnavailableAiAdapter(
    private val reason: String,
    private val reducer: UniversalStateReducer = UniversalStateReducer()
) : AiAdapter {
    override val targetAppLabel = "Unknown"
    override val detectionMethod = "Unavailable"

    override fun start(onSnapshot: (UniversalStateSnapshot) -> Unit) {
        onSnapshot(reducer.reduce(targetAppLabel, detectionMethod, null, reason))
    }

    override fun stop() = Unit
}
