---
name: feature-handoff
description: Package a finished (or paused) AndroidAPS feature-branch change for handoff, since the user does all building, testing, merging, and pushing themselves and the agent never pushes. Use this when a feature is ready to hand off, when explicitly asked to "package this up", "give me the patch", "prep this for testing", or when pausing mid-feature to switch tasks. Produces a committed feature branch, a summary, and the exact git commands to run locally. Never invoke this automatically mid-task, only when the user is ready to receive the change.
---

# feature-handoff

The full procedure is in `AI_agents/skills/feature-handoff/SKILL.md`. Read that file completely now and follow it exactly.
