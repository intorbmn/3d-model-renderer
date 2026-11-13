package com.shiroha.mmdskin.fabric.network;

import com.shiroha.mmdskin.fabric.register.MmdSkinRegisterCommon;
import io.netty.buffer.Unpooled;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 文件职责：定义 26.3 的网络载荷。
 *
 * <p>26.3 起 Fabric 网络必须通过 {@link CustomPacketPayload} 与 PayloadTypeRegistry 注册，
 * 不能再用 Identifier + FriendlyByteBuf 直接收发。为保持原有协议（opCode + UUID + 参数）不变，
 * 这里把旧协议的整段字节原样放进载荷里，收发两端仍用 FriendlyByteBuf 读写。
 */
public final class MmdSkinPayloads {
    private MmdSkinPayloads() {
    }

    /** 创建一个用于编码旧协议内容的空缓冲区。 */
    public static FriendlyByteBuf newBuffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    /** 把收到的载荷字节包装成可读缓冲区。 */
    public static FriendlyByteBuf wrap(byte[] data) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
    }

    /** 复制缓冲区中尚未读取的字节，不改变读指针。 */
    public static byte[] toBytes(FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        return bytes;
    }

    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> rawCodec(
            Function<byte[], T> factory, Function<T, byte[]> bytesOf) {
        return new StreamCodec<>() {
            @Override
            public T decode(RegistryFriendlyByteBuf buf) {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return factory.apply(bytes);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, T value) {
                buf.writeBytes(bytesOf.apply(value));
            }
        };
    }

    /** 客户端 → 服务端。 */
    public record C2S(byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<C2S> TYPE =
                new CustomPacketPayload.Type<>(MmdSkinRegisterCommon.SKIN_C2S);
        public static final StreamCodec<RegistryFriendlyByteBuf, C2S> CODEC = rawCodec(C2S::new, C2S::data);

        @Override
        public CustomPacketPayload.Type<C2S> type() {
            return TYPE;
        }
    }

    /** 服务端 → 客户端。 */
    public record S2C(byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<S2C> TYPE =
                new CustomPacketPayload.Type<>(MmdSkinRegisterCommon.SKIN_S2C);
        public static final StreamCodec<RegistryFriendlyByteBuf, S2C> CODEC = rawCodec(S2C::new, S2C::data);

        @Override
        public CustomPacketPayload.Type<S2C> type() {
            return TYPE;
        }
    }
}
