package com.aituber.poc.aiadapter

object CaptureStatus {
    const val NOT_STARTED = "Not started"
    const val WAITING_FOR_PERMISSION = "Waiting for MediaProjection permission"
    const val CAPTURING = "Capturing playback audio"
    const val BLOCKED_BY_SOURCE_APP = "Playback Capture unavailable or blocked by source app policy"
    const val RECORD_AUDIO_DENIED = "RECORD_AUDIO permission denied"
    const val STOPPED = "Stopped"
}
