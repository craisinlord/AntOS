package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ArchiveRecipeRequestPayload(String recipeId) implements CustomPacketPayload {
    public static final int MAX_ID_LENGTH = 256;
    public static final Type<ArchiveRecipeRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AntOS.MODID, "archive_recipe_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArchiveRecipeRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), ArchiveRecipeRequestPayload::recipeId,
            ArchiveRecipeRequestPayload::new
    );

    @Override
    public Type<ArchiveRecipeRequestPayload> type() {
        return TYPE;
    }
}
