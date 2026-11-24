package com.craisinlord.antos.content.computer.games;

import com.craisinlord.antos.content.antmail.AntmailEventData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

public final class ComputerGameRecords extends SavedData {
    public static final List<String> METRICS = List.of("wins", "time", "moves", "score");
    public static final List<String> BUILT_IN = List.of("antos:minesweeper_small", "antos:minesweeper_large",
            "antos:chess_easy", "antos:chess_normal", "antos:chess_hard", "antos:chess_expert", "antos:blockle",
            "antos:ore_2048", "antos:piston_push", "antos:flappy_ghast", "antos:cake_stack", "antos:fliptris",
            "antos:nether_breakout");
    private static final String ID = "antos_game_records";
    private static final int MAX_MODES = 256;
    private static final SavedData.Factory<ComputerGameRecords> FACTORY = new SavedData.Factory<>(ComputerGameRecords::new, ComputerGameRecords::load, null);
    private static final Set<String> KNOWN = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> UNKNOWN = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Map<String, Map<String, Integer>>> profiles = new HashMap<>();

    private static ComputerGameRecords access(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public static boolean register(ResourceLocation mode) {
        return mode != null && !mode.getNamespace().equals("antos") && KNOWN.add(mode.toString());
    }

    public static List<String> modes() {
        List<String> result = new ArrayList<>(BUILT_IN);
        KNOWN.stream().sorted().forEach(result::add);
        return List.copyOf(result);
    }

    public static boolean known(String mode) {
        return BUILT_IN.contains(mode) || KNOWN.contains(mode) || mode.startsWith("antos:piston_push/") && ResourceLocation.tryParse(mode) != null;
    }

    public static boolean lowerIsBetter(String metric) { return metric.equals("time") || metric.equals("moves"); }

    public static int value(MinecraftServer server, UUID profile, String mode, String metric) {
        Map<String, Map<String, Integer>> modes = profile == null ? null : access(server).profiles.get(profile);
        Map<String, Integer> values = modes == null ? null : modes.get(mode);
        return values == null ? -1 : values.getOrDefault(metric, -1);
    }

    public static boolean meets(MinecraftServer server, UUID profile, String mode, String metric, int goal) {
        return meets(value(server, profile, mode, metric), metric, goal);
    }

    public static boolean meets(int value, String metric, int goal) {
        return value >= 0 && (lowerIsBetter(metric) ? value <= goal : value >= goal);
    }

    public static int progress(MinecraftServer server, UUID profile, String mode, String metric, int goal) {
        int value = value(server, profile, mode, metric);
        if (value < 0) return 0;
        if (lowerIsBetter(metric)) return value <= goal ? goal : 0;
        return Math.min(value, goal);
    }

    public static boolean record(ServerPlayer player, UUID profile, String report) { return record(player, profile, report, true); }

    public static boolean record(ServerPlayer player, UUID profile, String report, boolean full) {
        Report parsed = Report.parse(report);
        if (profile == null || parsed == null) return false;
        if (!known(parsed.mode())) {
            if (UNKNOWN.size() < 32 && UNKNOWN.add(parsed.mode()))
                com.craisinlord.antos.AntOS.LOGGER.warn("Ignored a game result for {}. Its mod must register it with ComputerGameRecordsRegistry on the server too.", parsed.mode());
            return false;
        }
        ComputerGameRecords data = access(player.server);
        Map<String, Map<String, Integer>> modes = data.profiles.computeIfAbsent(profile, ignored -> new LinkedHashMap<>());
        if (!modes.containsKey(parsed.mode()) && modes.size() >= MAX_MODES) return false;
        Map<String, Integer> values = modes.computeIfAbsent(parsed.mode(), ignored -> new LinkedHashMap<>());
        Set<String> improved = new java.util.HashSet<>();
        for (Map.Entry<String, Integer> entry : parsed.values().entrySet()) {
            String metric = entry.getKey();
            if (!full && metric.equals("wins")) continue;
            Integer previous = values.get(metric);
            int next = metric.equals("wins") ? Math.min(1_000_000, (previous == null ? 0 : previous) + entry.getValue())
                    : previous == null ? entry.getValue() : lowerIsBetter(metric) ? Math.min(previous, entry.getValue()) : Math.max(previous, entry.getValue());
            if (previous == null || previous != next) {
                values.put(metric, next);
                improved.add(metric);
            }
        }
        if (!improved.isEmpty()) data.setDirty();
        if (!full) return false;
        AntmailEventData.onGameRecord(player, profile, parsed.mode(), improved);
        return !improved.isEmpty();
    }

    public record Report(String mode, Map<String, Integer> values) {
        public String encode() {
            StringBuilder result = new StringBuilder(mode);
            values.forEach((metric, value) -> result.append('|').append(metric).append('=').append(value));
            return result.toString();
        }

        public static Report parse(String text) {
            if (text == null || text.length() > 256) return null;
            String[] parts = text.split("[|]");
            if (parts.length < 2 || parts.length > 5 || ResourceLocation.tryParse(parts[0]) == null || !parts[0].contains(":")) return null;
            Map<String, Integer> values = new LinkedHashMap<>();
            for (int index = 1; index < parts.length; index++) {
                String[] pair = parts[index].split("=", 2);
                if (pair.length != 2 || !METRICS.contains(pair[0])) return null;
                try {
                    int value = Integer.parseInt(pair[1]);
                    if (value < 0 || value > 100_000_000 || pair[0].equals("wins") && value > 1) return null;
                    values.put(pair[0], value);
                } catch (NumberFormatException exception) { return null; }
            }
            return new Report(parts[0], Map.copyOf(values));
        }
    }

    private static ComputerGameRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        ComputerGameRecords data = new ComputerGameRecords();
        ListTag rows = tag.getList("Records", 10);
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            try {
                Map<String, Integer> values = data.profiles.computeIfAbsent(UUID.fromString(row.getString("Profile")), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(row.getString("Mode"), ignored -> new LinkedHashMap<>());
                for (String metric : METRICS) if (row.contains(metric)) values.put(metric, row.getInt(metric));
            } catch (IllegalArgumentException ignored) { }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        profiles.forEach((profile, modes) -> modes.forEach((mode, values) -> {
            CompoundTag row = new CompoundTag();
            row.putString("Profile", profile.toString());
            row.putString("Mode", mode);
            values.forEach(row::putInt);
            rows.add(row);
        }));
        tag.put("Records", rows);
        return tag;
    }
}
