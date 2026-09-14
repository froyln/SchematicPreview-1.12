#!/bin/bash
# Stop: refuse to end the turn while the project's verify command fails.
# Opt-in: set CLAUDE_VERIFY_ON_STOP=1 (settings.local.json → env). Change-gated: skips when
# the working tree is clean. Reads the command from AGENTS.md "## Verification" code block
# (first line), or CLAUDE_VERIFY_COMMAND if set.
INPUT=$(cat)
[ "${CLAUDE_VERIFY_ON_STOP:-0}" = "1" ] || exit 0
[ "$(printf '%s' "$INPUT" | jq -r '.stop_hook_active // false')" = "true" ] && exit 0

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0
if git diff --quiet && git diff --cached --quiet && [ -z "$(git ls-files --others --exclude-standard)" ]; then
  exit 0   # nothing changed, nothing to verify
fi

CMD="${CLAUDE_VERIFY_COMMAND:-}"
if [ -z "$CMD" ] && [ -f AGENTS.md ]; then
  CMD=$(awk '/^## Verification/{f=1;next} f&&/^```/{c++;next} f&&c==1&&NF{print;exit}' AGENTS.md | sed 's/[[:space:]]*#.*$//')
fi
case "$CMD" in ""|*"<"*">"*) exit 0 ;; esac   # unset or still a placeholder

LOG=$(mktemp)
if bash -c "$CMD" >"$LOG" 2>&1; then
  rm -f "$LOG"; exit 0
fi
TAIL=$(tail -n 40 "$LOG"); rm -f "$LOG"
jq -n --arg cmd "$CMD" --arg out "$TAIL" '{
  decision: "block",
  reason: ("Verification failed: `" + $cmd + "` exited non-zero. Fix the root cause (do not skip or silence the check), re-run, then stop.\n\n" + $out)
}'
exit 0
