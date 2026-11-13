#!/usr/bin/env bash
# Publishes the CI report directory to a branch (default: ci-reports) under
# reports/<source-branch>/, always as ONE squashed commit, so the results of the
# latest run can be read with a plain `git clone -b ci-reports <repo>`; no API
# token, no log download, no copy/paste.
#
# env:  REPORT_REMOTE  authenticated git remote URL (required)
#       REPORT_DIR     directory to publish          (default: ci-report)
#       REPORT_BRANCH  branch to publish to          (default: ci-reports)
#       SOURCE_BRANCH, SOURCE_SHA  what the report is about
#       MAX_FILE_BYTES per-file cap, the END of a file is kept (default 300000)
set -euo pipefail

DIR="${REPORT_DIR:-ci-report}"
REMOTE="${REPORT_REMOTE:?REPORT_REMOTE is required}"
BRANCH="${REPORT_BRANCH:-ci-reports}"
SRC_BRANCH="${SOURCE_BRANCH:-unknown}"
SRC_SHA="${SOURCE_SHA:-unknown}"
MAX="${MAX_FILE_BYTES:-300000}"
SAFE="${SRC_BRANCH//\//__}"

[ -d "$DIR" ] || { echo "no report directory '$DIR', nothing to publish"; exit 0; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Cap huge logs but keep their tail, where build errors are.
STAGE="$WORK/stage"; mkdir -p "$STAGE"
cp -r "$DIR"/. "$STAGE"/
find "$STAGE" -type f | while read -r f; do
  size=$(wc -c < "$f")
  if [ "$size" -gt "$MAX" ]; then
    { echo "[... truncated: kept last $MAX of $size bytes ...]"; tail -c "$MAX" "$f"; } > "$f.tmp"
    mv "$f.tmp" "$f"
  fi
done

for attempt in 1 2 3 4 5; do
  R="$WORK/repo"; rm -rf "$R"
  if git ls-remote --exit-code --heads "$REMOTE" "$BRANCH" >/dev/null 2>&1; then
    git clone -q --depth 1 -b "$BRANCH" "$REMOTE" "$R"
  else
    git init -q "$R"
    git -C "$R" remote add origin "$REMOTE"
  fi
  git -C "$R" config user.name "ci-report"
  git -C "$R" config user.email "ci-report@users.noreply.github.com"

  # Squash to a single commit so the branch never grows.
  git -C "$R" checkout -q --orphan "$BRANCH.new"
  rm -rf "$R/reports/$SAFE"; mkdir -p "$R/reports/$SAFE"
  cp -r "$STAGE"/. "$R/reports/$SAFE/"
  git -C "$R" add -A
  git -C "$R" commit -q -m "CI report for $SRC_BRANCH @ ${SRC_SHA:0:7}"
  if git -C "$R" push -q --force-with-lease origin "HEAD:refs/heads/$BRANCH" 2>"$WORK/push.err"; then
    echo "published reports/$SAFE to branch $BRANCH"
    exit 0
  fi
  echo "push attempt $attempt failed: $(tr '\n' ' ' < "$WORK/push.err")" >&2
  sleep $((attempt * 2))
done
echo "could not publish report" >&2
exit 1
