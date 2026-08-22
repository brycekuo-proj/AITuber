# Roadmap

## Phase 1: Android Playback Detection PoC

- Build minimal Android/Kotlin app.
- Request Android Playback Capture with user consent.
- Detect `SPEAKING` and `IDLE` from safe audio level readings.
- Show capture failures clearly when Android or the source app blocks capture.
- Preserve architectural boundaries between AI Adapter, Universal State, Character Engine, and Character Adapter.

## Phase 2: Overlay Prototype

- Move state display into a real Android overlay when permission is granted.
- Keep overlay rendering independent from AI detection.
- Add foreground service lifecycle handling for longer-running capture sessions.

## Phase 3: More Safe Signals

- Evaluate media session metadata where available.
- Evaluate accessibility signals only with explicit user permission and clear disclosure.
- Add per-app adapters without coupling them to character runtimes.

## Phase 4: Character Runtime

- Add the first real Character Adapter.
- Evaluate Live2D only after detection quality is proven.
- Keep character asset format and AI provider support independent.

## Explicitly Deferred

- Provider APIs such as OpenAI, Gemini, Claude, or Grok.
- TTS and ASR engines.
- Character marketplace.
- Cloud sync.
- Security bypasses or private API usage.
