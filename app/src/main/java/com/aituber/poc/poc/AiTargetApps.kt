package com.aituber.poc.poc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * AI apps AITuber can sit on top of. Detection is not tied to one app:
 *  - the output-mix Visualizer works for whatever is speaking;
 *  - Playback Capture listens to every installed app in this list.
 *
 * Package names must also be listed in AndroidManifest <queries> (Android 11+
 * package visibility), otherwise they look uninstalled.
 */
data class AiTargetApp(val label: String, val packageName: String)

object AiTargetApps {
    val CHATGPT = AiTargetApp("ChatGPT", "com.openai.chatgpt")
    val GEMINI = AiTargetApp("Gemini", "com.google.android.apps.bard")
    // The Google app (com.google.android.googlequicksearchbox) is deliberately not
    // listed: it also plays Discover videos and podcasts, which would make the
    // character talk along. Gemini Live inside it is still covered by the
    // speaker-output Visualizer during a voice session.
    val CLAUDE = AiTargetApp("Claude", "com.anthropic.claude")
    val GROK = AiTargetApp("Grok", "ai.x.grok")
    val COPILOT = AiTargetApp("Copilot", "com.microsoft.copilot")
    val PERPLEXITY = AiTargetApp("Perplexity", "ai.perplexity.app.android")

    val all: List<AiTargetApp> = listOf(CHATGPT, GEMINI, CLAUDE, GROK, COPILOT, PERPLEXITY)

    data class Installed(val app: AiTargetApp, val uid: Int)

    fun installed(context: Context): List<Installed> =
        all.mapNotNull { app -> uid(context, app.packageName)?.let { Installed(app, it) } }

    /** Label for status text, e.g. "ChatGPT / Claude" or "AI apps" when none is installed. */
    fun label(installed: List<Installed>): String =
        if (installed.isEmpty()) GENERIC_LABEL else installed.joinToString(" / ") { it.app.label }

    const val GENERIC_LABEL = "AI apps"

    private fun uid(context: Context, packageName: String): Int? = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        info.uid
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
