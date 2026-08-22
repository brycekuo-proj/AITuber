package com.aituber.poc.state

data class OverlayLifecycleSnapshot(
    val trace: List<String>,
    val overlayServiceAlive: String
) {
    companion object {
        fun empty() = OverlayLifecycleSnapshot(
            trace = emptyList(),
            overlayServiceAlive = "DISABLED"
        )
    }
}
