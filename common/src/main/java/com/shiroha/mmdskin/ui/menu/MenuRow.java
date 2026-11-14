package com.shiroha.mmdskin.ui.menu;

/**
 * One line of a menu list: a group header, or an entry with an optional detail on the right and a
 * marker when it is the current choice or switched on.
 */
public record MenuRow(String label, String detail, boolean header, boolean active, Runnable action) {
    public static MenuRow header(String label) {
        return new MenuRow(label, "", true, false, null);
    }

    public static MenuRow item(String label, Runnable action) {
        return new MenuRow(label, "", false, false, action);
    }

    public static MenuRow item(String label, String detail, boolean active, Runnable action) {
        return new MenuRow(label, detail == null ? "" : detail, false, active, action);
    }
}
