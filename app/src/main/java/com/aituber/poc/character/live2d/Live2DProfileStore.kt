package com.aituber.poc.character.live2d

import android.content.Context

object Live2DProfileStore {
    private const val PREFS = "aituber_live2d_profile"
    private const val KEY_PROFILE_ID = "live2d_profile_id"

    fun load(context: Context): Live2DCharacterProfile {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROFILE_ID, Live2DCharacterProfiles.HARU_ID)
        return Live2DCharacterProfiles.byId(id)
    }

    fun save(context: Context, profile: Live2DCharacterProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILE_ID, profile.id)
            .apply()
    }
}
