#!/bin/bash
# PreToolUse (Edit|Write): block edits to files agents must never touch.
# Exit 2 = block; stderr is shown to Claude as the reason.
INPUT=$(cat)
FILE_PATH=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')
[ -z "$FILE_PATH" ] && exit 0
FILE_PATH="${FILE_PATH//\\//}"

PROTECTED=(
  ".env"
  "secrets/"
  ".git/"
  "package-lock.json"
  "pnpm-lock.yaml"
  "yarn.lock"
  "uv.lock"
  "Cargo.lock"
  "poetry.lock"
  ".claude/settings.json"
)

for pattern in "${PROTECTED[@]}"; do
  case "$FILE_PATH" in
    *"$pattern"*)
      echo "Blocked: '$FILE_PATH' matches protected pattern '$pattern'. Lockfiles change via the package manager; secrets and settings are edited by a human." >&2
      exit 2 ;;
  esac
done
exit 0
