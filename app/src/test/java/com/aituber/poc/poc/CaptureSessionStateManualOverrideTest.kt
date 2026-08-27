package com.aituber.poc.poc

import com.aituber.poc.state.UniversalAiState
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureSessionStateManualOverrideTest {
    @Test
    fun manualStateOverridePublishesRequestedState() {
        val observed = mutableListOf<UniversalAiState>()
        val listener: (com.aituber.poc.state.UniversalStateSnapshot) -> Unit = { snapshot ->
            observed += snapshot.state
        }

        CaptureSessionState.subscribe(listener)
        try {
            CaptureSessionState.forceStateForDebug(UniversalAiState.LISTENING, "test")
            CaptureSessionState.forceStateForDebug(UniversalAiState.THINKING, "test")
            CaptureSessionState.forceStateForDebug(UniversalAiState.SPEAKING, "test")
        } finally {
            CaptureSessionState.unsubscribe(listener)
        }

        assertEquals(
            listOf(UniversalAiState.LISTENING, UniversalAiState.THINKING, UniversalAiState.SPEAKING),
            observed.takeLast(3)
        )
    }
}
