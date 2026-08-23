package com.aituber.poc.character.live2d

import com.aituber.poc.character.CharacterParameterFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DParameterBridgeTest {
    @Test
    fun mouthOpenZeroMapsToParamMouthOpenYZero() {
        val sink = FakeSink()
        val result = Live2DParameterBridge(Live2DModelConfig(), sink).apply(frame(0f))

        assertEquals("ParamMouthOpenY", result.parameterId)
        assertEquals(0f, result.value, 0.0001f)
        assertEquals(0f, sink.values["ParamMouthOpenY"]!!, 0.0001f)
    }

    @Test
    fun mouthOpenHalfMapsToParamMouthOpenYHalf() {
        val sink = FakeSink()
        val result = Live2DParameterBridge(Live2DModelConfig(), sink).apply(frame(0.5f))

        assertEquals(0.5f, result.value, 0.0001f)
        assertEquals(0.5f, sink.values["ParamMouthOpenY"]!!, 0.0001f)
    }

    @Test
    fun mouthOpenOneMapsToParamMouthOpenYOne() {
        val sink = FakeSink()
        val result = Live2DParameterBridge(Live2DModelConfig(), sink).apply(frame(1f))

        assertEquals(1f, result.value, 0.0001f)
        assertEquals(1f, sink.values["ParamMouthOpenY"]!!, 0.0001f)
    }

    @Test
    fun mouthOpenClampsToZeroOne() {
        val lowSink = FakeSink()
        val highSink = FakeSink()

        val low = Live2DParameterBridge(Live2DModelConfig(), lowSink).apply(frame(-1f))
        val high = Live2DParameterBridge(Live2DModelConfig(), highSink).apply(frame(2f))

        assertEquals(0f, low.value, 0.0001f)
        assertEquals(1f, high.value, 0.0001f)
    }

    @Test
    fun missingMouthParameterDoesNotCrash() {
        val sink = FakeSink(availableParameterIds = emptySet())

        val result = Live2DParameterBridge(Live2DModelConfig(), sink).apply(frame(0.7f))

        assertEquals(Live2DParameterStatus.NOT_FOUND, result.status)
        assertEquals(null, sink.values["ParamMouthOpenY"])
    }

    @Test
    fun live2dUnavailableAdapterReportsUnavailableWithoutCrash() {
        val adapter = Live2DCharacterAdapter(sdkAvailable = false, parameterSink = null)

        adapter.render(frame(0.5f))
        val diagnostics = adapter.diagnosticsSnapshot()

        assertEquals(false, diagnostics.available)
        assertEquals(false, diagnostics.modelLoaded)
        assertEquals("ParamMouthOpenY", diagnostics.mouthParameterId)
    }

    @Test
    fun live2dAdapterAppliesCharacterParameterFrameToMouthParameter() {
        val sink = FakeSink()
        val adapter = Live2DCharacterAdapter(sdkAvailable = true, parameterSink = sink)

        adapter.render(frame(0.62f))

        assertEquals(0.62f, sink.values["ParamMouthOpenY"]!!, 0.0001f)
        assertTrue(adapter.diagnosticsSnapshot().available)
    }

    private fun frame(mouthOpen: Float) = CharacterParameterFrame(
        mouthOpen = mouthOpen,
        speaking = mouthOpen > 0f
    )

    private class FakeSink(
        private val availableParameterIds: Set<String> = setOf("ParamMouthOpenY")
    ) : Live2DParameterSink {
        val values = mutableMapOf<String, Float>()

        override fun setParameter(parameterId: String, value: Float): Boolean {
            if (!availableParameterIds.contains(parameterId)) return false
            values[parameterId] = value
            return true
        }
    }
}
