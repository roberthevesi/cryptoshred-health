#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Delegate to unified micro-jmh runner
exec "$REPO_ROOT/benchmarks/micro-jmh/run-benchmarks.sh" "$@"
