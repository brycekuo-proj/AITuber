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

    @Test
    fun manualListeningStateReachesAdapter() {
        val renderedStates = mutableListOf<UniversalAiState>()
        val adapter = StateVideoCharacterAdapter(object : StateVideoStateSink {
            override fun renderState(state: UniversalAiState) {
                renderedStates += state
            }
        })

        adapter.render(CharacterParameterFrame(mouthOpen = 0f, speaking = false, universalState = UniversalAiState.LISTENING))

        assertEquals(listOf(UniversalAiState.LISTENING), renderedStates)
    }

    @Test
    fun manualThinkingStateReachesAdapter() {
        val renderedStates = mutableListOf<UniversalAiState>()
        val adapter = StateVideoCharacterAdapter(object : StateVideoStateSink {
            override fun renderState(state: UniversalAiState) {
                renderedStates += state
            }
        })

        adapter.render(CharacterParameterFrame(mouthOpen = 0f, speaking = false, universalState = UniversalAiState.THINKING))

        assertEquals(listOf(UniversalAiState.THINKING), renderedStates)
    }

    @Test
    fun manualSpeakingStateReachesAdapter() {
        val renderedStates = mutableListOf<UniversalAiState>()
        val adapter = StateVideoCharacterAdapter(object : StateVideoStateSink {
            override fun renderState(state: UniversalAiState) {
                renderedStates += state
            }
        })

        adapter.render(CharacterParameterFrame(mouthOpen = 1f, speaking = true, universalState = UniversalAiState.SPEAKING))

        assertEquals(listOf(UniversalAiState.SPEAKING), renderedStates)
    }

    @Test
    fun adapterUsesGenericStateVideoIdentity() {
        val adapter = StateVideoCharacterAdapter(object : StateVideoStateSink {
            override fun renderState(state: UniversalAiState) = Unit
        })

        assertEquals("state-video-character-adapter", adapter.characterId)
    }

    @Test
    fun metadataParserResolvesPackagePathsToStagedAssetPaths() {
        val characterPackage = StateVideoCharacterPackageLoader.parse(
            """
            {
              "id": "whitehair_female",
              "displayName": "White Hair Female",
              "runtimeType": "STATE_VIDEO",
              "states": {
                "AI_IDLE": "clips/whitehair_female__hailuo__idle__3s__v1.mp4",
                "AI_LISTENING": "clips/whitehair_female__hailuo__listening__3s__v1.mp4",
                "AI_THINKING": "clips/whitehair_female__hailuo__thinking__3s__v1.mp4",
                "AI_SPEAKING": "clips/whitehair_female__hailuo__speaking__3s__v2.mp4"
              }
            }
            """.trimIndent()
        )

        assertEquals(
            "characters/whitehair_female/clips/whitehair_female__hailuo__idle__3s__v1.mp4",
            characterPackage.clipFor(UniversalAiState.IDLE)
        )
    }

    @Test
    fun missingStateClipIsRepresentableWithoutFallingBackToIdle() {
        val partialPackage = StateVideoCharacterPackage(
            id = "partial",
            displayName = "Partial",
            runtimeType = "STATE_VIDEO",
            states = mapOf(UniversalAiState.IDLE to "characters/partial/clips/idle.mp4")
        )

        assertNull(partialPackage.clipFor(UniversalAiState.SPEAKING))
    }
}
