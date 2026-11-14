package com.shiroha.mmdskin.companion;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.ui.config.ActionWheelConfig;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.wheel.AbstractWheelScreen;
import com.shiroha.mmdskin.ui.wheel.service.ActionOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Picks the animations a companion offers when players interact with it: every animation found,
 * ticked when the mob has it. The choice is sent to the server (kept with the world) on close.
 */
public class CompanionAnimationsScreen extends AbstractWheelScreen {
    private static final WheelStyle STYLE = createTranslucentWheelStyle(0.5f, 0.3f);

    private final int entityId;
    private final List<ActionOption> all;
    private final Set<String> chosen;
    private final Set<String> original;

    public CompanionAnimationsScreen(UUID entityUUID, int entityId, String entityName) {
        super(Component.translatable("gui.mmdskin.companion.edit_title", entityName), STYLE);
        this.entityId = entityId;
        this.all = allAnimations();
        this.chosen = new LinkedHashSet<>(CompanionState.anims(entityUUID));
        this.original = Set.copyOf(chosen);
    }

    /** Every animation the player has: the favourites first, then the rest. */
    static List<ActionOption> allAnimations() {
        Map<String, ActionOption> byId = new LinkedHashMap<>();
        ActionWheelConfig config = ActionWheelConfig.getInstance();
        for (ActionWheelConfig.ActionEntry entry : config.getDisplayedActions()) {
            if (entry.animId != null) {
                byId.putIfAbsent(entry.animId, new ActionOption(entry.name, entry.animId));
            }
        }
        for (ActionWheelConfig.ActionEntry entry : config.getAvailableActions()) {
            if (entry.animId != null) {
                byId.putIfAbsent(entry.animId, new ActionOption(entry.name, entry.animId));
            }
        }
        return new ArrayList<>(byId.values());
    }

    /** The animations offered by a companion: its own list, or the player's favourites when it has none. */
    static List<ActionOption> offered(UUID entity) {
        List<String> own = CompanionState.anims(entity);
        List<ActionOption> options = new ArrayList<>();
        if (own.isEmpty()) {
            for (ActionWheelConfig.ActionEntry entry : ActionWheelConfig.getInstance().getDisplayedActions()) {
                options.add(new ActionOption(entry.name, entry.animId));
            }
            return options;
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (ActionOption option : allAnimations()) {
            names.put(option.animId(), option.displayName());
        }
        for (String animId : own) {
            options.add(new ActionOption(names.getOrDefault(animId, animId), animId));
        }
        return options;
    }

    @Override
    protected int getSlotCount() {
        return all.size();
    }

    @Override
    protected void init() {
        super.init();
        initWheelLayout();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        List<WheelEntry> entries = new ArrayList<>(all.size());
        for (ActionOption option : all) {
            entries.add(new WheelEntry(option.displayName(), chosen.contains(option.animId()) ? "✔" : ""));
        }
        renderWheelBase(graphics, mouseX, mouseY, partialTick, entries);
        if (all.isEmpty()) {
            renderEmptyState(graphics, Component.translatable("gui.mmdskin.action_wheel.no_actions"));
        }
        renderCenterBubble(graphics, Component.translatable("gui.mmdskin.companion.edit_hint", chosen.size()).getString(),
                style.lineColor());
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && selectedSlot >= 0 && selectedSlot < all.size()) {
            String animId = all.get(selectedSlot).animId();
            if (!chosen.remove(animId)) {
                chosen.add(animId);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        if (!chosen.equals(original)) {
            CompanionHooks.sender.send(NetworkOpCode.COMPANION_ANIMS, entityId, String.join("\n", chosen));
        }
        super.onClose();
    }
}
