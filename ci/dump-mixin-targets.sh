#!/usr/bin/env bash
# Diagnostic only: dumps the NON-public members (javap -p) of the Minecraft / Fabric / Cloth classes
# that the Mixin targets and the Fabric glue depend on, plus every method that calls a few renamed
# members. The existing diagnostics use `javap -public`, which hides private/protected members such as
# Camera#setPosition, so Mixin @Shadow / @Inject targets could not be verified from the report.
#
# Usage: ci/dump-mixin-targets.sh <minecraft-merged.jar> <classpath-with-fabric-and-cloth-jars>
set +e

JAR="$1"
LIBS="$2"
CP="$JAR:$LIBS"

dump() {
  local cls="$1" max="${2:-220}"
  echo "##### [javap -p] $cls"
  javap -p -cp "$CP" "$cls" 2>&1 | cut -c1-240 | head -"$max"
}

# --- Mixin targets that failed validation ------------------------------------------------------
dump net.minecraft.client.Camera 140
dump net.minecraft.client.MouseHandler 120
dump net.minecraft.client.renderer.GameRenderer 220
dump net.minecraft.client.renderer.LevelRenderer 260
dump net.minecraft.client.renderer.state.level.CameraRenderState 40
dump net.minecraft.client.DeltaTracker 30

# --- Cloth Config entry base classes (MobReplacementListEntry) ---------------------------------
dump me.shedaniel.clothconfig2.api.AbstractConfigListEntry 80
dump me.shedaniel.clothconfig2.gui.entries.TooltipListEntry 40

# --- Networking / registry glue ----------------------------------------------------------------
dump net.minecraft.network.protocol.common.custom.CustomPacketPayload 40
dump 'net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type' 20
dump net.minecraft.network.codec.StreamCodec 40
dump 'net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$Context' 20
dump 'net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayPayloadHandler' 20
dump 'net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$Context' 20
dump 'net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$PlayPayloadHandler' 20
dump net.minecraft.resources.Identifier 60

# --- Who calls renamed members? (finds the new injection points) -------------------------------
WORK=$(mktemp -d)
unzip -q -o "$JAR" 'net/minecraft/client/*' -d "$WORK" 2>/dev/null

find_callers() {
  local needle="$1"
  echo "##### callers of: $needle"
  grep -rl --include='*.class' "$needle" "$WORK" 2>/dev/null | sed "s#$WORK/##; s#\.class\$##; s#/#.#g" | sort | head -25 | while read -r cls; do
    echo "== $cls"
    javap -p -c -cp "$CP" "$cls" 2>/dev/null | awk -v n="$needle" '
      /^  [a-zA-Z].*\(.*\)/ { method=$0 }
      index($0, n) { print "   in " method; print "      " $0 }' | cut -c1-240 | head -16
  done
}

find_callers 'isDetached'
find_callers 'crosshairPickEntity'
find_callers 'mulPose'

rm -rf "$WORK"
