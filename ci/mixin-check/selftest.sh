#!/usr/bin/env bash
# Proves the validator itself works: runs it on fixtures that target JDK classes
# with deliberately good and bad Mixin targets and asserts the exact outcome.
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$(python3 "$D/check_mixins.py" --src "$D/selftest/src" --mixins-json "$D/selftest/mixins.json")"
RC=$?
echo "$OUT" | tail -1
[ "$RC" -eq 1 ] || { echo "selftest: expected exit code 1, got $RC" >&2; exit 1; }
echo "$OUT" | grep -q "6 FAIL, 2 WARN, 11 OK" || { echo "selftest: unexpected counts" >&2; echo "$OUT" >&2; exit 1; }
echo "mixin-check selftest passed"
