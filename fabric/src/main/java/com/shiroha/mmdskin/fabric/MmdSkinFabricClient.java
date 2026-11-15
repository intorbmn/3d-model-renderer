package com.shiroha.mmdskin.fabric;

import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.fabric.config.MmdSkinConfig;
import com.shiroha.mmdskin.fabric.maid.MaidCompatMixinPlugin;
import com.shiroha.mmdskin.fabric.register.MmdSkinRegisterClient;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric 客户端初始化
 */
public class MmdSkinFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MmdSkinConfig.init();
        // Before resources load, so the toon shader is compiled with Minecraft's.
        com.shiroha.mmdskin.render.toon.ToonRendering.init(new com.shiroha.mmdskin.render.toon.ToonRendering.Hooks() {
            @Override
            public com.mojang.renderpearl.api.pipeline.RenderPipeline registerOptional(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline) {
                return com.shiroha.mmdskin.mixin.fabric.RenderPipelinesAccessor.mmdskin$registerOptional(pipeline);
            }

            @Override
            public net.minecraft.client.renderer.rendertype.RenderType createRenderType(String name,
                    net.minecraft.client.renderer.rendertype.RenderSetup setup) {
                return com.shiroha.mmdskin.mixin.fabric.RenderTypeAccessor.mmdskin$create(name, setup);
            }
        });
        MmdSkinClient.initClient();
        MmdSkinRegisterClient.Register();
        ClientRenderRuntime.get().renderBackendSettings().setShaderEnabled(com.shiroha.mmdskin.config.ConfigManager.isMMDShaderEnabled());
    }
}
