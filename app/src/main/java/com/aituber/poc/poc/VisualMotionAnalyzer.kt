package com.aituber.poc.poc

import com.aituber.poc.state.RoiBounds
import com.aituber.poc.state.VisualMotionMetrics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class VisualMotionAnalyzer(
    private val downsampleSize: Int = 128,
    private val epsilon: Int = 3
) {
    private var previousFrame: DownsampledFrame? = null

    fun reset() {
        previousFrame = null
    }

    fun metricsFromArgbFrame(width: Int, height: Int, pixels: IntArray): VisualMotionMetrics {
        val roi = normalizedCenterRoi(width, height)
        val current = downsampleArgbRoi(width, pixels, roi)
        val previous = previousFrame
        previousFrame = current
        if (previous == null) return VisualMotionMetrics.zero()
        return metrics(previous, current, epsilon)
    }

    fun metricsFromRgbaBytes(
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        bytes: ByteArray
    ): VisualMotionMetrics {
        val roi = normalizedCenterRoi(width, height)
        val current = downsampleRgbaRoi(rowStride, pixelStride, bytes, roi)
        val previous = previousFrame
        previousFrame = current
        if (previous == null) return VisualMotionMetrics.zero()
        return metrics(previous, current, epsilon)
    }

    fun roiBounds(width: Int, height: Int): RoiBounds = normalizedCenterRoi(width, height)

    private fun downsampleArgbRoi(frameWidth: Int, pixels: IntArray, roi: RoiBounds): DownsampledFrame {
        val gray = IntArray(downsampleSize * downsampleSize)
        val red = IntArray(gray.size)
        val green = IntArray(gray.size)
        val blue = IntArray(gray.size)
        for (y in 0 until downsampleSize) {
            val sourceY = roi.top + y * roi.height / downsampleSize
            for (x in 0 until downsampleSize) {
                val sourceX = roi.left + x * roi.width / downsampleSize
                val pixel = pixels[sourceY * frameWidth + sourceX]
                val index = y * downsampleSize + x
                red[index] = pixel shr 16 and 0xff
                green[index] = pixel shr 8 and 0xff
                blue[index] = pixel and 0xff
                gray[index] = luminance(red[index], green[index], blue[index])
            }
        }
        return DownsampledFrame(gray, red, green, blue, downsampleSize)
    }

    private fun downsampleRgbaRoi(
        rowStride: Int,
        pixelStride: Int,
        bytes: ByteArray,
        roi: RoiBounds
    ): DownsampledFrame {
        val gray = IntArray(downsampleSize * downsampleSize)
        val red = IntArray(gray.size)
        val green = IntArray(gray.size)
        val blue = IntArray(gray.size)
        for (y in 0 until downsampleSize) {
            val sourceY = roi.top + y * roi.height / downsampleSize
            for (x in 0 until downsampleSize) {
                val sourceX = roi.left + x * roi.width / downsampleSize
                val offset = sourceY * rowStride + sourceX * pixelStride
                val index = y * downsampleSize + x
                red[index] = bytes.getOrNull(offset)?.toInt()?.and(0xff) ?: 0
                green[index] = bytes.getOrNull(offset + 1)?.toInt()?.and(0xff) ?: 0
                blue[index] = bytes.getOrNull(offset + 2)?.toInt()?.and(0xff) ?: 0
                gray[index] = luminance(red[index], green[index], blue[index])
            }
        }
        return DownsampledFrame(gray, red, green, blue, downsampleSize)
    }

    companion object {
        fun normalizedCenterRoi(width: Int, height: Int): RoiBounds {
            val side = min((width * 0.48).toInt(), (height * 0.32).toInt()).coerceAtLeast(1)
            val centerX = width / 2
            val centerY = height / 2
            val left = (centerX - side / 2).coerceIn(0, max(0, width - side))
            val top = (centerY - side / 2).coerceIn(0, max(0, height - side))
            return RoiBounds(left, top, left + side, top + side)
        }

        fun metrics(previous: DownsampledFrame, current: DownsampledFrame, epsilon: Int): VisualMotionMetrics {
            val count = min(previous.gray.size, current.gray.size)
            if (count == 0) return VisualMotionMetrics.zero()
            val diffs = IntArray(count)
            var graySum = 0.0
            var changed = 0
            var colorSum = 0.0
            for (index in 0 until count) {
                val diff = abs(current.gray[index] - previous.gray[index])
                diffs[index] = diff
                if (diff > epsilon) {
                    graySum += diff
                    changed += 1
                }
                colorSum += (
                    abs(current.red[index] - previous.red[index]) +
                        abs(current.green[index] - previous.green[index]) +
                        abs(current.blue[index] - previous.blue[index])
                    )
            }
            diffs.sort()
            val p95Index = ((count - 1) * 0.95).toInt().coerceIn(0, count - 1)
            return VisualMotionMetrics(
                meanMotion = (graySum / count / 255.0).coerceIn(0.0, 1.0),
                changedPixelRatio = (changed.toDouble() / count).coerceIn(0.0, 1.0),
                highMotionPercentile = (diffs[p95Index] / 255.0).coerceIn(0.0, 1.0),
                edgeMotion = edgeMotion(previous.gray, current.gray, previous.size, epsilon),
                colorMotion = (colorSum / count / (3.0 * 255.0)).coerceIn(0.0, 1.0)
            )
        }

        private fun edgeMotion(previous: IntArray, current: IntArray, size: Int, epsilon: Int): Double {
            var sum = 0.0
            var count = 0
            for (y in 1 until size - 1) {
                for (x in 1 until size - 1) {
                    val index = y * size + x
                    val previousGradient = abs(previous[index + 1] - previous[index - 1]) +
                        abs(previous[index + size] - previous[index - size])
                    val currentGradient = abs(current[index + 1] - current[index - 1]) +
                        abs(current[index + size] - current[index - size])
                    val diff = abs(currentGradient - previousGradient)
                    if (diff > epsilon) {
                        sum += diff
                    }
                    count += 1
                }
            }
            return if (count == 0) 0.0 else (sum / count / 510.0).coerceIn(0.0, 1.0)
        }

        private fun luminance(red: Int, green: Int, blue: Int): Int {
            return (red * 30 + green * 59 + blue * 11) / 100
        }
    }
}

data class DownsampledFrame(
    val gray: IntArray,
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    val size: Int
)
