package com.shiroha.mmdskin.render.backend.factory;

import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.bridge.runtime.PlatformCapabilityPort;
import com.shiroha.mmdskin.render.backend.mode.ModelInstanceFactory;
import com.shiroha.mmdskin.render.backend.mode.RenderModeManager;
import java.util.List;

/**
 * 模型工厂注册器。
 */
public final class ModelFactoryRegistry {
    private static boolean registered = false;

    private ModelFactoryRegistry() {

    }

    public static void registerAll(NativeRenderBackendPort nativeRenderBackendPort,
                                   PlatformCapabilityPort platformCapabilityPort) {
        if (registered) {
            return;
        }

        // Minecraft 26.3 removed the immediate-mode OpenGL state the old CPU/GPU skinning backends were
        // built on (they stay excluded from compilation, see common/build.gradle). Models are drawn
        // through the vanilla submit pipeline instead.
        List<ModelInstanceFactory> builtInFactories = List.of(
                new SubmitBackendFactory(nativeRenderBackendPort)
        );
        for (ModelInstanceFactory factory : builtInFactories) {
            RenderModeManager.registerFactory(factory);
        }

        registered = true;
    }
}
