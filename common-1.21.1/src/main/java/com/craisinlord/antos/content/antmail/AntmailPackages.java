package com.craisinlord.antos.content.antmail;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.computer.ComputerTasks;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Mail-triggered Antazon-style crate drops ({@code "delivery"} on a mail definition). Items use the vanilla
 * item stack JSON ({@code id}, {@code count}, {@code components}); an {@code explorer_map} entry builds a
 * treasure map to the nearest structure (ID or {@code #tag}) in the reader's current dimension.
 */
public final class AntmailPackages {
    private static final int MAP_SEARCH_RADIUS = 100;

    private AntmailPackages() { }

    public record Delivery(List<JsonObject> items, boolean onRead) { }

    public static Delivery parse(JsonObject object) {
        List<JsonObject> items = new ArrayList<>();
        if (object.has("items") && object.get("items").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("items")) if (element.isJsonObject()) items.add(element.getAsJsonObject());
        }
        String on = object.has("on") ? object.get("on").getAsString() : "read";
        return new Delivery(List.copyOf(items), !on.equals("deliver"));
    }

    public static void drop(ServerPlayer player, Delivery delivery) {
        List<ItemStack> stacks = new ArrayList<>();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, player.server.registryAccess());
        for (JsonObject item : delivery.items()) {
            try {
                ItemStack stack = item.has("explorer_map") ? explorerMap(player, item)
                        : ItemStack.CODEC.parse(ops, item).getOrThrow();
                if (stack != null && !stack.isEmpty()) stacks.add(stack);
            } catch (RuntimeException exception) {
                AntOS.LOGGER.warn("Skipping invalid Antmail delivery item {}: {}", item, exception.getMessage());
            }
        }
        if (!stacks.isEmpty()) ComputerTasks.dropRewardCrate(player, stacks);
    }

    private static ItemStack explorerMap(ServerPlayer player, JsonObject item) {
        ServerLevel level = player.serverLevel();
        String target = item.get("explorer_map").getAsString();
        BlockPos found;
        if (target.startsWith("#")) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(target.substring(1)));
            found = level.findNearestMapStructure(tag, player.blockPosition(), MAP_SEARCH_RADIUS, false);
        } else {
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(target))).orElse(null);
            if (structure == null) throw new IllegalArgumentException("unknown structure " + target);
            var pair = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(structure),
                    player.blockPosition(), MAP_SEARCH_RADIUS, false);
            found = pair == null ? null : pair.getFirst();
        }
        if (found == null) {
            AntOS.LOGGER.warn("Antmail explorer map found no {} near {} in {}", target, player.getGameProfile().getName(), level.dimension().location());
            return ItemStack.EMPTY;
        }
        ItemStack map = MapItem.create(level, found.getX(), found.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        Holder<MapDecorationType> decoration = MapDecorationTypes.RED_X;
        if (item.has("decoration")) {
            var key = ResourceKey.create(Registries.MAP_DECORATION_TYPE, ResourceLocation.parse(item.get("decoration").getAsString()));
            decoration = level.registryAccess().registryOrThrow(Registries.MAP_DECORATION_TYPE).getHolder(key).map(holder -> (Holder<MapDecorationType>) holder).orElse(decoration);
        }
        MapItemSavedData.addTargetDecoration(map, found, "+", decoration);
        if (item.has("name")) map.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable(item.get("name").getAsString()));
        return map;
    }
}
