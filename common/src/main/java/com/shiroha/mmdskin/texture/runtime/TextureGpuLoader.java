package com.shiroha.mmdskin.texture.runtime;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.bridge.runtime.NativeTexturePort;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

/**
 * 文件职责：封装纹理从 native 解码到 GPU 上传的无状态操作。
 *
 * <p>Textures are registered with Minecraft's texture manager instead of being created with raw
 * OpenGL calls, so they can be referenced by render types and work on every graphics backend.
 */
final class TextureGpuLoader {
    /** A texture counts as blended once this share of its pixels is partially transparent. */
    private static final int TRANSLUCENT_PIXEL_DIVISOR = 1000;
    private static final int ALPHA_SEMI_MIN = 8;
    private static final int ALPHA_SEMI_MAX = 247;

    private TextureGpuLoader() {
    }

    static TextureRepository.Texture loadToGpu(String filename, NativeTexturePort port) {
        TextureRepository.PredecodedTexture decoded = decode(filename, port);
        return decoded != null ? uploadPredecoded(decoded) : null;
    }

    static TextureRepository.PredecodedTexture decode(String filename, NativeTexturePort port) {
        long handle = port.loadTexture(filename);
        if (handle == 0) return null;
        try {
            int width = port.textureWidth(handle);
            int height = port.textureHeight(handle);
            if (width <= 0 || height <= 0) return null;
            long dataAddr = port.textureData(handle);
            boolean hasAlpha = port.textureHasAlpha(handle);
            int size = width * height * (hasAlpha ? 4 : 3);
            ByteBuffer buf = MemoryUtil.memAlloc(size);
            port.copyTextureData(buf, dataAddr, size);
            buf.rewind();
            return new TextureRepository.PredecodedTexture(buf, width, height, hasAlpha);
        } finally {
            port.deleteTexture(handle);
        }
    }

    static TextureRepository.Texture uploadPredecoded(TextureRepository.PredecodedTexture pre) {
        try {
            int width = pre.width;
            int height = pre.height;
            ByteBuffer source = pre.pixelData();
            int pixels = width * height;
            int channels = pre.hasAlpha ? 4 : 3;
            if (source == null || pixels <= 0 || source.capacity() < (long) pixels * channels) {
                return null;
            }

            NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
            Identifier location;
            boolean translucent;
            try {
                translucent = copyPixels(source, pixels, pre.hasAlpha, image.getPointer());
                // The image is owned by the registered texture from here on.
                location = RuntimeTextures.register(image);
            } catch (RuntimeException | Error e) {
                image.close();
                throw e;
            }

            TextureRepository.Texture texture = new TextureRepository.Texture();
            texture.location = location;
            texture.hasAlpha = pre.hasAlpha;
            texture.translucent = translucent;
            texture.vramSize = (long) pixels * 4;
            return texture;
        } finally {
            pre.release();
        }
    }

    /**
     * Copies tightly packed RGB/RGBA bytes into an RGBA {@link NativeImage} buffer.
     *
     * @return whether a meaningful share of the pixels is partially transparent
     */
    private static boolean copyPixels(ByteBuffer source, int pixels, boolean hasAlpha, long target) {
        long sourceAddress = MemoryUtil.memAddress(source, 0);
        if (!hasAlpha) {
            for (int i = 0; i < pixels; i++) {
                long src = sourceAddress + (long) i * 3;
                long dst = target + (long) i * 4;
                MemoryUtil.memPutByte(dst, MemoryUtil.memGetByte(src));
                MemoryUtil.memPutByte(dst + 1, MemoryUtil.memGetByte(src + 1));
                MemoryUtil.memPutByte(dst + 2, MemoryUtil.memGetByte(src + 2));
                MemoryUtil.memPutByte(dst + 3, (byte) 0xFF);
            }
            return false;
        }

        MemoryUtil.memCopy(sourceAddress, target, (long) pixels * 4);
        int semiTransparent = 0;
        for (int i = 0; i < pixels; i++) {
            int alpha = MemoryUtil.memGetByte(sourceAddress + (long) i * 4 + 3) & 0xFF;
            if (alpha >= ALPHA_SEMI_MIN && alpha <= ALPHA_SEMI_MAX) {
                semiTransparent++;
            }
        }
        return semiTransparent > pixels / TRANSLUCENT_PIXEL_DIVISOR;
    }

    static void deleteGlTexture(TextureRepository.Texture tex) {
        if (tex == null) {
            return;
        }
        Identifier location = tex.location;
        tex.location = null;
        tex.tex = 0;
        if (location == null) {
            return;
        }
        if (RenderSystem.isOnRenderThread()) {
            RuntimeTextures.release(location);
        } else {
            Minecraft.getInstance().execute(() -> RuntimeTextures.release(location));
        }
    }
}
