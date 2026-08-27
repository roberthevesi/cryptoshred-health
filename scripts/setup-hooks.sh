#!/bin/sh
# -----------------------------------------------------------------------------
# Setup Git Hooks for CryptoShred Health
# -----------------------------------------------------------------------------

set -e

echo "🔧 Configuring git to use .githooks directory..."
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit
echo "✅ Pre-commit security hook active! All future commits will be scanned for secrets."
