package com.shiroha.mmdskin.render.backend.submit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.render.backend.BaseModelInstance;
import com.shiroha.mmdskin.texture.runtime.RuntimeTextures;
import com.shiroha.mmdskin.texture.runtime.TextureRepository;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

/**
 * 文件职责：Minecraft 26.3 的模型渲染后端——把 native 蒙皮结果通过 SubmitNodeCollector 提交给原版渲染管线。
 *
 * <p>The native engine skins the mesh on the CPU. Each frame this instance copies the skinned
 * positions and normals and submits one custom-geometry node per material, using Minecraft's own
 * entity render types. Drawing through the vanilla pipeline means no direct OpenGL calls, so it works
 * on every graphics backend the game ships, and the model receives normal world lighting.
 *
 * <p>The entity render types draw quads, so each triangle is emitted as a quad whose last two corners
 * coincide.
 */
public final class SubmitModelInstance extends BaseModelInstance {
    private static final int SUB_MESH_STRIDE = 20;
    private static final float MIN_VISIBLE_ALPHA = 0.001f;
    /** Alpha below which a material is treated as blended rather than opaque. */
    private static final float OPAQUE_ALPHA = 0.999f;

    private static boolean missingCollectorLogged;

    /** One material's render state. */
    private static final class Material {
        Identifier texture;
        boolean translucentTexture;
        float red = 1.0f;
        float green = 1.0f;
        float blue = 1.0f;
        RenderType cutoutType;
        RenderType translucentType;

        RenderType renderType(boolean translucent) {
            if (translucent) {
                if (translucentType == null) {
                    translucentType = RenderTypes.entityTranslucent(texture);
                }
                return translucentType;
            }
            if (cutoutType == null) {
                cutoutType = RenderTypes.entityCutout(texture);
            }
            return cutoutType;
        }
    }

    private int vertexCount;
    private int[] indices;
    private Material[] materials;
    private int subMeshCount;
    private ByteBuffer subMeshData;
    private boolean hasUvMorph;

    private ByteBuffer positionBytes;
    private ByteBuffer normalBytes;
    private ByteBuffer uvBytes;
    private float[] positions;
    private float[] normals;
    private float[] uvs;
    private long copiedRevision = Long.MIN_VALUE;
    private boolean uvsCopied;

    // Geometry transformed into the pose of the most recent submit. Every material node of one submit
    // shares the pose, so the transform is done once and reused by the remaining nodes.
    private float[] transformedPositions;
    private float[] transformedNormals;
    private final Matrix4f transformedPose = new Matrix4f();
    private long transformedRevision = Long.MIN_VALUE;
    private boolean transformedValid;

    private volatile boolean disposed;

    private SubmitModelInstance() {
    }

    public static SubmitModelInstance createFromHandle(NativeRenderBackendPort port, long modelHandle, String modelDir) {
        if (modelHandle == 0) {
            return null;
        }
        SubmitModelInstance instance = new SubmitModelInstance();
        instance.nativeRenderBackendPort = port;
        instance.model = modelHandle;
        instance.modelDir = modelDir;
        try {
            instance.load(port, modelHandle);
        } catch (RuntimeException | Error e) {
            // The caller still owns the native handle when creation fails; only free what was allocated here.
            instance.releaseTextures();
            instance.freeBuffers();
            instance.model = 0;
            throw e;
        }
        return instance;
    }

    private void load(NativeRenderBackendPort port, long modelHandle) {
        vertexCount = (int) port.getVertexCount(modelHandle);
        int indexCount = (int) port.getIndexCount(modelHandle);
        if (vertexCount <= 0 || indexCount <= 0) {
            throw new IllegalStateException("Model has no geometry (vertices=" + vertexCount + ", indices=" + indexCount + ")");
        }

        indices = readIndices(port, modelHandle, indexCount);

        positionBytes = allocate(vertexCount * 12);
        normalBytes = allocate(vertexCount * 12);
        uvBytes = allocate(vertexCount * 8);
        positions = new float[vertexCount * 3];
        normals = new float[vertexCount * 3];
        uvs = new float[vertexCount * 2];
        transformedPositions = new float[vertexCount * 3];
        transformedNormals = new float[vertexCount * 3];

        textureKeys = new ArrayList<>();
        materials = loadMaterials(port, modelHandle, textureKeys);

        hasUvMorph = port.getUvMorphCount(modelHandle) > 0;

        int materialMorphCount = port.getMaterialMorphResultCount(modelHandle);
        if (materialMorphCount > 0) {
            materialMorphResultsByteBuffer = allocate(materialMorphCount * 56 * 4);
            materialMorphResultCount = materialMorphCount;
        }

        subMeshCount = port.getSubMeshCount(modelHandle);
        subMeshData = allocate(Math.max(1, subMeshCount) * SUB_MESH_STRIDE);

        port.setAutoBlinkEnabled(modelHandle, true);
    }

    private static ByteBuffer allocate(int bytes) {
        return MemoryUtil.memAlloc(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int[] readIndices(NativeRenderBackendPort port, long modelHandle, int indexCount) {
        int elementSize = port.getIndexElementSize(modelHandle);
        if (elementSize != 1 && elementSize != 2 && elementSize != 4) {
            throw new IllegalStateException("Unsupported index element size: " + elementSize);
        }
        int byteSize = indexCount * elementSize;
        ByteBuffer buffer = allocate(byteSize);
        try {
            port.copyNativeDataToBuffer(buffer, port.getIndexDataAddress(modelHandle), byteSize);
            int[] result = new int[indexCount - indexCount % 3];
            for (int i = 0; i < result.length; i++) {
                int index = switch (elementSize) {
                    case 1 -> buffer.get(i) & 0xFF;
                    case 2 -> buffer.getShort(i * 2) & 0xFFFF;
                    default -> buffer.getInt(i * 4);
                };
                // A corrupt index would read outside the vertex arrays; point it at vertex 0 instead.
                result[i] = index >= 0 && index < vertexCount ? index : 0;
            }
            return result;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private static Material[] loadMaterials(NativeRenderBackendPort port, long modelHandle, List<String> textureKeys) {
        Material[] result = new Material[port.getMaterialCount(modelHandle)];
        for (int i = 0; i < result.length; i++) {
            Material material = new Material();
            String texturePath = port.getMaterialTexturePath(modelHandle, i);
            if (texturePath != null && !texturePath.isEmpty()) {
                TextureRepository.Texture texture = TextureRepository.GetTexture(texturePath);
                if (texture != null && texture.location != null) {
                    material.texture = texture.location;
                    material.translucentTexture = texture.translucent;
                    TextureRepository.addRef(texturePath);
                    textureKeys.add(texturePath);
                }
            }
            if (material.texture == null) {
                // No usable texture: draw the material in its flat diffuse colour on a white texture.
                material.texture = RuntimeTextures.white();
                float[] diffuse = port.getMaterialDiffuse(modelHandle, i);
                if (diffuse != null && diffuse.length >= 3) {
                    material.red = clamp01(diffuse[0]);
                    material.green = clamp01(diffuse[1]);
                    material.blue = clamp01(diffuse[2]);
                }
            }
            result[i] = material;
        }
        return result;
    }

    private static float clamp01(float value) {
        if (!(value >= 0.0f)) {
            return 0.0f;
        }
        return Math.min(value, 1.0f);
    }

    @Override
    protected boolean isReady() {
        return !disposed && positions != null;
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
                                 PoseStack poseStack,
                                 int packedLight) {
        SubmitNodeCollector collector = RenderSubmitContext.current();
        if (collector == null) {
            if (!missingCollectorLogged) {
                missingCollectorLogged = true;
                logger.warn("Model render requested outside of an entity submit; nothing was drawn for '{}'.", getModelName());
            }
            return;
        }

        NativeRenderBackendPort port = backendPort();
        copyGeometryFromNative(port);

        fetchMaterialMorphResults();
        subMeshData.clear();
        port.batchGetSubMeshData(model, subMeshData);

        poseStack.pushPose();
        try {
            poseStack.rotate(tempQuat.identity().rotateY(-entityYaw * ((float) Math.PI / 180F)));
            poseStack.rotate(tempQuat.identity().rotateX(entityPitch * ((float) Math.PI / 180F)));
            poseStack.translate(entityTrans.x, entityTrans.y, entityTrans.z);
            float scale = getModelScale();
            poseStack.scale(scale, scale, scale);

            // Toon outline (off unless toon rendering is enabled in the settings).
            boolean outline = ConfigManager.isToonRenderingEnabled() && ConfigManager.isToonOutlineEnabled();
            float outlineWidth = outline ? ConfigManager.getToonOutlineWidth() : 0.0f;
            int outlineColor = outline ? packOutlineColor() : 0;
            RenderType outlineType = outline && outlineWidth > 0.0f ? outlineRenderType() : null;

            int translucentOrder = 0;
            for (int i = 0; i < subMeshCount; i++) {
                int base = i * SUB_MESH_STRIDE;
                int materialId = subMeshData.getInt(base);
                int beginIndex = subMeshData.getInt(base + 4);
                int indexCount = subMeshData.getInt(base + 8);
                float baseAlpha = subMeshData.getFloat(base + 12);
                boolean visible = subMeshData.get(base + 16) != 0;
                if (!visible || materialId < 0 || materialId >= materials.length) {
                    continue;
                }
                if (beginIndex < 0 || indexCount < 3 || beginIndex > indices.length - indexCount) {
                    continue;
                }
                float alpha = Math.min(1.0f, getEffectiveMaterialAlpha(materialId, baseAlpha));
                if (!(alpha >= MIN_VISIBLE_ALPHA)) {
                    continue;
                }

                Material material = materials[materialId];
                boolean translucent = material.translucentTexture || alpha < OPAQUE_ALPHA;
                int color = packColor(material, alpha);
                int first = beginIndex;
                int count = indexCount - indexCount % 3;

                // Blended materials are layered (eyes over face, lashes over eyes): keep the model's
                // material order for them. Opaque materials are depth-tested and can share one order.
                var target = translucent ? collector.order(++translucentOrder) : collector;
                target.submitCustomGeometry(poseStack, material.renderType(translucent),
                        (pose, consumer) -> emit(pose, consumer, first, count, color, packedLight));

                if (outlineType != null && !translucent) {
                    collector.submitCustomGeometry(poseStack, outlineType,
                            (pose, consumer) -> emitOutline(pose, consumer, first, count, outlineColor, outlineWidth, packedLight));
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static int packOutlineColor() {
        int r = Math.round(clamp01(ConfigManager.getToonOutlineR()) * 255.0f) & 0xFF;
        int g = Math.round(clamp01(ConfigManager.getToonOutlineG()) * 255.0f) & 0xFF;
        int b = Math.round(clamp01(ConfigManager.getToonOutlineB()) * 255.0f) & 0xFF;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static RenderType outlineType;

    private static RenderType outlineRenderType() {
        if (outlineType == null) {
            outlineType = RenderTypes.entityCutoutCull(RuntimeTextures.white());
        }
        return outlineType;
    }

    private static int packColor(Material material, float alpha) {
        int a = Math.round(alpha * 255.0f) & 0xFF;
        int r = Math.round(material.red * 255.0f) & 0xFF;
        int g = Math.round(material.green * 255.0f) & 0xFF;
        int b = Math.round(material.blue * 255.0f) & 0xFF;
        return a << 24 | r << 16 | g << 8 | b;
    }

    private void copyGeometryFromNative(NativeRenderBackendPort port) {
        long revision = getNativeUpdateRevision();
        if (revision != copiedRevision) {
            copiedRevision = revision;
            int size = vertexCount * 12;
            positionBytes.clear();
            port.copyNativeDataToBuffer(positionBytes, port.getPositionDataAddress(model), size);
            positionBytes.asFloatBuffer().get(positions, 0, vertexCount * 3);
            normalBytes.clear();
            port.copyNativeDataToBuffer(normalBytes, port.getNormalDataAddress(model), size);
            normalBytes.asFloatBuffer().get(normals, 0, vertexCount * 3);
            if (hasUvMorph) {
                uvsCopied = false;
            }
        }
        if (!uvsCopied) {
            uvsCopied = true;
            uvBytes.clear();
            port.copyNativeDataToBuffer(uvBytes, port.getUvDataAddress(model), vertexCount * 8);
            uvBytes.asFloatBuffer().get(uvs, 0, vertexCount * 2);
        }
    }

    /** Runs when Minecraft builds the vertex buffer for one material node. */
    private void emit(PoseStack.Pose pose, VertexConsumer consumer, int first, int count, int color, int packedLight) {
        if (disposed || positions == null) {
            return;
        }
        transformGeometry(pose);

        float[] tp = transformedPositions;
        float[] tn = transformedNormals;
        float[] uv = uvs;
        int[] idx = indices;
        int overlay = OverlayTexture.NO_OVERLAY;
        int end = first + count;
        for (int i = first; i < end; i += 3) {
            int a = idx[i];
            int b = idx[i + 1];
            int c = idx[i + 2];
            vertex(consumer, tp, tn, uv, a, color, overlay, packedLight);
            vertex(consumer, tp, tn, uv, b, color, overlay, packedLight);
            vertex(consumer, tp, tn, uv, c, color, overlay, packedLight);
            vertex(consumer, tp, tn, uv, c, color, overlay, packedLight);
        }
    }

    /**
     * Inverted-hull outline: the mesh pushed out along its normals and drawn with reversed winding on a
     * back-face-culling render type, so only the rim behind the model remains visible. The width
     * follows the original outline shader: slightly wider with distance, fading out beyond 25 blocks.
     */
    private void emitOutline(PoseStack.Pose pose, VertexConsumer consumer, int first, int count,
                             int color, float width, int packedLight) {
        if (disposed || positions == null) {
            return;
        }
        transformGeometry(pose);

        Matrix4f m = transformedPose;
        float depth = Math.max(0.5f, (float) Math.sqrt(m.m30() * m.m30() + m.m31() * m.m31() + m.m32() * m.m32()));
        float widen = 0.8f + 0.4f * Math.min(1.0f, Math.max(0.0f, (depth - 1.0f) / 12.0f));
        float fade = Math.min(1.0f, Math.max(0.0f, 1.0f - (depth - 25.0f) / 15.0f));
        float offset = width * widen * fade;
        if (!(offset > 0.0f)) {
            return;
        }

        float[] tp = transformedPositions;
        float[] tn = transformedNormals;
        int[] idx = indices;
        int overlay = OverlayTexture.NO_OVERLAY;
        int end = first + count;
        for (int i = first; i < end; i += 3) {
            int a = idx[i];
            int b = idx[i + 1];
            int c = idx[i + 2];
            outlineVertex(consumer, tp, tn, a, offset, color, overlay, packedLight);
            outlineVertex(consumer, tp, tn, c, offset, color, overlay, packedLight);
            outlineVertex(consumer, tp, tn, b, offset, color, overlay, packedLight);
            outlineVertex(consumer, tp, tn, b, offset, color, overlay, packedLight);
        }
    }

    private static void outlineVertex(VertexConsumer consumer, float[] tp, float[] tn, int v, float offset,
                                      int color, int overlay, int light) {
        int p = v * 3;
        float nx = tn[p], ny = tn[p + 1], nz = tn[p + 2];
        consumer.addVertex(tp[p] + nx * offset, tp[p + 1] + ny * offset, tp[p + 2] + nz * offset,
                color, 0.5f, 0.5f, overlay, light, -nx, -ny, -nz);
    }

    private static void vertex(VertexConsumer consumer, float[] tp, float[] tn, float[] uv,
                               int v, int color, int overlay, int light) {
        int p = v * 3;
        int t = v * 2;
        consumer.addVertex(tp[p], tp[p + 1], tp[p + 2], color, uv[t], uv[t + 1], overlay, light,
                tn[p], tn[p + 1], tn[p + 2]);
    }

    private void transformGeometry(PoseStack.Pose pose) {
        Matrix4f m = pose.pose();
        if (transformedValid && transformedRevision == copiedRevision && transformedPose.equals(m)) {
            return;
        }
        transformedPose.set(m);
        transformedRevision = copiedRevision;
        transformedValid = true;

        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02();
        float m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22();
        float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        Matrix3f n = pose.normal();
        float n00 = n.m00(), n01 = n.m01(), n02 = n.m02();
        float n10 = n.m10(), n11 = n.m11(), n12 = n.m12();
        float n20 = n.m20(), n21 = n.m21(), n22 = n.m22();

        float[] src = positions;
        float[] srcN = normals;
        float[] dst = transformedPositions;
        float[] dstN = transformedNormals;
        int length = vertexCount * 3;
        for (int i = 0; i < length; i += 3) {
            float x = src[i], y = src[i + 1], z = src[i + 2];
            dst[i] = m00 * x + m10 * y + m20 * z + m30;
            dst[i + 1] = m01 * x + m11 * y + m21 * z + m31;
            dst[i + 2] = m02 * x + m12 * y + m22 * z + m32;

            float nx = srcN[i], ny = srcN[i + 1], nz = srcN[i + 2];
            float tx = n00 * nx + n10 * ny + n20 * nz;
            float ty = n01 * nx + n11 * ny + n21 * nz;
            float tz = n02 * nx + n12 * ny + n22 * nz;
            float lengthSq = tx * tx + ty * ty + tz * tz;
            if (lengthSq > 1.0e-12f) {
                float inv = (float) (1.0 / Math.sqrt(lengthSq));
                dstN[i] = tx * inv;
                dstN[i + 1] = ty * inv;
                dstN[i + 2] = tz * inv;
            } else {
                dstN[i] = 0.0f;
                dstN[i + 1] = 1.0f;
                dstN[i + 2] = 0.0f;
            }
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        releaseTextures();
        disposeModelHandle();
        disposeMaterialMorphBuffers();
        freeBuffers();
    }

    private void freeBuffers() {
        positionBytes = free(positionBytes);
        normalBytes = free(normalBytes);
        uvBytes = free(uvBytes);
        subMeshData = free(subMeshData);
        positions = null;
        normals = null;
        uvs = null;
        transformedPositions = null;
        transformedNormals = null;
        transformedValid = false;
    }

    private static ByteBuffer free(ByteBuffer buffer) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
        }
        return null;
    }

    @Override
    public long getVramUsage() {
        return 0L;
    }
}
