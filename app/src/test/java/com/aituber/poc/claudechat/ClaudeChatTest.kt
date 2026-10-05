package com.aituber.poc.claudechat

import com.aituber.poc.character.live2d.install.JsonValue
import com.aituber.poc.character.live2d.install.MiniJson
import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.state.MouthSignal
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerWaveformMetrics
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeSseParserTest {
    private fun feed(vararg lines: String): List<ClaudeSseParser.Event> {
        val parser = ClaudeSseParser()
        return lines.flatMap { parser.feedLine(it) } + parser.finish()
    }

    @Test
    fun streamedReplyBecomesTextStopAndEnd() {
        val events = feed(
            "event: message_start",
            """data: {"type":"message_start","message":{"id":"msg_1","role":"assistant","content":[]}}""",
            "",
            "event: content_block_start",
            """data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            "",
            ": keep-alive",
            "event: content_block_delta",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你好"}}""",
            "",
            "event: ping",
            """data: {"type": "ping"}""",
            "",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"！\n我是 Haru"}}""",
            "",
            """data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":9}}""",
            "",
            """data: {"type":"message_stop"}""",
            ""
        )
        assertEquals(
            listOf(
                ClaudeSseParser.Event.Text("你好"),
                ClaudeSseParser.Event.Text("！\n我是 Haru"),
                ClaudeSseParser.Event.StopReason("end_turn"),
                ClaudeSseParser.Event.MessageStop
            ),
            events
        )
    }

    @Test
    fun midStreamErrorIsReported() {
        val events = feed(
            "event: error",
            """data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""",
            ""
        )
        assertEquals(listOf(ClaudeSseParser.Event.Error("overloaded_error", "Overloaded")), events)
        assertEquals(ClaudeChatError.Overloaded, ClaudeChatError.fromStream(events.single() as ClaudeSseParser.Event.Error))
    }

    @Test
    fun thinkingAndToolDeltasAreIgnoredAndGarbageIsSafe() {
        val events = feed(
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hmm"}}""",
            "",
            "data: {not json",
            ""
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun httpErrorsBecomeFriendlyMessages() {
        val body = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        val parsed = ClaudeSseParser.parseErrorBody(body)
        assertEquals("authentication_error", parsed?.type)
        assertEquals(ClaudeChatError.InvalidKey, ClaudeChatError.fromHttp(401, parsed))
        assertEquals(ClaudeChatError.RateLimited, ClaudeChatError.fromHttp(429, null))
        assertEquals(ClaudeChatError.Overloaded, ClaudeChatError.fromHttp(529, null))
        val credit = ClaudeSseParser.parseErrorBody(
            """{"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low"}}"""
        )
        assertEquals(ClaudeChatError.NoCredit, ClaudeChatError.fromHttp(400, credit))
        assertTrue(ClaudeChatError.fromHttp(500, null) is ClaudeChatError.Api)
    }
}

class SentenceChunkerTest {
    @Test
    fun firstSentenceIsReadyBeforeTheReplyEnds() {
        val chunker = SentenceChunker()
        assertEquals(emptyList<String>(), chunker.push("今天天氣"))
        assertEquals(listOf("今天天氣很好喔。"), chunker.push("很好喔。我們"))
        assertEquals(listOf("我們去散步吧！"), chunker.push("去散步吧！"))
        assertEquals(emptyList<String>(), chunker.flush())
    }

    @Test
    fun englishSentencesSplitAtPeriodsButNotDecimals() {
        val chunker = SentenceChunker()
        val out = chunker.push("Version 3.5 is out. It is fast! ") + chunker.flush()
        assertEquals(listOf("Version 3.5 is out.", "It is fast!"), out)
    }

    @Test
    fun markdownIsNotReadAloud() {
        val chunker = SentenceChunker()
        val out = chunker.push("## 重點\n- **第一**：多喝水\n- 看 [說明](https://example.com) 就好\n") + chunker.flush()
        assertEquals(listOf("重點 第一：多喝水", "看 說明 就好"), out)
    }

    @Test
    fun tinyFragmentsJoinTheirNeighbour() {
        val chunker = SentenceChunker()
        // "好的！" is too short alone, so it waits for the next sentence.
        assertEquals(listOf("好的！今天要做什麼呢？"), chunker.push("好的！今天要做什麼呢？嗯"))
        assertEquals(listOf("嗯"), chunker.flush())
    }

    @Test
    fun longRunsAreCutAtAComma() {
        val chunker = SentenceChunker(maxChars = 20)
        val out = chunker.push("這是一段很長很長的句子，沒有句號一直寫下去一直寫下去一直寫") + chunker.flush()
        assertEquals("這是一段很長很長的句子，", out.first())
        assertTrue(out.size >= 2)
    }

    @Test
    fun closingQuotesStayWithTheirSentence() {
        val chunker = SentenceChunker()
        assertEquals(listOf("她說：「我們走吧。」"), chunker.push("她說：「我們走吧。」然後"))
    }
}

class ClaudeConversationTest {
    @Test
    fun requestCarriesModelSystemAndAlternatingTurns() {
        val conversation = ClaudeConversation()
        conversation.addUser("嗨")
        conversation.addAssistant("你好！")
        conversation.addUser("今天好嗎？")
        val json = MiniJson.parse(conversation.requestJson("claude-haiku-4-5-20251001", "sys")) as JsonValue.Obj
        assertEquals("claude-haiku-4-5-20251001", json.str("model"))
        assertEquals(true, json.bool("stream"))
        assertEquals("sys", json.str("system"))
        val roles = json.arr("messages")!!.items.map { (it as JsonValue.Obj).str("role") }
        assertEquals(listOf("user", "assistant", "user"), roles)
    }

    @Test
    fun failedTurnDoesNotLeaveTwoUserMessages() {
        val conversation = ClaudeConversation()
        conversation.addUser("第一次")
        conversation.dropUnansweredUser()
        conversation.addUser("再試一次")
        assertEquals(listOf(ClaudeConversation.Message("user", "再試一次")), conversation.history)
        conversation.addUser("又一次")
        assertEquals(listOf(ClaudeConversation.Message("user", "再試一次\n又一次")), conversation.history)
    }

    @Test
    fun longHistoryKeepsTheNewestAndStartsWithTheUser() {
        val conversation = ClaudeConversation(maxMessages = 5)
        repeat(6) { i ->
            conversation.addUser("u$i")
            conversation.addAssistant("a$i")
        }
        val history = conversation.history
        assertTrue(history.size <= 5)
        assertEquals("user", history.first().role)
        assertEquals("a5", history.last().text)
    }

    @Test
    fun systemPromptNamesTheCharacterAndAsksForSpokenStyle() {
        val prompt = ClaudeConversation.systemPrompt("Haru")
        assertTrue(prompt.contains("Haru"))
        assertTrue(prompt.contains("Markdown"))
        assertTrue(prompt.contains("繁體中文"))
    }
}

class ClaudeChatSettingsTest {
    @Test
    fun apiKeyShapeCheck() {
        assertTrue(ClaudeChatSettings.looksLikeApiKey("  sk-ant-api03-" + "a".repeat(40) + "  "))
        assertFalse(ClaudeChatSettings.looksLikeApiKey("sk-proj-" + "a".repeat(40)))
        assertFalse(ClaudeChatSettings.looksLikeApiKey("sk-ant-short"))
        assertFalse(ClaudeChatSettings.looksLikeApiKey("sk-ant-api03-" + "a".repeat(20) + " " + "b".repeat(20)))
    }

    @Test
    fun unknownModelFallsBackToTheFastOne() {
        assertEquals(ClaudeModels.HAIKU, ClaudeModels.byId("claude-old-model"))
        assertEquals(ClaudeModels.SONNET, ClaudeModels.byId("claude-sonnet-5-5"))
    }

    @Test
    fun voiceLanguageFollowsTheText() {
        assertEquals(Locale.TRADITIONAL_CHINESE, ClaudeSpeaker.languageFor("你好呀"))
        assertEquals(Locale.JAPANESE, ClaudeSpeaker.languageFor("こんにちは"))
        assertFalse(ClaudeSpeaker.languageFor("Hello there").language == "zh")
    }
}

class DirectChatOverrideTest {
    @After
    fun tearDown() {
        CaptureSessionState.updateDirectChat(null, MouthSignal.NONE)
        CaptureSessionState.resetResolverForTests()
    }

    @Test
    fun chatStateAndVoiceDriveTheSharedSnapshot() {
        val voice = MouthSignal(
            MouthSignal.Source.DIRECT_CHAT,
            available = true,
            metrics = VisualizerWaveformMetrics(rms = 0.4, peak = 0.9, activityRatio = 0.7)
        )
        CaptureSessionState.updateDirectChat(UniversalAiState.SPEAKING, voice)
        val speaking = CaptureSessionState.current()
        assertEquals(UniversalAiState.SPEAKING, speaking.state)
        assertEquals(voice, speaking.mouthSignal)

        CaptureSessionState.updateDirectChat(null, voice)
        val after = CaptureSessionState.current()
        assertFalse(after.state == UniversalAiState.SPEAKING)
        assertFalse(after.mouthSignal.source == MouthSignal.Source.DIRECT_CHAT)
    }

    @Test
    fun noChatMeansNoOverride() {
        CaptureSessionState.updateDirectChat(null, MouthSignal.NONE)
        assertNull(null)
        assertFalse(CaptureSessionState.current().speakingSignalSource == "AITuber Claude chat")
    }
}
