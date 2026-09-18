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
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Resolution-independent Broadway scenery matching the supplied 864x1536
 * reference composition. Each scenery plane is independent from controls/avatar.
 */
class BroadwaySceneryLayerView(
    context: Context,
    private val layer: Layer
) : View(context) {

    enum class Layer { BACKDROP, LIGHTS, FLOOR, CURTAINS, FOREGROUND }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        canvas.save()
        canvas.scale(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        when (layer) {
            Layer.BACKDROP -> drawBackdrop(canvas)
            Layer.LIGHTS -> drawLights(canvas)
            Layer.FLOOR -> drawFloor(canvas)
            Layer.CURTAINS -> drawCurtains(canvas)
            Layer.FOREGROUND -> drawForeground(canvas)
        }
        canvas.restore()
    }

    private fun drawBackdrop(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, DESIGN_HEIGHT,
            intArrayOf(
                Color.rgb(74, 5, 15),
                Color.rgb(38, 5, 11),
                Color.rgb(94, 28, 16),
                Color.rgb(20, 4, 9)
            ),
            floatArrayOf(0f, 0.22f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, paint)
        paint.shader = null

        // Warm blurred theatre auditorium behind the avatar.
        paint.color = Color.rgb(67, 30, 18)
        canvas.drawRoundRect(RectF(88f, 236f, 776f, 1085f), 52f, 52f, paint)
        repeat(4) { row ->
            val y = 430f + row * 125f
            paint.color = Color.argb(100, 238, 139, 55)
            canvas.drawRoundRect(RectF(160f, y, 704f, y + 56f), 28f, 28f, paint)
            paint.color = Color.argb(80, 91, 28, 16)
            canvas.drawRoundRect(RectF(180f, y + 20f, 684f, y + 74f), 24f, 24f, paint)
        }

        // Golden proscenium frame.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 38f
        paint.shader = LinearGradient(
            50f, 0f, 814f, 0f,
            intArrayOf(
                Color.rgb(139, 72, 8),
                Color.rgb(255, 205, 89),
                Color.rgb(255, 237, 144),
                Color.rgb(171, 90, 10)
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(28f, 106f, 836f, 1120f), 56f, 56f, paint)
        paint.shader = null
        paint.strokeWidth = 7f
        paint.color = Color.rgb(255, 230, 133)
        canvas.drawRoundRect(RectF(45f, 122f, 819f, 1104f), 48f, 48f, paint)
        paint.style = Paint.Style.FILL

        // Side gold rails.
        paint.shader = LinearGradient(
            0f, 0f, 72f, 0f,
            intArrayOf(Color.rgb(120, 60, 7), Color.rgb(251, 184, 59), Color.rgb(103, 47, 5)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(20f, 210f, 72f, 1116f), 24f, 24f, paint)
        canvas.drawRoundRect(RectF(792f, 210f, 844f, 1116f), 24f, 24f, paint)
        paint.shader = null

        // Reference-like marquee bulbs.
        drawBulbRow(canvas, 54f, 810f, 188f, 16)
        drawBulbColumn(canvas, 49f, 254f, 1040f, 13)
        drawBulbColumn(canvas, 815f, 254f, 1040f, 13)

        // Decorative gold crest below the tabs.
        paint.color = Color.rgb(235, 167, 53)
        path.reset()
        path.moveTo(414f, 250f)
        path.lineTo(432f, 211f)
        path.lineTo(450f, 250f)
        path.lineTo(469f, 274f)
        path.lineTo(432f, 261f)
        path.lineTo(395f, 274f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawLights(canvas: Canvas) {
        drawSpot(canvas, 388f, 288f, 330f, 880f)
        drawSpot(canvas, 432f, 280f, 432f, 940f)
        drawSpot(canvas, 476f, 288f, 534f, 880f)
        listOf(388f, 432f, 476f).forEach { x ->
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(83, 54, 21)
            canvas.drawCircle(x, 280f, 15f, paint)
            paint.color = Color.rgb(255, 227, 157)
            canvas.drawCircle(x, 285f, 7f, paint)
        }
    }

    private fun drawSpot(canvas: Canvas, sx: Float, sy: Float, tx: Float, ty: Float) {
        path.reset()
        path.moveTo(sx - 10f, sy + 4f)
        path.lineTo(tx - 105f, ty)
        path.lineTo(tx + 105f, ty)
        path.lineTo(sx + 10f, sy + 4f)
        path.close()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            sx, sy, tx, ty,
            intArrayOf(Color.argb(82, 255, 235, 185), Color.argb(0, 255, 218, 132)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun drawFloor(canvas: Canvas) {
        // Red-carpet floor behind the podium.
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 1000f, 0f, 1536f,
            intArrayOf(Color.rgb(109, 27, 19), Color.rgb(74, 11, 17), Color.rgb(37, 4, 10)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 995f, 864f, 1536f, paint)
        paint.shader = null

        // Carpet perspective.
        path.reset()
        path.moveTo(330f, 1080f)
        path.lineTo(534f, 1080f)
        path.lineTo(665f, 1536f)
        path.lineTo(199f, 1536f)
        path.close()
        paint.shader = LinearGradient(
            0f, 1080f, 0f, 1536f,
            intArrayOf(Color.rgb(183, 29, 35), Color.rgb(122, 12, 24), Color.rgb(80, 5, 16)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.color = Color.rgb(222, 167, 65)
        canvas.drawLine(330f, 1080f, 199f, 1536f, paint)
        canvas.drawLine(534f, 1080f, 665f, 1536f, paint)
        paint.style = Paint.Style.FILL

        // Main circular podium.
        paint.shader = LinearGradient(
            0f, 1000f, 0f, 1180f,
            intArrayOf(Color.rgb(255, 208, 98), Color.rgb(167, 83, 13), Color.rgb(86, 35, 5)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(116f, 1000f, 748f, 1156f), paint)
        paint.shader = null
        paint.color = Color.rgb(139, 45, 25)
        canvas.drawOval(RectF(149f, 1018f, 715f, 1115f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.rgb(255, 203, 89)
        canvas.drawOval(RectF(149f, 1018f, 715f, 1115f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawCurtains(canvas: Canvas) {
        drawTopCurtain(canvas)
        drawSideCurtain(canvas, true)
        drawSideCurtain(canvas, false)
    }

    private fun drawTopCurtain(canvas: Canvas) {
        path.reset()
        path.moveTo(-20f, -12f)
        path.lineTo(884f, -12f)
        path.lineTo(884f, 212f)
        path.cubicTo(735f, 245f, 614f, 223f, 531f, 180f)
        path.cubicTo(478f, 150f, 456f, 129f, 432f, 92f)
        path.cubicTo(408f, 129f, 386f, 150f, 333f, 180f)
        path.cubicTo(250f, 223f, 129f, 245f, -20f, 212f)
        path.close()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, 260f,
            intArrayOf(Color.rgb(90, 2, 12), Color.rgb(207, 20, 40), Color.rgb(83, 3, 17)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        paint.color = Color.rgb(234, 171, 53)
        canvas.drawLine(0f, 204f, 864f, 204f, paint)
        paint.style = Paint.Style.FILL

        // Gold fringe.
        paint.color = Color.rgb(244, 191, 72)
        var x = 0f
        while (x < 864f) {
            path.reset()
            path.moveTo(x, 205f)
            path.lineTo(x + 13f, 225f)
            path.lineTo(x + 26f, 205f)
            path.close()
            canvas.drawPath(path, paint)
            x += 26f
        }
    }

    private fun drawSideCurtain(canvas: Canvas, left: Boolean) {
        path.reset()
        if (left) {
            path.moveTo(-22f, 150f)
            path.lineTo(205f, 225f)
            path.cubicTo(194f, 380f, 184f, 560f, 160f, 720f)
            path.cubicTo(142f, 870f, 118f, 995f, 75f, 1130f)
            path.lineTo(-22f, 1150f)
        } else {
            path.moveTo(886f, 150f)
            path.lineTo(659f, 225f)
            path.cubicTo(670f, 380f, 680f, 560f, 704f, 720f)
            path.cubicTo(722f, 870f, 746f, 995f, 789f, 1130f)
            path.lineTo(886f, 1150f)
        }
        path.close()

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            if (left) 0f else 650f, 0f, if (left) 214f else 864f, 0f,
            intArrayOf(
                Color.rgb(68, 1, 11),
                Color.rgb(174, 13, 35),
                Color.rgb(110, 4, 23),
                Color.rgb(210, 24, 47),
                Color.rgb(65, 1, 13)
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        // Fold highlights.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.argb(95, 255, 125, 135)
        val xs = if (left) listOf(35f, 78f, 121f, 165f) else listOf(829f, 786f, 743f, 699f)
        xs.forEach { fx -> canvas.drawLine(fx, 215f, if (left) fx - 30f else fx + 30f, 1070f, paint) }
        paint.style = Paint.Style.FILL

        // Tiebacks and tassels.
        val tx = if (left) 142f else 722f
        paint.color = Color.rgb(238, 180, 57)
        canvas.drawOval(RectF(tx - 30f, 505f, tx + 30f, 536f), paint)
        paint.color = Color.rgb(252, 206, 86)
        canvas.drawRect(tx - 6f, 530f, tx + 6f, 580f, paint)
        canvas.drawCircle(tx, 585f, 13f, paint)
    }

    private fun drawForeground(canvas: Canvas) {
        // Podium front lip and footlights.
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 1088f, 0f, 1195f,
            intArrayOf(Color.rgb(250, 188, 62), Color.rgb(159, 75, 11), Color.rgb(77, 28, 4)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(102f, 1088f, 762f, 1178f), 28f, 28f, paint)
        paint.shader = null
        repeat(13) { i -> drawBulb(canvas, 132f + i * 50f, 1132f, 11f) }

        // Red central step.
        paint.color = Color.rgb(133, 19, 27)
        canvas.drawRoundRect(RectF(244f, 1162f, 620f, 1255f), 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.rgb(220, 151, 45)
        canvas.drawRoundRect(RectF(244f, 1162f, 620f, 1255f), 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        // Audience silhouettes at the bottom, intentionally behind bottom buttons.
        val heads = listOf(
            22f to 1468f, 78f to 1452f, 142f to 1468f, 205f to 1448f,
            656f to 1448f, 720f to 1468f, 786f to 1452f, 842f to 1468f
        )
        heads.forEachIndexed { i, (cx, cy) ->
            paint.color = if (i % 2 == 0) Color.rgb(47, 18, 13) else Color.rgb(30, 13, 12)
            canvas.drawCircle(cx, cy, 38f, paint)
            canvas.drawOval(RectF(cx - 57f, cy + 18f, cx + 57f, 1548f), paint)
        }
    }

    private fun drawBulbRow(canvas: Canvas, startX: Float, endX: Float, y: Float, count: Int) {
        val step = (endX - startX) / (count - 1)
        repeat(count) { drawBulb(canvas, startX + step * it, y, 13f) }
    }

    private fun drawBulbColumn(canvas: Canvas, x: Float, startY: Float, endY: Float, count: Int) {
        val step = (endY - startY) / (count - 1)
        repeat(count) { drawBulb(canvas, x, startY + step * it, 13f) }
    }

    private fun drawBulb(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(
                Color.argb(230, 255, 250, 204),
                Color.argb(145, 255, 183, 58),
                Color.argb(0, 255, 139, 0)
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
        paint.color = Color.rgb(255, 244, 197)
        canvas.drawCircle(cx, cy, r * 0.38f, paint)
    }

    companion object {
        private const val DESIGN_WIDTH = 864f
        private const val DESIGN_HEIGHT = 1536f
    }
}
