package com.aituber.poc.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DOverlayView
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Broadway home rebuilt as independent runtime layers.
 *
 * The approved Canva screen is reference-only. The app does not draw the flat
 * Canva image. Theatre scenery and all controls are separate resolution-
 * independent Android views, so the UI stays sharp and every button keeps its
 * own hit area, pressed state and callback.
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
    private var previewResumed = false
    private var previewReleaseInFlight = false
    private var previewDisposed = false
    private var previewRequestSerial = 0L
    private var insetTopPx = 0
    private var insetBottomPx = 0

    private data class DesignRect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float
    )

    init {
        setBackgroundColor(Color.rgb(20, 1, 7))
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

        // Back-to-front scene composition. Each visual layer is independent.
        addSceneryLayer(BroadwaySceneryLayerView.Layer.BACKDROP)
        addSceneryLayer(BroadwaySceneryLayerView.Layer.LIGHTS)
        addSceneryLayer(BroadwaySceneryLayerView.Layer.FLOOR)

        stageHost.apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = true
            clipToPadding = true
        }
        addAt(
            stageHost,
            left = 164f,
            top = 284f,
            width = 536f,
            height = 806f
        )
        stageHost.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            ensurePreviewAttached()
        }

        // Foreground theatre pieces can overlap the avatar naturally.
        addSceneryLayer(BroadwaySceneryLayerView.Layer.CURTAINS)
        addSceneryLayer(BroadwaySceneryLayerView.Layer.FOREGROUND)

        // Diamond meter is decorative/status only; + is a separate button.
        addAt(
            BroadwayControlView(
                context = context,
                kind = BroadwayControlView.Kind.DIAMOND_METER,
                label = "0"
            ),
            left = 500f,
            top = 24f,
            width = 154f,
            height = 82f
        )

        addAt(
            controlButton(
                kind = BroadwayControlView.Kind.PLUS,
                description = "增加鑽石",
                action = onDiamondAdd
            ),
            left = 644f,
            top = 24f,
            width = 72f,
            height = 78f
        )

        addAt(
            controlButton(
                kind = BroadwayControlView.Kind.SETTINGS,
                description = "Settings",
                action = onSettings
            ),
            left = 742f,
            top = 13f,
            width = 98f,
            height = 98f
        )

        addAt(
            controlButton(
                kind = BroadwayControlView.Kind.TAB,
                label = "我的角色",
                description = "我的角色",
                action = onMyCharacters
            ),
            left = 34f,
            top = 126f,
            width = 380f,
            height = 124f
        )

        addAt(
            controlButton(
                kind = BroadwayControlView.Kind.TAB,
                label = "角色商城",
                description = "角色商城",
                action = onCharacterShop
            ),
            left = 450f,
            top = 126f,
            width = 380f,
            height = 124f
        )

        addAt(
            controlButton(
                kind = BroadwayControlView.Kind.ARROW_LEFT,
                description = "上一個角色"
            ) {
                switchPreview(Live2DCharacterProfiles.previous(previewProfile.id))
            },
            left = 32f,
            top = 610f,
            width = 160f,
            height = 166f
        )

        addAt(
            controlButton(
                kind = BroadwayControlView.Kind.ARROW_RIGHT,
                description = "下一個角色"
            ) {
                switchPreview(Live2DCharacterProfiles.next(previewProfile.id))
            },
            left = 672f,
            top = 610f,
            width = 160f,
            height = 166f
        )

        addAt(
            controlButton(
                kind = BroadwayControlView.Kind.LAUNCH,
                label = "啟動 AITuber",
                description = "啟動 AITuber"
            ) {
                onLaunch(previewProfile)
            },
            left = 214f,
            top = 1310f,
            width = 436f,
            height = 150f
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

        post { ensurePreviewAttached() }
    }

    fun resumePreview() {
        if (previewDisposed) return
        previewResumed = true
        ensurePreviewAttached()
        previewView?.onResume()
    }

    fun pausePreview() {
        previewResumed = false
        previewView?.onPause()
    }

    fun releasePreview() {
        previewDisposed = true
        previewResumed = false
        previewReleaseInFlight = false
        previewRequestSerial += 1L
        val old = previewView
        previewView = null
        stageHost.removeAllViews()
        old?.release()
    }

    private fun addSceneryLayer(layer: BroadwaySceneryLayerView.Layer) {
        designCanvas.addView(
            BroadwaySceneryLayerView(context, layer),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    private fun controlButton(
        kind: BroadwayControlView.Kind,
        label: String? = null,
        description: String,
        action: () -> Unit
    ): BroadwayControlView {
        return BroadwayControlView(
            context = context,
            kind = kind,
            label = label
        ).apply {
            contentDescription = description
            setOnClickListener { action() }
        }
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
        val requestSerial = ++previewRequestSerial
        val old = previewView
        previewView = null
        stageHost.removeAllViews()

        if (old == null) {
            if (!previewReleaseInFlight) {
                attachPreview(profile, requestSerial)
            }
            return
        }

        // Cubism runtime is process-global; release old preview before attaching next.
        previewReleaseInFlight = true
        old.release {
            previewReleaseInFlight = false
            ensurePreviewAttached()
        }
    }

    private fun ensurePreviewAttached() {
        if (previewDisposed || previewReleaseInFlight) return
        if (previewView != null) return
        if (stageHost.width < MIN_PREVIEW_EDGE_PX || stageHost.height < MIN_PREVIEW_EDGE_PX) return
        attachPreview(previewProfile, previewRequestSerial)
    }

    private fun attachPreview(profile: Live2DCharacterProfile, requestSerial: Long) {
        if (previewDisposed || previewReleaseInFlight) return
        if (requestSerial != previewRequestSerial) return
        if (stageHost.width < MIN_PREVIEW_EDGE_PX || stageHost.height < MIN_PREVIEW_EDGE_PX) return
        if (previewView != null) return

        val live2d = Live2DOverlayView(context, profile = profile)
        previewView = live2d
        stageHost.addView(
            live2d,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        if (previewResumed) {
            live2d.onResume()
        }
        live2d.postDelayed({
            if (previewView === live2d && profile.capabilities.idleMotion) {
                live2d.startIdleMotionForDebug()
            }
        }, 350L)
    }

    companion object {
        private const val DESIGN_WIDTH = 864f
        private const val DESIGN_HEIGHT = 1536f
        private const val MIN_PREVIEW_EDGE_PX = 32
    }
}
