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
