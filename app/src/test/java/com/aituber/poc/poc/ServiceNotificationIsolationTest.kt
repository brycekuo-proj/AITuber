package com.aituber.poc.poc

import com.aituber.poc.overlay.CharacterOverlayService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServiceNotificationIsolationTest {
    @Test
    fun foregroundNotificationIdsDoNotConflict() {
        assertNotEquals(CaptureSessionService.NOTIFICATION_ID, VisualMotionProbeService.NOTIFICATION_ID)
    }

    @Test
    fun overlayServiceDoesNotUseForegroundNotification() {
        assertFalse(CharacterOverlayService.USES_FOREGROUND_NOTIFICATION)
    }
}
