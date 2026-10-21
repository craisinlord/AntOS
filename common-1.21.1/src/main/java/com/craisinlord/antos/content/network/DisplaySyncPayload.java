package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DisplaySyncPayload(long version, int part, int parts, byte[] data) implements CustomPacketPayload {
    public static final int MAX_CHUNK = 900_000;
    public static final int MAX_PARTS = 64;
    public static final Type<DisplaySyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "display_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DisplaySyncPayload::version,
            ByteBufCodecs.VAR_INT, DisplaySyncPayload::part,
            ByteBufCodecs.VAR_INT, DisplaySyncPayload::parts,
            ByteBufCodecs.byteArray(MAX_CHUNK), DisplaySyncPayload::data,
            DisplaySyncPayload::new);

    @Override
    public Type<DisplaySyncPayload> type() {
        return TYPE;
    }
}
