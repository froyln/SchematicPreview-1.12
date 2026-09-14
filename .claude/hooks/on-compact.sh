#!/bin/bash
# SessionStart (startup|resume|compact): re-inject the current task and repo state.
# stdout is appended to Claude's context.
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
echo "## Session context (auto-injected by .claude/hooks/on-compact.sh)"
echo "Canary rule is active: begin every response with \"${CLAUDE_CANARY:-Felix}\"."
if [ -f PLAN.md ]; then
  echo
  echo "### Current PLAN.md task"
  # print from the first "in progress" task header to the next "---", max 40 lines
  awk '/^## Task:/{h=$0} /\*\*Status:\*\* in progress/{p=1; print h} p{print} p&&/^---/{exit}' PLAN.md | head -n 40
fi
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo
  echo "### Git"
  echo "branch: $(git branch --show-current 2>/dev/null)"
  git status --short 2>/dev/null | head -n 20
  echo "recent:"
  git log --oneline -5 2>/dev/null
fi
exit 0
