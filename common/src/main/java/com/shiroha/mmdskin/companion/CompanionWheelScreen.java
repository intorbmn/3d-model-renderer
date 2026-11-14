package com.shiroha.mmdskin.companion;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.asset.catalog.ModelCatalogEntry;
import com.shiroha.mmdskin.compat.maid.service.MaidActionService;
import com.shiroha.mmdskin.compat.maid.service.MaidModelSelectionService;
import com.shiroha.mmdskin.compat.maid.ui.MaidActionWheelScreen;
import com.shiroha.mmdskin.compat.maid.ui.MaidModelSelectorScreen;
import com.shiroha.mmdskin.ui.config.ActionWheelConfig;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.wheel.AbstractWheelScreen;
import com.shiroha.mmdskin.ui.wheel.service.ActionOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * The wheels of a mob given a model with the model tag ("companion"):
 * <ul>
 *   <li>with the tag in hand: Model (pick or remove its model) and Lines (what it says);</li>
 *   <li>with an empty hand: Talk and Animations.</li>
 * </ul>
 */
public class CompanionWheelScreen extends AbstractWheelScreen {
    private static final WheelStyle STYLE = createTranslucentWheelStyle(0.45f, 0.35f);

    private final List<Slot> slots = new ArrayList<>();
    private final UUID entityUUID;
    private final int entityId;
    private final String entityName;

    private record Slot(String name, Runnable action) {
    }

    private CompanionWheelScreen(Entity entity, boolean setup) {
        super(Component.translatable(setup ? "gui.mmdskin.companion.setup" : "gui.mmdskin.companion.interact"), STYLE);
        this.entityUUID = entity.getUUID();
        this.entityId = entity.getId();
        this.entityName = entity.getDisplayName().getString();
        if (setup) {
            slots.add(new Slot(Component.translatable("gui.mmdskin.companion.model").getString(), this::openModels));
            if (CompanionState.isCompanion(entityUUID)) {
                slots.add(new Slot(Component.translatable("gui.mmdskin.companion.lines").getString(), this::openLines));
            }
        } else {
            slots.add(new Slot(Component.translatable("gui.mmdskin.companion.talk").getString(),
                    () -> CompanionHooks.sender.send(NetworkOpCode.COMPANION_TALK, entityId, "")));
            slots.add(new Slot(Component.translatable("gui.mmdskin.companion.animations").getString(), this::openAnimations));
        }
    }

    /** Opens the setup wheel (model tag in hand). */
    public static void openSetup(Object entity) {
        if (entity instanceof Entity e) {
            Minecraft.getInstance().gui.setScreen(new CompanionWheelScreen(e, true));
        }
    }

    /** Opens the interaction wheel (empty hand on a companion). */
    public static void openInteraction(Object entity) {
        if (entity instanceof Entity e) {
            Minecraft.getInstance().gui.setScreen(new CompanionWheelScreen(e, false));
        }
    }

    @Override
    protected int getSlotCount() {
        return slots.size();
    }

    @Override
    protected void init() {
        super.init();
        initWheelLayout();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        List<WheelEntry> entries = new ArrayList<>(slots.size());
        for (Slot slot : slots) {
            entries.add(new WheelEntry(slot.name(), null));
        }
        renderWheelBase(guiGraphics, mouseX, mouseY, partialTick, entries);
        String center = selectedSlot >= 0 && selectedSlot < slots.size() ? slots.get(selectedSlot).name() : entityName;
        renderCenterBubble(guiGraphics, center, style.lineColor());
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && selectedSlot >= 0 && selectedSlot < slots.size()) {
            Slot slot = slots.get(selectedSlot);
            this.onClose();
            slot.action().run();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void openModels() {
        MaidModelSelectionService service = new MaidModelSelectionService() {
            @Override
            public List<String> loadAvailableModels() {
                List<String> models = new ArrayList<>();
                models.add(UIConstants.DEFAULT_MODEL_NAME);
                for (ModelCatalogEntry entry : ModelCatalogEntry.scanModels()) {
                    models.add(entry.getFolderName());
                }
                return models;
            }

            @Override
            public String getCurrentModel(UUID uuid) {
                String model = CompanionState.model(uuid);
                return model == null ? UIConstants.DEFAULT_MODEL_NAME : model;
            }

            @Override
            public void selectModel(UUID uuid, int id, String modelName) {
                String model = modelName == null || UIConstants.DEFAULT_MODEL_NAME.equals(modelName) ? "" : modelName;
                CompanionHooks.sender.send(NetworkOpCode.COMPANION_SET, id, model);
            }
        };
        Minecraft.getInstance().gui.setScreen(new MaidModelSelectorScreen(entityUUID, entityId, entityName, service,
                Component.translatable("gui.mmdskin.companion.model_title", entityName)));
    }

    private void openAnimations() {
        MaidActionService service = new MaidActionService() {
            @Override
            public List<ActionOption> loadActions() {
                return ActionWheelConfig.getInstance().getDisplayedActions().stream()
                        .map(entry -> new ActionOption(entry.name, entry.animId))
                        .toList();
            }

            @Override
            public void selectAction(UUID uuid, int id, String animId) {
                CompanionHooks.sender.send(NetworkOpCode.COMPANION_ACTION, id, animId);
            }
        };
        Minecraft.getInstance().gui.setScreen(new MaidActionWheelScreen(entityUUID, entityId, entityName, service,
                Component.translatable("gui.mmdskin.companion.animations")));
    }

    private void openLines() {
        Minecraft.getInstance().gui.setScreen(new CompanionLinesScreen(entityUUID, entityId, entityName));
    }
}
