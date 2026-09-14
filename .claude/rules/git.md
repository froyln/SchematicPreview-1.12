# Git and commit rules

- **No AI attribution anywhere.** No `Co-Authored-By` assistant trailer, no session link,
  no mention of the model or tool in subject or body. `.claude/settings.json` disables the
  automatic trailer; if one appears anyway, strip it before committing.
- **Commit style:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `build:`, `docs:`),
  imperative subject ≤ 72 chars, optional scope = package (`feat(render): ...`). Body
  explains *why*, not *what* the diff shows.
- **One change per commit.** No drive-by reformatting, no unrelated cleanups, no new
  dependencies without asking.
- **Branches:** `feat/<short-name>`, `fix/<short-name>`; one branch per PLAN.md task.
  Never commit directly to `main` unless the user says so.
- **Never** force-push, rewrite shared history, or delete branches. Pushing at all requires
  an explicit ask (permission rule prompts for it).
- Use `gh` for PRs and issues. PR description: what changed, why, how it was verified.
- Before committing: run `./gradlew build` (JDK 8); stage only the files for this change
  (`git add <paths>`, not `git add -A`). Never commit `libs/*.litemod`, `minecraft/`
  (run dir) or `build/`.
