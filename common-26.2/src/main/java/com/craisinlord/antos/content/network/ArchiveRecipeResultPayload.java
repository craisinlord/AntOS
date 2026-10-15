package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.computer.ArchiveRecipeData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record ArchiveRecipeResultPayload(String recipeId, Optional<ArchiveRecipeData> recipe) implements CustomPacketPayload {
    public static final Type<ArchiveRecipeResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AntOS.MODID, "archive_recipe_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArchiveRecipeResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(ArchiveRecipeRequestPayload.MAX_ID_LENGTH), ArchiveRecipeResultPayload::recipeId,
            ByteBufCodecs.optional(ArchiveRecipeData.STREAM_CODEC), ArchiveRecipeResultPayload::recipe,
            ArchiveRecipeResultPayload::new
    );

    @Override
    public Type<ArchiveRecipeResultPayload> type() {
        return TYPE;
    }
}
