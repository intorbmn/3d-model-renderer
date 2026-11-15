#!/usr/bin/env bash
# Dumps private signatures and the bytecode of chosen methods of Minecraft classes.
# usage: ci/dump-methods.sh <out-file> <classpath> "<class>:<method>|<method>..." ...
set -uo pipefail
OUT="${1:?out}"; CP="${2:?cp}"; shift 2
: > "$OUT"
for spec in "$@"; do
  cls="${spec%%:*}"; methods="${spec#*:}"
  { echo "################ $cls (all members)"; javap -p -cp "$CP" "$cls" 2>&1 | cut -c1-300; echo; } >> "$OUT"
  [ "$methods" = "$spec" ] && continue
  javap -c -p -cp "$CP" "$cls" 2>/dev/null | awk -v pat="$methods" '
    /^  [a-zA-Z].*\(.*\).*;$/ || /^  static \{\};$/ { keep = ($0 ~ "[ .](" pat ")\\(") ; if (keep) print "---- " $0; next }
    keep { print }' | cut -c1-260 >> "$OUT"
  echo >> "$OUT"
done
