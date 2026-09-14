---
name: plan-task
description: Turn a feature request or bug into a PLAN.md task with files-to-read, steps, and acceptance criteria. Interviews the user first.
disable-model-invocation: true
---

Create a new task in `PLAN.md` for: $ARGUMENTS

## Current PLAN.md
!`sed -n '1,80p' PLAN.md 2>/dev/null || echo "(no PLAN.md)"`

## Procedure

1. Read `AGENTS.md`. Explore the code the task touches (use the `explorer` subagent for
   anything wider than 3 files). Do not edit anything.
2. Interview the user with `AskUserQuestion` — only the hard parts: ambiguous scope, edge
   cases, tradeoffs, what is explicitly out of scope. Skip obvious questions. Stop when
   the plan has no open questions.
3. Write the task into `PLAN.md` using the existing task template:
   - Title: short imperative.
   - Status: `pending` (or `in progress` if the user wants to start now — then no other
     task may be `in progress`).
   - One paragraph of intent; hard constraints in bold.
   - Files to read, with why.
   - Steps: concrete, checkable, ordered.
   - Acceptance: observable conditions + `<VERIFY_COMMAND>` exits 0.
4. Show the user the task and stop. Do not start implementing.
