package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.editor.AuthoringWire;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AuthoringResultPayload(String id, int index, int total, String data) implements CustomPacketPayload {
    public static final Type<AuthoringResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("antos", "authoring_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuthoringResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), AuthoringResultPayload::id,
            ByteBufCodecs.VAR_INT, AuthoringResultPayload::index,
            ByteBufCodecs.VAR_INT, AuthoringResultPayload::total,
            ByteBufCodecs.stringUtf8(AuthoringWire.CHUNK_CHARS), AuthoringResultPayload::data, AuthoringResultPayload::new);
    public AuthoringWire.Chunk chunk() { return new AuthoringWire.Chunk(id, index, total, data); }
    @Override public Type<AuthoringResultPayload> type() { return TYPE; }
}
