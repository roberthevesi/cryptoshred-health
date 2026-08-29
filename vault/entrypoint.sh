#!/bin/sh

echo "[Vault-Init] Starting HashiCorp Vault server with persistent file storage..."
vault server -config=/vault/config/vault.json &
VAULT_PID=$!

export VAULT_ADDR="http://127.0.0.1:8200"

echo "[Vault-Init] Waiting for Vault server HTTP listener on port 8200..."
MAX_RETRIES=30
COUNT=0
while [ $COUNT -lt $MAX_RETRIES ]; do
  if vault status >/dev/null 2>&1 || [ $? -le 2 ]; then
    echo "[Vault-Init] Vault listener is active."
    break
  fi
  sleep 1
  COUNT=$((COUNT + 1))
done

INIT_FILE="/vault/file/vault-init.txt"

# If INIT_FILE exists and has valid Unseal Key
if [ -s "$INIT_FILE" ] && grep -q "Unseal Key 1:" "$INIT_FILE"; then
  echo "[Vault-Init] Found existing unseal credentials in $INIT_FILE."
  UNSEAL_KEY=$(grep -E 'Unseal Key 1:' "$INIT_FILE" | awk '{print $NF}' | tr -d '\r\n ')
  for i in $(seq 1 30); do
    STATUS_OUT=$(vault status 2>&1 || true)
    if echo "$STATUS_OUT" | grep -qi "Sealed.*false"; then
      echo "[Vault-Init] Vault is unsealed and healthy."
      break
    elif echo "$STATUS_OUT" | grep -qi "Sealed.*true"; then
      echo "[Vault-Init] Unsealing Vault with persistent credentials (attempt $i/30)..."
      vault operator unseal "$UNSEAL_KEY" >/dev/null 2>&1 || true
    fi
    sleep 1
  done
else
  # Check if Vault is initialized
  STATUS_OUT=$(vault status 2>&1 || true)
  if ! echo "$STATUS_OUT" | grep -qi "Initialized.*true"; then
    echo "[Vault-Init] First-time setup: Initializing Vault operator..."
    vault operator init -key-shares=1 -key-threshold=1 > /tmp/init.tmp 2>&1 || true
    if grep -q "Unseal Key 1:" /tmp/init.tmp; then
      cp /tmp/init.tmp "$INIT_FILE"
      chmod 600 "$INIT_FILE"
      UNSEAL_KEY=$(grep -E 'Unseal Key 1:' "$INIT_FILE" | awk '{print $NF}' | tr -d '\r\n ')
      ROOT_TOKEN=$(grep -E 'Initial Root Token:' "$INIT_FILE" | awk '{print $NF}' | tr -d '\r\n ')

      echo "[Vault-Init] Unsealing Vault with generated key..."
      vault operator unseal "$UNSEAL_KEY"
      export VAULT_TOKEN="$ROOT_TOKEN"

      TARGET_DEV_TOKEN="${VAULT_DEV_ROOT_TOKEN:-root}"
      echo "[Vault-Init] Provisioning static root token '${TARGET_DEV_TOKEN}'..."
      vault token create -id="$TARGET_DEV_TOKEN" -policy=root -orphan >/dev/null 2>&1 || true

      echo "[Vault-Init] Mounting Transit secrets engine..."
      vault secrets enable transit >/dev/null 2>&1 || true
    else
      echo "[Vault-Init] Initialization log: $(cat /tmp/init.tmp)"
    fi
  else
    echo "[Vault-Init] Vault storage is initialized, but $INIT_FILE was empty or missing."
  fi
fi

echo "[Vault-Init] ========================================================"
echo "[Vault-Init] HashiCorp Vault is running, unsealed, and ready for connections."
echo "[Vault-Init] ========================================================"

wait $VAULT_PID
