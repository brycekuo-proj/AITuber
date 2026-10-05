package com.aituber.poc.claudechat

/**
 * Cuts streamed text into sentences so the character can start speaking the
 * first one while Claude is still writing the rest.
 *
 * A sentence ends at 。！？!?；; or a newline, or at "." followed by a space
 * (so "3.5" and "v1.2" stay whole). A run longer than [maxChars] is cut at
 * the last comma. Markdown marks are removed because they would be read aloud.
 */
class SentenceChunker(
    private val minChars: Int = 4,
    private val maxChars: Int = 120
) {
    private val buffer = StringBuilder()

    fun push(text: String): List<String> {
        buffer.append(text)
        val out = mutableListOf<String>()
        while (true) {
            val end = findBreak() ?: break
            val piece = buffer.substring(0, end)
            buffer.delete(0, end)
            addSpeakable(out, piece)
        }
        return out
    }

    /** Whatever is left once the reply is complete. */
    fun flush(): List<String> {
        val out = mutableListOf<String>()
        addSpeakable(out, buffer.toString(), force = true)
        buffer.setLength(0)
        return out
    }

    fun reset() = buffer.setLength(0)

    private fun findBreak(): Int? {
        for (i in buffer.indices) {
            val c = buffer[i]
            val isEnd = when (c) {
                '。', '！', '？', '!', '?', '；', ';', '\n', '…' -> true
                '.' -> i + 1 < buffer.length && buffer[i + 1].isWhitespace()
                else -> false
            }
            if (isEnd && speakableLength(buffer.substring(0, i + 1)) >= minChars) {
                // Keep closing quotes/brackets with their sentence.
                var end = i + 1
                while (end < buffer.length && buffer[end] in CLOSERS) end++
                return end
            }
            if (i + 1 >= maxChars) {
                val comma = buffer.substring(0, i + 1).indexOfLast { it in SOFT_BREAKS }
                return if (comma >= minChars) comma + 1 else i + 1
            }
        }
        return null
    }

    private fun addSpeakable(out: MutableList<String>, raw: String, force: Boolean = false) {
        val cleaned = clean(raw)
        if (cleaned.isEmpty()) return
        // A fragment too short to say on its own ("好" before a newline) joins the next one.
        if (!force && speakableLength(cleaned) < minChars && out.isNotEmpty()) {
            out[out.lastIndex] = out.last() + cleaned
            return
        }
        out += cleaned
    }

    companion object {
        private const val CLOSERS = "」』）)】\"'”’"
        private const val SOFT_BREAKS = "，,、 ：:"

        private val CODE_FENCE = Regex("```[a-zA-Z0-9]*")
        private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
        private val LIST_MARK = Regex("(?m)^\\s*(?:[-*+•]|\\d+[.)])\\s+")
        private val HEADING = Regex("(?m)^\\s*#{1,6}\\s*")
        private val EMPHASIS = Regex("[*_`~>|]+")
        private val SPACES = Regex("\\s+")

        /** Text as it should be spoken: no Markdown marks, links read by their label. */
        fun clean(text: String): String = text
            .replace(CODE_FENCE, " ")
            .replace(LINK, "$1")
            .replace(LIST_MARK, "")
            .replace(HEADING, "")
            .replace(EMPHASIS, "")
            .replace(SPACES, " ")
            .trim()

        private fun speakableLength(text: String): Int = text.count { it.isLetterOrDigit() }
    }
}
