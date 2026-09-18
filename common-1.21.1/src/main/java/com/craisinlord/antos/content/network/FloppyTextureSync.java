package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Sends data-pack disk texture mappings to clients, including clients joining after a reload. */
public final class FloppyTextureSync {
    private static BiConsumer<ServerPlayer, FloppyTextureSyncPayload> sender = (player, payload) -> {};
    private static MinecraftServer trackedServer;
    private static String lastMappings;
    private static long lastRevision = Long.MIN_VALUE;
    private static final Set<UUID> sentPlayers = new HashSet<>();

    private FloppyTextureSync() {}

    public static void setSender(BiConsumer<ServerPlayer, FloppyTextureSyncPayload> sender) {
        FloppyTextureSync.sender = sender;
    }

    public static void tick(MinecraftServer server) {
        if (trackedServer != server) {
            trackedServer = server;
            lastMappings = null;
            lastRevision = Long.MIN_VALUE;
            sentPlayers.clear();
        }
        long revision = ComputerGuideData.diskTextureRevision();
        boolean changed = revision != lastRevision;
        if (changed) {
            JsonObject json = new JsonObject();
            ComputerGuideData.diskCategoryMappings().forEach((disk, category) -> json.addProperty(disk.toString(), category.toString()));
            lastMappings = json.toString();
            lastRevision = revision;
            sentPlayers.clear();
        }
        String mappings = lastMappings == null ? "{}" : lastMappings;
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            online.add(id);
            if (changed || sentPlayers.add(id)) {
                sender.accept(player, new FloppyTextureSyncPayload(mappings));
            }
        }
        sentPlayers.retainAll(online);
    }
}
