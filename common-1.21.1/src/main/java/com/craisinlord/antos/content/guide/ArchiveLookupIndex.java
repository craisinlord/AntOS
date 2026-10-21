package com.craisinlord.antos.content.guide;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public final class ArchiveLookupIndex {
    public record Match(ComputerGuideData.Entry entry, String field) { }

    private ArchiveLookupIndex() { }

    public static Map<String, Match> build(Collection<ComputerGuideData.Entry> all, List<ComputerGuideData.Entry> accessible) {
        return build(all, accessible, ArchiveRecipeOutputs.EMPTY);
    }

    public static Map<String, Match> build(Collection<ComputerGuideData.Entry> all, List<ComputerGuideData.Entry> accessible, ArchiveRecipeOutputs outputs) {
        Map<String, Set<ResourceLocation>> covers = new HashMap<>();
        Map<String, Set<ResourceLocation>> galleries = new HashMap<>();
        for (ComputerGuideData.Entry entry : all) {
            if (!entry.coverItemId().isBlank()) covers.computeIfAbsent(entry.coverItemId(), ignored -> new HashSet<>()).add(entry.id());
            for (ComputerGuideData.GalleryItem item : entry.gallery()) {
                if (!item.entity() && !item.id().isBlank()) galleries.computeIfAbsent(item.id(), ignored -> new HashSet<>()).add(entry.id());
            }
        }
        Map<String, Match> index = new LinkedHashMap<>();
        for (ComputerGuideData.Entry entry : accessible) {
            if (!entry.itemId().isBlank()) index.putIfAbsent(entry.itemId(), new Match(entry, "item"));
        }
        for (ComputerGuideData.Entry entry : accessible) {
            for (String id : entry.lookup()) if (!id.startsWith("#")) index.putIfAbsent(id, new Match(entry, "lookup"));
        }
        for (ComputerGuideData.Entry entry : accessible) {
            for (String id : entry.lookup()) if (id.startsWith("#")) for (String item : tagItems(id.substring(1))) index.putIfAbsent(item, new Match(entry, "lookup " + id));
        }
        for (ComputerGuideData.Entry entry : accessible) {
            String cover = entry.coverItemId();
            if (!cover.isBlank() && covers.getOrDefault(cover, Set.of()).size() == 1) index.putIfAbsent(cover, new Match(entry, "cover_item"));
        }
        for (ComputerGuideData.Entry entry : accessible) {
            for (ComputerGuideData.GalleryItem item : entry.gallery()) {
                if (item.entity() || item.id().isBlank() || galleries.getOrDefault(item.id(), Set.of()).size() != 1) continue;
                if (covers.containsKey(item.id()) && covers.get(item.id()).size() > 1) continue;
                index.putIfAbsent(item.id(), new Match(entry, "gallery"));
            }
        }
        Map<String, Set<ComputerGuideData.Entry>> stations = new HashMap<>();
        for (ComputerGuideData.Entry entry : all) {
            for (String type : entry.lookupRecipes()) stations.computeIfAbsent(type, ignored -> new HashSet<>()).add(entry);
        }
        if (stations.isEmpty()) return index;
        Set<ResourceLocation> open = new HashSet<>();
        for (ComputerGuideData.Entry entry : accessible) open.add(entry.id());
        for (String item : outputs.items()) {
            if (index.containsKey(item) || item.startsWith("minecraft:")) continue;
            List<Set<String>> producers = outputs.producers(item);
            ComputerGuideData.Entry station = station(stations, producers);
            if (station == null || !open.contains(station.id())) continue;
            String type = producers.get(0).stream().filter(station.lookupRecipes()::contains).findFirst().orElse("");
            index.put(item, new Match(station, "lookup_recipes " + type));
        }
        return index;
    }

    private static ComputerGuideData.Entry station(Map<String, Set<ComputerGuideData.Entry>> stations, List<Set<String>> producers) {
        ComputerGuideData.Entry found = null;
        for (Set<String> keys : producers) {
            for (String key : keys) {
                for (ComputerGuideData.Entry owner : stations.getOrDefault(key, Set.of())) {
                    if (found != null && !found.id().equals(owner.id())) return null;
                    found = owner;
                }
            }
            if (found == null || keys.stream().noneMatch(stations::containsKey)) return null;
        }
        return found;
    }

    private static List<String> tagItems(String id) {
        ResourceLocation tag = ResourceLocation.tryParse(id);
        if (tag == null) return List.of();
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag))
                .map(set -> set.stream().map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString()).toList())
                .orElse(List.of());
    }
}
