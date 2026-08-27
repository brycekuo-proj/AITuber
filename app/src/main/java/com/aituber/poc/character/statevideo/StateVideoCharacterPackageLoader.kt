package com.aituber.poc.character.statevideo

import android.content.Context
import com.aituber.poc.state.UniversalAiState

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
        return StateVideoCharacterPackage(
            id = stringField(json, "id"),
            displayName = stringField(json, "displayName", "Whitehair Female"),
            runtimeType = stringField(json, "runtimeType"),
            states = mapOf(
                UniversalAiState.IDLE to assetPath(CHARACTER_ID, stringField(json, "AI_IDLE")),
                UniversalAiState.LISTENING to assetPath(CHARACTER_ID, stringField(json, "AI_LISTENING")),
                UniversalAiState.THINKING to assetPath(CHARACTER_ID, stringField(json, "AI_THINKING")),
                UniversalAiState.SPEAKING to assetPath(CHARACTER_ID, stringField(json, "AI_SPEAKING"))
            )
        )
    }

    private fun assetPath(characterId: String, packageRelativePath: String): String {
        return "characters/$characterId/${packageRelativePath.removePrefix("/")}"
    }

    private fun stringField(json: String, key: String, default: String? = null): String {
        val value = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]+)\"")
            .find(json)
            ?.groupValues
            ?.get(1)
        return value ?: default ?: error("Missing state video metadata field: $key")
    }
}
