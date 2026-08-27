package com.aituber.poc.character.staticpng

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.aituber.poc.character.CharacterDiagnostics

class StaticPngOverlayView(
    context: Context,
    private val characterPackage: StaticPngCharacterPackage = StaticPngCharacterPackage.XianxiaFemale
) : FrameLayout(context) {
    private val contentView = FrameLayout(context)
    private val imageView = ImageView(context)
    private val mouthLayerView = ImageView(context)
    private val mouthBitmaps: Map<StaticPngMouthShape, Bitmap>
    private var activeShape = StaticPngMouthShape.CLOSED
    private var activeRatio = 0f
    private var idleMotionEnabled = true
    private var idleMotionRunning = false
    private var idleMotionStartMs = 0L
    private var idleMotionFrame = StaticPngIdleMotion.frame(0L)
    private val idleMotionRunnable = object : Runnable {
        override fun run() {
            if (!idleMotionRunning) return
            applyIdleMotionFrame(SystemClock.uptimeMillis())
            postOnAnimation(this)
        }
    }

    init {
        visibility = View.VISIBLE
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        contentView.visibility = View.VISIBLE
        contentView.setBackgroundColor(Color.TRANSPARENT)
        contentView.clipChildren = false
        contentView.clipToPadding = false
        addView(
            contentView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        imageView.visibility = View.VISIBLE
        imageView.setBackgroundColor(Color.TRANSPARENT)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.adjustViewBounds = false
        contentView.addView(
            imageView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        context.assets.open(characterPackage.assetPath).use { input ->
            imageView.setImageBitmap(BitmapFactory.decodeStream(input))
        }
        mouthBitmaps = characterPackage.mouthLayers.mapValues { (_, layer) ->
            context.assets.open(layer.assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
        mouthLayerView.visibility = View.GONE
        mouthLayerView.setBackgroundColor(Color.TRANSPARENT)
        mouthLayerView.scaleType = ImageView.ScaleType.FIT_CENTER
        mouthLayerView.adjustViewBounds = false
        mouthLayerView.clipToOutline = false
        contentView.addView(
            mouthLayerView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        recordMouthShape()
        recordIdleMotion()
    }

    fun show() {
        visibility = View.VISIBLE
        imageView.visibility = View.VISIBLE
    }

    fun setMouthOpenRatio(ratio: Float) {
        activeRatio = ratio.coerceIn(0f, 1f)
        val nextShape = StaticPngMouthShape.fromRatio(activeRatio)
        if (nextShape != activeShape) {
            activeShape = nextShape
            updateMouthLayer()
        }
        recordMouthShape()
    }

    fun setMouthShapeForDebug(shape: StaticPngMouthShape) {
        activeShape = shape
        activeRatio = StaticPngMouthShape.debugRatio(shape)
        updateMouthLayer()
        recordMouthShape()
    }

    fun setIdleMotionEnabled(enabled: Boolean) {
        idleMotionEnabled = enabled
        if (enabled) {
            startIdleMotion()
        } else {
            stopIdleMotion(resetTransform = true)
        }
    }

    fun release() {
        stopIdleMotion(resetTransform = true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (idleMotionEnabled) {
            startIdleMotion()
        }
    }

    override fun onDetachedFromWindow() {
        stopIdleMotion(resetTransform = true)
        super.onDetachedFromWindow()
    }

    private fun updateMouthLayer() {
        val layer = characterPackage.mouthLayers[activeShape]
        val bitmap = mouthBitmaps[activeShape]
        if (layer == null || bitmap == null) {
            mouthLayerView.visibility = View.GONE
            mouthLayerView.setImageDrawable(null)
            return
        }
        mouthLayerView.setImageBitmap(bitmap)
        mouthLayerView.visibility = View.VISIBLE
    }

    private fun recordMouthShape() {
        val patch = characterPackage.mouthReplacementPatch
        CharacterDiagnostics.recordStaticPngMouth(
            shape = activeShape.name,
            ratio = activeRatio,
            patchX = patch.x,
            patchY = patch.y,
            patchWidth = patch.width,
            patchHeight = patch.height
        )
    }

    private fun startIdleMotion() {
        if (idleMotionRunning || !isAttachedToWindow) {
            recordIdleMotion()
            return
        }
        idleMotionRunning = true
        idleMotionStartMs = SystemClock.uptimeMillis()
        applyIdleMotionFrame(idleMotionStartMs)
        postOnAnimation(idleMotionRunnable)
    }

    private fun stopIdleMotion(resetTransform: Boolean) {
        idleMotionRunning = false
        removeCallbacks(idleMotionRunnable)
        if (resetTransform) {
            idleMotionFrame = StaticPngIdleMotion.frame(0L)
            contentView.translationY = 0f
            contentView.scaleX = 1f
            contentView.scaleY = 1f
        }
        recordIdleMotion()
    }

    private fun applyIdleMotionFrame(nowMs: Long) {
        idleMotionFrame = StaticPngIdleMotion.frame(nowMs - idleMotionStartMs)
        contentView.translationY = idleMotionFrame.offsetYDp * resources.displayMetrics.density
        contentView.scaleX = idleMotionFrame.scale
        contentView.scaleY = idleMotionFrame.scale
        recordIdleMotion()
    }

    private fun recordIdleMotion() {
        CharacterDiagnostics.recordStaticPngIdleMotion(
            active = idleMotionRunning,
            phase = idleMotionFrame.phase,
            offsetYDp = idleMotionFrame.offsetYDp,
            scale = idleMotionFrame.scale
        )
    }
}
