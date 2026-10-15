package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerLocationObjectives {
    public static final Set<String> TYPES = Set.of("dimension_entered", "biome_visited", "structure_entered");
    private static final int CHECK_INTERVAL = 20;
    private static final int UNAVAILABLE_RETRY = 200;
    private static final int FULL_RECHECK = 20;
    private static final Map<UUID, Set<String>> DONE = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Integer>> RETRY_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, LastCheck> LAST_CHECKS = new ConcurrentHashMap<>();
    private static volatile int signature;
    private static volatile java.lang.ref.WeakReference<MinecraftServer> lastServer = new java.lang.ref.WeakReference<>(null);

    private ComputerLocationObjectives() { }

    private enum Kind { DIMENSION, BIOME, STRUCTURE }

    private static final class Candidate {
        private final Identifier taskId;
        private final ComputerGuideData.Objective objective;
        private final String key;
        private final Kind kind;
        private final Identifier id;
        private final TagKey<Biome> biomeTag;
        private final TagKey<Structure> structureTag;
        private final ResourceKey<Structure> structureKey;
        private Registry<Structure> structureRegistry;
        private HolderSet<Structure> structureHolders;

        private Candidate(Identifier taskId, ComputerGuideData.Objective objective) {
            this.taskId = taskId;
            this.objective = objective;
            this.key = taskId + "|" + objective.id();
            String target = objective.target();
            boolean tag = target.startsWith("#");
            this.id = Identifier.tryParse(tag ? target.substring(1) : target);
            this.kind = switch (objective.type()) {
                case "dimension_entered" -> Kind.DIMENSION;
                case "biome_visited" -> Kind.BIOME;
                default -> Kind.STRUCTURE;
            };
            this.biomeTag = id != null && tag && kind == Kind.BIOME ? TagKey.create(Registries.BIOME, id) : null;
            this.structureTag = id != null && tag && kind == Kind.STRUCTURE ? TagKey.create(Registries.STRUCTURE, id) : null;
            this.structureKey = id != null && !tag && kind == Kind.STRUCTURE ? ResourceKey.create(Registries.STRUCTURE, id) : null;
        }

        String key() { return key; }

        Identifier taskId() { return taskId; }

        ComputerGuideData.Objective objective() { return objective; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Candidate candidate && taskId.equals(candidate.taskId) && objective.equals(candidate.objective);
        }

        @Override
        public int hashCode() {
            return 31 * taskId.hashCode() + objective.hashCode();
        }

        synchronized HolderSet<Structure> structureHolders(ServerLevel level) {
            Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            if (registry != structureRegistry) {
                Holder<Structure> holder = registry.get(structureKey).orElse(null);
                structureHolders = holder == null ? null : HolderSet.direct(holder);
                structureRegistry = registry;
            }
            return structureHolders;
        }
    }

    private record LastCheck(UUID account, ResourceKey<net.minecraft.world.level.Level> dimension, long position, int tick,
                             List<Candidate> candidates) { }

    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % CHECK_INTERVAL != 0) return;
        if (lastServer.get() != server) {
            LAST_CHECKS.clear();
            lastServer = new java.lang.ref.WeakReference<>(server);
        }
        List<Candidate> candidates = candidates();
        if (candidates.isEmpty()) return;
        AntmailServerData accounts = AntmailServerData.access(server);
        Set<UUID> online = new java.util.HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            var account = accounts.deliveryAccount(player);
            if (account == null) continue;
            UUID key = account.accountId();
            ResourceKey<net.minecraft.world.level.Level> dimension = player.level().dimension();
            long position = player.blockPosition().asLong();
            LastCheck last = LAST_CHECKS.get(player.getUUID());
            if (last != null && last.account().equals(key) && last.dimension() == dimension && last.position() == position
                    && last.candidates() == candidates && tick - last.tick() < FULL_RECHECK) continue;
            LAST_CHECKS.put(player.getUUID(), new LastCheck(key, dimension, position, tick, candidates));
            Set<String> done = DONE.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
            Map<String, Integer> retry = RETRY_AT.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
            List<Candidate> matched = new ArrayList<>();
            Probe probe = new Probe(player);
            for (Candidate candidate : candidates) {
                if (done.contains(candidate.key()) || retry.getOrDefault(candidate.key(), 0) > tick) continue;
                if (matches(probe, candidate)) matched.add(candidate);
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
        LAST_CHECKS.keySet().retainAll(online);
    }

    private static volatile List<ComputerGuideData.Task> candidatesSource;
    private static volatile List<Candidate> cachedCandidates = List.of();

    static void serverStopped() {
        LAST_CHECKS.clear();
        candidatesSource = null;
        cachedCandidates = List.of();
    }

    private static List<Candidate> candidates() {
        List<ComputerGuideData.Task> tasks = ComputerGuideData.tasks();
        if (tasks == candidatesSource) return cachedCandidates;
        List<Candidate> candidates = new ArrayList<>();
        int current = 1;
        for (ComputerGuideData.Task task : tasks) {
            for (ComputerGuideData.Objective objective : task.objectives()) {
                if (!TYPES.contains(objective.type())) continue;
                Candidate candidate = new Candidate(task.id(), objective);
                current = 31 * current + candidate.hashCode();
                candidates.add(candidate);
            }
        }
        if (current != signature) {
            signature = current;
            DONE.clear();
            RETRY_AT.clear();
        }
        cachedCandidates = List.copyOf(candidates);
        candidatesSource = tasks;
        return cachedCandidates;
    }

    private static final class Probe {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final BlockPos position;
        private Holder<Biome> biome;
        private Boolean hasStructures;

        private Probe(ServerPlayer player) {
            this.player = player;
            this.level = player.level();
            this.position = player.blockPosition();
        }

        Holder<Biome> biome() {
            if (biome == null) biome = level.getBiome(position);
            return biome;
        }

        boolean hasStructures() {
            if (hasStructures == null) hasStructures = !level.getChunk(position).getAllReferences().isEmpty();
            return hasStructures;
        }
    }

    private static boolean matches(Probe probe, Candidate candidate) {
        if (candidate.id == null) return false;
        switch (candidate.kind) {
            case DIMENSION -> {
                return probe.level.dimension().identifier().equals(candidate.id);
            }
            case BIOME -> {
                return candidate.biomeTag != null ? probe.biome().is(candidate.biomeTag) : probe.biome().is(candidate.id);
            }
            case STRUCTURE -> {
                if (!probe.hasStructures()) return false;
                var structures = probe.level.structureManager();
                if (candidate.structureTag != null) return structures.getStructureWithPieceAt(probe.position, candidate.structureTag).isValid();
                HolderSet<Structure> holders = candidate.structureHolders(probe.level);
                return holders != null && structures.getStructureWithPieceAt(probe.position, holders).isValid();
            }
            default -> {
                return false;
            }
        }
    }
}
