package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.fabric.YsmCompat;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.player.sync.PlayerModelSyncService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称手臂隐藏。
 *
 * <p>When the first-person MMD view is active the model's own arms are visible, so the vanilla
 * first-person hands and held items must not be drawn on top of them. In 26.3 this is
 * FirstPersonHandsAndItemsRenderer#submitHandsWithItems (formerly ItemInHandRenderer#renderHandsWithItems).
 *
 * <p>The per-arm Vivecraft hooks of the old mixin are not ported: Vivecraft calls those methods
 * itself and has no 26.3 build to verify against.
 */
@Mixin(value = FirstPersonHandsAndItemsRenderer.class, priority = 900)
public abstract class FirstPersonHandsMixin {

    @Inject(
        method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mmdskin$hideVanillaHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
                                          PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                          CallbackInfo ci) {
        if (VRArmHider.shouldHideVRArms()) {
            ci.cancel();
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        if (YsmCompat.isYsmModelActive(player)) {
            if (YsmCompat.isDisableSelfHands()) {
                ci.cancel();
            }
            return;
        }

        if (!FirstPersonManager.shouldRenderFirstPerson()) {
            return;
        }

        String selectedModel = PlayerModelSyncService.getPlayerModel(player.getUUID(), player.getName().getString(), true);
        boolean usesVanillaRendering = selectedModel == null
                || selectedModel.isEmpty()
                || selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME)
                || selectedModel.equals("VanilaModel")
                || selectedModel.equalsIgnoreCase("vanila");
        if (!usesVanillaRendering) {
            ci.cancel();
        }
    }
}
