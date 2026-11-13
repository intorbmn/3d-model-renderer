package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.config.RuntimeConfigPort;
import com.shiroha.mmdskin.config.RuntimeConfigPortHolder;
import com.shiroha.mmdskin.fabric.YsmCompat;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 相机 Mixin，用于接管舞台模式与第一人称 MMD 相机位置。 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    private Entity entity;

    @Shadow
    private boolean detached;

    /** 舞台模式 FOV 覆盖。26.3 中 GameRenderer#getFov 已移除，FOV 由 Camera#calculateFov 计算。 */
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
    private void onCalculateFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isActive()) {
            cir.setReturnValue(controller.getCameraFov());
        }
    }

    /**
     * 26.3 中 Camera#setup(level, entity, detached, mirrored, partialTick) 已移除；
     * 相机在 alignWithEntity(partialTick) 中对齐到实体，实体与 detached 状态改为字段。
     */
    @Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
    private void onAlignWithEntity(float partialTick, CallbackInfo ci) {
        Entity entity = this.entity;
        boolean detached = this.detached;
        if (entity == null) {
            return;
        }

        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isActive()) {
            controller.checkEscapeKey();
            if (controller.isActive()) {
                controller.updateCamera();
                if (controller.isActive()) {
                    this.setPosition(controller.getCameraX(), controller.getCameraY(), controller.getCameraZ());
                    this.setRotation(controller.getCameraYaw(), controller.getCameraPitch());
                }
            }
        } else {
            boolean vrEyeCameraActive = FirstPersonManager.isVrEyeCameraActive();
            boolean eyeCameraActive = FirstPersonManager.isEyeCameraActive();
            boolean eyeAnchorReady = vrEyeCameraActive || FirstPersonManager.isEyeBoneValid();
            if (eyeCameraActive && eyeAnchorReady && !detached) {

                if (entity instanceof LivingEntity living) {
                    boolean ysmActive = YsmCompat.isYsmModelActive(living);
                    boolean ysmDisableSelf = YsmCompat.isDisableSelfModel();
                    if (ysmActive && !ysmDisableSelf) {
                        return;
                    }
                }

                if (vrEyeCameraActive) {
                    Vec3 vrCameraPos = FirstPersonManager.getVrCameraPosition(entity, partialTick);
                    FirstPersonManager.setLastCameraPos(vrCameraPos);
                    this.setPosition(vrCameraPos.x, vrCameraPos.y, vrCameraPos.z);
                    return;
                }

                Vec3 boneEyePos = FirstPersonManager.getRotatedEyePosition(entity, partialTick);
                float originalYaw = entity.getViewYRot(partialTick);
                float originalPitch = entity.getViewXRot(partialTick);
                float lookPitchRad = originalPitch * ((float) Math.PI / 180F);
                float lookYawRad = originalYaw * ((float) Math.PI / 180F);
                float cosLookPitch = Mth.cos(lookPitchRad);
                float sinLookPitch = Mth.sin(lookPitchRad);
                float cosLookYaw = Mth.cos(lookYawRad);
                float sinLookYaw = Mth.sin(lookYawRad);
                RuntimeConfigPort runtimeConfig = RuntimeConfigPortHolder.get();

                double forwardOffset = runtimeConfig.getFirstPersonCameraForwardOffset();
                double verticalOffset = runtimeConfig.getFirstPersonCameraVerticalOffset();

                double targetX = boneEyePos.x + (double) (sinLookYaw * cosLookPitch * (float) (-forwardOffset));
                double targetY = boneEyePos.y + (double) (sinLookPitch * (float) (-forwardOffset)) + verticalOffset;
                double targetZ = boneEyePos.z + (double) (cosLookYaw * cosLookPitch * (float) forwardOffset);

                Vec3 finalPos = new Vec3(targetX, targetY, targetZ);
                FirstPersonManager.setLastCameraPos(finalPos);

                this.setPosition(finalPos.x, finalPos.y, finalPos.z);
                this.setRotation(originalYaw, originalPitch);
            }
        }
    }
}
