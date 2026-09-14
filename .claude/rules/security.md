# Security rules

- Secrets never enter code, logs, commits, or chat. `.env*`, `secrets/`, `~/.ssh`,
  `~/.aws` are deny-listed for reading and editing; do not try to work around that. If a
  value is needed, ask the user to set it and reference it by env var name.
- Validate at trust boundaries (HTTP handlers, CLI args, file/DB input, third-party
  responses). Internal function boundaries do not need re-validation.
- Never disable, skip, or weaken a security check to make a test pass. If a check is
  wrong, say so and stop.
- New dependencies: name them, say why, prefer what is already installed. No `curl | sh`.
- Any change to auth, permissions, crypto, or data deletion: describe the threat model in
  the PLAN.md notes and request `/review` before finishing.
- Keep the **Security invariants** section of `AGENTS.md` true. If a change touches one,
  update the section in the same change.
