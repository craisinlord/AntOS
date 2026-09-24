package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AntmailRetryPayload(String messageId) implements CustomPacketPayload {
    public static final Type<AntmailRetryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "antmail_retry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AntmailRetryPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(64), AntmailRetryPayload::messageId, AntmailRetryPayload::new);
    @Override public Type<AntmailRetryPayload> type() { return TYPE; }
}


