package com.aituber.poc.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DCharacterProfiles

/**
 * "我的角色": every character on the phone. Tap one to put it on the stage; downloaded
 * characters can be deleted here. Main thread only.
 */
internal object MyCharactersDialog {

    fun show(
        activity: Activity,
        currentId: String,
        onPick: (Live2DCharacterProfile) -> Unit,
        /** Runs right before a character's files are deleted: move the stage off it. */
        releaseUsers: (Live2DCharacterProfile) -> Unit,
        onDeleted: () -> Unit,
        onOpenStore: () -> Unit
    ): AlertDialog {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        var dialog: AlertDialog? = null
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }

        Live2DCharacterProfiles.all.forEach { profile ->
            val downloaded = DownloadedCharacters.isDownloaded(profile)
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(6), dp(10))
                isClickable = true
                isFocusable = true
                contentDescription = "選擇 ${profile.displayName}"
                setOnClickListener {
                    dialog?.dismiss()
                    onPick(profile)
                }
                installBroadwayPressFeedback()
            }
            val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(activity).apply {
                text = if (profile.id == currentId) "${profile.displayName}（舞台上）" else profile.displayName
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(40, 24, 30))
            })
            texts.addView(TextView(activity).apply {
                text = if (downloaded) {
                    "已下載 · ${DownloadedCharacters.formatSize(DownloadedCharacters.sizeBytes(activity, profile.id))}"
                } else {
                    "內建角色"
                }
                textSize = 13f
                setTextColor(Color.rgb(120, 110, 115))
            })
            row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            if (downloaded) {
                row.addView(
                    TextView(activity).apply {
                        text = "刪除"
                        textSize = 15f
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(10).toFloat()
                            setColor(Color.rgb(176, 48, 52))
                        }
                        setPadding(dp(14), dp(8), dp(14), dp(8))
                        isClickable = true
                        isFocusable = true
                        contentDescription = "刪除 ${profile.displayName}"
                        setOnClickListener {
                            DownloadedCharacters.confirmDelete(
                                activity,
                                profile,
                                releaseUsers = { releaseUsers(profile) },
                                onDone = {
                                    dialog?.dismiss()
                                    onDeleted()
                                }
                            )
                        }
                        installBroadwayPressFeedback()
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            list.addView(row)
            list.addView(View(activity).apply { setBackgroundColor(Color.rgb(230, 224, 226)) }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ))
        }

        if (DownloadedCharacters.list().isEmpty()) {
            list.addView(TextView(activity).apply {
                text = "還沒有下載的角色。到角色商城按「下載」就能加入；下載的角色可以在這裡刪除。"
                textSize = 13f
                setTextColor(Color.rgb(120, 110, 115))
                setPadding(dp(10), dp(12), dp(10), dp(4))
            })
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle("我的角色")
            .setView(ScrollView(activity).apply { addView(list) })
            .setPositiveButton("關閉", null)
            .setNeutralButton("角色商城") { _, _ -> onOpenStore() }
            .show()
        return dialog
    }
}
