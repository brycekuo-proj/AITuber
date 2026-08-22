# Android PoC

## Goal

Verify whether Android can detect another AI app's playback start and stop events, and whether a usable real-time audio level is available.

## Primary Method

Use Android Playback Capture on Android 10 / API 29+:

- The user must grant MediaProjection permission.
- The app must have microphone recording permission because audio capture uses `AudioRecord`.
- The source app must allow its playback to be captured.
- Audio usages currently matched: `USAGE_MEDIA` and `USAGE_GAME`.

## Expected Debug UI

The Debug UI shows:

- Target App
- Detection Method
- State: `IDLE`, `SPEAKING`, or `UNKNOWN`
- Audio Level
- Capture Status

## State Mapping

The first reducer uses a small hysteresis window:

- Several frames above the speaking threshold become `SPEAKING`.
- Several frames below the idle threshold become `IDLE`.
- Missing or blocked signal becomes `UNKNOWN`.

This avoids treating a single audio spike as speech.

## Capture Limits

Android Playback Capture is permissioned and source-controlled. If the source app disables capture, AITuber must report that capture is unavailable or blocked. It must not attempt to bypass app policy, Android permissions, DRM behavior, or platform restrictions.

## Fallback Design

When Playback Capture is blocked:

- Display `UNKNOWN` instead of pretending detection is reliable.
- Keep the Character Engine alive with a neutral idle-safe state.
- Allow later adapters to contribute safe signals, such as media session changes or explicitly permitted accessibility events.
- Log/report the capture status for debugging.

## Manual Test Steps

1. Install and launch the debug app on Android 10+.
2. Tap `Start Playback Capture`.
3. Grant microphone permission if prompted.
4. Grant screen/audio capture permission if prompted.
5. Play speech from a target AI app.
6. Observe whether Audio Level rises and State becomes `SPEAKING`.
7. Stop playback and observe whether State returns to `IDLE`.
8. Test an app that blocks capture and confirm Capture Status reports the limitation.
