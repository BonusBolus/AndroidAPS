---
name: upstream-diff-check
description: Check the BonusBolus/AndroidAPS fork against upstream nightscout/AndroidAPS master before editing a given area of the codebase - reports how far behind the fork is and whether upstream has touched the same files recently. Always run this before starting a non-trivial change, and especially before touching insulin dosing, safety constraints, algorithms (OpenAPS/AutoISF/determine-basal), pump communication, or CGM/glucose processing, since unnoticed upstream changes there are the highest-risk kind to miss. Trigger this whenever the user asks to implement, port, or modify a feature in AndroidAPS, asks "is this area safe to touch", or asks to check upstream status.
---

# upstream-diff-check

The full procedure is in `AI_agents/skills/upstream-diff-check/SKILL.md`. Read that file completely now and follow it exactly.
