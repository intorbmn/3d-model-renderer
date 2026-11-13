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


# --- Bytecode of the methods the camera mixin injects into (verifies call order / partialTick) --
code() {
  local cls="$1" pat="$2" max="${3:-120}"
  echo "##### [javap -p -c] $cls :: $pat"
  javap -p -c -cp "$CP" "$cls" 2>/dev/null | awk -v p="$pat" '
    /^  [a-zA-Z].*\(.*\).*;$/ { on = ($0 ~ p) }
    on { print }' | grep -E '^  [a-zA-Z]|invoke|putfield|getfield|return' | cut -c1-220 | head -"$max"
}
code net.minecraft.client.Camera 'void update[(]|void alignWithEntity[(]|float calculateFov[(]|void extractRenderState[(]' 200
code net.minecraft.client.renderer.extract.LevelExtractor 'void extractVisibleEntities[(]' 80
code net.minecraft.client.Minecraft 'void pick[(]' 60
dump net.minecraft.client.input.MouseButtonInfo 20

# --- Classes the submit-based render backend depends on ----------------------------------------
dump com.mojang.blaze3d.platform.NativeImage 110
dump 'com.mojang.blaze3d.platform.NativeImage$Format' 30
dump net.minecraft.client.renderer.texture.DynamicTexture 30
dump net.minecraft.client.renderer.texture.AbstractTexture 30
dump net.minecraft.client.renderer.texture.TextureManager 50
dump net.minecraft.client.renderer.texture.OverlayTexture 30
dump net.minecraft.client.renderer.entity.EntityRenderer 70
dump net.minecraft.client.renderer.entity.EntityRenderDispatcher 70
dump net.minecraft.client.renderer.entity.LivingEntityRenderer 80
dump net.minecraft.client.renderer.entity.player.AvatarRenderer 70
dump net.minecraft.client.renderer.entity.state.HumanoidRenderState 60
dump net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer 60
dump net.minecraft.client.player.FirstPersonHandsAndItems 40
dump net.minecraft.client.gui.render.pip.GuiEntityRenderer 40
dump net.minecraft.client.renderer.rendertype.RenderSetup 60
code net.minecraft.client.renderer.entity.EntityRenderer 'createRenderState[(]net' 40
code net.minecraft.client.renderer.entity.EntityRenderDispatcher 'void submit[(]|extractEntity[(]' 80
code net.minecraft.client.renderer.rendertype.RenderTypes 'entityCutout[(]|entityTranslucent[(]|entityCutoutCull[(]' 60
code net.minecraft.client.renderer.extract.LevelExtractor 'void extractVisibleEntities[(]' 120
echo "##### classes: light / gui entity / pip"
unzip -Z1 "$JAR" | grep -E '(LightCoords|LightTexture|Lightmap|GuiEntity|PictureInPicture|InventoryScreen)[A-Za-z$]*\.class$' | head -30

# --- Texture sampling (filtering of runtime model textures) -----------------------------------
code net.minecraft.client.renderer.texture.DynamicTexture 'DynamicTexture[(]|void upload[(]' 60
code net.minecraft.client.renderer.texture.AbstractTexture '.' 40
dump com.mojang.renderpearl.api.textures.GpuSampler 30
dump com.mojang.blaze3d.systems.SamplerCache 30
dump com.mojang.renderpearl.api.textures.FilterMode 20
dump com.mojang.renderpearl.api.textures.AddressMode 20
echo "##### classes: sampler"
unzip -Z1 "$JAR" | grep -E '(Sampler|FilterMode|AddressMode)[A-Za-z$]*\.class$' | head -20

# --- First-person hands (methods only; the class has ~60 constants before them) ----------------
echo "##### [javap -p methods] net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer"
javap -p -cp "$CP" net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer 2>&1 | grep '(' | cut -c1-330
dump net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState 40
dump 'net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState$HandRenderSelection' 20
code net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer 'void submit|void extract|void render' 80
code net.minecraft.client.renderer.texture.AbstractTexture 'AbstractTexture[(][)]' 20
echo "##### AbstractTexture default sampler arguments"
javap -p -c -cp "$CP" net.minecraft.client.renderer.texture.AbstractTexture 2>/dev/null | sed -n '/AbstractTexture();/,/return/p' | cut -c1-200
find_callers 'FirstPersonHandsAndItemsRenderer'

rm -rf "$WORK"
