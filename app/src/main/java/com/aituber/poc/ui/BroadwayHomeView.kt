package com.aituber.poc.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.aituber.poc.R
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DOverlayView
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Broadway home rebuilt from the approved Canva artwork.
 *
 * The artwork supplies the theatre, curtains, stage, lights and button chrome.
 * Android only adds Live2D plus transparent/visual hit layers, so the home does
 * not drift back toward generic Cards / rounded engineering buttons.
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
    private var previewView: Live2DOverlayView? = null
    private var insetTopPx = 0
    private var insetBottomPx = 0

    private data class DesignRect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float
    )

    init {
        setBackgroundColor(Color.rgb(24, 2, 7))
        clipChildren = false
        clipToPadding = false

        designCanvas.clipChildren = false
        designCanvas.clipToPadding = false
        addView(
            designCanvas,
            LayoutParams(1, 1).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        )

        designCanvas.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.aituber_broadway_reference_bg)
                scaleType = ImageView.ScaleType.FIT_XY
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        stageHost.apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = true
            clipToPadding = true
        }
        addAt(
            stageHost,
            left = 184f,
            top = 298f,
            width = 496f,
            height = 782f
        )

        // Diamond count is dynamic; the gem + meter are part of the approved artwork.
        addAt(
            TextView(context).apply {
                text = "0"
                gravity = Gravity.CENTER
                textSize = 22f
                setTextColor(Color.WHITE)
                setShadowLayer(3f, 0f, 2f, 0xCC12346DL.toInt())
                includeFontPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            left = 528f,
            top = 31f,
            width = 94f,
            height = 71f
        )

        addAt(
            artworkButton(
                drawableRes = R.drawable.aituber_broadway_plus,
                contentDescriptionText = "增加鑽石",
                action = onDiamondAdd
            ),
            left = 620f,
            top = 27f,
            width = 68f,
            height = 72f
        )

        addAt(
            artworkButton(
                drawableRes = R.drawable.aituber_broadway_settings,
                contentDescriptionText = "Settings",
                action = onSettings
            ),
            left = 720f,
            top = 8f,
            width = 122f,
            height = 112f
        )

        addAt(
            artworkButton(
                drawableRes = R.drawable.aituber_broadway_tab_char,
                label = "我的角色",
                labelTextSize = 22f,
                labelColor = Color.WHITE,
                labelShadowColor = 0xD72066B5.toInt(),
                contentDescriptionText = "我的角色",
                action = onMyCharacters
            ),
            left = 18f,
            top = 112f,
            width = 430f,
            height = 168f
        )

        addAt(
            artworkButton(
                drawableRes = R.drawable.aituber_broadway_tab_shop,
                label = "角色商城",
                labelTextSize = 22f,
                labelColor = Color.WHITE,
                labelShadowColor = 0xD72066B5.toInt(),
                contentDescriptionText = "角色商城",
                action = onCharacterShop
            ),
            left = 442f,
            top = 112f,
            width = 408f,
            height = 163f
        )

        addAt(
            artworkButton(
                drawableRes = R.drawable.aituber_broadway_arrow_left,
                contentDescriptionText = "上一個角色"
            ) {
                switchPreview(Live2DCharacterProfiles.previous(previewProfile.id))
            },
            left = 25f,
            top = 625f,
            width = 180f,
            height = 185f
        )

        addAt(
            artworkButton(
                drawableRes = R.drawable.aituber_broadway_arrow_right,
                contentDescriptionText = "下一個角色"
            ) {
                switchPreview(Live2DCharacterProfiles.next(previewProfile.id))
            },
            left = 660f,
            top = 625f,
            width = 180f,
            height = 185f
        )

        addAt(
            artworkButton(
                drawableRes = R.drawable.aituber_broadway_launch,
                label = "啟動 AITuber",
                labelTextSize = 26f,
                labelColor = 0xFF9A5600.toInt(),
                labelShadowColor = 0x66FFFFFF,
                contentDescriptionText = "啟動 AITuber"
            ) {
                onLaunch(previewProfile)
            },
            left = 225f,
            top = 1308f,
            width = 420f,
            height = 187f
        )

        designCanvas.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            relayoutDesignChildren()
        }
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            layoutDesignCanvas()
        }

        setOnApplyWindowInsetsListener { _, insets ->
            insetTopPx = insets.systemWindowInsetTop
            insetBottomPx = insets.systemWindowInsetBottom
            layoutDesignCanvas()
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

    private fun layoutDesignCanvas() {
        val rootWidth = width
        val rootHeight = height
        if (rootWidth <= 0 || rootHeight <= 0) return

        val availableHeight = (rootHeight - insetTopPx - insetBottomPx).coerceAtLeast(1)
        val scale = min(
            rootWidth / DESIGN_WIDTH,
            availableHeight / DESIGN_HEIGHT
        )
        val canvasWidth = (DESIGN_WIDTH * scale).roundToInt().coerceAtLeast(1)
        val canvasHeight = (DESIGN_HEIGHT * scale).roundToInt().coerceAtLeast(1)
        val top = insetTopPx + ((availableHeight - canvasHeight) / 2)

        val lp = designCanvas.layoutParams as LayoutParams
        if (lp.width != canvasWidth || lp.height != canvasHeight || lp.topMargin != top) {
            lp.width = canvasWidth
            lp.height = canvasHeight
            lp.topMargin = top
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            designCanvas.layoutParams = lp
        }
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

        val live2d = Live2DOverlayView(context, profile = profile)
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

    private fun artworkButton(
        drawableRes: Int,
        label: String? = null,
        labelTextSize: Float = 20f,
        labelColor: Int = Color.WHITE,
        labelShadowColor: Int = 0x99000000.toInt(),
        contentDescriptionText: String,
        action: () -> Unit
    ): FrameLayout {
        return FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            contentDescription = contentDescriptionText
            background = null
            foreground = null
            stateListAnimator = null

            val artwork = ImageView(context).apply {
                setImageResource(drawableRes)
                scaleType = ImageView.ScaleType.FIT_XY
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(
                artwork,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )

            if (label != null) {
                addView(
                    TextView(context).apply {
                        text = label
                        gravity = Gravity.CENTER
                        textSize = labelTextSize
                        setTextColor(labelColor)
                        setShadowLayer(3f, 0f, 2f, labelShadowColor)
                        includeFontPadding = false
                        isClickable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                )
            }

            setOnClickListener { action() }
            installArtworkPressFeedback(artwork)
        }
    }

    private fun View.installArtworkPressFeedback(artwork: ImageView) {
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    animate().cancel()
                    animate()
                        .scaleX(PRESSED_SCALE)
                        .scaleY(PRESSED_SCALE)
                        .translationY(dp(4).toFloat())
                        .setDuration(PRESS_DOWN_MS)
                        .start()
                    artwork.setColorFilter(0xFFD7D7D7.toInt(), PorterDuff.Mode.MULTIPLY)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    animate().cancel()
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(PRESS_UP_MS)
                        .start()
                    artwork.clearColorFilter()
                }
            }
            false
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val DESIGN_WIDTH = 864f
        private const val DESIGN_HEIGHT = 1536f
        private const val PRESSED_SCALE = 0.965f
        private const val PRESS_DOWN_MS = 60L
        private const val PRESS_UP_MS = 120L
    }
}
