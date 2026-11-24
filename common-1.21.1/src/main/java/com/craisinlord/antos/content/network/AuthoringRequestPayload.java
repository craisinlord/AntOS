package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.editor.AuthoringWire;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AuthoringRequestPayload(String id, int index, int total, String data) implements CustomPacketPayload {
    public static final Type<AuthoringRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("antos", "authoring_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuthoringRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), AuthoringRequestPayload::id,
            ByteBufCodecs.VAR_INT, AuthoringRequestPayload::index,
            ByteBufCodecs.VAR_INT, AuthoringRequestPayload::total,
            ByteBufCodecs.stringUtf8(AuthoringWire.CHUNK_CHARS), AuthoringRequestPayload::data, AuthoringRequestPayload::new);
    public AuthoringWire.Chunk chunk() { return new AuthoringWire.Chunk(id, index, total, data); }
    @Override public Type<AuthoringRequestPayload> type() { return TYPE; }
}
