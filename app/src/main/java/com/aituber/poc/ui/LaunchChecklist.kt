package com.aituber.poc.ui

/** One thing the user may still need to allow before the character can float over an AI app. */
enum class LaunchStep {
    OVERLAY,
    MICROPHONE,
    NOTIFICATIONS,
    READY
}

/**
 * What still stands between the user and the floating character.
 *
 * Overlay and microphone are required. Notifications are optional (they carry the
 * hide/stop buttons) and are asked once; after that a denial is respected.
 */
data class LaunchChecklist(
    val overlayGranted: Boolean,
    val microphoneGranted: Boolean,
    /** False below Android 13, where posting notifications needs no permission. */
    val notificationsApplicable: Boolean,
    val notificationsGranted: Boolean,
    val notificationsAsked: Boolean
) {
    val nextStep: LaunchStep
        get() = when {
            !overlayGranted -> LaunchStep.OVERLAY
            !microphoneGranted -> LaunchStep.MICROPHONE
            notificationsApplicable && !notificationsGranted && !notificationsAsked -> LaunchStep.NOTIFICATIONS
            else -> LaunchStep.READY
        }

    /** The rows of the guide, in the order they are asked. */
    val steps: List<LaunchStep>
        get() = buildList {
            add(LaunchStep.OVERLAY)
            add(LaunchStep.MICROPHONE)
            if (notificationsApplicable) add(LaunchStep.NOTIFICATIONS)
        }

    fun isDone(step: LaunchStep): Boolean = when (step) {
        LaunchStep.OVERLAY -> overlayGranted
        LaunchStep.MICROPHONE -> microphoneGranted
        LaunchStep.NOTIFICATIONS -> !notificationsApplicable || notificationsGranted
        LaunchStep.READY -> nextStep == LaunchStep.READY
    }
}
