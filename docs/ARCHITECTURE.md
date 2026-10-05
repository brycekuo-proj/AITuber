# Architecture

AITuber is split into four independent layers:

## AI Adapter

AI Adapters know how to observe one AI source. In the Android PoC, the first adapter uses Android Playback Capture to inspect system playback audio when Android and the source app allow it.

Adapters emit detection metadata and raw signal values. They do not know how characters animate.

Examples planned for later:

- Playback Capture adapter.
- Accessibility event adapter where policy allows it.
- Notification/media session adapter.
- App-specific adapter for a given AI app.

## Universal State

Universal State converts adapter-specific signals into a shared state model:

- `IDLE`
- `SPEAKING`
- `LISTENING`
- `THINKING`
- `UNKNOWN`

The current reducer only maps audio level to `SPEAKING`, `IDLE`, or `UNKNOWN`. `LISTENING` and `THINKING` are reserved for later adapters that can observe safe microphone, UI, or app activity signals.

## Character Engine

Character Engine consumes Universal State and applies behavior rules. It should not depend on a specific AI provider or detection method.

The first commit contains a debug engine that forwards snapshots to the Debug UI.

## Character Adapter

Character Adapters render a character through a specific character runtime. Future adapters may target Live2D, native Android views, sprites, or other renderers.

The first phase does not integrate Live2D. The debug adapter only renders text state into the app UI.

## Boundary Rule

The dependency direction is:

`AI Adapter -> Universal State -> Character Engine -> Character Adapter`

No AI Adapter may call a Character Adapter directly. No Character Adapter may inspect Android Playback Capture details. This keeps future support open for any AI app and any character renderer.

## Detection Sources (current)

| Situation | Who decides SPEAKING / IDLE | Mouth loudness |
|---|---|---|
| An AI app plays speech as media (read aloud, or a voice mode that plays as media) and Playback Capture hears it | `PLAYBACK_CAPTURE` | Playback Capture PCM |
| Voice session active (e.g. ChatGPT voice mode plays as a voice call) and the output-mix Visualizer works | `VISUALIZER` | Visualizer waveform |
| Voice session active, Visualizer unavailable | `PLAYBACK_FALLBACK` (Playback Capture) | Playback Capture PCM |
| Otherwise | `NO_ACTIVE_SESSION` → IDLE | — |

"Voice session" (`VoiceSessionDetector`, fed by `AndroidPlaybackStateProbe`) is any of,
while something is playing:

- voice-call audio (`USAGE_VOICE_COMMUNICATION`, any content type) — ChatGPT;
- assistant audio (`USAGE_ASSISTANT`);
- the audio mode is `MODE_IN_COMMUNICATION`;
- another app has the microphone open (or closed it less than 60 s ago, for
  push-to-talk) while media/unknown audio plays — hands-free voice modes that
  do not play as a call (Claude's hands-free mode lets the user talk over it, so
  its mic stays open). AITuber's own capture (`REMOTE_SUBMIX`) and system recorders such as
  the "Hey Google" hotword do not count, so music alone never makes a session.

- Playback Capture listens to every installed app in `AiTargetApps` (ChatGPT, Gemini, Claude, Grok, Copilot, Perplexity) for `MEDIA`, `GAME` and `UNKNOWN` audio. Voice-call audio cannot be captured by third-party apps, which is why call-style voice modes rely on the Visualizer. If an AI app opts out of capture the status shows "capturing silence" and the Visualizer path still works during a voice session.
- `UniversalStateSnapshot.mouthSignal` is the detector-neutral loudness for the mouth; character code must not read `visualizerProbe` or capture details.
- The accessibility probe was removed: it only produced diagnostics and conflicts with Play Store AccessibilityService policy.

## Live2D Runtime Ownership

The Cubism runtime (and its GL shader cache) is process-wide, so only one Live2D view renders at a time. `Live2DNativeBridge` gives ownership to the most recently initialized view; older views become inert. The floating overlay always wins: while it runs (`Live2DOverlayPresence.active`) the home screen shows a notice instead of its preview.
