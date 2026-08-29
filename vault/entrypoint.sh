#!/bin/sh

NODE_NAME="${HOSTNAME:-vault-1}"

cat << EOF > /tmp/vault-config.json
{
  "storage": {
    "raft": {
      "path": "/vault/file",
      "node_id": "${NODE_NAME}"
    }
  },
  "listener": {
    "tcp": {
      "address": "0.0.0.0:8200",
      "cluster_address": "0.0.0.0:8201",
      "tls_disable": 1,
      "telemetry": {
        "unauthenticated_metrics_access": true
      }
    }
  },
  "api_addr": "http://${NODE_NAME}:8200",
  "cluster_addr": "http://${NODE_NAME}:8201",
  "telemetry": {
    "prometheus_retention_time": "30s",
    "disable_hostname": true
  },
  "default_lease_ttl": "720h",
  "max_lease_ttl": "8760h",
  "ui": true,
  "disable_mlock": false
}
EOF

echo "[Vault-Init] Starting HashiCorp Vault server with Integrated Raft Storage on ${NODE_NAME}..."
vault server -config=/tmp/vault-config.json &
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
SHARED_INIT_FILE="/vault/shared/vault-init.txt"

# If this node has a VAULT_JOIN_ADDR and is a follower node
if [ -n "$VAULT_JOIN_ADDR" ]; then
  echo "[Vault-Init] Follower node configured with VAULT_JOIN_ADDR: $VAULT_JOIN_ADDR"
  for i in $(seq 1 30); do
    STATUS_OUT=$(vault status 2>&1 || true)
    if echo "$STATUS_OUT" | grep -qi "Initialized.*false"; then
      echo "[Vault-Init] Joining Raft cluster at $VAULT_JOIN_ADDR (attempt $i)..."
      vault operator raft join "$VAULT_JOIN_ADDR" >/dev/null 2>&1 || true
    fi

    STATUS_OUT=$(vault status 2>&1 || true)
    if echo "$STATUS_OUT" | grep -qi "Sealed.*true"; then
      KEY_SOURCE=""
      if [ -s "$SHARED_INIT_FILE" ] && grep -q "Unseal Key 1:" "$SHARED_INIT_FILE"; then
        KEY_SOURCE="$SHARED_INIT_FILE"
      elif [ -s "$INIT_FILE" ] && grep -q "Unseal Key 1:" "$INIT_FILE"; then
        KEY_SOURCE="$INIT_FILE"
      fi

      if [ -n "$KEY_SOURCE" ]; then
        UNSEAL_KEY=$(grep -E 'Unseal Key 1:' "$KEY_SOURCE" | awk '{print $NF}' | tr -d '\r\n ')
        echo "[Vault-Init] Unsealing follower node with cluster credentials (attempt $i/30)..."
        vault operator unseal "$UNSEAL_KEY" >/dev/null 2>&1 || true
      fi
    elif echo "$STATUS_OUT" | grep -qi "Sealed.*false"; then
      echo "[Vault-Init] Follower Vault is unsealed and synchronized with cluster."
      break
    fi
    sleep 1
  done
else
  # Primary node (vault-1)
  STATUS_OUT=$(vault status 2>&1 || true)
  if ! echo "$STATUS_OUT" | grep -qi "Initialized.*true"; then
    echo "[Vault-Init] First-time setup: Initializing Vault cluster leader..."
    vault operator init -key-shares=1 -key-threshold=1 > /tmp/init.tmp 2>&1 || true
    if grep -q "Unseal Key 1:" /tmp/init.tmp; then
      cp /tmp/init.tmp "$INIT_FILE"
      mkdir -p /vault/shared 2>/dev/null || true
      cp /tmp/init.tmp "$SHARED_INIT_FILE" 2>/dev/null || true
      chmod 600 "$INIT_FILE"
      UNSEAL_KEY=$(grep -E 'Unseal Key 1:' "$INIT_FILE" | awk '{print $NF}' | tr -d '\r\n ')
      ROOT_TOKEN=$(grep -E 'Initial Root Token:' "$INIT_FILE" | awk '{print $NF}' | tr -d '\r\n ')

      echo "[Vault-Init] Unsealing Vault leader with generated key..."
      vault operator unseal "$UNSEAL_KEY"
      export VAULT_TOKEN="$ROOT_TOKEN"

      TARGET_DEV_TOKEN="${VAULT_DEV_ROOT_TOKEN:-root}"
      echo "[Vault-Init] Provisioning static root token '${TARGET_DEV_TOKEN}'..."
      vault token create -id="$TARGET_DEV_TOKEN" -policy=root -orphan >/dev/null 2>&1 || true

      echo "[Vault-Init] Mounting Transit secrets engine..."
      vault secrets enable transit >/dev/null 2>&1 || true
    else
      echo "[Vault-Init] Initialization error: $(cat /tmp/init.tmp)"
    fi
  elif [ -s "$INIT_FILE" ] && grep -q "Unseal Key 1:" "$INIT_FILE"; then
    echo "[Vault-Init] Found existing unseal credentials in $INIT_FILE."
    mkdir -p /vault/shared 2>/dev/null || true
    cp "$INIT_FILE" "$SHARED_INIT_FILE" 2>/dev/null || true
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
    echo "[Vault-Init] Vault storage is initialized, but $INIT_FILE was empty or missing."
  fi
fi

echo "[Vault-Init] ========================================================"
echo "[Vault-Init] HashiCorp Vault is running, unsealed, and ready for connections."
echo "[Vault-Init] ========================================================"

wait $VAULT_PID
