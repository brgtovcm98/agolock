#!/usr/bin/env bash
set -euo pipefail

base_url="${1:?usage: smoke.sh https://staging.example.com}"
curl -fsS "${base_url}/api/v1/info" | grep -q '"name":"agolock"'
echo "info ok"
