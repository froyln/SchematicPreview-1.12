#!/bin/bash
# PreToolUse (Bash): deny destructive commands. Backstop for permissions.deny — catches
# forms that slip past prefix rules (subshells, chained commands, paths).
INPUT=$(cat)
CMD=$(printf '%s' "$INPUT" | jq -r '.tool_input.command // empty')
[ -z "$CMD" ] && exit 0

PATTERNS=(
  'rm[[:space:]]+-[a-zA-Z]*r[a-zA-Z]*f'    # rm -rf, rm -fr, rm -Rf ...
  'rm[[:space:]]+-[a-zA-Z]*f[a-zA-Z]*r'
  'git[[:space:]]+push[[:space:]].*(--force|-f\b)'
  'git[[:space:]]+reset[[:space:]]+--hard'
  'git[[:space:]]+clean[[:space:]]'
  'git[[:space:]]+checkout[[:space:]]+--[[:space:]]+\.'
  'git[[:space:]]+branch[[:space:]]+-D'
  'DROP[[:space:]]+(TABLE|DATABASE|SCHEMA)'
  'TRUNCATE[[:space:]]+TABLE'
  'chmod[[:space:]]+(-R[[:space:]]+)?777'
  'mkfs\.'
  'dd[[:space:]]+if='
  '>[[:space:]]*/dev/sd'
  'curl[^|]*\|[[:space:]]*(ba)?sh'
  'wget[^|]*\|[[:space:]]*(ba)?sh'
)

for re in "${PATTERNS[@]}"; do
  if printf '%s' "$CMD" | grep -qiE "$re"; then
    jq -n --arg re "$re" '{
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        permissionDecision: "deny",
        permissionDecisionReason: ("Destructive command blocked by .claude/hooks/block-destructive.sh (pattern: " + $re + "). If this is intended, the user must run it manually.")
      }
    }'
    exit 0
  fi
done
exit 0
