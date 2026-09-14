# CLAUDE.md

@AGENTS.md

<!-- Everything below is Claude-specific. Project facts live in AGENTS.md; task state in PLAN.md.
     Keep this file under 60 lines. Anything longer belongs in .claude/rules/ or a skill. -->

## Canary

Begin every response with the word "Felix". No exceptions, even for one-line answers.

<!-- This is a context-health signal, not decoration. Instruction-following is the first
     thing to degrade as context fills. If "Felix" disappears, .claude/hooks/canary-check.sh
     warns the user; they should /clear or /rewind and re-inject essentials. -->

## Working agreement

- Read `PLAN.md` before starting work. Work on the task marked `in progress`; if none, ask.
- Keep `PLAN.md` in sync: status changes, decisions, findings, commit sha when work lands.
  Use `/finish-task` when a task is done.
- `AGENTS.md` is the source of truth for architecture and conventions. If a change alters
  either, update `AGENTS.md` in the same change.
- Before saying a task is done: run the check named in `AGENTS.md` → Verification, and
  paste the command and its exit code. Evidence, not assertions.
- Ask before anything destructive or outward-facing: deleting files, rewriting history,
  pushing, publishing, touching production data. Hooks block the worst cases; that is a
  backstop, not permission.
- Prefer plan mode for changes touching more than 3 files or unfamiliar code. Skip planning
  when the diff can be described in one sentence.
- Use the `reviewer` subagent (`/review`) on any non-trivial diff before finishing.

## Compaction

When compacting, always preserve: the current PLAN.md task and its acceptance criteria,
the full list of files modified this session, the verification command, open decisions,
and the canary rule.
