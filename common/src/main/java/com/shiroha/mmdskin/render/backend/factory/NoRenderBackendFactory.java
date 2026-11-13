package com.shiroha.mmdskin.render.backend.factory;

import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.render.backend.mode.ModelInstanceFactory;
import com.shiroha.mmdskin.render.backend.mode.RenderCategory;
import com.shiroha.mmdskin.render.backend.noop.NoRenderModelInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Factory used while the render backend is disabled for the Minecraft 26.3 port.
 * It loads models through the native engine but never draws them.
 */
public final class NoRenderBackendFactory implements ModelInstanceFactory {
    private static final Logger logger = LogManager.getLogger();

    private final NativeRenderBackendPort nativeRenderBackendPort;

    public NoRenderBackendFactory(NativeRenderBackendPort nativeRenderBackendPort) {
        if (nativeRenderBackendPort == null) {
            throw new IllegalArgumentException("nativeRenderBackendPort cannot be null");
        }
        this.nativeRenderBackendPort = nativeRenderBackendPort;
    }

    @Override
    public RenderCategory getCategory() {
        return RenderCategory.CPU_SKINNING;
    }

    @Override
    public String getModeName() {
        return "Render backend disabled (MC 26.3 port)";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public ModelInstance createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        long handle = isPMD
                ? nativeRenderBackendPort.loadPmdModel(modelFilename, modelDir, layerCount)
                : nativeRenderBackendPort.loadPmxModel(modelFilename, modelDir, layerCount);
        if (handle == 0) {
            logger.warn("Cannot open model: '{}'.", modelFilename);
            return null;
        }
        return NoRenderModelInstance.createFromHandle(nativeRenderBackendPort, handle, modelDir);
    }

    @Override
    public ModelInstance createModelFromHandle(long modelHandle, String modelDir) {
        return NoRenderModelInstance.createFromHandle(nativeRenderBackendPort, modelHandle, modelDir);
    }
}
