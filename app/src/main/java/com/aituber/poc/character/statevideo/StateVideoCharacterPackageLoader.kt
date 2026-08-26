package com.aituber.poc.character.statevideo

import android.content.Context
import com.aituber.poc.state.UniversalAiState
import org.json.JSONObject

object StateVideoCharacterPackageLoader {
    private const val CHARACTER_ID = "whitehair_female"
    private const val METADATA_PATH = "characters/whitehair_female/metadata/character.json"

    fun loadWhitehairFemale(context: Context): StateVideoCharacterPackage {
        val metadata = runCatching {
            context.assets.open(METADATA_PATH).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return StateVideoCharacterPackage.WhitehairFemale

        return runCatching { parse(metadata) }.getOrElse { StateVideoCharacterPackage.WhitehairFemale }
    }

    fun parse(json: String): StateVideoCharacterPackage {
        val root = JSONObject(json)
        val states = root.getJSONObject("states")
        return StateVideoCharacterPackage(
            id = root.getString("id"),
            displayName = root.optString("displayName", "Whitehair Female"),
            runtimeType = root.getString("runtimeType"),
            states = mapOf(
                UniversalAiState.IDLE to assetPath(CHARACTER_ID, states.getString("AI_IDLE")),
                UniversalAiState.LISTENING to assetPath(CHARACTER_ID, states.getString("AI_LISTENING")),
                UniversalAiState.THINKING to assetPath(CHARACTER_ID, states.getString("AI_THINKING")),
                UniversalAiState.SPEAKING to assetPath(CHARACTER_ID, states.getString("AI_SPEAKING"))
            )
        )
    }

    private fun assetPath(characterId: String, packageRelativePath: String): String {
        return "characters/$characterId/${packageRelativePath.removePrefix("/")}"
    }
}
