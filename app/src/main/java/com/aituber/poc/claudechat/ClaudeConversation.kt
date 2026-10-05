package com.aituber.poc.claudechat

import com.aituber.poc.character.live2d.install.JsonValue
import com.aituber.poc.character.live2d.install.MiniJson

/**
 * The conversation with Claude as the Messages API sees it: alternating user
 * and assistant turns, the oldest dropped once it grows long (the character is
 * a chat companion, not a long-document assistant, and every turn costs tokens).
 */
class ClaudeConversation(
    private val maxMessages: Int = 24
) {
    data class Message(val role: String, val text: String)

    private val messages = mutableListOf<Message>()

    val history: List<Message> get() = messages.toList()

    fun addUser(text: String) {
        // Two user turns in a row (the last reply was stopped before it began): send both.
        val last = messages.lastOrNull()
        if (last?.role == USER) {
            messages[messages.lastIndex] = Message(USER, last.text + "\n" + text)
        } else {
            messages += Message(USER, text)
        }
        trim()
    }

    fun addAssistant(text: String) {
        if (text.isBlank()) return
        if (messages.lastOrNull()?.role != USER) return
        messages += Message(ASSISTANT, text)
        trim()
    }

    /** Drops a user turn that never got an answer, so a retry starts clean. */
    fun dropUnansweredUser() {
        if (messages.lastOrNull()?.role == USER) messages.removeAt(messages.lastIndex)
    }

    fun clear() = messages.clear()

    /** The request body for a streamed reply. */
    fun requestJson(model: String, system: String, maxTokens: Int = 1024): String {
        val body = JsonValue.Obj()
        body["model"] = JsonValue.of(model)
        body["max_tokens"] = JsonValue.of(maxTokens)
        body["stream"] = JsonValue.of(true)
        body["system"] = JsonValue.of(system)
        body["messages"] = JsonValue.Arr(
            messages.mapTo(mutableListOf()) { message ->
                JsonValue.Obj().apply {
                    this["role"] = JsonValue.of(message.role)
                    this["content"] = JsonValue.of(message.text)
                }
            }
        )
        return MiniJson.write(body)
    }

    private fun trim() {
        while (messages.size > maxMessages) messages.removeAt(0)
        // The API wants the first message to be the user's.
        while (messages.firstOrNull()?.role == ASSISTANT) messages.removeAt(0)
    }

    companion object {
        const val USER = "user"
        const val ASSISTANT = "assistant"

        /**
         * The character's instructions. Replies are read aloud by the phone and
         * shown in a speech bubble, so they must be short and plain.
         */
        fun systemPrompt(characterName: String): String = """
            你是「$characterName」，一個住在使用者手機裡的 Live2D 角色，正在和使用者聊天。
            你的回覆會被手機念出來，同時顯示在角色的對話框裡，所以：
            - 像朋友說話一樣自然、口語，通常一到三句話就好；使用者要求詳細時才說長一點。
            - 不要用 Markdown、條列、表格、程式碼區塊或表情符號。
            - 使用者用什麼語言，你就用什麼語言回答；中文請用繁體中文。
            - 你背後是 Anthropic 的 Claude；被問到時可以照實說。
        """.trimIndent()
    }
}
