---
name: sync-docs
description: Check AGENTS.md and PLAN.md against the actual code and git history; report drift and propose edits. Read-only unless the user approves.
disable-model-invocation: true
---

Audit documentation drift.

## Recent history
!`git log --oneline -20 2>/dev/null`

## Procedure

1. Read `AGENTS.md`. For each section (Tech stack, Commands, Verification, Project
   structure, Architecture, Configuration, Security invariants, Conventions, Gotchas),
   check it against the code: do the commands exist in the manifest/scripts? does the
   tree match? are listed env vars actually read? Use the `explorer` subagent for wide
   searches.
2. Read `PLAN.md`. Any task `in progress` with no recent related commits? Any `done` entry
   missing its sha? More than one task `in progress`?
3. Check for placeholders left unfilled (`<...>`) in `AGENTS.md`, `CLAUDE.md`,
   `.claude/settings.json`, `.claude/rules/`.
4. Report as a table: `location — what the doc says — what the code says — proposed
   edit`. Then ask whether to apply. Apply only approved edits.
