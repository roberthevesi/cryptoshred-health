#!/bin/bash
set -e

echo "🔓 [Vault-Unseal] Checking and unsealing all 3 Vault cluster nodes..."

for NODE in vault-1 vault-2 vault-3; do
  if docker ps --format '{{.Names}}' | grep -q "^${NODE}$"; then
    echo "🔍 Checking status of $NODE..."
    STATUS=$(docker exec $NODE vault status 2>&1 || true)
    if echo "$STATUS" | grep -qi "Sealed.*true"; then
      echo "🔑 $NODE is sealed. Unsealing..."
      docker exec $NODE sh -c '
        if [ -s /vault/file/vault-init.txt ] && grep -q "Unseal Key 1:" /vault/file/vault-init.txt; then
          KEY=$(grep -E "Unseal Key 1:" /vault/file/vault-init.txt | awk "{print \$NF}")
          vault operator unseal "$KEY"
        else
          echo "⚠️ No valid unseal key found in /vault/file/vault-init.txt"
        fi
      '
    elif echo "$STATUS" | grep -qi "Sealed.*false"; then
      echo "✅ $NODE is already unsealed."
    else
      echo "⚠️ $NODE is still starting up."
    fi
  else
    echo "ℹ️ $NODE container is not running."
  fi
done

echo "🎉 [Vault-Unseal] Complete."
