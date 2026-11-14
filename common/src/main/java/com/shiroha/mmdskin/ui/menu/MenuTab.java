package com.shiroha.mmdskin.ui.menu;

import java.util.List;
import net.minecraft.network.chat.Component;

/** A tab of the main menu: a list of rows and a few buttons above it. */
public interface MenuTab {
    Component title();

    /** The rows, rebuilt only when the tab opens or after one of its actions ran. */
    List<MenuRow> rows();

    /** Buttons above the list. */
    default List<MenuRow> buttons() {
        return List.of();
    }

    /** Shown in the list when it is empty. */
    default Component emptyText() {
        return Component.translatable("gui.mmdskin.menu.empty");
    }

    /** The tab is left (another tab opened or the menu closed): save what changed. */
    default void leave() {
    }
}
