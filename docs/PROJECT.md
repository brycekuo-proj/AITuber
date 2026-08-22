# AITuber

AITuber is an Android-first universal AI character skin. It does not provide its own LLM, TTS, or ASR. Instead, it sits above existing AI apps such as ChatGPT, Gemini, Claude, and Grok, then drives a character from safe, observable app or system signals.

The first phase is an Android proof of concept. The PoC answers two questions:

1. Can Android detect when another AI app starts and stops audio playback?
2. When the system and source app allow it, can AITuber read a usable real-time audio level?

This repository intentionally starts small. There is no Live2D runtime, OpenAI API integration, third-party TTS, character marketplace, backend, or unnecessary UI framework dependency in the first commit.

## Current Scope

- Android app written in Kotlin.
- Minimal Debug UI showing Target App, Detection Method, State, Audio Level, and Capture Status.
- Playback Capture based AI adapter for Android 10 / API 29+.
- Universal state reducer that maps raw detection signals to `IDLE`, `SPEAKING`, or `UNKNOWN`.
- Character Engine and Character Adapter interfaces.
- Placeholder overlay service for later character surface work.

## Non-Goals

- Bypassing Android security restrictions.
- Capturing audio from apps that opt out of playback capture.
- Shipping a character runtime.
- Calling AI provider APIs directly.
- Replacing existing AI apps.
