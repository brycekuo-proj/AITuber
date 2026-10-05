package com.aituber.poc.claudechat

/** Claude models the chat screen offers. The first is the default. */
data class ClaudeModel(val id: String, val label: String)

object ClaudeModels {
    /** Fastest replies: the character starts talking soonest. */
    val HAIKU = ClaudeModel("claude-haiku-4-5-20251001", "Haiku 4.5（最快）")
    val SONNET = ClaudeModel("claude-sonnet-5-5", "Sonnet 5.5（較聰明）")
    val OPUS = ClaudeModel("claude-opus-5-5", "Opus 5.5（最強，較慢）")

    val all: List<ClaudeModel> = listOf(HAIKU, SONNET, OPUS)

    fun byId(id: String?): ClaudeModel = all.firstOrNull { it.id == id } ?: HAIKU
}
