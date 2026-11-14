## Minecraft 26.3 render API

<details><summary><b>SubmitNodeCollector</b></summary>

```java
Compiled from "SubmitNodeCollector.java"
public interface net.minecraft.client.renderer.SubmitNodeCollector extends net.minecraft.client.renderer.OrderedSubmitNodeCollector {
  public abstract net.minecraft.client.renderer.OrderedSubmitNodeCollector order(int);
}
```

</details>

<details><summary><b>SubmitNodeCollector$CustomGeometryRenderer</b></summary>

```java
Compiled from "SubmitNodeCollector.java"
public interface net.minecraft.client.renderer.SubmitNodeCollector$CustomGeometryRenderer {
  public abstract void render(com.mojang.blaze3d.vertex.PoseStack$Pose, com.mojang.blaze3d.vertex.VertexConsumer);
}
```

</details>

<details><summary><b>OrderedSubmitNodeCollector</b></summary>

```java
Compiled from "OrderedSubmitNodeCollector.java"
public interface net.minecraft.client.renderer.OrderedSubmitNodeCollector {
  public abstract void submitShadow(com.mojang.blaze3d.vertex.PoseStack, float, java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState$ShadowPiece>);
  public abstract void submitNameTag(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.world.phys.Vec3, int, net.minecraft.network.chat.Component, boolean, int, net.minecraft.client.renderer.state.level.CameraRenderStat
  public abstract void submitText(com.mojang.blaze3d.vertex.PoseStack, float, float, net.minecraft.util.FormattedCharSequence, boolean, net.minecraft.client.gui.Font$DisplayMode, int, int, int, int);
  public abstract void submitTextBackground(com.mojang.blaze3d.vertex.PoseStack, float, float, float, float, int, net.minecraft.client.gui.Font$DisplayMode, int);
  public abstract void submitFlame(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.entity.state.EntityRenderState, org.joml.Quaternionf);
  public abstract void submitLeash(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.entity.state.EntityRenderState$LeashState);
  public abstract <S> void submitModel(net.minecraft.client.model.Model<? super S>, S, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.rendertype.RenderType, int, int, int, net.minecraft.client.rendere
  public default <S> void submitModel(net.minecraft.client.model.Model<? super S>, S, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.rendertype.RenderType, int, int, int);
  public default <S> void submitModel(net.minecraft.client.model.Model<? super S>, S, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.resources.Identifier, int, int, int);
  public default <S> void submitModel(net.minecraft.client.model.Model<S>, S, com.mojang.blaze3d.vertex.PoseStack, int, int, int, net.minecraft.client.resources.model.sprite.SpriteId, net.minecraft.client.resources.model
  public abstract <S> void submitCrumblingOverlay(net.minecraft.client.model.Model<? super S>, S, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.rendertype.RenderType, int, int, int, net.minecraft.cli
  public default void submitCrumblingOverlay(net.minecraft.client.model.geom.ModelPart, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.rendertype.RenderType, int, int, int, net.minecraft.client.render
  public default void submitModelPart(net.minecraft.client.model.geom.ModelPart, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.rendertype.RenderType, int, int, net.minecraft.client.renderer.texture.U
  public default void submitModelPart(net.minecraft.client.model.geom.ModelPart, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.rendertype.RenderType, int, int, net.minecraft.client.renderer.texture.U
  public default void submitModelPart(net.minecraft.client.model.geom.ModelPart, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.rendertype.RenderType, int, int, net.minecraft.client.renderer.texture.U
  public abstract void submitMovingBlock(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.block.MovingBlockRenderState, int);
  public abstract void submitBlockModel(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.rendertype.RenderType, java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>, int[], i
  public abstract void submitBreakingBlockModel(com.mojang.blaze3d.vertex.PoseStack, java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>, int, boolean);
  public abstract void submitShapeOutline(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.world.phys.shapes.VoxelShape, net.minecraft.client.renderer.rendertype.RenderType, int, float, boolean);
  public abstract void submitItem(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.world.item.ItemDisplayContext, int, int, int, int[], net.minecraft.client.resources.model.geometry.ItemQuads, net.minecraft.client.rend
  public abstract void submitCustomGeometry(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.rendertype.RenderType, net.minecraft.client.renderer.SubmitNodeCollector$CustomGeometryRenderer);
  public abstract void submitQuadParticleGroup(net.minecraft.client.renderer.state.level.QuadParticleRenderState);
  public abstract void submitGizmoPrimitives(net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives$Group, net.minecraft.client.renderer.state.level.CameraRenderState, boolean);
}
```

</details>

<details><summary><b>RenderType</b></summary>

```java
Compiled from "RenderType.java"
public class net.minecraft.client.renderer.rendertype.RenderType {
  public static final int BIG_BUFFER_SIZE;
  public static final int SMALL_BUFFER_SIZE;
  public static final int TRANSIENT_BUFFER_SIZE;
  public java.lang.String toString();
  public boolean hasBlending();
  public net.minecraft.client.renderer.rendertype.PreparedRenderType prepare();
  public com.mojang.renderpearl.api.vertex.VertexFormat format();
  public com.mojang.renderpearl.api.pipeline.PrimitiveTopology primitiveTopology();
  public java.util.Optional<net.minecraft.client.renderer.rendertype.RenderType> outline();
  public boolean isOutline();
  public com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline();
  public boolean affectsCrumbling();
  public boolean canConsolidateConsecutiveGeometry();
  public boolean sortOnUpload();
  public boolean forceSolidModelPhase();
}
```

</details>

<details><summary><b>RenderTypes</b></summary>

```java
Compiled from "RenderTypes.java"
public class net.minecraft.client.renderer.rendertype.RenderTypes {
  public static final net.minecraft.client.renderer.rendertype.RenderType LINES;
  public static final net.minecraft.client.renderer.rendertype.RenderType LINES_TRANSLUCENT;
  public static final net.minecraft.client.renderer.rendertype.RenderType LINES_TRANSLUCENT_NO_DEPTH_WRITE;
  public static final net.minecraft.client.renderer.rendertype.RenderType LINES_DEPTH_BIAS;
  public static final net.minecraft.client.renderer.rendertype.RenderType SECONDARY_BLOCK_OUTLINE;
  public net.minecraft.client.renderer.rendertype.RenderTypes();
  public static net.minecraft.client.renderer.rendertype.RenderType solidMovingBlock();
  public static net.minecraft.client.renderer.rendertype.RenderType cutoutMovingBlock();
  public static net.minecraft.client.renderer.rendertype.RenderType translucentMovingBlock();
  public static net.minecraft.client.renderer.rendertype.RenderType armorCutoutNoCull(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType armorCutoutNoCullGlint(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType armorTrim(net.minecraft.resources.Identifier, boolean);
  public static net.minecraft.client.renderer.rendertype.RenderType wolfArmorCracks(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entitySolid(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entitySolidGlint(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entitySolidZOffsetForward(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entityCutoutCull(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entityCutout(net.minecraft.resources.Identifier, boolean);
  public static net.minecraft.client.renderer.rendertype.RenderType entityCutout(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entityCutoutZOffset(net.minecraft.resources.Identifier, boolean);
  public static net.minecraft.client.renderer.rendertype.RenderType entityCutoutZOffset(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entityCutoutDissolve(net.minecraft.resources.Identifier, net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entityTranslucentCull(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType itemCutout(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType itemCutoutGlint(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType itemCutoutGlintSpecial(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType itemTranslucent(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType itemTranslucentGlint(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType itemTranslucentGlintSpecial(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entityTranslucent(net.minecraft.resources.Identifier, boolean);
  public static net.minecraft.client.renderer.rendertype.RenderType entityTranslucent(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entityTranslucentEmissive(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType endCrystalBeam(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType beaconBeam(net.minecraft.resources.Identifier, boolean);
  public static net.minecraft.client.renderer.rendertype.RenderType bannerPattern(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType entityShadow(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType eyes(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType breezeEyes(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType breezeWind(net.minecraft.resources.Identifier, float, float);
  public static net.minecraft.client.renderer.rendertype.RenderType energySwirl(net.minecraft.resources.Identifier, float, float);
  public static net.minecraft.client.renderer.rendertype.RenderType leash();
  public static net.minecraft.client.renderer.rendertype.RenderType waterMask();
  public static net.minecraft.client.renderer.rendertype.RenderType outline(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType patternedShieldGlint();
  public static net.minecraft.client.renderer.rendertype.RenderType trimmedArmorGlint();
  public static net.minecraft.client.renderer.rendertype.RenderType crumbling(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType text(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType textGrayscale(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType textPolygonOffset(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType textGrayscalePolygonOffset(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType textSeeThrough(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType textGrayscaleSeeThrough(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType lightning();
  public static net.minecraft.client.renderer.rendertype.RenderType dragonRays();
  public static net.minecraft.client.renderer.rendertype.RenderType endPortal();
  public static net.minecraft.client.renderer.rendertype.RenderType endGateway();
  public static net.minecraft.client.renderer.rendertype.RenderType lines();
  public static net.minecraft.client.renderer.rendertype.RenderType linesTranslucent();
  public static net.minecraft.client.renderer.rendertype.RenderType linesTranslucentNoDepthWrite();
  public static net.minecraft.client.renderer.rendertype.RenderType linesDepthBias();
  public static net.minecraft.client.renderer.rendertype.RenderType secondaryBlockOutline();
  public static net.minecraft.client.renderer.rendertype.RenderType debugFilledBox();
  public static net.minecraft.client.renderer.rendertype.RenderType debugPoint();
  public static net.minecraft.client.renderer.rendertype.RenderType debugQuads();
  public static net.minecraft.client.renderer.rendertype.RenderType debugTriangleFan();
  public static net.minecraft.client.renderer.rendertype.RenderType blockScreenEffect(net.minecraft.resources.Identifier);
  public static net.minecraft.client.renderer.rendertype.RenderType fireScreenEffect(net.minecraft.resources.Identifier);
}
```

</details>

<details><summary><b>RenderPipelines</b></summary>

```java
Compiled from "RenderPipelines.java"
public class net.minecraft.client.renderer.RenderPipelines {
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline$Snippet OIT_DEPTH_BOUNDS_SNIPPET;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline$Snippet OIT_TRANSMITTANCE_SNIPPET;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline$Snippet OIT_ACCUMULATE_SNIPPET;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline$Snippet POST_PROCESSING_SNIPPET;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline SOLID_BLOCK;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline SOLID_TERRAIN;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline SOLID_TERRAIN_MULTIDRAW;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline WIREFRAME;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline WIREFRAME_MULTIDRAW;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline CUTOUT_BLOCK;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline CUTOUT_TERRAIN;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline CUTOUT_TERRAIN_MULTIDRAW;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline TRANSLUCENT_TERRAIN;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline TRANSLUCENT_TERRAIN_MULTIDRAW;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_TERRAIN;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_TERRAIN_MULTIDRAW;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline TRANSLUCENT_BLOCK;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_TRANSLUCENT_BLOCK;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ARMOR_CUTOUT_NO_CULL;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ARMOR_DECAL_CUTOUT_NO_CULL;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline WOLF_ARMOR_CRACKS;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_SOLID;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_SOLID_Z_OFFSET_FORWARD;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_CUTOUT_CULL;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_CUTOUT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_CUTOUT_Z_OFFSET;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_CUTOUT_DISSOLVE;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_TRANSLUCENT;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_ENTITY;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_TRANSLUCENT_EMISSIVE;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_ENTITY_EMISSIVE;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_TRANSLUCENT_CULL;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_ENTITY_CULL;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline END_CRYSTAL_BEAM;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline BANNER_PATTERN;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline BREEZE_WIND;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_BREEZE_WIND;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENERGY_SWIRL;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_ENERGY_SWIRL;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline EYES;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_EYES;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_SHADOW;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_ENTITY_SHADOW;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline$Snippet GLINT_SNIPPET;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline$Snippet GLINT_SPECIAL_SNIPPET;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ARMOR_CUTOUT_NO_CULL_GLINT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ENTITY_SOLID_GLINT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ITEM_CUTOUT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ITEM_CUTOUT_GLINT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ITEM_CUTOUT_GLINT_SPECIAL;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ITEM_TRANSLUCENT;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_ITEM;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ITEM_TRANSLUCENT_GLINT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline ITEM_TRANSLUCENT_GLINT_SPECIAL;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_ITEM_GLINT;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_ITEM_GLINT_SPECIAL;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline BEACON_BEAM_OPAQUE;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline BEACON_BEAM_TRANSLUCENT;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_BEACON_BEAM;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline LEASH;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline WATER_MASK;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline OIT_WATER_MASK;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GLINT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline CRUMBLING;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_CRUMBLING;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline TEXT;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_TEXT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline TEXT_GRAYSCALE;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_TEXT_GRAYSCALE;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXT_GRAYSCALE;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline TEXT_POLYGON_OFFSET;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_TEXT_POLYGON_OFFSET;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline TEXT_GRAYSCALE_POLYGON_OFFSET;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_TEXT_GRAYSCALE_POLYGON_OFFSET;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline TEXT_SEE_THROUGH;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline TEXT_GRAYSCALE_SEE_THROUGH;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline LIGHTNING;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_LIGHTNING;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline DRAGON_RAYS;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_DRAGON_RAYS;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline END_PORTAL;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline END_GATEWAY;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline FLAT_CLOUDS;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline CLOUDS;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_CLOUDS;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_FLAT_CLOUDS;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline LINES;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline LINES_TRANSLUCENT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline LINES_TRANSLUCENT_NO_DEPTH_WRITE;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_LINES_TRANSLUCENT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline LINES_DEPTH_BIAS;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline SECONDARY_BLOCK_OUTLINE;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline DEBUG_POINTS;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_DEBUG_POINTS;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline DEBUG_FILLED_BOX;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_DEBUG_FILLED_BOX;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline DEBUG_QUADS;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_DEBUG_QUADS;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline DEBUG_TRIANGLE_FAN;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_DEBUG_TRIANGLE_FAN;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline WORLD_BORDER;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_WORLD_BORDER;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline OPAQUE_PARTICLE;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline TRANSLUCENT_PARTICLE;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_PARTICLE;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline WEATHER;
  public static final net.minecraft.client.renderer.oit.OitPipelineSet OIT_WEATHER;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline SKY;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline END_SKY;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline SUNRISE_SUNSET;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline STARS;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline CELESTIAL;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_INVERT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXT_HIGHLIGHT;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXTURED;
  public static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXTURED_PREMULTIPLIED_ALPHA;
```

</details>

<details><summary><b>VertexConsumer</b></summary>

```java
Compiled from "VertexConsumer.java"
public interface com.mojang.blaze3d.vertex.VertexConsumer {
  public abstract com.mojang.blaze3d.vertex.VertexConsumer addVertex(float, float, float);
  public abstract com.mojang.blaze3d.vertex.VertexConsumer setColor(int, int, int, int);
  public abstract com.mojang.blaze3d.vertex.VertexConsumer setColor(int);
  public abstract com.mojang.blaze3d.vertex.VertexConsumer setUv(float, float);
  public abstract com.mojang.blaze3d.vertex.VertexConsumer setUv1(int, int);
  public abstract com.mojang.blaze3d.vertex.VertexConsumer setUv2(int, int);
  public abstract com.mojang.blaze3d.vertex.VertexConsumer setUv3(float, float);
  public abstract com.mojang.blaze3d.vertex.VertexConsumer setNormal(float, float, float);
  public abstract com.mojang.blaze3d.vertex.VertexConsumer setLineWidth(float);
  public default void addVertex(float, float, float, int, float, float, int, int, float, float, float);
  public default com.mojang.blaze3d.vertex.VertexConsumer setColor(float, float, float, float);
  public default com.mojang.blaze3d.vertex.VertexConsumer setLight(int);
  public default com.mojang.blaze3d.vertex.VertexConsumer setOverlay(int);
  public default void putBlockBakedQuad(float, float, float, net.minecraft.client.resources.model.geometry.BakedQuad, com.mojang.blaze3d.vertex.QuadInstance);
  public default void putBakedQuad(com.mojang.blaze3d.vertex.PoseStack$Pose, net.minecraft.client.resources.model.geometry.BakedQuad, com.mojang.blaze3d.vertex.QuadInstance);
  public default void putBakedQuadWithGlint(com.mojang.blaze3d.vertex.PoseStack$Pose, net.minecraft.client.resources.model.geometry.BakedQuad, com.mojang.blaze3d.vertex.QuadInstance, com.mojang.blaze3d.vertex.PoseStack$P
  public default com.mojang.blaze3d.vertex.VertexConsumer addVertex(org.joml.Vector3fc);
  public default com.mojang.blaze3d.vertex.VertexConsumer addVertex(com.mojang.blaze3d.vertex.PoseStack$Pose, org.joml.Vector3fc);
  public default com.mojang.blaze3d.vertex.VertexConsumer addVertex(com.mojang.blaze3d.vertex.PoseStack$Pose, float, float, float);
  public default com.mojang.blaze3d.vertex.VertexConsumer addVertex(org.joml.Matrix4fc, float, float, float);
  public default com.mojang.blaze3d.vertex.VertexConsumer addVertexWith2DPose(org.joml.Matrix3x2fc, float, float);
  public default com.mojang.blaze3d.vertex.VertexConsumer setNormal(com.mojang.blaze3d.vertex.PoseStack$Pose, float, float, float);
  public default com.mojang.blaze3d.vertex.VertexConsumer setNormal(com.mojang.blaze3d.vertex.PoseStack$Pose, org.joml.Vector3fc);
}
```

</details>

<details><summary><b>PoseStack</b></summary>

```java
Compiled from "PoseStack.java"
public class com.mojang.blaze3d.vertex.PoseStack {
  public com.mojang.blaze3d.vertex.PoseStack();
  public void translate(double, double, double);
  public void translate(float, float, float);
  public void translate(net.minecraft.world.phys.Vec3);
  public void scale(float, float, float);
  public void rotate(org.joml.Quaternionfc);
  public void rotate(com.mojang.math.Axis, float);
  public void rotateDegrees(com.mojang.math.Axis, float);
  public void rotateAround(org.joml.Quaternionfc, float, float, float);
  public void pushPose();
  public void popPose();
  public com.mojang.blaze3d.vertex.PoseStack$Pose last();
  public boolean isEmpty();
  public void setIdentity();
  public void mulPose(org.joml.Matrix4fc);
  public void mulPose(com.mojang.math.Transformation);
}
```

</details>

<details><summary><b>PoseStack$Pose</b></summary>

```java
Compiled from "PoseStack.java"
public final class com.mojang.blaze3d.vertex.PoseStack$Pose {
  public com.mojang.blaze3d.vertex.PoseStack$Pose();
  public void set(com.mojang.blaze3d.vertex.PoseStack$Pose);
  public org.joml.Matrix4f pose();
  public org.joml.Matrix3f normal();
  public org.joml.Vector3f transformNormal(org.joml.Vector3fc, org.joml.Vector3f);
  public org.joml.Vector3f transformNormal(float, float, float, org.joml.Vector3f);
  public org.joml.Matrix4f translate(float, float, float);
  public void scale(float, float, float);
  public void rotate(org.joml.Quaternionfc);
  public void rotate(com.mojang.math.Axis, float);
  public void rotateDegrees(com.mojang.math.Axis, float);
  public void rotateAround(org.joml.Quaternionfc, float, float, float);
  public void setIdentity();
  public void mulPose(org.joml.Matrix4fc);
  public void mulPose(com.mojang.math.Transformation);
  public com.mojang.blaze3d.vertex.PoseStack$Pose copy();
}
```

</details>

<details><summary><b>EntityRenderer</b></summary>

```java
Compiled from "EntityRenderer.java"
public abstract class net.minecraft.client.renderer.entity.EntityRenderer<T extends net.minecraft.world.entity.Entity, S extends net.minecraft.client.renderer.entity.state.EntityRenderState> {
  public static final float NAMETAG_SCALE;
  public final int getPackedLightCoords(T, float);
  public boolean shouldRender(T, net.minecraft.client.renderer.culling.Frustum, double, double, double, float);
  public net.minecraft.world.phys.Vec3 getRenderOffset(S);
  public void submit(S, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.SubmitNodeCollector, net.minecraft.client.renderer.state.level.CameraRenderState);
  public net.minecraft.client.gui.Font getFont();
  public abstract S createRenderState();
  public final S createRenderState(T, float);
  public void extractRenderState(T, S, float);
}
```

</details>

<details><summary><b>LivingEntityRenderer</b></summary>

```java
Compiled from "LivingEntityRenderer.java"
public abstract class net.minecraft.client.renderer.entity.LivingEntityRenderer<T extends net.minecraft.world.entity.LivingEntity, S extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState, M extends n
  public net.minecraft.client.renderer.entity.LivingEntityRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider$Context, M, float);
  public M getModel();
  public void submit(S, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.SubmitNodeCollector, net.minecraft.client.renderer.state.level.CameraRenderState);
  public abstract net.minecraft.resources.Identifier getTextureLocation(S);
  public static int getOverlayCoords(net.minecraft.client.renderer.entity.state.LivingEntityRenderState, float);
  public boolean isEntityUpsideDown(T);
  public void extractRenderState(T, S, float);
  public void extractRenderState(net.minecraft.world.entity.Entity, net.minecraft.client.renderer.entity.state.EntityRenderState, float);
  public void submit(net.minecraft.client.renderer.entity.state.EntityRenderState, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.SubmitNodeCollector, net.minecraft.client.renderer.state.level.CameraR
}
```

</details>

<details><summary><b>AvatarRenderer</b></summary>

```java
Compiled from "AvatarRenderer.java"
public class net.minecraft.client.renderer.entity.player.AvatarRenderer<AvatarlikeEntity extends net.minecraft.world.entity.Avatar & net.minecraft.client.entity.ClientAvatarEntity> extends net.minecraft.client.renderer.e
  public net.minecraft.client.renderer.entity.player.AvatarRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider$Context, boolean);
  public net.minecraft.world.phys.Vec3 getRenderOffset(net.minecraft.client.renderer.entity.state.AvatarRenderState);
  public net.minecraft.resources.Identifier getTextureLocation(net.minecraft.client.renderer.entity.state.AvatarRenderState);
  public net.minecraft.client.renderer.entity.state.AvatarRenderState createRenderState();
  public void extractRenderState(AvatarlikeEntity, net.minecraft.client.renderer.entity.state.AvatarRenderState, float);
  public void renderRightHand(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.SubmitNodeCollector, int, net.minecraft.resources.Identifier, boolean);
  public void renderLeftHand(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.SubmitNodeCollector, int, net.minecraft.resources.Identifier, boolean);
  public boolean isEntityUpsideDown(AvatarlikeEntity);
  public static boolean isPlayerUpsideDown(net.minecraft.world.entity.player.Player);
  public void extractRenderState(net.minecraft.world.entity.LivingEntity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState, float);
  public boolean isEntityUpsideDown(net.minecraft.world.entity.LivingEntity);
  public net.minecraft.resources.Identifier getTextureLocation(net.minecraft.client.renderer.entity.state.LivingEntityRenderState);
  public void extractRenderState(net.minecraft.world.entity.Entity, net.minecraft.client.renderer.entity.state.EntityRenderState, float);
  public net.minecraft.client.renderer.entity.state.EntityRenderState createRenderState();
  public net.minecraft.world.phys.Vec3 getRenderOffset(net.minecraft.client.renderer.entity.state.EntityRenderState);
}
```

</details>

<details><summary><b>EntityRenderState</b></summary>

```java
Compiled from "EntityRenderState.java"
public class net.minecraft.client.renderer.entity.state.EntityRenderState {
  public static final int NO_OUTLINE;
  public net.minecraft.world.entity.EntityType<?> entityType;
  public double x;
  public double y;
  public double z;
  public float ageInTicks;
  public float boundingBoxWidth;
  public float boundingBoxHeight;
  public float eyeHeight;
  public double distanceToCameraSq;
  public boolean isInvisible;
  public boolean isDiscrete;
  public boolean displayFireAnimation;
  public int lightCoords;
  public int outlineColor;
  public net.minecraft.world.phys.Vec3 passengerOffset;
  public net.minecraft.network.chat.Component nameTag;
  public net.minecraft.network.chat.Component scoreText;
  public net.minecraft.world.phys.Vec3 nameTagAttachment;
  public java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState$LeashState> leashStates;
  public float shadowRadius;
  public final java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState$ShadowPiece> shadowPieces;
  public net.minecraft.client.renderer.entity.state.EntityRenderState();
  public boolean appearsGlowing();
  public void fillCrashReportCategory(net.minecraft.CrashReportCategory);
}
```

</details>

<details><summary><b>LivingEntityRenderState</b></summary>

```java
Compiled from "LivingEntityRenderState.java"
public class net.minecraft.client.renderer.entity.state.LivingEntityRenderState extends net.minecraft.client.renderer.entity.state.EntityRenderState {
  public float bodyRot;
  public float yRot;
  public float xRot;
  public float deathTime;
  public float walkAnimationPos;
  public float walkAnimationSpeed;
  public float scale;
  public float ageScale;
  public float ticksSinceKineticHitFeedback;
  public boolean isUpsideDown;
  public boolean isFullyFrozen;
  public boolean isBaby;
  public boolean isInWater;
  public boolean isAutoSpinAttack;
  public boolean hasRedOverlay;
  public boolean isInvisibleToPlayer;
  public net.minecraft.core.Direction bedOrientation;
  public net.minecraft.world.entity.Pose pose;
  public final net.minecraft.client.renderer.item.ItemStackRenderState headItem;
  public float wornHeadAnimationPos;
  public net.minecraft.world.level.block.SkullBlock$Type wornHeadType;
  public net.minecraft.world.item.component.ResolvableProfile wornHeadProfile;
  public net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
  public boolean hasPose(net.minecraft.world.entity.Pose);
}
```

</details>

<details><summary><b>AvatarRenderState</b></summary>

```java
Compiled from "AvatarRenderState.java"
public class net.minecraft.client.renderer.entity.state.AvatarRenderState extends net.minecraft.client.renderer.entity.state.HumanoidRenderState {
  public net.minecraft.world.entity.player.PlayerSkin skin;
  public float capeFlap;
  public float capeLean;
  public float capeLean2;
  public int arrowCount;
  public int stingerCount;
  public boolean isSpectator;
  public boolean showHat;
  public boolean showJacket;
  public boolean showLeftPants;
  public boolean showRightPants;
  public boolean showLeftSleeve;
  public boolean showRightSleeve;
  public boolean showCape;
  public float fallFlyingTimeInTicks;
  public boolean shouldApplyFlyingYRot;
  public float flyingYRot;
  public net.minecraft.world.entity.animal.parrot.Parrot$Variant parrotOnLeftShoulder;
  public net.minecraft.world.entity.animal.parrot.Parrot$Variant parrotOnRightShoulder;
  public int id;
  public boolean showExtraEars;
  public final net.minecraft.client.renderer.item.ItemStackRenderState heldOnHead;
  public net.minecraft.client.renderer.entity.state.AvatarRenderState();
  public float fallFlyingScale();
}
```

</details>

<details><summary><b>CameraRenderState</b></summary>

```java
Compiled from "CameraRenderState.java"
public class net.minecraft.client.renderer.state.level.CameraRenderState {
  public net.minecraft.core.BlockPos blockPos;
  public net.minecraft.world.phys.Vec3 pos;
  public float xRot;
  public float yRot;
  public boolean initialized;
  public boolean isPanoramicMode;
  public boolean isFrustumCaptured;
  public boolean isFirstPerson;
  public boolean smartCull;
  public org.joml.Quaternionf orientation;
  public float cameraEntityPartialTicks;
  public net.minecraft.client.renderer.culling.Frustum cullFrustum;
  public net.minecraft.world.level.material.FogType fogType;
  public net.minecraft.client.renderer.fog.FogData fogData;
  public float hudFov;
  public float depthFar;
  public org.joml.Matrix4f projectionMatrix;
  public org.joml.Matrix4f viewRotationMatrix;
  public net.minecraft.client.renderer.state.level.CameraEntityRenderState entityRenderState;
  public net.minecraft.client.renderer.state.level.CameraRenderState();
}
```

</details>

