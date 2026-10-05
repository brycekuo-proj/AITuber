package com.aituber.poc.claudechat

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.aituber.poc.character.CharacterEngine
import com.aituber.poc.character.live2d.Live2DCharacterAdapter
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DOverlayPresence
import com.aituber.poc.character.live2d.Live2DOverlayView
import com.aituber.poc.character.live2d.Live2DProfileStore
import com.aituber.poc.overlay.MouthAmplitudeMapper
import com.aituber.poc.overlay.MouthDriveMode
import com.aituber.poc.overlay.MouthSilenceGate
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import com.aituber.poc.state.VisualizerWaveformMetrics
import kotlin.math.abs
import kotlin.math.sin

/**
 * Talk to Claude directly inside AITuber: the selected character listens,
 * thinks and answers out loud with its mouth following the voice.
 *
 * Uses the user's own Claude API key (Anthropic Console). If the floating
 * character is on screen, that one talks instead of a second copy here,
 * because only one Live2D view can render at a time.
 */
class ClaudeChatActivity : Activity(), ClaudeChatSession.Listener {

    private lateinit var profile: Live2DCharacterProfile
    private lateinit var session: ClaudeChatSession

    private lateinit var stage: FrameLayout
    private lateinit var stageNotice: TextView
    private lateinit var statusLine: TextView
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var micButton: Button

    private var characterView: Live2DOverlayView? = null
    private var engine: CharacterEngine? = null
    private val silenceGate = MouthSilenceGate()
    private val mouthMapper = MouthAmplitudeMapper()
    private var replyBubble: TextView? = null
    private var resumed = false
    private var lastPublishMs = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val overlayPresenceListener: (Boolean) -> Unit = { active ->
        // The floating character closed: give this screen its own character back.
        if (!active) handler.postDelayed({ if (!isDestroyed && characterView == null) attachCharacter() }, 450L)
    }
    private val frameLoop = object : Runnable {
        override fun run() {
            renderFrame()
            if (resumed) handler.postDelayed(this, FRAME_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        profile = intent.getStringExtra(EXTRA_PROFILE_ID)?.let { Live2DCharacterProfiles.byId(it) }
            ?: Live2DProfileStore.load(this)
        session = ClaudeChatSession(this, profile.displayName, this)
        setContentView(buildLayout())
        attachCharacter()
        Live2DOverlayPresence.addListener(overlayPresenceListener)
        updateStatus(UniversalAiState.IDLE)
        if (!ClaudeChatSettings.hasApiKey(this)) {
            addNote("第一次使用：請先設定你自己的 Claude API key（右上「API key」）。")
            showApiKeyDialog()
        } else {
            addNote("跟 ${profile.displayName} 說點什麼吧！可以打字，也可以按 🎤 用說的。")
        }
    }

    override fun onStart() {
        super.onStart()
        session.setForeground(true)
    }

    override fun onStop() {
        // In the background the chat stops talking and hands the floating character back
        // to the AI-app detectors.
        session.interrupt()
        session.setForeground(false)
        refreshSendButton()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        characterView?.onResume()
        handler.removeCallbacks(frameLoop)
        handler.post(frameLoop)
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(frameLoop)
        characterView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Live2DOverlayPresence.removeListener(overlayPresenceListener)
        session.release()
        val view = characterView
        characterView = null
        engine = null
        view?.release { stage.removeView(view) }
        super.onDestroy()
    }

    // ---- Character ---------------------------------------------------------

    private fun attachCharacter() {
        if (Live2DOverlayPresence.active) {
            // The floating character owns the Live2D runtime; it follows this chat instead.
            stageNotice.text = "${profile.displayName} 正在浮動視窗中，\n會跟著這裡的對話說話。"
            stageNotice.visibility = View.VISIBLE
            return
        }
        stageNotice.visibility = View.GONE
        val view = Live2DOverlayView(this, profile = profile, onRuntimeFailure = { error ->
            runOnUiThread {
                stageNotice.text = "角色載入失敗：$error"
                stageNotice.visibility = View.VISIBLE
            }
        })
        characterView = view
        engine = CharacterEngine(Live2DCharacterAdapter(profile = profile, nativeView = view))
        stage.addView(view, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        if (resumed) view.onResume()
        if (profile.capabilities.idleMotion) {
            view.postDelayed({ if (characterView === view) view.startIdleMotionForDebug() }, 350L)
        }
    }

    private fun renderFrame() {
        val now = SystemClock.elapsedRealtime()
        val state = session.state
        val signal = session.mouthSignal()
        // Without a loudness measure, speak with a gentle generic rhythm.
        val metrics = if (signal.available) signal.metrics else syntheticMetrics(state, now)
        val gate = silenceGate.evaluate(state, metrics, visualizerAvailable = true, nowMs = now)
        val frame = mouthMapper.evaluate(state, metrics, mouthActive = gate.mouthActive, visualizerAvailable = true, nowMs = now)
        val open = if (frame.mode == MouthDriveMode.RMS && frame.shouldRender) frame.smoothedOpen.toFloat() else 0f
        engine?.bind(snapshotFor(state), open)

        if (state == UniversalAiState.SPEAKING && now - lastPublishMs >= PUBLISH_MS) {
            lastPublishMs = now
            session.publish()
        }
    }

    private fun syntheticMetrics(state: UniversalAiState, now: Long): VisualizerWaveformMetrics {
        if (state != UniversalAiState.SPEAKING) return VisualizerWaveformMetrics.zero()
        val t = now / 1000.0
        val level = 0.18 + 0.16 * abs(sin(t * 7.1)) + 0.06 * abs(sin(t * 13.3))
        return VisualizerWaveformMetrics(rms = level, peak = (level * 2.4).coerceAtMost(1.0), activityRatio = 0.6)
    }

    private fun snapshotFor(state: UniversalAiState) = UniversalStateSnapshot(
        targetApp = "Claude",
        detectionMethod = "AITuber Claude chat",
        state = state,
        audioLevel = null,
        captureStatus = "Claude chat"
    )

    // ---- Chat callbacks ----------------------------------------------------

    override fun onUserMessage(text: String) {
        addBubble(text, fromUser = true)
        replyBubble = null
    }

    override fun onReplyProgress(text: String) {
        val bubble = replyBubble ?: addBubble("", fromUser = false).also { replyBubble = it }
        bubble.text = text
        scrollToEnd()
    }

    override fun onReplyDone(text: String) {
        if (replyBubble == null && text.isNotBlank()) addBubble(text, fromUser = false)
        replyBubble = null
        refreshSendButton()
    }

    override fun onError(error: ClaudeChatError) {
        addNote(error.userMessage)
        if (error === ClaudeChatError.NoApiKey || error === ClaudeChatError.InvalidKey) showApiKeyDialog()
        refreshSendButton()
    }

    override fun onStateChanged(state: UniversalAiState) {
        updateStatus(state)
        refreshSendButton()
    }

    private fun updateStatus(state: UniversalAiState) {
        val model = ClaudeChatSettings.model(this).label
        val doing = when (state) {
            UniversalAiState.THINKING -> "思考中…"
            UniversalAiState.SPEAKING -> "說話中"
            UniversalAiState.LISTENING -> "在聽你說"
            else -> "等你說話"
        }
        statusLine.text = "${profile.displayName} · $doing · Claude $model"
    }

    // ---- Input -------------------------------------------------------------

    private fun sendTyped() {
        if (session.busy) {
            session.interrupt()
            refreshSendButton()
            return
        }
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        session.send(text)
        refreshSendButton()
    }

    private fun refreshSendButton() {
        sendButton.text = if (session.busy) "停止" else "送出"
    }

    private fun startVoiceInput() {
        if (session.busy) session.interrupt()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "跟 ${profile.displayName} 說話")
        }
        try {
            session.setListening(true)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_SPEECH)
        } catch (_: ActivityNotFoundException) {
            session.setListening(false)
            Toast.makeText(this, "這支手機沒有語音輸入功能", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SPEECH) return
        session.setListening(false)
        val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (resultCode == RESULT_OK && !heard.isNullOrBlank()) session.send(heard)
        refreshSendButton()
    }

    // ---- API key -----------------------------------------------------------

    private fun showApiKeyDialog() {
        val padding = dp(20)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(8), padding, 0)
        }
        val help = TextView(this).apply {
            text = "AITuber 用你自己的 Claude API key 和 Claude 對話，費用由你的 Anthropic 帳戶計算。\n" +
                "到 console.anthropic.com → API Keys 建立一把，貼在下面。\n" +
                "Key 會加密存在這支手機，只會傳給 api.anthropic.com。"
            textSize = 14f
            movementMethod = LinkMovementMethod.getInstance()
        }
        val field = EditText(this).apply {
            hint = "sk-ant-…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        box.addView(help)
        box.addView(field)
        val builder = AlertDialog.Builder(this)
            .setTitle("Claude API key")
            .setView(box)
            .setPositiveButton("儲存", null)
            .setNeutralButton("開啟 Console") { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CONSOLE_URL))) }
            }
            .setNegativeButton("取消", null)
        if (ClaudeChatSettings.hasApiKey(this)) {
            builder.setNeutralButton("刪除已存的 key") { _, _ ->
                ClaudeChatSettings.clearApiKey(this)
                addNote("已刪除 API key。")
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = field.text.toString().trim()
                if (!ClaudeChatSettings.looksLikeApiKey(key)) {
                    field.error = "看起來不是 Claude API key（應該以 sk-ant- 開頭）"
                    return@setOnClickListener
                }
                runCatching { ClaudeChatSettings.saveApiKey(this, key) }
                    .onSuccess {
                        addNote("API key 已儲存。跟 ${profile.displayName} 說點什麼吧！")
                        dialog.dismiss()
                    }
                    .onFailure { field.error = "無法安全儲存：${it.javaClass.simpleName}" }
            }
        }
        dialog.show()
    }

    // ---- Layout ------------------------------------------------------------

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            fitsSystemWindows = true
        }

        stage = FrameLayout(this).apply { setBackgroundColor(STAGE) }
        stageNotice = TextView(this).apply {
            setTextColor(ACCENT)
            textSize = 15f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        stage.addView(stageNotice, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(stage, LinearLayout.LayoutParams(MATCH, 0, 0.42f))

        statusLine = TextView(this).apply {
            setTextColor(ACCENT)
            textSize = 13f
            setPadding(dp(14), dp(6), dp(14), dp(2))
        }
        root.addView(statusLine, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(buildControls(), LinearLayout.LayoutParams(MATCH, WRAP))

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        scroll = ScrollView(this).apply { addView(messages) }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 0.58f))

        root.addView(buildInputRow(), LinearLayout.LayoutParams(MATCH, WRAP))
        return root
    }

    private fun buildControls(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        val models = ClaudeModels.all
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, models.map { it.label })
            setSelection(models.indexOf(ClaudeChatSettings.model(context)).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    ClaudeChatSettings.setModel(context, models[position])
                    updateStatus(session.state)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        row.addView(spinner, LinearLayout.LayoutParams(0, WRAP, 1f))

        val speak = CheckBox(this).apply {
            text = "朗讀"
            setTextColor(TEXT)
            isChecked = ClaudeChatSettings.speakReplies(context)
            setOnCheckedChangeListener { _, checked ->
                ClaudeChatSettings.setSpeakReplies(context, checked)
                if (!checked) session.stopVoice()
            }
        }
        row.addView(speak)
        row.addView(smallButton("API key") { showApiKeyDialog() })
        row.addView(smallButton("清除") {
            session.clearHistory()
            messages.removeAllViews()
            addNote("已清除對話。")
        })
        return row
    }

    private fun buildInputRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }
        input = EditText(this).apply {
            hint = "輸入訊息…"
            setHintTextColor(Color.argb(140, 236, 207, 177))
            setTextColor(TEXT)
            // Single-line input type so the keyboard shows "Send"; long text still wraps.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            setHorizontallyScrolling(false)
            maxLines = 4
            setOnEditorActionListener { _, actionId, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                    sendTyped()
                    true
                } else {
                    false
                }
            }
        }
        row.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))
        micButton = smallButton("🎤") { startVoiceInput() }
        row.addView(micButton)
        sendButton = smallButton("送出") { sendTyped() }
        row.addView(sendButton)
        return row
    }

    private fun addBubble(text: String, fromUser: Boolean): TextView {
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(if (fromUser) Color.rgb(32, 24, 40) else TEXT)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (fromUser) ACCENT else BUBBLE)
            }
            setTextIsSelectable(true)
        }
        val params = LinearLayout.LayoutParams(WRAP, WRAP).apply {
            topMargin = dp(6)
            gravity = if (fromUser) Gravity.END else Gravity.START
            if (fromUser) leftMargin = dp(48) else rightMargin = dp(48)
        }
        messages.addView(bubble, params)
        scrollToEnd()
        return bubble
    }

    private fun addNote(text: String) {
        val note = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(typeface, Typeface.ITALIC)
            setTextColor(Color.argb(190, 236, 207, 177))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        messages.addView(note, LinearLayout.LayoutParams(MATCH, WRAP))
        scrollToEnd()
    }

    private fun scrollToEnd() {
        // scrollTo, not fullScroll: fullScroll moves focus to the last bubble and away from the input.
        scroll.post { scroll.scrollTo(0, messages.bottom) }
    }

    private fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val REQUEST_SPEECH = 4101
        private const val FRAME_MS = 33L
        private const val PUBLISH_MS = 50L
        private const val CONSOLE_URL = "https://console.anthropic.com/settings/keys"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private val BACKGROUND = Color.rgb(29, 24, 34)
        private val STAGE = Color.rgb(40, 32, 48)
        private val BUBBLE = Color.rgb(58, 48, 70)
        private val TEXT = Color.rgb(245, 236, 228)
        private val ACCENT = Color.rgb(236, 207, 177)

        fun start(context: Context, profile: Live2DCharacterProfile) {
            context.startActivity(
                Intent(context, ClaudeChatActivity::class.java).putExtra(EXTRA_PROFILE_ID, profile.id)
            )
        }
    }
}
