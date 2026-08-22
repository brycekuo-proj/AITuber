package com.aituber.poc.poc

import com.aituber.poc.state.SafeAccessibilityNodeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccessibilityUiSignatureTest {
    @Test
    fun signatureIsDeterministic() {
        val nodes = listOf(node(viewIdResourceName = "id/action", boundsInScreen = "0,0,20,20"))

        val first = AccessibilityUiSignature.signature(nodes)
        val second = AccessibilityUiSignature.signature(nodes)

        assertEquals(first, second)
    }

    @Test
    fun nodeOrderingDoesNotAffectSignature() {
        val first = node(viewIdResourceName = "id/first", boundsInScreen = "0,0,20,20")
        val second = node(viewIdResourceName = "id/second", boundsInScreen = "20,0,40,20")

        val signatureA = AccessibilityUiSignature.signature(listOf(first, second))
        val signatureB = AccessibilityUiSignature.signature(listOf(second, first))

        assertEquals(signatureA, signatureB)
    }

    @Test
    fun textContentDoesNotEnterSignature() {
        val withPrivateText = node(hasText = true, textLength = 12)
        val withDifferentPrivateTextSameLength = node(hasText = true, textLength = 12)

        assertEquals(
            AccessibilityUiSignature.signature(listOf(withPrivateText)),
            AccessibilityUiSignature.signature(listOf(withDifferentPrivateTextSameLength))
        )
    }

    @Test
    fun metadataChangeChangesSignature() {
        val quiet = node(stateDescription = "quiet", boundsInScreen = "0,0,100,100")
        val active = quiet.copy(stateDescription = "active")

        assertNotEquals(
            AccessibilityUiSignature.signature(listOf(quiet)),
            AccessibilityUiSignature.signature(listOf(active))
        )
    }

    private fun node(
        treePath: String = "0",
        className: String = "android.view.View",
        viewIdResourceName: String = "n/a",
        contentDescription: String = "n/a",
        stateDescription: String = "n/a",
        selected: Boolean = false,
        checked: Boolean = false,
        enabled: Boolean = true,
        focused: Boolean = false,
        clickable: Boolean = false,
        boundsInScreen: String = "0,0,1,1",
        childCount: Int = 0,
        hasText: Boolean = false,
        textLength: Int = 0
    ) = SafeAccessibilityNodeMetadata(
        treePath = treePath,
        className = className,
        viewIdResourceName = viewIdResourceName,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        selected = selected,
        checked = checked,
        enabled = enabled,
        focused = focused,
        clickable = clickable,
        boundsInScreen = boundsInScreen,
        childCount = childCount,
        hasText = hasText,
        textLength = textLength
    )
}
