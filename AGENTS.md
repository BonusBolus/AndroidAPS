# AGENTS.md

All project rules for AI agents live in `AI_agents/AGENTS.md`. **Read it completely before doing any task in this repo, and follow it.**

Non-negotiables (full detail in that file):

- Safety above all else. Never bypass or weaken a safety mechanism.
- Never work on `master`, and never push or merge. Use `feature/*` branches; the user pushes.
- Before editing dosing, constraints, algorithms, pump, or CGM code: run the `upstream-diff-check` skill first and the `safety-review` skill after (`AI_agents/skills/`).
