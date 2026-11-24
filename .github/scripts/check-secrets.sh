#!/usr/bin/env bash
# Fails if the repository holds something that looks like a secret: a private key, a known kind of
# access token, or a key store file. Runs in CI on every push; no dependency beyond git and grep.
set -euo pipefail

self=".github/scripts/check-secrets.sh"
found=0

# Key store and certificate files that would carry private keys.
while IFS= read -r f; do
  echo "::error file=$f::key store or private key file in the repository"
  found=1
done < <(git ls-files | grep -Ei '\.(jks|keystore|p12|pfx|pem|key)$' || true)

# Private keys and well-known token formats inside tracked files.
patterns=(
  '-----BEGIN ([A-Z]+ )?PRIVATE KEY-----'
  'AKIA[0-9A-Z]{16}'
  'gh[pousr]_[A-Za-z0-9]{36,}'
  'github_pat_[A-Za-z0-9_]{40,}'
  'AIza[0-9A-Za-z_-]{35}'
  'xox[abprs]-[A-Za-z0-9-]{10,}'
  'sk-[A-Za-z0-9_-]{32,}'
)
for p in "${patterns[@]}"; do
  while IFS= read -r hit; do
    [ -z "$hit" ] && continue
    file="${hit%%:*}"
    [ "$file" = "$self" ] && continue
    echo "::error file=$file::possible secret (pattern $p)"
    found=1
  done < <(git grep -nIE -e "$p" -- . || true)
done

if [ "$found" -ne 0 ]; then
  echo "Secrets check failed: remove the secret, rotate it, and push again."
  exit 1
fi
echo "Secrets check: nothing found."
