package com.craisinlord.antos.content.computer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TaskItemTags {
    private TaskItemTags() { }

    static int count(Map<Item, Integer> inventory, TagKey<Item> tag) {
        int total = 0;
        for (Map.Entry<Item, Integer> entry : inventory.entrySet()) if (matches(entry, tag)) total += entry.getValue();
        return total;
    }

    static Map<Item, Integer> select(Map<Item, Integer> inventory, TagKey<Item> tag, int required) {
        List<Map.Entry<Item, Integer>> matches = inventory.entrySet().stream().filter(entry -> matches(entry, tag))
                .sorted(Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(entry.getKey()).toString())).toList();
        Map<Item, Integer> result = new LinkedHashMap<>();
        int remaining = required;
        for (Map.Entry<Item, Integer> entry : matches) {
            if (remaining <= 0) break;
            int amount = Math.min(remaining, entry.getValue());
            result.put(entry.getKey(), amount);
            remaining -= amount;
        }
        return remaining > 0 ? Map.of() : result;
    }

    private static boolean matches(Map.Entry<Item, Integer> entry, TagKey<Item> tag) {
        return entry.getValue() > 0 && BuiltInRegistries.ITEM.wrapAsHolder(entry.getKey()).is(tag);
    }
}
