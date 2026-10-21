package com.craisinlord.antos.compat.jei;

import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.client.JeiClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class JeiDrawing {
    static final int WIDTH = 170;
    private static final String[] COIN = {"..###..", ".#+++#.", "#++#++#", "#+###+#", "#++#++#", ".#+++#.", "..###.."};

    private JeiDrawing() { }

    static Font font() {
        return Minecraft.getInstance().font;
    }

    static void panel(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, Ui.BORDER_STRONG);
        g.fill(1, 1, width - 1, height - 1, Ui.PANEL);
    }

    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, Ui.LINE);
        g.fill(x, y, x + 16, y + 16, Ui.SURFACE_SUNKEN);
    }

    static void coin(GuiGraphics g, int x, int y) {
        for (int row = 0; row < COIN.length; row++) {
            for (int column = 0; column < COIN[row].length(); column++) {
                char pixel = COIN[row].charAt(column);
                if (pixel == '#') g.fill(x + column, y + row, x + column + 1, y + row + 1, Ui.ACCENT);
                else if (pixel == '+') g.fill(x + column, y + row, x + column + 1, y + row + 1, Ui.BRAND_MARK);
            }
        }
    }

    static int text(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        Font font = font();
        String value = text.getString();
        if (font.width(value) > maxWidth) value = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
        g.drawString(font, value, x, y, color, false);
        return x + font.width(value);
    }

    static int struck(GuiGraphics g, String value, int x, int y, int color) {
        Font font = font();
        g.drawString(font, value, x, y, color, false);
        int width = font.width(value);
        g.fill(x, y + 4, x + width, y + 5, color);
        return x + width;
    }

    static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    static Item item(String id) {
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        return location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
    }

    static ItemStack stack(String id, int count) {
        Item item = item(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
    }

    static List<ItemStack> payment(JeiClientState.Payment payment) {
        List<ItemStack> stacks = new ArrayList<>();
        String resource = payment.resource();
        if (resource.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(resource.substring(1));
            if (tag != null) for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag)))
                if (holder.value() != Items.AIR) stacks.add(new ItemStack(holder.value(), Math.max(1, payment.amount())));
        } else {
            ItemStack stack = stack(resource, payment.amount());
            if (!stack.isEmpty()) stacks.add(stack);
        }
        return stacks;
    }

    static boolean antcoins(JeiClientState.Payment payment) {
        return payment.type().equals("antcoins");
    }

    static Component currency(long amount) {
        return Component.literal(number(amount) + " " + AntOSPlayerText.currencyName());
    }

    static String itemName(String id) {
        if (id.startsWith("#")) return id;
        Item item = item(id);
        return item == Items.AIR ? id : new ItemStack(item).getHoverName().getString();
    }

    static Component listingName(JeiClientState.Listing listing) {
        String text = Component.translatable(listing.name()).getString();
        if (!listing.poolItem().isBlank()) return Component.literal(text.replace("{item}", itemName(listing.poolItem())));
        if (!listing.variantMode().isBlank() && text.contains("{item}")) {
            String key = switch (listing.variantMode()) {
                case "all" -> "jei.antos.variant.all_name";
                case "random" -> "jei.antos.variant.random_name";
                default -> "jei.antos.variant.choose_name";
            };
            return Component.literal(text.replace("{item}", Component.translatable(key).getString()));
        }
        return Component.literal(text);
    }

    static List<Component> requirements(JeiClientState.Unlock unlock) {
        JeiClientState.Definitions definitions = JeiClientState.definitions();
        List<Component> lines = new ArrayList<>();
        int count = unlock.tasks().size() + unlock.disks().size() + unlock.commands().size();
        if (count > 1) lines.add(Component.translatable(unlock.mode().equals("any") ? "jei.antos.requirements.any" : "jei.antos.requirements.all"));
        for (String task : unlock.tasks())
            lines.add(Component.translatable("jei.antos.requirement.task", taskTitle(definitions, task)));
        for (String disk : unlock.disks()) {
            String title = definitions.diskTitles().get(disk);
            lines.add(Component.translatable("jei.antos.requirement.disk", title == null ? Component.literal(disk) : Component.translatable(title)));
        }
        for (String command : unlock.commands())
            lines.add(Component.translatable("jei.antos.requirement.command", Component.translatable(command)));
        return lines;
    }

    static Component firstRequirement(JeiClientState.Unlock unlock) {
        JeiClientState.Definitions definitions = JeiClientState.definitions();
        if (!unlock.tasks().isEmpty()) return taskTitle(definitions, unlock.tasks().get(0));
        if (!unlock.disks().isEmpty()) {
            String title = definitions.diskTitles().get(unlock.disks().get(0));
            return title == null ? Component.literal(unlock.disks().get(0)) : Component.translatable(title);
        }
        if (!unlock.commands().isEmpty()) return Component.translatable(unlock.commands().get(0));
        return Component.empty();
    }

    static Component taskTitle(JeiClientState.Definitions definitions, String task) {
        String title = definitions.taskTitles().get(task);
        return title == null ? Component.literal(task) : Component.translatable(title);
    }

    static Component limit(int limit, String reset) {
        String key = switch (reset) {
            case "minecraft_day" -> "jei.antos.limit.day";
            case "minecraft_week" -> "jei.antos.limit.week";
            default -> "jei.antos.limit.total";
        };
        return Component.translatable(key, number(limit));
    }

    static String duration(long millis) {
        long minutes = Math.max(1L, millis / 60_000L);
        long days = minutes / 1440L;
        long hours = minutes / 60L % 24L;
        long rest = minutes % 60L;
        if (days > 0L) return days + "d " + hours + "h";
        if (hours > 0L) return hours + "h " + rest + "m";
        return rest + "m";
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    record Line(Component text, int color, List<Component> tooltip) { }
}
