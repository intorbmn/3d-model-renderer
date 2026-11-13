package com.shiroha.mmdskin.render.backend.noop;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.render.backend.BaseModelInstance;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/**
 * Model instance for the Minecraft 26.3 port while the render backend is disabled.
 *
 * <p>The native model is still loaded, animated and updated, so animation, physics and
 * state-sync logic can be exercised, but nothing is drawn.
 */
public final class NoRenderModelInstance extends BaseModelInstance {

    private NoRenderModelInstance() {
    }

    public static NoRenderModelInstance createFromHandle(NativeRenderBackendPort nativeBackendPort,
                                                         long modelHandle,
                                                         String modelDirectory) {
        NoRenderModelInstance instance = new NoRenderModelInstance();
        instance.nativeRenderBackendPort = nativeBackendPort;
        instance.model = modelHandle;
        instance.modelDir = modelDirectory;
        return instance;
    }

    @Override
    protected void onUpdate(float deltaTime) {
        backendPort().updateModel(model, deltaTime);
    }

    @Override
    protected void doRenderModel(Entity entityIn,
                                 float entityYaw,
                                 float entityPitch,
                                 Vector3f entityTrans,
                                 PoseStack deliverStack,
                                 int packedLight) {
        // Intentionally empty: the render backend is disabled until it is ported to the 26.3 pipeline.
    }

    @Override
    public void dispose() {
        releaseTextures();
        disposeModelHandle();
        disposeMaterialMorphBuffers();
    }

    @Override
    public long getVramUsage() {
        return 0L;
    }
}
