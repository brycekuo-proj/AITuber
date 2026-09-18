package com.aituber.poc.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.aituber.poc.R
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DOverlayView
import kotlin.math.roundToInt

/**
 * Broadway home v2.
 *
 * The visual is a single high-resolution theatre artwork. Interactive Android views are layered
 * over the exact artwork coordinates so the screen does not fall back to "cards with gold borders".
 * Live2D is restricted to the stage opening; navigation only changes preview until Launch is tapped.
 */
class BroadwayHomeView(
    context: Context,
    initialProfile: Live2DCharacterProfile,
    private val onPreviewChanged: (Live2DCharacterProfile) -> Unit,
    private val onLaunch: (Live2DCharacterProfile) -> Unit,
    private val onDiamondAdd: () -> Unit,
    private val onSettings: () -> Unit,
    private val onMyCharacters: () -> Unit,
    private val onCharacterShop: () -> Unit
) : FrameLayout(context) {

    var previewProfile: Live2DCharacterProfile = initialProfile
        private set

    private val designCanvas = FrameLayout(context)
    private val stageHost = FrameLayout(context)
    private val characterName = TextView(context)
    private var previewView: Live2DOverlayView? = null

    private data class DesignRect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float
    )

    init {
        setBackgroundColor(BACKGROUND)
        clipChildren = false
        clipToPadding = false

        addView(
            designCanvas,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        designCanvas.clipChildren = false
        designCanvas.clipToPadding = false

        designCanvas.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.aituber_broadway_stage_v2)
                scaleType = ImageView.ScaleType.FIT_XY
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        addAt(
            TextView(context).apply {
                text = "💎 0"
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = badgeBackground()
            },
            left = 14f,
            top = 8f,
            width = 65f,
            height = 38f
        )

        addAt(
            stageButton(
                label = "+",
                textSize = 22f,
                compact = true,
                contentDescriptionText = "增加鑽石",
                action = onDiamondAdd
            ),
            left = 84f,
            top = 8f,
            width = 42f,
            height = 38f
        )

        addAt(
            stageButton(
                label = "Settings",
                textSize = 14f,
                compact = true,
                action = onSettings
            ),
            left = 252f,
            top = 8f,
            width = 94f,
            height = 38f
        )

        addAt(
            TextView(context).apply {
                text = "AITuber"
                gravity = Gravity.CENTER
                textSize = 30f
                setTextColor(TITLE_CREAM)
                typeface = Typeface.create("serif", Typeface.BOLD)
                letterSpacing = 0.075f
                includeFontPadding = false
            },
            left = 54f,
            top = 48f,
            width = 252f,
            height = 64f
        )

        addAt(
            stageButton(
                label = "我的角色",
                textSize = 15f,
                selected = true,
                action = onMyCharacters
            ),
            left = 58f,
            top = 132f,
            width = 112f,
            height = 36f
        )

        addAt(
            stageButton(
                label = "角色商城",
                textSize = 15f,
                action = onCharacterShop
            ),
            left = 190f,
            top = 132f,
            width = 112f,
            height = 36f
        )

        addAt(
            TextView(context).apply {
                text = "✦  THE AITUBER STAGE  ✦"
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(STAGE_GOLD)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                letterSpacing = 0.035f
            },
            left = 92f,
            top = 181f,
            width = 176f,
            height = 27f
        )

        stageHost.apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = true
            clipToPadding = true
        }
        addAt(
            stageHost,
            left = 84f,
            top = 255f,
            width = 192f,
            height = 274f
        )

        addAt(
            stageButton(
                label = "‹",
                textSize = 32f,
                compact = true,
                contentDescriptionText = "上一個角色"
            ) {
                switchPreview(Live2DCharacterProfiles.previous(previewProfile.id))
            },
            left = 24f,
            top = 340f,
            width = 42f,
            height = 58f
        )

        addAt(
            stageButton(
                label = "›",
                textSize = 32f,
                compact = true,
                contentDescriptionText = "下一個角色"
            ) {
                switchPreview(Live2DCharacterProfiles.next(previewProfile.id))
            },
            left = 294f,
            top = 340f,
            width = 42f,
            height = 58f
        )

        characterName.apply {
            gravity = Gravity.CENTER
            textSize = 19f
            setTextColor(TITLE_CREAM)
            typeface = Typeface.create("serif", Typeface.BOLD)
            includeFontPadding = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        addAt(
            characterName,
            left = 84f,
            top = 588f,
            width = 192f,
            height = 36f
        )

        addAt(
            stageButton(
                label = "啟動 AITuber",
                textSize = 19f,
                prominent = true
            ) {
                onLaunch(previewProfile)
            },
            left = 43f,
            top = 644f,
            width = 274f,
            height = 50f
        )

        designCanvas.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            relayoutDesignChildren()
        }

        setOnApplyWindowInsetsListener { _, insets ->
            val lp = designCanvas.layoutParams as LayoutParams
            val nextTop = insets.systemWindowInsetTop
            val nextBottom = insets.systemWindowInsetBottom
            if (lp.topMargin != nextTop || lp.bottomMargin != nextBottom) {
                lp.topMargin = nextTop
                lp.bottomMargin = nextBottom
                designCanvas.layoutParams = lp
            }
            insets
        }
        post { requestApplyInsets() }

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

    private fun addAt(
        view: View,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        view.tag = DesignRect(left, top, width, height)
        designCanvas.addView(view, LayoutParams(1, 1))
    }

    private fun relayoutDesignChildren() {
        val canvasWidth = designCanvas.width
        val canvasHeight = designCanvas.height
        if (canvasWidth <= 0 || canvasHeight <= 0) return

        val scaleX = canvasWidth / DESIGN_WIDTH
        val scaleY = canvasHeight / DESIGN_HEIGHT

        for (index in 0 until designCanvas.childCount) {
            val child = designCanvas.getChildAt(index)
            val spec = child.tag as? DesignRect ?: continue
            val lp = child.layoutParams as LayoutParams
            lp.leftMargin = (spec.left * scaleX).roundToInt()
            lp.topMargin = (spec.top * scaleY).roundToInt()
            lp.width = (spec.width * scaleX).roundToInt().coerceAtLeast(1)
            lp.height = (spec.height * scaleY).roundToInt().coerceAtLeast(1)
            child.layoutParams = lp
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
            characterName.text = profile.displayName
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

    private fun stageButton(
        label: String,
        textSize: Float,
        compact: Boolean = false,
        prominent: Boolean = false,
        selected: Boolean = false,
        contentDescriptionText: String? = null,
        action: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            contentDescription = contentDescriptionText ?: label
            this.textSize = textSize
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            setTextColor(if (prominent) Color.WHITE else TITLE_CREAM)
            setPadding(dp(if (compact) 4 else 8), 0, dp(if (compact) 4 else 8), 0)
            background = buttonBackground(prominent = prominent, selected = selected)
            elevation = dp(if (prominent) 6 else 3).toFloat()
            stateListAnimator = null
            setOnClickListener { action() }
            installBroadwayPressFeedback()
        }
    }

    private fun badgeBackground(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFFD2A13FL.toInt(), 0xFF986823L.toInt())
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0xFFF6D88AL.toInt())
        }

    private fun buttonBackground(prominent: Boolean, selected: Boolean): StateListDrawable {
        val normalTop: Int
        val normalBottom: Int
        val stroke: Int
        val strokeWidth: Int

        when {
            prominent -> {
                normalTop = 0xFFB32743L.toInt()
                normalBottom = 0xFF7D1429L.toInt()
                stroke = 0xFFF3CE73L.toInt()
                strokeWidth = dp(2)
            }
            selected -> {
                normalTop = 0xFF8B1832L.toInt()
                normalBottom = 0xFF601020L.toInt()
                stroke = 0xFFE5B657L.toInt()
                strokeWidth = dp(1)
            }
            else -> {
                normalTop = 0xFF671226L.toInt()
                normalBottom = 0xFF480B18L.toInt()
                stroke = 0xFFB67A2CL.toInt()
                strokeWidth = dp(1)
            }
        }

        val pressedTop = darken(normalTop, 0.78f)
        val pressedBottom = darken(normalBottom, 0.72f)
        val pressedStroke = darken(stroke, 0.82f)

        val pressed = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(pressedTop, pressedBottom)
        ).apply {
            cornerRadius = dp(if (prominent) 17 else 14).toFloat()
            setStroke(strokeWidth, pressedStroke)
        }

        val normal = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(normalTop, normalBottom)
        ).apply {
            cornerRadius = dp(if (prominent) 17 else 14).toFloat()
            setStroke(strokeWidth, stroke)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun darken(color: Int, factor: Float): Int =
        Color.rgb(
            (Color.red(color) * factor).roundToInt().coerceIn(0, 255),
            (Color.green(color) * factor).roundToInt().coerceIn(0, 255),
            (Color.blue(color) * factor).roundToInt().coerceIn(0, 255)
        )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val DESIGN_WIDTH = 360f
        private const val DESIGN_HEIGHT = 700f
        private val BACKGROUND = Color.rgb(20, 3, 8)
        private val TITLE_CREAM = Color.rgb(255, 238, 196)
        private val STAGE_GOLD = Color.rgb(246, 205, 104)
    }
}
