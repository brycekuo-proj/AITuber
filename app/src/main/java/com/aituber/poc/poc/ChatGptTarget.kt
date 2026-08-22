package com.aituber.poc.poc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object ChatGptTarget {
    const val packageName = "com.openai.chatgpt"
    const val label = "ChatGPT ($packageName)"

    fun uid(context: Context): Int? {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            applicationInfo.uid
        } catch (notFound: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun isInstalled(context: Context): Boolean = uid(context) != null
}
