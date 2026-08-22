package com.aituber.poc.poc

import com.aituber.poc.state.SafeAccessibilityNodeMetadata
import java.security.MessageDigest

object AccessibilityUiSignature {
    fun signature(nodes: List<SafeAccessibilityNodeMetadata>): String {
        if (nodes.isEmpty()) return "empty"
        val normalized = nodes.map { node ->
            listOf(
                "class=${node.className}",
                "id=${node.viewIdResourceName}",
                "cd=${node.contentDescription}",
                "state=${node.stateDescription}",
                "selected=${node.selected}",
                "checked=${node.checked}",
                "enabled=${node.enabled}",
                "focused=${node.focused}",
                "clickable=${node.clickable}",
                "bounds=${node.boundsInScreen}",
                "children=${node.childCount}",
                "hasText=${node.hasText}",
                "textLength=${node.textLength}"
            ).joinToString("|")
        }.sorted().joinToString("\n")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return digest.take(12)
    }
}
