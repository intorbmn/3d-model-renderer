package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.fabric.YsmCompat;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.compat.iris.IrisCompat;
import com.shiroha.mmdskin.player.sync.PlayerModelSyncService;
import net.minecraft.client.Camera;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * LevelExtractor Mixin，用于在 MMD 第一人称与 VR 场景下决定本地玩家是否强制渲染。
 *
 * <p>26.3 中实体可见性判断从 LevelRenderer#renderLevel 移到了 LevelExtractor#extractVisibleEntities。
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

    @Redirect(
        method = "extractVisibleEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z", ordinal = 0)
    )
    private boolean onCameraIsDetached(Camera camera) {
        if (IrisCompat.isRenderingShadows()) {
            return camera.isDetached();
        }

        Entity entity = camera.entity();
        if (!(entity instanceof AbstractClientPlayer player)) {
            return camera.isDetached();
        }

        String playerName = player.getName().getString();
        String selectedModel = PlayerModelSyncService.getPlayerModel(player.getUUID(), playerName, true);

        boolean isMmdDefault = selectedModel == null || selectedModel.isEmpty()
                || selectedModel.equals("默认 (原版渲染)");
        boolean isMmdActive = !isMmdDefault;
        boolean isVanilaMmdModel = isMmdActive && (selectedModel.equals("VanillaModel")
                || selectedModel.equalsIgnoreCase("vanilla")
                || selectedModel.equals("VanilaModel")
                || selectedModel.equalsIgnoreCase("vanila"));

        if (isMmdActive && !isVanilaMmdModel && VRArmHider.isLocalPlayerInVR()) {
            return true;
        }

        if (FirstPersonManager.shouldRenderFirstPerson() && isMmdActive && !isVanilaMmdModel) {

            if (YsmCompat.isYsmModelActive(player)) {
                if (YsmCompat.isDisableSelfModel()) {
                    return camera.xRot() >= 0;
                }
                return false;
            }

            return camera.xRot() >= 0;
        }

        return camera.isDetached();
    }
}
