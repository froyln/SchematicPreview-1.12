---
name: commit
description: Stage the files for the current task and commit with a message that follows .claude/rules/git.md. No AI attribution.
disable-model-invocation: true
allowed-tools: Bash(git status *) Bash(git diff *) Bash(git add *) Bash(git commit *) Bash(git log *) Read
---

Commit the current work. Extra instructions: $ARGUMENTS

## State
!`git status --short`

## Diff
!`git diff; git diff --cached`

## Procedure

1. Confirm the diff belongs to a single logical change. If it contains unrelated changes,
   say so and ask which to commit; do not commit everything.
2. Stage by explicit path (`git add <paths>`). Never `git add -A` or `git add .`.
3. Write the message per `.claude/rules/git.md`: subject in the project's style, body says
   *why*. No `Co-Authored-By`, no session links, no tool names.
4. `git commit`. Show the resulting `git log -1 --stat`.
5. Do not push. Pushing is a separate, user-initiated action.
