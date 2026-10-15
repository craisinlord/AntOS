package com.craisinlord.antos.content.computer.blockle;

import com.craisinlord.antos.AntOS;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class BlockleAnswers extends SimplePreparableReloadListener<BlockleAnswers.Data> {
    private static final BlockleAnswers INSTANCE = new BlockleAnswers();
    private static volatile Data data = new Data(List.of(), Map.of());

    private BlockleAnswers() {}

    public static BlockleAnswers instance() { return INSTANCE; }

    public static Answer answerForDay(long day) {
        Data current = data;
        if (current.words().isEmpty()) return null;
        String word = current.words().get((int) Math.floorMod(day + 0x4A17, current.words().size()));
        return new Answer(word, current.items().getOrDefault(word, ResourceLocation.withDefaultNamespace("book")));
    }

    @Override
    protected Data prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        Map<String, ResourceLocation> items = new LinkedHashMap<>();
        resourceManager.listResources("blockle", id -> id.getPath().endsWith("answers.json")).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing((ResourceLocation id) -> id.getNamespace().equals("antos") ? "" : id.getNamespace()).thenComparing(ResourceLocation::toString)))
                .forEach(entry -> {
                var resource = entry.getValue();
                try (var reader = resource.openAsReader()) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    if (root.has("replace") && root.get("replace").getAsBoolean()) {
                        words.clear();
                        items.clear();
                    }
                    if (root.has("values") && root.get("values").isJsonArray()) {
                        for (JsonElement element : root.getAsJsonArray("values")) {
                            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                                String word = element.getAsString().toLowerCase(java.util.Locale.ROOT);
                                if (word.matches("[a-z]{5}")) words.add(word);
                            }
                        }
                    }
                    if (root.has("items") && root.get("items").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> itemEntry : root.getAsJsonObject("items").entrySet()) {
                            String word = itemEntry.getKey().toLowerCase(java.util.Locale.ROOT);
                            ResourceLocation item = ResourceLocation.tryParse(itemEntry.getValue().getAsString());
                            if (word.matches("[a-z]{5}") && item != null) items.put(word, item);
                        }
                    }
                } catch (Exception exception) {
                    AntOS.LOGGER.error("Failed to load Blockle answers from {}", entry.getKey(), exception);
                }
        });
        return new Data(List.copyOf(words), Map.copyOf(items));
    }

    @Override
    protected void apply(Data loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
        data = loaded;
        AntOS.LOGGER.info("Loaded {} Blockle answers", loaded.words().size());
    }

    record Data(List<String> words, Map<String, ResourceLocation> items) {}
    public record Answer(String word, ResourceLocation itemId) {}
}
