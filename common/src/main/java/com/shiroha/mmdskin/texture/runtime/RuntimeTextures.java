package com.shiroha.mmdskin.texture.runtime;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** 文件职责：把运行时解码的模型贴图注册到 Minecraft 的纹理管理器，并提供共享的纯白贴图。 */
public final class RuntimeTextures {
    private static final String NAMESPACE = "mmdskin";
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private static Identifier white;

    private RuntimeTextures() {
    }

    /** Registers {@code image} as a texture and returns its id. Must run on the render thread. */
    static Identifier register(NativeImage image) {
        long id = NEXT_ID.incrementAndGet();
        Identifier location = Identifier.fromNamespaceAndPath(NAMESPACE, "runtime/texture_" + id);
        DynamicTexture texture = new DynamicTexture(() -> "mmdskin runtime texture " + id, image);
        Minecraft.getInstance().getTextureManager().register(location, texture);
        return location;
    }

    static void release(Identifier location) {
        Minecraft.getInstance().getTextureManager().release(location);
    }

    /** A shared opaque white texture, for materials that have a colour but no texture. */
    public static Identifier white() {
        if (white == null) {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, 2, 2, false);
            try {
                image.fillRect(0, 0, 2, 2, 0xFFFFFFFF);
                white = register(image);
            } catch (RuntimeException | Error e) {
                image.close();
                throw e;
            }
        }
        return white;
    }
}
