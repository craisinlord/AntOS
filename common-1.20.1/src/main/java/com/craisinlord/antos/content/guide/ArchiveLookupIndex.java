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
        return index;
    }

    private static List<String> tagItems(String id) {
        ResourceLocation tag = ResourceLocation.tryParse(id);
        if (tag == null) return List.of();
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag))
                .map(set -> set.stream().map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString()).toList())
                .orElse(List.of());
    }
}
