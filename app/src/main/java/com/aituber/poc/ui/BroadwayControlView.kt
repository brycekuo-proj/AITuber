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

/**
 * Independent Broadway control.
 *
 * Every interactive control owns its own visual chrome, hit area, pressed state
 * and click callback. No control is baked into the scenery bitmap.
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
        DIAMOND_METER
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        isClickable = kind != Kind.DIAMOND_METER
        isFocusable = isClickable
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        when (kind) {
            Kind.TAB -> drawTab(canvas)
            Kind.PLUS -> drawPlus(canvas)
            Kind.SETTINGS -> drawSettings(canvas)
            Kind.ARROW_LEFT -> drawArrow(canvas, left = true)
            Kind.ARROW_RIGHT -> drawArrow(canvas, left = false)
            Kind.LAUNCH -> drawLaunch(canvas)
            Kind.DIAMOND_METER -> drawDiamondMeter(canvas)
        }
    }

    private fun drawTab(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.style = Paint.Style.FILL
        paint.color = if (isPressed) Color.rgb(63, 9, 17) else Color.rgb(99, 11, 26)
        canvas.drawRoundRect(RectF(4f, 7f, w - 4f, h - 8f), h * 0.28f, h * 0.28f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.055f
        paint.shader = LinearGradient(
            0f,
            0f,
            w,
            0f,
            intArrayOf(
                Color.rgb(129, 70, 14),
                Color.rgb(255, 219, 113),
                Color.rgb(144, 78, 14)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(h * 0.08f, h * 0.10f, w - h * 0.08f, h - h * 0.13f),
            h * 0.24f,
            h * 0.24f,
            paint
        )
        paint.shader = null
        paint.style = Paint.Style.FILL

        drawMiniBulbs(canvas, horizontal = true)
        drawCenteredText(
            canvas,
            label.orEmpty(),
            size = h * 0.28f,
            color = Color.WHITE,
            shadow = true
        )
    }

    private fun drawPlus(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = minOf(w, h) * 0.42f

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            w / 2f,
            h / 2f,
            r,
            intArrayOf(
                Color.rgb(255, 224, 124),
                Color.rgb(188, 111, 17),
                Color.rgb(89, 39, 4)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w / 2f, h / 2f, r, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = minOf(w, h) * 0.12f
        paint.color = if (isPressed) Color.rgb(98, 43, 5) else Color.rgb(81, 29, 1)
        canvas.drawLine(w * 0.28f, h * 0.5f, w * 0.72f, h * 0.5f, paint)
        canvas.drawLine(w * 0.5f, h * 0.28f, w * 0.5f, h * 0.72f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawSettings(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.29f

        paint.style = Paint.Style.FILL
        paint.color = if (isPressed) Color.rgb(71, 8, 17) else Color.rgb(105, 12, 27)
        canvas.drawCircle(cx, cy, minOf(w, h) * 0.43f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = minOf(w, h) * 0.065f
        paint.color = Color.rgb(244, 194, 79)
        canvas.drawCircle(cx, cy, radius, paint)
        repeat(8) { index ->
            val angle = Math.toRadians((index * 45.0))
            val x1 = cx + (radius * 0.78f * kotlin.math.cos(angle).toFloat())
            val y1 = cy + (radius * 0.78f * kotlin.math.sin(angle).toFloat())
            val x2 = cx + (radius * 1.22f * kotlin.math.cos(angle).toFloat())
            val y2 = cy + (radius * 1.22f * kotlin.math.sin(angle).toFloat())
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
        canvas.drawCircle(cx, cy, radius * 0.35f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawArrow(canvas: Canvas, left: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            w / 2f,
            h / 2f,
            minOf(w, h) * 0.48f,
            intArrayOf(
                Color.rgb(151, 21, 40),
                Color.rgb(78, 5, 17),
                Color.rgb(24, 2, 7)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w / 2f, h / 2f, minOf(w, h) * 0.43f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = minOf(w, h) * 0.055f
        paint.color = Color.rgb(241, 190, 71)
        canvas.drawCircle(w / 2f, h / 2f, minOf(w, h) * 0.39f, paint)

        path.reset()
        val direction = if (left) -1f else 1f
        val centerX = w / 2f
        val tipX = centerX + direction * w * 0.20f
        val tailX = centerX - direction * w * 0.16f
        path.moveTo(tipX, h * 0.50f)
        path.lineTo(tailX, h * 0.27f)
        path.lineTo(tailX, h * 0.41f)
        path.lineTo(centerX - direction * w * 0.23f, h * 0.41f)
        path.lineTo(centerX - direction * w * 0.23f, h * 0.59f)
        path.lineTo(tailX, h * 0.59f)
        path.lineTo(tailX, h * 0.73f)
        path.close()

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            h * 0.25f,
            0f,
            h * 0.75f,
            intArrayOf(
                Color.rgb(255, 242, 165),
                Color.rgb(232, 163, 47),
                Color.rgb(116, 58, 7)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun drawLaunch(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h,
            intArrayOf(
                Color.rgb(255, 229, 137),
                Color.rgb(219, 151, 43),
                Color.rgb(124, 66, 10)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(4f, 6f, w - 4f, h - 9f), h * 0.23f, h * 0.23f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.055f
        paint.color = Color.rgb(89, 36, 4)
        canvas.drawRoundRect(
            RectF(h * 0.11f, h * 0.12f, w - h * 0.11f, h - h * 0.15f),
            h * 0.18f,
            h * 0.18f,
            paint
        )
        paint.style = Paint.Style.FILL

        drawMiniBulbs(canvas, horizontal = true)
        drawCenteredText(
            canvas,
            label.orEmpty(),
            size = h * 0.20f,
            color = if (isPressed) Color.rgb(93, 41, 4) else Color.rgb(104, 46, 5),
            shadow = false
        )
    }

    private fun drawDiamondMeter(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Meter body.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(225, 32, 7, 16)
        canvas.drawRoundRect(RectF(h * 0.58f, h * 0.10f, w, h * 0.90f), h * 0.33f, h * 0.33f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.055f
        paint.color = Color.rgb(226, 170, 54)
        canvas.drawRoundRect(
            RectF(h * 0.60f, h * 0.12f, w - 2f, h * 0.88f),
            h * 0.30f,
            h * 0.30f,
            paint
        )

        // Diamond icon.
        path.reset()
        path.moveTo(h * 0.43f, h * 0.08f)
        path.lineTo(h * 0.76f, h * 0.32f)
        path.lineTo(h * 0.52f, h * 0.82f)
        path.lineTo(h * 0.17f, h * 0.32f)
        path.close()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            0f,
            h * 0.8f,
            h,
            intArrayOf(
                Color.rgb(201, 252, 255),
                Color.rgb(69, 198, 242),
                Color.rgb(22, 90, 168)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        drawCenteredText(
            canvas,
            label.orEmpty(),
            size = h * 0.34f,
            color = Color.WHITE,
            shadow = true,
            xOffset = h * 0.34f
        )
    }

    private fun drawMiniBulbs(canvas: Canvas, horizontal: Boolean) {
        if (!horizontal) return
        val w = width.toFloat()
        val h = height.toFloat()
        val count = if (kind == Kind.LAUNCH) 9 else 7
        val start = h * 0.18f
        val end = w - h * 0.18f
        val step = (end - start) / (count - 1)
        repeat(count) { index ->
            val cx = start + index * step
            val cy = h * 0.15f
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(255, 239, 157)
            canvas.drawCircle(cx, cy, h * 0.026f, paint)
        }
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        size: Float,
        color: Int,
        shadow: Boolean,
        xOffset: Float = 0f
    ) {
        if (text.isEmpty()) return

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        paint.color = color
        if (shadow) {
            paint.setShadowLayer(size * 0.10f, 0f, size * 0.06f, Color.argb(190, 0, 0, 0))
            setLayerType(LAYER_TYPE_SOFTWARE, paint)
        } else {
            paint.clearShadowLayer()
        }
        val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, width / 2f + xOffset, baseline, paint)
        paint.clearShadowLayer()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isClickable || !isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                animate()
                    .scaleX(PRESSED_SCALE)
                    .scaleY(PRESSED_SCALE)
                    .translationY(dp(3).toFloat())
                    .setDuration(PRESS_DOWN_MS)
                    .start()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val inside = event.x >= 0f && event.x <= width && event.y >= 0f && event.y <= height
                if (isPressed != inside) {
                    isPressed = inside
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val inside = event.x >= 0f && event.x <= width && event.y >= 0f && event.y <= height
                isPressed = false
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(PRESS_UP_MS)
                    .start()
                invalidate()
                if (inside) performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(PRESS_UP_MS)
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val PRESSED_SCALE = 0.965f
        private const val PRESS_DOWN_MS = 60L
        private const val PRESS_UP_MS = 120L
    }
}
