package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.asset.catalog.ModelCatalogEntry;
import com.shiroha.mmdskin.asset.catalog.OutfitPresets;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialEntryState;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialScreenContext;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Changes the outfit of the player's character card in game: one button per outfit preset made in
 * the studio. The choice is kept per model, like the material visibility screen's.
 */
public class OutfitScreen extends Screen {
    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private final MaterialScreenContext context;
    private final OutfitPresets presets;
    private final String current;

    private OutfitScreen(MaterialScreenContext context, OutfitPresets presets) {
        super(Component.translatable("gui.mmdskin.outfit.title"));
        this.context = context;
        this.presets = presets;
        ModelConfigData config = ModelConfigManager.getConfig(context.configModelName());
        this.current = presets.find(config.outfit).name();
    }

    private static MaterialVisibilityApplicationService service() {
        return ModelSelectorServices.materialVisibility();
    }

    /** The screen for the player's model, or null with a chat message when it has no outfits. */
    public static Screen createForPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        MaterialScreenContext context = service().createPlayerContext().orElse(null);
        if (context == null) {
            minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.translatable("message.mmdskin.player.model_not_found"));
            return null;
        }
        ModelCatalogEntry entry = ModelCatalogEntry.findByFolderName(context.configModelName());
        OutfitPresets presets = entry == null ? null
                : OutfitPresets.load(Path.of(entry.getFolderPath())).orElse(null);
        if (presets == null) {
            minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.translatable("message.mmdskin.outfit.none"));
            return null;
        }
        return new OutfitScreen(context, presets);
    }

    @Override
    protected void init() {
        super.init();
        List<OutfitPresets.Preset> list = presets.presets();
        int count = list.size() + 2;
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = Math.max(8, (this.height - count * (BUTTON_HEIGHT + GAP)) / 2);
        Button title = Button.builder(this.getTitle(), button -> { }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        title.active = false;
        this.addRenderableWidget(title);
        y += BUTTON_HEIGHT + GAP * 2;
        for (OutfitPresets.Preset preset : list) {
            String label = (preset.name().equals(current) ? "> " : "") + preset.name();
            this.addRenderableWidget(Button.builder(Component.literal(label), button -> wear(preset))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
            y += BUTTON_HEIGHT + GAP;
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.outfit.close"), button -> onClose())
                .bounds(x, y + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void wear(OutfitPresets.Preset preset) {
        List<MaterialEntryState> materials = service().loadMaterials(context);
        // Everything on, then off what this outfit does not wear (same path as the material screen).
        service().setAllVisible(context, materials, true);
        Set<Integer> hidden = new HashSet<>();
        for (int i = 0; i < materials.size(); i++) {
            if (preset.hidden().contains(materials.get(i).name())) {
                service().toggleMaterial(context, materials, i);
                hidden.add(materials.get(i).index());
            }
        }
        ModelConfigData config = ModelConfigManager.getConfig(context.configModelName());
        config.hiddenMaterials = hidden;
        config.outfit = preset.name();
        ModelConfigManager.saveConfig(context.configModelName(), config);
        Minecraft.getInstance().gui.setScreen(new OutfitScreen(context, presets));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
