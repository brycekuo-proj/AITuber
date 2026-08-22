package com.aituber.poc.aiadapter

object CaptureStatus {
    const val NOT_STARTED = "Not started"
    const val WAITING_FOR_PERMISSION = "Waiting for MediaProjection permission"
    const val SERVICE_STARTING = "Starting persistent capture session"
    const val CAPTURING = "Capturing playback audio"
    const val BLOCKED_BY_SOURCE_APP = "Playback Capture unavailable or blocked by source app policy"
    const val CHATGPT_NOT_INSTALLED = "ChatGPT is not installed: com.openai.chatgpt"
    const val RECORD_AUDIO_DENIED = "RECORD_AUDIO permission denied"
    const val MEDIA_PROJECTION_DENIED = "MediaProjection permission was not granted"
    const val MEDIA_PROJECTION_STOPPED = "MediaProjection stopped by system or user"
    const val STOPPED = "Stopped"
}
