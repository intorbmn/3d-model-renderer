package com.shiroha.mmdskin.asset.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Outfit presets of a character card ({@code outfits.json} next to the extracted model, from the
 * card's {@code outfits} section): named sets of materials to hide, so the outfit can be changed in
 * game. The model carries every piece; a preset only shows some of them.
 */
public record OutfitPresets(List<Preset> presets, int defaultIndex) {
    public static final String FILE = "outfits.json";

    /** One outfit: its name and the materials (by name) it hides. */
    public record Preset(String name, Set<String> hidden) {
    }

    /** The presets next to a model, or empty when it has none. */
    public static Optional<OutfitPresets> load(Path modelDir) {
        if (modelDir == null) {
            return Optional.empty();
        }
        Path file = modelDir.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public static Optional<OutfitPresets> parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Preset> presets = new ArrayList<>();
        if (root.has("presets") && root.get("presets").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("presets")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject preset = element.getAsJsonObject();
                String name = preset.has("name") ? preset.get("name").getAsString().trim() : "";
                Set<String> hidden = new HashSet<>();
                if (preset.has("hidden") && preset.get("hidden").isJsonArray()) {
                    for (JsonElement material : preset.getAsJsonArray("hidden")) {
                        if (material.isJsonPrimitive()) {
                            hidden.add(material.getAsString());
                        }
                    }
                }
                presets.add(new Preset(name.isEmpty() ? "Outfit " + (presets.size() + 1) : name, Set.copyOf(hidden)));
            }
        }
        if (presets.isEmpty()) {
            return Optional.empty();
        }
        int defaultIndex = root.has("default") && root.get("default").isJsonPrimitive()
                ? root.get("default").getAsInt() : 0;
        if (defaultIndex < 0 || defaultIndex >= presets.size()) {
            defaultIndex = 0;
        }
        return Optional.of(new OutfitPresets(List.copyOf(presets), defaultIndex));
    }

    /** The preset with this name, or the default one. */
    public Preset find(String name) {
        if (name != null) {
            for (Preset preset : presets) {
                if (preset.name().equals(name)) {
                    return preset;
                }
            }
        }
        return presets.get(defaultIndex);
    }

    /** Indices of the materials a preset hides, given the model's material names. */
    public static Set<Integer> hiddenIndices(Preset preset, int materialCount, IntFunction<String> materialName) {
        Set<Integer> hidden = new HashSet<>();
        for (int i = 0; i < materialCount; i++) {
            String name = materialName.apply(i);
            if (name != null && preset.hidden().contains(name)) {
                hidden.add(i);
            }
        }
        return hidden;
    }
}
