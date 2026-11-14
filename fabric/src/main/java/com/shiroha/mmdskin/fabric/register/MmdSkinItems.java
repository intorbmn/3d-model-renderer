package com.shiroha.mmdskin.fabric.register;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/** The mod's items. */
public final class MmdSkinItems {
    /** Gives one mob its own model (right-click it), instead of every mob of its kind. */
    public static final ResourceKey<Item> MODEL_TAG_KEY =
            ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("mmdskin", "model_tag"));
    public static Item MODEL_TAG;

    private MmdSkinItems() {
    }

    public static void register() {
        MODEL_TAG = Registry.register(BuiltInRegistries.ITEM, MODEL_TAG_KEY,
                new Item(new Item.Properties().setId(MODEL_TAG_KEY).stacksTo(1)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(MODEL_TAG));
    }
}
