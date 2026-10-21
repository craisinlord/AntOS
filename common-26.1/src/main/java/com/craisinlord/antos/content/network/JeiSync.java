package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.computer.ComputerAccountLoginEvents;
import com.craisinlord.antos.content.computer.ComputerTaskCompletionEvents;
import com.craisinlord.antos.content.jei.JeiIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.zip.GZIPOutputStream;

public final class JeiSync {
    private static final int DEFINITION_CHECK_INTERVAL = 20;
    private static final int STATE_INTERVAL = 100;
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, PlayerSync> PLAYERS = new HashMap<>();
    private static BiConsumer<ServerPlayer, JeiSyncPayload> sender = (player, payload) -> {};
    private static MinecraftServer trackedServer;
    private static JeiIndex.Key key;
    private static JeiIndex.Definitions definitions;
    private static byte[] definitionBytes;
    private static long nextVersion = 1L;
    private static int ticksUntilCheck;
    private static boolean registered;

    private JeiSync() { }

    public static void setSender(BiConsumer<ServerPlayer, JeiSyncPayload> sender) {
        JeiSync.sender = sender;
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ComputerTaskCompletionEvents.register((player, task) -> markDirty(player));
        ComputerAccountLoginEvents.register((player, account) -> markDirty(player));
    }

    public static void markDirty(ServerPlayer player) {
        if (player != null) DIRTY.add(player.getUUID());
    }

    public static void tick(MinecraftServer server) {
        if (trackedServer != server) {
            trackedServer = server;
            key = null;
            definitions = null;
            definitionBytes = null;
            PLAYERS.clear();
            DIRTY.clear();
            ticksUntilCheck = 0;
        }
        if (--ticksUntilCheck <= 0) {
            ticksUntilCheck = DEFINITION_CHECK_INTERVAL;
            try {
                JeiIndex.Key current = JeiIndex.key();
                if (!current.equals(key) || definitions == null) {
                    key = current;
                    definitions = JeiIndex.definitions(current, nextVersion++);
                    definitionBytes = compress(definitions.json());
                }
            } catch (RuntimeException exception) {
                AntOS.LOGGER.error("Could not build the AntOS JEI index", exception);
                return;
            }
        }
        if (definitions == null) return;
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            online.add(id);
            PlayerSync sync = PLAYERS.computeIfAbsent(id, ignored -> new PlayerSync());
            boolean freshDefinitions = sync.definitions != definitions.version();
            if (freshDefinitions) {
                send(player, JeiSyncPayload.DEFINITIONS, definitions.version(), definitionBytes);
                sync.definitions = definitions.version();
                sync.state = null;
            }
            boolean due = freshDefinitions | DIRTY.remove(id) | --sync.countdown <= 0;
            if (!due) continue;
            sync.countdown = STATE_INTERVAL;
            String state;
            try {
                state = JeiIndex.state(player, definitions);
            } catch (RuntimeException exception) {
                AntOS.LOGGER.error("Could not build the AntOS JEI state for {}", player.getGameProfile().name(), exception);
                continue;
            }
            if (state.equals(sync.state)) continue;
            sync.state = state;
            send(player, JeiSyncPayload.STATE, definitions.version(), compress(state));
        }
        PLAYERS.keySet().retainAll(online);
        DIRTY.retainAll(online);
    }

    private static void send(ServerPlayer player, int kind, long version, byte[] bytes) {
        int parts = Math.max(1, (bytes.length + JeiSyncPayload.MAX_CHUNK - 1) / JeiSyncPayload.MAX_CHUNK);
        for (int part = 0; part < parts; part++) {
            int start = part * JeiSyncPayload.MAX_CHUNK;
            int end = Math.min(bytes.length, start + JeiSyncPayload.MAX_CHUNK);
            sender.accept(player, new JeiSyncPayload(kind, version, part, parts, Arrays.copyOfRange(bytes, start, end)));
        }
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

    private static final class PlayerSync {
        private long definitions;
        private String state;
        private int countdown;
    }
}
