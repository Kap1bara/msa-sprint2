#!/usr/bin/env bash
set -e

echo "▶️ Checking canary release (90% v1, 10% v2)..."

out=$(kubectl run -i --rm canary-test --image=curlimages/curl --restart=Never -- \
  sh -c 'for i in $(seq 1 100); do curl -s http://booking-service/ping; echo; done' \
  | sort | uniq -c)

echo "$out"

v1=$(echo "$out" | awk '/version=v1/{print $1}' | head -n1)
v2=$(echo "$out" | awk '/version=v2/{print $1}' | head -n1)
v1=${v1:-0}; v2=${v2:-0}

echo "v1=$v1 v2=$v2"

if [ "$v1" -ge 80 ] && [ "$v2" -ge 5 ]; then
  echo "✅ Canary looks OK"
else
  echo "❌ Canary split is not OK"
  exit 1
fi
