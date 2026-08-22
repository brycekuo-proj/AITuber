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
import com.aituber.poc.poc.VisualizerAudioProbe
import com.aituber.poc.poc.VisualMotionProbeService
import com.aituber.poc.state.CombinedPlaybackRecordingEvent
import com.aituber.poc.state.FineGrainedVoiceEvent
import com.aituber.poc.state.PlaybackProbeEvent
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import com.aituber.poc.state.VisualMotionMetrics
import com.aituber.poc.state.VisualMotionPhaseSummary
import com.aituber.poc.state.VisualizerPhaseSummary
import com.aituber.poc.state.VisualizerWaveformMetrics

class MainActivity : Activity() {
    private val projectionRequestCode = 1001
    private val visualProjectionRequestCode = 1003
    private val visualTestProjectionRequestCode = 1004
    private val permissionRequestCode = 1002
    private val visualizerPermissionRequestCode = 1005

    private lateinit var universalStateValue: TextView
    private lateinit var voiceSessionValue: TextView
    private lateinit var playbackActiveValue: TextView
    private lateinit var recordingActiveValue: TextView
    private lateinit var audioSourceValue: TextView
    private lateinit var clientSilencedValue: TextView
    private lateinit var combinedCandidateValue: TextView
    private lateinit var confidenceValue: TextView
    private lateinit var speakingSignalValue: TextView
    private lateinit var visualizerSignalValue: TextView
    private lateinit var visualizerRmsCoreValue: TextView
    private lateinit var visualizerPeakCoreValue: TextView
    private lateinit var visualizerActivityCoreValue: TextView
    private lateinit var derivedSpeakingCoreValue: TextView

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
    private lateinit var centerCandidatePresentValue: TextView
    private lateinit var centerCandidateBoundsValue: TextView
    private lateinit var centerChildCountValue: TextView
    private lateinit var centerChangeRate1sValue: TextView
    private lateinit var centerChangeRate3sValue: TextView
    private lateinit var currentTestPhaseValue: TextView
    private lateinit var quietAverageRateValue: TextView
    private lateinit var userAverageRateValue: TextView
    private lateinit var aiAverageRateValue: TextView
    private lateinit var centerProbeSampleCountValue: TextView
    private lateinit var centerHistoryValue: TextView
    private lateinit var lastValidChatGptSignatureValue: TextView
    private lateinit var validSignatureEventCountValue: TextView
    private lateinit var signatureTransitionCountValue: TextView
    private lateinit var ignoredEmptyEventsValue: TextView
    private lateinit var duplicateSignatureEventsValue: TextView
    private lateinit var trackedAccessibilityNodesValue: TextView
    private lateinit var dynamicCandidateCountValue: TextView
    private lateinit var topDynamicCandidateNodesValue: TextView
    private lateinit var topCandidateSnapshotHistoryValue: TextView
    private lateinit var signatureTransitionsValue: TextView
    private lateinit var lastAccessibilityEventsValue: TextView
    private lateinit var visualProbeActiveValue: TextView
    private lateinit var visualRoiBoundsValue: TextView
    private lateinit var visualMotionAlgorithmValue: TextView
    private lateinit var currentMotionValue: TextView
    private lateinit var motionAvg1sValue: TextView
    private lateinit var motionAvg3sValue: TextView
    private lateinit var peakMotionValue: TextView
    private lateinit var validFramesValue: TextView
    private lateinit var skippedFramesValue: TextView
    private lateinit var processingMsValue: TextView
    private lateinit var visualCurrentPhaseValue: TextView
    private lateinit var quietMotionAverageValue: TextView
    private lateinit var userMotionAverageValue: TextView
    private lateinit var aiMotionAverageValue: TextView
    private lateinit var quietMotionPeakValue: TextView
    private lateinit var userMotionPeakValue: TextView
    private lateinit var aiMotionPeakValue: TextView
    private lateinit var aiQuietRatioValue: TextView
    private lateinit var aiUserRatioValue: TextView
    private lateinit var visualMotionHistoryValue: TextView
    private lateinit var visualizerInitStatusValue: TextView
    private lateinit var visualizerEnabledValue: TextView
    private lateinit var visualizerCaptureSizeValue: TextView
    private lateinit var visualizerCaptureRateValue: TextView
    private lateinit var visualizerCallbackCountValue: TextView
    private lateinit var visualizerCurrentRmsValue: TextView
    private lateinit var visualizerCurrentPeakValue: TextView
    private lateinit var visualizerCurrentActivityValue: TextView
    private lateinit var visualizerOutputMixStatusValue: TextView
    private lateinit var visualizerCurrentPhaseValue: TextView
    private lateinit var visualizerDetectorThresholdsValue: TextView
    private lateinit var visualizerDetectorAttackReleaseValue: TextView
    private lateinit var visualizerDetectorHysteresisValue: TextView
    private lateinit var visualizerDetectorTransitionsValue: TextView
    private lateinit var visualizerQuietSummaryValue: TextView
    private lateinit var visualizerUserSummaryValue: TextView
    private lateinit var visualizerAiSummaryValue: TextView
    private lateinit var visualizerAiQuietRatioValue: TextView
    private lateinit var visualizerAiUserRatioValue: TextView
    private lateinit var visualizerHistoryValue: TextView

    private var diagnosticsExpanded = false
    private var pendingVisualizerAutomatedTest = true
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
        when (requestCode) {
            projectionRequestCode -> {
                if (resultCode == RESULT_OK && data != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startCaptureService(resultCode, data)
                } else {
                    publishLocal(CaptureStatus.MEDIA_PROJECTION_DENIED)
                }
            }
            visualProjectionRequestCode -> {
                if (resultCode == RESULT_OK && data != null) {
                    startVisualMotionService(resultCode, data, automatedTest = false)
                }
            }
            visualTestProjectionRequestCode -> {
                if (resultCode == RESULT_OK && data != null) {
                    startVisualMotionService(resultCode, data, automatedTest = true)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            permissionRequestCode -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    requestPlaybackCapture()
                } else {
                    publishLocal(CaptureStatus.RECORD_AUDIO_DENIED)
                }
            }
            visualizerPermissionRequestCode -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startVisualizerProbeAfterPermission(pendingVisualizerAutomatedTest)
                } else {
                    publishLocal(CaptureStatus.RECORD_AUDIO_DENIED)
                }
            }
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
        visualizerSignalValue = addCoreField(root, "Visualizer Signal")
        visualizerRmsCoreValue = addCoreField(root, "RMS")
        visualizerPeakCoreValue = addCoreField(root, "Peak")
        visualizerActivityCoreValue = addCoreField(root, "Activity")
        derivedSpeakingCoreValue = addCoreField(root, "Derived Speaking")

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
            text = "START VISUAL MOTION PROBE"
            setOnClickListener { requestVisualMotionProbe(automatedTest = false) }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "STOP VISUAL MOTION PROBE"
            setOnClickListener { stopVisualMotionService() }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "START 30S VISUAL TEST"
            setOnClickListener { requestVisualMotionProbe(automatedTest = true) }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "START 30S VISUALIZER TEST"
            setOnClickListener { startVisualizerProbe(automatedTest = true) }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "START VISUALIZER DETECTOR"
            setOnClickListener { startVisualizerProbe(automatedTest = false) }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "STOP VISUALIZER TEST"
            setOnClickListener { VisualizerAudioProbe.stop() }
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
            text = "MARK QUIET"
            setOnClickListener { CaptureSessionState.markAccessibilityTestPhase("QUIET") }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "MARK USER"
            setOnClickListener { CaptureSessionState.markAccessibilityTestPhase("USER") }
        }, buttonLayoutParams())

        root.addView(Button(this).apply {
            text = "MARK AI"
            setOnClickListener { CaptureSessionState.markAccessibilityTestPhase("AI") }
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
        root.addView(sectionTitle("Center Voice UI Probe"))
        centerCandidatePresentValue = addDiagnosticField(root, "Center Candidate Present")
        centerCandidateBoundsValue = addDiagnosticField(root, "Center Candidate Bounds")
        centerChildCountValue = addDiagnosticField(root, "Center Child Count")
        centerChangeRate1sValue = addDiagnosticField(root, "Center Change Rate 1s")
        centerChangeRate3sValue = addDiagnosticField(root, "Center Change Rate 3s")
        currentTestPhaseValue = addDiagnosticField(root, "Current Test Phase")
        quietAverageRateValue = addDiagnosticField(root, "QUIET Average Rate")
        userAverageRateValue = addDiagnosticField(root, "USER Average Rate")
        aiAverageRateValue = addDiagnosticField(root, "AI Average Rate")
        centerProbeSampleCountValue = addDiagnosticField(root, "Center Probe Sample Count")

        root.addView(sectionTitle("Candidate Node Diagnostics"))
        lastValidChatGptSignatureValue = addDiagnosticField(root, "Last Valid ChatGPT Signature")
        validSignatureEventCountValue = addDiagnosticField(root, "Valid Signature Event Count")
        signatureTransitionCountValue = addDiagnosticField(root, "Signature Transition Count")
        ignoredEmptyEventsValue = addDiagnosticField(root, "Ignored Empty Events")
        duplicateSignatureEventsValue = addDiagnosticField(root, "Duplicate Signature Events")
        trackedAccessibilityNodesValue = addDiagnosticField(root, "Tracked Accessibility Nodes")
        dynamicCandidateCountValue = addDiagnosticField(root, "Dynamic Candidate Count")

        root.addView(sectionTitle("Event Log"))
        lastPlaybackEventsValue = addLogField(root, "Last 10 Playback Events")
        lastFineGrainedEventsValue = addLogField(root, "Last 20 Fine-Grained Events")
        lastCombinedEventsValue = addLogField(root, "Last 20 Combined Events")
        root.addView(sectionTitle("Visual Motion Probe"))
        visualProbeActiveValue = addDiagnosticField(root, "Visual Probe Active")
        visualRoiBoundsValue = addDiagnosticField(root, "ROI Bounds")
        visualMotionAlgorithmValue = addDiagnosticField(root, "Motion Algorithm")
        currentMotionValue = addDiagnosticField(root, "Current Motion")
        motionAvg1sValue = addDiagnosticField(root, "Motion Avg 1s")
        motionAvg3sValue = addDiagnosticField(root, "Motion Avg 3s")
        peakMotionValue = addDiagnosticField(root, "Peak Motion")
        validFramesValue = addDiagnosticField(root, "Valid Frames")
        skippedFramesValue = addDiagnosticField(root, "Dropped/Skipped Frames")
        processingMsValue = addDiagnosticField(root, "Average Processing")
        visualCurrentPhaseValue = addDiagnosticField(root, "Current Test Phase")
        quietMotionAverageValue = addDiagnosticField(root, "QUIET Summary")
        userMotionAverageValue = addDiagnosticField(root, "USER Summary")
        aiMotionAverageValue = addDiagnosticField(root, "AI Summary")
        quietMotionPeakValue = addDiagnosticField(root, "QUIET Peak")
        userMotionPeakValue = addDiagnosticField(root, "USER Peak")
        aiMotionPeakValue = addDiagnosticField(root, "AI Peak")
        aiQuietRatioValue = addDiagnosticField(root, "AI / QUIET Ratios")
        aiUserRatioValue = addDiagnosticField(root, "AI / USER Ratios")
        visualMotionHistoryValue = addLogField(root, "Visual Motion History")
        root.addView(sectionTitle("Visualizer Output Mix Probe"))
        visualizerInitStatusValue = addDiagnosticField(root, "Visualizer Init Status")
        visualizerEnabledValue = addDiagnosticField(root, "Visualizer Enabled")
        visualizerCaptureSizeValue = addDiagnosticField(root, "Capture Size")
        visualizerCaptureRateValue = addDiagnosticField(root, "Capture Rate")
        visualizerCallbackCountValue = addDiagnosticField(root, "Waveform Callback Count")
        visualizerCurrentRmsValue = addDiagnosticField(root, "Current RMS")
        visualizerCurrentPeakValue = addDiagnosticField(root, "Current Peak")
        visualizerCurrentActivityValue = addDiagnosticField(root, "Current Activity Ratio")
        visualizerOutputMixStatusValue = addDiagnosticField(root, "Output Mix Signal Status")
        visualizerCurrentPhaseValue = addDiagnosticField(root, "Current Test Phase")
        visualizerDetectorThresholdsValue = addDiagnosticField(root, "Detector Thresholds")
        visualizerDetectorAttackReleaseValue = addDiagnosticField(root, "Attack / Release")
        visualizerDetectorHysteresisValue = addDiagnosticField(root, "Detector Hysteresis")
        visualizerDetectorTransitionsValue = addDiagnosticField(root, "Detector Transitions")
        visualizerQuietSummaryValue = addDiagnosticField(root, "QUIET RMS Avg / Peak")
        visualizerUserSummaryValue = addDiagnosticField(root, "USER RMS Avg / Peak")
        visualizerAiSummaryValue = addDiagnosticField(root, "AI RMS Avg / Peak")
        visualizerAiQuietRatioValue = addDiagnosticField(root, "AI / QUIET RMS+Peak Ratio")
        visualizerAiUserRatioValue = addDiagnosticField(root, "AI / USER RMS+Peak Ratio")
        visualizerHistoryValue = addLogField(root, "Visualizer History")
        centerHistoryValue = addLogField(root, "Center History")
        topDynamicCandidateNodesValue = addLogField(root, "Top 10 Dynamic Candidate Nodes")
        topCandidateSnapshotHistoryValue = addLogField(root, "Top Candidate Snapshot History")
        signatureTransitionsValue = addLogField(root, "Last 50 Signature Transitions")
        lastAccessibilityEventsValue = addLogField(root, "Last 30 Accessibility Events")
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

    private fun requestVisualMotionProbe(automatedTest: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            publishLocal("Visual motion probe requires Android 10 / API 29 or later")
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val requestCode = if (automatedTest) visualTestProjectionRequestCode else visualProjectionRequestCode
        startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
    }

    private fun startVisualizerProbe(automatedTest: Boolean) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingVisualizerAutomatedTest = automatedTest
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), visualizerPermissionRequestCode)
            return
        }
        startVisualizerProbeAfterPermission(automatedTest)
    }

    private fun startVisualizerProbeAfterPermission(automatedTest: Boolean) {
        if (automatedTest) {
            VisualizerAudioProbe.startThirtySecondTest()
        } else {
            VisualizerAudioProbe.startDetector()
        }
    }

    private fun startVisualMotionService(resultCode: Int, data: Intent, automatedTest: Boolean) {
        val intent = Intent(this, VisualMotionProbeService::class.java).apply {
            action = if (automatedTest) {
                VisualMotionProbeService.ACTION_START_30S_TEST
            } else {
                VisualMotionProbeService.ACTION_START
            }
            putExtra(VisualMotionProbeService.EXTRA_RESULT_CODE, resultCode)
            putExtra(VisualMotionProbeService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopVisualMotionService() {
        val intent = Intent(this, VisualMotionProbeService::class.java).apply {
            action = VisualMotionProbeService.ACTION_STOP
        }
        startService(intent)
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
            speakingSignalValue.text = snapshot.speakingSignalSource
            visualizerSignalValue.text = snapshot.visualizerProbe.outputMixSignalStatus
            visualizerRmsCoreValue.text = "%.3f".format(snapshot.visualizerProbe.currentMetrics.rms)
            visualizerPeakCoreValue.text = "%.3f".format(snapshot.visualizerProbe.currentMetrics.peak)
            visualizerActivityCoreValue.text = "%.3f".format(snapshot.visualizerProbe.currentMetrics.activityRatio)
            derivedSpeakingCoreValue.text = snapshot.visualizerProbe.derivedSpeaking

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
            visualProbeActiveValue.text = snapshot.visualMotion.active
            visualRoiBoundsValue.text = snapshot.visualMotion.roiBounds
            visualMotionAlgorithmValue.text = snapshot.visualMotion.motionAlgorithm
            currentMotionValue.text = compactMetrics(snapshot.visualMotion.currentMetrics)
            motionAvg1sValue.text = compactMetrics(snapshot.visualMotion.average1s)
            motionAvg3sValue.text = compactMetrics(snapshot.visualMotion.average3s)
            peakMotionValue.text =
                "filtered=${"%.3f".format(snapshot.visualMotion.filteredPeakMotionScore)} raw=${"%.3f".format(snapshot.visualMotion.rawPeakMotionScore)}"
            validFramesValue.text = snapshot.visualMotion.validFrameCount.toString()
            skippedFramesValue.text = snapshot.visualMotion.skippedFrameCount.toString()
            processingMsValue.text = "%.1f ms/frame".format(snapshot.visualMotion.averageProcessingMs)
            visualCurrentPhaseValue.text = snapshot.visualMotion.currentTestPhase
            quietMotionAverageValue.text = compactSummary(snapshot.visualMotion.quietSummary)
            userMotionAverageValue.text = compactSummary(snapshot.visualMotion.userSummary)
            aiMotionAverageValue.text = compactSummary(snapshot.visualMotion.aiSummary)
            quietMotionPeakValue.text = compactPeak(snapshot.visualMotion.quietSummary)
            userMotionPeakValue.text = compactPeak(snapshot.visualMotion.userSummary)
            aiMotionPeakValue.text = compactPeak(snapshot.visualMotion.aiSummary)
            aiQuietRatioValue.text = compactRatios(
                snapshot.visualMotion.aiQuietMeanRatio,
                snapshot.visualMotion.aiQuietChangedPixelRatio,
                snapshot.visualMotion.aiQuietHighMotionRatio,
                snapshot.visualMotion.aiQuietEdgeMotionRatio,
                snapshot.visualMotion.aiQuietColorMotionRatio
            )
            aiUserRatioValue.text = compactRatios(
                snapshot.visualMotion.aiUserMeanRatio,
                snapshot.visualMotion.aiUserChangedPixelRatio,
                snapshot.visualMotion.aiUserHighMotionRatio,
                snapshot.visualMotion.aiUserEdgeMotionRatio,
                snapshot.visualMotion.aiUserColorMotionRatio
            )
            visualMotionHistoryValue.text = snapshot.visualMotion.history.takeLast(100).joinToString("\n") { sample ->
                "${sample.elapsedTimestampMs} | ${sample.phase} | ${compactMetrics(sample.metrics)} | excluded=${sample.excludedFromSummary}"
            }.ifBlank { "n/a" }
            visualizerInitStatusValue.text = snapshot.visualizerProbe.initStatus
            visualizerEnabledValue.text = snapshot.visualizerProbe.enabled
            visualizerCaptureSizeValue.text = snapshot.visualizerProbe.captureSize.toString()
            visualizerCaptureRateValue.text = snapshot.visualizerProbe.captureRate.toString()
            visualizerCallbackCountValue.text = snapshot.visualizerProbe.waveformCallbackCount.toString()
            visualizerCurrentRmsValue.text = "%.3f".format(snapshot.visualizerProbe.currentMetrics.rms)
            visualizerCurrentPeakValue.text = "%.3f".format(snapshot.visualizerProbe.currentMetrics.peak)
            visualizerCurrentActivityValue.text = "%.3f".format(snapshot.visualizerProbe.currentMetrics.activityRatio)
            visualizerOutputMixStatusValue.text = snapshot.visualizerProbe.outputMixSignalStatus
            visualizerCurrentPhaseValue.text = snapshot.visualizerProbe.currentTestPhase
            visualizerDetectorThresholdsValue.text = snapshot.visualizerProbe.detectorThresholds
            visualizerDetectorAttackReleaseValue.text = snapshot.visualizerProbe.detectorAttackRelease
            visualizerDetectorHysteresisValue.text = snapshot.visualizerProbe.detectorHysteresisState
            visualizerDetectorTransitionsValue.text =
                "count=${snapshot.visualizerProbe.detectorTransitionCount} last=${snapshot.visualizerProbe.detectorLastTransitionElapsedMs?.let { "$it ms" } ?: "n/a"}"
            visualizerQuietSummaryValue.text = compactVisualizerSummary(snapshot.visualizerProbe.quietSummary)
            visualizerUserSummaryValue.text = compactVisualizerSummary(snapshot.visualizerProbe.userSummary)
            visualizerAiSummaryValue.text = compactVisualizerSummary(snapshot.visualizerProbe.aiSummary)
            visualizerAiQuietRatioValue.text =
                "rms=${"%.2f".format(snapshot.visualizerProbe.aiQuietRmsRatio)} peak=${"%.2f".format(snapshot.visualizerProbe.aiQuietPeakRatio)}"
            visualizerAiUserRatioValue.text =
                "rms=${"%.2f".format(snapshot.visualizerProbe.aiUserRmsRatio)} peak=${"%.2f".format(snapshot.visualizerProbe.aiUserPeakRatio)}"
            visualizerHistoryValue.text = snapshot.visualizerProbe.history.takeLast(100).joinToString("\n") { sample ->
                "${sample.elapsedTimestampMs} | ${sample.phase} | ${compactVisualizerMetrics(sample.metrics)}"
            }.ifBlank { "n/a" }
            accessibilityEnabledValue.text = accessibilityEnabledLabel(snapshot.accessibilityProbe.enabled)
            accessibilityObservedPackageValue.text = snapshot.accessibilityProbe.observedPackage
            accessibilityEventCountValue.text = snapshot.accessibilityProbe.eventCount.toString()
            accessibilityRootNodeAvailableValue.text = snapshot.accessibilityProbe.rootNodeAvailable
            accessibilityCandidateNodesValue.text = snapshot.accessibilityProbe.voiceUiCandidateNodes.toString()
            accessibilityUiSignatureValue.text = snapshot.accessibilityProbe.uiSignature
            accessibilityUiSignatureChangedValue.text = snapshot.accessibilityProbe.uiSignatureChanged
            accessibilityLastUiChangeValue.text = snapshot.accessibilityProbe.lastUiChangeElapsedMs?.let { "$it ms" } ?: "n/a"
            accessibilityCandidateStateValue.text = snapshot.accessibilityProbe.candidateState
            centerCandidatePresentValue.text = snapshot.accessibilityProbe.centerCandidatePresent
            centerCandidateBoundsValue.text = snapshot.accessibilityProbe.centerCandidateBounds
            centerChildCountValue.text = snapshot.accessibilityProbe.centerChildCount.toString()
            centerChangeRate1sValue.text = "%.1f/s".format(snapshot.accessibilityProbe.centerChangeRate1s)
            centerChangeRate3sValue.text = "%.1f/s".format(snapshot.accessibilityProbe.centerChangeRate3s)
            currentTestPhaseValue.text = snapshot.accessibilityProbe.currentTestPhase
            quietAverageRateValue.text = "%.1f/s".format(snapshot.accessibilityProbe.quietAverageRate)
            userAverageRateValue.text = "%.1f/s".format(snapshot.accessibilityProbe.userAverageRate)
            aiAverageRateValue.text = "%.1f/s".format(snapshot.accessibilityProbe.aiAverageRate)
            centerProbeSampleCountValue.text = snapshot.accessibilityProbe.centerProbeSampleCount.toString()
            centerHistoryValue.text = snapshot.accessibilityProbe.centerHistory.takeLast(200).joinToString("\n") { sample ->
                "${sample.elapsedTimestampMs} | ${sample.phase} | present=${sample.present} | " +
                    "child=${sample.childCount} | Δchild=${if (sample.childCountChanged) 1 else 0} | " +
                    "Δbounds=${if (sample.boundsChanged) 1 else 0} | " +
                    "rate1=${"%.1f".format(sample.changeRate1s)} | rate3=${"%.1f".format(sample.changeRate3s)}"
            }.ifBlank { "n/a" }
            lastValidChatGptSignatureValue.text = snapshot.accessibilityProbe.lastValidChatGptSignature
            validSignatureEventCountValue.text = snapshot.accessibilityProbe.validSignatureEventCount.toString()
            signatureTransitionCountValue.text = snapshot.accessibilityProbe.signatureTransitionCount.toString()
            ignoredEmptyEventsValue.text = snapshot.accessibilityProbe.ignoredEmptyEvents.toString()
            duplicateSignatureEventsValue.text = snapshot.accessibilityProbe.duplicateSignatureEvents.toString()
            trackedAccessibilityNodesValue.text = snapshot.accessibilityProbe.trackedAccessibilityNodes.toString()
            dynamicCandidateCountValue.text = snapshot.accessibilityProbe.dynamicCandidateCount.toString()
            topDynamicCandidateNodesValue.text = snapshot.accessibilityProbe.topDynamicCandidateNodes.mapIndexed { index, candidate ->
                "#${index + 1} id=${candidate.stableId} class=${candidate.className.shortClass()} " +
                    "bounds=[${candidate.boundsInScreen}] region=${candidate.regionHint} " +
                    "rate=${"%.1f".format(candidate.recentChangeRatePerSecond)}/s " +
                    "obs=${candidate.observedCount} metaΔ=${candidate.metadataChangeCount} " +
                    "childΔ=${candidate.childCountChangeCount} boundsΔ=${candidate.boundsChangeCount} " +
                    "stateΔ=${candidate.stateFlagChangeCount}"
            }.joinToString("\n").ifBlank { "n/a" }
            topCandidateSnapshotHistoryValue.text = snapshot.accessibilityProbe.topCandidateSnapshotHistory.joinToString("\n") { change ->
                "${change.elapsedTimestampMs} | ${change.candidateId} | ${change.changedFields}"
            }.ifBlank { "n/a" }
            signatureTransitionsValue.text = snapshot.accessibilityProbe.signatureTransitions.joinToString("\n") { transition ->
                "${transition.elapsedTimestampMs} | ${transition.oldSignature} -> ${transition.newSignature} | " +
                    "nodes=${transition.candidateNodeCount} | ${transition.eventType.shortEventType()}"
            }.ifBlank { "n/a" }
            lastAccessibilityEventsValue.text = snapshot.accessibilityProbe.lastEvents.takeLast(30).joinToString("\n") { event ->
                val ignored = if (event.ignored) " | ignored" else ""
                "${event.elapsedTimestampMs} | ${event.eventType.shortEventType()} | sig=${event.uiSignature} | nodes=${event.candidateNodeCount}$ignored"
            }.ifBlank { "n/a" }
        }
    }

    private fun accessibilityEnabledLabel(serviceSnapshotValue: String): String {
        return if (isAccessibilityServiceEnabled()) "ENABLED" else serviceSnapshotValue
    }

    private fun compactMetrics(metrics: VisualMotionMetrics): String {
        return "mean=${"%.3f".format(metrics.meanMotion)} " +
            "px=${"%.3f".format(metrics.changedPixelRatio)} " +
            "p95=${"%.3f".format(metrics.highMotionPercentile)} " +
            "edge=${"%.3f".format(metrics.edgeMotion)} " +
            "color=${"%.3f".format(metrics.colorMotion)}"
    }

    private fun compactSummary(summary: VisualMotionPhaseSummary): String {
        return "mean=${"%.3f".format(summary.meanAverage)} " +
            "px=${"%.3f".format(summary.changedPixelRatioAverage)} " +
            "p95=${"%.3f".format(summary.highMotionPercentileAverage)} " +
            "edge=${"%.3f".format(summary.edgeMotionAverage)} " +
            "color=${"%.3f".format(summary.colorMotionAverage)}"
    }

    private fun compactPeak(summary: VisualMotionPhaseSummary): String {
        return "filtered=${"%.3f".format(summary.filteredPeakMeanMotion)} raw=${"%.3f".format(summary.rawPeakMeanMotion)}"
    }

    private fun compactRatios(
        mean: Double,
        changedPixel: Double,
        highMotion: Double,
        edge: Double,
        color: Double
    ): String {
        return "mean=${"%.2f".format(mean)} " +
            "px=${"%.2f".format(changedPixel)} " +
            "p95=${"%.2f".format(highMotion)} " +
            "edge=${"%.2f".format(edge)} " +
            "color=${"%.2f".format(color)}"
    }

    private fun compactVisualizerMetrics(metrics: VisualizerWaveformMetrics): String {
        return "rms=${"%.3f".format(metrics.rms)} " +
            "peak=${"%.3f".format(metrics.peak)} " +
            "activity=${"%.3f".format(metrics.activityRatio)}"
    }

    private fun compactVisualizerSummary(summary: VisualizerPhaseSummary): String {
        return "rmsAvg=${"%.3f".format(summary.rmsAverage)} " +
            "rmsPeak=${"%.3f".format(summary.rmsPeak)} " +
            "peakAvg=${"%.3f".format(summary.peakAverage)} " +
            "peakPeak=${"%.3f".format(summary.peakPeak)} " +
            "activityAvg=${"%.3f".format(summary.activityAverage)}"
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

    private fun String.shortClass() = replace("android.widget.", "")
        .replace("android.view.", "")
}
