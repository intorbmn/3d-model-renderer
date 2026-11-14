package com.shiroha.mmdskin.render.lod;

import java.util.Arrays;

/**
 * A coarse copy of a triangle mesh for models far from the camera ("LOD"): vertices that fall in the
 * same cell of a 3D grid are merged into one of them (vertex clustering), and the triangles that
 * collapse are dropped. Each merged vertex is a real vertex of the model, so skinning, UVs and
 * normals still work; the mesh only loses detail too small to see at a distance.
 *
 * <p>Plain Java with no game classes, so it can run off the render thread.
 */
public final class MeshSimplifier {
    /** Grid cells along the longest side of the model, tried from fine to coarse. */
    private static final int[] GRID_STEPS = {160, 112, 80, 56, 40};

    private MeshSimplifier() {
    }

    /**
     * The simplified triangles of some sub-meshes.
     *
     * @param indices   the model's triangle list
     * @param positions vertex positions (x, y, z per vertex), in any pose
     * @param begins    per sub-mesh, its first index; a negative value skips the sub-mesh
     * @param counts    per sub-mesh, its index count
     * @param ratio     the share of triangles to keep, roughly (0..1]
     * @return the new triangle list, with per sub-mesh ranges into it
     */
    public static Result simplify(int[] indices, float[] positions, int[] begins, int[] counts, float ratio) {
        int subMeshes = begins.length;
        // Bounds of the vertices in use.
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        long total = 0;
        for (int s = 0; s < subMeshes; s++) {
            if (begins[s] < 0) {
                continue;
            }
            total += counts[s] / 3;
            for (int k = begins[s], end = begins[s] + counts[s]; k < end; k++) {
                int p = indices[k] * 3;
                float x = positions[p], y = positions[p + 1], z = positions[p + 2];
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (z < minZ) minZ = z;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                if (z > maxZ) maxZ = z;
            }
        }
        int[] newBegins = new int[subMeshes];
        int[] newCounts = new int[subMeshes];
        if (total == 0 || !(maxX >= minX)) {
            return new Result(new int[0], newBegins, newCounts, 0, 0);
        }
        float size = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        long target = Math.max(1, Math.round(total * (double) ratio));

        int[] best = null;
        for (int steps : GRID_STEPS) {
            float cell = Math.max(size / steps, 1.0e-6f);
            int[] out = cluster(indices, positions, begins, counts, minX, minY, minZ, cell, newBegins, newCounts);
            best = out;
            if (out.length / 3 <= target) {
                break;
            }
        }
        return new Result(best, newBegins, newCounts, total, best.length / 3);
    }

    private static int[] cluster(int[] indices, float[] positions, int[] begins, int[] counts,
                                 float minX, float minY, float minZ, float cell,
                                 int[] newBegins, int[] newCounts) {
        float inv = 1.0f / cell;
        int[] out = new int[16];
        int size = 0;
        LongIntMap cells = new LongIntMap(1024);
        for (int s = 0; s < begins.length; s++) {
            newBegins[s] = size;
            newCounts[s] = 0;
            if (begins[s] < 0) {
                continue;
            }
            // One grid per sub-mesh: a material never borrows another one's vertex (or its UVs).
            cells.clear();
            int end = begins[s] + counts[s] - counts[s] % 3;
            for (int k = begins[s]; k < end; k += 3) {
                int a = representative(cells, indices[k], positions, minX, minY, minZ, inv);
                int b = representative(cells, indices[k + 1], positions, minX, minY, minZ, inv);
                int c = representative(cells, indices[k + 2], positions, minX, minY, minZ, inv);
                if (a == b || b == c || a == c) {
                    continue;
                }
                if (size + 3 > out.length) {
                    out = Arrays.copyOf(out, out.length * 2);
                }
                out[size++] = a;
                out[size++] = b;
                out[size++] = c;
            }
            if (size == newBegins[s]) {
                // A small part (an eye, a button) would vanish: keep it as it is.
                if (size + (end - begins[s]) > out.length) {
                    out = Arrays.copyOf(out, Math.max(out.length * 2, size + (end - begins[s])));
                }
                System.arraycopy(indices, begins[s], out, size, end - begins[s]);
                size += end - begins[s];
            }
            newCounts[s] = size - newBegins[s];
        }
        return Arrays.copyOf(out, size);
    }

    private static int representative(LongIntMap cells, int vertex, float[] positions,
                                      float minX, float minY, float minZ, float inv) {
        int p = vertex * 3;
        long x = (long) ((positions[p] - minX) * inv);
        long y = (long) ((positions[p + 1] - minY) * inv);
        long z = (long) ((positions[p + 2] - minZ) * inv);
        long key = (x & 0x1FFFFF) | (y & 0x1FFFFF) << 21 | (z & 0x1FFFFF) << 42;
        return cells.putIfAbsent(key, vertex);
    }

    /** The simplified triangles: sub-mesh {@code i} is {@code indices[begins[i] .. begins[i] + counts[i])}. */
    public record Result(int[] indices, int[] begins, int[] counts, long sourceTriangles, long triangles) {
    }

    /** Open-addressing long → int map; only what clustering needs. */
    private static final class LongIntMap {
        private long[] keys;
        private int[] values;
        private boolean[] used;
        private int size;

        LongIntMap(int capacity) {
            allocate(capacity);
        }

        private void allocate(int capacity) {
            keys = new long[capacity];
            values = new int[capacity];
            used = new boolean[capacity];
            size = 0;
        }

        void clear() {
            Arrays.fill(used, false);
            size = 0;
        }

        /** The value for the key, storing {@code value} first when the key is new. */
        int putIfAbsent(long key, int value) {
            if ((size + 1) * 2 > keys.length) {
                grow();
            }
            int mask = keys.length - 1;
            int slot = (int) (mix(key) & mask);
            while (used[slot]) {
                if (keys[slot] == key) {
                    return values[slot];
                }
                slot = (slot + 1) & mask;
            }
            used[slot] = true;
            keys[slot] = key;
            values[slot] = value;
            size++;
            return value;
        }

        private void grow() {
            long[] oldKeys = keys;
            int[] oldValues = values;
            boolean[] oldUsed = used;
            allocate(oldKeys.length * 2);
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldUsed[i]) {
                    putIfAbsent(oldKeys[i], oldValues[i]);
                }
            }
        }

        private static long mix(long key) {
            key ^= key >>> 33;
            key *= 0xff51afd7ed558ccdL;
            key ^= key >>> 33;
            return key;
        }
    }
}
