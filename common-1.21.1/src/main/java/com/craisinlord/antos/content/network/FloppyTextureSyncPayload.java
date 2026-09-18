package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FloppyTextureSyncPayload(String mappings) implements CustomPacketPayload {
    public static final Type<FloppyTextureSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "floppy_texture_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FloppyTextureSyncPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(1_048_576), FloppyTextureSyncPayload::mappings,
                    FloppyTextureSyncPayload::new);

    @Override
    public Type<FloppyTextureSyncPayload> type() {
        return TYPE;
    }
}
