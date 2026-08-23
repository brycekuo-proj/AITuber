# Live2D Setup

AITuber treats Live2D as an optional Character Adapter. The stable detection path remains:

`AI Adapter -> Universal State -> Character Engine -> Character Adapter`

Audio probes, Visualizer waveform processing, Universal State resolution, and mouth amplitude mapping do not call Live2D directly. The Character Engine converts the latest `UniversalStateSnapshot` plus the verified mouth amplitude into a neutral `CharacterParameterFrame`, then passes that frame to the selected adapter.

## Repository Policy

Do not commit Live2D SDK binaries, proprietary headers, generated native libraries, or restricted sample models to this public repository.

The repository may contain only placeholders and setup notes:

- `app/libs/live2d/.gitkeep`
- `app/src/main/assets/live2d/.gitkeep`
- local README/setup files

## Local SDK Location

Place a legally obtained Live2D Cubism SDK for Native or Android under:

`app/libs/live2d/`

This directory is intended for local development only. Keep SDK binaries and licensed files ignored by Git unless their license explicitly allows redistribution.

## Local Model Location

Place legally usable model assets under:

`app/src/main/assets/live2d/<model-name>/`

Expected model entry point:

`app/src/main/assets/live2d/<model-name>/<model-name>.model3.json`

Do not commit proprietary `.moc3`, texture, motion, physics, or sample model files unless you have explicit redistribution rights.

## Enabling Live2D

The default debug build must work with no Live2D SDK installed. Live2D is therefore disabled by default and the app uses `MINIMAL_MOUTH`.

Future local builds can enable a real renderer with a Gradle flag such as:

`LIVE2D_ENABLED=true`

Until a real SDK-backed renderer is wired in, selecting `LIVE2D` without SDK/model support falls back to:

`MINIMAL_MOUTH`

Diagnostics will report:

- `Live2D Available = NO`
- `Live2D Model Loaded = NO`
- `Live2D Fallback Reason = LIVE2D_UNAVAILABLE_FALLBACK_MINIMAL_MOUTH`

## Model Config

`Live2DModelConfig` defines:

- `modelName`
- `model3JsonPath`
- `mouthParameterId`

Default mouth parameter:

`ParamMouthOpenY`

## Mouth Parameter Bridge

`CharacterParameterFrame.mouthOpen` is mapped to Live2D:

`mouthOpen -> ParamMouthOpenY`

The value is clamped to `0.0..1.0`.

If the model does not expose `ParamMouthOpenY`, the adapter must not crash. It should report:

`Live2D Mouth Parameter = NOT_FOUND`

## Current Alpha Scope

`v0.1.0-alpha1` includes the Character Parameter abstraction, Minimal Mouth adapter migration, Live2D parameter bridge, optional/stub integration boundary, fallback behavior, diagnostics, and tests.

It intentionally does not include face tracking, camera tracking, physics tuning, expressions, body motion, a character editor, marketplace, `.aiskin`, VRM, or bundled Live2D assets.
