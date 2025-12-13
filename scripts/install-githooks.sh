#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

git config core.hooksPath .githooks

if [[ -f .githooks/pre-commit ]]; then
  chmod +x .githooks/pre-commit || true
fi

echo "Git hooks installed (core.hooksPath=.githooks)."