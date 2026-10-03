#!/usr/bin/env bash
# Probe an endpoint ONCE and record what it really returns (status, headers, JSON shape).
# Usage: tools/probe_ulasimapi.sh "<url>"   -> writes tools/probe-out/<timestamp>/{headers.txt,body.json,shape.txt}
# Be polite: single request, no loops. Read the provider's terms before any automated use.
set -euo pipefail
URL="${1:?usage: $0 <url>}"
OUT="tools/probe-out/$(date -u +%Y%m%dT%H%M%SZ)"; mkdir -p "$OUT"
curl -sS --max-time 20 -A "OfLayn-probe/0.1 (contact: YOUR-EMAIL)" -D "$OUT/headers.txt" -o "$OUT/body.json" "$URL" || { echo "request failed"; exit 1; }
head -n 1 "$OUT/headers.txt"
python3 - "$OUT/body.json" <<'PY' | tee "$OUT/shape.txt"
import json, sys
try: d = json.load(open(sys.argv[1]))
except Exception as e: print("Body is not JSON:", e); sys.exit(0)
def shape(x, p="", depth=0):
    if depth > 4: return
    if isinstance(x, dict):
        for k, v in x.items(): print(f"{p}/{k}: {type(v).__name__}"); shape(v, f"{p}/{k}", depth+1)
    elif isinstance(x, list):
        print(f"{p}: list[{len(x)}]")
        if x: shape(x[0], f"{p}/0", depth+1)
shape(d)
PY
echo "Saved to $OUT. Use /pointer paths from shape.txt to fill VehicleFieldMapping. Do NOT commit bodies containing personal data."
