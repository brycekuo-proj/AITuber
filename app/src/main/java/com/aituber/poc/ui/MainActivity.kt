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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aituber.poc.aiadapter.CaptureStatus
import com.aituber.poc.character.CharacterDiagnostics
import com.aituber.poc.character.CharacterMode
import com.aituber.poc.overlay.CharacterOverlayService
import com.aituber.poc.overlay.MouthDriveDiagnostics
import com.aituber.poc.overlay.MouthRenderDiagnostics
import com.aituber.poc.overlay.OverlayLifecycleTrace
import com.aituber.poc.poc.AndroidPlaybackStateProbe
import com.aituber.poc.poc.CaptureSessionService
import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.poc.CaptureStartupTrace
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
    private lateinit var derivedSpeakingCoreValue: TextView
    private lateinit var mouthOverlayStateValue: TextView
    private lateinit var mouthDriveModeValue: TextView
    private lateinit var mouthTargetOpenValue: TextView
    private lateinit var mouthSmoothedOpenValue: TextView

    private lateinit var diagnosticsContainer: LinearLayout
    private lateinit var diagnosticsToggleButton: Button
    private lateinit var timingVisualizerDerivedStateValue: TextView
    private lateinit var timingVisualizerDerivedLastChangeValue: TextView
    private lateinit var timingVisualizerLastSpeakingValue: TextView
    private lateinit var timingResolvedUniversalStateValue: TextView
    private lateinit var timingUniversalLastChangeValue: TextView
    private lateinit var timingStateAuthorityValue: TextView
    private lateinit var timingDerivedDelayValue: TextView
    private lateinit var timingLastWriterValue: TextView
    private lateinit var timingLastSourceValue: TextView
    private lateinit var timingSpeakingHoldValue: TextView
    private lateinit var timingSpeakingHoldRemainingValue: TextView
    private lateinit var mouthAudibleValue: TextView
    private lateinit var mouthGateStateValue: TextView
    private lateinit var mouthLastAudibleTimeValue: TextView
    private lateinit var mouthSilenceDurationValue: TextView
    private lateinit var mouthSilenceHoldRemainingValue: TextView
    private lateinit var mouthCloseModeValue: TextView
    private lateinit var mouthActiveCloseTimeConstantValue: TextView
    private lateinit var silenceCloseStartTimeValue: TextView
    private lateinit var silenceCloseDurationValue: TextView
    private lateinit var mouthClosedSnapThresholdValue: TextView
    private lateinit var mouthClosedSnapCountValue: TextView
    private lateinit var lastClosedSnapTimeValue: TextView
    private lateinit var mouthRmsNormalizedValue: TextView
    private lateinit var mouthPeakNormalizedValue: TextView
    private lateinit var mouthLoudnessBoostedValue: TextView
    private lateinit var mouthLoudnessContrastValue: TextView
    private lateinit var mouthLoudnessAccelerationValue: TextView
    private lateinit var mouthLoudnessBandValue: TextView
    private lateinit var mouthGateRmsValue: TextView
    private lateinit var mouthGatePeakValue: TextView
    private lateinit var mouthGateActivityValue: TextView
    private lateinit var captureStartupTraceValue: TextView
    private lateinit var mouthPipelineDriveModeValue: TextView
    private lateinit var mouthPipelineMapperTargetValue: TextView
    private lateinit var mouthPipelineSmoothedOpenValue: TextView
    private lateinit var mouthPipelineOverlayStateValue: TextView
    private lateinit var mouthPipelineOverlayRmsValue: TextView
    private lateinit var mouthPipelineOverlayPeakValue: TextView
    private lateinit var mouthPipelineCharacterRenderCountValue: TextView
    private lateinit var mouthPipelineAdapterRenderCountValue: TextView
    private lateinit var mouthPipelineAdapterRatioValue: TextView
    private lateinit var mouthPipelineViewSetCountValue: TextView
    private lateinit var mouthPipelineViewRequestedRatioValue: TextView
    private lateinit var mouthPipelineViewDrawCountValue: TextView
    private lateinit var mouthPipelineViewDrawnRatioValue: TextView
    private lateinit var mouthPipelineViewSizeValue: TextView
    private lateinit var mouthPipelineCalculatedHeightValue: TextView
    private lateinit var mouthPipelineLastRenderTimeValue: TextView
    private lateinit var mouthPipelineLastDrawTimeValue: TextView
    private lateinit var mouthPipelineRenderThreadValue: TextView
    private lateinit var mouthPipelineDrawThreadValue: TextView
    private lateinit var characterModeValue: TextView
    private lateinit var activeCharacterAdapterValue: TextView
    private lateinit var characterFrameCountValue: TextView
    private lateinit var characterMouthInputValue: TextView
    private lateinit var characterMouthOutputValue: TextView
    private lateinit var live2dAvailableValue: TextView
    private lateinit var live2dRuntimeLoadedValue: TextView
    private lateinit var live2dCoreLoadedValue: TextView
    private lateinit var live2dModelLoadedValue: TextView
    private lateinit var live2dModelNameValue: TextView
    private lateinit var live2dMouthParameterIdValue: TextView
    private lateinit var live2dInputMouthOpenValue: TextView
    private lateinit var live2dMouthParameterValue: TextView
    private lateinit var live2dMouthParameterStatusValue: TextView
    private lateinit var live2dRenderFpsValue: TextView
    private lateinit var live2dNativeFrameCountValue: TextView
    private lateinit var live2dSurfaceSizeValue: TextView
    private lateinit var live2dDisplayScaleValue: TextView
    private lateinit var live2dDisplayOffsetXValue: TextView
    private lateinit var live2dDisplayOffsetYValue: TextView
    private lateinit var live2dViewportSizeValue: TextView
    private lateinit var live2dBottomSafeZonePercentValue: TextView
    private lateinit var live2dBottomSafeZonePxValue: TextView
    private lateinit var live2dTextureCountValue: TextView
    private lateinit var live2dTexturesLoadedValue: TextView
    private lateinit var live2dLastTexturePathValue: TextView
    private lateinit var live2dLastTextureErrorValue: TextView
    private lateinit var live2dGlTextureIdsValue: TextView
    private lateinit var live2dPoseFileValue: TextView
    private lateinit var live2dPoseLoadedValue: TextView
    private lateinit var live2dPoseActiveValue: TextView
    private lateinit var live2dLifecycleStateValue: TextView
    private lateinit var live2dFallbackReasonValue: TextView
    private lateinit var live2dLastErrorValue: TextView
    private lateinit var startButtonClickCountValue: TextView
    private lateinit var projectionRequestCountValue: TextView
    private lateinit var projectionResultOkCountValue: TextView
    private lateinit var captureServiceStartRequestCountValue: TextView
    private lateinit var serviceOnCreateCountValue: TextView
    private lateinit var serviceOnStartCommandCountValue: TextView
    private lateinit var startCaptureCountValue: TextView
    private lateinit var captureServiceAliveValue: TextView
    private lateinit var overlayServiceAliveValue: TextView
    private lateinit var overlayLifecycleTraceValue: TextView
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
    private lateinit var visualizerStartupTraceValue: TextView
    private lateinit var visualizerStartRequestCountValue: TextView
    private lateinit var visualizerStartInternalCountValue: TextView
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
    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiRefreshScheduler = UiRefreshScheduler(
        postToUiDelayed = { block, delayMs -> uiHandler.postDelayed({ block() }, delayMs) },
        nowMs = { SystemClock.elapsedRealtime() },
        refreshIntervalMs = 150L,
        render = ::renderSnapshotOnUi
    )
    private val stateListener: (UniversalStateSnapshot) -> Unit = { snapshot ->
        uiRefreshScheduler.submit(snapshot)
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
        CaptureSessionState.subscribe(stateListener)
        playbackProbe?.start()
    }

    override fun onStop() {
        CaptureSessionState.unsubscribe(stateListener)
        super.onStop()
    }

    override fun onDestroy() {
        uiRefreshScheduler.destroy()
        uiHandler.removeCallbacksAndMessages(null)
        playbackProbe?.stop()
        super.onDestroy()
    }

    @Deprecated("Used for the minimal PoC Activity result flow.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            projectionRequestCode -> {
                if (resultCode == RESULT_OK && data != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    CaptureStartupTrace.projectionResultOk()
                    startCaptureService(resultCode, data)
                } else {
                    CaptureStartupTrace.record("MediaProjection denied")
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
                    CaptureStartupTrace.recordAudioPermissionCheck(granted = true)
                    requestPlaybackCapture()
                } else {
                    CaptureStartupTrace.recordAudioPermissionCheck(granted = false)
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
        visualizerSignalValue = addCoreField(root, "Visualizer Signal")
        visualizerRmsCoreValue = addCoreField(root, "RMS")
        visualizerPeakCoreValue = addCoreField(root, "Peak")
        derivedSpeakingCoreValue = addCoreField(root, "Derived Speaking")
        mouthOverlayStateValue = addCoreField(root, "Mouth Overlay")
        mouthDriveModeValue = addCoreField(root, "Mouth Drive Mode")
        mouthTargetOpenValue = addCoreField(root, "Mouth Target Open")
        mouthSmoothedOpenValue = addCoreField(root, "Mouth Smoothed Open")

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
        addButton(root, primaryControlLabels[0]) { startDetection() }
        addButton(root, primaryControlLabels[1]) { stopCaptureService() }
        addButton(root, primaryControlLabels[2]) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
        addButton(root, primaryControlLabels[3]) { enableMouthOverlay() }
        addButton(root, primaryControlLabels[4]) { disableMouthOverlay() }
        addButton(root, primaryControlLabels[5]) { setCharacterMode(CharacterMode.MINIMAL_MOUTH) }
        addButton(root, primaryControlLabels[6]) { setCharacterMode(CharacterMode.LIVE2D) }

        diagnosticsToggleButton = Button(this).apply {
            text = "SHOW DIAGNOSTICS"
            setOnClickListener { toggleDiagnostics() }
        }
        root.addView(diagnosticsToggleButton, buttonLayoutParams())
    }

    private fun addButton(root: LinearLayout, label: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
        }, buttonLayoutParams())
    }

    private fun addDiagnosticsFields(root: LinearLayout) {
        root.addView(sectionTitle("State Timing / Authority"))
        timingVisualizerDerivedStateValue = addDiagnosticField(root, "Visualizer Derived State")
        timingVisualizerDerivedLastChangeValue = addDiagnosticField(root, "Visualizer Derived Last Change")
        timingVisualizerLastSpeakingValue = addDiagnosticField(root, "Visualizer Last Speaking Time")
        timingResolvedUniversalStateValue = addDiagnosticField(root, "Resolved Universal State")
        timingUniversalLastChangeValue = addDiagnosticField(root, "Universal State Last Change")
        timingStateAuthorityValue = addDiagnosticField(root, "State Authority")
        timingDerivedDelayValue = addDiagnosticField(root, "Derived -> Universal Delay")
        timingLastWriterValue = addDiagnosticField(root, "Last State Writer")
        timingLastSourceValue = addDiagnosticField(root, "Last State Source")
        timingSpeakingHoldValue = addDiagnosticField(root, "Speaking Hold ms")
        timingSpeakingHoldRemainingValue = addDiagnosticField(root, "Speaking Hold Remaining")

        root.addView(sectionTitle("Mouth Silence Gate"))
        mouthAudibleValue = addDiagnosticField(root, "Mouth Audible")
        mouthGateStateValue = addDiagnosticField(root, "Mouth Gate State")
        mouthLastAudibleTimeValue = addDiagnosticField(root, "Mouth Last Audible Time")
        mouthSilenceDurationValue = addDiagnosticField(root, "Mouth Silence Duration")
        mouthSilenceHoldRemainingValue = addDiagnosticField(root, "Mouth Silence Hold Remaining")
        mouthCloseModeValue = addDiagnosticField(root, "Mouth Close Mode")
        mouthActiveCloseTimeConstantValue = addDiagnosticField(root, "Mouth Active Close Time Constant")
        silenceCloseStartTimeValue = addDiagnosticField(root, "Silence Close Start Time")
        silenceCloseDurationValue = addDiagnosticField(root, "Silence Close Duration")
        mouthClosedSnapThresholdValue = addDiagnosticField(root, "Mouth Closed Snap Threshold")
        mouthClosedSnapCountValue = addDiagnosticField(root, "Mouth Closed Snap Count")
        lastClosedSnapTimeValue = addDiagnosticField(root, "Last Closed Snap Time")
        mouthRmsNormalizedValue = addDiagnosticField(root, "RMS Normalized")
        mouthPeakNormalizedValue = addDiagnosticField(root, "Peak Normalized")
        mouthLoudnessBoostedValue = addDiagnosticField(root, "Loudness Boosted")
        mouthLoudnessContrastValue = addDiagnosticField(root, "Loudness Contrast")
        mouthLoudnessAccelerationValue = addDiagnosticField(root, "Loudness Acceleration")
        mouthLoudnessBandValue = addDiagnosticField(root, "Mouth Loudness Band")
        mouthGateRmsValue = addDiagnosticField(root, "Mouth Gate RMS")
        mouthGatePeakValue = addDiagnosticField(root, "Mouth Gate Peak")
        mouthGateActivityValue = addDiagnosticField(root, "Mouth Gate Activity Ratio")

        root.addView(sectionTitle("Mouth Render Pipeline"))
        addButton(root, "TEST MOUTH 100%") { CharacterOverlayService.testMouthFullyOpenForDebug() }
        mouthPipelineDriveModeValue = addDiagnosticField(root, "Drive Mode")
        mouthPipelineMapperTargetValue = addDiagnosticField(root, "Mapper Target")
        mouthPipelineSmoothedOpenValue = addDiagnosticField(root, "Smoothed Open")
        mouthPipelineOverlayStateValue = addDiagnosticField(root, "Overlay State")
        mouthPipelineOverlayRmsValue = addDiagnosticField(root, "Overlay RMS")
        mouthPipelineOverlayPeakValue = addDiagnosticField(root, "Overlay Peak")
        mouthPipelineCharacterRenderCountValue = addDiagnosticField(root, "Character Render Count")
        mouthPipelineAdapterRenderCountValue = addDiagnosticField(root, "Adapter Render Count")
        mouthPipelineAdapterRatioValue = addDiagnosticField(root, "Adapter Ratio")
        mouthPipelineViewSetCountValue = addDiagnosticField(root, "View Set Ratio Count")
        mouthPipelineViewRequestedRatioValue = addDiagnosticField(root, "View Requested Ratio")
        mouthPipelineViewDrawCountValue = addDiagnosticField(root, "View Draw Count")
        mouthPipelineViewDrawnRatioValue = addDiagnosticField(root, "View Drawn Ratio")
        mouthPipelineViewSizeValue = addDiagnosticField(root, "View Size")
        mouthPipelineCalculatedHeightValue = addDiagnosticField(root, "Calculated Mouth Height")
        mouthPipelineLastRenderTimeValue = addDiagnosticField(root, "Last Render Time")
        mouthPipelineLastDrawTimeValue = addDiagnosticField(root, "Last Draw Time")
        mouthPipelineRenderThreadValue = addDiagnosticField(root, "Render Thread")
        mouthPipelineDrawThreadValue = addDiagnosticField(root, "Draw Thread")

        root.addView(sectionTitle("Character Engine"))
        characterModeValue = addDiagnosticField(root, "Requested Character Mode")
        activeCharacterAdapterValue = addDiagnosticField(root, "Active Character Adapter")
        characterFrameCountValue = addDiagnosticField(root, "Character Frame Count")
        characterMouthInputValue = addDiagnosticField(root, "Mouth Parameter Input")
        characterMouthOutputValue = addDiagnosticField(root, "Mouth Parameter Output")
        live2dAvailableValue = addDiagnosticField(root, "Live2D Available")
        live2dRuntimeLoadedValue = addDiagnosticField(root, "Live2D Runtime Loaded")
        live2dCoreLoadedValue = addDiagnosticField(root, "Cubism Core Loaded")
        live2dModelLoadedValue = addDiagnosticField(root, "Live2D Model Loaded")
        live2dModelNameValue = addDiagnosticField(root, "Live2D Model Name")
        live2dMouthParameterIdValue = addDiagnosticField(root, "Live2D Mouth Parameter ID")
        live2dInputMouthOpenValue = addDiagnosticField(root, "Input Mouth Open")
        live2dMouthParameterValue = addDiagnosticField(root, "Live2D Mouth Parameter Value")
        live2dMouthParameterStatusValue = addDiagnosticField(root, "Live2D Mouth Parameter")
        live2dRenderFpsValue = addDiagnosticField(root, "Live2D Render FPS")
        live2dNativeFrameCountValue = addDiagnosticField(root, "Native Frame Count")
        live2dSurfaceSizeValue = addDiagnosticField(root, "Live2D Surface Size")
        live2dDisplayScaleValue = addDiagnosticField(root, "Live2D Display Scale")
        live2dDisplayOffsetXValue = addDiagnosticField(root, "Live2D Display Offset X")
        live2dDisplayOffsetYValue = addDiagnosticField(root, "Live2D Display Offset Y")
        live2dViewportSizeValue = addDiagnosticField(root, "Live2D Viewport Size")
        live2dBottomSafeZonePercentValue = addDiagnosticField(root, "Live2D Bottom Safe Zone %")
        live2dBottomSafeZonePxValue = addDiagnosticField(root, "Live2D Bottom Safe Zone px")
        live2dTextureCountValue = addDiagnosticField(root, "Live2D Texture Count")
        live2dTexturesLoadedValue = addDiagnosticField(root, "Live2D Textures Loaded")
        live2dLastTexturePathValue = addDiagnosticField(root, "Last Texture Path")
        live2dLastTextureErrorValue = addDiagnosticField(root, "Last Texture Error")
        live2dGlTextureIdsValue = addDiagnosticField(root, "GL Texture IDs")
        live2dPoseFileValue = addDiagnosticField(root, "Pose File")
        live2dPoseLoadedValue = addDiagnosticField(root, "Pose Loaded")
        live2dPoseActiveValue = addDiagnosticField(root, "Pose Active")
        live2dLifecycleStateValue = addDiagnosticField(root, "Live2D Lifecycle State")
        live2dFallbackReasonValue = addDiagnosticField(root, "Live2D Fallback Reason")
        live2dLastErrorValue = addDiagnosticField(root, "Last Live2D Error")

        root.addView(sectionTitle("Capture Startup Trace"))
        captureStartupTraceValue = addLogField(root, "Capture Startup Trace")
        startButtonClickCountValue = addDiagnosticField(root, "Start Button Click Count")
        projectionRequestCountValue = addDiagnosticField(root, "Projection Request Count")
        projectionResultOkCountValue = addDiagnosticField(root, "Projection Result OK Count")
        captureServiceStartRequestCountValue = addDiagnosticField(root, "Capture Service Start Request Count")
        serviceOnCreateCountValue = addDiagnosticField(root, "Service onCreate Count")
        serviceOnStartCommandCountValue = addDiagnosticField(root, "Service onStartCommand Count")
        startCaptureCountValue = addDiagnosticField(root, "startCapture Count")
        captureServiceAliveValue = addDiagnosticField(root, "Capture Service Alive")
        overlayServiceAliveValue = addDiagnosticField(root, "Overlay Service Alive")
        overlayLifecycleTraceValue = addLogField(root, "Overlay Lifecycle Trace")

        root.addView(sectionTitle("Diagnostics"))
        targetAppValue = addDiagnosticField(root, "Target App")
        detectionMethodValue = addDiagnosticField(root, "Detection Method")
        captureStatusValue = addDiagnosticField(root, "Capture Status")
        playbackActiveValue = addDiagnosticField(root, "Playback Active")
        recordingActiveValue = addDiagnosticField(root, "Recording Active")
        audioSourceValue = addDiagnosticField(root, "Audio Source")
        clientSilencedValue = addDiagnosticField(root, "Client Silenced")
        combinedCandidateValue = addDiagnosticField(root, "Combined Candidate")
        confidenceValue = addDiagnosticField(root, "Confidence")
        speakingSignalValue = addDiagnosticField(root, "Speaking Signal")
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
        visualizerStartupTraceValue = addLogField(root, "Visualizer Startup Trace")
        visualizerStartRequestCountValue = addDiagnosticField(root, "Visualizer Start Request Count")
        visualizerStartInternalCountValue = addDiagnosticField(root, "Visualizer startInternal Count")
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

        root.addView(sectionTitle("Legacy Probe Controls"))
        addButton(root, legacyProbeControlLabels[0]) { requestVisualMotionProbe(automatedTest = false) }
        addButton(root, legacyProbeControlLabels[1]) { stopVisualMotionService() }
        addButton(root, legacyProbeControlLabels[2]) { requestVisualMotionProbe(automatedTest = true) }
        addButton(root, legacyProbeControlLabels[3]) { startVisualizerProbe(automatedTest = true) }
        addButton(root, legacyProbeControlLabels[4]) { startVisualizerProbe(automatedTest = false) }
        addButton(root, legacyProbeControlLabels[5]) { VisualizerAudioProbe.stop() }
        addButton(root, legacyProbeControlLabels[6]) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addButton(root, legacyProbeControlLabels[7]) { CaptureSessionState.markAccessibilityTestPhase("QUIET") }
        addButton(root, legacyProbeControlLabels[8]) { CaptureSessionState.markAccessibilityTestPhase("USER") }
        addButton(root, legacyProbeControlLabels[9]) { CaptureSessionState.markAccessibilityTestPhase("AI") }
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
        CaptureStartupTrace.startButtonClicked()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            CaptureStartupTrace.record("Android version unsupported for MediaProjection")
            publishLocal("Android Playback Capture requires Android 10 / API 29 or later")
            return
        }
        if (!ChatGptTarget.isInstalled(this)) {
            CaptureStartupTrace.record("ChatGPT UID unavailable")
            publishLocal(CaptureStatus.CHATGPT_NOT_INSTALLED)
            return
        }
        val recordAudioGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        CaptureStartupTrace.recordAudioPermissionCheck(recordAudioGranted)
        if (!recordAudioGranted) {
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
        CaptureStartupTrace.projectionRequestLaunched()
        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequestCode)
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        CaptureStartupTrace.captureServiceStartRequested()
        val intent = Intent(this, CaptureSessionService::class.java).apply {
            action = CaptureSessionService.ACTION_START
            putExtra(CaptureSessionService.EXTRA_RESULT_CODE, resultCode)
            putExtra(CaptureSessionService.EXTRA_RESULT_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (exception: RuntimeException) {
            CaptureStartupTrace.record("service start exception: ${exception::class.java.simpleName}")
            throw exception
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
        OverlayLifecycleTrace.record("overlay enable requested")
        if (!Settings.canDrawOverlays(this)) {
            OverlayLifecycleTrace.record("overlay permission requested")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            return
        }
        startService(Intent(this, CharacterOverlayService::class.java))
    }

    private fun disableMouthOverlay() {
        OverlayLifecycleTrace.record("overlay disable requested")
        stopService(Intent(this, CharacterOverlayService::class.java))
    }

    private fun setCharacterMode(mode: CharacterMode) {
        CharacterOverlayService.requestedCharacterMode = mode
        CharacterDiagnostics.recordRequestedMode(
            requestedMode = mode,
            overlayRunning = CharacterOverlayService.isRunning
        )
        OverlayLifecycleTrace.record("character mode requested ${mode.name}")
        if (CharacterOverlayService.isRunning) {
            stopService(Intent(this, CharacterOverlayService::class.java))
            uiHandler.postDelayed({ enableMouthOverlay() }, 150L)
        }
    }

    private fun renderSnapshotOnUi(snapshot: UniversalStateSnapshot) {
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
            derivedSpeakingCoreValue.text = snapshot.visualizerProbe.derivedSpeaking
            mouthOverlayStateValue.text = mouthOverlayStateLabel()
            val mouthDrive = MouthDriveDiagnostics.snapshot()
            mouthDriveModeValue.text = mouthDrive.mode
            mouthTargetOpenValue.text = mouthDrive.targetOpen?.let { "%.3f".format(it) } ?: "n/a"
            mouthSmoothedOpenValue.text = "%.3f".format(mouthDrive.smoothedOpen)
            mouthAudibleValue.text = mouthDrive.mouthAudible
            mouthGateStateValue.text = mouthDrive.mouthGateState
            mouthLastAudibleTimeValue.text = mouthDrive.mouthLastAudibleTimeMs?.let { "$it ms" } ?: "n/a"
            mouthSilenceDurationValue.text = mouthDrive.mouthSilenceDurationMs?.let { "$it ms" } ?: "n/a"
            mouthSilenceHoldRemainingValue.text = "${mouthDrive.mouthSilenceHoldRemainingMs} ms"
            mouthCloseModeValue.text = mouthDrive.mouthCloseMode
            mouthActiveCloseTimeConstantValue.text = "${mouthDrive.mouthActiveCloseTimeConstantMs} ms"
            silenceCloseStartTimeValue.text = mouthDrive.silenceCloseStartTimeMs?.let { "$it ms" } ?: "n/a"
            silenceCloseDurationValue.text = "${mouthDrive.silenceCloseDurationMs} ms"
            mouthClosedSnapThresholdValue.text = "%.3f".format(mouthDrive.mouthClosedSnapThreshold)
            mouthClosedSnapCountValue.text = mouthDrive.mouthClosedSnapCount.toString()
            lastClosedSnapTimeValue.text = mouthDrive.lastClosedSnapTimeMs?.let { "$it ms" } ?: "n/a"
            mouthRmsNormalizedValue.text = "%.3f".format(mouthDrive.mouthRmsNormalized)
            mouthPeakNormalizedValue.text = "%.3f".format(mouthDrive.mouthPeakNormalized)
            mouthLoudnessBoostedValue.text = "%.3f".format(mouthDrive.mouthLoudnessBoosted)
            mouthLoudnessContrastValue.text = "%.3f".format(mouthDrive.mouthLoudnessContrast)
            mouthLoudnessAccelerationValue.text = "%.3f".format(mouthDrive.mouthLoudnessAcceleration)
            mouthLoudnessBandValue.text = mouthDrive.mouthLoudnessBand
            mouthGateRmsValue.text = "%.3f".format(mouthDrive.mouthGateRms)
            mouthGatePeakValue.text = "%.3f".format(mouthDrive.mouthGatePeak)
            mouthGateActivityValue.text = "%.3f".format(mouthDrive.mouthGateActivityRatio)
            timingVisualizerDerivedStateValue.text = snapshot.stateTiming.visualizerDerivedState
            timingVisualizerDerivedLastChangeValue.text = snapshot.stateTiming.visualizerDerivedLastChangeTimeMs?.let { "$it ms" } ?: "n/a"
            timingVisualizerLastSpeakingValue.text = snapshot.stateTiming.visualizerLastSpeakingTimeMs?.let { "$it ms" } ?: "n/a"
            timingResolvedUniversalStateValue.text = snapshot.stateTiming.resolvedUniversalState
            timingUniversalLastChangeValue.text = snapshot.stateTiming.universalStateLastChangeTimeMs?.let { "$it ms" } ?: "n/a"
            timingStateAuthorityValue.text = snapshot.stateTiming.stateAuthority
            timingDerivedDelayValue.text = snapshot.stateTiming.derivedToUniversalDelayMs?.let { "$it ms" } ?: "n/a"
            timingLastWriterValue.text = snapshot.stateTiming.lastStateWriter
            timingLastSourceValue.text = snapshot.stateTiming.lastStateSource
            timingSpeakingHoldValue.text = "${snapshot.stateTiming.speakingHoldMs} ms"
            timingSpeakingHoldRemainingValue.text = "${snapshot.stateTiming.speakingHoldRemainingMs} ms"
            val mouthRender = MouthRenderDiagnostics.snapshot()
            mouthPipelineDriveModeValue.text = mouthRender.mouthDriveMode
            mouthPipelineMapperTargetValue.text = mouthRender.mapperTargetOpen?.let { "%.3f".format(it) } ?: "n/a"
            mouthPipelineSmoothedOpenValue.text = "%.3f".format(mouthRender.smoothedOpen)
            mouthPipelineOverlayStateValue.text = mouthRender.overlayReceivedState
            mouthPipelineOverlayRmsValue.text = "%.3f".format(mouthRender.overlayReceivedRms)
            mouthPipelineOverlayPeakValue.text = "%.3f".format(mouthRender.overlayReceivedPeak)
            mouthPipelineCharacterRenderCountValue.text = mouthRender.characterEngineRenderCount.toString()
            mouthPipelineAdapterRenderCountValue.text = mouthRender.adapterRenderCount.toString()
            mouthPipelineAdapterRatioValue.text = mouthRender.adapterLastMouthRatio?.let { "%.3f".format(it) } ?: "n/a"
            mouthPipelineViewSetCountValue.text = mouthRender.viewSetMouthOpenRatioCount.toString()
            mouthPipelineViewRequestedRatioValue.text = "%.3f".format(mouthRender.viewLastRequestedRatio)
            mouthPipelineViewDrawCountValue.text = mouthRender.viewOnDrawCount.toString()
            mouthPipelineViewDrawnRatioValue.text = "%.3f".format(mouthRender.viewLastDrawnRatio)
            mouthPipelineViewSizeValue.text = "${mouthRender.viewWidth} x ${mouthRender.viewHeight}"
            mouthPipelineCalculatedHeightValue.text = "%.3f".format(mouthRender.calculatedMouthHeight)
            mouthPipelineLastRenderTimeValue.text = mouthRender.lastRenderTimestampMs?.toString() ?: "n/a"
            mouthPipelineLastDrawTimeValue.text = mouthRender.lastDrawTimestampMs?.toString() ?: "n/a"
            mouthPipelineRenderThreadValue.text = mouthRender.renderThread
            mouthPipelineDrawThreadValue.text = mouthRender.drawThread
            val character = CharacterDiagnostics.snapshot()
            characterModeValue.text = character.characterMode
            activeCharacterAdapterValue.text = character.activeCharacterAdapter
            characterFrameCountValue.text = character.characterFrameCount.toString()
            characterMouthInputValue.text = "%.3f".format(character.mouthParameterInput)
            characterMouthOutputValue.text = "%.3f".format(character.mouthParameterOutput)
            live2dAvailableValue.text = character.live2dAvailable
            live2dRuntimeLoadedValue.text = character.live2dRuntimeLoaded
            live2dCoreLoadedValue.text = character.live2dCoreLoaded
            live2dModelLoadedValue.text = character.live2dModelLoaded
            live2dModelNameValue.text = character.live2dModelName
            live2dMouthParameterIdValue.text = character.live2dMouthParameterId
            live2dInputMouthOpenValue.text = "%.3f".format(character.live2dInputMouthOpen)
            live2dMouthParameterValue.text = character.live2dMouthParameterValue?.let { "%.3f".format(it) } ?: "n/a"
            live2dMouthParameterStatusValue.text = character.live2dMouthParameterStatus
            live2dRenderFpsValue.text = "%.1f".format(character.live2dRenderFps)
            live2dNativeFrameCountValue.text = character.live2dNativeFrameCount.toString()
            live2dSurfaceSizeValue.text = "${character.live2dSurfaceWidth} x ${character.live2dSurfaceHeight}"
            live2dDisplayScaleValue.text = "%.2f".format(character.live2dDisplayScale)
            live2dDisplayOffsetXValue.text = character.live2dDisplayOffsetX.toString()
            live2dDisplayOffsetYValue.text = character.live2dDisplayOffsetY.toString()
            live2dViewportSizeValue.text = "${character.live2dViewportWidth} x ${character.live2dViewportHeight}"
            live2dBottomSafeZonePercentValue.text = "%.1f".format(character.live2dBottomSafeZonePercent)
            live2dBottomSafeZonePxValue.text = character.live2dBottomSafeZonePx.toString()
            live2dTextureCountValue.text = character.live2dTextureCount.toString()
            live2dTexturesLoadedValue.text = character.live2dTexturesLoaded.toString()
            live2dLastTexturePathValue.text = character.live2dLastTexturePath
            live2dLastTextureErrorValue.text = character.live2dLastTextureError
            live2dGlTextureIdsValue.text = character.live2dGlTextureIds
            live2dPoseFileValue.text = character.live2dPoseFile
            live2dPoseLoadedValue.text = character.live2dPoseLoaded
            live2dPoseActiveValue.text = character.live2dPoseActive
            live2dLifecycleStateValue.text = character.live2dLifecycleState
            live2dFallbackReasonValue.text = character.live2dFallbackReason
            live2dLastErrorValue.text = character.live2dLastError

            captureStartupTraceValue.text = snapshot.captureStartupTrace.trace.joinToString("\n").ifBlank { "n/a" }
            startButtonClickCountValue.text = snapshot.captureStartupTrace.startButtonClickCount.toString()
            projectionRequestCountValue.text = snapshot.captureStartupTrace.projectionRequestCount.toString()
            projectionResultOkCountValue.text = snapshot.captureStartupTrace.projectionResultOkCount.toString()
            captureServiceStartRequestCountValue.text = snapshot.captureStartupTrace.captureServiceStartRequestCount.toString()
            serviceOnCreateCountValue.text = snapshot.captureStartupTrace.serviceOnCreateCount.toString()
            serviceOnStartCommandCountValue.text = snapshot.captureStartupTrace.serviceOnStartCommandCount.toString()
            startCaptureCountValue.text = snapshot.captureStartupTrace.startCaptureCount.toString()
            captureServiceAliveValue.text = if (CaptureSessionService.isRunning) "YES" else "NO"
            overlayServiceAliveValue.text = snapshot.overlayLifecycle.overlayServiceAlive
            overlayLifecycleTraceValue.text = snapshot.overlayLifecycle.trace.joinToString("\n").ifBlank { "n/a" }
            targetAppValue.text = snapshot.targetApp
            detectionMethodValue.text = snapshot.detectionMethod
            captureStatusValue.text = snapshot.captureStatus
            playbackActiveValue.text = activeLabel(snapshot.playbackProbe.playbackSessionActive)
            recordingActiveValue.text = activeLabel(snapshot.playbackProbe.recordingSessionActive)
            audioSourceValue.text = compactAudioSource(snapshot.playbackProbe.observedAudioSource)
            clientSilencedValue.text = snapshot.playbackProbe.clientSilenced
            combinedCandidateValue.text = snapshot.playbackProbe.combinedCandidateState
            confidenceValue.text = snapshot.playbackProbe.combinedCandidateConfidence
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
            visualizerStartupTraceValue.text = snapshot.visualizerProbe.startupTrace.joinToString("\n").ifBlank { "n/a" }
            visualizerStartRequestCountValue.text = snapshot.visualizerProbe.startRequestCount.toString()
            visualizerStartInternalCountValue.text = snapshot.visualizerProbe.startInternalCount.toString()
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

    private fun accessibilityEnabledLabel(serviceSnapshotValue: String): String {
        return if (isAccessibilityServiceEnabled()) "ENABLED" else serviceSnapshotValue
    }

    private fun mouthOverlayStateLabel(): String {
        return when {
            !Settings.canDrawOverlays(this) -> "PERMISSION REQUIRED"
            CharacterOverlayService.isRunning -> "ENABLED"
            else -> "DISABLED"
        }
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

    companion object {
        internal val primaryControlLabels = listOf(
            "START CHATGPT CAPTURE",
            "STOP",
            "OPEN OVERLAY PERMISSION",
            "ENABLE MOUTH OVERLAY",
            "DISABLE MOUTH OVERLAY",
            "USE MINIMAL MOUTH",
            "USE LIVE2D",
            "SHOW DIAGNOSTICS"
        )

        internal val legacyProbeControlLabels = listOf(
            "START VISUAL MOTION PROBE",
            "STOP VISUAL MOTION PROBE",
            "START 30S VISUAL TEST",
            "START 30S VISUALIZER TEST",
            "START VISUALIZER DETECTOR",
            "STOP VISUALIZER TEST",
            "OPEN ACCESSIBILITY SETTINGS",
            "MARK QUIET",
            "MARK USER",
            "MARK AI"
        )
    }
}
