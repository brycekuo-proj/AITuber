package com.aituber.poc.character.statevideo

import com.aituber.poc.character.CharacterParameterFrame
import com.aituber.poc.state.UniversalAiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StateVideoCharacterPackageTest {
    @Test
    fun whitehairFemaleDeclaresStateVideoRuntimeType() {
        val characterPackage = StateVideoCharacterPackage.WhitehairFemale

        assertEquals("whitehair_female", characterPackage.id)
        assertEquals("STATE_VIDEO", characterPackage.runtimeType)
    }

    @Test
    fun whitehairFemaleMapsUniversalStatesToSelectedThreeSecondClips() {
        val characterPackage = StateVideoCharacterPackage.WhitehairFemale

        assertEquals(
            "characters/whitehair_female/clips/whitehair_female__hailuo__idle__3s__v1.mp4",
            characterPackage.clipFor(UniversalAiState.IDLE)
        )
        assertEquals(
            "characters/whitehair_female/clips/whitehair_female__hailuo__listening__3s__v1.mp4",
            characterPackage.clipFor(UniversalAiState.LISTENING)
        )
        assertEquals(
            "characters/whitehair_female/clips/whitehair_female__hailuo__thinking__3s__v1.mp4",
            characterPackage.clipFor(UniversalAiState.THINKING)
        )
        assertEquals(
            "characters/whitehair_female/clips/whitehair_female__hailuo__speaking__3s__v2.mp4",
            characterPackage.clipFor(UniversalAiState.SPEAKING)
        )
        assertNull(characterPackage.clipFor(UniversalAiState.UNKNOWN))
    }

    @Test
    fun adapterPassesUniversalStateToStateVideoSink() {
        val renderedStates = mutableListOf<UniversalAiState>()
        val adapter = StateVideoCharacterAdapter(object : StateVideoStateSink {
            override fun renderState(state: UniversalAiState) {
                renderedStates += state
            }
        })

        adapter.render(CharacterParameterFrame(mouthOpen = 0f, speaking = false, universalState = UniversalAiState.THINKING))

        assertEquals(listOf(UniversalAiState.THINKING), renderedStates)
    }
}
