package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AntmailAnternetResultPayload(int status, String address, String messageId, String detail, String data, long version) implements CustomPacketPayload {
    public static final Type<AntmailAnternetResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "antmail_anternet_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AntmailAnternetResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AntmailAnternetResultPayload::status,
            ByteBufCodecs.stringUtf8(64), AntmailAnternetResultPayload::address,
            ByteBufCodecs.stringUtf8(64), AntmailAnternetResultPayload::messageId,
            ByteBufCodecs.stringUtf8(128), AntmailAnternetResultPayload::detail,
            ByteBufCodecs.stringUtf8(65536), AntmailAnternetResultPayload::data,
            ByteBufCodecs.VAR_LONG, AntmailAnternetResultPayload::version,
            AntmailAnternetResultPayload::new);
    @Override public Type<AntmailAnternetResultPayload> type() { return TYPE; }
}
