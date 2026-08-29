package com.aituber.poc.viseme

import org.junit.Assert.assertEquals
import org.junit.Test

class MandarinVisemeClassifierTest {
    private val classifier = MandarinVisemeClassifier()

    @Test
    fun prototypeFormantsMapToExpectedVisemes() {
        assertEquals(MandarinVisemeClassifier.Viseme.A, classifier.classifyFormantsForTest(780.0, 1250.0))
        assertEquals(MandarinVisemeClassifier.Viseme.E, classifier.classifyFormantsForTest(500.0, 1650.0))
        assertEquals(MandarinVisemeClassifier.Viseme.I, classifier.classifyFormantsForTest(300.0, 2450.0))
        assertEquals(MandarinVisemeClassifier.Viseme.O, classifier.classifyFormantsForTest(500.0, 950.0))
        assertEquals(MandarinVisemeClassifier.Viseme.U, classifier.classifyFormantsForTest(340.0, 800.0))
    }

    @Test
    fun silenceReturnsRest() {
        val silence = ShortArray(512)
        val result = classifier.classify(silence)
        assertEquals(MandarinVisemeClassifier.Viseme.REST, result.rawViseme)
    }
}
