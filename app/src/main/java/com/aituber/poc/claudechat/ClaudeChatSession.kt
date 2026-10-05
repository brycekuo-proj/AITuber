package com.aituber.poc.claudechat

import android.content.Context
import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.state.MouthSignal
import com.aituber.poc.state.UniversalAiState

/**
 * One chat with Claude: sends what the user said, streams the reply into the
 * screen, speaks it sentence by sentence, and tells the character what to do
 * (THINKING while waiting for the first words, SPEAKING while the voice plays).
 *
 * The state is also published to [CaptureSessionState], so the floating
 * character (when it is on screen instead of the chat's own) talks too.
 * Main thread only.
 */
class ClaudeChatSession(
    context: Context,
    private val characterName: String,
    private val listener: Listener
) {
    interface Listener {
        fun onUserMessage(text: String)
        /** The reply so far; called again as more arrives. */
        fun onReplyProgress(text: String)
        fun onReplyDone(text: String)
        fun onError(error: ClaudeChatError)
        fun onStateChanged(state: UniversalAiState)
    }

    private val appContext = context.applicationContext
    private val client = ClaudeApiClient()
    private val conversation = ClaudeConversation()
    private val chunker = SentenceChunker()
    private val speaker = ClaudeSpeaker(appContext) { speaking -> onSpeakerChanged(speaking) }
    private var call: ClaudeApiClient.Call? = null
    private val reply = StringBuilder()
    private var streaming = false

    var state: UniversalAiState = UniversalAiState.IDLE
        private set

    val busy: Boolean get() = streaming || speaker.hasWork || speaker.speaking

    /** False while the chat screen is in the background: it then stops steering the character. */
    private var foreground = true

    private val voiceOn: Boolean
        get() = ClaudeChatSettings.speakReplies(appContext) && !speaker.failed

    /** Loudness of the voice right now, for the mouth. */
    fun mouthSignal(): MouthSignal = MouthSignal(
        source = MouthSignal.Source.DIRECT_CHAT,
        available = speaker.loudnessAvailable,
        metrics = speaker.latestMetrics
    )

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty()) return
        val apiKey = ClaudeChatSettings.loadApiKey(appContext)
        if (apiKey == null) {
            listener.onError(ClaudeChatError.NoApiKey)
            return
        }
        interrupt()
        conversation.addUser(message)
        listener.onUserMessage(message)
        reply.setLength(0)
        chunker.reset()
        streaming = true
        setState(UniversalAiState.THINKING)

        val model = ClaudeChatSettings.model(appContext)
        val body = conversation.requestJson(model.id, ClaudeConversation.systemPrompt(characterName))
        call = client.stream(apiKey, body, object : ClaudeApiClient.Listener {
            override fun onText(text: String) {
                reply.append(text)
                listener.onReplyProgress(reply.toString())
                if (voiceOn) {
                    chunker.push(text).forEach(speaker::speak)
                } else {
                    // No voice: the character "talks" while the words appear.
                    setState(UniversalAiState.SPEAKING)
                }
            }

            override fun onComplete(stopReason: String?) {
                call = null
                streaming = false
                if (voiceOn) chunker.flush().forEach(speaker::speak)
                val text = reply.toString().trim()
                conversation.addAssistant(text)
                listener.onReplyDone(text)
                if (!speaker.hasWork && !speaker.speaking) setState(UniversalAiState.IDLE)
            }

            override fun onError(error: ClaudeChatError) {
                call = null
                streaming = false
                // Keep what was already said; a reply that never started is retried from scratch.
                if (reply.isBlank()) conversation.dropUnansweredUser() else conversation.addAssistant(reply.toString())
                listener.onError(error)
                if (!speaker.hasWork && !speaker.speaking) setState(UniversalAiState.IDLE)
            }
        })
    }

    /** Stops the reply being written and the voice. */
    fun interrupt() {
        call?.cancel()
        call = null
        if (streaming && reply.isNotBlank()) {
            conversation.addAssistant(reply.toString().trim())
            listener.onReplyDone(reply.toString().trim())
        }
        // Interrupted before the first word: the question stays, Claude sees it with the next one.
        streaming = false
        chunker.reset()
        speaker.stop()
        setState(UniversalAiState.IDLE)
    }

    /** Stops reading aloud but lets the written reply finish. */
    fun stopVoice() {
        chunker.reset()
        speaker.stop()
        setState(if (streaming) UniversalAiState.SPEAKING else UniversalAiState.IDLE)
    }

    /** The chat screen went to the background / came back. */
    fun setForeground(value: Boolean) {
        foreground = value
        publish()
    }

    /** The user is speaking into the microphone. */
    fun setListening(listening: Boolean) {
        if (busy) return
        setState(if (listening) UniversalAiState.LISTENING else UniversalAiState.IDLE)
    }

    fun clearHistory() {
        interrupt()
        conversation.clear()
    }

    fun release() {
        call?.cancel()
        call = null
        streaming = false
        speaker.release()
        CaptureSessionState.updateDirectChat(null, MouthSignal.NONE)
    }

    private fun onSpeakerChanged(speaking: Boolean) {
        if (speaking) setState(UniversalAiState.SPEAKING)
        else if (!speaker.hasWork) setState(if (streaming) UniversalAiState.THINKING else UniversalAiState.IDLE)
    }

    private fun setState(next: UniversalAiState) {
        if (state != next) {
            state = next
            listener.onStateChanged(next)
        }
        publish()
    }

    /** Called by the screen every frame while speaking, so the floating character's mouth moves too. */
    fun publish() {
        if (foreground) {
            CaptureSessionState.updateDirectChat(state, mouthSignal())
        } else {
            CaptureSessionState.updateDirectChat(null, MouthSignal.NONE)
        }
    }
}
