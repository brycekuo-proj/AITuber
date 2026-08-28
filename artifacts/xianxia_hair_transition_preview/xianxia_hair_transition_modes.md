# Xianxia Static Hair Transition Modes

Preview-only timing proposal for Step 7.0.

## DIRECT
- Immediate bitmap switch.
- Baseline/control mode.
- Duration: 0ms.

## CROSSFADE
- Keep previous full replacement state visible while fading the next full replacement state in.
- Duration: 120ms.
- Intended for BASE <-> FLOAT_A/B only; keep short to avoid double-hair.

## BRIDGE
- Route non-base directional changes through BASE.
- FLOAT_A -> BASE: 60ms
- BASE hold: 30ms
- BASE -> FLOAT_B: 100ms
- Same mirrored timing for FLOAT_B -> BASE -> FLOAT_A.
- Recommended for A <-> B because it reads as hair returning to neutral before drifting the other way.
