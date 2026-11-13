package com.shiroha.mmdskin.asset.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads model cards made by 3D Model Renderer Studio: a PNG picture with the model packed after it.
 * The format is described in docs/CARD_FORMAT.md of the studio repository.
 */
public final class ModelCardFile {
    public static final String FORMAT = "3d-model-renderer-card";
    public static final int VERSION = 1;
    /** Written next to the extracted model; read by the renderer for tints and scale. */
    public static final String SETTINGS_FILE = ".card-settings.json";
    /** Physics groups of the card, read by the engine for glTF/VRM models. */
    public static final String PHYSICS_FILE = "physics.json";
    private static final byte[] MAGIC = "3DMRCARD".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] END_MAGIC = "3DMR-END".getBytes(StandardCharsets.US_ASCII);
    private static final List<String> MODEL_TYPES = List.of("pmx", "pmd", "vrm", "glb", "gltf");
    private static final long MAX_TOTAL_SIZE = 1024L * 1024L * 1024L;
    private static final int MAX_FILES = 4096;
    private static final int MAX_MANIFEST = 16 * 1024 * 1024;

    public static final class CardException extends IOException {
        public CardException(String message) {
            super(message);
        }
    }

    /** What a card holds, after its files are extracted. */
    public record Extracted(String name, String author, Path modelFile, String modelType, Path root) {
    }

    private ModelCardFile() {
    }

    /** True when the file ends with a card trailer (cheap: reads 16 bytes). */
    public static boolean isCard(Path file) {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            return locatePayload(in) >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Extracts the card into {@code cacheRoot/<card name>-<hash>/} unless that folder is already complete.
     * Files are written to a temporary folder first, so a crash never leaves a half-written model behind.
     */
    public static Extracted extract(Path card, Path cacheRoot) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(card.toFile(), "r")) {
            long start = locatePayload(in);
            if (start < 0) {
                throw new CardException("not a model card");
            }
            long end = in.length() - 16;
            byte[] header = new byte[16];
            in.seek(start);
            in.readFully(header);
            ByteBuffer headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int version = headerBuffer.getInt(8);
            if (version != VERSION) {
                throw new CardException("unsupported card version " + version);
            }
            long manifestLength = Integer.toUnsignedLong(headerBuffer.getInt(12));
            long dataStart = start + 16 + manifestLength;
            if (manifestLength > MAX_MANIFEST || dataStart > end) {
                throw new CardException("the manifest is truncated");
            }
            byte[] manifestBytes = new byte[(int) manifestLength];
            in.readFully(manifestBytes);
            JsonObject manifest;
            try {
                manifest = JsonParser.parseString(new String(manifestBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException e) {
                throw new CardException("the manifest is not valid JSON");
            }
            if (!FORMAT.equals(string(manifest, "format")) || !manifest.has("version")
                    || manifest.get("version").getAsInt() != VERSION) {
                throw new CardException("not a version 1 model card");
            }
            JsonObject model = manifest.has("model") && manifest.get("model").isJsonObject()
                    ? manifest.getAsJsonObject("model") : new JsonObject();
            String modelPath = string(model, "path");
            String modelType = string(model, "type").toLowerCase(Locale.ROOT);
            if (!MODEL_TYPES.contains(modelType) || !isSafePath(modelPath)) {
                throw new CardException("unknown model type");
            }
            JsonArray files = manifest.has("files") && manifest.get("files").isJsonArray()
                    ? manifest.getAsJsonArray("files") : new JsonArray();
            if (files.size() > MAX_FILES) {
                throw new CardException("too many files");
            }

            // The folder name changes with the content, so an updated card never reuses stale files.
            String hash = sha256(manifestBytes).substring(0, 12);
            String name = string(manifest, "name");
            Path target = cacheRoot.resolve(safeFolderName(card) + "-" + hash);
            Path modelFile = target.resolve(modelPath);
            if (Files.isRegularFile(target.resolve(SETTINGS_FILE)) && Files.isRegularFile(modelFile)) {
                return new Extracted(name, string(manifest, "author"), modelFile, modelType, target);
            }

            Files.createDirectories(cacheRoot);
            Path temp = Files.createTempDirectory(cacheRoot, ".extract-");
            try {
                long dataLength = end - dataStart;
                long total = 0;
                Set<String> seen = new HashSet<>();
                boolean modelFound = false;
                for (JsonElement element : files) {
                    JsonObject entry = element.getAsJsonObject();
                    String path = string(entry, "path");
                    if (!isSafePath(path) || !seen.add(path)) {
                        throw new CardException("unsafe or duplicate path: " + path);
                    }
                    long offset = entry.get("offset").getAsLong();
                    long size = entry.get("size").getAsLong();
                    long stored = entry.get("stored").getAsLong();
                    if (offset < 0 || size < 0 || stored < 0 || offset + stored > dataLength || size > Integer.MAX_VALUE - 16) {
                        throw new CardException("invalid entry: " + path);
                    }
                    total += size;
                    if (total > MAX_TOTAL_SIZE) {
                        throw new CardException("the card is too large");
                    }
                    byte[] raw = new byte[Math.toIntExact(stored)];
                    in.seek(dataStart + offset);
                    in.readFully(raw);
                    String compression = string(entry, "compression");
                    byte[] data = switch (compression) {
                        case "deflate" -> inflate(raw, (int) size);
                        case "none" -> raw;
                        default -> throw new CardException("unknown compression for " + path);
                    };
                    if (data.length != size || !sha256(data).equals(string(entry, "sha256"))) {
                        throw new CardException("file is damaged: " + path);
                    }
                    Path out = temp.resolve(path).normalize();
                    if (!out.startsWith(temp)) {
                        throw new CardException("unsafe path: " + path);
                    }
                    Files.createDirectories(out.getParent());
                    Files.write(out, data);
                    modelFound |= path.equals(modelPath);
                }
                if (!modelFound) {
                    throw new CardException("the model file is missing from the card");
                }
                JsonObject settings = manifest.has("settings") && manifest.get("settings").isJsonObject()
                        ? manifest.getAsJsonObject("settings") : new JsonObject();
                Files.writeString(temp.resolve(SETTINGS_FILE), settings.toString(), StandardCharsets.UTF_8);
                // Settings live next to the model as well, where the renderer looks for them.
                Path modelDir = temp.resolve(modelPath).getParent();
                if (modelDir != null && !modelDir.equals(temp)) {
                    Files.writeString(modelDir.resolve(SETTINGS_FILE), settings.toString(), StandardCharsets.UTF_8);
                }
                // The card's physics groups (hair, clothing, body, custom) go where the engine
                // looks for them; they take priority over the model's own spring bones.
                if (manifest.has("physics") && manifest.get("physics").isJsonObject() && modelDir != null) {
                    Files.writeString(modelDir.resolve(PHYSICS_FILE), manifest.get("physics").toString(), StandardCharsets.UTF_8);
                }
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException moveFailed) {
                    if (!Files.isRegularFile(modelFile)) {
                        throw moveFailed;
                    }
                }
            } finally {
                deleteTree(temp);
            }
            return new Extracted(name, string(manifest, "author"), modelFile, modelType, target);
        }
    }

    /** Reads the settings written next to an extracted model: scale and per-material tints. */
    public static Settings readSettings(Path modelDir) {
        if (modelDir == null) {
            return Settings.NONE;
        }
        Path file = modelDir.resolve(SETTINGS_FILE);
        if (!Files.isRegularFile(file)) {
            return Settings.NONE;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            float scale = json.has("scale") ? json.get("scale").getAsFloat() : 1.0f;
            if (!(scale > 0.05f && scale < 20.0f)) {
                scale = 1.0f;
            }
            Map<String, float[]> tints = new LinkedHashMap<>();
            if (json.has("materialTints") && json.get("materialTints").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("materialTints").entrySet()) {
                    if (!e.getValue().isJsonArray() || e.getValue().getAsJsonArray().size() < 3) {
                        continue;
                    }
                    JsonArray rgb = e.getValue().getAsJsonArray();
                    tints.put(e.getKey(), new float[] {
                            clampTint(rgb.get(0).getAsFloat()), clampTint(rgb.get(1).getAsFloat()), clampTint(rgb.get(2).getAsFloat())});
                }
            }
            return new Settings(scale, Map.copyOf(tints));
        } catch (IOException | RuntimeException e) {
            return Settings.NONE;
        }
    }

    public record Settings(float scale, Map<String, float[]> materialTints) {
        public static final Settings NONE = new Settings(1.0f, Map.of());
    }

    private static float clampTint(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(2.0f, value)) : 1.0f;
    }

    private static long locatePayload(RandomAccessFile in) throws IOException {
        long length = in.length();
        if (length < 48) {
            return -1;
        }
        byte[] trailer = new byte[16];
        in.seek(length - 16);
        in.readFully(trailer);
        for (int i = 0; i < 8; i++) {
            if (trailer[8 + i] != END_MAGIC[i]) {
                return -1;
            }
        }
        long start = ByteBuffer.wrap(trailer).order(ByteOrder.LITTLE_ENDIAN).getLong(0);
        if (start < 8 || start + 16 > length - 16) {
            return -1;
        }
        byte[] magic = new byte[8];
        in.seek(start);
        in.readFully(magic);
        for (int i = 0; i < 8; i++) {
            if (magic[i] != MAGIC[i]) {
                return -1;
            }
        }
        return start;
    }

    static boolean isSafePath(String path) {
        if (path == null || path.isEmpty() || path.length() > 512) {
            return false;
        }
        if (path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0 || path.indexOf(':') >= 0 || path.startsWith("/")) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static String safeFolderName(Path card) {
        String name = card.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim();
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        return name.isEmpty() ? "card" : name;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static byte[] inflate(byte[] raw, int expectedSize) throws CardException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(raw);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, expectedSize));
            byte[] buffer = new byte[64 * 1024];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new CardException("truncated compressed data");
                }
                out.write(buffer, 0, n);
                if (out.size() > expectedSize) {
                    throw new CardException("file is larger than declared");
                }
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new CardException("corrupt compressed data");
        } finally {
            inflater.end();
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // A leftover temp folder is harmless; it is named .extract-* and skipped by the scan.
                }
            });
        } catch (IOException ignored) {
            // Same as above.
        }
    }
}
