package com.aituber.poc.overlay

object MouthDriveDiagnostics {
    @Volatile
    private var current = MouthDriveSnapshot.empty()

    fun update(mode: MouthDriveMode, targetOpen: Double?, smoothedOpen: Double) {
        current = MouthDriveSnapshot(
            mode = mode.name,
            targetOpen = targetOpen,
            smoothedOpen = smoothedOpen
        )
    }

    fun reset() {
        current = MouthDriveSnapshot.empty()
    }

    fun snapshot(): MouthDriveSnapshot = current
}

data class MouthDriveSnapshot(
    val mode: String,
    val targetOpen: Double?,
    val smoothedOpen: Double
) {
    companion object {
        fun empty() = MouthDriveSnapshot(
            mode = MouthDriveMode.CLOSED.name,
            targetOpen = 0.0,
            smoothedOpen = 0.0
        )
    }
}
