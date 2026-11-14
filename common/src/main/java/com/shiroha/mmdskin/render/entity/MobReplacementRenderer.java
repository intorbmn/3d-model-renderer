package com.shiroha.mmdskin.render.entity;

import com.shiroha.mmdskin.companion.CompanionState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.render.policy.RenderPriorityService;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import com.shiroha.mmdskin.render.scene.RenderScene;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 原版生物的 MMD 替换渲染器。 */
public final class MobReplacementRenderer {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final MutableRenderPose REUSABLE_POSE = new MutableRenderPose();

    private MobReplacementRenderer() {
    }

    public static boolean render(LivingEntity entity, float entityYaw, float tickDelta, PoseStack poseStack, int packedLight) {
        String modelName = MobReplacementService.getReplacementModelName(entity);
        if (modelName == null) {
            return false;
        }

        if (!RenderPriorityService.get().shouldUseMobReplacement(entity)) {
            return false;
        }

        try {
            ManagedModel model = ClientRenderRuntime.get().modelRepository()
                    .acquire(ModelRequestKey.mob(entity, modelName));
            if (model == null || model.modelInstance() == null || model.entityState() == null) {
                return false;
            }

            float modelScale = model.renderProperties().modelScale();

            REUSABLE_POSE.reset();
            // An animation a player picked for this mob (model tag companions) plays over its own.
            CompanionState.Action action = CompanionState.action(entity.getUUID());
            if (action != null) {
                boolean moving = entity.getX() - entity.xo != 0.0 || entity.getZ() - entity.zo != 0.0;
                if (moving && action.startedFor != null) {
                    CompanionState.stop(entity.getUUID());
                    action = null;
                } else if (action.startedFor != model) {
                    long animation = model.animationLibrary().animation(action.animId);
                    if (animation != 0L) {
                        model.modelInstance().transitionAnim(animation, 0, 0.25f);
                    }
                    action.startedFor = model;
                    // Its own motion starts again afresh when the animation ends.
                    model.entityState().stateLayers[0] = null;
                }
            }
            EntityAnimationResolver.resolve(entity, model, entityYaw, tickDelta, REUSABLE_POSE, action == null);

            poseStack.pushPose();
            try {
                poseStack.translate(0.0D, 0.01D, 0.0D);

                if (entity.isBaby()) {
                    poseStack.scale(0.5f, 0.5f, 0.5f);
                }

                poseStack.scale(modelScale, modelScale, modelScale);
                model.modelInstance().render(entity, REUSABLE_POSE.bodyYaw, REUSABLE_POSE.bodyPitch, REUSABLE_POSE.translation,
                    tickDelta, poseStack, packedLight, RenderScene.WORLD);
                return true;
            } finally {
                poseStack.popPose();
            }
        } catch (Exception e) {
            LOGGER.warn("生物 MMD 渲染失败，回退原版渲染: {} -> {}", entity.getType(), modelName, e);
            return false;
        }
    }
}
