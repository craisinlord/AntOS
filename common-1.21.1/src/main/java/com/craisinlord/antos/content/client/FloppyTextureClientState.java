package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.FloppyTextureSyncPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class FloppyTextureClientState {
    private static volatile Map<ResourceLocation, ResourceLocation> categoriesByDisk = Map.of();

    private FloppyTextureClientState() {}

    public static void update(FloppyTextureSyncPayload payload) {
        try {
            JsonObject json = JsonParser.parseString(payload.mappings()).getAsJsonObject();
            Map<ResourceLocation, ResourceLocation> updated = new HashMap<>();
            json.entrySet().forEach(entry -> updated.put(ResourceLocation.parse(entry.getKey()),
                    ResourceLocation.parse(entry.getValue().getAsString())));
            categoriesByDisk = Map.copyOf(updated);
        } catch (RuntimeException exception) {
            categoriesByDisk = Map.of();
        }
    }

    public static float modelOverride(ItemStack stack) {
        ResourceLocation diskId = stack.get(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        ResourceLocation category = diskId == null ? null : categoriesByDisk.get(diskId);
        if (category == null) return 0.0F;
        return switch (category.getPath()) {
            case "elythia" -> 1.0F;
            case "thoraxis" -> 2.0F;
            case "cavaryn" -> 3.0F;
            default -> 0.0F;
        };
    }
}
