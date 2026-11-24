package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.client.screen.StudioAssets;
import java.util.HashMap;
import java.util.Map;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

interface StudioIcon {
    Map<String, ItemStack> STACKS = new HashMap<>();

    void draw(StudioKit kit, int x, int y, int size);

    static ItemStack stack(String id) {
        return STACKS.computeIfAbsent(id, key -> {
            Identifier parsed = Identifier.tryParse(key);
            var item = parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    static StudioIcon item(String id) {
        return (kit, x, y, size) -> {
            ItemStack stack = stack(id);
            if (stack.isEmpty()) unknown(kit, x, y, size);
            else drawStack(kit, stack, x, y, size);
        };
    }

    static StudioIcon stack(ItemStack stack) { return (kit, x, y, size) -> drawStack(kit, stack, x, y, size); }

    static StudioIcon entity(String id) {
        Identifier parsed = Identifier.tryParse(id);
        var type = parsed == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(parsed).orElse(null);
        var egg = type == null ? null : SpawnEggItem.byId(type).orElse(null);
        if (egg != null) return stack(new ItemStack(egg));
        return type == null ? StudioIcon::unknown : item("minecraft:name_tag");
    }

    static StudioIcon pixel(String path) {
        Identifier texture = Identifier.fromNamespaceAndPath("antos", "textures/gui/" + path + ".png");
        return (kit, x, y, size) -> StudioAssets.icon(kit.g(), texture, x, y, size);
    }

    static StudioIcon texture(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) return StudioIcon::unknown;
        Identifier file = Identifier.fromNamespaceAndPath(parsed.getNamespace(), "textures/" + parsed.getPath() + ".png");
        return (kit, x, y, size) -> kit.g().blit(file, x, y, size, size, 0, 0, 16, 16, 16, 16);
    }

    static StudioIcon coin() {
        return (kit, x, y, size) -> {
            int inset = Math.max(1, size / 6);
            kit.fill(x + inset, y + inset + 1, size - inset * 2, size - inset * 2 - 2, Ui.COIN);
            kit.fill(x + inset + 1, y + inset, size - inset * 2 - 2, size - inset * 2, Ui.COIN);
            kit.fill(x + size / 2 - 1, y + inset + 2, 2, Math.max(2, size - inset * 2 - 4), Ui.COIN_SHADE);
        };
    }

    static StudioIcon experience() { return item("minecraft:experience_bottle"); }

    static void unknown(StudioKit kit, int x, int y, int size) {
        kit.outline(x + 1, y + 1, size - 2, size - 2, StudioTheme.strongLine);
        if (size >= 9) kit.g().drawString(kit.font(), "?", x + (size - 5) / 2, y + (size - 8) / 2, StudioTheme.soft, false);
    }

    static void drawStack(StudioKit kit, ItemStack stack, int x, int y, int size) {
        ThemedGraphics g = kit.g();
        g.pose().pushPose();
        try {
            float scale = size / 16.0F;
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.renderItem(stack, 0, 0);
        } finally { g.pose().popPose(); }
    }

    static String coins(long amount) { return amount + " " + AntOSPlayerText.currencyName(); }
}
