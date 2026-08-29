#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
RESULTS_DIR="$SCRIPT_DIR/results"

mkdir -p "$RESULTS_DIR"

cd "$BACKEND_DIR"

# 1. Ensure JAVA_HOME is configured
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -d "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
    elif [ -d "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
    fi
fi

if [ -n "${JAVA_HOME:-}" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# 2. Source local environment variables for live Vault and DB connections
if [ -f "$BACKEND_DIR/.env" ]; then
    set -a
    source "$BACKEND_DIR/.env"
    set +a
elif [ -f "$REPO_ROOT/.env" ]; then
    set -a
    source "$REPO_ROOT/.env"
    set +a
fi

echo "============================================================"
echo "  🚀 Compiling CryptoShred Health JMH Benchmark Suite...   "
echo "============================================================"
./mvnw test-compile

# 3. Build classpath manifest
echo "  📦 Generating benchmark classpath..."
./mvnw dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=target/mvn_test_cp.txt -q
python3 -c '
with open("target/mvn_test_cp.txt") as f:
    cp = f.read().strip()
with open("target/cp.txt", "w") as f:
    f.write("target/test-classes:target/classes:" + cp)
'

echo ""
echo "============================================================"
echo "  📊 Executing JMH Microbenchmarks...                      "
echo "============================================================"

RESULTS_JSON="$RESULTS_DIR/results.json"

java -cp @target/cp.txt \
    com.roberthevesi.cryptoshred_health.benchmarks.BenchmarkRunner \
    -rf json -rff "$RESULTS_JSON" \
    "$@"

echo ""
echo "============================================================"
echo "  📈 Generating Dissertation Tables & Summary...           "
echo "============================================================"

if command -v python3 &>/dev/null; then
    python3 "$SCRIPT_DIR/parse_results.py" "$RESULTS_JSON"
else
    echo "Python 3 not found. Raw results saved to $RESULTS_JSON"
fi

echo ""
echo "✅ Benchmarking complete! Results saved in benchmarks/micro-jmh/results/"
