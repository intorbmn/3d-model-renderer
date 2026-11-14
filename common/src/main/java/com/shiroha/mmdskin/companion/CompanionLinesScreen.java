package com.shiroha.mmdskin.companion;

import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What a companion says when a player talks to it: up to eight lines, said in turn. Empty lines
 * are skipped; with none, it says a default line.
 */
public class CompanionLinesScreen extends Screen {
    private static final int LINES = 8;
    private static final int WIDTH = 300;

    private final UUID entityUUID;
    private final int entityId;
    private final EditBox[] boxes = new EditBox[LINES];

    CompanionLinesScreen(UUID entityUUID, int entityId, String entityName) {
        super(Component.translatable("gui.mmdskin.companion.lines_title", entityName));
        this.entityUUID = entityUUID;
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - WIDTH) / 2;
        int y = Math.max(10, this.height / 2 - (LINES * 24 + 60) / 2);
        Button title = Button.builder(this.getTitle(), button -> { }).bounds(x, y, WIDTH, 20).build();
        title.active = false;
        this.addRenderableWidget(title);
        y += 28;
        List<String> current = CompanionState.lines(entityUUID);
        for (int i = 0; i < LINES; i++) {
            String previous = boxes[i] != null ? boxes[i].getValue() : (i < current.size() ? current.get(i) : "");
            EditBox box = new EditBox(this.font, x, y, WIDTH, 20, Component.translatable("gui.mmdskin.companion.line", i + 1));
            box.setMaxLength(200);
            box.setValue(previous);
            boxes[i] = box;
            this.addRenderableWidget(box);
            y += 24;
        }
        y += 6;
        int half = (WIDTH - 6) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                .bounds(x, y, half, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(x + half + 6, y, half, 20).build());
    }

    private void save() {
        StringBuilder text = new StringBuilder();
        for (EditBox box : boxes) {
            String line = box.getValue().trim();
            if (!line.isEmpty()) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(line);
            }
        }
        CompanionHooks.sender.send(NetworkOpCode.COMPANION_LINES, entityId, text.toString());
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
