package com.aituber.poc.aiadapter

object CaptureStatus {
    const val NOT_STARTED = "Not started"
    const val WAITING_FOR_PERMISSION = "Waiting for MediaProjection permission"
    const val SERVICE_STARTING = "Starting persistent capture session"
    const val CAPTURING = "Capturing playback audio"
    const val BLOCKED_BY_SOURCE_APP = "Playback Capture unavailable or blocked by source app policy"
    /** Playback Capture reads only zeros: the app is silent or opted out of capture (Android cannot tell which). */
    const val CAPTURING_SILENT = "Playback Capture active but receiving silence (app is quiet or blocks capture)"
    const val CAPTURE_FAILED = "Playback Capture stopped after repeated audio errors; restart detection"
    /** No known AI app installed: Playback Capture is skipped, the output-mix Visualizer still works. */
    const val NO_KNOWN_AI_APP = "No known AI app installed; using speaker-output detection only"
    const val RECORD_AUDIO_DENIED = "RECORD_AUDIO permission denied"
    const val MEDIA_PROJECTION_DENIED = "MediaProjection permission was not granted"
    const val MEDIA_PROJECTION_STOPPED = "MediaProjection stopped by system or user"
    const val STOPPED = "Stopped"
}
