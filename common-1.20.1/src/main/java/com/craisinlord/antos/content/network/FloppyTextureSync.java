package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class FloppyTextureSync {
    private static final int FULL_SCAN_INTERVAL = 20;
    private static BiConsumer<ServerPlayer, FloppyTextureSyncPayload> sender = (player, payload) -> {};
    private static MinecraftServer trackedServer;
    private static String lastMappings;
    private static long lastRevision = Long.MIN_VALUE;
    private static final Set<UUID> sentPlayers = new HashSet<>();
    private static int lastPlayerCount = -1;
    private static int ticksUntilScan;

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
            lastPlayerCount = -1;
            ticksUntilScan = 0;
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
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        boolean scan = --ticksUntilScan <= 0;
        if (!changed && !scan && players.size() == lastPlayerCount) return;
        if (scan) ticksUntilScan = FULL_SCAN_INTERVAL;
        lastPlayerCount = players.size();
        String mappings = lastMappings == null ? "{}" : lastMappings;
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            online.add(id);
            if (changed || sentPlayers.add(id)) {
                if (changed) sentPlayers.add(id);
                sender.accept(player, new FloppyTextureSyncPayload(mappings));
            }
        }
        sentPlayers.retainAll(online);
        if (scan) ComputerAccessHandler.retainOnline(online);
    }
}
