package com.craisinlord.antos.content.client.studio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

final class StudioItems {
    private static final Map<String, ItemStack> DECODED = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) { return size() > 256; }
    };

    private StudioItems() { }

    static ItemStack decode(JsonObject object) {
        if (!object.has("tag")) return StudioIcon.stack(StudioJson.string(object, "id"));
        return DECODED.computeIfAbsent(object.toString(), ignored -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return ItemStack.EMPTY;
            return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), object).result().orElse(ItemStack.EMPTY);
        });
    }

    static JsonObject encode(StudioPicker.Picked picked, int count) {
        JsonObject plain = new JsonObject();
        plain.addProperty("id", picked.id());
        plain.addProperty("Count", count);
        var level = Minecraft.getInstance().level;
        if (picked.stack().isEmpty() || level == null || ItemStack.isSameItemSameTags(picked.stack(), new ItemStack(picked.stack().getItem()))) return plain;
        JsonElement encoded = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), picked.stack().copyWithCount(1)).result().orElse(plain);
        if (!encoded.isJsonObject()) return plain;
        encoded.getAsJsonObject().addProperty("Count", count);
        return encoded.getAsJsonObject();
    }
}
