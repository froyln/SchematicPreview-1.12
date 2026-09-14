---
name: review
description: Adversarial review of the current diff in a fresh context, checked against PLAN.md acceptance and AGENTS.md security invariants. Reports only correctness, requirement gaps, scope creep, and security.
context: fork
agent: reviewer
---

Review the current change. Base: $ARGUMENTS (default: uncommitted changes — run
`git diff` and `git diff --cached`; if empty, review `git diff HEAD~1`).

Check it against the `in progress` task in `PLAN.md` (Steps and Acceptance) and the
Security invariants in `AGENTS.md`. Report only gaps that affect correctness, stated
requirements, scope, or security. Style is out of scope. End with
`VERDICT: ship | fix first`.
