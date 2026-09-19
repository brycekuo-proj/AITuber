package com.aituber.poc.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.aituber.poc.R
import com.aituber.poc.character.CharacterDiagnostics
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DOverlayView
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Broadway home rebuilt from independent raster artwork layers.
 *
 * No stage/curtain/button artwork is painted with Android Canvas. Every visible
 * Broadway component comes from a packaged image asset, and every interactive
 * control has a distinct up/down bitmap.
 */
class BroadwayHomeView(
    context: Context,
    initialProfile: Live2DCharacterProfile,
    private val onPreviewChanged: (Live2DCharacterProfile) -> Unit,
    private val onLaunch: (Live2DCharacterProfile) -> Unit,
    private val onCoinAdd: () -> Unit,
    private val onDiamondAdd: () -> Unit,
    private val onSettings: () -> Unit,
    private val onMyCharacters: () -> Unit,
    private val onCharacterShop: () -> Unit,
    private val onBack: () -> Unit,
    private val onHome: () -> Unit
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

    private data class DesignRect(val left: Float, val top: Float, val width: Float, val height: Float)

    init {
        setBackgroundColor(Color.rgb(23, 2, 8))
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

        // New high-resolution Image Generation pass. Keep the full-screen plate,
        // curtain, stage, character preview and controls as independent layers.
        addFullArtwork(R.drawable.broadway_bg_main)
        addAt(artwork(R.drawable.broadway_curtain_top), 0f, 0f, 864f, 358f)
        addAt(artwork(R.drawable.broadway_stage_base), 122f, 930f, 620f, 310f)

        stageHost.apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = true
            clipToPadding = true
        }
        addAt(stageHost, 198f, 292f, 468f, 787f)
        stageHost.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> ensurePreviewAttached() }

        // Top bar: the generated diamond counter is one cohesive group.
        addAt(
            artworkButton(
                R.drawable.broadway_diamond_counter_group_up,
                R.drawable.broadway_diamond_counter_group_down,
                "增加鑽石",
                onDiamondAdd
            ),
            370f, 18f, 306f, 102f
        )
        addAt(
            artworkButton(
                R.drawable.broadway_settings_up,
                R.drawable.broadway_settings_down,
                "Settings",
                onSettings
            ),
            736f, 18f, 110f, 110f
        )

        addAt(
            artworkButton(
                R.drawable.broadway_characters_up,
                R.drawable.broadway_characters_down,
                "Characters",
                onMyCharacters
            ),
            24f, 120f, 396f, 132f
        )
        addAt(
            artworkButton(
                R.drawable.broadway_props_up,
                R.drawable.broadway_props_down,
                "Character Shop",
                onCharacterShop
            ),
            444f, 120f, 396f, 132f
        )

        addAt(
            artworkButton(
                R.drawable.broadway_arrow_left_up,
                R.drawable.broadway_arrow_left_down,
                "上一個角色"
            ) {
                switchPreview(Live2DCharacterProfiles.previous(previewProfile.id))
            },
            32f, 622f, 170f, 170f
        )
        addAt(
            artworkButton(
                R.drawable.broadway_arrow_right_up,
                R.drawable.broadway_arrow_right_down,
                "下一個角色"
            ) {
                switchPreview(Live2DCharacterProfiles.next(previewProfile.id))
            },
            662f, 622f, 170f, 170f
        )

        addAt(
            artworkButton(
                R.drawable.broadway_play_up,
                R.drawable.broadway_play_down,
                "啟動 AITuber"
            ) { onLaunch(previewProfile) },
            234f, 1322f, 396f, 158f
        )
        designCanvas.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> relayoutDesignChildren() }
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutDesignCanvas() }

        setOnApplyWindowInsetsListener { _, insets ->
            insetTopPx = insets.systemWindowInsetTop
            insetBottomPx = insets.systemWindowInsetBottom
            layoutDesignCanvas()
            insets
        }

        logAllBuiltInAssetPreflight()
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
        if (previewDisposed) return
        previewDisposed = true
        previewResumed = false
        previewRequestSerial += 1L
        val old = previewView
        previewView = null
        previewReleaseInFlight = old != null
        if (old == null) {
            stageHost.removeAllViews()
            return
        }
        old.release {
            stageHost.removeView(old)
            previewReleaseInFlight = false
        }
    }

    fun showPreview(profile: Live2DCharacterProfile) {
        if (previewDisposed) return
        if (profile.id == previewProfile.id) {
            ensurePreviewAttached()
            return
        }
        switchPreview(profile)
    }

    private fun addFullArtwork(drawableRes: Int) {
        designCanvas.addView(
            ImageView(context).apply {
                setImageResource(drawableRes)
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = false
                isFocusable = false
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    private fun artwork(drawableRes: Int): ImageView =
        ImageView(context).apply {
            setImageResource(drawableRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }

    private fun artworkButton(
        upRes: Int,
        downRes: Int,
        description: String,
        action: () -> Unit
    ): ImageView {
        val states = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                requireNotNull(context.getDrawable(downRes))
            )
            addState(
                intArrayOf(),
                requireNotNull(context.getDrawable(upRes))
            )
        }
        return ImageView(context).apply {
            setImageDrawable(states)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = description
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            installBroadwayPressFeedback()
        }
    }

    private fun addAt(view: View, left: Float, top: Float, width: Float, height: Float) {
        view.tag = DesignRect(left, top, width, height)
        designCanvas.addView(view, LayoutParams(1, 1))
    }

    private fun layoutDesignCanvas() {
        val rootWidth = width
        val rootHeight = height
        if (rootWidth <= 0 || rootHeight <= 0) return

        val availableHeight = (rootHeight - insetTopPx - insetBottomPx).coerceAtLeast(1)
        val scale = min(rootWidth / DESIGN_WIDTH, availableHeight / DESIGN_HEIGHT)
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
        if (profile.id == previewProfile.id || previewDisposed) return
        previewProfile = profile
        Log.i(TAG, "preview-select id=${profile.id} name=${profile.displayName}")
        renderPreview(profile)
        onPreviewChanged(profile)
    }

    private fun renderPreview(profile: Live2DCharacterProfile) {
        val requestSerial = ++previewRequestSerial
        val old = previewView
        previewView = null

        if (old == null) {
            if (!previewReleaseInFlight) attachPreview(profile, requestSerial)
            return
        }

        // The bundled Cubism native runtime is process-global. Keep the old
        // GLSurfaceView attached until its GL-thread release has actually run,
        // then remove it and attach the next profile.
        previewReleaseInFlight = true
        old.release {
            stageHost.removeView(old)
            previewReleaseInFlight = false
            ensurePreviewAttached()
        }
    }

    private fun ensurePreviewAttached() {
        if (previewDisposed || previewReleaseInFlight || previewView != null) return
        if (stageHost.width < MIN_PREVIEW_EDGE_PX || stageHost.height < MIN_PREVIEW_EDGE_PX) return
        attachPreview(previewProfile, previewRequestSerial)
    }

    private fun attachPreview(profile: Live2DCharacterProfile, requestSerial: Long) {
        if (previewDisposed || previewReleaseInFlight || previewView != null) return
        if (requestSerial != previewRequestSerial) return
        if (stageHost.width < MIN_PREVIEW_EDGE_PX || stageHost.height < MIN_PREVIEW_EDGE_PX) return

        val assetsOk = verifyProfileAssets(profile)
        Log.i(
            TAG,
            "preview-attach id=${profile.id} assetsOk=$assetsOk host=${stageHost.width}x${stageHost.height}"
        )

        val live2d = Live2DOverlayView(
            context,
            profile = profile,
            onRuntimeFailure = { error ->
                Log.e(TAG, "preview-runtime-failure id=${profile.id} error=$error")
            }
        )
        previewView = live2d
        stageHost.addView(
            live2d,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        if (previewResumed) live2d.onResume()

        live2d.postDelayed({
            if (previewView === live2d && profile.capabilities.idleMotion) {
                live2d.startIdleMotionForDebug()
            }
        }, 350L)

        live2d.postDelayed({
            if (previewView === live2d) {
                live2d.publishDiagnostics()
                val d = CharacterDiagnostics.snapshot()
                Log.i(
                    TAG,
                    "preview-runtime id=${profile.id} lifecycle=${d.live2dLifecycleState} " +
                        "model=${d.live2dModelLoaded} textures=${d.live2dTexturesLoaded}/${d.live2dTextureCount} " +
                        "fps=${d.live2dRenderFps} error=${d.live2dLastError}"
                )
            }
        }, 1200L)
    }

    private fun logAllBuiltInAssetPreflight() {
        Live2DCharacterProfiles.all.forEach { profile ->
            Log.i(TAG, "asset-preflight id=${profile.id} ok=${verifyProfileAssets(profile)}")
        }
        val forward = buildList {
            var p = Live2DCharacterProfiles.Tororo
            repeat(8) {
                add(p.id)
                p = Live2DCharacterProfiles.next(p.id)
            }
        }
        val backward = buildList {
            var p = Live2DCharacterProfiles.Tororo
            repeat(8) {
                add(p.id)
                p = Live2DCharacterProfiles.previous(p.id)
            }
        }
        Log.i(TAG, "loop-forward=${forward.joinToString(" -> ")}")
        Log.i(TAG, "loop-backward=${backward.joinToString(" -> ")}")
    }

    private fun verifyProfileAssets(profile: Live2DCharacterProfile): Boolean {
        return runCatching {
            verifyApkProfile(profile)
        }.onFailure {
            Log.e(TAG, "asset-preflight-failure id=${profile.id}", it)
        }.getOrDefault(false)
    }

    private fun verifyApkProfile(profile: Live2DCharacterProfile): Boolean {
        val model3Path = "${profile.assetDir}/${profile.model3File}"
        val json = context.assets.open(model3Path).bufferedReader().use { it.readText() }
        val refs = JSONObject(json).getJSONObject("FileReferences")
        val required = mutableListOf(refs.getString("Moc"))
        val textures = refs.optJSONArray("Textures")
        if (textures != null) {
            for (i in 0 until textures.length()) required += textures.getString(i)
        }
        return required.isNotEmpty() && required.all { relative ->
            runCatching {
                context.assets.open("${profile.assetDir}/$relative").use { it.read(byteArrayOf(1)) }
            }.isSuccess
        }
    }


    companion object {
        private const val TAG = "BroadwayHomeQA"
        private const val DESIGN_WIDTH = 864f
        private const val DESIGN_HEIGHT = 1536f
        private const val MIN_PREVIEW_EDGE_PX = 32
    }
}
