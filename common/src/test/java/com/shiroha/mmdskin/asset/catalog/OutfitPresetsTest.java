package com.shiroha.mmdskin.asset.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** outfits.json as the studio writes it (docs/CARD_FORMAT.md, "outfits"). */
class OutfitPresetsTest {
    private static final String JSON = """
            {"version": 1, "default": 1, "presets": [
              {"name": "School", "hidden": ["jacket", "swimsuit"]},
              {"name": "Casual", "hidden": ["uniform", "swimsuit"]},
              {"name": "", "hidden": []}
            ]}""";

    @Test
    void readsPresetsAndTheDefault() {
        OutfitPresets presets = OutfitPresets.parse(JSON).orElseThrow();
        assertEquals(3, presets.presets().size());
        assertEquals("Casual", presets.find(null).name(), "no choice yet: the default outfit");
        assertEquals("School", presets.find("School").name());
        assertEquals("Casual", presets.find("gone").name(), "an outfit that no longer exists: the default");
        assertEquals("Outfit 3", presets.presets().get(2).name(), "unnamed presets get a name");
    }

    @Test
    void hidesMaterialsByName() {
        OutfitPresets presets = OutfitPresets.parse(JSON).orElseThrow();
        List<String> materials = List.of("body", "uniform", "jacket", "swimsuit", "jacket");
        Set<Integer> hidden = OutfitPresets.hiddenIndices(presets.find("School"), materials.size(), materials::get);
        assertEquals(Set.of(2, 3, 4), hidden);
    }

    @Test
    void noPresetsIsNoOutfits() {
        assertTrue(OutfitPresets.parse("{\"presets\": []}").isEmpty());
    }
}
