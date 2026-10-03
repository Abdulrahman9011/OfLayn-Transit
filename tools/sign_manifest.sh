#!/usr/bin/env bash
# Offline signing of a fare manifest (ECDSA P-256 / SHA-256, DER) — matches ManifestVerifier.
#   tools/sign_manifest.sh keygen                 -> manifest-signing.key.pem (KEEP OFFLINE) + app/src/main/assets/manifest_pub.der
#   tools/sign_manifest.sh sign <manifest.json>   -> <manifest.json>.sig
set -euo pipefail
case "${1:-}" in
  keygen)
    openssl ecparam -name prime256v1 -genkey -noout -out manifest-signing.key.pem
    mkdir -p app/src/main/assets
    openssl pkey -in manifest-signing.key.pem -pubout -outform DER -out app/src/main/assets/manifest_pub.der
    echo "Private key: manifest-signing.key.pem (never commit it). Public key written to app assets." ;;
  sign)
    F="${2:?manifest path}"
    openssl dgst -sha256 -sign manifest-signing.key.pem -out "$F.sig" "$F"
    echo "Wrote $F.sig" ;;
  *) echo "usage: $0 keygen | sign <manifest.json>"; exit 1 ;;
esac
