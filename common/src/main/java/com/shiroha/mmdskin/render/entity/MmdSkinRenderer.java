package com.shiroha.mmdskin.render.entity;


import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.render.backend.submit.RenderSubmitContext;
import com.shiroha.mmdskin.render.scene.RenderScene;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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

        SubmitNodeCollector previous = RenderSubmitContext.begin(collector);
        try {
            boolean guiPass = RenderSubmitContext.isGuiPass();
            float scale = guiPass ? size[1] : size[0];
            matrixStackIn.scale(scale, scale, scale);
            model.modelInstance().render(entityIn, reusablePose.bodyYaw, reusablePose.bodyPitch, reusablePose.translation,
                             tickDelta, matrixStackIn, packedLightIn, guiPass ? RenderScene.INVENTORY : RenderScene.WORLD);
        } finally {
            RenderSubmitContext.restore(previous);
            matrixStackIn.popPose();
        }
    }

    private static float[] parseModelSize(ManagedModel model, float[] out) {
        out[0] = model.renderProperties().modelScale();
        out[1] = model.renderProperties().inventoryScale();
        return out;
    }
}
