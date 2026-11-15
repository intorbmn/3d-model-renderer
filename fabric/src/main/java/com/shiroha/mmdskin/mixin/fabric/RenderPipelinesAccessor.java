package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Registers the mod's pipelines with Minecraft's, so they are compiled with the shaders on reload. */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Invoker("registerOptional")
    static RenderPipeline mmdskin$registerOptional(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
