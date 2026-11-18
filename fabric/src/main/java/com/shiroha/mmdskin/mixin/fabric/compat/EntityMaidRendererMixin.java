package com.shiroha.mmdskin.mixin.fabric.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.maid.render.MaidMMDRenderer;
import com.shiroha.mmdskin.compat.maid.runtime.MaidMMDModelManager;
import com.shiroha.mmdskin.fabric.maid.MaidCompatMixinPlugin;
import com.shiroha.mmdskin.render.backend.submit.RenderSubmitContext;
import com.shiroha.mmdskin.render.entity.EntityRenderStateAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TouhouLittleMaid (Orihime) 女仆渲染兼容 Mixin
 *
 * 注入 LivingEntityRenderer.submit() 的 HEAD，
 * 运行时通过类名检测女仆实体，若已绑定 MMD 模型则替换渲染。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class EntityMaidRendererMixin {

    @Unique
    private static final Logger MMDSKIN_LOGGER = LogManager.getLogger();

    @Unique
    private static boolean mmdskin$failureLogged;

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mmdskin$onSubmitMaid(LivingEntityRenderState state, PoseStack poseStack,
                                       SubmitNodeCollector collector, CameraRenderState camera,
                                       CallbackInfo ci) {
        if (!MaidCompatMixinPlugin.isMaidModLoaded()) {
            return;
        }

        if (!(state instanceof EntityRenderStateAccess access)) {
            return;
        }
        Entity entity = access.mmdskin$entity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        String className = entity.getClass().getName();
        if (!className.contains("EntityMaid") && !className.contains("touhoulittlemaid")) {
            return;
        }

        if (!MaidMMDModelManager.hasMMDModel(entity.getUUID())) {
            return;
        }

        float partialTick = access.mmdskin$partialTick();
        int packedLight = state.lightCoords;
        float entityYaw = living.getYRot(partialTick);

        SubmitNodeCollector previous = RenderSubmitContext.begin(collector);
        try {
            poseStack.pushPose();
            poseStack.translate(0, 0.01, 0);

            boolean rendered;
            try {
                rendered = MaidMMDRenderer.render(
                    living,
                    entity.getUUID(),
                    entityYaw,
                    partialTick,
                    poseStack,
                    packedLight
                );
            } catch (RuntimeException e) {
                if (!mmdskin$failureLogged) {
                    mmdskin$failureLogged = true;
                    MMDSKIN_LOGGER.error("Maid MMD model render failed for {}; falling back to vanilla rendering",
                        entity.getType(), e);
                }
                rendered = false;
            }

            poseStack.popPose();

            if (rendered) {
                ci.cancel();
            }
        } finally {
            RenderSubmitContext.restore(previous);
        }
    }
}
