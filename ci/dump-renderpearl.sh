#!/usr/bin/env bash
# Dumps what a custom render pipeline needs on Minecraft 26.3 into a directory:
#   api/*.txt        public API of every com.mojang.renderpearl class (pipelines, buffers, shaders, vertex formats)
#   mc/*.txt         Minecraft classes that create pipelines, render types and uniforms (+ bytecode of their setup)
#   shaders/...      vanilla shader sources (entity shaders and includes)
# usage: ci/dump-renderpearl.sh <out-dir> <minecraft-jar> <classpath>
set -uo pipefail
OUT="${1:?out dir}"; JAR="${2:?minecraft jar}"; CP="$JAR:${3:-}"
mkdir -p "$OUT/api" "$OUT/mc" "$OUT/shaders"

# The renderpearl classes may live in Minecraft's jar or in a library jar.
JARS=("$JAR")
IFS=: read -r -a LIBJARS <<< "${3:-}"
for j in "${LIBJARS[@]}"; do
  [ -f "$j" ] && unzip -Z1 "$j" 2>/dev/null | grep -q '^com/mojang/renderpearl/' && JARS+=("$j")
done
echo "renderpearl found in: ${JARS[*]}" > "$OUT/where.txt"

for j in "${JARS[@]}"; do
  unzip -Z1 "$j" 2>/dev/null | grep '^com/mojang/renderpearl/.*\.class$' | grep -v '\$[0-9]' ; done | sort -u > "$OUT/renderpearl-classes.txt"
while IFS= read -r p; do
  cls="${p%.class}"; cls="${cls//\//.}"
  pkg="$(dirname "$p" | sed 's#com/mojang/renderpearl/##; s#/#_#g')"
  { echo "=== $cls"; javap -public -cp "$CP" "$cls" 2>&1 | cut -c1-260; echo; } >> "$OUT/api/$pkg.txt"
done < "$OUT/renderpearl-classes.txt"

# Minecraft classes about pipelines, render types, uniforms, shaders and lighting.
unzip -Z1 "$JAR" | grep '^net/minecraft/client/renderer/.*\.class$' | grep -v '\$[0-9]' \
  | grep -E '/(RenderPipelines|RenderType|RenderTypes|RenderSetup[^/]*|ShaderManager|ShaderDefines|DynamicUniforms[^/]*|GlobalSettingsUniform|Lighting|LightTexture|ProjectionMatrixBuffer|CachedOrthoProjectionMatrixBuffer|UniformValue[^/]*|ShaderProgram[^/]*|CoreShaders|MappableRingBuffer|SubmitNodeStorage[^/]*|feature/CustomFeatureRenderer[^/]*|LevelRenderer|GameRenderer|OutlineBufferSource|MultiBufferSource[^/]*|TextureSetup|RenderStateShard[^/]*)\.class$' \
  | sort > "$OUT/mc-classes.txt"
while IFS= read -r p; do
  cls="${p%.class}"; cls="${cls//\//.}"
  { echo "=== $cls"; javap -public -cp "$CP" "$cls" 2>&1 | cut -c1-260 | head -200; echo; } >> "$OUT/mc/public.txt"
done < "$OUT/mc-classes.txt"

# How vanilla builds its entity pipelines and render types (bytecode of the static setup).
for cls in net.minecraft.client.renderer.RenderPipelines net.minecraft.client.renderer.rendertype.RenderTypes net.minecraft.client.renderer.RenderTypes net.minecraft.client.renderer.rendertype.RenderType net.minecraft.client.renderer.RenderType; do
  javap -c -p -cp "$CP" "$cls" > "$OUT/mc/bytecode-${cls##*.}.txt" 2>&1 || rm -f "$OUT/mc/bytecode-${cls##*.}.txt"
done

# Vanilla shader sources.
unzip -Z1 "$JAR" | grep -E '^assets/minecraft/shaders/(core/(entity|rendertype_entity|position_tex|position_color)[^/]*|include/[^/]+)$' > "$OUT/shader-files.txt"
unzip -q -o "$JAR" $(cat "$OUT/shader-files.txt") -d "$OUT/shaders" 2>/dev/null
unzip -Z1 "$JAR" | grep '^assets/minecraft/shaders/' | head -400 > "$OUT/all-shader-files.txt"
