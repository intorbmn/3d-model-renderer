package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.render.entity.EntityRenderStateAccess;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在提取阶段记录渲染状态对应的实体。 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(
        method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
        at = @At("RETURN")
    )
    private void mmdskin$captureEntity(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        if (cir.getReturnValue() instanceof EntityRenderStateAccess access) {
            access.mmdskin$capture(entity, partialTick);
        }
    }
}
