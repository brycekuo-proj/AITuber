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

    @Test
    fun haruProfileMapsMouthToParamMouthOpenY() {
        val profile = Live2DCharacterProfiles.Haru

        assertEquals("ParamMouthOpenY", profile.parameterMapping.mouthOpen)
        assertEquals("ParamEyeLOpen", profile.parameterMapping.eyeLeftOpen)
        assertEquals("ParamEyeROpen", profile.parameterMapping.eyeRightOpen)
        assertEquals("ParamBreath", profile.parameterMapping.breath)
    }

    @Test
    fun loafDogProfileMapsMouthToParamMouthOpen() {
        val profile = Live2DCharacterProfiles.LoafDog

        assertEquals("duokhay-loaf-dog", profile.id)
        assertEquals("Loaf Dog", profile.displayName)
        assertEquals("bread dog chonk.model3.json", profile.model3File)
        assertEquals("ParamMouthOpen", profile.parameterMapping.mouthOpen)
        assertEquals("ParamEyeLOpen", profile.parameterMapping.eyeLeftOpen)
        assertEquals("ParamEyeROpen", profile.parameterMapping.eyeRightOpen)
        assertEquals("ParamBreath", profile.parameterMapping.breath)
    }

    @Test
    fun loafDogProfileCapabilitiesMatchAssetAudit() {
        val capabilities = Live2DCharacterProfiles.LoafDog.capabilities

        assertEquals(false, capabilities.idleMotion)
        assertEquals(true, capabilities.physics)
        assertEquals(false, capabilities.pose)
        assertEquals(false, capabilities.expressions)
    }

    @Test
    fun haruProfileCapabilitiesRemainEnabled() {
        val capabilities = Live2DCharacterProfiles.Haru.capabilities

        assertEquals(true, capabilities.idleMotion)
        assertEquals(true, capabilities.physics)
        assertEquals(true, capabilities.pose)
        assertEquals(false, capabilities.expressions)
    }

    @Test
    fun dogParameterBridgeUsesProfileSpecificMouthParameter() {
        val sink = FakeSink(availableParameterIds = setOf("ParamMouthOpen"))
        val profile = Live2DCharacterProfiles.LoafDog

        val result = Live2DParameterBridge(profile.toModelConfig(), sink).apply(frame(0.64f))

        assertEquals("ParamMouthOpen", result.parameterId)
        assertEquals(Live2DParameterStatus.APPLIED, result.status)
        assertTrue(sink.values["ParamMouthOpen"]!! > 0.64f)
        assertTrue(sink.values["ParamMouthOpen"]!! < 1.30f)
        assertEquals(null, sink.values["ParamMouthOpenY"])
    }

    @Test
    fun dogMouthUsesProfileScalingAndNeverExceedsConfiguredSafeMax() {
        val profile = Live2DCharacterProfiles.LoafDog

        assertEquals(0f, profile.mouthTuning.map(0f), 0.0001f)
        assertEquals(1.30f, profile.mouthTuning.map(1f), 0.0001f)
        assertTrue(profile.mouthTuning.map(0.5f) < 0.65f)
    }

    @Test
    fun haruMouthScalingIsUnchangedLinearZeroOne() {
        val profile = Live2DCharacterProfiles.Haru

        assertEquals(0f, profile.mouthTuning.map(0f), 0.0001f)
        assertEquals(0.5f, profile.mouthTuning.map(0.5f), 0.0001f)
        assertEquals(1f, profile.mouthTuning.map(1f), 0.0001f)
    }

    @Test
    fun missingOptionalProfileCapabilityDoesNotMakeAdapterUnavailable() {
        val sink = FakeSink(availableParameterIds = setOf("ParamMouthOpen"))
        val adapter = Live2DCharacterAdapter(
            profile = Live2DCharacterProfiles.LoafDog,
            sdkAvailable = true,
            parameterSink = sink
        )

        adapter.render(frame(0.25f))

        val diagnostics = adapter.diagnosticsSnapshot()
        assertEquals(true, diagnostics.available)
        assertEquals("duokhay-loaf-dog", diagnostics.profileId)
        assertEquals(false, diagnostics.capabilityIdle)
        assertEquals(true, diagnostics.capabilityPhysics)
    }

    @Test
    fun eyeOpenMapsToStandardLive2dEyeParameters() {
        val sink = FakeSink(
            availableParameterIds = setOf("ParamMouthOpenY", "ParamEyeLOpen", "ParamEyeROpen")
        )

        val result = Live2DParameterBridge(Live2DModelConfig(), sink).apply(
            CharacterParameterFrame(
                mouthOpen = 0.2f,
                speaking = true,
                eyeLeftOpen = 0.25f,
                eyeRightOpen = 0.75f
            )
        )

        assertEquals(Live2DParameterStatus.APPLIED, result.leftEyeStatus)
        assertEquals(Live2DParameterStatus.APPLIED, result.rightEyeStatus)
        assertEquals(0.25f, sink.values["ParamEyeLOpen"]!!, 0.0001f)
        assertEquals(0.75f, sink.values["ParamEyeROpen"]!!, 0.0001f)
    }

    @Test
    fun missingEyeParametersDoNotCrashOrAffectMouth() {
        val sink = FakeSink(availableParameterIds = setOf("ParamMouthOpenY"))

        val result = Live2DParameterBridge(Live2DModelConfig(), sink).apply(
            CharacterParameterFrame(
                mouthOpen = 0.4f,
                speaking = true,
                eyeLeftOpen = 0f,
                eyeRightOpen = 0f
            )
        )

        assertEquals(Live2DParameterStatus.APPLIED, result.status)
        assertEquals(Live2DParameterStatus.NOT_FOUND, result.leftEyeStatus)
        assertEquals(Live2DParameterStatus.NOT_FOUND, result.rightEyeStatus)
        assertEquals(0.4f, sink.values["ParamMouthOpenY"]!!, 0.0001f)
        assertEquals(null, sink.values["ParamEyeLOpen"])
        assertEquals(null, sink.values["ParamEyeROpen"])
    }

    @Test
    fun oneEyeMissingStillAppliesExistingEyeSafely() {
        val sink = FakeSink(availableParameterIds = setOf("ParamMouthOpenY", "ParamEyeLOpen"))

        val result = Live2DParameterBridge(Live2DModelConfig(), sink).apply(
            CharacterParameterFrame(
                mouthOpen = 0.4f,
                speaking = true,
                eyeLeftOpen = 0.1f,
                eyeRightOpen = 0.9f
            )
        )

        assertEquals(Live2DParameterStatus.APPLIED, result.leftEyeStatus)
        assertEquals(Live2DParameterStatus.NOT_FOUND, result.rightEyeStatus)
        assertEquals(0.1f, sink.values["ParamEyeLOpen"]!!, 0.0001f)
    }

    @Test
    fun breathMapsToParamBreathWithConservativeIntensity() {
        val sink = FakeSink(availableParameterIds = setOf("ParamMouthOpenY", "ParamBreath"))

        val result = Live2DParameterBridge(Live2DModelConfig(), sink).apply(
            CharacterParameterFrame(
                mouthOpen = 0.4f,
                speaking = true,
                breath = 1f
            )
        )

        assertEquals(Live2DParameterStatus.APPLIED, result.breathStatus)
        assertEquals(0.65f, sink.values["ParamBreath"]!!, 0.0001f)
    }

    @Test
    fun testBreathIntensityCanUseMostOfSafeRangeWithoutChangingNaturalDefault() {
        val sink = FakeSink(availableParameterIds = setOf("ParamMouthOpenY", "ParamBreath"))

        val result = Live2DParameterBridge(Live2DModelConfig(), sink).apply(
            CharacterParameterFrame(
                mouthOpen = 0.4f,
                speaking = true,
                breath = 1f,
                breathIntensity = 1f
            )
        )

        assertEquals(Live2DParameterStatus.APPLIED, result.breathStatus)
        assertEquals(1.0f, sink.values["ParamBreath"]!!, 0.0001f)
    }

    @Test
    fun missingBreathParameterDoesNotCrashOrAffectMouth() {
        val sink = FakeSink(availableParameterIds = setOf("ParamMouthOpenY"))

        val result = Live2DParameterBridge(Live2DModelConfig(), sink).apply(
            CharacterParameterFrame(
                mouthOpen = 0.4f,
                speaking = true,
                breath = 1f
            )
        )

        assertEquals(Live2DParameterStatus.APPLIED, result.status)
        assertEquals(Live2DParameterStatus.NOT_FOUND, result.breathStatus)
        assertEquals(0.4f, sink.values["ParamMouthOpenY"]!!, 0.0001f)
        assertEquals(null, sink.values["ParamBreath"])
    }

    @Test
    fun breathRangeMappingRespectsMinMaxDefaultAndIntensity() {
        val low = Live2DBreathParameterMapper.map(
            normalized = 0f,
            min = -2f,
            max = 2f,
            default = 0f,
            intensity = 0.30f
        )
        val neutral = Live2DBreathParameterMapper.map(
            normalized = 0.5f,
            min = -2f,
            max = 2f,
            default = 0f,
            intensity = 0.30f
        )
        val high = Live2DBreathParameterMapper.map(
            normalized = 1f,
            min = -2f,
            max = 2f,
            default = 0f,
            intensity = 0.30f
        )

        assertEquals(-0.6f, low, 0.0001f)
        assertEquals(0f, neutral, 0.0001f)
        assertEquals(0.6f, high, 0.0001f)
    }

    @Test
    fun breathConservativeAmplitudeDoesNotExceedConfiguredFraction() {
        val value = Live2DBreathParameterMapper.map(
            normalized = 1f,
            min = 0f,
            max = 10f,
            default = 5f,
            intensity = 0.30f
        )

        assertEquals(6.5f, value, 0.0001f)
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
