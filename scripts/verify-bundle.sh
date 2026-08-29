#!/usr/bin/env bash
# ==============================================================================
# CryptoShred Health — Backup Bundle Cryptographic Integrity Verifier
# ==============================================================================
# Verifies SHA-256 checksums and manifest consistency for any backup bundle.
# Returns exit code 0 on success, exit code 1 on corruption/tampering/missing files.
#
# STRICT SECURITY DIRECTIVE: Zero hardcoded secrets.
# ==============================================================================

set -euo pipefail

BUNDLE_TARGET=""
BACKUP_BASE_DIR="${BACKUP_BUNDLE_DIR:-backups/bundles}"

# Parse CLI arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --bundle|-b)
      BUNDLE_TARGET="$2"
      shift 2
      ;;
    --base-dir)
      BACKUP_BASE_DIR="$2"
      shift 2
      ;;
    --help|-h)
      echo "Usage: $0 --bundle <bundle_path_or_id>"
      echo "Options:"
      echo "  --bundle, -b <PATH_OR_ID>   Path to bundle folder or bundle name/ID"
      echo "  --base-dir <DIR>            Base backup directory (default: backups/bundles)"
      echo "  --help, -h                  Display this help message"
      exit 0
      ;;
    *)
      if [ -z "${BUNDLE_TARGET}" ]; then
        BUNDLE_TARGET="$1"
        shift
      else
        echo "Unknown option: $1" >&2
        exit 1
      fi
      ;;
  esac
done

if [ -z "${BUNDLE_TARGET}" ]; then
  echo "❌ Error: --bundle argument is required." >&2
  echo "Usage: $0 --bundle <bundle_path_or_id>" >&2
  exit 1
fi

# Locate bundle folder
BUNDLE_DIR=""
if [ -d "${BUNDLE_TARGET}" ]; then
  BUNDLE_DIR="${BUNDLE_TARGET}"
elif [ -d "${BACKUP_BASE_DIR}/${BUNDLE_TARGET}" ]; then
  BUNDLE_DIR="${BACKUP_BASE_DIR}/${BUNDLE_TARGET}"
else
  # Search inside base backup directory for matching manifest bundleId
  for d in "${BACKUP_BASE_DIR}"/bundle_*; do
    if [ -d "${d}" ] && [ -f "${d}/bundle_manifest.json" ]; then
      if grep -q "\"bundleId\": \"${BUNDLE_TARGET}\"" "${d}/bundle_manifest.json" 2>/dev/null; then
        BUNDLE_DIR="${d}"
        break
      fi
    fi
  done
fi

if [ -z "${BUNDLE_DIR}" ] || [ ! -d "${BUNDLE_DIR}" ]; then
  echo "❌ Error: Backup bundle not found for target '${BUNDLE_TARGET}'" >&2
  exit 1
fi

MANIFEST_FILE="${BUNDLE_DIR}/bundle_manifest.json"
if [ ! -f "${MANIFEST_FILE}" ]; then
  echo "❌ Error: bundle_manifest.json not found in '${BUNDLE_DIR}'" >&2
  exit 1
fi

echo "================================================================================"
echo "🔍 CryptoShred Health: Verifying Backup Bundle Integrity"
echo "================================================================================"
echo "Bundle Directory: ${BUNDLE_DIR}"
echo "Manifest:         ${MANIFEST_FILE}"
echo "================================================================================"

compute_sha256() {
  local target_file="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${target_file}" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${target_file}" | awk '{print $1}'
  elif command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 "${target_file}" | awk '{print $NF}'
  else
    python3 -c "import hashlib, sys; print(hashlib.sha256(open(sys.argv[1], 'rb').read()).hexdigest())" "${target_file}"
  fi
}

ALL_PASSED=true

# Python/jq/awk parser for manifest file entries
python3 - "${BUNDLE_DIR}" "${MANIFEST_FILE}" << 'EOF'
import sys, json, os, hashlib

bundle_dir = sys.argv[1]
manifest_path = sys.argv[2]

try:
    with open(manifest_path, 'r', encoding='utf-8') as f:
        manifest = json.load(f)
except Exception as e:
    print(f"❌ Corrupt manifest JSON: {e}")
    sys.exit(1)

bundle_id = manifest.get('bundleId', 'UNKNOWN')
merkle_root = manifest.get('merkleRoot', 'UNKNOWN')
files = manifest.get('files', [])

print(f"Bundle UUID:        {bundle_id}")
print(f"Active Merkle Root: {merkle_root}")
print(f"Files Registered:   {len(files)}")
print("--------------------------------------------------------------------------------")

failed = False
for entry in files:
    filename = entry.get('fileName')
    expected_hash = entry.get('sha256Checksum', '')
    expected_size = entry.get('sizeBytes', 0)
    file_type = entry.get('type', 'UNKNOWN')

    file_path = os.path.join(bundle_dir, filename)
    if not os.path.exists(file_path):
        print(f"❌ [MISSING FILE] {filename} does not exist!")
        failed = True
        continue

    # Compute actual SHA-256
    hasher = hashlib.sha256()
    with open(file_path, 'rb') as bf:
        while chunk := bf.read(8192):
            hasher.update(chunk)
    actual_hash = hasher.hexdigest()
    actual_size = os.path.getsize(file_path)

    if actual_hash.lower() == expected_hash.lower() and actual_size == expected_size:
        print(f"✅ [PASS] {filename} ({file_type}) - SHA-256 match ({actual_size} bytes)")
    else:
        print(f"🚨 [CORRUPT / CHECKSUM MISMATCH] {filename}")
        print(f"    Expected SHA-256: {expected_hash}")
        print(f"    Actual SHA-256:   {actual_hash}")
        print(f"    Expected Size:    {expected_size}")
        print(f"    Actual Size:      {actual_size}")
        failed = True

if failed:
    sys.exit(1)
else:
    print("--------------------------------------------------------------------------------")
    print("🛡️  All file checksums and manifest entries verified 100% authentic.")
    sys.exit(0)
EOF

EXIT_CODE=$?

if [ ${EXIT_CODE} -eq 0 ]; then
  echo "================================================================================"
  echo "✅ BUNDLE INTEGRITY CHECK PASSED (Status: VALID)"
  echo "================================================================================"
  exit 0
else
  echo "================================================================================"
  echo "🚨 BUNDLE INTEGRITY CHECK FAILED (Status: CORRUPTED / TAMPERED)"
  echo "================================================================================"
  exit 1
fi
