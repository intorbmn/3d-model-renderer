package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.render.backend.submit.RenderSubmitContext;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 标记 GUI 实体预览（背包界面等）的渲染区间，让模型按预览姿态而不是世界姿态绘制。 */
@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityRendererMixin {
    @Inject(
        method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At("HEAD")
    )
    private void mmdskin$enterGuiPass(CallbackInfo ci) {
        RenderSubmitContext.enterGuiPass();
    }

    @Inject(
        method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At("RETURN")
    )
    private void mmdskin$exitGuiPass(CallbackInfo ci) {
        RenderSubmitContext.exitGuiPass();
    }
}
