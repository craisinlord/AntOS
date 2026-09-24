package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AntmailReadPayload(String messageId, boolean read) implements CustomPacketPayload {
    public static final Type<AntmailReadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "antmail_read"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AntmailReadPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(64), AntmailReadPayload::messageId, ByteBufCodecs.BOOL, AntmailReadPayload::read, AntmailReadPayload::new);
    @Override public Type<AntmailReadPayload> type() { return TYPE; }
}


