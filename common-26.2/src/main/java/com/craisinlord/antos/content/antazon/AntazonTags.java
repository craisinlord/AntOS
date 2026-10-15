package com.craisinlord.antos.content.antazon;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class AntazonTags {
    private AntazonTags() { }

    public static List<Identifier> items(List<Identifier> itemIds, List<Identifier> tagIds) {
        Set<Identifier> result = new LinkedHashSet<>();
        for (Identifier id : itemIds) {
            if (BuiltInRegistries.ITEM.getValue(id) != Items.AIR) result.add(id);
        }
        for (Identifier tag : tagIds) {
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag))) {
                if (holder.value() != Items.AIR) result.add(BuiltInRegistries.ITEM.getKey(holder.value()));
            }
        }
        List<Identifier> sorted = new ArrayList<>(result);
        sorted.sort(Comparator.comparing(Identifier::toString));
        return List.copyOf(sorted);
    }

    public static Predicate<ItemStack> matcher(String resource) {
        if (resource == null || resource.isBlank()) return null;
        try {
            if (resource.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(resource.substring(1)));
                return stack -> !stack.isEmpty() && stack.is(tag);
            }
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(resource));
            return item == Items.AIR ? null : stack -> !stack.isEmpty() && stack.is(item);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
