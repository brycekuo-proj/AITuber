package com.aituber.poc.poc

import com.aituber.poc.state.VisualMotionSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualMotionAnalyzerTest {
    @Test
    fun identicalFramesProduceNearZeroMotionForAllMetrics() {
        val analyzer = VisualMotionAnalyzer(downsampleSize = 8)
        val frame = IntArray(100 * 100) { 0xff202020.toInt() }

        analyzer.metricsFromArgbFrame(100, 100, frame)
        val metrics = analyzer.metricsFromArgbFrame(100, 100, frame)

        assertEquals(0.0, metrics.meanMotion, 0.0001)
        assertEquals(0.0, metrics.changedPixelRatio, 0.0001)
        assertEquals(0.0, metrics.highMotionPercentile, 0.0001)
        assertEquals(0.0, metrics.edgeMotion, 0.0001)
        assertEquals(0.0, metrics.colorMotion, 0.0001)
    }

    @Test
    fun localizedChangedRegionProducesRatioAndPercentileMotion() {
        val analyzer = VisualMotionAnalyzer(downsampleSize = 16, epsilon = 0)
        val first = solidFrame(100, 100, 0xff202020.toInt())
        val second = solidFrame(100, 100, 0xff202020.toInt()).also {
            fillRect(it, 100, 43, 43, 57, 57, 0xfff0f0f0.toInt())
        }

        analyzer.metricsFromArgbFrame(100, 100, first)
        val metrics = analyzer.metricsFromArgbFrame(100, 100, second)

        assertTrue(metrics.changedPixelRatio > 0.01)
        assertTrue(metrics.highMotionPercentile > 0.1)
    }

    @Test
    fun smallLocalizedMotionIsVisibleOutsideMeanMetric() {
        val first = downsampled(16, gray = 32)
        val second = downsampled(16, gray = 32).copy(
            gray = IntArray(16 * 16) { index -> if (index in 112..127) 255 else 32 },
            red = IntArray(16 * 16) { index -> if (index in 112..127) 255 else 32 },
            green = IntArray(16 * 16) { index -> if (index in 112..127) 255 else 32 },
            blue = IntArray(16 * 16) { index -> if (index in 112..127) 255 else 32 }
        )
        val metrics = VisualMotionAnalyzer.metrics(first, second, epsilon = 0)

        assertTrue(metrics.meanMotion < 0.08)
        assertTrue(metrics.changedPixelRatio > 0.0)
        assertTrue(metrics.highMotionPercentile >= metrics.meanMotion)
    }

    @Test
    fun colorOnlyChangeIsDetectedByColorMetric() {
        val analyzer = VisualMotionAnalyzer(downsampleSize = 8, epsilon = 3)
        val first = solidFrame(100, 100, 0xffff0000.toInt())
        val second = solidFrame(100, 100, 0xff00ff00.toInt())

        analyzer.metricsFromArgbFrame(100, 100, first)
        val metrics = analyzer.metricsFromArgbFrame(100, 100, second)

        assertTrue(metrics.colorMotion > 0.5)
    }

    @Test
    fun edgeChangeIsDetectedByEdgeMetric() {
        val firstGray = IntArray(16 * 16) { index -> if (index % 16 < 8) 0 else 255 }
        val secondGray = IntArray(16 * 16) { index -> if (index % 16 < 9) 0 else 255 }
        val first = DownsampledFrame(firstGray, firstGray, firstGray, firstGray, 16)
        val second = DownsampledFrame(secondGray, secondGray, secondGray, secondGray, 16)
        val metrics = VisualMotionAnalyzer.metrics(first, second, epsilon = 0)

        assertTrue(metrics.edgeMotion > 0.01)
    }

    @Test
    fun normalizedRoiIsCenteredAndInsideFrame() {
        val roi = VisualMotionAnalyzer.normalizedCenterRoi(1080, 2400)

        assertEquals(540, roi.left + roi.width / 2)
        assertEquals(1200, roi.top + roi.height / 2)
        assertTrue(roi.width in 510..520)
        assertTrue(roi.left >= 0)
        assertTrue(roi.top >= 0)
        assertTrue(roi.right <= 1080)
        assertTrue(roi.bottom <= 2400)
    }

    @Test
    fun visualMotionSampleDoesNotPersistFrameBuffers() {
        val fieldTypes = VisualMotionSample::class.java.declaredFields.map { it.type }

        assertTrue(fieldTypes.none { it == ByteArray::class.java || it == IntArray::class.java })
    }

    private fun solidFrame(width: Int, height: Int, color: Int): IntArray {
        return IntArray(width * height) { color }
    }

    private fun fillRect(
        pixels: IntArray,
        frameWidth: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        color: Int
    ) {
        for (y in top until bottom) {
            for (x in left until right) {
                pixels[y * frameWidth + x] = color
            }
        }
    }

    private fun downsampled(size: Int, gray: Int): DownsampledFrame {
        val values = IntArray(size * size) { gray }
        return DownsampledFrame(values, values, values, values, size)
    }
}
