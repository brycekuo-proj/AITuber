package com.aituber.poc.character.staticpng

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.aituber.poc.character.CharacterDiagnostics
import kotlin.math.roundToInt

class StaticPngOverlayView(
    context: Context,
    private val characterPackage: StaticPngCharacterPackage = StaticPngCharacterPackage.XianxiaFemale
) : FrameLayout(context) {
    private val imageView = ImageView(context)
    private val mouthLayerView = ImageView(context)
    private val mouthBitmaps: Map<StaticPngMouthShape, Bitmap>
    private var activeShape = StaticPngMouthShape.CLOSED
    private var activeRatio = 0f

    init {
        visibility = View.VISIBLE
        setBackgroundColor(Color.TRANSPARENT)
        imageView.visibility = View.VISIBLE
        imageView.setBackgroundColor(Color.TRANSPARENT)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.adjustViewBounds = false
        addView(
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
        mouthLayerView.scaleType = ImageView.ScaleType.FIT_XY
        addView(mouthLayerView, LayoutParams(1, 1))
        recordMouthShape()
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

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateMouthLayer()
    }

    private fun updateMouthLayer() {
        val layer = characterPackage.mouthLayers[activeShape]
        val bitmap = mouthBitmaps[activeShape]
        if (layer == null || bitmap == null || width <= 0 || height <= 0) {
            mouthLayerView.visibility = View.GONE
            mouthLayerView.setImageDrawable(null)
            return
        }
        val scale = minOf(
            width.toFloat() / characterPackage.sourceWidthPx.toFloat(),
            height.toFloat() / characterPackage.sourceHeightPx.toFloat()
        )
        val displayedWidth = characterPackage.sourceWidthPx * scale
        val displayedHeight = characterPackage.sourceHeightPx * scale
        val displayedLeft = (width - displayedWidth) / 2f
        val displayedTop = (height - displayedHeight) / 2f
        val params = mouthLayerView.layoutParams as LayoutParams
        params.width = (layer.width * scale).roundToInt().coerceAtLeast(1)
        params.height = (layer.height * scale).roundToInt().coerceAtLeast(1)
        params.leftMargin = (displayedLeft + layer.x * scale).roundToInt()
        params.topMargin = (displayedTop + layer.y * scale).roundToInt()
        mouthLayerView.layoutParams = params
        mouthLayerView.setImageBitmap(bitmap)
        mouthLayerView.visibility = View.VISIBLE
    }

    private fun recordMouthShape() {
        CharacterDiagnostics.recordStaticPngMouth(
            shape = activeShape.name,
            ratio = activeRatio
        )
    }
}
