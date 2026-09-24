package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AntmailDeletePayload(String messageId, boolean sent) implements CustomPacketPayload {
    public static final Type<AntmailDeletePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "antmail_delete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AntmailDeletePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(64), AntmailDeletePayload::messageId, ByteBufCodecs.BOOL, AntmailDeletePayload::sent, AntmailDeletePayload::new);
    @Override public Type<AntmailDeletePayload> type() { return TYPE; }
}


