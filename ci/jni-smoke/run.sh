#!/usr/bin/env bash
# Independent JNI smoke test.
#
# Compiles only NativeFunc + NativeLibraryLoader (no Minecraft, Fabric or Gradle),
# puts the freshly built native library on the classpath at the path the loader
# expects, and checks that System.load and GetVersion work.
#
# Environment:
#   NATIVE_LIB     path to libmmd_engine.so (default: rust_engine/target/release/libmmd_engine.so)
#   LOG4J_API_JAR  path to a log4j-api jar (default: downloaded from Maven Central and checksum-verified)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

NATIVE_LIB="${NATIVE_LIB:-$ROOT/rust_engine/target/release/libmmd_engine.so}"
LOG4J_VERSION="2.24.3"

case "$(uname -s)-$(uname -m)" in
    Linux-x86_64)          NATIVE_DIR="linux-x64" ;;
    Linux-aarch64|Linux-arm64) NATIVE_DIR="linux-arm64" ;;
    *) echo "Unsupported platform for the JNI smoke test: $(uname -s)-$(uname -m)" >&2; exit 2 ;;
esac

if [ ! -f "$NATIVE_LIB" ]; then
    echo "Native library not found: $NATIVE_LIB (build it with cargo first)" >&2
    exit 2
fi

LOG4J_API_JAR="${LOG4J_API_JAR:-}"
if [ -z "$LOG4J_API_JAR" ]; then
    BASE="https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/$LOG4J_VERSION"
    LOG4J_API_JAR="$WORK/log4j-api-$LOG4J_VERSION.jar"
    curl -fsSL -o "$LOG4J_API_JAR" "$BASE/log4j-api-$LOG4J_VERSION.jar"
    expected_sha1="$(curl -fsSL "$BASE/log4j-api-$LOG4J_VERSION.jar.sha1" | cut -d' ' -f1)"
    echo "$expected_sha1  $LOG4J_API_JAR" | sha1sum -c - >/dev/null
fi

mkdir -p "$WORK/classes" "$WORK/resources/natives/$NATIVE_DIR"
cp "$NATIVE_LIB" "$WORK/resources/natives/$NATIVE_DIR/libmmd_engine.so"

javac -encoding UTF-8 -d "$WORK/classes" -cp "$LOG4J_API_JAR" \
    "$ROOT/common/src/main/java/com/shiroha/mmdskin/NativeFunc.java" \
    "$ROOT/common/src/main/java/com/shiroha/mmdskin/NativeLibraryLoader.java" \
    "$ROOT/ci/jni-smoke/src/com/shiroha/mmdskin/JniSmoke.java"

java --enable-native-access=ALL-UNNAMED \
    -cp "$WORK/classes:$WORK/resources:$LOG4J_API_JAR" \
    com.shiroha.mmdskin.JniSmoke
