package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AntmailDraftPayload(int action, String data) implements CustomPacketPayload {
    public static final int SAVE = 0;
    public static final int DELETE = 1;
    public static final Type<AntmailDraftPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "antmail_draft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AntmailDraftPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, AntmailDraftPayload::action, ByteBufCodecs.stringUtf8(65536), AntmailDraftPayload::data, AntmailDraftPayload::new);
    @Override public Type<AntmailDraftPayload> type() { return TYPE; }
}


