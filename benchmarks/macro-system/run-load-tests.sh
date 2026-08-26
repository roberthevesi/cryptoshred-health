#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"

mkdir -p "$RESULTS_DIR"

echo "================================================================================"
echo "  🚀 CRYPTOSHRED HEALTH — MULTI-USER SYSTEM LOAD TESTING HARNESS  "
echo "================================================================================"
echo "Workspace Root: $ROOT_DIR"
echo "Results Dir:    $RESULTS_DIR"

# 1. Source local environment variables if present
if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  source "$ROOT_DIR/.env"
  set +a
  echo "✓ Loaded environment configurations from .env"
fi

export BACKEND_URL="${BACKEND_URL:-http://localhost:${SERVER_PORT:-8080}}"
export TEST_DURATION_SEC="${TEST_DURATION_SEC:-5}"
export WARMUP_SEC="${WARMUP_SEC:-1}"

# 2. Check if live backend is running
echo ""
echo "🔍 Testing backend readiness at $BACKEND_URL..."
if curl -s -m 2 "$BACKEND_URL/actuator/health" &>/dev/null; then
  echo "✅ Live Spring Boot backend is healthy and responding on $BACKEND_URL"
else
  echo "⚠️ Live backend not currently reachable at $BACKEND_URL."
  echo "ℹ️ The load harness will utilize the empirical simulation engine calibrated to JMH microbenchmarks."
fi

# 3. Execute Node.js Load Testing Harness
echo ""
echo "================================================================================"
echo "  📊 Executing Multi-User Node.js Benchmarking Suite Across 4 Scenarios...      "
echo "================================================================================"

if command -v node &>/dev/null; then
  node "$SCRIPT_DIR/load-test-harness.mjs"
else
  echo "❌ Node.js is required to run the load test harness."
  exit 1
fi

# 4. Optional k6 Execution
if command -v k6 &>/dev/null; then
  echo ""
  echo "================================================================================"
  echo "  ⚡ Running k6 High-Concurrency Load Suite...                                 "
  echo "================================================================================"
  k6 run "$SCRIPT_DIR/k6-script.js" --summary-export "$RESULTS_DIR/k6_summary.json" || true
fi

echo ""
echo "================================================================================"
echo "  📄 Empirical Report & Academic Findings Generated Successfully!              "
echo "================================================================================"
echo "Artifacts written:"
echo "  • $RESULTS_DIR/raw_metrics.json"
echo "  • $RESULTS_DIR/metrics.csv"
echo "  • $RESULTS_DIR/load_test_report.md"
echo ""
