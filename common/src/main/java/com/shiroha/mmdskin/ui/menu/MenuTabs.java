package com.shiroha.mmdskin.ui.menu;

import com.shiroha.mmdskin.asset.catalog.OutfitPresets;
import com.shiroha.mmdskin.config.ConfigData;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.config.PhysicsConfigSnapshot;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.ui.config.ActionWheelConfig;
import com.shiroha.mmdskin.ui.config.ActionWheelConfigScreen;
import com.shiroha.mmdskin.ui.config.MorphWheelConfigScreen;
import com.shiroha.mmdskin.ui.selector.ModelAnimationScreen;
import com.shiroha.mmdskin.ui.selector.ModelSelectorServices;
import com.shiroha.mmdskin.ui.selector.ModelSettingsScreen;
import com.shiroha.mmdskin.ui.selector.OutfitScreen;
import com.shiroha.mmdskin.ui.selector.SceneSelectorScreen;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialEntryState;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialScreenContext;
import com.shiroha.mmdskin.ui.selector.application.ModelSelectionApplicationService;
import com.shiroha.mmdskin.ui.stage.StagePlaybackUiAdapter;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import com.shiroha.mmdskin.ui.wheel.service.ActionOption;
import com.shiroha.mmdskin.ui.wheel.service.DefaultActionWheelService;
import com.shiroha.mmdskin.ui.wheel.service.DefaultMorphWheelService;
import com.shiroha.mmdskin.ui.wheel.service.MorphOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** The tabs of the main menu. */
final class MenuTabs {
    private MenuTabs() {
    }

    static List<MenuTab> create(MainMenuScreen menu) {
        return List.of(
                new ConfigTab(menu),
                new ModelTab(menu),
                new AnimationTab(menu),
                new ExpressionTab(menu),
                new PresetTab(),
                new MaterialTab(),
                new SceneTab(menu),
                new StageTab(menu));
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** Settings that change right away, plus the full settings screen and the list editors. */
    static final class ConfigTab implements MenuTab {
        private static final float[] LOD_DISTANCES = {0.0f, 12.0f, 16.0f, 24.0f, 32.0f, 48.0f};
        private static final int[] VISIBLE_MODELS = {3, 5, 10, 15, 20, 30};
        private final MainMenuScreen menu;

        ConfigTab(MainMenuScreen menu) {
            this.menu = menu;
        }

        @Override
        public Component title() {
            return Component.translatable("gui.mmdskin.menu.tab.config");
        }

        @Override
        public List<MenuRow> buttons() {
            return List.of(
                    MenuRow.item(tr("gui.mmdskin.menu.all_settings"), () -> menu.open(ConfigWheelScreen::createModSettingsScreen)),
                    MenuRow.item(tr("gui.mmdskin.menu.edit_actions"), () -> menu.open(() -> new ActionWheelConfigScreen(menu))),
                    MenuRow.item(tr("gui.mmdskin.menu.edit_expressions"), () -> menu.open(() -> new MorphWheelConfigScreen(menu))));
        }

        @Override
        public List<MenuRow> rows() {
            ConfigData data = ConfigManager.editableData();
            List<MenuRow> rows = new ArrayList<>();
            if (data == null) {
                return rows;
            }
            rows.add(MenuRow.header(tr("gui.mmdskin.menu.config.render")));
            rows.add(toggle("gui.mmdskin.menu.config.toon", data.toonRenderingEnabled, () -> data.toonRenderingEnabled ^= true));
            rows.add(toggle("gui.mmdskin.menu.config.outline", data.toonOutlineEnabled, () -> data.toonOutlineEnabled ^= true));
            rows.add(toggle("gui.mmdskin.menu.config.first_person", data.firstPersonModelEnabled, () -> data.firstPersonModelEnabled ^= true));

            rows.add(MenuRow.header(tr("gui.mmdskin.menu.config.performance")));
            rows.add(MenuRow.item(tr("gui.mmdskin.menu.config.mesh_lod"),
                    data.meshLodDistance <= 0.0f ? tr("gui.mmdskin.menu.off") : tr("gui.mmdskin.menu.blocks", Math.round(data.meshLodDistance)),
                    data.meshLodDistance > 0.0f, () -> {
                        data.meshLodDistance = next(LOD_DISTANCES, data.meshLodDistance);
                        ConfigManager.persist();
                    }));
            rows.add(MenuRow.item(tr("gui.mmdskin.menu.config.visible_models"), String.valueOf(data.maxVisibleModelsPerFrame), false, () -> {
                data.maxVisibleModelsPerFrame = next(VISIBLE_MODELS, data.maxVisibleModelsPerFrame);
                ConfigManager.persist();
            }));

            rows.add(MenuRow.header(tr("gui.mmdskin.menu.config.physics")));
            rows.add(toggle("gui.mmdskin.menu.config.physics_on", data.physicsEnabled, () -> {
                data.physicsEnabled ^= true;
                ClientRenderRuntime.get().applyPhysicsConfig(PhysicsConfigSnapshot.from(data));
            }));
            return rows;
        }

        private static MenuRow toggle(String key, boolean on, Runnable flip) {
            return MenuRow.item(tr(key), tr(on ? "gui.mmdskin.menu.on" : "gui.mmdskin.menu.off"), on, () -> {
                flip.run();
                ConfigManager.persist();
            });
        }

        private static float next(float[] values, float current) {
            for (float value : values) {
                if (value > current + 0.01f) {
                    return value;
                }
            }
            return values[0];
        }

        private static int next(int[] values, int current) {
            for (int value : values) {
                if (value > current) {
                    return value;
                }
            }
            return values[0];
        }
    }

    /** Model ("skin") list. */
    static final class ModelTab implements MenuTab {
        private final MainMenuScreen menu;
        private final ModelSelectionApplicationService service = ModelSelectorServices.modelSelection();

        ModelTab(MainMenuScreen menu) {
            this.menu = menu;
        }

        @Override
        public Component title() {
            return Component.translatable("gui.mmdskin.menu.tab.model");
        }

        @Override
        public List<MenuRow> buttons() {
            String current = service.getCurrentModel();
            boolean configurable = false;
            for (ModelSelectionApplicationService.ModelCard card : service.loadModelCards()) {
                if (card.displayName().equals(current)) {
                    configurable = card.configurable();
                }
            }
            List<MenuRow> buttons = new ArrayList<>();
            buttons.add(MenuRow.item(tr("gui.mmdskin.refresh"), service::refreshModelCatalog));
            buttons.add(MenuRow.item(tr("gui.mmdskin.model_settings.title"),
                    configurable ? () -> menu.open(() -> new ModelSettingsScreen(current, menu)) : null));
            buttons.add(MenuRow.item(tr("gui.mmdskin.menu.model_animations"),
                    configurable ? () -> menu.open(() -> new ModelAnimationScreen(current, menu)) : null));
            return buttons;
        }

        @Override
        public List<MenuRow> rows() {
            String current = service.getCurrentModel();
            List<MenuRow> rows = new ArrayList<>();
            for (ModelSelectionApplicationService.ModelCard card : service.loadModelCards()) {
                String name = card.displayName();
                rows.add(MenuRow.item(card.label(), card.card() ? tr("gui.mmdskin.menu.card") : "", name.equals(current),
                        () -> service.selectModel(name)));
            }
            return rows;
        }
    }

    /** Animations: the favourites (the old action wheel), then every other one found. */
    static final class AnimationTab implements MenuTab {
        private final MainMenuScreen menu;
        private final DefaultActionWheelService service = new DefaultActionWheelService();

        AnimationTab(MainMenuScreen menu) {
            this.menu = menu;
        }

        @Override
        public Component title() {
            return Component.translatable("gui.mmdskin.menu.tab.animation");
        }

        @Override
        public List<MenuRow> buttons() {
            return List.of(MenuRow.item(tr("gui.mmdskin.menu.edit_actions"), () -> menu.open(() -> new ActionWheelConfigScreen(menu))));
        }

        @Override
        public List<MenuRow> rows() {
            List<MenuRow> rows = new ArrayList<>();
            Set<String> listed = new HashSet<>();
            List<ActionOption> favourites = service.loadActions();
            if (!favourites.isEmpty()) {
                rows.add(MenuRow.header(tr("gui.mmdskin.menu.favourites")));
                for (ActionOption action : favourites) {
                    listed.add(action.animId());
                    rows.add(MenuRow.item(action.displayName(), () -> service.selectAction(action.animId())));
                }
            }
            List<MenuRow> others = new ArrayList<>();
            for (ActionWheelConfig.ActionEntry entry : ActionWheelConfig.getInstance().getAvailableActions()) {
                if (entry.animId != null && listed.add(entry.animId)) {
                    String animId = entry.animId;
                    others.add(MenuRow.item(entry.name, entry.getSourceDescription(), false, () -> service.selectAction(animId)));
                }
            }
            if (!others.isEmpty()) {
                rows.add(MenuRow.header(tr("gui.mmdskin.menu.all_animations")));
                rows.addAll(others);
            }
            return rows;
        }
    }

    /** Expressions (the old morph wheel). */
    static final class ExpressionTab implements MenuTab {
        private final MainMenuScreen menu;
        private final DefaultMorphWheelService service = new DefaultMorphWheelService();

        ExpressionTab(MainMenuScreen menu) {
            this.menu = menu;
        }

        @Override
        public Component title() {
            return Component.translatable("gui.mmdskin.menu.tab.expression");
        }

        @Override
        public List<MenuRow> buttons() {
            return List.of(MenuRow.item(tr("gui.mmdskin.menu.edit_expressions"), () -> menu.open(() -> new MorphWheelConfigScreen(menu))));
        }

        @Override
        public List<MenuRow> rows() {
            List<MenuRow> rows = new ArrayList<>();
            for (MorphOption option : service.loadMorphs()) {
                rows.add(MenuRow.item(option.displayName(), () -> service.selectMorph(option)));
            }
            return rows;
        }
    }

    /** Outfit presets of the player's character card. */
    static final class PresetTab implements MenuTab {
        @Override
        public Component title() {
            return Component.translatable("gui.mmdskin.menu.tab.preset");
        }

        @Override
        public Component emptyText() {
            return Component.translatable("message.mmdskin.outfit.none");
        }

        @Override
        public List<MenuRow> rows() {
            List<MenuRow> rows = new ArrayList<>();
            MaterialScreenContext context = ModelSelectorServices.materialVisibility().createPlayerContext().orElse(null);
            OutfitPresets presets = OutfitScreen.presetsFor(context);
            if (presets == null) {
                return rows;
            }
            String current = OutfitScreen.currentOutfit(context, presets);
            for (OutfitPresets.Preset preset : presets.presets()) {
                rows.add(MenuRow.item(preset.name(), "", preset.name().equals(current), () -> OutfitScreen.wear(context, preset)));
            }
            return rows;
        }
    }

    /** Material visibility, in collapsible groups (face, body, hair, clothing slots, accessories). */
    static final class MaterialTab implements MenuTab {
        private final MaterialVisibilityApplicationService service = ModelSelectorServices.materialVisibility();
        private final Set<MaterialGroups.Group> collapsed = EnumSet.noneOf(MaterialGroups.Group.class);
        private MaterialScreenContext context;
        private List<MaterialEntryState> materials = List.of();
        private Set<Integer> initialHidden = Set.of();

        @Override
        public Component title() {
            return Component.translatable("gui.mmdskin.menu.tab.material");
        }

        private void ensureLoaded() {
            MaterialScreenContext now = service.createPlayerContext().orElse(null);
            if (now == null) {
                context = null;
                materials = List.of();
                return;
            }
            if (context == null || context.modelHandle() != now.modelHandle()) {
                context = now;
                materials = service.loadMaterials(now);
                initialHidden = service.snapshotHiddenMaterials(materials);
            }
        }

        @Override
        public List<MenuRow> buttons() {
            ensureLoaded();
            if (context == null) {
                return List.of();
            }
            return List.of(
                    MenuRow.item(tr("gui.mmdskin.material_visibility.show_all"), () -> service.setAllVisible(context, materials, true)),
                    MenuRow.item(tr("gui.mmdskin.material_visibility.hide_all"), () -> service.setAllVisible(context, materials, false)),
                    MenuRow.item(tr("gui.mmdskin.material_visibility.invert"), () -> service.invertSelection(context, materials)));
        }

        @Override
        public List<MenuRow> rows() {
            ensureLoaded();
            List<MenuRow> rows = new ArrayList<>();
            if (context == null) {
                return rows;
            }
            Map<MaterialGroups.Group, List<Integer>> groups = new EnumMap<>(MaterialGroups.Group.class);
            for (int i = 0; i < materials.size(); i++) {
                groups.computeIfAbsent(MaterialGroups.of(materials.get(i).name()), g -> new ArrayList<>()).add(i);
            }
            for (Map.Entry<MaterialGroups.Group, List<Integer>> group : groups.entrySet()) {
                MaterialGroups.Group id = group.getKey();
                List<Integer> members = group.getValue();
                int shown = 0;
                for (int i : members) {
                    if (materials.get(i).visible()) {
                        shown++;
                    }
                }
                boolean open = !collapsed.contains(id);
                rows.add(new MenuRow((open ? "▾ " : "▸ ") + tr(MaterialGroups.key(id)), shown + "/" + members.size(), true, false,
                        () -> {
                            if (!collapsed.remove(id)) {
                                collapsed.add(id);
                            }
                        }));
                if (!open) {
                    continue;
                }
                for (int i : members) {
                    MaterialEntryState material = materials.get(i);
                    int index = i;
                    rows.add(MenuRow.item(displayName(material.name()),
                            tr(material.visible() ? "gui.mmdskin.menu.shown" : "gui.mmdskin.menu.hidden"), material.visible(),
                            () -> service.toggleMaterial(context, materials, index)));
                }
            }
            return rows;
        }

        private static String displayName(String name) {
            return name != null && name.startsWith("SVS ") ? name.substring(4) : name;
        }

        @Override
        public void leave() {
            if (context != null && service.saveIfChanged(context, materials, initialHidden)) {
                initialHidden = service.snapshotHiddenMaterials(materials);
            }
        }
    }

    /** A tab that leads to one of the older full screens. */
    abstract static class LinkTab implements MenuTab {
        protected final MainMenuScreen menu;

        LinkTab(MainMenuScreen menu) {
            this.menu = menu;
        }

        abstract String key();

        abstract void openScreen();

        @Override
        public Component title() {
            return Component.translatable("gui.mmdskin.menu.tab." + key());
        }

        @Override
        public List<MenuRow> rows() {
            return List.of(
                    MenuRow.item(tr("gui.mmdskin.menu." + key() + ".open"), () -> menu.open(() -> {
                        openScreen();
                        return null;
                    })),
                    new MenuRow(tr("gui.mmdskin.menu." + key() + ".hint"), "", false, false, null));
        }
    }

    static final class SceneTab extends LinkTab {
        SceneTab(MainMenuScreen menu) {
            super(menu);
        }

        @Override
        String key() {
            return "scene";
        }

        @Override
        void openScreen() {
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(new SceneSelectorScreen());
        }
    }

    static final class StageTab extends LinkTab {
        StageTab(MainMenuScreen menu) {
            super(menu);
        }

        @Override
        String key() {
            return "stage";
        }

        @Override
        void openScreen() {
            StagePlaybackUiAdapter.INSTANCE.openStageSelection();
        }
    }
}
