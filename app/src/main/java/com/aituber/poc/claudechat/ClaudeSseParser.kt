package com.aituber.poc.claudechat

import com.aituber.poc.character.live2d.install.JsonValue
import com.aituber.poc.character.live2d.install.MiniJson

/**
 * Reads the Messages API's server-sent events line by line and turns them into
 * the few things the chat needs: text as it arrives, why the reply stopped,
 * the end of the message, and errors sent mid-stream.
 */
class ClaudeSseParser {

    sealed class Event {
        data class Text(val text: String) : Event()
        data class StopReason(val reason: String) : Event()
        object MessageStop : Event()
        data class Error(val type: String, val message: String) : Event()
    }

    private val data = StringBuilder()

    /** Feeds one line without its line break; a blank line ends an event. */
    fun feedLine(line: String): List<Event> {
        if (line.isEmpty()) return dispatch()
        when {
            line.startsWith(":") -> Unit // comment / keep-alive
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").removePrefix(" "))
            }
            // "event:" repeats the JSON "type"; the JSON is what we read.
            else -> Unit
        }
        return emptyList()
    }

    /** Ends the stream: an event left without its blank line is still read. */
    fun finish(): List<Event> = dispatch()

    private fun dispatch(): List<Event> {
        if (data.isEmpty()) return emptyList()
        val payload = data.toString()
        data.setLength(0)
        return parsePayload(payload)
    }

    companion object {
        fun parsePayload(payload: String): List<Event> {
            val json = runCatching { MiniJson.parse(payload) as? JsonValue.Obj }.getOrNull() ?: return emptyList()
            return when (json.str("type")) {
                "content_block_delta" -> {
                    val delta = json.obj("delta")
                    val text = delta?.takeIf { it.str("type") == "text_delta" }?.str("text")
                    if (text.isNullOrEmpty()) emptyList() else listOf(Event.Text(text))
                }
                "message_delta" -> {
                    val reason = json.obj("delta")?.str("stop_reason")
                    if (reason == null) emptyList() else listOf(Event.StopReason(reason))
                }
                "message_stop" -> listOf(Event.MessageStop)
                "error" -> {
                    val error = json.obj("error")
                    listOf(Event.Error(error?.str("type") ?: "error", error?.str("message") ?: ""))
                }
                else -> emptyList()
            }
        }

        /** The `error` object of a non-streaming error response body, if it has one. */
        fun parseErrorBody(body: String): Event.Error? {
            val json = runCatching { MiniJson.parse(body) as? JsonValue.Obj }.getOrNull() ?: return null
            val error = json.obj("error") ?: return null
            return Event.Error(error.str("type") ?: "error", error.str("message") ?: "")
        }
    }
}
