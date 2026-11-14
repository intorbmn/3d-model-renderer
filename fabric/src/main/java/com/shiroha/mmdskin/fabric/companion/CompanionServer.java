package com.shiroha.mmdskin.fabric.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shiroha.mmdskin.companion.CompanionHooks;
import com.shiroha.mmdskin.companion.CompanionState;
import com.shiroha.mmdskin.fabric.network.MmdSkinPayloads;
import com.shiroha.mmdskin.fabric.register.MmdSkinItems;
import com.shiroha.mmdskin.fabric.register.MmdSkinRegisterCommon;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.EntityHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mobs given their own model with the model tag ("companions"), on the server: which model each
 * one wears and what it says, kept with the world (data/mmdskin_companions.json) and sent to every
 * player. Players can talk to a companion and make it play an animation.
 */
public final class CompanionServer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final double RANGE_SQ = 12.0 * 12.0;
    private static final double HEAR_RANGE = 24.0;
    private static final int MAX_LINES = 8;
    private static final int MAX_TEXT = 200;

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static volatile Path file;

    private static final class Entry {
        String model;
        List<String> lines = new ArrayList<>();
        int next;
    }

    private CompanionServer() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(CompanionServer::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            save();
            ENTRIES.clear();
            file = null;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> sendAll(handler.getPlayer())));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (ENTRIES.remove(entity.getUUID()) != null && entity.level() instanceof ServerLevel level) {
                save();
                broadcast(level.getServer(), NetworkOpCode.COMPANION_SYNC, entity.getUUID(), "");
            }
        });
        UseEntityCallback.EVENT.register(CompanionServer::onUseEntity);
    }

    /** Right-click on a mob: with the model tag, its setup; with an empty hand, talk and animations. */
    private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity,
                                                 EntityHitResult hit) {
        if (!(entity instanceof LivingEntity) || entity instanceof Player || isMaid(entity)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        boolean client = level.isClientSide();
        if (MmdSkinItems.MODEL_TAG != null && stack.is(MmdSkinItems.MODEL_TAG)) {
            if (client) {
                CompanionHooks.openSetup.accept(entity);
            }
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND || !stack.isEmpty() || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        boolean companion = client ? CompanionState.isCompanion(entity.getUUID()) : ENTRIES.containsKey(entity.getUUID());
        if (!companion) {
            return InteractionResult.PASS;
        }
        if (client) {
            CompanionHooks.openInteraction.accept(entity);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean isMaid(Entity entity) {
        String className = entity.getClass().getName();
        return className.contains("EntityMaid") || className.contains("touhoulittlemaid");
    }

    /** True for the requests this class handles. */
    public static boolean handles(int opCode) {
        return opCode == NetworkOpCode.COMPANION_SET || opCode == NetworkOpCode.COMPANION_LINES
                || opCode == NetworkOpCode.COMPANION_TALK || opCode == NetworkOpCode.COMPANION_ACTION;
    }

    /** A request from a player (network thread): checked and run on the server thread. */
    public static void handle(MinecraftServer server, ServerPlayer player, int opCode, int entityId, String data) {
        server.execute(() -> {
            try {
                run(server, player, opCode, entityId, data == null ? "" : data);
            } catch (RuntimeException e) {
                LOGGER.warn("Companion request {} from {} failed", opCode, player.getName().getString(), e);
            }
        });
    }

    private static void run(MinecraftServer server, ServerPlayer player, int opCode, int entityId, String data) {
        Entity entity = player.level().getEntity(entityId);
        if (!(entity instanceof LivingEntity) || entity instanceof Player || isMaid(entity)
                || player.distanceToSqr(entity) > RANGE_SQ) {
            return;
        }
        UUID id = entity.getUUID();
        switch (opCode) {
            case NetworkOpCode.COMPANION_SET -> {
                if (!holdsTag(player)) {
                    return;
                }
                String model = clip(data.trim());
                if (model.isEmpty()) {
                    ENTRIES.remove(id);
                } else {
                    ENTRIES.computeIfAbsent(id, key -> new Entry()).model = model;
                }
                save();
                broadcast(server, NetworkOpCode.COMPANION_SYNC, id, syncJson(ENTRIES.get(id)));
            }
            case NetworkOpCode.COMPANION_LINES -> {
                Entry entry = ENTRIES.get(id);
                if (entry == null || !holdsTag(player)) {
                    return;
                }
                List<String> lines = new ArrayList<>();
                for (String line : data.split("\n")) {
                    if (!line.isBlank() && lines.size() < MAX_LINES) {
                        lines.add(clip(line.trim()));
                    }
                }
                entry.lines = lines;
                entry.next = 0;
                save();
                broadcast(server, NetworkOpCode.COMPANION_SYNC, id, syncJson(entry));
            }
            case NetworkOpCode.COMPANION_TALK -> {
                Entry entry = ENTRIES.get(id);
                if (entry == null) {
                    return;
                }
                attend(entity, player);
                Component line = entry.lines.isEmpty()
                        ? Component.translatable("message.mmdskin.companion.default_line")
                        : Component.literal(entry.lines.get(entry.next++ % entry.lines.size()));
                Component message = Component.translatable("chat.type.text", entity.getDisplayName(), line);
                if (entity.level() instanceof ServerLevel level) {
                    for (ServerPlayer listener : PlayerLookup.around(level, entity.position(), HEAR_RANGE)) {
                        listener.sendSystemMessage(message);
                    }
                }
                broadcast(server, NetworkOpCode.COMPANION_PLAY, id, "talk");
            }
            case NetworkOpCode.COMPANION_ACTION -> {
                if (!ENTRIES.containsKey(id) || data.isBlank()) {
                    return;
                }
                attend(entity, player);
                broadcast(server, NetworkOpCode.COMPANION_PLAY, id, clip(data.trim()));
            }
            default -> {
            }
        }
    }

    /** The mob stops and looks at the player it is with. */
    private static void attend(Entity entity, ServerPlayer player) {
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(player, 30.0f, 30.0f);
        }
    }

    private static boolean holdsTag(ServerPlayer player) {
        return MmdSkinItems.MODEL_TAG != null
                && (player.getMainHandItem().is(MmdSkinItems.MODEL_TAG) || player.getOffhandItem().is(MmdSkinItems.MODEL_TAG));
    }

    private static String clip(String text) {
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }

    private static String syncJson(Entry entry) {
        if (entry == null) {
            return "";
        }
        JsonObject root = new JsonObject();
        root.addProperty("model", entry.model);
        JsonArray lines = new JsonArray();
        entry.lines.forEach(lines::add);
        root.add("lines", lines);
        return root.toString();
    }

    private static void sendAll(ServerPlayer player) {
        ENTRIES.forEach((id, entry) -> send(player, NetworkOpCode.COMPANION_SYNC, id, syncJson(entry)));
    }

    private static void broadcast(MinecraftServer server, int opCode, UUID entity, String data) {
        for (ServerPlayer player : PlayerLookup.all(server)) {
            send(player, opCode, entity, data);
        }
    }

    private static void send(ServerPlayer player, int opCode, UUID entity, String data) {
        FriendlyByteBuf buffer = MmdSkinPayloads.newBuffer();
        buffer.writeInt(opCode);
        buffer.writeUUID(entity);
        buffer.writeUtf(data);
        MmdSkinRegisterCommon.sendToClient(player, buffer);
    }

    private static void load(MinecraftServer server) {
        ENTRIES.clear();
        file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("mmdskin_companions.json");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> item : root.entrySet()) {
                JsonObject value = item.getValue().getAsJsonObject();
                Entry entry = new Entry();
                entry.model = value.get("model").getAsString();
                if (value.has("lines")) {
                    value.getAsJsonArray("lines").forEach(line -> entry.lines.add(line.getAsString()));
                }
                ENTRIES.put(UUID.fromString(item.getKey()), entry);
            }
            LOGGER.info("Loaded {} companion(s)", ENTRIES.size());
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}", file, e);
        }
    }

    private static synchronized void save() {
        Path target = file;
        if (target == null) {
            return;
        }
        JsonObject root = new JsonObject();
        ENTRIES.forEach((id, entry) -> {
            JsonObject value = new JsonObject();
            value.addProperty("model", entry.model);
            JsonArray lines = new JsonArray();
            entry.lines.forEach(lines::add);
            value.add("lines", lines);
            root.add(id.toString(), value);
        });
        try {
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", target, e);
        }
    }
}
