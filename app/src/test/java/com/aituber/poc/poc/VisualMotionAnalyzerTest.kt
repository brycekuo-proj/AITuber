package com.aituber.poc.poc

import com.aituber.poc.state.VisualMotionSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualMotionAnalyzerTest {
    @Test
    fun identicalFramesProduceNearZeroMotion() {
        val analyzer = VisualMotionAnalyzer(downsampleSize = 8)
        val frame = IntArray(100 * 100) { 0xff202020.toInt() }

        analyzer.scoreFromArgbFrame(100, 100, frame)
        val score = analyzer.scoreFromArgbFrame(100, 100, frame)

        assertEquals(0.0, score, 0.0001)
    }

    @Test
    fun changedSyntheticFramesProduceMotion() {
        val analyzer = VisualMotionAnalyzer(downsampleSize = 8, epsilon = 0)
        val first = IntArray(100 * 100) { 0xff202020.toInt() }
        val second = IntArray(100 * 100) { 0xfff0f0f0.toInt() }

        analyzer.scoreFromArgbFrame(100, 100, first)
        val score = analyzer.scoreFromArgbFrame(100, 100, second)

        assertTrue(score > 0.1)
    }

    @Test
    fun normalizedRoiIsCenteredAndInsideFrame() {
        val roi = VisualMotionAnalyzer.normalizedCenterRoi(1080, 2400)

        assertEquals(540, roi.left + roi.width / 2)
        assertEquals(1200, roi.top + roi.height / 2)
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
}
