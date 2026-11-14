package com.shiroha.mmdskin.companion;

import java.util.function.Consumer;

/**
 * Connects the shared interaction code (run on both sides) to client-only screens and to the
 * network sender, which the client entry point sets.
 */
public final class CompanionHooks {
    /** Opens the model tag's setup wheel for an entity (client only). */
    public static volatile Consumer<Object> openSetup = entity -> { };
    /** Opens the interaction wheel (talk, animations) for a companion (client only). */
    public static volatile Consumer<Object> openInteraction = entity -> { };
    /** Sends a companion request: op code, entity id, text. */
    public static volatile Sender sender = (opCode, entityId, data) -> { };

    @FunctionalInterface
    public interface Sender {
        void send(int opCode, int entityId, String data);
    }

    private CompanionHooks() {
    }
}
