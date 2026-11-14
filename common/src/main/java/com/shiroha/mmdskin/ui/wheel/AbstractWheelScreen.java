package com.shiroha.mmdskin.ui.wheel;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Base of the pick-one menus (actions, expressions, companion and maid menus). It used to draw a
 * radial wheel row by row with thousands of fills and spring animations every frame, which made
 * hovering lag; it is now a plain list panel: a handful of rectangles, no animation. The slot under
 * the mouse is {@link #selectedSlot}, as before, so the menus themselves are unchanged.
 */
public abstract class AbstractWheelScreen extends Screen {

    public record WheelStyle(
            float screenRatio, float innerRatio,
            int lineColor, int lineColorDim, int highlightColor,
            int centerBg, int centerBorder, int textShadow
    ) {}

    public record WheelEntry(String primaryText, String secondaryText) {}

    private static final int PANEL_WIDTH = 240;
    private static final int ROW_HEIGHT = 18;
    private static final int HEADER_HEIGHT = 30;
    private static final int PADDING = 6;

    protected final WheelStyle style;
    /** Panel centre, kept for subclasses that position things around the menu. */
    protected int centerX;
    protected int centerY;
    protected int outerRadius;
    protected int innerRadius;
    protected int selectedSlot = -1;

    private int panelX;
    private int panelY;
    private int panelHeight;
    private int listTop;
    private int visibleRows;
    private int scrollRow;
    private int layoutSlots = -1;

    protected AbstractWheelScreen(Component title, WheelStyle style) {
        super(title);
        this.style = style;
    }

    protected static WheelStyle createTranslucentWheelStyle(float screenRatio, float innerRatio) {
        return new WheelStyle(screenRatio, innerRatio,
                TranslucentTrayChrome.TITLE_TEXT, TranslucentTrayChrome.SUBTITLE_TEXT, TranslucentTrayChrome.CARD_HOVER,
                0xFF20262D, 0xFFDDE3E9, 0xD0000000);
    }

    protected abstract int getSlotCount();

    protected void initWheelLayout() {
        int count = Math.max(1, getSlotCount());
        int maxRows = Math.max(3, (this.height - 2 * PADDING - HEADER_HEIGHT - 40) / ROW_HEIGHT);
        visibleRows = Math.min(count, maxRows);
        panelHeight = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + PADDING * 2;
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = Math.max(PADDING, (this.height - panelHeight) / 2);
        listTop = panelY + HEADER_HEIGHT + PADDING;
        centerX = this.width / 2;
        centerY = panelY + panelHeight / 2;
        outerRadius = PANEL_WIDTH / 2;
        innerRadius = HEADER_HEIGHT;
        scrollRow = Mth.clamp(scrollRow, 0, Math.max(0, getSlotCount() - visibleRows));
        layoutSlots = getSlotCount();
    }

    protected void updateSelectedSlot(int mouseX, int mouseY) {
        selectedSlot = -1;
        if (mouseX < panelX + PADDING || mouseX >= panelX + PANEL_WIDTH - PADDING || mouseY < listTop) {
            return;
        }
        int row = (mouseY - listTop) / ROW_HEIGHT;
        if (row < visibleRows) {
            int slot = scrollRow + row;
            selectedSlot = slot < getSlotCount() ? slot : -1;
        }
    }

    protected void renderWheelBase(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, List<WheelEntry> entries) {
        if (layoutSlots != getSlotCount()) {
            initWheelLayout();
        }
        updateSelectedSlot(mouseX, mouseY);
        TranslucentTrayChrome.drawOverlay(graphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight);
        graphics.text(this.font, this.title, panelX + PADDING + 2, panelY + PADDING + 2, TranslucentTrayChrome.TITLE_TEXT, false);
        TranslucentTrayChrome.drawSeparator(graphics, panelX + PADDING, panelY + HEADER_HEIGHT, PANEL_WIDTH - PADDING * 2);

        int rowWidth = PANEL_WIDTH - PADDING * 2 - 4;
        int end = Math.min(entries.size(), scrollRow + visibleRows);
        for (int i = scrollRow; i < end; i++) {
            int y = listTop + (i - scrollRow) * ROW_HEIGHT;
            int x = panelX + PADDING;
            graphics.fill(x, y, x + rowWidth, y + ROW_HEIGHT - 2, TranslucentTrayChrome.cardBackground(false, i == selectedSlot));
            WheelEntry entry = entries.get(i);
            String label = entry.secondaryText() == null || entry.secondaryText().isEmpty()
                    ? entry.primaryText()
                    : entry.primaryText() + "  " + entry.secondaryText();
            graphics.text(this.font, fitText(label, rowWidth - 8), x + 4, y + (ROW_HEIGHT - 2 - this.font.lineHeight) / 2 + 1,
                    i == selectedSlot ? TranslucentTrayChrome.TITLE_TEXT : TranslucentTrayChrome.BODY_TEXT, false);
        }
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        TranslucentTrayChrome.drawScrollbar(graphics, panelX + PANEL_WIDTH - PADDING - 2, listTop,
                listTop + visibleRows * ROW_HEIGHT, scrollRow * (float) ROW_HEIGHT, maxScroll * (float) ROW_HEIGHT);
    }

    /** The hint line under the title (it used to be the bubble in the middle of the wheel). */
    protected void renderCenterBubble(GuiGraphicsExtractor graphics, String text, int textColor) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int titleWidth = this.font.width(this.title);
        int x = panelX + PADDING + 2 + titleWidth + 10;
        int room = panelX + PANEL_WIDTH - PADDING - 2 - x;
        if (room > 20) {
            graphics.text(this.font, fitText(text, room), x, panelY + PADDING + 2, TranslucentTrayChrome.MUTED_TEXT, false);
        }
    }

    protected void renderEmptyState(GuiGraphicsExtractor graphics, Component hint) {
        graphics.centeredText(this.font, hint, panelX + PANEL_WIDTH / 2, listTop + 4, TranslucentTrayChrome.DIM_TEXT);
    }

    protected Button createWheelIconButton(Component label, Button.OnPress onPress) {
        return Button.builder(label, onPress)
                .bounds(panelX + PANEL_WIDTH - PADDING - 20, panelY + PADDING - 1, 20, 18)
                .build();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, getSlotCount() - visibleRows);
        if (maxScroll > 0 && scrollY != 0.0) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    protected void renderFittedLabel(GuiGraphicsExtractor graphics, String text, int cx, int cy, int maxWidth, int color) {
        if (text != null && !text.isEmpty()) {
            graphics.centeredText(this.font, fitText(text, maxWidth), cx, cy - this.font.lineHeight / 2, color);
        }
    }

    protected String fitText(String text, int maxWidth) {
        if (text == null || text.isEmpty() || this.font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String trimmed = text;
        while (trimmed.length() > 1 && this.font.width(trimmed + "..") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }

    protected int blendColors(int from, int to, float progress) {
        float t = Mth.clamp(progress, 0.0f, 1.0f);
        int a = Mth.floor(Mth.lerp(t, from >>> 24, to >>> 24));
        int r = Mth.floor(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Mth.floor(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Mth.floor(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return a << 24 | r << 16 | g << 8 | b;
    }

    protected int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
