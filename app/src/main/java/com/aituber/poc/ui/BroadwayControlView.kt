package com.aituber.poc.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Independent controls for the Broadway reference home.
 *
 * The reference artwork is not used as a flat screen bitmap. Every visible
 * control is a separate View with its own hit target, press state and callback.
 */
class BroadwayControlView(
    context: Context,
    private val kind: Kind,
    private val label: String? = null
) : View(context) {

    enum class Kind {
        TAB,
        PLUS,
        SETTINGS,
        ARROW_LEFT,
        ARROW_RIGHT,
        LAUNCH,
        COIN_METER,
        DIAMOND_METER,
        BACK,
        HOME
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        isClickable = kind !in setOf(Kind.COIN_METER, Kind.DIAMOND_METER)
        isFocusable = isClickable
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        when (kind) {
            Kind.TAB -> drawTab(canvas)
            Kind.PLUS -> drawPlus(canvas)
            Kind.SETTINGS -> drawSettings(canvas)
            Kind.ARROW_LEFT -> drawArrow(canvas, true)
            Kind.ARROW_RIGHT -> drawArrow(canvas, false)
            Kind.LAUNCH -> drawLaunch(canvas)
            Kind.COIN_METER -> drawMeter(canvas, diamond = false)
            Kind.DIAMOND_METER -> drawMeter(canvas, diamond = true)
            Kind.BACK -> drawBottomButton(canvas, home = false)
            Kind.HOME -> drawBottomButton(canvas, home = true)
        }
    }

    private fun drawTab(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bodyBottom = h * 0.78f
        val radius = h * 0.18f

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, bodyBottom,
            intArrayOf(
                if (isPressed) Color.rgb(48, 156, 226) else Color.rgb(91, 207, 255),
                if (isPressed) Color.rgb(35, 128, 211) else Color.rgb(56, 165, 242),
                if (isPressed) Color.rgb(23, 101, 190) else Color.rgb(42, 134, 229)
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(6f, h * 0.15f, w - 6f, bodyBottom), radius, radius, paint)
        paint.shader = null

        // Only the active Characters tab has the lower pointer in the reference.
        val cx = w * 0.5f
        if (label == "Characters") {
            path.reset()
            path.moveTo(cx - h * 0.10f, bodyBottom - 2f)
            path.lineTo(cx, h * 0.96f)
            path.lineTo(cx + h * 0.10f, bodyBottom - 2f)
            path.close()
            paint.color = if (isPressed) Color.rgb(35, 128, 211) else Color.rgb(59, 176, 244)
            canvas.drawPath(path, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(3f, h * 0.025f)
        paint.color = Color.rgb(211, 245, 255)
        canvas.drawRoundRect(RectF(8f, h * 0.16f, w - 8f, bodyBottom - 2f), radius, radius, paint)
        paint.style = Paint.Style.FILL

        // Small ornamental mark.
        paint.color = Color.argb(150, 220, 246, 255)
        val markY = h * 0.26f
        canvas.drawOval(RectF(cx - h * 0.10f, markY - h * 0.025f, cx + h * 0.10f, markY + h * 0.025f), paint)
        drawCenteredText(canvas, label.orEmpty(), h * 0.27f, Color.WHITE, bodyBottom * 0.63f, true)
    }

    private fun drawPlus(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w, h) * 0.18f
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                if (isPressed) Color.rgb(94, 211, 53) else Color.rgb(139, 244, 82),
                if (isPressed) Color.rgb(38, 157, 28) else Color.rgb(55, 190, 40),
                Color.rgb(25, 112, 23)
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(4f, 4f, w - 4f, h - 5f), r, r, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.055f
        paint.color = Color.rgb(34, 110, 28)
        canvas.drawRoundRect(RectF(5f, 5f, w - 5f, h - 6f), r, r, paint)

        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = h * 0.12f
        paint.color = Color.WHITE
        canvas.drawLine(w * 0.28f, h * 0.49f, w * 0.72f, h * 0.49f, paint)
        canvas.drawLine(w * 0.50f, h * 0.27f, w * 0.50f, h * 0.71f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawSettings(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = min(w, h) * 0.18f
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                if (isPressed) Color.rgb(62, 156, 226) else Color.rgb(104, 213, 255),
                if (isPressed) Color.rgb(34, 117, 203) else Color.rgb(54, 149, 235),
                Color.rgb(26, 91, 184)
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(7f, 7f, w - 7f, h - 8f), radius, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.035f
        paint.color = Color.rgb(210, 244, 255)
        canvas.drawRoundRect(RectF(9f, 9f, w - 9f, h - 10f), radius, radius, paint)

        val cx = w / 2f
        val cy = h / 2f
        val rr = min(w, h) * 0.23f
        paint.strokeWidth = min(w, h) * 0.075f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, rr * 0.64f, paint)
        repeat(8) { i ->
            val a = Math.toRadians(i * 45.0)
            val x1 = cx + cos(a).toFloat() * rr * 0.82f
            val y1 = cy + sin(a).toFloat() * rr * 0.82f
            val x2 = cx + cos(a).toFloat() * rr * 1.26f
            val y2 = cy + sin(a).toFloat() * rr * 1.26f
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawArrow(canvas: Canvas, left: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val rr = min(w, h) * 0.42f

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, rr,
            intArrayOf(
                if (isPressed) Color.rgb(52, 151, 226) else Color.rgb(105, 213, 255),
                if (isPressed) Color.rgb(29, 108, 199) else Color.rgb(48, 146, 232),
                Color.rgb(25, 82, 172)
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, rr, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = min(w, h) * 0.055f
        paint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.rgb(255, 242, 157), Color.rgb(225, 153, 42), Color.rgb(255, 224, 115)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, rr * 0.92f, paint)
        paint.shader = null

        path.reset()
        val d = if (left) -1f else 1f
        val tipX = cx + d * w * 0.20f
        val backX = cx - d * w * 0.13f
        path.moveTo(tipX, cy)
        path.lineTo(backX, cy - h * 0.20f)
        path.lineTo(backX, cy - h * 0.08f)
        path.lineTo(cx - d * w * 0.24f, cy - h * 0.08f)
        path.lineTo(cx - d * w * 0.24f, cy + h * 0.08f)
        path.lineTo(backX, cy + h * 0.08f)
        path.lineTo(backX, cy + h * 0.20f)
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.setShadowLayer(5f, 0f, 4f, Color.argb(130, 16, 62, 134))
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
    }

    private fun drawLaunch(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h * 0.25f

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                if (isPressed) Color.rgb(255, 216, 59) else Color.rgb(255, 247, 126),
                if (isPressed) Color.rgb(247, 174, 16) else Color.rgb(255, 202, 31),
                Color.rgb(225, 132, 10)
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(6f, 8f, w - 6f, h - 8f), radius, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.035f
        paint.color = Color.rgb(255, 235, 116)
        canvas.drawRoundRect(RectF(9f, 10f, w - 9f, h - 11f), radius * 0.92f, radius * 0.92f, paint)
        paint.style = Paint.Style.FILL
        drawCenteredText(canvas, label.orEmpty(), h * 0.39f, Color.rgb(154, 76, 10), h * 0.53f, true)
    }

    private fun drawMeter(canvas: Canvas, diamond: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()
        val iconSize = h * 0.92f
        val barLeft = iconSize * 0.72f

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(Color.rgb(28, 41, 85), Color.rgb(66, 55, 82), Color.rgb(31, 36, 70)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(barLeft, h * 0.09f, w - h * 0.08f, h * 0.91f), h * 0.28f, h * 0.28f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.025f
        paint.color = Color.argb(210, 235, 183, 72)
        canvas.drawRoundRect(RectF(barLeft + 1f, h * 0.10f, w - h * 0.09f, h * 0.90f), h * 0.27f, h * 0.27f, paint)
        paint.style = Paint.Style.FILL

        if (diamond) drawDiamond(canvas, iconSize * 0.48f, h * 0.50f, iconSize * 0.44f)
        else drawCoin(canvas, iconSize * 0.48f, h * 0.50f, iconSize * 0.40f)

        val x = barLeft + (w - barLeft) * 0.55f
        drawTextAt(canvas, label.orEmpty(), h * 0.45f, Color.WHITE, x, h * 0.64f, true)
    }

    private fun drawCoin(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - r * 0.25f, cy - r * 0.30f, r * 1.2f,
            intArrayOf(Color.rgb(255, 248, 126), Color.rgb(255, 193, 29), Color.rgb(214, 118, 8)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.11f
        paint.color = Color.rgb(255, 224, 78)
        canvas.drawCircle(cx, cy, r * 0.79f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 235, 100)
        path.reset()
        path.moveTo(cx, cy - r * 0.54f)
        path.lineTo(cx + r * 0.18f, cy - r * 0.10f)
        path.lineTo(cx + r * 0.04f, cy - r * 0.08f)
        path.lineTo(cx - r * 0.18f, cy + r * 0.52f)
        path.lineTo(cx - r * 0.05f, cy + r * 0.08f)
        path.lineTo(cx - r * 0.18f, cy + r * 0.10f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx, cy - r)
        path.lineTo(cx + r * 0.86f, cy - r * 0.33f)
        path.lineTo(cx + r * 0.60f, cy + r * 0.80f)
        path.lineTo(cx, cy + r)
        path.lineTo(cx - r * 0.60f, cy + r * 0.80f)
        path.lineTo(cx - r * 0.86f, cy - r * 0.33f)
        path.close()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            intArrayOf(Color.rgb(191, 249, 255), Color.rgb(43, 203, 255), Color.rgb(47, 84, 218)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        paint.color = Color.argb(190, 230, 253, 255)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawBottomButton(canvas: Canvas, home: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = min(w, h) * 0.18f
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                if (isPressed) Color.rgb(56, 155, 226) else Color.rgb(103, 213, 255),
                if (isPressed) Color.rgb(31, 110, 201) else Color.rgb(50, 147, 233),
                Color.rgb(25, 83, 172)
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(8f, 8f, w - 8f, h - 9f), radius, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.04f
        paint.color = Color.rgb(255, 221, 109)
        canvas.drawRoundRect(RectF(6f, 6f, w - 6f, h - 7f), radius, radius, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.setShadowLayer(5f, 0f, 4f, Color.argb(130, 16, 62, 134))
        if (home) drawHomeGlyph(canvas, w / 2f, h / 2f, min(w, h) * 0.38f)
        else drawBackGlyph(canvas, w / 2f, h / 2f, min(w, h) * 0.40f)
        paint.clearShadowLayer()
    }

    private fun drawBackGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx - r * 0.75f, cy)
        path.lineTo(cx - r * 0.05f, cy - r * 0.62f)
        path.lineTo(cx - r * 0.05f, cy - r * 0.25f)
        path.lineTo(cx + r * 0.72f, cy - r * 0.25f)
        path.lineTo(cx + r * 0.72f, cy + r * 0.25f)
        path.lineTo(cx - r * 0.05f, cy + r * 0.25f)
        path.lineTo(cx - r * 0.05f, cy + r * 0.62f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawHomeGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx - r * 0.78f, cy - r * 0.05f)
        path.lineTo(cx, cy - r * 0.74f)
        path.lineTo(cx + r * 0.78f, cy - r * 0.05f)
        path.lineTo(cx + r * 0.56f, cy - r * 0.05f)
        path.lineTo(cx + r * 0.56f, cy + r * 0.65f)
        path.lineTo(cx + r * 0.12f, cy + r * 0.65f)
        path.lineTo(cx + r * 0.12f, cy + r * 0.18f)
        path.lineTo(cx - r * 0.12f, cy + r * 0.18f)
        path.lineTo(cx - r * 0.12f, cy + r * 0.65f)
        path.lineTo(cx - r * 0.56f, cy + r * 0.65f)
        path.lineTo(cx - r * 0.56f, cy - r * 0.05f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, size: Float, color: Int, centerY: Float, shadow: Boolean) {
        if (text.isEmpty()) return
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        paint.color = color
        if (shadow) paint.setShadowLayer(size * 0.09f, 0f, size * 0.06f, Color.argb(175, 20, 74, 142))
        val baseline = centerY - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, width / 2f, baseline, paint)
        paint.clearShadowLayer()
    }

    private fun drawTextAt(canvas: Canvas, text: String, size: Float, color: Int, x: Float, baselineY: Float, shadow: Boolean) {
        if (text.isEmpty()) return
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        paint.color = color
        if (shadow) paint.setShadowLayer(size * 0.08f, 0f, size * 0.05f, Color.argb(190, 0, 0, 0))
        canvas.drawText(text, x, baselineY, paint)
        paint.clearShadowLayer()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isClickable || !isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                animate().cancel()
                animate()
                    .scaleX(PRESSED_SCALE)
                    .scaleY(PRESSED_SCALE)
                    .translationY(dp(PRESSED_OFFSET_DP))
                    .setDuration(PRESS_DOWN_MS)
                    .start()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val inside = event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                if (isPressed != inside) {
                    isPressed = inside
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val inside = event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                isPressed = false
                animate().cancel()
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(PRESS_UP_MS)
                    .setInterpolator(OvershootInterpolator(1.25f))
                    .start()
                invalidate()
                if (inside) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                animate().cancel()
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(PRESS_UP_MS)
                    .setInterpolator(OvershootInterpolator(1.25f))
                    .start()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val PRESSED_SCALE = 0.965f
        private const val PRESSED_OFFSET_DP = 4f
        private const val PRESS_DOWN_MS = 55L
        private const val PRESS_UP_MS = 120L
    }
}
