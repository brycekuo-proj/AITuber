# Xianxia Static Hair Transition Modes

Preview-only timing proposal for Step 7.0.

## DIRECT
- Immediate bitmap switch.
- Baseline/control mode.
- Duration: 0ms.

## CROSSFADE
- Keep previous full replacement state visible while fading the next full replacement state in.
- Duration: 500ms.
- Deliberately exaggerated for A16 visual verification.

## BRIDGE
- Route non-base directional changes through BASE.
- FLOAT_A -> BASE: 300ms
- BASE hold: 200ms
- BASE -> FLOAT_B: 300ms
- Same mirrored timing for FLOAT_B -> BASE -> FLOAT_A.
- Recommended for A <-> B because it reads as hair returning to neutral before drifting the other way.
- BASE -> FLOAT_A/B also uses the same pipeline timing so manual test buttons visibly exercise the transition path.
