package com.aituber.poc.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aituber.poc.poc.AiTargetApp
import com.aituber.poc.poc.AiTargetApps
import com.aituber.poc.settings.AituberSettings

/** The few dialogs of the launch flow, kept out of MainActivity. Main thread only. */
internal object LaunchDialogs {

    /**
     * Explains what is still missing before the character can float over an AI app,
     * and offers the next step. Shown only while something is missing.
     */
    fun guide(
        activity: Activity,
        checklist: LaunchChecklist,
        microphoneBlocked: Boolean,
        onProceed: (LaunchStep) -> Unit,
        onSkipNotifications: () -> Unit,
        onCancel: () -> Unit
    ): AlertDialog {
        val next = checklist.nextStep
        val body = buildString {
            append("只需要設定一次，之後按「啟動 AITuber」就會直接進到 AI App。\n")
            checklist.steps.forEach { step ->
                val mark = when {
                    checklist.isDone(step) -> "✅"
                    step == next -> "👉"
                    else -> "⬜"
                }
                append("\n").append(mark).append(" ").append(title(step)).append("\n")
                append(explanation(step)).append("\n")
            }
            hint(next, microphoneBlocked)?.let { append("\n").append(it) }
        }
        val positive = when (next) {
            LaunchStep.OVERLAY -> "前往設定"
            LaunchStep.MICROPHONE -> if (microphoneBlocked) "打開 App 設定" else "允許麥克風"
            LaunchStep.NOTIFICATIONS -> "允許通知"
            LaunchStep.READY -> "開始"
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle("啟動前準備")
            .setMessage(body)
            .setPositiveButton(positive) { _, _ -> onProceed(next) }
            .setOnCancelListener { onCancel() }
        if (next == LaunchStep.NOTIFICATIONS) {
            builder.setNegativeButton("略過") { _, _ -> onSkipNotifications() }
        } else {
            builder.setNegativeButton("取消") { _, _ -> onCancel() }
        }
        return builder.show()
    }

    private fun title(step: LaunchStep): String = when (step) {
        LaunchStep.OVERLAY -> "顯示在其他 App 上層"
        LaunchStep.MICROPHONE -> "麥克風"
        LaunchStep.NOTIFICATIONS -> "通知（建議）"
        LaunchStep.READY -> ""
    }

    private fun explanation(step: LaunchStep): String = when (step) {
        LaunchStep.OVERLAY -> "讓角色浮在 ChatGPT 等 AI App 上面。"
        LaunchStep.MICROPHONE -> "只用來讀取手機正在播放的聲音大小，讓角色對嘴；不會錄下你說的話。"
        LaunchStep.NOTIFICATIONS -> "通知列會有「收起／顯示角色」和「停止」按鈕。"
        LaunchStep.READY -> ""
    }

    private fun hint(next: LaunchStep, microphoneBlocked: Boolean): String? = when (next) {
        LaunchStep.OVERLAY -> "按「前往設定」，在清單中找到「AITuber PoC」打開開關，再按返回。"
        LaunchStep.MICROPHONE -> if (microphoneBlocked) {
            "麥克風權限被關掉了：請在 App 設定的「權限」裡允許麥克風，再按返回。"
        } else {
            null
        }
        else -> null
    }

    /** Settings behind the gear button. Saved when the user taps 完成. */
    fun settings(activity: Activity, onOpenSystemSettings: () -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val installed = AiTargetApps.installed(activity).map { it.app }
        var chosenApp: AiTargetApp? = AituberSettings.aiApp(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        fun note(text: String) = TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.rgb(110, 110, 110))
            setPadding(dp(32), 0, 0, dp(12))
        }

        val autoOpen = CheckBox(activity).apply {
            text = "啟動後自動打開 AI App"
            isChecked = AituberSettings.autoOpenAiApp(activity)
        }
        val appPicker = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.rgb(36, 107, 253))
            setPadding(dp(32), 0, 0, dp(12))
        }
        fun refreshAppPicker() {
            appPicker.text = when {
                installed.isEmpty() -> "沒有偵測到支援的 AI App（ChatGPT、Gemini、Claude…）"
                installed.size == 1 -> "會打開：${chosenApp?.label}"
                else -> "會打開：${chosenApp?.label}　（點這裡更換）"
            }
            appPicker.isClickable = installed.size > 1
            appPicker.alpha = if (autoOpen.isChecked) 1f else 0.4f
        }
        appPicker.setOnClickListener {
            if (installed.size <= 1) return@setOnClickListener
            val labels = installed.map { it.label }.toTypedArray()
            val checked = installed.indexOfFirst { it.packageName == chosenApp?.packageName }
            AlertDialog.Builder(activity)
                .setTitle("要打開哪個 AI App？")
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    chosenApp = installed[which]
                    refreshAppPicker()
                    dialog.dismiss()
                }
                .show()
        }
        autoOpen.setOnCheckedChangeListener { _, _ -> refreshAppPicker() }
        refreshAppPicker()

        val autoHide = CheckBox(activity).apply {
            text = "自動收起角色"
            isChecked = AituberSettings.autoHideCharacter(activity)
        }
        val precise = CheckBox(activity).apply {
            text = "精準偵測（進階）"
            isChecked = AituberSettings.preciseCapture(activity)
        }

        root.addView(autoOpen)
        root.addView(appPicker)
        root.addView(autoHide)
        root.addView(note("對話停下來 15 秒後縮成小泡泡，AI 一開口會自己跳出來。"))
        root.addView(precise)
        root.addView(note("每次啟動都要按一次「開始錄製」，但能偵測 AI 朗讀文字。下次啟動生效。"))

        AlertDialog.Builder(activity)
            .setTitle("AITuber 設定")
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton("完成") { _, _ ->
                AituberSettings.setAutoOpenAiApp(activity, autoOpen.isChecked)
                chosenApp?.let { AituberSettings.setAiAppPackage(activity, it.packageName) }
                AituberSettings.setAutoHideCharacter(activity, autoHide.isChecked)
                AituberSettings.setPreciseCapture(activity, precise.isChecked)
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("系統設定") { _, _ -> onOpenSystemSettings() }
            .show()
    }

    /** Tapping 啟動 while the character is already floating: jump back in, switch, or stop. */
    fun running(
        activity: Activity,
        aiAppLabel: String?,
        switchToName: String?,
        onOpenAiApp: () -> Unit,
        onSwitch: () -> Unit,
        onStop: () -> Unit
    ) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        if (aiAppLabel != null) {
            labels += "回到 $aiAppLabel"
            actions += onOpenAiApp
        }
        if (switchToName != null) {
            labels += "換成「$switchToName」"
            actions += onSwitch
        }
        labels += "停止 AITuber"
        actions += onStop
        AlertDialog.Builder(activity)
            .setTitle("AITuber 正在執行")
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton("取消", null)
            .show()
    }
}
