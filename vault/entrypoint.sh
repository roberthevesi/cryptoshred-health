#!/bin/sh
set -e

# Start Vault server in the background using the persistent file config
vault server -config=/vault/config/vault.json &
VAULT_PID=$!

export VAULT_ADDR="http://127.0.0.1:8200"

echo "Waiting for Vault server to start..."
until vault status > /dev/null 2>&1 || [ $? -le 2 ]; do
  sleep 1
done

# Check if Vault is initialized
if ! vault status 2>&1 | grep -q "Initialized.*true"; then
  echo "First-time setup: Initializing Vault with persistent storage..."
  INIT_OUTPUT=$(vault operator init -key-shares=1 -key-threshold=1 -format=json)
  echo "$INIT_OUTPUT" > /vault/file/vault-init.json
  chmod 600 /vault/file/vault-init.json

  UNSEAL_KEY=$(echo "$INIT_OUTPUT" | grep -o '"unseal_keys_b64": *\["[^"]*"' | sed 's/.*"\(.*\)".*/\1/')
  ROOT_TOKEN=$(echo "$INIT_OUTPUT" | grep -o '"root_token": *"[^"]*"' | sed 's/.*"\(.*\)".*/\1/')

  echo "Unsealing Vault..."
  vault operator unseal "$UNSEAL_KEY"

  export VAULT_TOKEN="$ROOT_TOKEN"

  # Create configured dev root token if specified so client services connect seamlessly
  TARGET_DEV_TOKEN="${VAULT_DEV_ROOT_TOKEN:-root}"
  if [ "$TARGET_DEV_TOKEN" != "$ROOT_TOKEN" ]; then
    echo "Creating persistent root token '${TARGET_DEV_TOKEN}'..."
    vault token create -id="$TARGET_DEV_TOKEN" -policy=root -orphan > /dev/null 2>&1 || true
  fi

  # Enable transit secrets engine
  echo "Enabling Transit secrets engine..."
  vault secrets enable transit > /dev/null 2>&1 || true
else
  echo "Vault is already initialized on persistent storage."
  if vault status 2>&1 | grep -q "Sealed.*true"; then
    if [ -f /vault/file/vault-init.json ]; then
      UNSEAL_KEY=$(grep -o '"unseal_keys_b64": *\["[^"]*"' /vault/file/vault-init.json | sed 's/.*"\(.*\)".*/\1/')
      echo "Unsealing Vault using stored credentials..."
      vault operator unseal "$UNSEAL_KEY"
    else
      echo "WARNING: Vault is sealed but /vault/file/vault-init.json was not found!"
    fi
  fi
fi

echo "===================================================="
echo " HashiCorp Vault is ready and unsealed with persistent storage."
echo "===================================================="

wait $VAULT_PID
