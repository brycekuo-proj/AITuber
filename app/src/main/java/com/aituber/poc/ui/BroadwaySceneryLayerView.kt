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

/**
 * Resolution-independent Broadway scenery.
 *
 * The approved Canva screen is reference-only. Nothing here draws the flat
 * reference bitmap. Each scenery layer is rendered independently at runtime so
 * curtains, lights, floor and proscenium stay sharp on any device density.
 */
class BroadwaySceneryLayerView(
    context: Context,
    private val layer: Layer
) : View(context) {

    enum class Layer {
        BACKDROP,
        LIGHTS,
        FLOOR,
        CURTAINS,
        FOREGROUND
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
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
            0f,
            0f,
            0f,
            DESIGN_HEIGHT,
            intArrayOf(
                Color.rgb(20, 1, 7),
                Color.rgb(49, 4, 15),
                Color.rgb(12, 3, 9)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, paint)
        paint.shader = null

        // Theatre opening.
        paint.color = Color.rgb(6, 4, 10)
        canvas.drawRoundRect(RectF(119f, 236f, 745f, 1135f), 42f, 42f, paint)

        // Outer proscenium shadow.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 42f
        paint.color = Color.rgb(57, 27, 6)
        canvas.drawRoundRect(RectF(117f, 226f, 747f, 1144f), 54f, 54f, paint)

        // Gold proscenium.
        paint.strokeWidth = 24f
        paint.shader = LinearGradient(
            90f,
            0f,
            774f,
            0f,
            intArrayOf(
                Color.rgb(112, 57, 7),
                Color.rgb(255, 214, 105),
                Color.rgb(143, 76, 10)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(118f, 227f, 746f, 1142f), 52f, 52f, paint)
        paint.shader = null

        // Inner gold bevel.
        paint.strokeWidth = 7f
        paint.color = Color.rgb(255, 235, 167)
        canvas.drawRoundRect(RectF(138f, 247f, 726f, 1122f), 38f, 38f, paint)
        paint.style = Paint.Style.FILL

        // Side ornamental panels.
        drawColumn(canvas, 70f, 272f, 112f, 1115f)
        drawColumn(canvas, 752f, 272f, 794f, 1115f)

        // Marquee bulbs around the arch.
        drawBulbRow(canvas, 151f, 713f, 252f, 13)
        drawBulbColumn(canvas, 124f, 306f, 1022f, 12)
        drawBulbColumn(canvas, 740f, 306f, 1022f, 12)
    }

    private fun drawColumn(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            left,
            0f,
            right,
            0f,
            intArrayOf(
                Color.rgb(68, 31, 5),
                Color.rgb(222, 165, 55),
                Color.rgb(91, 42, 6)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, paint)
        paint.shader = null
    }

    private fun drawBulbRow(canvas: Canvas, startX: Float, endX: Float, y: Float, count: Int) {
        val step = (endX - startX) / (count - 1)
        repeat(count) { index ->
            drawBulb(canvas, startX + step * index, y)
        }
    }

    private fun drawBulbColumn(canvas: Canvas, x: Float, startY: Float, endY: Float, count: Int) {
        val step = (endY - startY) / (count - 1)
        repeat(count) { index ->
            drawBulb(canvas, x, startY + step * index)
        }
    }

    private fun drawBulb(canvas: Canvas, cx: Float, cy: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx,
            cy,
            13f,
            intArrayOf(
                Color.argb(220, 255, 248, 194),
                Color.argb(110, 255, 183, 57),
                Color.argb(0, 255, 141, 0)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, 13f, paint)
        paint.shader = null
        paint.color = Color.rgb(255, 241, 179)
        canvas.drawCircle(cx, cy, 4.5f, paint)
    }

    private fun drawLights(canvas: Canvas) {
        drawSpotCone(canvas, 255f, 272f, 338f, 905f, Color.argb(68, 255, 226, 166))
        drawSpotCone(canvas, 432f, 252f, 432f, 980f, Color.argb(76, 255, 239, 188))
        drawSpotCone(canvas, 609f, 272f, 526f, 905f, Color.argb(68, 255, 226, 166))

        listOf(255f, 432f, 609f).forEach { x ->
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(78, 50, 18)
            canvas.drawCircle(x, 252f, 24f, paint)
            paint.color = Color.rgb(255, 216, 113)
            canvas.drawCircle(x, 258f, 13f, paint)
        }
    }

    private fun drawSpotCone(
        canvas: Canvas,
        sourceX: Float,
        sourceY: Float,
        targetX: Float,
        targetY: Float,
        color: Int
    ) {
        path.reset()
        path.moveTo(sourceX - 13f, sourceY + 10f)
        path.lineTo(targetX - 116f, targetY)
        path.lineTo(targetX + 116f, targetY)
        path.lineTo(sourceX + 13f, sourceY + 10f)
        path.close()

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            sourceX,
            sourceY,
            targetX,
            targetY,
            intArrayOf(color, Color.argb(3, 255, 240, 196)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun drawFloor(canvas: Canvas) {
        path.reset()
        path.moveTo(121f, 1008f)
        path.lineTo(743f, 1008f)
        path.lineTo(864f, 1342f)
        path.lineTo(0f, 1342f)
        path.close()

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            1008f,
            0f,
            1342f,
            intArrayOf(
                Color.rgb(117, 43, 21),
                Color.rgb(75, 20, 18),
                Color.rgb(31, 8, 12)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        // Floor boards.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(110, 247, 158, 77)
        for (x in -40..900 step 70) {
            canvas.drawLine(432f, 1015f, x.toFloat(), 1340f, paint)
        }
        for (y in 1060..1320 step 55) {
            canvas.drawLine(0f, y.toFloat(), 864f, y.toFloat(), paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawCurtains(canvas: Canvas) {
        drawTopValance(canvas)
        drawSideCurtain(canvas, leftSide = true)
        drawSideCurtain(canvas, leftSide = false)
    }

    private fun drawTopValance(canvas: Canvas) {
        path.reset()
        path.moveTo(-10f, 72f)
        path.lineTo(874f, 72f)
        path.lineTo(874f, 286f)

        var x = 874f
        var high = true
        while (x >= -10f) {
            path.lineTo(x, if (high) 260f else 326f)
            high = !high
            x -= 72f
        }
        path.lineTo(-10f, 286f)
        path.close()

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            70f,
            0f,
            330f,
            intArrayOf(
                Color.rgb(77, 4, 14),
                Color.rgb(192, 22, 49),
                Color.rgb(91, 4, 20)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        // Top gold trim.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        paint.color = Color.rgb(223, 163, 49)
        canvas.drawLine(0f, 82f, 864f, 82f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSideCurtain(canvas: Canvas, leftSide: Boolean) {
        path.reset()
        if (leftSide) {
            path.moveTo(-25f, 188f)
            path.lineTo(214f, 210f)
            path.cubicTo(207f, 475f, 199f, 715f, 164f, 918f)
            path.cubicTo(148f, 1014f, 124f, 1110f, 78f, 1220f)
            path.lineTo(-25f, 1264f)
        } else {
            path.moveTo(889f, 188f)
            path.lineTo(650f, 210f)
            path.cubicTo(657f, 475f, 665f, 715f, 700f, 918f)
            path.cubicTo(716f, 1014f, 740f, 1110f, 786f, 1220f)
            path.lineTo(889f, 1264f)
        }
        path.close()

        val left = if (leftSide) 0f else 650f
        val right = if (leftSide) 214f else 864f
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            left,
            0f,
            right,
            0f,
            intArrayOf(
                Color.rgb(58, 2, 12),
                Color.rgb(170, 17, 42),
                Color.rgb(104, 6, 24),
                Color.rgb(208, 25, 51),
                Color.rgb(63, 2, 14)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        // Curtain folds.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.argb(95, 255, 125, 135)
        val foldXs = if (leftSide) {
            listOf(38f, 82f, 126f, 168f)
        } else {
            listOf(826f, 782f, 738f, 696f)
        }
        foldXs.forEach { x ->
            canvas.drawLine(x, 230f, if (leftSide) x - 36f else x + 36f, 1160f, paint)
        }
        paint.style = Paint.Style.FILL

        // Tiebacks.
        val tieX = if (leftSide) 151f else 713f
        paint.color = Color.rgb(229, 177, 56)
        canvas.drawOval(RectF(tieX - 34f, 730f, tieX + 34f, 768f), paint)
        paint.color = Color.rgb(255, 223, 112)
        canvas.drawOval(RectF(tieX - 22f, 738f, tieX + 22f, 758f), paint)
    }

    private fun drawForeground(canvas: Canvas) {
        // Stage lip in front of the avatar feet.
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            1070f,
            0f,
            1160f,
            intArrayOf(
                Color.rgb(139, 77, 17),
                Color.rgb(245, 185, 62),
                Color.rgb(73, 31, 7)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(101f, 1074f, 763f, 1142f), 20f, 20f, paint)
        paint.shader = null

        // Footlights.
        val count = 11
        val startX = 142f
        val endX = 722f
        val step = (endX - startX) / (count - 1)
        repeat(count) { index ->
            drawBulb(canvas, startX + index * step, 1110f)
        }

        // Decorative lower framing, leaving the launch button area clear.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.rgb(139, 76, 16)
        canvas.drawRoundRect(RectF(146f, 1182f, 718f, 1277f), 34f, 34f, paint)
        paint.style = Paint.Style.FILL
    }

    companion object {
        private const val DESIGN_WIDTH = 864f
        private const val DESIGN_HEIGHT = 1536f
    }
}
