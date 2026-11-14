package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.fabric.network.MmdSkinPayloads;
import com.shiroha.mmdskin.fabric.stage.FabricStageSessionRegistry;
import com.shiroha.mmdskin.player.sync.ServerModelRegistry;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Fabric 服务端网络注册
 */
public class MmdSkinRegisterCommon {
    private static final Logger logger = LogManager.getLogger();

    public static Identifier SKIN_C2S = Identifier.fromNamespaceAndPath("3d-skin", "network_c2s");
    public static Identifier SKIN_S2C = Identifier.fromNamespaceAndPath("3d-skin", "network_s2c");

    public static void Register() {
        // 26.3 要求先注册载荷类型；客户端与专用服务端都会执行 main 入口，所以两个方向都在这里注册。
        PayloadTypeRegistry.serverboundPlay().register(MmdSkinPayloads.C2S.TYPE, MmdSkinPayloads.C2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MmdSkinPayloads.S2C.TYPE, MmdSkinPayloads.S2C.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MmdSkinPayloads.C2S.TYPE, (payload, context) -> {
            try {
                handleClientPacket(context.server(), context.player(), payload.data());
            } catch (RuntimeException e) {
                // 客户端可能发来被截断或格式错误的数据，丢弃即可，不能让异常传播到网络线程。
                logger.warn("丢弃格式错误的数据包: player={}", context.player().getUUID(), e);
            }
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    ServerModelRegistry.onPlayerLeave(handler.getPlayer().getUUID());
                    FabricStageSessionRegistry.getInstance().onPlayerDisconnect(server, handler.getPlayer());
                });
    }

    private static void handleClientPacket(MinecraftServer server, ServerPlayer player, byte[] data) {
        FriendlyByteBuf buf = MmdSkinPayloads.wrap(data);
        int opCode = buf.readInt();
        UUID claimedUUID = buf.readUUID();

        UUID realUUID = player.getUUID();
        if (!realUUID.equals(claimedUUID)) {
            logger.warn("UUID 不匹配，丢弃数据包: claimed={}, real={}", claimedUUID, realUUID);
            return;
        }

        String strData = null;
        byte[] binaryData = null;
        int entityId = 0;
        int intArg = 0;

        if (opCode == NetworkOpCode.BONE_SYNC) {
            binaryData = buf.readByteArray();
        } else if (NetworkOpCode.isStringPayload(opCode)) {
            strData = buf.readUtf();
        } else if (NetworkOpCode.isEntityStringPayload(opCode)) {
            entityId = buf.readInt();
            strData = buf.readUtf();
        } else {
            intArg = buf.readInt();
        }

        if (com.shiroha.mmdskin.fabric.companion.CompanionServer.handles(opCode)) {
            com.shiroha.mmdskin.fabric.companion.CompanionServer.handle(server, player, opCode, entityId, strData);
            return;
        }

        if (opCode == NetworkOpCode.MODEL_SELECT && strData != null) {
            ServerModelRegistry.updateModel(realUUID, strData);
        }

        if (opCode == NetworkOpCode.REQUEST_ALL_MODELS) {
            server.execute(() -> {
                ServerModelRegistry.sendAllTo((modelOwnerUUID, modelName) -> {
                    FriendlyByteBuf replyBuf = MmdSkinPayloads.newBuffer();
                    replyBuf.writeInt(NetworkOpCode.MODEL_SELECT);
                    replyBuf.writeUUID(modelOwnerUUID);
                    replyBuf.writeUtf(modelName);
                    sendToClient(player, replyBuf);
                });
            });
            return;
        }

        if (opCode == NetworkOpCode.STAGE_MULTI && strData != null) {
            final String stagePayload = strData;
            server.execute(() -> FabricStageSessionRegistry.getInstance().handlePacket(server, player, stagePayload));
            return;
        }

        final FriendlyByteBuf packetBuf = MmdSkinPayloads.newBuffer();
        packetBuf.writeInt(opCode);
        packetBuf.writeUUID(realUUID);

        if (opCode == NetworkOpCode.BONE_SYNC) {
            packetBuf.writeByteArray(binaryData != null ? binaryData : new byte[0]);
        } else if (NetworkOpCode.isStringPayload(opCode)) {
            packetBuf.writeUtf(strData);
        } else if (NetworkOpCode.isEntityStringPayload(opCode)) {
            packetBuf.writeInt(entityId);
            packetBuf.writeUtf(strData);
        } else {
            packetBuf.writeInt(intArg);
        }

        final byte[] relayed = MmdSkinPayloads.toBytes(packetBuf);
        server.execute(() -> {
            for (ServerPlayer serverPlayer : PlayerLookup.all(server)) {
                if (!serverPlayer.equals(player)) {
                    sendToClient(serverPlayer, relayed);
                }
            }
        });
    }

    /** 向指定玩家发送一个旧协议缓冲区；对方没有安装本模组时直接跳过。 */
    public static void sendToClient(ServerPlayer target, FriendlyByteBuf buf) {
        sendToClient(target, MmdSkinPayloads.toBytes(buf));
    }

    private static void sendToClient(ServerPlayer target, byte[] data) {
        if (!ServerPlayNetworking.canSend(target, MmdSkinPayloads.S2C.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(target, new MmdSkinPayloads.S2C(data));
    }
}
