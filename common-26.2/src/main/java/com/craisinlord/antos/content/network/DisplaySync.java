package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.guide.CustomDisplays;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.zip.GZIPOutputStream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class DisplaySync {
    private static BiConsumer<ServerPlayer, DisplaySyncPayload> sender = (player, payload) -> {};
    private static final Map<UUID, Long> SENT = new HashMap<>();
    private static MinecraftServer trackedServer;
    private static long revision = -1;
    private static byte[] bytes;
    private static long warned = -1;

    private DisplaySync() { }

    public static void setSender(BiConsumer<ServerPlayer, DisplaySyncPayload> sender) {
        DisplaySync.sender = sender;
    }

    public static void tick(MinecraftServer server) {
        if (trackedServer != server) {
            trackedServer = server;
            SENT.clear();
            revision = -1;
            bytes = null;
        }
        long current = CustomDisplays.revision();
        if (current != revision || bytes == null) {
            try {
                bytes = compress(CustomDisplays.encode().toString());
                revision = current;
            } catch (RuntimeException exception) {
                AntOS.LOGGER.error("Could not encode AntOS displays", exception);
                return;
            }
        }
        int parts = Math.max(1, (bytes.length + DisplaySyncPayload.MAX_CHUNK - 1) / DisplaySyncPayload.MAX_CHUNK);
        if (parts > DisplaySyncPayload.MAX_PARTS) {
            if (warned != revision) AntOS.LOGGER.error("AntOS displays are {} bytes compressed; that is over the sync limit", bytes.length);
            warned = revision;
            return;
        }
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            online.add(id);
            Long sent = SENT.get(id);
            if (sent != null && sent == revision) continue;
            SENT.put(id, revision);
            for (int part = 0; part < parts; part++) {
                int start = part * DisplaySyncPayload.MAX_CHUNK;
                int end = Math.min(bytes.length, start + DisplaySyncPayload.MAX_CHUNK);
                sender.accept(player, new DisplaySyncPayload(revision, part, parts, Arrays.copyOfRange(bytes, start, end)));
            }
        }
        SENT.keySet().retainAll(online);
    }

    private static byte[] compress(String text) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return output.toByteArray();
    }
}
