package com.shiroha.mmdskin.ui.menu;

import java.util.Locale;

/**
 * Sorts material names into groups for the material tab: face, body, hair, the clothing slots and
 * accessories. Knows the names of ILLGAMES (Koikatsu / Summer Vacation Scramble) exports and common
 * English and Japanese MMD names; anything else is "other".
 */
public final class MaterialGroups {
    public enum Group {
        FACE, BODY, HAIR, TOP, BOTTOM, UNDERWEAR, LEGWEAR, SHOES, ACCESSORY, OTHER
    }

    private MaterialGroups() {
    }

    public static Group of(String materialName) {
        if (materialName == null) {
            return Group.OTHER;
        }
        String n = materialName.toLowerCase(Locale.ROOT);
        if (has(n, "m_acs", "acs_", "accessor", "ribbon", "ribon", "装飾", "アクセ", "リボン")) {
            return Group.ACCESSORY;
        }
        if (has(n, "head", "face", "mayuge", "eyelash", "eyelid", "eyeline", "hitomi", "sirome", "eye",
                "tooth", "teeth", "tango", "tongue", "mouth", "namida", "brow", "顔", "目", "眉", "瞳", "歯", "舌", "口", "まつ")) {
            return Group.FACE;
        }
        if (has(n, "hair", "髪", "kami")) {
            return Group.HAIR;
        }
        if (has(n, "bra", "shorts", "panties", "pants_under", "underwear", "lingerie", "下着", "ブラ", "パンツ")) {
            return Group.UNDERWEAR;
        }
        if (has(n, "sock", "panst", "stocking", "tights", "legging", "靴下", "ストッキング", "タイツ")) {
            return Group.LEGWEAR;
        }
        if (has(n, "shoe", "boot", "loafer", "heel", "sandal", "靴", "ブーツ")) {
            return Group.SHOES;
        }
        if (has(n, "bot", "skirt", "pants", "trouser", "スカート", "ズボン")) {
            return Group.BOTTOM;
        }
        if (has(n, "top", "shirt", "jacket", "coat", "vest", "dress", "cloth", "uniform", "服", "シャツ", "上着")) {
            return Group.TOP;
        }
        if (has(n, "body", "skin", "nail", "肌", "体", "身体", "爪")) {
            return Group.BODY;
        }
        return Group.OTHER;
    }

    private static boolean has(String name, String... parts) {
        for (String part : parts) {
            if (name.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /** The translation key of a group's header. */
    public static String key(Group group) {
        return "gui.mmdskin.menu.group." + group.name().toLowerCase(Locale.ROOT);
    }
}
