package com.craisinlord.antos.content.computer.terminal;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.guide.DiskFeatures;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class TerminalCommandData {
    public static final String DIRECTORY = "computer/terminal_command";
    public static final Set<String> BUILT_IN = Set.of("help", "clear", "pwd", "ls", "cd", "mkdir", "touch", "cat", "write", "rm", "rmdir", "mv", "open", "decrypt");
    public static final Set<String> STYLES = Set.of("output", "highlight", "system", "error");
    private static final Pattern NAME = Pattern.compile("[a-z0-9_.\\-]{1,32}");
    private static final int MAX_DEFINITIONS = 512;
    private static final int MAX_ARGUMENTS = 8;
    private static final int MAX_LINES = 64;
    private static final int MAX_LINE_LENGTH = 512;
    private static volatile Map<ResourceLocation, Definition> definitions = Map.of();
    private static volatile Map<String, List<Definition>> byName = Map.of();

    private TerminalCommandData() { }

    public static Map<ResourceLocation, Definition> load(ResourceManager manager, Map<ResourceLocation, ComputerGuideData.RewardPool> pools) {
        Map<ResourceLocation, Definition> loaded = new LinkedHashMap<>();
        Map<String, ResourceLocation> signatures = new LinkedHashMap<>();
        var resources = manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        for (var resource : resources) {
            String path = resource.getKey().getPath();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(resource.getKey().getNamespace(), path.substring(DIRECTORY.length() + 1, path.length() - 5));
            if (loaded.size() >= MAX_DEFINITIONS) {
                AntOS.LOGGER.warn("AntOS terminal command limit reached ({}); additional definitions are skipped", MAX_DEFINITIONS);
                break;
            }
            try (var reader = resource.getValue().openAsReader()) {
                Definition definition = parse(id, JsonParser.parseReader(reader), pools);
                if (definition == null) continue;
                boolean clash = false;
                for (String name : definition.names()) {
                    ResourceLocation previous = signatures.get(signature(name, definition.arguments()));
                    if (previous != null) {
                        AntOS.LOGGER.warn("Ignoring terminal command {} because '{}' with the same arguments is already defined by {}", id, name, previous);
                        clash = true;
                        break;
                    }
                }
                if (clash) continue;
                for (String name : definition.names()) signatures.put(signature(name, definition.arguments()), id);
                loaded.put(id, definition);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Failed to load terminal command {}", resource.getKey(), exception);
            }
        }
        return Map.copyOf(loaded);
    }

    public static void apply(Map<ResourceLocation, Definition> loaded) {
        Map<String, List<Definition>> names = new LinkedHashMap<>();
        loaded.values().stream().sorted(Comparator.comparing(definition -> definition.id().toString())).forEach(definition -> {
            for (String name : definition.names()) names.computeIfAbsent(name, ignored -> new ArrayList<>()).add(definition);
        });
        names.replaceAll((name, list) -> List.copyOf(list));
        definitions = Map.copyOf(loaded);
        byName = Map.copyOf(names);
    }

    public static Definition definition(ResourceLocation id) { return id == null ? null : definitions.get(id); }
    public static Map<ResourceLocation, Definition> definitions() { return definitions; }
    public static List<Definition> named(String name) { return name == null ? List.of() : byName.getOrDefault(name.toLowerCase(Locale.ROOT), List.of()); }

    private static String signature(String name, List<String> arguments) {
        return name + "\u0000" + String.join("\u0000", arguments.stream().map(argument -> argument.toLowerCase(Locale.ROOT)).toList());
    }

    private static Definition parse(ResourceLocation id, JsonElement element, Map<ResourceLocation, ComputerGuideData.RewardPool> pools) {
        if (!element.isJsonObject()) {
            AntOS.LOGGER.warn("Ignoring terminal command {} because it is not a JSON object", id);
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String command = string(object, "command", "").trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(command).matches()) {
            AntOS.LOGGER.warn("Ignoring terminal command {} because 'command' must be 1-32 lowercase letters, digits, '.', '_' or '-'", id);
            return null;
        }
        List<String> names = new ArrayList<>();
        names.add(command);
        for (String alias : strings(object, "aliases")) {
            String name = alias.trim().toLowerCase(Locale.ROOT);
            if (!NAME.matcher(name).matches()) {
                AntOS.LOGGER.warn("Ignoring invalid alias '{}' on terminal command {}", alias, id);
                continue;
            }
            if (!names.contains(name)) names.add(name);
        }
        for (String name : names) {
            if (BUILT_IN.contains(name)) {
                AntOS.LOGGER.warn("Ignoring terminal command {} because '{}' is a built-in command", id, name);
                return null;
            }
        }
        List<String> arguments = strings(object, "arguments");
        if (arguments.size() > MAX_ARGUMENTS || arguments.stream().anyMatch(argument -> argument.isEmpty() || argument.length() > 64)) {
            AntOS.LOGGER.warn("Ignoring terminal command {} because it has more than {} arguments or an empty or overlong argument", id, MAX_ARGUMENTS);
            return null;
        }
        boolean caseSensitive = bool(object, "case_sensitive", false);
        boolean hidden = bool(object, "hidden", true);
        String usage = string(object, "usage", "");
        if (usage.isBlank()) usage = String.join(" ", names.get(0), String.join(" ", arguments)).trim();
        String style = string(object, "style", "output").toLowerCase(Locale.ROOT);
        if (!STYLES.contains(style)) {
            AntOS.LOGGER.warn("Terminal command {} uses unknown style '{}'; using output", id, style);
            style = "output";
        }
        boolean typing = bool(object, "typing", false);
        int speed = Math.max(5, Math.min(400, integer(object, "typing_speed", 40)));
        List<Line> output = lines(id, object.get("output"), style, typing, speed);
        List<Line> lockedOutput = lines(id, object.get("locked_output"), style, typing, speed);
        Requirements requires = Requirements.parse(id, object.get("requires"));
        if (requires == null) return null;
        String wallpaper = string(object, "wallpaper", "");
        ResourceLocation wallpaperId = wallpaper.isBlank() ? null : ResourceLocation.tryParse(wallpaper);
        if (!wallpaper.isBlank() && wallpaperId == null) AntOS.LOGGER.warn("Terminal command {} has an invalid wallpaper ID {}", id, wallpaper);
        DiskFeatures features = DiskFeatures.parse(object);
        if (object.has("encryption") || object.has("consumable")) AntOS.LOGGER.warn("Terminal command {} ignores 'encryption' and 'consumable'; those are disk-only fields", id);
        List<ComputerGuideData.TaskReward> rewards = ComputerGuideData.parseRewards(id, object.get("rewards"), false).stream().filter(reward -> {
            if (!reward.type().equals("item_pool") || pools.containsKey(reward.poolId())) return true;
            AntOS.LOGGER.warn("Terminal command {} refers to missing or invalid reward pool {}", id, reward.poolId());
            return false;
        }).toList();
        String open = string(object, "open", "");
        if (output.isEmpty() && lockedOutput.isEmpty() && rewards.isEmpty() && !requires.any() && open.isBlank()
                && ids(object, "entries").isEmpty() && ids(object, "tasks").isEmpty() && features.files().isEmpty() && features.programs().isEmpty()) {
            AntOS.LOGGER.warn("Ignoring terminal command {} because it has no output and does nothing", id);
            return null;
        }
        return new Definition(id, List.copyOf(names), List.copyOf(arguments), caseSensitive, hidden, usage, string(object, "description", ""),
                string(object, "hint", ""), requires, output, lockedOutput, bool(object, "once", true), bool(object, "clear", false),
                ids(object, "entries"), ids(object, "tasks"), wallpaperId, features.files(), features.programs(), features.subscriptions(), rewards, open);
    }

    private static List<Line> lines(ResourceLocation id, JsonElement element, String style, boolean typing, int speed) {
        if (element == null || element.isJsonNull()) return List.of();
        List<JsonElement> rows = new ArrayList<>();
        if (element.isJsonArray()) element.getAsJsonArray().forEach(rows::add);
        else rows.add(element);
        List<Line> lines = new ArrayList<>();
        for (JsonElement row : rows) {
            if (lines.size() >= MAX_LINES) {
                AntOS.LOGGER.warn("Terminal command {} has more than {} output lines; the rest are skipped", id, MAX_LINES);
                break;
            }
            try {
                Line line;
                if (row.isJsonPrimitive()) line = new Line(row.getAsString(), style, typing ? speed : 0, 0);
                else {
                    JsonObject object = row.getAsJsonObject();
                    String lineStyle = string(object, "style", style).toLowerCase(Locale.ROOT);
                    if (!STYLES.contains(lineStyle)) {
                        AntOS.LOGGER.warn("Terminal command {} uses unknown style '{}'; using {}", id, lineStyle, style);
                        lineStyle = style;
                    }
                    int lineSpeed = Math.max(5, Math.min(400, integer(object, "typing_speed", speed)));
                    line = new Line(string(object, "text", ""), lineStyle, bool(object, "typing", typing) ? lineSpeed : 0,
                            Math.max(0, Math.min(10000, integer(object, "delay", 0))));
                }
                if (line.text().length() > MAX_LINE_LENGTH) {
                    AntOS.LOGGER.warn("Terminal command {} has an output line longer than {} characters; it is shortened", id, MAX_LINE_LENGTH);
                    line = new Line(line.text().substring(0, MAX_LINE_LENGTH), line.style(), line.speed(), line.delay());
                }
                lines.add(line);
            } catch (RuntimeException exception) {
                AntOS.LOGGER.warn("Ignoring malformed output line on terminal command {}", id);
            }
        }
        return List.copyOf(lines);
    }

    static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }

    static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt() : fallback;
    }

    static List<String> strings(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement row : value.getAsJsonArray()) if (row.isJsonPrimitive()) result.add(row.getAsString());
        return List.copyOf(result);
    }

    static List<ResourceLocation> ids(JsonObject object, String key) {
        List<ResourceLocation> result = new ArrayList<>();
        for (String value : strings(object, key)) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) AntOS.LOGGER.warn("Ignoring invalid ID '{}' in terminal command field {}", value, key);
            else if (!result.contains(id) && result.size() < 64) result.add(id);
        }
        return List.copyOf(result);
    }

    public record Definition(ResourceLocation id, List<String> names, List<String> arguments, boolean caseSensitive, boolean hidden,
                             String usage, String description, String hint, Requirements requires, List<Line> output, List<Line> lockedOutput,
                             boolean once, boolean clear, List<ResourceLocation> entries, List<ResourceLocation> tasks, ResourceLocation wallpaper,
                             List<DiskFeatures.File> files, List<ResourceLocation> programs, List<ResourceLocation> subscriptions,
                             List<ComputerGuideData.TaskReward> rewards, String open) {
        public String command() { return names.get(0); }

        public boolean hasEffects() {
            return !entries.isEmpty() || !tasks.isEmpty() || wallpaper != null || !files.isEmpty() || !programs.isEmpty() || !subscriptions.isEmpty() || !rewards.isEmpty();
        }

        public boolean matches(List<String> given) {
            if (given.size() != arguments.size()) return false;
            for (int index = 0; index < given.size(); index++) {
                if (caseSensitive ? !given.get(index).equals(arguments.get(index)) : !given.get(index).equalsIgnoreCase(arguments.get(index))) return false;
            }
            return true;
        }
    }

    public record Line(String text, String style, int speed, int delay) { }

    public record Requirements(List<ResourceLocation> disks, List<ResourceLocation> tasks, List<ResourceLocation> commands, String mode, String directory) {
        static final Requirements NONE = new Requirements(List.of(), List.of(), List.of(), "all", "");

        boolean any() { return !disks.isEmpty() || !tasks.isEmpty() || !commands.isEmpty() || !directory.isBlank(); }

        static Requirements parse(ResourceLocation id, JsonElement element) {
            if (element == null || element.isJsonNull()) return NONE;
            if (!element.isJsonObject()) {
                AntOS.LOGGER.warn("Ignoring terminal command {} because 'requires' must be an object", id);
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            String mode = string(object, "mode", "all").toLowerCase(Locale.ROOT);
            if (!mode.equals("all") && !mode.equals("any")) {
                AntOS.LOGGER.warn("Ignoring terminal command {} because requires.mode must be all or any", id);
                return null;
            }
            String directory = string(object, "directory", "").trim();
            if (!directory.isEmpty() && !directory.startsWith("/")) directory = "/" + directory;
            return new Requirements(ids(object, "disks"), ids(object, "tasks"), ids(object, "commands"), mode, directory);
        }
    }
}
