#!/usr/bin/env python3
"""Motion stress QA for Live2D-style layer sets.

Why this exists
---------------
"Reassemble all layers and compare with the master" always passes when every
layer was cut out of the master: the pieces fit back together by construction.
It says nothing about whether the rig can MOVE, which is the whole point of
splitting layers. A part that moves or hides reveals whatever is underneath it,
and if nothing was painted there you see a hole.

This script simulates the motions a Lite rig actually performs and counts the
holes that appear inside the character's silhouette:

* shift test  - every layer is nudged by +/- N px in x and y
                (head tilt, breathing, hair sway, arm idle).
* hide test   - layers that disappear during animation (eyes on blink, mouth
                closed variant on speech, brows on expression swap) are removed.

A hole is a pixel inside the silhouette (at least 2x the shift distance away
from its outer edge) that was opaque in the neutral composite and becomes
transparent after the motion. Holes are counted per layer and the layer fails
if the hole area exceeds a fraction of the layer's own area.

Input: a folder of full-canvas RGBA PNGs (identical size). Draw order is the
numeric prefix in the filename (01_hair_back.png is drawn first / furthest back).

Usage
-----
    python3 layer_motion_qa.py LAYER_DIR [--out QA_DIR] [--shift-pct 0.6]
                               [--hide 'eye|mouth_closed|brow']
                               [--exclude 'mouth_half|mouth_open']
                               [--max-hole 0.02]

Exit code 0 = PASS, 1 = FAIL, 2 = input error.
Only numpy and Pillow are required.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ALPHA_SOLID = 0.5  # alpha above this counts as "covered"


def natural_key(p: Path):
    return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", p.name)]


def load_layers(layer_dir: Path, exclude: str | None, max_side: int):
    files = sorted(layer_dir.glob("*.png"), key=natural_key)
    if exclude:
        rx = re.compile(exclude, re.I)
        files = [f for f in files if not rx.search(f.stem)]
    if not files:
        raise SystemExit(f"no PNG layers found in {layer_dir}")
    size = Image.open(files[0]).size
    scale = min(1.0, max_side / max(size))
    work = (round(size[0] * scale), round(size[1] * scale))
    layers = []
    for f in files:
        im = Image.open(f)
        if im.size != size:
            raise SystemExit(f"canvas mismatch: {f.name} is {im.size}, expected {size}")
        im = im.convert("RGBA")
        if scale < 1.0:
            im = im.resize(work, Image.LANCZOS)
        layers.append((f.stem, np.asarray(im, dtype=np.float32) / 255.0))
    return layers, size, scale


def composite_alpha(layers, skip=None, shift=None):
    """Porter-Duff 'over' on alpha only; skip = index to omit, shift = (idx, dx, dy)."""
    h, w = layers[0][1].shape[:2]
    a_out = np.zeros((h, w), np.float32)
    for i, (_, arr) in enumerate(layers):
        if i == skip:
            continue
        a = arr[..., 3]
        if shift and shift[0] == i:
            a = shifted(a, shift[1], shift[2])
        a_out = a + a_out * (1.0 - a)
    return a_out


def composite_rgb(layers):
    h, w = layers[0][1].shape[:2]
    rgb = np.zeros((h, w, 3), np.float32)
    a_out = np.zeros((h, w), np.float32)
    for _, arr in layers:
        a = arr[..., 3:4]
        rgb = arr[..., :3] * a + rgb * (1.0 - a)
        a_out = arr[..., 3] + a_out * (1.0 - arr[..., 3])
    return rgb, a_out


def shifted(a, dx, dy):
    out = np.zeros_like(a)
    h, w = a.shape
    xs, xd = (slice(0, w - dx), slice(dx, w)) if dx >= 0 else (slice(-dx, w), slice(0, w + dx))
    ys, yd = (slice(0, h - dy), slice(dy, h)) if dy >= 0 else (slice(-dy, h), slice(0, h + dy))
    out[yd, xd] = a[ys, xs]
    return out


def clean_holes(mask):
    """Drop 1-2 px antialiasing slivers so only real holes are counted."""
    im = Image.fromarray((mask * 255).astype(np.uint8))
    im = im.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3))
    return np.asarray(im) > 127


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("layer_dir", type=Path)
    ap.add_argument("--out", type=Path, default=None, help="QA output folder (default: <layer_dir>/../qa_motion)")
    ap.add_argument("--shift-pct", type=float, default=0.6,
                    help="shift distance as %% of the character's silhouette height (default 0.6)")
    ap.add_argument("--hide", default=r"eye|mouth_closed|brow",
                    help="regex of layers that disappear during animation (hide test)")
    ap.add_argument("--exclude", default=r"mouth_half|mouth_open|master|reference",
                    help="regex of layers not part of the neutral pose (alternate states, references)")
    ap.add_argument("--max-hole", type=float, default=0.02,
                    help="FAIL if holes exceed this fraction of the layer's own area (default 0.02)")
    ap.add_argument("--max-side", type=int, default=1500, help="analysis resolution (default 1500)")
    args = ap.parse_args()

    if not args.layer_dir.is_dir():
        print(f"not a folder: {args.layer_dir}", file=sys.stderr)
        return 2
    out = args.out or args.layer_dir.parent / "qa_motion"
    out.mkdir(parents=True, exist_ok=True)

    layers, full_size, scale = load_layers(args.layer_dir, args.exclude, args.max_side)
    base_a = composite_alpha(layers)
    solid = base_a > ALPHA_SOLID
    ys = np.where(solid.any(axis=1))[0]
    sil_h = (ys[-1] - ys[0] + 1) if len(ys) else base_a.shape[0]
    d = max(2, round(sil_h * args.shift_pct / 100.0))
    hide_rx = re.compile(args.hide, re.I) if args.hide else None
    # Only judge pixels well inside the silhouette: a part on the outer edge that
    # moves by d px legitimately uncovers a d-wide strip of background.
    k = 2 * (2 * d) + 1
    interior = np.asarray(Image.fromarray(solid.astype(np.uint8) * 255).filter(ImageFilter.MinFilter(k))) > 127

    results, hole_union = [], np.zeros_like(solid)
    for i, (name, arr) in enumerate(layers):
        area = int((arr[..., 3] > ALPHA_SOLID).sum())
        if area == 0:
            results.append({"layer": name, "area_px": 0, "status": "EMPTY"})
            continue
        tests = {}
        layer_holes = np.zeros_like(solid)
        for dx, dy in ((d, 0), (-d, 0), (0, d), (0, -d)):
            a = composite_alpha(layers, shift=(i, dx, dy))
            holes = clean_holes(interior & (a < ALPHA_SOLID))
            tests[f"shift({dx:+d},{dy:+d})"] = int(holes.sum())
            layer_holes |= holes
        if hide_rx and hide_rx.search(name):
            a = composite_alpha(layers, skip=i)
            holes = clean_holes(interior & (a < ALPHA_SOLID))
            tests["hide"] = int(holes.sum())
            layer_holes |= holes
        worst = max(tests.values())
        frac = worst / area
        results.append({
            "layer": name,
            "area_px": area,
            "worst_hole_px": worst,
            "worst_hole_fraction": round(frac, 4),
            "tests": tests,
            "status": "PASS" if frac <= args.max_hole else "FAIL",
        })
        hole_union |= layer_holes

    failed = [r for r in results if r["status"] == "FAIL"]
    verdict = "FAIL" if failed else "PASS"

    # Visual: neutral composite with every revealed hole painted magenta.
    rgb, a = composite_rgb(layers)
    bg = np.full_like(rgb, 0.78)
    img = rgb + bg * (1.0 - a[..., None])
    img[hole_union] = (1.0, 0.0, 1.0)
    vis = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    ImageDraw.Draw(vis).text((12, 12), f"{verdict}  magenta = holes revealed by motion (shift {d}px @ analysis res)",
                             fill=(0, 0, 0))
    vis.save(out / "motion_holes_overlay.png")

    report = {
        "layer_dir": str(args.layer_dir),
        "canvas": list(full_size),
        "analysis_scale": round(scale, 4),
        "shift_px_analysis": d,
        "shift_px_full_canvas": round(d / scale),
        "max_hole_fraction": args.max_hole,
        "verdict": verdict,
        "layers": results,
    }
    (out / "motion_qa.json").write_text(json.dumps(report, indent=2, ensure_ascii=False))

    lines = [
        "# Motion stress QA",
        "",
        f"- Layers: `{args.layer_dir}` ({len(layers)} used, excluded: `{args.exclude}`)",
        f"- Shift test: +/-{round(d / scale)} px on the full canvas ({args.shift_pct}% of silhouette height)",
        f"- Hide test: layers matching `{args.hide}`",
        f"- Fail threshold: holes > {args.max_hole:.0%} of the layer's own area",
        f"- **VERDICT: {verdict}** ({len(failed)} of {len(results)} layers fail)",
        "",
        "| Layer | Worst hole | % of layer | Status |",
        "|---|---:|---:|---|",
    ]
    for r in results:
        if r["status"] == "EMPTY":
            lines.append(f"| {r['layer']} | - | - | EMPTY |")
        else:
            lines.append(f"| {r['layer']} | {r['worst_hole_px']} | {r['worst_hole_fraction']:.1%} | {r['status']} |")
    lines += ["", "See `motion_holes_overlay.png`: magenta pixels are background showing through after motion.",
              "Fix a FAIL by painting the hidden area on the layer underneath (not on the moving layer)."]
    (out / "MOTION_QA_REPORT.md").write_text("\n".join(lines) + "\n")

    print("\n".join(lines))
    return 0 if verdict == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
