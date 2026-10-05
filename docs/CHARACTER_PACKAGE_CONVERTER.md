# Character package converter

Store characters are made by other creators and arrive as ZIP files in many
layouts. The converter turns each one into AITuber's unified character format
**on the user's phone** (creators usually forbid redistribution, so AITuber never
hosts converted models).

Code: `app/src/main/java/com/aituber/poc/character/live2d/install/`

## Store download sources

| Store entries | What "下載" does |
|---|---|
| Arch Chan (GitHub) | Fetches the 7 runtime files from raw GitHub, installs, opens the character |
| Live2D official, 8 models (Hiyori, Mao, Mark, Ren, Rice, Wanko, Koharu, Kei) | Fetches the runtime files from Live2D Inc.'s own GitHub repos (pinned commit), installs, opens the character |
| Live2D official, 3 models already in the APK (Haru, Tororo, Hijiki) | "使用" selects the built-in character; nothing is downloaded |
| Live2D official, 4 models waiting for rights (Zundamon, Hatsune Miku, Jin Natori, Tsumiki Harugasa) | Shows "授權確認中"; not downloadable |
| Live2D official, the other 14 | In-app page opens the Live2D sample page and picks the ZIP next to the character's name; unverified — replace with direct URLs when known |
| BOOTH | In-app page; one-time BOOTH login; free-download link picked automatically |

## Flow

1. Store card: one "下載" button (see the table above). Direct files and direct
   ZIPs download in the background; other sources open `StoreDownloadActivity`,
   whose download listener hands the ZIP straight to the installer.
   "匯入已下載的角色 ZIP" still imports any ZIP via the system file picker.
2. `CharacterPackageInstaller` copies the ZIP to cache, runs the converter into a
   staging folder, then renames it to `filesDir/characters/<id>/`.
3. `InstalledCharacterRegistry` scans that folder and feeds the profiles into
   `Live2DCharacterProfiles.all`, so the home carousel, the overlay service and the
   saved selection see them without an APK rebuild. The new character is selected
   and shown on the home stage.

Ids: `store_<catalog id>` for store installs, `local_<name>_<hash>` for imports.

## Output format

```
filesDir/characters/<id>/
  aituber_character.json        manifest (CharacterManifest)
  model/model.model3.json       rewritten to the names below
  model/model.moc3
  model/textures/texture_00.png
  model/model.physics3.json     model.pose3.json  model.cdi3.json  model.userdata3.json
  model/motions/motion_000.motion3.json
  model/expressions/expression_00.exp3.json
  docs/creator_doc_00.txt       creator readme / license files, if any
```

## What the converter fixes

| Problem in creator packages | Handling |
|---|---|
| Shift_JIS / GBK file names in the ZIP | Decoded; references also retried in other charsets |
| `\` paths, wrong letter case, files referenced from the ZIP root | Resolved |
| Several models in one ZIP | Picks the complete one (moc3 + all textures present) |
| Idle group named `idle` / `待機` / VTS `IdleAnimation` | Renamed to `Idle` (the only group the runtime plays) |
| motion3 / physics3 `Meta` counts wrong | Recomputed (the SDK sizes buffers from them) |
| physics `VertexIndex` out of range, missing arrays | Physics disabled with a warning |
| Numbers like `1e-05`, `[0, 1]`, `1 }` | Rewritten; the Cubism JSON parser only accepts a number followed by `,` or newline |
| Japanese / emoji file names | Every file renamed to ASCII (JNI-safe) |
| Unknown parameter names | `ParameterMapper`: VTS settings → LipSync/EyeBlink groups → ids found in cdi3/motions/physics/moc3 → defaults |
| Missing mouth parameter | Installs anyway, warns "可能不會動嘴" |

Rejected with a clear message: RAR / 7z, Cubism 2 (`.moc`), editor-only `.cmo3`,
Live2DViewerEX `.lpk`, VRM / Inochi2D, moc3 newer than the runtime, missing moc3 or
textures, textures over 8192 px.

Parameter ids in the manifest may be `A|B|C`; the native runtime uses the first id
the model actually has.

## Native runtime changes (`AituberLive2DNative.cpp`)

- `nativeInitializeV2`: candidate parameter lists, head X/Y ids, per-model layout.
  The old `nativeInitialize` is still exported.
- `nativeApiVersion()` = 2. Kotlin checks it: with an older prebuilt `.so`,
  downloaded characters are hidden and the store refuses to install.
- Every JSON is validated with the Cubism parser before the SDK uses it; moc3 and
  motion3 consistency checks are on; physics Meta/VertexIndex are checked.
- File size caps; texture size checked before decoding and downscaled to the GPU
  limit; no mipmaps for non-power-of-two textures.
- C++ exceptions no longer cross JNI (load and draw).

**The prebuilt `app/src/main/jniLibs/arm64-v8a/libaituber_live2d.so` (2026-09-18)
predates this and must be rebuilt** from source with the Cubism SDK
(`live2d.sdk.dir` in `local.properties`) before downloaded characters work.

## Tests

`app/src/test/java/com/aituber/poc/character/live2d/install/` — synthetic
packages (Shift_JIS/GBK names, broken Meta, VTS mapping, unsupported formats).
Also verified by hand on Arch Chan (GitHub) and the Haru / Tororo / Hijiki /
Loaf Dog packages: all converted JSON parses with the Cubism SDK parser and
repaired Meta counts equal the Cubism Editor originals.
