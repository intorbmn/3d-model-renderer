package com.shiroha.mmdskin.mixin.fabric;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Creates render types for the mod's pipelines (the factory is package-private). */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    @Invoker("create")
    static RenderType mmdskin$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
