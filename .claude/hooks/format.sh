#!/bin/bash
# PostToolUse (Edit|Write): format the edited file with whatever formatter the project has.
# Silent no-op when no formatter is installed. Never blocks.
INPUT=$(cat)
F=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')
[ -f "$F" ] || exit 0
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
has() { command -v "$1" >/dev/null 2>&1; }

case "$F" in
  *.js|*.jsx|*.ts|*.tsx|*.json|*.css|*.scss|*.md|*.yaml|*.yml|*.html|*.vue|*.svelte)
    if [ -x node_modules/.bin/biome ]; then node_modules/.bin/biome format --write "$F" >/dev/null 2>&1
    elif [ -x node_modules/.bin/prettier ]; then node_modules/.bin/prettier --write "$F" >/dev/null 2>&1
    fi ;;
  *.py)
    if has ruff; then ruff format "$F" >/dev/null 2>&1; ruff check --fix "$F" >/dev/null 2>&1
    elif has black; then black -q "$F" >/dev/null 2>&1; fi ;;
  *.go)   has gofmt && gofmt -w "$F" >/dev/null 2>&1 ;;
  *.rs)   has rustfmt && rustfmt "$F" >/dev/null 2>&1 ;;
  *.java|*.kt) : ;;   # add google-java-format / ktlint here if the project uses them
  *.sh)   has shfmt && shfmt -w "$F" >/dev/null 2>&1 ;;
esac
exit 0
