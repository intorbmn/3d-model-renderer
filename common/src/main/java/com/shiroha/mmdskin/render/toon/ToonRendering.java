package com.shiroha.mmdskin.render.toon;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.shiroha.mmdskin.config.ConfigManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Toon (cel) shading on Minecraft 26.3: two pipelines with the mod's toon shader
 * ({@code assets/mmdskin/shaders/core/toon.*}) built like the vanilla entity pipelines, and render
 * types that use them. The toon settings reach the shader through a tiny texture, so changing them
 * needs no shader reload.
 *
 * <p>The pipelines are registered as optional: if the shader does not compile on a machine, the game
 * carries on and models are drawn with the vanilla entity shader as before ({@link #renderType}
 * returns null).
 */
public final class ToonRendering {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("mmdskin", "core/toon");
    private static final Identifier PARAMS = Identifier.fromNamespaceAndPath("mmdskin", "toon/params");
    private static final int PARAM_COUNT = 8;

    /** What the loader-specific side provides (mixin accessors to private Minecraft methods). */
    public interface Hooks {
        RenderPipeline registerOptional(RenderPipeline pipeline);

        RenderType createRenderType(String name, RenderSetup setup);
    }

    private static Hooks hooks;
    private static RenderPipeline cutout;
    private static RenderPipeline translucent;
    private static final Map<Identifier, RenderType> CUTOUT_TYPES = new HashMap<>();
    private static final Map<Identifier, RenderType> TRANSLUCENT_TYPES = new HashMap<>();
    private static String paramsKey;
    private static boolean warned;

    private ToonRendering() {
    }

    /** Registers the toon pipelines; call during client start-up, before resources load. */
    public static void init(Hooks loaderHooks) {
        hooks = loaderHooks;
        try {
            cutout = hooks.registerOptional(build("toon_cutout", RenderPipelines.ENTITY_CUTOUT, 0.1f));
            translucent = hooks.registerOptional(build("toon_translucent", RenderPipelines.ENTITY_TRANSLUCENT, 0.004f));
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Toon shading is unavailable: could not set up its pipelines", e);
            cutout = null;
            translucent = null;
        }
    }

    private static RenderPipeline build(String name, RenderPipeline base, float alphaCutout) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("mmdskin", "pipeline/" + name))
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withShaderDefine("ALPHA_CUTOUT", alphaCutout)
                .withVertexBinding(0, base.getVertexFormatBinding(0))
                .withPrimitiveTopology(base.getPrimitiveTopology())
                .withPolygonMode(base.getPolygonMode())
                .withDepthStencilState(Optional.ofNullable(base.getDepthStencilState()))
                .withCull(false);
        for (BindGroupLayout layout : base.getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }
        builder.withBindGroupLayout(BindGroupLayout.builder()
                .withUniform("ToonParams", UniformType.COMBINED_IMAGE_SAMPLER)
                .build());
        List<ColorTargetState> targets = base.getColorTargetStates();
        for (int i = 0; i < targets.size(); i++) {
            builder.withColorTargetState(i, targets.get(i));
        }
        if (base.pushConstantSize() > 0) {
            builder.withPushConstantSize(base.pushConstantSize());
        }
        return builder.build();
    }

    /**
     * The toon render type for a texture, or null when toon shading is off or its shader is not
     * available (then the caller uses the vanilla entity render type). Render thread only.
     */
    public static RenderType renderType(Identifier texture, boolean translucentMaterial) {
        if (!ConfigManager.isToonRenderingEnabled() || hooks == null || cutout == null || translucent == null) {
            return null;
        }
        RenderPipeline pipeline = translucentMaterial ? translucent : cutout;
        if (RenderSystem.getCompiledPipelineNullable(pipeline) == null) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Toon shading is unavailable: its shader did not compile; models use the default shader");
            }
            return null;
        }
        updateParams();
        Map<Identifier, RenderType> cache = translucentMaterial ? TRANSLUCENT_TYPES : CUTOUT_TYPES;
        return cache.computeIfAbsent(texture, id -> {
            RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", id)
                    .withTexture("ToonParams", PARAMS)
                    .useLightmap()
                    .useOverlay()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
            if (translucentMaterial) {
                setup.sortOnUpload();
            }
            return hooks.createRenderType("mmdskin_" + (translucentMaterial ? "toon_translucent" : "toon_cutout"),
                    setup.createRenderSetup());
        });
    }

    /** Writes the toon settings into the parameter texture when they changed. */
    private static void updateParams() {
        float[] values = {
                ConfigManager.getToonShadowR(), ConfigManager.getToonShadowG(), ConfigManager.getToonShadowB(),
                ConfigManager.getToonLevels() / 5.0f,
                ConfigManager.getToonRimPower() / 10.0f, ConfigManager.getToonRimIntensity(),
                ConfigManager.getToonSpecularPower() / 128.0f, ConfigManager.getToonSpecularIntensity(),
        };
        StringBuilder key = new StringBuilder();
        for (float value : values) {
            key.append(value).append(',');
        }
        if (key.toString().equals(paramsKey)) {
            return;
        }
        paramsKey = key.toString();
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, PARAM_COUNT, 1, false);
        for (int i = 0; i < PARAM_COUNT; i++) {
            int v = Math.round(Math.max(0.0f, Math.min(1.0f, values[i])) * 255.0f);
            // Grey, so the channel order of the image format does not matter.
            image.fillRect(i, 0, 1, 1, 0xFF000000 | v << 16 | v << 8 | v);
        }
        Minecraft.getInstance().getTextureManager().register(PARAMS, new DynamicTexture(() -> "mmdskin toon settings", image));
    }
}
