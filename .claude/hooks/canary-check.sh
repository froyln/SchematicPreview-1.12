#!/bin/bash
# Stop: warn the user when the canary word is missing from the last reply.
# The canary ("Felix" by default; override with CLAUDE_CANARY) is the cheapest instruction
# in CLAUDE.md. When it drops, context is degraded — /clear or /rewind and re-inject
# essentials. This hook never blocks; it only surfaces a warning.
INPUT=$(cat)
MSG=$(printf '%s' "$INPUT" | jq -r '.last_assistant_message // ""')
[ -z "$MSG" ] && exit 0            # tool-only turns have no text; skip
CANARY="${CLAUDE_CANARY:-Felix}"

if printf '%s' "$MSG" | head -c 300 | grep -q "$CANARY"; then
  exit 0
fi

jq -n --arg c "$CANARY" '{
  systemMessage: ("⚠ CANARY MISSING: reply did not start with \"" + $c + "\". Context may be degraded. Consider /clear (or /rewind) and re-inject: current PLAN.md task, files touched, decisions made.")
}'
exit 0
