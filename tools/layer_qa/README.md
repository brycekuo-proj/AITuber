# Layer motion QA

`layer_motion_qa.py` checks whether a set of full-canvas RGBA layers can actually
**move** without showing holes, which is what a Live2D / Lite rig needs.

The older check ("reassemble every layer and compare with the master") always
passes when the layers were cut out of the master, so it cannot catch missing
hidden areas. This script instead:

- **Shift test**: nudges each layer by ±0.6% of the silhouette height in x and y
  (head tilt, breathing, hair sway, arm idle).
- **Hide test**: removes layers that disappear during animation
  (eyes when blinking, the closed-mouth layer when speaking, brows).

and counts pixels inside the silhouette that turn transparent. A layer fails if
its holes exceed 2% of its own area.

```bash
python3 tools/layer_qa/layer_motion_qa.py <layer_dir> [--out <qa_dir>]
```

- Layers must be same-size PNGs; draw order = numeric filename prefix
  (`01_hair_back.png` is furthest back).
- Alternate states (`mouth_half`, `mouth_open`) and references are excluded by default (`--exclude`).
- Output: `MOTION_QA_REPORT.md`, `motion_qa.json`, `motion_holes_overlay.png` (holes in magenta).
- Exit code 0 = PASS, 1 = FAIL. Requires only numpy and Pillow.

Limitation: it checks that hidden areas are **covered**, not that the fill looks
right. A blurry or mosaic patch under the eyes passes this test, so still look at
the underlay layers yourself.

## Baseline (2026-09-29)

`characters/demon_girl/lite_live2d_v2/full_character_25layer_v1/layers_3000`
(the set whose earlier QA reported PASS): **FAIL, 12 of 23 layers**. Worst:
`arm_R` 23%, `neck_accessory` 12%, `arm_L` / `collar_accessory` 9%, `tail` 8%,
`skirt_front` 7%, wings 5%, `hair_back` 4%, `face_base` 3.5%.
