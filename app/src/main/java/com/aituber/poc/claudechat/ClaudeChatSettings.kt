package com.aituber.poc.claudechat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The chat screen's settings. The API key is the user's own: it is encrypted
 * with a key kept in the Android Keystore (never leaves the phone) and is only
 * ever sent to api.anthropic.com. allowBackup is off, so it is not backed up.
 */
object ClaudeChatSettings {
    private const val PREFS = "aituber_claude_chat"
    private const val KEY_API_KEY = "api_key_enc"
    private const val KEY_MODEL = "model"
    private const val KEY_SPEAK = "speak_replies"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "aituber_claude_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasApiKey(context: Context): Boolean = prefs(context).contains(KEY_API_KEY)

    fun loadApiKey(context: Context): String? {
        val stored = prefs(context).getString(KEY_API_KEY, null) ?: return null
        return runCatching {
            val (ivPart, dataPart) = stored.split(':', limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(ivPart, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(dataPart, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val data = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        val stored = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
        prefs(context).edit().putString(KEY_API_KEY, stored).apply()
    }

    fun clearApiKey(context: Context) {
        prefs(context).edit().remove(KEY_API_KEY).apply()
    }

    fun model(context: Context): ClaudeModel = ClaudeModels.byId(prefs(context).getString(KEY_MODEL, null))

    fun setModel(context: Context, model: ClaudeModel) {
        prefs(context).edit().putString(KEY_MODEL, model.id).apply()
    }

    fun speakReplies(context: Context): Boolean = prefs(context).getBoolean(KEY_SPEAK, true)

    fun setSpeakReplies(context: Context, speak: Boolean) {
        prefs(context).edit().putBoolean(KEY_SPEAK, speak).apply()
    }

    /** Anthropic keys start with "sk-ant-"; anything else is almost surely a paste mistake. */
    fun looksLikeApiKey(text: String): Boolean {
        val key = text.trim()
        return key.startsWith("sk-ant-") && key.length >= 40 && key.none { it.isWhitespace() }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
