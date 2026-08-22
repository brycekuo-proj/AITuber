package com.aituber.poc.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.aituber.poc.aiadapter.CaptureStatus
import com.aituber.poc.poc.AndroidPlaybackStateProbe
import com.aituber.poc.poc.AccessibilityProbeState
import com.aituber.poc.poc.CaptureSessionService
import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.poc.ChatGptAccessibilityProbeService
import com.aituber.poc.poc.ChatGptTarget
import com.aituber.poc.poc.DetectionMethod
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class MainActivity : Activity() {
    private val projectionRequestCode = 1001
    private val permissionRequestCode = 1002

    private lateinit var targetAppValue: TextView
    private lateinit var detectionMethodValue: TextView
    private lateinit var stateValue: TextView
    private lateinit var audioLevelValue: TextView
    private lateinit var peakAudioLevelValue: TextView
    private lateinit var capturedSamplesValue: TextView
    private lateinit var nonZeroSamplesValue: TextView
    private lateinit var speakingEventsValue: TextView
    private lateinit var lastNonZeroAudioValue: TextView
    private lateinit var lastReadResultValue: TextView
    private lateinit var captureDiagnosticValue: TextView
    private lateinit var captureStatusValue: TextView
    private lateinit var playbackCallbackValue: TextView
    private lateinit var registrationAttemptedValue: TextView
    private lateinit var registrationResultValue: TextView
    private lateinit var callbackEventCountValue: TextView
    private lateinit var lastCallbackTimestampValue: TextView
    private lateinit var activePlaybackCountValue: TextView
    private lateinit var peakActivePlaybackCountValue: TextView
    private lateinit var activePlaybackEventsValue: TextView
    private lateinit var playbackBecameActiveCountValue: TextView
    private lateinit var playbackBecameInactiveCountValue: TextView
    private lateinit var lastNonZeroActiveCountValue: TextView
    private lateinit var lastActiveTimestampValue: TextView
    private lateinit var lastObservedUsageWhileActiveValue: TextView
    private lateinit var lastObservedContentTypeWhileActiveValue: TextView
    private lateinit var lastPlaybackEventsValue: TextView
    private lateinit var chatGptPlaybackDetectedValue: TextView
    private lateinit var chatGptPlaybackStateValue: TextView
    private lateinit var lastPlaybackChangeValue: TextView
    private lateinit var observedUsageValue: TextView
    private lateinit var observedContentTypeValue: TextView
    private lateinit var observedPlayerStateValue: TextView
    private lateinit var playbackAttributionValue: TextView
    private lateinit var accessibilityServiceValue: TextView
    private lateinit var chatGptWindowDetectedValue: TextView
    private lateinit var accessibilityLastEventTypeValue: TextView
    private lateinit var accessibilityLastEventTimestampValue: TextView
    private lateinit var rootNodeAvailableValue: TextView
    private lateinit var voiceUiCandidateDetectedValue: TextView
    private lateinit var candidateStateValue: TextView
    private lateinit var candidateSignalSummaryValue: TextView
    private lateinit var accessibilityEventCountValue: TextView
    private lateinit var windowChangeCountValue: TextView
    private lateinit var nodeTreeChangeCountValue: TextView
    private lateinit var distinctViewIdsObservedValue: TextView
    private lateinit var distinctContentDescriptionStatePatternsValue: TextView
    private lateinit var lastAccessibilityEventSummariesValue: TextView
    private lateinit var levelBar: ProgressBar

    private val stateListener: (UniversalStateSnapshot) -> Unit = { snapshot ->
        renderSnapshot(snapshot)
    }
    private var playbackProbe: AndroidPlaybackStateProbe? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackProbe = AndroidPlaybackStateProbe(this) { snapshot ->
            CaptureSessionState.updatePlaybackProbe(snapshot)
        }
        setContentView(buildUi())
    }

    override fun onStart() {
        super.onStart()
        AccessibilityProbeState.setServiceEnabled(isAccessibilityProbeEnabled())
        CaptureSessionState.subscribe(stateListener)
        playbackProbe?.start()
    }

    override fun onStop() {
        CaptureSessionState.unsubscribe(stateListener)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            playbackProbe?.stop()
        }
        super.onDestroy()
    }

    @Deprecated("Used for the minimal PoC Activity result flow.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != projectionRequestCode) return

        if (resultCode == RESULT_OK && data != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startCaptureService(resultCode, data)
        } else {
            publishLocal(CaptureStatus.MEDIA_PROJECTION_DENIED)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != permissionRequestCode) return

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            requestPlaybackCapture()
        } else {
            publishLocal(CaptureStatus.RECORD_AUDIO_DENIED)
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 40)
            setBackgroundColor(Color.rgb(250, 250, 250))
        }

        root.addView(TextView(this).apply {
            text = "AITuber Android PoC"
            textSize = 24f
            setTextColor(Color.rgb(24, 28, 36))
        })

        targetAppValue = addField(root, "Target App")
        detectionMethodValue = addField(root, "Detection Method")
        stateValue = addField(root, "State")
        audioLevelValue = addField(root, "Current Audio Level")
        peakAudioLevelValue = addField(root, "Peak Audio Level")
        capturedSamplesValue = addField(root, "Captured Frames/Samples")
        nonZeroSamplesValue = addField(root, "Non-zero Frames/Samples")
        speakingEventsValue = addField(root, "Speaking Events")
        lastNonZeroAudioValue = addField(root, "Last Non-zero Audio")
        lastReadResultValue = addField(root, "Last Read Result")
        captureDiagnosticValue = addField(root, "Capture Diagnostic")
        captureStatusValue = addField(root, "Capture Status")
        playbackCallbackValue = addField(root, "Playback Callback")
        registrationAttemptedValue = addField(root, "Registration Attempted")
        registrationResultValue = addField(root, "Registration Result")
        callbackEventCountValue = addField(root, "Callback Event Count")
        lastCallbackTimestampValue = addField(root, "Last Callback Timestamp")
        activePlaybackCountValue = addField(root, "Active Playback Count")
        peakActivePlaybackCountValue = addField(root, "Peak Active Playback Count")
        activePlaybackEventsValue = addField(root, "Active Playback Events")
        playbackBecameActiveCountValue = addField(root, "Playback Became Active Count")
        playbackBecameInactiveCountValue = addField(root, "Playback Became Inactive Count")
        lastNonZeroActiveCountValue = addField(root, "Last Non-zero Active Count")
        lastActiveTimestampValue = addField(root, "Last Active Timestamp")
        lastObservedUsageWhileActiveValue = addField(root, "Last Observed Usage While Active")
        lastObservedContentTypeWhileActiveValue = addField(root, "Last Observed Content Type While Active")
        lastPlaybackEventsValue = addField(root, "Last 10 Playback Events")
        chatGptPlaybackDetectedValue = addField(root, "ChatGPT Playback Detected")
        chatGptPlaybackStateValue = addField(root, "ChatGPT Playback State")
        lastPlaybackChangeValue = addField(root, "Last Playback Change")
        observedUsageValue = addField(root, "Observed Usage")
        observedContentTypeValue = addField(root, "Observed Content Type")
        observedPlayerStateValue = addField(root, "Observed Player State")
        playbackAttributionValue = addField(root, "Playback Attribution")
        accessibilityServiceValue = addField(root, "Accessibility Service")
        chatGptWindowDetectedValue = addField(root, "ChatGPT Window Detected")
        accessibilityLastEventTypeValue = addField(root, "Last Event Type")
        accessibilityLastEventTimestampValue = addField(root, "Last Event Timestamp")
        rootNodeAvailableValue = addField(root, "Root Node Available")
        voiceUiCandidateDetectedValue = addField(root, "Voice UI Candidate Detected")
        candidateStateValue = addField(root, "Candidate State")
        candidateSignalSummaryValue = addField(root, "Candidate Signal Summary")
        accessibilityEventCountValue = addField(root, "Event Count")
        windowChangeCountValue = addField(root, "Window Change Count")
        nodeTreeChangeCountValue = addField(root, "Node Tree Change Count")
        distinctViewIdsObservedValue = addField(root, "Distinct View IDs observed")
        distinctContentDescriptionStatePatternsValue = addField(root, "Distinct content-description-presence/state patterns")
        lastAccessibilityEventSummariesValue = addField(root, "Last 20 safe event summaries")

        levelBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        root.addView(levelBar)

        root.addView(Button(this).apply {
            text = "Start ChatGPT Capture"
            setOnClickListener { startDetection() }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener { stopCaptureService() }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "Open Overlay Permission"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }, buttonLayoutParams())

        return ScrollView(this).apply {
            addView(root)
        }
    }

    private fun addField(root: LinearLayout, label: String): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 18 }
        }
        row.addView(TextView(this).apply {
            text = "$label:"
            textSize = 15f
            setTextColor(Color.rgb(80, 86, 98))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        return TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(20, 24, 32))
            gravity = Gravity.END
            row.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.25f))
            root.addView(row)
        }
    }

    private fun buttonLayoutParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = 14 }

    private fun startDetection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            publishLocal("Android Playback Capture requires Android 10 / API 29 or later")
            return
        }
        if (!ChatGptTarget.isInstalled(this)) {
            publishLocal(CaptureStatus.CHATGPT_NOT_INSTALLED)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestCapturePermissions()
            return
        }
        requestPlaybackCapture()
    }

    private fun requestCapturePermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray(), permissionRequestCode)
    }

    private fun requestPlaybackCapture() {
        CaptureSessionState.update(
            UniversalStateSnapshot(
                targetApp = ChatGptTarget.label,
                detectionMethod = DetectionMethod.PLAYBACK_CAPTURE.label,
                state = UniversalAiState.UNKNOWN,
                audioLevel = null,
                captureStatus = CaptureStatus.WAITING_FOR_PERMISSION
            )
        )
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequestCode)
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, CaptureSessionService::class.java).apply {
            action = CaptureSessionService.ACTION_START
            putExtra(CaptureSessionService.EXTRA_RESULT_CODE, resultCode)
            putExtra(CaptureSessionService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopCaptureService() {
        val intent = Intent(this, CaptureSessionService::class.java).apply {
            action = CaptureSessionService.ACTION_STOP
        }
        startService(intent)
    }

    private fun publishLocal(status: String) {
        CaptureSessionState.update(
            UniversalStateSnapshot(
                targetApp = ChatGptTarget.label,
                detectionMethod = DetectionMethod.PLAYBACK_CAPTURE.label,
                state = UniversalAiState.UNKNOWN,
                audioLevel = null,
                captureStatus = status
            )
        )
    }

    private fun isAccessibilityProbeEnabled(): Boolean {
        val expected = ComponentName(this, ChatGptAccessibilityProbeService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { service -> service.equals(expected, ignoreCase = true) }
    }

    private fun renderSnapshot(snapshot: UniversalStateSnapshot) {
        runOnUiThread {
            targetAppValue.text = snapshot.targetApp
            detectionMethodValue.text = snapshot.detectionMethod
            stateValue.text = snapshot.state.name
            audioLevelValue.text = snapshot.diagnostics.currentAudioLevel?.let { "%.3f".format(it) } ?: "n/a"
            peakAudioLevelValue.text = "%.3f".format(snapshot.diagnostics.peakAudioLevel)
            capturedSamplesValue.text = snapshot.diagnostics.capturedSamples.toString()
            nonZeroSamplesValue.text = snapshot.diagnostics.nonZeroSamples.toString()
            speakingEventsValue.text = snapshot.diagnostics.speakingEvents.toString()
            lastNonZeroAudioValue.text = snapshot.diagnostics.lastNonZeroAudioElapsedMs?.let { "$it ms" } ?: "n/a"
            lastReadResultValue.text = snapshot.diagnostics.lastReadResult?.toString() ?: "n/a"
            captureDiagnosticValue.text = snapshot.diagnostics.diagnostic.label
            captureStatusValue.text = snapshot.captureStatus
            playbackCallbackValue.text = snapshot.playbackProbe.callbackStatus
            registrationAttemptedValue.text = snapshot.playbackProbe.registrationAttempted
            registrationResultValue.text = snapshot.playbackProbe.registrationResult
            callbackEventCountValue.text = snapshot.playbackProbe.callbackEventCount.toString()
            lastCallbackTimestampValue.text = snapshot.playbackProbe.lastCallbackElapsedMs?.let { "$it ms" } ?: "n/a"
            activePlaybackCountValue.text = snapshot.playbackProbe.activePlaybackCount.toString()
            peakActivePlaybackCountValue.text = snapshot.playbackProbe.peakActivePlaybackCount.toString()
            activePlaybackEventsValue.text = snapshot.playbackProbe.activePlaybackEvents.toString()
            playbackBecameActiveCountValue.text = snapshot.playbackProbe.playbackBecameActiveCount.toString()
            playbackBecameInactiveCountValue.text = snapshot.playbackProbe.playbackBecameInactiveCount.toString()
            lastNonZeroActiveCountValue.text = snapshot.playbackProbe.lastNonZeroActiveCount.toString()
            lastActiveTimestampValue.text = snapshot.playbackProbe.lastActiveElapsedMs?.let { "$it ms" } ?: "n/a"
            lastObservedUsageWhileActiveValue.text = snapshot.playbackProbe.lastObservedUsageWhileActive
            lastObservedContentTypeWhileActiveValue.text = snapshot.playbackProbe.lastObservedContentTypeWhileActive
            lastPlaybackEventsValue.text = snapshot.playbackProbe.lastPlaybackEvents.joinToString("\n") { event ->
                "${event.elapsedTimestampMs} ms | count=${event.activePlaybackCount} | ${event.usage} | ${event.contentType}"
            }.ifBlank { "n/a" }
            chatGptPlaybackDetectedValue.text = snapshot.playbackProbe.chatGptPlaybackDetected
            chatGptPlaybackStateValue.text = snapshot.playbackProbe.chatGptPlaybackState
            lastPlaybackChangeValue.text = snapshot.playbackProbe.lastPlaybackChangeElapsedMs?.let { "$it ms" } ?: "n/a"
            observedUsageValue.text = snapshot.playbackProbe.observedUsage
            observedContentTypeValue.text = snapshot.playbackProbe.observedContentType
            observedPlayerStateValue.text = snapshot.playbackProbe.observedPlayerState
            playbackAttributionValue.text = snapshot.playbackProbe.attribution
            accessibilityServiceValue.text = snapshot.accessibilityProbe.serviceStatus
            chatGptWindowDetectedValue.text = snapshot.accessibilityProbe.chatGptWindowDetected
            accessibilityLastEventTypeValue.text = snapshot.accessibilityProbe.lastEventType
            accessibilityLastEventTimestampValue.text = snapshot.accessibilityProbe.lastEventElapsedMs?.let { "$it ms" } ?: "n/a"
            rootNodeAvailableValue.text = snapshot.accessibilityProbe.rootNodeAvailable
            voiceUiCandidateDetectedValue.text = snapshot.accessibilityProbe.voiceUiCandidateDetected
            candidateStateValue.text = snapshot.accessibilityProbe.candidateState
            candidateSignalSummaryValue.text = snapshot.accessibilityProbe.candidateSignalSummary
            accessibilityEventCountValue.text = snapshot.accessibilityProbe.eventCount.toString()
            windowChangeCountValue.text = snapshot.accessibilityProbe.windowChangeCount.toString()
            nodeTreeChangeCountValue.text = snapshot.accessibilityProbe.nodeTreeChangeCount.toString()
            distinctViewIdsObservedValue.text = snapshot.accessibilityProbe.distinctViewIdsObserved.joinToString("\n").ifBlank { "n/a" }
            distinctContentDescriptionStatePatternsValue.text =
                snapshot.accessibilityProbe.distinctContentDescriptionStatePatterns.joinToString("\n").ifBlank { "n/a" }
            lastAccessibilityEventSummariesValue.text =
                snapshot.accessibilityProbe.lastEventSummaries.joinToString("\n") { event ->
                    "${event.elapsedTimestampMs} ms | ${event.eventType} | root=${event.rootNodeAvailable} | candidate=${event.voiceUiCandidateDetected} | ${event.signalSummary}"
                }.ifBlank { "n/a" }
            levelBar.progress = ((snapshot.diagnostics.currentAudioLevel ?: 0f) * 100).toInt().coerceIn(0, 100)
        }
    }
}
