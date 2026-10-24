#!/usr/bin/env bash
# Puts the cause of a failed Gradle build on the pull request itself, as an
# error annotation, so it can be read without opening the full log.
set -uo pipefail

log="${RUNNER_TEMP:-/tmp}/gradle.log"
if [ ! -f "$log" ]; then
  echo "::error title=Build failed::No Gradle log was written. The failure happened before Gradle ran."
  exit 0
fi

{
  # Compiler errors, then Gradle's own account of what went wrong.
  grep -E '^e: ' "$log" | head -n 15
  sed -n '/^FAILURE: /,/^\* Try:/p' "$log" | grep -v '^\* Try:' | head -n 40
} | cut -c1-400 > "${log}.cause"

if [ ! -s "${log}.cause" ]; then
  tail -n 30 "$log" | cut -c1-400 > "${log}.cause"
fi

message="$(cat "${log}.cause")"
message="${message//'%'/'%25'}"
message="${message//$'\r'/'%0D'}"
message="${message//$'\n'/'%0A'}"
echo "::error title=Build failed::${message}"
