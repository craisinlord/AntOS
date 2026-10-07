package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native location task objectives: {@code dimension_entered}, {@code biome_visited} and {@code structure_entered}.
 * Checked once a second per player; each completes while the player is standing in the place and the task is
 * available, including when the task becomes available after they arrived.
 */
public final class ComputerLocationObjectives {
    public static final Set<String> TYPES = Set.of("dimension_entered", "biome_visited", "structure_entered");
    private static final int CHECK_INTERVAL = 20;
    private static final int UNAVAILABLE_RETRY = 200;
    private static final Map<UUID, Set<String>> DONE = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Integer>> RETRY_AT = new ConcurrentHashMap<>();
    private static volatile int signature;

    private ComputerLocationObjectives() { }

    private record Candidate(ResourceLocation taskId, ComputerGuideData.Objective objective) {
        String key() { return taskId + "|" + objective.id(); }
    }

    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % CHECK_INTERVAL != 0) return;
        List<Candidate> candidates = candidates();
        if (candidates.isEmpty()) return;
        AntmailServerData accounts = AntmailServerData.access(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var account = accounts.deliveryAccount(player);
            if (account == null) continue;
            UUID key = account.accountId();
            Set<String> done = DONE.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
            Map<String, Integer> retry = RETRY_AT.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
            List<Candidate> matched = new ArrayList<>();
            for (Candidate candidate : candidates) {
                if (done.contains(candidate.key()) || retry.getOrDefault(candidate.key(), 0) > tick) continue;
                if (matches(player, candidate.objective())) matched.add(candidate);
            }
            if (matched.isEmpty()) continue;
            AccountWorkspace workspace = new AccountWorkspace(player, account);
            for (Candidate candidate : matched) {
                switch (ComputerTasks.completeLocationObjective(player, workspace, candidate.taskId(), candidate.objective().id())) {
                    case DONE -> done.add(candidate.key());
                    case UNAVAILABLE -> retry.put(candidate.key(), tick + UNAVAILABLE_RETRY);
                }
            }
        }
    }

    private static volatile List<ComputerGuideData.Task> candidatesSource;
    private static volatile List<Candidate> cachedCandidates = List.of();

    /** Location objectives only change on a data reload, which swaps the cached task list. */
    private static List<Candidate> candidates() {
        List<ComputerGuideData.Task> tasks = ComputerGuideData.tasks();
        if (tasks == candidatesSource) return cachedCandidates;
        List<Candidate> candidates = new ArrayList<>();
        for (ComputerGuideData.Task task : tasks) {
            for (ComputerGuideData.Objective objective : task.objectives()) {
                if (TYPES.contains(objective.type())) candidates.add(new Candidate(task.id(), objective));
            }
        }
        int current = candidates.hashCode();
        if (current != signature) {
            signature = current;
            DONE.clear();
            RETRY_AT.clear();
        }
        cachedCandidates = List.copyOf(candidates);
        candidatesSource = tasks;
        return cachedCandidates;
    }

    private static boolean matches(ServerPlayer player, ComputerGuideData.Objective objective) {
        ServerLevel level = player.serverLevel();
        String target = objective.target();
        boolean tag = target.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? target.substring(1) : target);
        if (id == null) return false;
        switch (objective.type()) {
            case "dimension_entered" -> {
                return level.dimension().location().equals(id);
            }
            case "biome_visited" -> {
                var biome = level.getBiome(player.blockPosition());
                return tag ? biome.is(TagKey.create(Registries.BIOME, id)) : biome.is(id);
            }
            case "structure_entered" -> {
                var structures = level.structureManager();
                if (tag) return structures.getStructureWithPieceAt(player.blockPosition(), TagKey.create(Registries.STRUCTURE, id)).isValid();
                var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                        .getHolder(ResourceKey.create(Registries.STRUCTURE, id)).orElse(null);
                return holder != null && structures.getStructureWithPieceAt(player.blockPosition(), HolderSet.direct(holder)).isValid();
            }
            default -> {
                return false;
            }
        }
    }
}
