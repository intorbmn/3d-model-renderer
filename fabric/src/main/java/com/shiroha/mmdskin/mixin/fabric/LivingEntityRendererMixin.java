package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.fabric.YsmCompat;
import com.shiroha.mmdskin.player.render.PlayerRenderAction;
import com.shiroha.mmdskin.player.render.PlayerRenderEntrypoint;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.render.backend.submit.RenderSubmitContext;
import com.shiroha.mmdskin.render.entity.EntityRenderStateAccess;
import com.shiroha.mmdskin.render.entity.MobReplacementRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
 * 玩家与原版生物的 MMD 模型渲染入口。
 *
 * <p>In 26.3 players are drawn by AvatarRenderer, which inherits {@code submit} from
 * LivingEntityRenderer, so one hook covers both players and mob replacement. When a model is drawn the
 * vanilla body is skipped, but {@link EntityRenderer#submit} still runs so name tags and leashes stay.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState>
        extends EntityRenderer<T, S> {

    @Unique
    private static final Logger MMDSKIN_LOGGER = LogManager.getLogger();

    @Unique
    private static boolean mmdskin$failureLogged;

    protected LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mmdskin$submitModel(S state, PoseStack poseStack, SubmitNodeCollector collector,
                                     CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof EntityRenderStateAccess access)) {
            return;
        }
        Entity entity = access.mmdskin$entity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        float partialTick = access.mmdskin$partialTick();
        int packedLight = state.lightCoords;

        boolean handled;
        SubmitNodeCollector previous = RenderSubmitContext.begin(collector);
        try {
            if (living instanceof AbstractClientPlayer player) {
                handled = mmdskin$submitPlayer(player, state, partialTick, poseStack, collector, packedLight);
            } else {
                handled = MobReplacementRenderer.render(living, living.getYRot(partialTick), partialTick, poseStack, packedLight);
            }
        } catch (RuntimeException e) {
            // A broken model must not take the whole frame down: fall back to the vanilla renderer.
            if (!mmdskin$failureLogged) {
                mmdskin$failureLogged = true;
                MMDSKIN_LOGGER.error("MMD model render failed for {}; falling back to vanilla rendering", entity.getType(), e);
            }
            handled = false;
        } finally {
            RenderSubmitContext.restore(previous);
        }

        if (handled) {
            super.submit(state, poseStack, collector, camera);
            ci.cancel();
        }
    }

    @Unique
    private static boolean mmdskin$submitPlayer(AbstractClientPlayer player, LivingEntityRenderState state, float partialTick,
                                                PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        boolean guiPass = RenderSubmitContext.isGuiPass();
        Minecraft minecraft = Minecraft.getInstance();
        boolean isLocalPlayer = minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
        if (!guiPass && isLocalPlayer && minecraft.options.getCameraType().isFirstPerson()
                && !FirstPersonManager.shouldRenderFirstPerson() && !VRArmHider.isLocalPlayerInVR()) {
            FirstPersonManager.reset();
            return false;
        }

        // In a GUI preview Minecraft overrides the facing on the render state rather than on the entity.
        float entityYaw = guiPass ? state.bodyRot : player.getYRot(partialTick);
        PlayerRenderAction action = PlayerRenderEntrypoint.handleRender(
                player, entityYaw, partialTick, poseStack, collector, packedLight,
                YsmCompat.isYsmActive(player));
        if (!guiPass) {
            PlayerRenderEntrypoint.renderSceneOverlay(player, partialTick, poseStack, packedLight);
        }
        return action == PlayerRenderAction.CANCEL;
    }
}
