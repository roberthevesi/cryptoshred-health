#!/bin/sh

echo "[Vault-Init] Starting HashiCorp Vault server with persistent file storage..."
vault server -config=/vault/config/vault.json &
VAULT_PID=$!

export VAULT_ADDR="http://127.0.0.1:8200"

echo "[Vault-Init] Waiting for Vault server HTTP listener on port 8200..."
MAX_RETRIES=30
COUNT=0
while [ $COUNT -lt $MAX_RETRIES ]; do
  STATUS=$(vault status -format=json 2>/dev/null || true)
  if [ -n "$STATUS" ]; then
    echo "[Vault-Init] Vault listener is active."
    break
  fi
  sleep 1
  COUNT=$((COUNT + 1))
done

STATUS=$(vault status -format=json 2>/dev/null || true)
IS_INIT=$(echo "$STATUS" | grep -o '"initialized": *true' || true)
IS_SEALED=$(echo "$STATUS" | grep -o '"sealed": *true' || true)

if [ -z "$IS_INIT" ]; then
  echo "[Vault-Init] First-time setup: Initializing Vault operator..."
  INIT_OUTPUT=$(vault operator init -key-shares=1 -key-threshold=1 -format=json)
  echo "$INIT_OUTPUT" > /vault/file/vault-init.json
  chmod 600 /vault/file/vault-init.json

  UNSEAL_KEY=$(echo "$INIT_OUTPUT" | grep -o '"unseal_keys_b64": *\["[^"]*"' | sed 's/.*"\(.*\)".*/\1/')
  ROOT_TOKEN=$(echo "$INIT_OUTPUT" | grep -o '"root_token": *"[^"]*"' | sed 's/.*"\(.*\)".*/\1/')

  echo "[Vault-Init] Unsealing Vault..."
  vault operator unseal "$UNSEAL_KEY"

  export VAULT_TOKEN="$ROOT_TOKEN"

  TARGET_DEV_TOKEN="${VAULT_DEV_ROOT_TOKEN:-root}"
  if [ "$TARGET_DEV_TOKEN" != "$ROOT_TOKEN" ]; then
    echo "[Vault-Init] Provisioning static root token '${TARGET_DEV_TOKEN}'..."
    vault token create -id="$TARGET_DEV_TOKEN" -policy=root -orphan >/dev/null 2>&1 || true
  fi

  echo "[Vault-Init] Mounting Transit secrets engine..."
  vault secrets enable transit >/dev/null 2>&1 || true
else
  echo "[Vault-Init] Vault is already initialized."
  if [ -n "$IS_SEALED" ]; then
    if [ -f /vault/file/vault-init.json ]; then
      UNSEAL_KEY=$(grep -o '"unseal_keys_b64": *\["[^"]*"' /vault/file/vault-init.json | sed 's/.*"\(.*\)".*/\1/')
      echo "[Vault-Init] Unsealing Vault from persistent credentials..."
      vault operator unseal "$UNSEAL_KEY"
    else
      echo "[Vault-Init] WARNING: Vault is sealed but /vault/file/vault-init.json was not found!"
    fi
  fi
fi

echo "[Vault-Init] ========================================================"
echo "[Vault-Init] HashiCorp Vault is running, unsealed, and ready for connections."
echo "[Vault-Init] ========================================================"

wait $VAULT_PID
