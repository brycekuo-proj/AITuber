package com.aituber.poc.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aituber.poc.aiadapter.CaptureStatus
import com.aituber.poc.overlay.CharacterOverlayService
import com.aituber.poc.poc.AndroidPlaybackStateProbe
import com.aituber.poc.poc.CaptureSessionService
import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.poc.ChatGptTarget
import com.aituber.poc.poc.DetectionMethod
import com.aituber.poc.state.CombinedPlaybackRecordingEvent
import com.aituber.poc.state.FineGrainedVoiceEvent
import com.aituber.poc.state.PlaybackProbeEvent
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class MainActivity : Activity() {
    private val projectionRequestCode = 1001
    private val permissionRequestCode = 1002

    private lateinit var universalStateValue: TextView
    private lateinit var voiceSessionValue: TextView
    private lateinit var playbackActiveValue: TextView
    private lateinit var recordingActiveValue: TextView
    private lateinit var audioSourceValue: TextView
    private lateinit var clientSilencedValue: TextView
    private lateinit var combinedCandidateValue: TextView
    private lateinit var confidenceValue: TextView
    private lateinit var speakingSignalValue: TextView

    private lateinit var diagnosticsContainer: LinearLayout
    private lateinit var diagnosticsToggleButton: Button
    private lateinit var targetAppValue: TextView
    private lateinit var detectionMethodValue: TextView
    private lateinit var captureStatusValue: TextView
    private lateinit var currentAudioLevelValue: TextView
    private lateinit var peakAudioLevelValue: TextView
    private lateinit var capturedSamplesValue: TextView
    private lateinit var nonZeroSamplesValue: TextView
    private lateinit var speakingEventsValue: TextView
    private lateinit var lastReadResultValue: TextView
    private lateinit var captureDiagnosticValue: TextView
    private lateinit var playbackCallbackValue: TextView
    private lateinit var registrationAttemptedValue: TextView
    private lateinit var registrationResultValue: TextView
    private lateinit var callbackEventCountValue: TextView
    private lateinit var recordingCallbackEventCountValue: TextView
    private lateinit var activePlaybackCountValue: TextView
    private lateinit var peakActivePlaybackCountValue: TextView
    private lateinit var activePlaybackEventsValue: TextView
    private lateinit var playbackTransitionValue: TextView
    private lateinit var lastActiveTimestampValue: TextView
    private lateinit var observedUsageValue: TextView
    private lateinit var observedContentTypeValue: TextView
    private lateinit var actualSpeakingCandidateValue: TextView
    private lateinit var candidateConfidenceValue: TextView
    private lateinit var lastCandidateChangeValue: TextView
    private lateinit var probeSignalAValue: TextView
    private lateinit var probeSignalBValue: TextView
    private lateinit var probeSignalCValue: TextView
    private lateinit var recordingSessionIdentityValue: TextView
    private lateinit var lastPlaybackEventsValue: TextView
    private lateinit var lastFineGrainedEventsValue: TextView
    private lateinit var lastCombinedEventsValue: TextView
    private lateinit var playbackAttributionValue: TextView
    private lateinit var accessibilityEnabledValue: TextView
    private lateinit var accessibilityObservedPackageValue: TextView
    private lateinit var accessibilityEventCountValue: TextView
    private lateinit var accessibilityRootNodeAvailableValue: TextView
    private lateinit var accessibilityCandidateNodesValue: TextView
    private lateinit var accessibilityUiSignatureValue: TextView
    private lateinit var accessibilityUiSignatureChangedValue: TextView
    private lateinit var accessibilityLastUiChangeValue: TextView
    private lateinit var accessibilityCandidateStateValue: TextView
    private lateinit var lastAccessibilityEventsValue: TextView

    private var diagnosticsExpanded = false
    private val stateListener: (UniversalStateSnapshot) -> Unit = { snapshot ->
        renderSnapshot(snapshot)
    }
    private var playbackProbe: AndroidPlaybackStateProbe? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackProbe = AndroidPlaybackStateProbe(this, CaptureSessionState::current) { snapshot ->
            CaptureSessionState.updatePlaybackProbe(snapshot)
        }
        setContentView(buildUi())
    }

    override fun onStart() {
        super.onStart()
        CaptureSessionState.subscribe(stateListener)
        playbackProbe?.start()
    }

    override fun onStop() {
        CaptureSessionState.unsubscribe(stateListener)
        super.onStop()
    }

    override fun onDestroy() {
        playbackProbe?.stop()
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
            setPadding(36, 44, 36, 36)
            setBackgroundColor(Color.rgb(250, 250, 250))
        }

        root.addView(TextView(this).apply {
            text = "AITuber Debug"
            textSize = 24f
            setTextColor(Color.rgb(24, 28, 36))
            typeface = Typeface.DEFAULT_BOLD
        })

        universalStateValue = addCoreField(root, "Universal State")
        voiceSessionValue = addCoreField(root, "Voice Session")
        playbackActiveValue = addCoreField(root, "Playback Active")
        recordingActiveValue = addCoreField(root, "Recording Active")
        audioSourceValue = addCoreField(root, "Audio Source")
        clientSilencedValue = addCoreField(root, "Client Silenced")
        combinedCandidateValue = addCoreField(root, "Combined Candidate")
        confidenceValue = addCoreField(root, "Confidence")
        speakingSignalValue = addCoreField(root, "Speaking Signal")

        addControls(root)

        diagnosticsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 12, 0, 0)
        }
        root.addView(diagnosticsContainer)
        addDiagnosticsFields(diagnosticsContainer)

        return ScrollView(this).apply {
            addView(root)
        }
    }

    private fun addControls(root: LinearLayout) {
        root.addView(Button(this).apply {
            text = "START CHATGPT CAPTURE"
            setOnClickListener { startDetection() }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "STOP"
            setOnClickListener { stopCaptureService() }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "OPEN OVERLAY PERMISSION"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "OPEN ACCESSIBILITY SETTINGS"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "ENABLE MOUTH OVERLAY"
            setOnClickListener { enableMouthOverlay() }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "DISABLE MOUTH OVERLAY"
            setOnClickListener { disableMouthOverlay() }
        }, buttonLayoutParams())

        diagnosticsToggleButton = Button(this).apply {
            text = "SHOW DIAGNOSTICS"
            setOnClickListener { toggleDiagnostics() }
        }
        root.addView(diagnosticsToggleButton, buttonLayoutParams())
    }

    private fun addDiagnosticsFields(root: LinearLayout) {
        root.addView(sectionTitle("Diagnostics"))
        targetAppValue = addDiagnosticField(root, "Target App")
        detectionMethodValue = addDiagnosticField(root, "Detection Method")
        captureStatusValue = addDiagnosticField(root, "Capture Status")
        currentAudioLevelValue = addDiagnosticField(root, "Current Audio Level")
        peakAudioLevelValue = addDiagnosticField(root, "Peak Audio Level")
        capturedSamplesValue = addDiagnosticField(root, "Captured Samples")
        nonZeroSamplesValue = addDiagnosticField(root, "Non-zero Samples")
        speakingEventsValue = addDiagnosticField(root, "Speaking Events")
        lastReadResultValue = addDiagnosticField(root, "Last Read Result")
        captureDiagnosticValue = addDiagnosticField(root, "Capture Diagnostic")

        root.addView(sectionTitle("Callback Counters"))
        playbackCallbackValue = addDiagnosticField(root, "Playback Callback")
        registrationAttemptedValue = addDiagnosticField(root, "Registration Attempted")
        registrationResultValue = addDiagnosticField(root, "Registration Result")
        callbackEventCountValue = addDiagnosticField(root, "Playback Callback Events")
        recordingCallbackEventCountValue = addDiagnosticField(root, "Recording Callback Events")
        activePlaybackCountValue = addDiagnosticField(root, "Active Playback Count")
        peakActivePlaybackCountValue = addDiagnosticField(root, "Peak Active Playback Count")
        activePlaybackEventsValue = addDiagnosticField(root, "Active Playback Events")
        playbackTransitionValue = addDiagnosticField(root, "Playback Transitions")
        lastActiveTimestampValue = addDiagnosticField(root, "Last Active Timestamp")

        root.addView(sectionTitle("Fine-Grained Signals"))
        observedUsageValue = addDiagnosticField(root, "Observed Usage")
        observedContentTypeValue = addDiagnosticField(root, "Observed Content Type")
        actualSpeakingCandidateValue = addDiagnosticField(root, "Actual Speaking Candidate")
        candidateConfidenceValue = addDiagnosticField(root, "Candidate Confidence")
        lastCandidateChangeValue = addDiagnosticField(root, "Last Candidate Change")
        probeSignalAValue = addDiagnosticField(root, "Config Identity")
        probeSignalBValue = addDiagnosticField(root, "Audio Mode / Device")
        probeSignalCValue = addDiagnosticField(root, "Callback Timing")
        recordingSessionIdentityValue = addDiagnosticField(root, "Recording Identity")
        playbackAttributionValue = addDiagnosticField(root, "Attribution")

        root.addView(sectionTitle("Accessibility Probe"))
        accessibilityEnabledValue = addDiagnosticField(root, "Accessibility Enabled")
        accessibilityObservedPackageValue = addDiagnosticField(root, "Observed Package")
        accessibilityEventCountValue = addDiagnosticField(root, "Accessibility Event Count")
        accessibilityRootNodeAvailableValue = addDiagnosticField(root, "Root Node Available")
        accessibilityCandidateNodesValue = addDiagnosticField(root, "Voice UI Candidate Nodes")
        accessibilityUiSignatureValue = addDiagnosticField(root, "UI Signature")
        accessibilityUiSignatureChangedValue = addDiagnosticField(root, "UI Signature Changed")
        accessibilityLastUiChangeValue = addDiagnosticField(root, "Last UI Change")
        accessibilityCandidateStateValue = addDiagnosticField(root, "Accessibility Candidate State")

        root.addView(sectionTitle("Event Log"))
        lastPlaybackEventsValue = addLogField(root, "Last 10 Playback Events")
        lastFineGrainedEventsValue = addLogField(root, "Last 20 Fine-Grained Events")
        lastCombinedEventsValue = addLogField(root, "Last 20 Combined Events")
        lastAccessibilityEventsValue = addLogField(root, "Last 20 Accessibility Events")
    }

    private fun addCoreField(root: LinearLayout, label: String): TextView {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 18, 0, 0)
        }
        block.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.rgb(92, 98, 112))
            typeface = Typeface.DEFAULT_BOLD
        })
        return TextView(this).apply {
            textSize = 20f
            setTextColor(Color.rgb(18, 22, 30))
            includeFontPadding = true
            block.addView(this)
            root.addView(block)
        }
    }

    private fun addDiagnosticField(root: LinearLayout, label: String): TextView {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 0)
        }
        block.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.rgb(92, 98, 112))
            typeface = Typeface.DEFAULT_BOLD
        })
        return TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(30, 34, 44))
            block.addView(this)
            root.addView(block)
        }
    }

    private fun addLogField(root: LinearLayout, label: String): TextView {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 0)
        }
        block.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.rgb(92, 98, 112))
            typeface = Typeface.DEFAULT_BOLD
        })
        return TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(24, 28, 36))
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.rgb(238, 240, 244))
            setPadding(12, 10, 12, 10)
            block.addView(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            root.addView(block)
        }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.rgb(24, 28, 36))
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 22, 0, 0)
    }

    private fun buttonLayoutParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = 12 }

    private fun toggleDiagnostics() {
        diagnosticsExpanded = !diagnosticsExpanded
        diagnosticsContainer.visibility = if (diagnosticsExpanded) View.VISIBLE else View.GONE
        diagnosticsToggleButton.text = if (diagnosticsExpanded) "HIDE DIAGNOSTICS" else "SHOW DIAGNOSTICS"
    }

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

    private fun enableMouthOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            return
        }
        startService(Intent(this, CharacterOverlayService::class.java))
    }

    private fun disableMouthOverlay() {
        stopService(Intent(this, CharacterOverlayService::class.java))
    }

    private fun renderSnapshot(snapshot: UniversalStateSnapshot) {
        runOnUiThread {
            universalStateValue.text = snapshot.state.name
            voiceSessionValue.text = activeLabel(snapshot.playbackProbe.voiceSessionActive)
            playbackActiveValue.text = activeLabel(snapshot.playbackProbe.playbackSessionActive)
            recordingActiveValue.text = activeLabel(snapshot.playbackProbe.recordingSessionActive)
            audioSourceValue.text = compactAudioSource(snapshot.playbackProbe.observedAudioSource)
            clientSilencedValue.text = snapshot.playbackProbe.clientSilenced
            combinedCandidateValue.text = snapshot.playbackProbe.combinedCandidateState
            confidenceValue.text = snapshot.playbackProbe.combinedCandidateConfidence
            speakingSignalValue.text = "Not established"

            targetAppValue.text = snapshot.targetApp
            detectionMethodValue.text = snapshot.detectionMethod
            captureStatusValue.text = snapshot.captureStatus
            currentAudioLevelValue.text = snapshot.diagnostics.currentAudioLevel?.let { "%.3f".format(it) } ?: "n/a"
            peakAudioLevelValue.text = "%.3f".format(snapshot.diagnostics.peakAudioLevel)
            capturedSamplesValue.text = snapshot.diagnostics.capturedSamples.toString()
            nonZeroSamplesValue.text = snapshot.diagnostics.nonZeroSamples.toString()
            speakingEventsValue.text = snapshot.diagnostics.speakingEvents.toString()
            lastReadResultValue.text = snapshot.diagnostics.lastReadResult?.toString() ?: "n/a"
            captureDiagnosticValue.text = snapshot.diagnostics.diagnostic.label
            playbackCallbackValue.text = snapshot.playbackProbe.callbackStatus
            registrationAttemptedValue.text = snapshot.playbackProbe.registrationAttempted
            registrationResultValue.text = snapshot.playbackProbe.registrationResult
            callbackEventCountValue.text = snapshot.playbackProbe.callbackEventCount.toString()
            recordingCallbackEventCountValue.text = snapshot.playbackProbe.recordingCallbackEventCount.toString()
            activePlaybackCountValue.text = snapshot.playbackProbe.activePlaybackCount.toString()
            peakActivePlaybackCountValue.text = snapshot.playbackProbe.peakActivePlaybackCount.toString()
            activePlaybackEventsValue.text = snapshot.playbackProbe.activePlaybackEvents.toString()
            playbackTransitionValue.text =
                "active=${snapshot.playbackProbe.playbackBecameActiveCount} inactive=${snapshot.playbackProbe.playbackBecameInactiveCount}"
            lastActiveTimestampValue.text = snapshot.playbackProbe.lastActiveElapsedMs?.let { "$it ms" } ?: "n/a"
            observedUsageValue.text = snapshot.playbackProbe.observedUsage
            observedContentTypeValue.text = snapshot.playbackProbe.observedContentType
            actualSpeakingCandidateValue.text = snapshot.playbackProbe.actualSpeakingCandidate
            candidateConfidenceValue.text = snapshot.playbackProbe.candidateConfidence
            lastCandidateChangeValue.text = snapshot.playbackProbe.lastCandidateChangeElapsedMs?.let { "$it ms" } ?: "n/a"
            probeSignalAValue.text = snapshot.playbackProbe.probeSignalA
            probeSignalBValue.text = snapshot.playbackProbe.probeSignalB
            probeSignalCValue.text = snapshot.playbackProbe.probeSignalC
            recordingSessionIdentityValue.text = snapshot.playbackProbe.recordingSessionIdentity
            playbackAttributionValue.text = snapshot.playbackProbe.attribution
            lastPlaybackEventsValue.text = compactPlaybackLog(snapshot.playbackProbe.lastPlaybackEvents)
            lastFineGrainedEventsValue.text = compactFineGrainedLog(snapshot.playbackProbe.lastFineGrainedEvents)
            lastCombinedEventsValue.text = compactCombinedLog(snapshot.playbackProbe.lastCombinedEvents)
            accessibilityEnabledValue.text = accessibilityEnabledLabel(snapshot.accessibilityProbe.enabled)
            accessibilityObservedPackageValue.text = snapshot.accessibilityProbe.observedPackage
            accessibilityEventCountValue.text = snapshot.accessibilityProbe.eventCount.toString()
            accessibilityRootNodeAvailableValue.text = snapshot.accessibilityProbe.rootNodeAvailable
            accessibilityCandidateNodesValue.text = snapshot.accessibilityProbe.voiceUiCandidateNodes.toString()
            accessibilityUiSignatureValue.text = snapshot.accessibilityProbe.uiSignature
            accessibilityUiSignatureChangedValue.text = snapshot.accessibilityProbe.uiSignatureChanged
            accessibilityLastUiChangeValue.text = snapshot.accessibilityProbe.lastUiChangeElapsedMs?.let { "$it ms" } ?: "n/a"
            accessibilityCandidateStateValue.text = snapshot.accessibilityProbe.candidateState
            lastAccessibilityEventsValue.text = snapshot.accessibilityProbe.lastEvents.joinToString("\n") { event ->
                "${event.elapsedTimestampMs} | ${event.eventType.shortEventType()} | sig=${event.uiSignature} | nodes=${event.candidateNodeCount}"
            }.ifBlank { "n/a" }
        }
    }

    private fun accessibilityEnabledLabel(serviceSnapshotValue: String): String {
        return if (isAccessibilityServiceEnabled()) "ENABLED" else serviceSnapshotValue
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/.accessibility.ChatGptAccessibilityProbeService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { serviceName ->
            serviceName.equals(expected, ignoreCase = true) ||
                serviceName.equals("$packageName/com.aituber.poc.accessibility.ChatGptAccessibilityProbeService", ignoreCase = true)
        }
    }

    private fun activeLabel(value: String): String {
        return if (value == "YES") "ACTIVE" else "INACTIVE"
    }

    private fun compactAudioSource(value: String): String {
        return if (value == "n/a") value else value.replace("VOICE_COMMUNICATION", "VOICE_COMM")
    }

    private fun compactPlaybackLog(events: List<PlaybackProbeEvent>): String {
        return events.joinToString("\n") { event ->
            "${event.elapsedTimestampMs} | P=${event.activePlaybackCount} | ${event.usage.shortUsage()} | ${event.contentType.shortContent()}"
        }.ifBlank { "n/a" }
    }

    private fun compactFineGrainedLog(events: List<FineGrainedVoiceEvent>): String {
        return events.joinToString("\n") { event ->
            "${event.elapsedTimestampMs} | P=${event.activePlaybackCount} | ${event.usage.shortUsage()} | " +
                "${event.contentType.shortContent()} | cfg=${event.configurationIdentity} | ${event.publicAudioModeAndDeviceSignal.shortMode()}"
        }.ifBlank { "n/a" }
    }

    private fun compactCombinedLog(events: List<CombinedPlaybackRecordingEvent>): String {
        return events.joinToString("\n") { event ->
            "${event.elapsedTimestampMs} | P=${event.playbackActiveCount} | R=${event.recordingActiveCount} | " +
                "${event.playbackUsage.shortUsage()} | ${event.playbackContentType.shortContent()} | " +
                "src=${event.audioSource.shortSource()} | sil=${event.clientSilenced} | ${event.audioManagerMode.shortMode()}"
        }.ifBlank { "n/a" }
    }

    private fun String.shortUsage() = replace("USAGE_VOICE_COMMUNICATION", "VC")
        .replace("USAGE_MEDIA", "MEDIA")
        .replace("USAGE_UNKNOWN", "U?")

    private fun String.shortContent() = replace("CONTENT_TYPE_SPEECH", "SP")
        .replace("CONTENT_TYPE_MUSIC", "MUSIC")
        .replace("CONTENT_TYPE_UNKNOWN", "C?")

    private fun String.shortSource() = replace("VOICE_COMMUNICATION", "VOICE_COMM")
        .replace("VOICE_RECOGNITION", "VOICE_REC")

    private fun String.shortMode() = replace("MODE_IN_COMMUNICATION", "mode=COMM")
        .replace("MODE_NORMAL", "mode=NORMAL")

    private fun String.shortEventType() = replace("WINDOW_CONTENT_CHANGED", "CONTENT")
        .replace("WINDOW_STATE_CHANGED", "WINDOW")
        .replace("WINDOWS_CHANGED", "WINDOWS")
        .replace("VIEW_ACCESSIBILITY_FOCUSED", "A11Y_FOCUS")
        .replace("VIEW_FOCUSED", "FOCUS")
        .replace("VIEW_SELECTED", "SELECTED")
}
