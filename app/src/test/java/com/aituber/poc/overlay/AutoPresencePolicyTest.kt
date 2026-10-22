package com.aituber.poc.overlay

import com.aituber.poc.overlay.AutoPresencePolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoPresencePolicyTest {
    private val idleMs = 15_000L

    private fun AutoPresencePolicy.at(
        nowMs: Long,
        speaking: Boolean = false,
        session: Boolean = false,
        chat: Boolean = false,
        collapsed: Boolean = false,
        autoHide: Boolean = true,
        hold: Boolean = false
    ) = evaluate(nowMs, speaking, session, chat, collapsed, autoHide, hold)

    @Test
    fun staysVisibleAfterLaunchUntilAConversationHappened() {
        val policy = AutoPresencePolicy(idleMs)

        assertEquals(Action.NONE, policy.at(0L).action)
        assertEquals(Action.NONE, policy.at(10 * 60_000L).action)
    }

    @Test
    fun tucksAwayAfterTheConversationHasBeenQuietLongEnough() {
        val policy = AutoPresencePolicy(idleMs)
        policy.at(1_000L, speaking = true, session = true)

        val quiet = policy.at(2_000L)
        assertEquals(Action.NONE, quiet.action)
        assertEquals(1_000L + idleMs, quiet.recheckAtMs)

        assertEquals(Action.COLLAPSE, policy.at(1_000L + idleMs).action)
    }

    @Test
    fun anOngoingVoiceSessionKeepsTheCharacterOut() {
        val policy = AutoPresencePolicy(idleMs)
        policy.at(0L, speaking = true, session = true)

        val stillInSession = policy.at(60_000L, session = true)
        assertEquals(Action.NONE, stillInSession.action)
        assertNull(stillInSession.recheckAtMs)
    }

    @Test
    fun comesBackWhenTheAiSpeaks() {
        val policy = AutoPresencePolicy(idleMs)

        assertEquals(Action.EXPAND, policy.at(0L, speaking = true, collapsed = true).action)
        assertEquals(Action.NONE, policy.at(0L, session = true, collapsed = true).action)
    }

    @Test
    fun autoHideOffNeverMovesTheCharacterOnItsOwn() {
        val policy = AutoPresencePolicy(idleMs)
        policy.at(0L, speaking = true, autoHide = false)

        assertEquals(Action.NONE, policy.at(idleMs * 4, autoHide = false).action)
        assertEquals(Action.NONE, policy.at(idleMs * 5, speaking = true, collapsed = true, autoHide = false).action)
    }

    @Test
    fun openingTheAituberChatBringsTheCharacterBackEvenWithAutoHideOff() {
        val policy = AutoPresencePolicy(idleMs)

        assertEquals(Action.EXPAND, policy.at(0L, chat = true, collapsed = true, autoHide = false).action)
        // Only the opening of the chat, not every update while it stays open.
        assertEquals(Action.NONE, policy.at(100L, chat = true, collapsed = true, autoHide = false).action)
    }

    @Test
    fun neverTucksAwayWhileTheUserIsHandlingIt() {
        val policy = AutoPresencePolicy(idleMs)
        policy.at(0L, speaking = true)

        assertEquals(Action.NONE, policy.at(idleMs * 2, hold = true).action)
        assertEquals(Action.COLLAPSE, policy.at(idleMs * 2).action)
    }

    @Test
    fun broughtBackByHandWhileQuietItStaysUntilTheNextConversationEnds() {
        val policy = AutoPresencePolicy(idleMs)
        policy.at(0L, speaking = true)
        assertEquals(Action.COLLAPSE, policy.at(idleMs).action)

        policy.onShown(idleMs + 1_000L, conversationActive = false)
        assertEquals(Action.NONE, policy.at(idleMs * 10).action)

        policy.at(idleMs * 10, speaking = true)
        assertEquals(Action.COLLAPSE, policy.at(idleMs * 11).action)
    }
}
