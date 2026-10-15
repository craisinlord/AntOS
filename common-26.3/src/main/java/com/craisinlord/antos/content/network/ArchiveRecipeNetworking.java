package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.computer.ArchiveRecipeData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ArchiveRecipeNetworking {
    private static final ComputerRequestLimiter REQUEST_LIMITER = new ComputerRequestLimiter(128.0D, 32.0D);
    private static Consumer<ArchiveRecipeRequestPayload> sender = payload -> { };
    private static BiConsumer<ServerPlayer, ArchiveRecipeResultPayload> resultSender = (player, payload) -> { };

    private ArchiveRecipeNetworking() { }

    public static void setSender(Consumer<ArchiveRecipeRequestPayload> sender) {
        ArchiveRecipeNetworking.sender = sender == null ? payload -> { } : sender;
    }

    public static void setResultSender(BiConsumer<ServerPlayer, ArchiveRecipeResultPayload> resultSender) {
        ArchiveRecipeNetworking.resultSender = resultSender == null ? (player, payload) -> { } : resultSender;
    }

    public static boolean request(String recipeId) {
        if (recipeId == null || recipeId.isBlank() || recipeId.length() > ArchiveRecipeRequestPayload.MAX_ID_LENGTH) return false;
        sender.accept(new ArchiveRecipeRequestPayload(recipeId));
        return true;
    }

    public static void handle(ServerPlayer player, ArchiveRecipeRequestPayload payload) {
        if (!REQUEST_LIMITER.tryAcquire(player.getUUID(), 1)) return;
        ArchiveRecipeData recipe;
        try {
            recipe = ArchiveRecipeData.resolve(player.level().getServer().getRecipeManager(), payload.recipeId(),
                    SlotDisplayContext.fromLevel(player.level()));
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Archive recipe lookup failed id={} error={}", payload.recipeId(), exception.toString());
            recipe = null;
        }
        resultSender.accept(player, new ArchiveRecipeResultPayload(payload.recipeId(), Optional.ofNullable(recipe)));
    }

    public static void playerDisconnected(ServerPlayer player) {
        REQUEST_LIMITER.forget(player.getUUID());
    }

    public static void retainOnline(Collection<UUID> online) {
        REQUEST_LIMITER.retain(online);
    }
}
