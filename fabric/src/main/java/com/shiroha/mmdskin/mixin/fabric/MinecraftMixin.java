package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft Mixin — 舞台模式下拦截暂停和游戏按键
 *
 * pauseGame: 阻止 ESC/失焦 打开 PauseScreen（由 MMDCameraController 统一处理 ESC）
 * handleKeybinds: 阻止舞台播放期间的攻击、使用物品等游戏操作
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void onPauseGame(boolean showPauseMenu, CallbackInfo ci) {
        if (MMDCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void onHandleKeybinds(CallbackInfo ci) {
        if (MMDCameraController.getInstance().shouldBlockInput()) {
            ci.cancel();
        }
    }

    /** 第一人称 MMD 相机下，按原版眼睛位置校正准星拾取结果。26.3 中 pick 从 GameRenderer 移到了 Minecraft。 */
    @Inject(method = "pick(F)V", at = @At("RETURN"), require = 0)
    private void mmdskin$adjustPickResult(float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !FirstPersonManager.shouldUseVanillaReachValidation(player)) {
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        Vec3 viewDir = player.getViewVector(partialTick);
        if (viewDir.lengthSqr() < 1.0E-6) return;

        Vec3 vanillaEyePos = FirstPersonManager.getVanillaEyePosition(player, partialTick);
        double blockRange = player.blockInteractionRange();
        double entityRange = player.entityInteractionRange();
        HitResult missHit = player.pick(0.0D, partialTick, false);
        HitResult blockHit = player.pick(blockRange, partialTick, false);
        HitResult vanillaRayBlockHit = mc.level.clip(
                new ClipContext(
                        vanillaEyePos,
                        vanillaEyePos.add(viewDir.scale(blockRange)),
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        player
                )
        );
        HitResult allowedBlockHit = mmdskin$filterBlockHitByVanillaReach(blockHit, vanillaRayBlockHit);
        HitResult finalHit = allowedBlockHit != null ? allowedBlockHit : missHit;
        mc.crosshairPickEntity = null;

        if (entityRange > 1.0E-6D) {
            Vec3 entityEnd = cameraPos.add(viewDir.scale(entityRange));
            AABB searchBox = player.getBoundingBox().expandTowards(viewDir.scale(entityRange)).inflate(1.0D, 1.0D, 1.0D);
            double maxDistanceSqr = mmdskin$getHitDistanceSqr(cameraPos, blockHit, entityRange);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    player, cameraPos, entityEnd, searchBox,
                    target -> !target.isSpectator() && target.isPickable(),
                    maxDistanceSqr
            );
            EntityHitResult allowedEntityHit = mmdskin$filterEntityHitByVanillaReach(vanillaEyePos, entityHit, entityRange);
            if (allowedEntityHit != null) {
                double entityDistanceSqr = cameraPos.distanceToSqr(allowedEntityHit.getLocation());
                double blockDistanceSqr = mmdskin$getHitDistanceSqr(cameraPos, allowedBlockHit, entityRange);
                if (entityDistanceSqr <= entityRange * entityRange + 1.0E-6D && entityDistanceSqr <= blockDistanceSqr) {
                    finalHit = allowedEntityHit;
                    Entity target = allowedEntityHit.getEntity();
                    if (target instanceof LivingEntity || target instanceof ItemFrame) {
                        mc.crosshairPickEntity = target;
                    }
                }
            }
        }

        mc.hitResult = finalHit;
    }

    private static HitResult mmdskin$filterBlockHitByVanillaReach(HitResult cameraHit, HitResult vanillaHit) {
        if (cameraHit == null || vanillaHit == null) return null;
        if (cameraHit.getType() != HitResult.Type.BLOCK || vanillaHit.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult c = (BlockHitResult) cameraHit;
        BlockHitResult v = (BlockHitResult) vanillaHit;
        return c.getBlockPos().equals(v.getBlockPos()) ? cameraHit : null;
    }

    private static EntityHitResult mmdskin$filterEntityHitByVanillaReach(Vec3 vanillaEyePos, EntityHitResult hitResult, double maxRange) {
        if (hitResult == null) return null;
        return vanillaEyePos.distanceToSqr(hitResult.getLocation()) <= maxRange * maxRange + 1.0E-6D ? hitResult : null;
    }

    private static double mmdskin$getHitDistanceSqr(Vec3 cameraPos, HitResult hitResult, double fallbackRange) {
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) return fallbackRange * fallbackRange;
        return cameraPos.distanceToSqr(hitResult.getLocation());
    }
}
