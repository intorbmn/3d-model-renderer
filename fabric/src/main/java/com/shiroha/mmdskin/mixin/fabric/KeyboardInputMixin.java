package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KeyboardInput Mixin — 舞台模式下清零移动输入
 *
 * <p>In 26.3 the pressed keys live in the {@link Input} record on {@link ClientInput#keyPresses}
 * and {@code tick()} no longer takes arguments.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onStageTick(CallbackInfo ci) {
        if (MMDCameraController.getInstance().shouldBlockInput()) {
            this.keyPresses = Input.EMPTY;
        }
    }
}
