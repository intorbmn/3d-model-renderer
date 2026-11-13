package com.shiroha.mmdskin.fabric.network;

import java.util.UUID;

import com.shiroha.mmdskin.player.sync.ClientNetworkBindings;
import com.shiroha.mmdskin.compat.maid.runtime.MaidMMDModelManager;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.sync.PlayerModelSyncService;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.player.sync.MorphSyncHelper;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Fabric 网络包发送与客户端处理
 */
public class MmdSkinNetworkPack {
    private MmdSkinNetworkPack() {
    }

    public static int toOpCode(ClientNetworkBindings.NetworkMessageType messageType) {
        return switch (messageType) {
            case CUSTOM_ANIM -> NetworkOpCode.CUSTOM_ANIM;
            case RESET_PHYSICS -> NetworkOpCode.RESET_PHYSICS;
            case MODEL_SELECT -> NetworkOpCode.MODEL_SELECT;
            case MORPH_SYNC -> NetworkOpCode.MORPH_SYNC;
            case STAGE_MULTI -> NetworkOpCode.STAGE_MULTI;
            case BONE_SYNC -> NetworkOpCode.BONE_SYNC;
        };
    }

    /** 发送旧协议缓冲区；未连接服务器，或服务器没有安装本模组时直接跳过。 */
    private static void send(FriendlyByteBuf buffer) {
        if (!ClientPlayNetworking.canSend(MmdSkinPayloads.C2S.TYPE)) {
            return;
        }
        ClientPlayNetworking.send(new MmdSkinPayloads.C2S(MmdSkinPayloads.toBytes(buffer)));
    }

    public static void sendToServer(int opCode, UUID playerUUID, int arg0) {
        FriendlyByteBuf buffer = MmdSkinPayloads.newBuffer();
        buffer.writeInt(opCode);
        buffer.writeUUID(playerUUID);
        buffer.writeInt(arg0);
        send(buffer);
    }

    public static void sendBinaryToServer(int opCode, UUID playerUUID, byte[] data) {
        FriendlyByteBuf buffer = MmdSkinPayloads.newBuffer();
        buffer.writeInt(opCode);
        buffer.writeUUID(playerUUID);
        buffer.writeByteArray(data);
        send(buffer);
    }

    public static void sendToServer(int opCode, UUID playerUUID, String animId) {
        FriendlyByteBuf buffer = MmdSkinPayloads.newBuffer();
        buffer.writeInt(opCode);
        buffer.writeUUID(playerUUID);
        buffer.writeUtf(animId);
        send(buffer);
    }

    public static void sendToServer(int opCode, UUID playerUUID, int entityId, String data) {
        FriendlyByteBuf buffer = MmdSkinPayloads.newBuffer();
        buffer.writeInt(opCode);
        buffer.writeUUID(playerUUID);
        buffer.writeInt(entityId);
        buffer.writeUtf(data);
        send(buffer);
    }

    public static void doInClient(FriendlyByteBuf buffer) {
        int opCode = buffer.readInt();
        UUID playerUUID = buffer.readUUID();

        if (NetworkOpCode.isStringPayload(opCode)) {
            String data = buffer.readUtf();
            handleString(opCode, playerUUID, data);
        } else if (NetworkOpCode.isEntityStringPayload(opCode)) {
            int entityId = buffer.readInt();
            String data = buffer.readUtf();
            handleMaid(opCode, playerUUID, entityId, data);
        } else {
            int arg0 = buffer.readInt();
            handleInt(opCode, playerUUID, arg0);
        }
    }

    private static void handleInt(int opCode, UUID playerUUID, int arg0) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || playerUUID.equals(mc.player.getUUID())) return;
        if (mc.level == null) return;

        if (opCode == NetworkOpCode.RESET_PHYSICS) {
            Player target = mc.level.getPlayerByUUID(playerUUID);
            if (target != null) {
                MmdSkinRendererPlayerHelper.ResetPhysics(target);
            } else {
                PendingAnimSignalCache.put(playerUUID, PendingAnimSignalCache.SignalType.RESET);
            }
        }
    }

    private static void handleString(int opCode, UUID playerUUID, String data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (opCode == NetworkOpCode.STAGE_MULTI) {
            com.shiroha.mmdskin.stage.client.StageClientPacketHandler.getInstance().handle(playerUUID, data);
            return;
        }
        if (playerUUID.equals(mc.player.getUUID())) return;
        if (mc.level == null) return;

        Player target = mc.level.getPlayerByUUID(playerUUID);
        switch (opCode) {
            case NetworkOpCode.CUSTOM_ANIM -> {
                if (target != null) MmdSkinRendererPlayerHelper.CustomAnim(target, data);
            }
            case NetworkOpCode.MODEL_SELECT -> {
                PlayerModelSyncService.onRemotePlayerModelReceived(playerUUID, data);
            }
            case NetworkOpCode.MORPH_SYNC -> {
                if (target != null) MorphSyncHelper.applyRemoteMorph(target, data);
            }
            default -> {}
        }
    }

    private static void handleMaid(int opCode, UUID playerUUID, int entityId, String data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || playerUUID.equals(mc.player.getUUID())) return;
        if (mc.level == null) return;

        Entity maidEntity = mc.level.getEntity(entityId);
        if (maidEntity == null) return;

        switch (opCode) {
            case NetworkOpCode.MAID_MODEL -> MaidMMDModelManager.bindModel(maidEntity.getUUID(), data);
            case NetworkOpCode.MAID_ACTION -> MaidMMDModelManager.playAnimation(maidEntity.getUUID(), data);
            default -> {}
        }
    }
}

