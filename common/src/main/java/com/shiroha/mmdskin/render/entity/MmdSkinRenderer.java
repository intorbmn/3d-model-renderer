package com.shiroha.mmdskin.render.entity;

import com.shiroha.mmdskin.player.render.InventoryRenderHelper;

import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.render.scene.RenderScene;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * MMD 自定义实体渲染器。
 *
 * <p>Minecraft 26.3 renders entities in two steps: {@link #extractRenderState} copies what is needed
 * into a render state, then {@link #submit} draws from that state. The render state does not carry the
 * entity, so the entity and frame values the MMD pipeline needs are stored on {@link MmdRenderState}.
 */
public class MmdSkinRenderer<T extends Entity> extends EntityRenderer<T, MmdSkinRenderer.MmdRenderState> {

    /** Per-frame data handed from extract to submit. */
    public static final class MmdRenderState extends EntityRenderState {
        Entity entity;
        float partialTick;
        float entityYaw;
        int packedLight;
    }

    protected final String modelName;

    private final MutableRenderPose reusablePose = new MutableRenderPose();
    private final Quaternionf reusableQuat = new Quaternionf();
    private final Vector3f reusableVec = new Vector3f();
    private final float[] reusableSize = new float[2];

    public MmdSkinRenderer(EntityRendererProvider.Context renderManager, String entityName) {
        super(renderManager);
        this.modelName = entityName.replace(':', '.');
    }

    @Override
    public MmdRenderState createRenderState() {
        return new MmdRenderState();
    }

    @Override
    public void extractRenderState(T entity, MmdRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
        state.partialTick = partialTick;
        state.entityYaw = entity.getYRot(partialTick);
        state.packedLight = getPackedLightCoords(entity, partialTick);
    }

    @Override
    public void submit(MmdRenderState state, PoseStack matrixStackIn,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, matrixStackIn, collector, camera);

        Entity entityIn = state.entity;
        if (entityIn == null) return;
        float entityYaw = state.entityYaw;
        float tickDelta = state.partialTick;
        int packedLightIn = state.packedLight;

        ManagedModel model = ClientRenderRuntime.get().modelRepository()
                .acquire(ModelRequestKey.mob(entityIn, modelName));
        if (model == null) return;

        float[] size = parseModelSize(model, reusableSize);

        reusablePose.reset();
        EntityAnimationResolver.resolve(entityIn, model, entityYaw, tickDelta, reusablePose);

        matrixStackIn.pushPose();

        if (entityIn instanceof LivingEntity living && living.isBaby()) {
            matrixStackIn.scale(0.5f, 0.5f, 0.5f);
        }

        if (InventoryRenderHelper.isInventoryScreen()) {
            renderInInventory(entityIn, model, entityYaw, tickDelta, packedLightIn, size);
        } else {
            matrixStackIn.scale(size[0], size[0], size[0]);
            model.modelInstance().render(entityIn, reusablePose.bodyYaw, reusablePose.bodyPitch, reusablePose.translation,
                             tickDelta, matrixStackIn, packedLightIn, RenderScene.WORLD);
        }

        matrixStackIn.popPose();
    }

    private void renderInInventory(Entity entityIn, ManagedModel model, float entityYaw,
                                    float tickDelta, int packedLight, float[] size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null) return;

        // RenderSystem.getModelViewStack() no longer exposes a PoseStack in 26.3, so the GUI transform
        // is built on a local stack. The inventory draw path is part of the render-backend port.
        PoseStack modelViewStack = new PoseStack();

        int posX = (mc.gui.screen().width - 176) / 2;
        int posY = (mc.gui.screen().height - 166) / 2;
        modelViewStack.translate(posX + 51, posY + 60, 50.0);
        modelViewStack.pushPose();
        modelViewStack.scale(20.0f, 20.0f, -20.0f);
        modelViewStack.scale(size[1], size[1], size[1]);

        reusableQuat.identity()
                .rotateZ((float) Math.PI)
                .rotateX(-entityIn.getXRot() * ((float) Math.PI / 180F))
                .rotateY(-entityIn.getYRot() * ((float) Math.PI / 180F));
        modelViewStack.rotate(reusableQuat);

        reusableVec.set(0.0f);
        model.modelInstance().render(entityIn, entityYaw, 0.0f, reusableVec,
                          tickDelta, modelViewStack, packedLight, RenderScene.INVENTORY);
        modelViewStack.popPose();
    }

    private static float[] parseModelSize(ManagedModel model, float[] out) {
        out[0] = model.renderProperties().modelScale();
        out[1] = model.renderProperties().inventoryScale();
        return out;
    }
}
