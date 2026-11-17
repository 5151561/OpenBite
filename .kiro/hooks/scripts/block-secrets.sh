#!/bin/sh
# PreToolUse hook for file writes. Kiro sends the tool call as JSON on stdin.
# Exit 2 blocks the write when the new content looks like a real secret.
payload=$(cat)

if printf '%s' "$payload" | grep -Eq \
  'sk-ant-[A-Za-z0-9_-]{20,}|sk-(proj-)?[A-Za-z0-9]{32,}|AIza[0-9A-Za-z_-]{35}|-----BEGIN [A-Z ]*PRIVATE KEY-----|RELEASE_(STORE|KEY)_PASSWORD *= *[^ "$]'; then
  echo "Blocked: the write contains what looks like an API key, private key or signing password." >&2
  echo "API keys belong in SecretStore at runtime; signing values belong in local.properties or env vars (both outside git)." >&2
  exit 2
fi
exit 0
