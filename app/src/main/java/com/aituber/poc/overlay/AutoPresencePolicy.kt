package com.aituber.poc.overlay

/**
 * When the floating character tucks itself into the bubble and when it comes back.
 *
 *  - It comes back as soon as the AI speaks (if auto-hide is on), and whenever
 *    AITuber's own chat screen opens (that screen talks through this character).
 *  - It tucks itself away once a conversation has happened and then nothing has
 *    been going on for [idleCollapseMs]. "Going on" means the AI speaking, a voice
 *    session (call-style audio, an open microphone) or the AITuber chat being open.
 *  - Shown by the user while nothing was going on, it stays until a conversation
 *    has come and gone, so it does not vanish right after being opened.
 *  - Never tucked away while the user is handling it (menu open, moving, resizing).
 *
 * Pure logic, main thread only; see AutoPresencePolicyTest.
 */
class AutoPresencePolicy(
    private val idleCollapseMs: Long = DEFAULT_IDLE_COLLAPSE_MS
) {
    enum class Action { NONE, EXPAND, COLLAPSE }

    /** [recheckAtMs]: when to evaluate again even if no new state arrives. */
    data class Decision(val action: Action, val recheckAtMs: Long? = null)

    private var lastActiveMs: Long? = null
    private var conversationSeenSinceShown = false
    private var chatWasActive = false

    fun evaluate(
        nowMs: Long,
        speaking: Boolean,
        conversationActive: Boolean,
        chatActive: Boolean,
        collapsed: Boolean,
        autoHideEnabled: Boolean,
        holdVisible: Boolean
    ): Decision {
        val active = speaking || conversationActive || chatActive
        val chatStarted = chatActive && !chatWasActive
        chatWasActive = chatActive
        if (active) {
            lastActiveMs = nowMs
            conversationSeenSinceShown = true
        }

        if (collapsed) {
            val expand = chatStarted || (speaking && autoHideEnabled)
            return Decision(if (expand) Action.EXPAND else Action.NONE)
        }
        if (!autoHideEnabled || holdVisible || active || !conversationSeenSinceShown) {
            return Decision(Action.NONE)
        }
        val last = lastActiveMs ?: return Decision(Action.NONE)
        val collapseAtMs = last + idleCollapseMs
        return if (nowMs >= collapseAtMs) {
            Decision(Action.COLLAPSE)
        } else {
            Decision(Action.NONE, recheckAtMs = collapseAtMs)
        }
    }

    /**
     * The character was just shown. [conversationActive]: shown because something is going
     * on (it will tuck away after that conversation); false when the user brought it back
     * by hand while nothing was happening.
     */
    fun onShown(nowMs: Long, conversationActive: Boolean) {
        conversationSeenSinceShown = conversationActive
        if (conversationActive) lastActiveMs = nowMs
    }

    companion object {
        const val DEFAULT_IDLE_COLLAPSE_MS = 15_000L
    }
}
