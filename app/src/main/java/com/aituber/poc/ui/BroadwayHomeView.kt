package com.aituber.poc.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DOverlayView

class BroadwayHomeView(
    context: Context,
    initialProfile: Live2DCharacterProfile,
    private val onPreviewChanged: (Live2DCharacterProfile) -> Unit,
    private val onLaunch: (Live2DCharacterProfile) -> Unit,
    private val onDiamondAdd: () -> Unit,
    private val onSettings: () -> Unit,
    private val onMyCharacters: () -> Unit,
    private val onCharacterShop: () -> Unit
) : LinearLayout(context) {

    var previewProfile: Live2DCharacterProfile = initialProfile
        private set

    private val stageHost = FrameLayout(context)
    private val characterName = TextView(context)
    private var previewView: Live2DOverlayView? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(12), dp(16), dp(18))
        background = verticalGradient(0xFF21080EL.toInt(), 0xFF3A0B16L.toInt())

        addView(buildTopBar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))
        addView(buildMarquee(), LayoutParams(LayoutParams.MATCH_PARENT, dp(76)))
        addView(buildTabs(), LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))

        addView(buildStageArea(), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(10)
            bottomMargin = dp(12)
        })

        addView(
            broadwayButton("啟動 AITuber", prominent = true) { onLaunch(previewProfile) },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(58))
        )

        renderPreview(previewProfile)
    }

    fun resumePreview() {
        previewView?.onResume()
    }

    fun pausePreview() {
        previewView?.onPause()
    }

    fun releasePreview() {
        val old = previewView
        previewView = null
        old?.release()
        stageHost.removeAllViews()
    }

    private fun buildTopBar(): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(context).apply {
                text = "💎 0"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(CREAM)
                typeface = Typeface.DEFAULT_BOLD
                background = roundedBox(GOLD_DARK, GOLD, dp(18), dp(1))
                setPadding(dp(14), 0, dp(14), 0)
            }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)))

            addView(
                broadwayButton("+", compact = true, contentDescriptionText = "增加鑽石") { onDiamondAdd() },
                LayoutParams(dp(44), dp(40)).apply { marginStart = dp(8) }
            )

            addView(View(context), LayoutParams(0, 1, 1f))

            addView(
                broadwayButton("Settings", compact = true) { onSettings() },
                LayoutParams(dp(110), dp(40))
            )
        }
    }

    private fun buildMarquee(): View {
        return FrameLayout(context).apply {
            background = roundedBox(0xFF4F0E1CL.toInt(), GOLD, dp(14), dp(2))
            addView(TextView(context).apply {
                text = "AITuber"
                textSize = 31f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFE7A8L.toInt())
                typeface = Typeface.create("serif", Typeface.BOLD)
                letterSpacing = 0.08f
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    private fun buildTabs(): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            addView(
                broadwayButton("我的角色") { onMyCharacters() },
                LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) }
            )
            addView(
                broadwayButton("角色商城") { onCharacterShop() },
                LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) }
            )
        }
    }

    private fun buildStageArea(): View {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            background = roundedBox(STAGE_RED, GOLD, dp(18), dp(2))
            setPadding(dp(10), dp(10), dp(10), dp(10))

            addView(TextView(context).apply {
                text = "✦  ✦  ✦  THE AITUBER STAGE  ✦  ✦  ✦"
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(0xFFFFD978L.toInt())
                typeface = Typeface.DEFAULT_BOLD
                background = roundedBox(0xFF6E1424L.toInt(), 0xFFB9822FL.toInt(), dp(10), dp(1))
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(34)))

            val selector = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }

            selector.addView(
                broadwayButton("‹", compact = true, contentDescriptionText = "上一個角色") {
                    switchPreview(Live2DCharacterProfiles.previous(previewProfile.id))
                },
                LayoutParams(dp(54), dp(74)).apply { marginEnd = dp(8) }
            )

            stageHost.apply {
                background = verticalGradient(0xFF16080CL.toInt(), 0xFF301019L.toInt())
                clipChildren = true
                clipToPadding = true
            }
            selector.addView(stageHost, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

            selector.addView(
                broadwayButton("›", compact = true, contentDescriptionText = "下一個角色") {
                    switchPreview(Live2DCharacterProfiles.next(previewProfile.id))
                },
                LayoutParams(dp(54), dp(74)).apply { marginStart = dp(8) }
            )
            addView(selector, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(8)
            })

            characterName.apply {
                gravity = Gravity.CENTER
                textSize = 20f
                setTextColor(CREAM)
                typeface = Typeface.create("serif", Typeface.BOLD)
                background = roundedBox(0xFF351018L.toInt(), GOLD_DARK, dp(12), dp(1))
            }
            addView(characterName, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
                topMargin = dp(8)
            })

            addView(View(context).apply {
                background = verticalGradient(0xFF8A5731L.toInt(), 0xFF4C2A1DL.toInt())
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(20)).apply {
                topMargin = dp(6)
            })
        }
    }

    private fun switchPreview(profile: Live2DCharacterProfile) {
        if (profile.id == previewProfile.id) return
        previewProfile = profile
        renderPreview(profile)
        onPreviewChanged(profile)
    }

    private fun renderPreview(profile: Live2DCharacterProfile) {
        val old = previewView
        previewView = null
        old?.release()
        stageHost.removeAllViews()

        characterName.text = profile.displayName
        val live2d = Live2DOverlayView(context, profile = profile) {
            characterName.text = "${profile.displayName} · Preview unavailable"
        }
        previewView = live2d
        stageHost.addView(
            live2d,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        live2d.postDelayed({
            if (previewView === live2d && profile.capabilities.idleMotion) {
                live2d.startIdleMotionForDebug()
            }
        }, 350L)
    }

    private fun broadwayButton(
        label: String,
        compact: Boolean = false,
        prominent: Boolean = false,
        contentDescriptionText: String? = null,
        action: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            contentDescription = contentDescriptionText ?: label
            isAllCaps = false
            textSize = when {
                prominent -> 19f
                compact -> 15f
                else -> 16f
            }
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(CREAM)
            setPadding(dp(10), 0, dp(10), 0)
            background = roundedBox(
                if (prominent) 0xFF8D1D2EL.toInt() else BUTTON_RED,
                if (prominent) 0xFFF0C66AL.toInt() else GOLD_DARK,
                dp(if (compact) 12 else 14),
                dp(if (prominent) 2 else 1)
            )
            elevation = dp(if (prominent) 6 else 3).toFloat()
            stateListAnimator = null
            setOnClickListener { action() }
            installBroadwayPressFeedback()
        }
    }

    private fun roundedBox(fill: Int, stroke: Int, radius: Int, strokeWidth: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radius.toFloat()
            setStroke(strokeWidth, stroke)
        }
    }

    private fun verticalGradient(top: Int, bottom: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(top, bottom)
        ).apply {
            shape = GradientDrawable.RECTANGLE
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private val CREAM = Color.rgb(255, 241, 211)
        private val GOLD = Color.rgb(224, 178, 82)
        private val GOLD_DARK = Color.rgb(155, 108, 42)
        private val STAGE_RED = Color.rgb(74, 12, 26)
        private val BUTTON_RED = Color.rgb(92, 20, 34)
    }
}
