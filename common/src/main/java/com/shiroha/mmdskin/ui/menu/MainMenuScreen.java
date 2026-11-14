package com.shiroha.mmdskin.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The mod's main menu (Alt by default): a static panel on the left with a column of tabs (config,
 * model, animation, expression, outfit presets, materials, scene, stage) and the selected tab's list.
 * The character stays visible on the right. Everything is a few rectangles and text, laid out once
 * per resize, without animation, so hovering costs nothing.
 */
public class MainMenuScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int PADDING = 6;
    private static final int TAB_WIDTH = 86;
    private static final int TAB_HEIGHT = 20;
    private static final int TITLE_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 16;
    private static final int ROW_HEIGHT = 16;
    private static final int MAX_PANEL_WIDTH = 380;

    /** The tab that was open last time, so the menu reopens where it was left. */
    private static int lastTab;

    private final KeyMapping toggleKey;
    private final List<MenuTab> tabs;
    private int tab;
    private List<MenuRow> rows = List.of();
    private List<MenuRow> buttons = List.of();
    private int scrollRow;
    private Supplier<Screen> pendingScreen;

    // Layout, computed in init().
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentX;
    private int contentW;
    private int buttonsY;
    private int listTop;
    private int visibleRows;
    private final List<int[]> buttonBoxes = new ArrayList<>();

    public MainMenuScreen(KeyMapping toggleKey) {
        super(Component.translatable("gui.mmdskin.menu.title"));
        this.toggleKey = toggleKey;
        this.tabs = MenuTabs.create(this);
        this.tab = Mth.clamp(lastTab, 0, tabs.size() - 1);
    }

    /** Opens another screen from a tab; it is shown after the current input event. */
    void open(Supplier<Screen> screen) {
        this.pendingScreen = screen;
    }

    /** Rebuilds the rows of the current tab (after an action changed something). */
    void refresh() {
        MenuTab current = tabs.get(tab);
        rows = current.rows();
        buttons = current.buttons();
        layoutButtons();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
    }

    @Override
    protected void init() {
        super.init();
        panelX = MARGIN;
        panelY = MARGIN;
        panelW = Math.min(MAX_PANEL_WIDTH, this.width - MARGIN * 2);
        panelH = this.height - MARGIN * 2;
        contentX = panelX + TAB_WIDTH + PADDING * 2;
        contentW = panelX + panelW - PADDING - contentX;
        refresh();
    }

    private void layoutButtons() {
        buttonBoxes.clear();
        int x = contentX;
        int y = panelY + PADDING + TITLE_HEIGHT;
        for (MenuRow button : buttons) {
            int w = this.font.width(button.label()) + 12;
            if (x + w > contentX + contentW && x > contentX) {
                x = contentX;
                y += BUTTON_HEIGHT + 3;
            }
            buttonBoxes.add(new int[] {x, y, w, BUTTON_HEIGHT});
            x += w + 4;
        }
        buttonsY = y;
        listTop = buttons.isEmpty() ? panelY + PADDING + TITLE_HEIGHT : y + BUTTON_HEIGHT + PADDING;
        visibleRows = Math.max(1, (panelY + panelH - PADDING - listTop) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRows);
    }

    private void selectTab(int index) {
        if (index == tab || index < 0 || index >= tabs.size()) {
            return;
        }
        tabs.get(tab).leave();
        tab = index;
        lastTab = index;
        scrollRow = 0;
        refresh();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        TranslucentTrayChrome.drawPanel(graphics, panelX, panelY, panelW, panelH);

        // Tabs.
        int tabX = panelX + PADDING;
        for (int i = 0; i < tabs.size(); i++) {
            int y = panelY + PADDING + i * (TAB_HEIGHT + 2);
            boolean hovered = inside(mouseX, mouseY, tabX, y, TAB_WIDTH, TAB_HEIGHT);
            graphics.fill(tabX, y, tabX + TAB_WIDTH, y + TAB_HEIGHT, TranslucentTrayChrome.cardBackground(i == tab, hovered));
            if (i == tab) {
                graphics.fill(tabX, y, tabX + 2, y + TAB_HEIGHT, TranslucentTrayChrome.ACCENT);
            }
            graphics.text(this.font, fit(tabs.get(i).title().getString(), TAB_WIDTH - 10), tabX + 6,
                    y + (TAB_HEIGHT - this.font.lineHeight) / 2 + 1,
                    i == tab ? TranslucentTrayChrome.TITLE_TEXT : TranslucentTrayChrome.BODY_TEXT, false);
        }
        graphics.fill(contentX - PADDING, panelY + PADDING, contentX - PADDING + 1, panelY + panelH - PADDING,
                TranslucentTrayChrome.SEPARATOR);

        // Title and buttons.
        graphics.text(this.font, tabs.get(tab).title(), contentX, panelY + PADDING + 3, TranslucentTrayChrome.TITLE_TEXT, false);
        for (int i = 0; i < buttons.size(); i++) {
            int[] box = buttonBoxes.get(i);
            TranslucentTrayChrome.drawButton(graphics, this.font, box[0], box[1], box[2], box[3], buttons.get(i).label(),
                    inside(mouseX, mouseY, box[0], box[1], box[2], box[3]), buttons.get(i).action() != null);
        }

        // Rows.
        if (rows.isEmpty()) {
            graphics.text(this.font, tabs.get(tab).emptyText(), contentX + 2, listTop + 4, TranslucentTrayChrome.DIM_TEXT, false);
            return;
        }
        int rowW = contentW - 4;
        int end = Math.min(rows.size(), scrollRow + visibleRows);
        for (int i = scrollRow; i < end; i++) {
            MenuRow row = rows.get(i);
            int y = listTop + (i - scrollRow) * ROW_HEIGHT;
            boolean hovered = row.action() != null && inside(mouseX, mouseY, contentX, y, rowW, ROW_HEIGHT - 1);
            int textY = y + (ROW_HEIGHT - 1 - this.font.lineHeight) / 2 + 1;
            if (row.header()) {
                if (hovered) {
                    graphics.fill(contentX, y, contentX + rowW, y + ROW_HEIGHT - 1, TranslucentTrayChrome.CARD_BACKGROUND);
                }
                graphics.text(this.font, fit(row.label(), rowW - 60), contentX + 2, textY, TranslucentTrayChrome.ACCENT, false);
                drawDetail(graphics, row.detail(), contentX + rowW - 2, textY, TranslucentTrayChrome.MUTED_TEXT);
                graphics.fill(contentX, y + ROW_HEIGHT - 2, contentX + rowW, y + ROW_HEIGHT - 1, TranslucentTrayChrome.SEPARATOR);
                continue;
            }
            graphics.fill(contentX, y, contentX + rowW, y + ROW_HEIGHT - 1, TranslucentTrayChrome.cardBackground(row.active(), hovered));
            if (row.active()) {
                graphics.fill(contentX, y, contentX + 2, y + ROW_HEIGHT - 1, TranslucentTrayChrome.ACCENT);
            }
            int detailWidth = row.detail().isEmpty() ? 0 : this.font.width(row.detail()) + 8;
            graphics.text(this.font, fit(row.label(), rowW - 10 - detailWidth), contentX + 6, textY,
                    row.active() ? TranslucentTrayChrome.TITLE_TEXT : TranslucentTrayChrome.BODY_TEXT, false);
            drawDetail(graphics, row.detail(), contentX + rowW - 4, textY, TranslucentTrayChrome.DIM_TEXT);
        }
        TranslucentTrayChrome.drawScrollbar(graphics, contentX + contentW - 2, listTop, listTop + visibleRows * ROW_HEIGHT,
                scrollRow * (float) ROW_HEIGHT, maxScroll() * (float) ROW_HEIGHT);
    }

    private void drawDetail(GuiGraphicsExtractor graphics, String detail, int right, int y, int color) {
        if (detail != null && !detail.isEmpty()) {
            graphics.text(this.font, detail, right - this.font.width(detail), y, color, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();
        int tabX = panelX + PADDING;
        for (int i = 0; i < tabs.size(); i++) {
            int y = panelY + PADDING + i * (TAB_HEIGHT + 2);
            if (inside(mx, my, tabX, y, TAB_WIDTH, TAB_HEIGHT)) {
                selectTab(i);
                return true;
            }
        }
        for (int i = 0; i < buttons.size(); i++) {
            int[] box = buttonBoxes.get(i);
            if (inside(mx, my, box[0], box[1], box[2], box[3]) && buttons.get(i).action() != null) {
                run(buttons.get(i).action());
                return true;
            }
        }
        if (my >= listTop && inside(mx, my, contentX, listTop, contentW, visibleRows * ROW_HEIGHT)) {
            int index = scrollRow + (int) ((my - listTop) / ROW_HEIGHT);
            if (index < rows.size() && rows.get(index).action() != null) {
                run(rows.get(index).action());
                return true;
            }
        }
        return inside(mx, my, panelX, panelY, panelW, panelH) || super.mouseClicked(event, doubleClick);
    }

    private void run(Runnable action) {
        action.run();
        if (pendingScreen == null) {
            refresh();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0 && inside(mouseX, mouseY, contentX, listTop, contentW, visibleRows * ROW_HEIGHT)) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY) * 3, 0, maxScroll());
            return true;
        }
        if (scrollY != 0.0 && inside(mouseX, mouseY, panelX, panelY, TAB_WIDTH + PADDING * 2, panelH)) {
            selectTab(Mth.clamp(tab - (int) Math.signum(scrollY), 0, tabs.size() - 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE || isToggleKey(key)) {
            onClose();
            return true;
        }
        if (key == InputConstants.KEY_PAGEDOWN || key == InputConstants.KEY_DOWN) {
            scrollRow = Mth.clamp(scrollRow + (key == InputConstants.KEY_DOWN ? 1 : visibleRows), 0, maxScroll());
            return true;
        }
        if (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_UP) {
            scrollRow = Mth.clamp(scrollRow - (key == InputConstants.KEY_UP ? 1 : visibleRows), 0, maxScroll());
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            selectTab((tab + 1) % tabs.size());
            return true;
        }
        return super.keyPressed(event);
    }

    private boolean isToggleKey(int key) {
        if (toggleKey == null) {
            return false;
        }
        InputConstants.Key bound = KeyMappingUtil.getBoundKey(toggleKey);
        return bound != null && bound.getType() == InputConstants.Type.KEYBOARD && bound.getValue() == key;
    }

    @Override
    public void tick() {
        super.tick();
        Supplier<Screen> next = pendingScreen;
        if (next != null) {
            pendingScreen = null;
            tabs.get(tab).leave();
            Screen screen = next.get();
            if (screen != null) {
                Minecraft.getInstance().gui.setScreen(screen);
            }
        }
    }

    @Override
    public void onClose() {
        tabs.get(tab).leave();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String fit(String text, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String trimmed = text;
        while (trimmed.length() > 1 && this.font.width(trimmed + "..") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }

    private static boolean inside(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }
}
