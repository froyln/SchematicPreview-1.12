---
name: finish-task
description: Close out the in-progress PLAN.md task. Runs verification, checks acceptance criteria, updates PLAN.md and AGENTS.md, reports evidence.
disable-model-invocation: true
---

Finish the task currently marked `in progress` in `PLAN.md`.

## State
!`git status --short 2>/dev/null | head -30`

## Procedure

1. Read the task's **Acceptance** list in `PLAN.md`.
2. Run the verification command from `AGENTS.md` → Verification. Paste the exact command
   and its exit code. If it fails, fix the root cause and re-run; never skip or silence a
   check.
3. Walk each Acceptance item and state PASS / FAIL with one line of evidence (command
   output, file:line, behavior observed).
4. If any item FAILs, stop here and report; do not mark done.
5. Run `/review`. Address `fix first` findings; re-verify.
6. Update `PLAN.md`: status → `done (<sha>)` once committed (or `done (uncommitted)` if the
   user commits themselves), fill in Notes / findings, move the entry to **Done**.
7. If the change altered architecture, conventions, commands, or security invariants,
   update `AGENTS.md` in the same change.
8. Summarize in ≤ 6 lines: what shipped, evidence, anything deferred.
