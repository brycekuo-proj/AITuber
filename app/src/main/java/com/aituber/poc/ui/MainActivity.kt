package com.aituber.poc.ui

import android.Manifest
import android.app.Activity
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
import android.widget.TextView
import com.aituber.poc.aiadapter.AiAdapter
import com.aituber.poc.aiadapter.CaptureStatus
import com.aituber.poc.aiadapter.PlaybackCaptureAiAdapter
import com.aituber.poc.aiadapter.UnavailableAiAdapter
import com.aituber.poc.character.CharacterEngine
import com.aituber.poc.character.DebugCharacterAdapter
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class MainActivity : Activity() {
    private val projectionRequestCode = 1001
    private val recordAudioRequestCode = 1002

    private lateinit var targetAppValue: TextView
    private lateinit var detectionMethodValue: TextView
    private lateinit var stateValue: TextView
    private lateinit var audioLevelValue: TextView
    private lateinit var captureStatusValue: TextView
    private lateinit var levelBar: ProgressBar
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var fallbackButton: Button
    private lateinit var overlayButton: Button

    private var adapter: AiAdapter? = null
    private lateinit var characterEngine: CharacterEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        characterEngine = CharacterEngine(DebugCharacterAdapter(::renderSnapshot))
        setContentView(buildUi())
        renderSnapshot(
            UniversalStateSnapshot(
                targetApp = "Unknown",
                detectionMethod = "Not selected",
                state = UniversalAiState.UNKNOWN,
                audioLevel = null,
                captureStatus = CaptureStatus.NOT_STARTED
            )
        )
    }

    override fun onDestroy() {
        adapter?.stop()
        super.onDestroy()
    }

    @Deprecated("Used for the minimal PoC Activity result flow.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != projectionRequestCode) return

        if (resultCode == RESULT_OK && data != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data)
            adapter?.stop()
            adapter = PlaybackCaptureAiAdapter(this, projection)
            adapter?.start { snapshot -> characterEngine.bind(snapshot) }
        } else {
            useUnavailableAdapter("MediaProjection permission was not granted")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == recordAudioRequestCode && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestPlaybackCapture()
        } else {
            useUnavailableAdapter(CaptureStatus.RECORD_AUDIO_DENIED)
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
        audioLevelValue = addField(root, "Audio Level")
        captureStatusValue = addField(root, "Capture Status")

        levelBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        root.addView(levelBar)

        startButton = Button(this).apply {
            text = "Start Playback Capture"
            setOnClickListener { startDetection() }
        }
        stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener {
                adapter?.stop()
                renderSnapshot(UniversalStateSnapshot("Unknown", "Stopped", UniversalAiState.UNKNOWN, null, CaptureStatus.STOPPED))
            }
        }
        fallbackButton = Button(this).apply {
            text = "Use Manual Fallback"
            setOnClickListener {
                renderSnapshot(UniversalStateSnapshot("Manual", "Manual fallback", UniversalAiState.UNKNOWN, null, "Fallback selected: show UNKNOWN until a safe signal is available"))
            }
        }
        overlayButton = Button(this).apply {
            text = "Open Overlay Permission"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }

        listOf(startButton, stopButton, fallbackButton, overlayButton).forEach { button ->
            root.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14 })
        }

        return root
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

    private fun startDetection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            useUnavailableAdapter("Android Playback Capture requires Android 10 / API 29 or later")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), recordAudioRequestCode)
            return
        }
        requestPlaybackCapture()
    }

    private fun requestPlaybackCapture() {
        renderSnapshot(
            UniversalStateSnapshot(
                "System playback",
                "Android Playback Capture",
                UniversalAiState.UNKNOWN,
                null,
                CaptureStatus.WAITING_FOR_PERMISSION
            )
        )
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequestCode)
    }

    private fun useUnavailableAdapter(reason: String) {
        adapter?.stop()
        adapter = UnavailableAiAdapter(reason)
        adapter?.start { snapshot -> characterEngine.bind(snapshot) }
    }

    private fun renderSnapshot(snapshot: UniversalStateSnapshot) {
        runOnUiThread {
            targetAppValue.text = snapshot.targetApp
            detectionMethodValue.text = snapshot.detectionMethod
            stateValue.text = snapshot.state.name
            audioLevelValue.text = snapshot.audioLevel?.let { "%.3f".format(it) } ?: "n/a"
            captureStatusValue.text = snapshot.captureStatus
            levelBar.progress = ((snapshot.audioLevel ?: 0f) * 100).toInt().coerceIn(0, 100)
        }
    }
}
