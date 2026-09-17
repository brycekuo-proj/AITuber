# Tororo & Hijiki Live2D Runtime

AITuber uses the Live2D Cubism sample characters Tororo (white cat) and Hijiki (black cat) as local Android runtime validation characters.

## Android asset layout

The local build expects the complete runtime folders at:

- `app/src/main/assets/live2d/tororo/`
- `app/src/main/assets/live2d/hijiki/`

Each package must include its `.model3.json`, `.moc3`, texture, `.physics3.json`, `.pose3.json`, `.cdi3.json`, and referenced motion files.

These raw Live2D sample-model files are intentionally ignored by Git and must not be committed to the public repository. Obtain the sample data through Live2D's official Tororo & Hijiki sample page and accept the applicable Live2D Free Material License Agreement and Cubism Sample Data Terms of Use before use.

Official sample page:
https://www.live2d.com/en/learn/sample/tororo-hijiki/

License references:
https://www.live2d.com/eula/live2d-free-material-license-agreement_en.html
https://www.live2d.com/eula/live2d-sample-model-terms_en.html

## Runtime profiles

- Tororo profile id: `tororo`
- Hijiki profile id: `hijiki`
- Mouth: `PARAM_MOUTH_OPEN_Y`
- Eyes: `PARAM_EYE_L_OPEN`, `PARAM_EYE_R_OPEN`
- Breath: `PARAM_BREATH`
- Head: `PARAM_ANGLE_X`, `PARAM_ANGLE_Y`, `PARAM_ANGLE_Z`
- Both profiles expose Idle motions, Physics, and Pose.

## Packaging rule

The AITuber repository keeps licensed model binaries out of Git via `.gitignore`. They may be staged locally into the Android assets directory for an APK build. Before public release, verify the current Live2D terms and include the copyright notice required by Live2D in the appropriate application/store description.

## 2026-09-17 validation

A local `assembleDebug` build succeeded with both cat packages staged. The APK contained 15 Tororo runtime files and 15 Hijiki runtime files, and contained no `xianxia` asset entries.
