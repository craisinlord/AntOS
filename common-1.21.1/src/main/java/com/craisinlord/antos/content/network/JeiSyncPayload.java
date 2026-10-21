package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record JeiSyncPayload(int kind, long version, int part, int parts, byte[] data) implements CustomPacketPayload {
    public static final int DEFINITIONS = 0;
    public static final int STATE = 1;
    public static final int MAX_CHUNK = 900_000;
    public static final Type<JeiSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "jei_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JeiSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JeiSyncPayload::kind,
            ByteBufCodecs.VAR_LONG, JeiSyncPayload::version,
            ByteBufCodecs.VAR_INT, JeiSyncPayload::part,
            ByteBufCodecs.VAR_INT, JeiSyncPayload::parts,
            ByteBufCodecs.byteArray(MAX_CHUNK), JeiSyncPayload::data,
            JeiSyncPayload::new);

    @Override
    public Type<JeiSyncPayload> type() {
        return TYPE;
    }
}
