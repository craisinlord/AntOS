package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.FloppyTextureSyncPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class FloppyTextureClientState {
    private static volatile Map<Identifier, Identifier> categoriesByDisk = Map.of();

    private FloppyTextureClientState() {}

    public static void update(FloppyTextureSyncPayload payload) {
        try {
            JsonObject json = JsonParser.parseString(payload.mappings()).getAsJsonObject();
            Map<Identifier, Identifier> updated = new HashMap<>();
            json.entrySet().forEach(entry -> updated.put(Identifier.parse(entry.getKey()),
                    Identifier.parse(entry.getValue().getAsString())));
            categoriesByDisk = Map.copyOf(updated);
        } catch (RuntimeException exception) {
            categoriesByDisk = Map.of();
        }
    }

    public static String modelCategory(ItemStack stack) {
        Identifier diskId = stack.get(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        Identifier category = diskId == null ? null : categoriesByDisk.get(diskId);
        if (category == null) return "default";
        return switch (category.getPath()) {
            case "elythia" -> "elythia";
            case "thoraxis" -> "thoraxis";
            case "cavaryn" -> "cavaryn";
            default -> "default";
        };
    }
}
