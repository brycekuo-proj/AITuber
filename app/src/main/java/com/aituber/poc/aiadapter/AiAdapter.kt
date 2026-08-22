package com.aituber.poc.aiadapter

import com.aituber.poc.state.UniversalStateSnapshot

interface AiAdapter {
    val targetAppLabel: String
    val detectionMethod: String
    fun start(onSnapshot: (UniversalStateSnapshot) -> Unit)
    fun stop()
}
