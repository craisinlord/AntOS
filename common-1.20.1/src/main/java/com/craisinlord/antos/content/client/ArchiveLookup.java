package com.craisinlord.antos.content.client;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.client.screen.ComputerScreen;
import com.craisinlord.antos.content.guide.ArchiveLookupIndex;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class ArchiveLookup {
    public record Target(String kind, String id) { }

    private static final int HOLD_TICKS = 16;
    private static final int BAR = 12;
    private static KeyMapping key;
    private static List<ResourceLocation> unlocked = List.of();
    private static List<ResourceLocation> disks = List.of();
    private static boolean known;
    private static Map<String, String> tasks = Map.of();
    private static List<ComputerGuideData.Entry> indexed;
    private static Map<String, ArchiveLookupIndex.Match> byItem = Map.of();
    private static Target hovered;
    private static long hoveredTick = Long.MIN_VALUE;
    private static long ticks;
    private static int hold;
    private static boolean waitRelease;
    private static Target pending;
    private static Screen returnTo;

    private ArchiveLookup() { }

    public static KeyMapping create(int defaultKey) {
        key = new KeyMapping("key.antos.archive_lookup", InputConstants.Type.KEYSYM, defaultKey, "key.categories.antos");
        return key;
    }

    public static void remember(List<ResourceLocation> unlockedEntries, List<ResourceLocation> activatedDisks) {
        List<ResourceLocation> diskIds = new ArrayList<>();
        diskIds.add(new ResourceLocation("antos", "introduction"));
        diskIds.addAll(activatedDisks);
        unlocked = List.copyOf(unlockedEntries);
        disks = List.copyOf(diskIds);
        known = true;
    }

    public static void rememberTasks(Map<String, String> taskByItem) {
        tasks = Map.copyOf(taskByItem);
    }

    public static void clear() {
        unlocked = List.of();
        disks = List.of();
        known = false;
        tasks = Map.of();
        indexed = null;
        byItem = Map.of();
        hovered = null;
        hold = 0;
        pending = null;
        returnTo = null;
    }

    public static void tooltip(ItemStack stack, List<Component> lines) {
        if (stack == null || stack.isEmpty() || lines == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen == null || minecraft.screen instanceof ComputerScreen) return;
        if (key == null || key.isUnbound() || !hasPhone(minecraft.player)) return;
        Target target = find(stack);
        if (target == null) return;
        if (!target.equals(hovered)) hold = 0;
        hovered = target;
        hoveredTick = ticks;
        lines.add(hold > 0 ? progress() : Component.translatable("computer.antos.lookup.hint",
                Component.literal(key.getTranslatedKeyMessage().getString()).withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void tick() {
        ticks++;
        Minecraft minecraft = Minecraft.getInstance();
        boolean down = down(minecraft);
        if (!down) waitRelease = false;
        Target target = hoveredTick >= ticks - 2 && minecraft.screen != null && !(minecraft.screen instanceof ComputerScreen) ? hovered : null;
        if (target == null || !down || waitRelease || typing(minecraft.screen)) {
            hold = 0;
            return;
        }
        if (++hold < HOLD_TICKS) return;
        hold = 0;
        waitRelease = true;
        open(minecraft, target);
    }

    public static Target pending() { return pending; }

    public static void finishPending() { pending = null; }

    public static void forgetReturn() { returnTo = null; }

    public static Screen takeReturn() {
        Screen screen = returnTo;
        returnTo = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (screen == null || minecraft.player == null) return null;
        if (screen instanceof AbstractContainerScreen<?> container && container.getMenu() != minecraft.player.containerMenu) return null;
        return screen;
    }

    public static void openTarget(Target target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || target == null) return;
        open(minecraft, target);
    }

    public static Screen peekReturn() { return returnTo; }

    private static void open(Minecraft minecraft, Target target) {
        Screen parent = minecraft.screen;
        pending = target;
        if (JeiBridge.parentOf(parent) instanceof ComputerScreen computer) {
            minecraft.setScreen(computer);
            return;
        }
        minecraft.setScreen(new ComputerScreen());
        returnTo = parent;
    }

    private static MutableComponent progress() {
        int filled = Math.min(BAR, Math.round(hold * (float) BAR / HOLD_TICKS));
        return Component.literal("|".repeat(filled)).withStyle(ChatFormatting.GREEN)
                .append(Component.literal("|".repeat(BAR - filled)).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static boolean typing(Screen screen) {
        return screen != null && screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox box && box.isFocused();
    }

    private static boolean down(Minecraft minecraft) {
        if (key == null || key.isUnbound()) return false;
        InputConstants.Key bound = InputConstants.getKey(key.saveString());
        long window = minecraft.getWindow().getWindow();
        if (bound.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
        return bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(window, bound.getValue());
    }

    public static boolean hasPhone(Player player) {
        if (!AntOSSettings.archiveLookupRequiresPhone()) return true;
        Item phone = AntOSObjects.ANTROID_PHONE.get();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(phone)) return true;
        }
        return player.containerMenu != null && player.containerMenu.getCarried().is(phone);
    }

    private static Target find(ItemStack stack) {
        if (!known) return null;
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (AntOSSettings.appEnabled("ARCHIVE")) {
            List<ComputerGuideData.Entry> entries = ComputerGuideData.entriesFor(disks, unlocked);
            if (entries != indexed) {
                byItem = ArchiveLookupIndex.build(ComputerGuideData.archiveEntries(), entries);
                indexed = entries;
            }
            ArchiveLookupIndex.Match match = byItem.get(item);
            if (match != null) return new Target("archive", match.entry().id().toString());
        }
        String task = AntOSSettings.appEnabled("TASKS") ? tasks.get(item) : null;
        return task == null ? null : new Target("task", task);
    }
}
