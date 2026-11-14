package com.shiroha.mmdskin.ui.network;

/**
 * 网络操作码常量定义
 */
public final class NetworkOpCode {
    public static final int CUSTOM_ANIM = 1;
    public static final int RESET_PHYSICS = 2;
    public static final int MODEL_SELECT = 3;
    public static final int MAID_MODEL = 4;
    public static final int MAID_ACTION = 5;
    public static final int MORPH_SYNC = 6;
    public static final int REQUEST_ALL_MODELS = 10;
    public static final int STAGE_MULTI = 11;
    public static final int BONE_SYNC = 12;
    // Mobs given a model with the model tag ("companions").
    // Client → server, entity id + text: set the model ("" removes it), set the talk lines (one per
    // line), talk, play an animation.
    public static final int COMPANION_SET = 20;
    public static final int COMPANION_LINES = 21;
    public static final int COMPANION_TALK = 22;
    public static final int COMPANION_ACTION = 23;
    // Server → client, the entity's UUID in place of the player's + text: its model ("" = none),
    // an animation to play.
    public static final int COMPANION_SYNC = 24;
    public static final int COMPANION_PLAY = 25;


    public static boolean isStringPayload(int opCode) {
        return opCode == CUSTOM_ANIM || opCode == MODEL_SELECT
                || opCode == MORPH_SYNC || opCode == REQUEST_ALL_MODELS
                || opCode == STAGE_MULTI || opCode == COMPANION_SYNC || opCode == COMPANION_PLAY;
    }

    
    public static boolean isEntityStringPayload(int opCode) {
        return opCode == MAID_MODEL || opCode == MAID_ACTION
                || opCode == COMPANION_SET || opCode == COMPANION_LINES
                || opCode == COMPANION_TALK || opCode == COMPANION_ACTION;
    }

    private NetworkOpCode() {}
}
