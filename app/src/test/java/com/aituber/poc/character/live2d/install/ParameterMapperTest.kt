package com.aituber.poc.character.live2d.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterMapperTest {

    @Test
    fun mocIdScanNeedsWholeZeroPaddedIds() {
        val moc = TestPackages.moc(parameterIds = listOf("ParamMouthOpenY", "ParamEyeLOpen"))
        assertTrue(ParameterMapper.mocContainsId(moc, "ParamMouthOpenY"))
        assertTrue(ParameterMapper.mocContainsId(moc, "ParamEyeLOpen"))
        // Prefixes of stored ids must not match.
        assertFalse(ParameterMapper.mocContainsId(moc, "ParamMouthOpen"))
        assertFalse(ParameterMapper.mocContainsId(moc, "ParamEye"))
        assertFalse(ParameterMapper.mocContainsId(moc, "ParamBreath"))
    }

    @Test
    fun eyeBlinkIdsAreSplitBySide() {
        assertEquals(
            listOf("PARAM_EYE_L_OPEN") to listOf("PARAM_EYE_R_OPEN"),
            ParameterMapper.splitEyes(listOf("PARAM_EYE_L_OPEN", "PARAM_EYE_R_OPEN"))
        )
        assertEquals(
            listOf("EyeOpenLeft") to listOf("EyeOpenRight"),
            ParameterMapper.splitEyes(listOf("EyeOpenRight", "EyeOpenLeft"))
        )
        // Unknown naming: first is left, second is right.
        assertEquals(listOf("Blink1") to listOf("Blink2"), ParameterMapper.splitEyes(listOf("Blink1", "Blink2")))
    }

    @Test
    fun groupsBeatDefaultsAndDefaultsAreAlwaysAppended() {
        val result = ParameterMapper.map(
            ParameterMapper.Evidence(lipSyncIds = listOf("ParamMouthCustom"))
        )
        val candidates = result.mapping.mouthOpen.split('|')
        assertEquals("ParamMouthCustom", candidates.first())
        assertTrue("ParamMouthOpenY" in candidates)
        assertTrue(candidates.size <= 6)
        assertTrue(result.lipSyncFound)
    }

    @Test
    fun cubism2StyleIdsComeFirstWhenKnown() {
        val result = ParameterMapper.map(
            ParameterMapper.Evidence(knownIds = setOf("PARAM_MOUTH_OPEN_Y", "PARAM_BREATH", "PARAM_ANGLE_X"))
        )
        assertTrue(result.mapping.mouthOpen.startsWith("PARAM_MOUTH_OPEN_Y|"))
        assertTrue(result.mapping.breath.startsWith("PARAM_BREATH|"))
        assertTrue(result.mapping.headX!!.startsWith("PARAM_ANGLE_X|"))
        assertTrue(result.lipSyncFound)
    }

    @Test
    fun noEvidenceMeansNoLipSync() {
        val result = ParameterMapper.map(ParameterMapper.Evidence())
        assertFalse(result.lipSyncFound)
        assertTrue(result.mapping.mouthOpen.startsWith("ParamMouthOpenY|"))
    }
}
