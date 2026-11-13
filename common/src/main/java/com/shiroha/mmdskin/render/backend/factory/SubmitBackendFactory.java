package com.shiroha.mmdskin.render.backend.factory;

import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.render.backend.mode.ModelInstanceFactory;
import com.shiroha.mmdskin.render.backend.mode.RenderCategory;
import com.shiroha.mmdskin.render.backend.submit.SubmitModelInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：创建通过 Minecraft 26.3 提交式渲染管线绘制的 CPU 蒙皮模型。 */
public final class SubmitBackendFactory implements ModelInstanceFactory {
    private static final Logger logger = LogManager.getLogger();

    private final NativeRenderBackendPort nativeRenderBackendPort;

    public SubmitBackendFactory(NativeRenderBackendPort nativeRenderBackendPort) {
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
        return "CPU skinning (vanilla render pipeline)";
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
        ModelInstance instance = createModelFromHandle(handle, modelDir);
        if (instance == null) {
            nativeRenderBackendPort.deleteModel(handle);
        }
        return instance;
    }

    @Override
    public ModelInstance createModelFromHandle(long modelHandle, String modelDir) {
        try {
            return SubmitModelInstance.createFromHandle(nativeRenderBackendPort, modelHandle, modelDir);
        } catch (RuntimeException | Error e) {
            logger.error("Failed to create a render instance for model in '{}'", modelDir, e);
            return null;
        }
    }
}
