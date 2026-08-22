package com.aituber.poc.poc

import com.aituber.poc.state.RoiBounds
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class VisualMotionAnalyzer(
    private val downsampleSize: Int = 64,
    private val epsilon: Int = 3
) {
    private var previousGray: IntArray? = null

    fun reset() {
        previousGray = null
    }

    fun scoreFromArgbFrame(width: Int, height: Int, pixels: IntArray): Double {
        val roi = normalizedCenterRoi(width, height)
        val gray = downsampleArgbRoi(width, pixels, roi)
        val previous = previousGray
        previousGray = gray
        if (previous == null) return 0.0
        return normalizedMeanAbsoluteDifference(previous, gray, epsilon)
    }

    fun scoreFromRgbaBytes(
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        bytes: ByteArray
    ): Double {
        val roi = normalizedCenterRoi(width, height)
        val gray = downsampleRgbaRoi(width, rowStride, pixelStride, bytes, roi)
        val previous = previousGray
        previousGray = gray
        if (previous == null) return 0.0
        return normalizedMeanAbsoluteDifference(previous, gray, epsilon)
    }

    fun roiBounds(width: Int, height: Int): RoiBounds = normalizedCenterRoi(width, height)

    private fun downsampleArgbRoi(frameWidth: Int, pixels: IntArray, roi: RoiBounds): IntArray {
        val out = IntArray(downsampleSize * downsampleSize)
        for (y in 0 until downsampleSize) {
            val sourceY = roi.top + y * roi.height / downsampleSize
            for (x in 0 until downsampleSize) {
                val sourceX = roi.left + x * roi.width / downsampleSize
                val pixel = pixels[sourceY * frameWidth + sourceX]
                val r = pixel shr 16 and 0xff
                val g = pixel shr 8 and 0xff
                val b = pixel and 0xff
                out[y * downsampleSize + x] = (r * 30 + g * 59 + b * 11) / 100
            }
        }
        return out
    }

    private fun downsampleRgbaRoi(
        frameWidth: Int,
        rowStride: Int,
        pixelStride: Int,
        bytes: ByteArray,
        roi: RoiBounds
    ): IntArray {
        val out = IntArray(downsampleSize * downsampleSize)
        for (y in 0 until downsampleSize) {
            val sourceY = roi.top + y * roi.height / downsampleSize
            for (x in 0 until downsampleSize) {
                val sourceX = roi.left + x * roi.width / downsampleSize
                val offset = sourceY * rowStride + sourceX * pixelStride
                val r = bytes.getOrNull(offset)?.toInt()?.and(0xff) ?: 0
                val g = bytes.getOrNull(offset + 1)?.toInt()?.and(0xff) ?: 0
                val b = bytes.getOrNull(offset + 2)?.toInt()?.and(0xff) ?: 0
                out[y * downsampleSize + x] = (r * 30 + g * 59 + b * 11) / 100
            }
        }
        return out
    }

    companion object {
        fun normalizedCenterRoi(width: Int, height: Int): RoiBounds {
            val side = min((width * 0.62).toInt(), (height * 0.38).toInt()).coerceAtLeast(1)
            val centerX = width / 2
            val centerY = height / 2
            val left = (centerX - side / 2).coerceIn(0, max(0, width - side))
            val top = (centerY - side / 2).coerceIn(0, max(0, height - side))
            return RoiBounds(left, top, left + side, top + side)
        }

        fun normalizedMeanAbsoluteDifference(previous: IntArray, current: IntArray, epsilon: Int): Double {
            val count = min(previous.size, current.size)
            if (count == 0) return 0.0
            var sum = 0.0
            for (index in 0 until count) {
                val diff = abs(current[index] - previous[index])
                if (diff > epsilon) {
                    sum += diff
                }
            }
            return (sum / count / 255.0).coerceIn(0.0, 1.0)
        }
    }
}
