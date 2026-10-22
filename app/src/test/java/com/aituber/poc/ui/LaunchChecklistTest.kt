package com.aituber.poc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchChecklistTest {
    private fun checklist(
        overlay: Boolean = true,
        microphone: Boolean = true,
        notificationsApplicable: Boolean = true,
        notifications: Boolean = true,
        notificationsAsked: Boolean = false
    ) = LaunchChecklist(overlay, microphone, notificationsApplicable, notifications, notificationsAsked)

    @Test
    fun allowedEverythingMeansOneTapLaunch() {
        assertEquals(LaunchStep.READY, checklist().nextStep)
    }

    @Test
    fun asksForTheOverlayFirstThenTheMicrophone() {
        assertEquals(LaunchStep.OVERLAY, checklist(overlay = false, microphone = false).nextStep)
        assertEquals(LaunchStep.MICROPHONE, checklist(microphone = false).nextStep)
    }

    @Test
    fun notificationsAreAskedOnceAndADenialIsRespected() {
        assertEquals(LaunchStep.NOTIFICATIONS, checklist(notifications = false).nextStep)
        assertEquals(LaunchStep.READY, checklist(notifications = false, notificationsAsked = true).nextStep)
    }

    @Test
    fun belowAndroid13NotificationsAreNotAStep() {
        val old = checklist(notificationsApplicable = false, notifications = false)

        assertEquals(LaunchStep.READY, old.nextStep)
        assertEquals(listOf(LaunchStep.OVERLAY, LaunchStep.MICROPHONE), old.steps)
        assertTrue(old.isDone(LaunchStep.NOTIFICATIONS))
    }

    @Test
    fun rowsReportWhatIsDone() {
        val partial = checklist(microphone = false, notifications = false)

        assertTrue(partial.isDone(LaunchStep.OVERLAY))
        assertFalse(partial.isDone(LaunchStep.MICROPHONE))
        assertFalse(partial.isDone(LaunchStep.NOTIFICATIONS))
        assertFalse(partial.isDone(LaunchStep.READY))
    }
}
