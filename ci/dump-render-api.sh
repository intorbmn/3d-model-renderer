#!/usr/bin/env bash
# Prints the public API of the Minecraft classes needed to design the 26.3
# submit-based render backend, as Markdown suitable for $GITHUB_STEP_SUMMARY.
# Classes are located by simple name, so package moves do not break it.
# usage: ci/dump-render-api.sh <minecraft-jar> [extra-classpath]
set -uo pipefail
JAR="${1:?usage: dump-render-api.sh <minecraft-jar> [extra-classpath]}"
CP="$JAR${2:+:$2}"
NAMES=(
  'SubmitNodeCollector' 'SubmitNodeCollector$CustomGeometryRenderer'
  'OrderedSubmitNodeCollector'
  'RenderType' 'RenderTypes' 'RenderPipelines'
  'VertexConsumer' 'PoseStack' 'PoseStack$Pose'
  'EntityRenderer' 'LivingEntityRenderer' 'AvatarRenderer'
  'EntityRenderState' 'LivingEntityRenderState' 'AvatarRenderState' 'CameraRenderState'
)
LIST="$(unzip -Z1 "$JAR" 2>/dev/null | grep '\.class$')"
for n in "${NAMES[@]}"; do
  path="$(printf '%s\n' "$LIST" | grep -E "(^|/)${n//\$/\\$}\.class$" | head -3)"
  echo "<details><summary><b>$n</b></summary>"
  echo
  if [ -z "$path" ]; then echo "_not found in jar_"; echo; echo "</details>"; echo; continue; fi
  while IFS= read -r p; do
    cls="${p%.class}"; cls="${cls//\//.}"
    echo '```java'
    javap -public -cp "$CP" "$cls" 2>&1 | cut -c1-220 | head -120
    echo '```'
  done <<< "$path"
  echo
  echo "</details>"
  echo
done
