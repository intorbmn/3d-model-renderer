package com.shiroha.mmdskin.companion;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side: which mobs were given a model with the model tag ("companions"), as the server
 * reports them, and the animation each one is playing for the player.
 */
public final class CompanionState {
    /** How long a picked animation plays before the mob goes back to its own motion. */
    public static final long ACTION_MILLIS = 6000L;

    private static final Map<UUID, String> MODELS = new ConcurrentHashMap<>();
    private static final Map<UUID, java.util.List<String>> LINES = new ConcurrentHashMap<>();
    private static final Map<UUID, Action> ACTIONS = new ConcurrentHashMap<>();

    /** An animation a companion plays: started once per loaded model. */
    public static final class Action {
        public final String animId;
        public final long until;
        public volatile Object startedFor;

        Action(String animId, long until) {
            this.animId = animId;
            this.until = until;
        }
    }

    private CompanionState() {
    }

    public static void setModel(UUID entity, String model) {
        if (entity == null) {
            return;
        }
        if (model == null || model.isBlank()) {
            MODELS.remove(entity);
            LINES.remove(entity);
            ACTIONS.remove(entity);
        } else {
            MODELS.put(entity, model);
        }
    }

    /** A sync message from the server: {"model": "...", "lines": ["...", ...]} ("" removes). */
    public static void applySync(UUID entity, String json) {
        if (json == null || json.isBlank()) {
            setModel(entity, null);
            return;
        }
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String model = root.has("model") ? root.get("model").getAsString() : null;
            setModel(entity, model);
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (root.has("lines") && root.get("lines").isJsonArray()) {
                root.getAsJsonArray("lines").forEach(line -> lines.add(line.getAsString()));
            }
            if (model != null && !model.isBlank()) {
                LINES.put(entity, java.util.List.copyOf(lines));
            }
        } catch (RuntimeException e) {
            setModel(entity, null);
        }
    }

    /** The companion's talk lines (empty: it says the default line). */
    public static java.util.List<String> lines(UUID entity) {
        return entity == null ? java.util.List.of() : LINES.getOrDefault(entity, java.util.List.of());
    }

    /** The companion's model, or null when the mob has none. */
    public static String model(UUID entity) {
        return entity == null ? null : MODELS.get(entity);
    }

    public static boolean isCompanion(UUID entity) {
        return model(entity) != null;
    }

    public static void play(UUID entity, String animId) {
        if (entity != null && animId != null && !animId.isBlank()) {
            ACTIONS.put(entity, new Action(animId, System.currentTimeMillis() + ACTION_MILLIS));
        }
    }

    /** The animation the companion plays now, or null (an expired one is dropped). */
    public static Action action(UUID entity) {
        Action action = entity == null ? null : ACTIONS.get(entity);
        if (action != null && System.currentTimeMillis() > action.until) {
            ACTIONS.remove(entity, action);
            return null;
        }
        return action;
    }

    public static void stop(UUID entity) {
        if (entity != null) {
            ACTIONS.remove(entity);
        }
    }

    public static void clear() {
        MODELS.clear();
        LINES.clear();
        ACTIONS.clear();
    }
}
