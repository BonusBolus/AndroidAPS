---
name: safety-review
description: Do a dedicated, adversarial second-pass review of a diff or change touching insulin dosing, safety constraints, algorithms (OpenAPS/AutoISF/determine-basal), pump communication, or CGM/glucose processing in AndroidAPS - looking specifically for bypassed safety checks, silently widened bounds, weakened fail-fast behavior, and unit/off-by-one errors. Always run this after writing or modifying code in those areas, even without being asked, before considering the change ready to hand off. Also trigger when the user asks to "review this for safety", "sanity check this change", "is this safe to build", or similar.
---

# safety-review

The full procedure is in `AI_agents/skills/safety-review/SKILL.md`. Read that file completely now and follow it exactly.
