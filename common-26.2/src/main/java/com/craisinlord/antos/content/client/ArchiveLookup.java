package com.craisinlord.antos.content.client;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.client.screen.ComputerScreen;
import com.craisinlord.antos.content.guide.ArchiveLookupIndex;
import com.craisinlord.antos.content.guide.ArchiveRecipeOutputs;
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
import net.minecraft.resources.Identifier;
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
    private static List<Identifier> unlocked = List.of();
    private static List<Identifier> disks = List.of();
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
    private static ArchiveRecipeOutputs indexedOutputs;
    private static ArchiveRecipeOutputs outputs = ArchiveRecipeOutputs.EMPTY;
    private static Target insideTarget;
    private static int insideHold;
    private static boolean insideWaitRelease;

    private ArchiveLookup() { }

    public static KeyMapping create(int defaultKey, KeyMapping.Category category) {
        key = new KeyMapping("key.antos.archive_lookup", InputConstants.Type.KEYSYM, defaultKey, category);
        return key;
    }

    public static void remember(List<Identifier> unlockedEntries, List<Identifier> activatedDisks) {
        List<Identifier> diskIds = new ArrayList<>();
        diskIds.add(Identifier.fromNamespaceAndPath("antos", "introduction"));
        diskIds.addAll(activatedDisks);
        unlocked = List.copyOf(unlockedEntries);
        disks = List.copyOf(diskIds);
        known = true;
    }

    public static void rememberOutputs(ArchiveRecipeOutputs recipeOutputs) {
        outputs = recipeOutputs == null ? ArchiveRecipeOutputs.EMPTY : recipeOutputs;
    }

    public static void rememberTasks(Map<String, String> taskByItem) {
        tasks = Map.copyOf(taskByItem);
    }

    public static void clear() {
        unlocked = List.of();
        disks = List.of();
        known = false;
        tasks = Map.of();
        outputs = ArchiveRecipeOutputs.EMPTY;
        indexed = null;
        indexedOutputs = null;
        insideTarget = null;
        insideHold = 0;
        byItem = Map.of();
        hovered = null;
        hold = 0;
        pending = null;
        returnTo = null;
    }

    public static void tooltip(ItemStack stack, List<Component> lines) {
        if (stack == null || stack.isEmpty() || lines == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() == null || minecraft.gui.screen() instanceof ComputerScreen) return;
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
        Target target = hoveredTick >= ticks - 2 && minecraft.gui.screen() != null && !(minecraft.gui.screen() instanceof ComputerScreen) ? hovered : null;
        if (target == null || !down || waitRelease || typing(minecraft.gui.screen())) {
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
        Screen parent = minecraft.gui.screen();
        pending = target;
        if (JeiBridge.parentOf(parent) instanceof ComputerScreen computer) {
            minecraft.gui.setScreen(computer);
            return;
        }
        minecraft.gui.setScreen(new ComputerScreen());
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
        var window = minecraft.getWindow();
        if (bound.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window.handle(), bound.getValue()) == GLFW.GLFW_PRESS;
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
            ArchiveRecipeOutputs outputs = recipeOutputs();
            if (entries != indexed || outputs != indexedOutputs) {
                byItem = ArchiveLookupIndex.build(ComputerGuideData.archiveEntries(), entries, outputs);
                indexed = entries;
                indexedOutputs = outputs;
            }
            ArchiveLookupIndex.Match match = byItem.get(item);
            if (match != null) return new Target("archive", match.entry().id().toString());
        }
        String task = AntOSSettings.appEnabled("TASKS") ? tasks.get(item) : null;
        return task == null ? null : new Target("task", task);
    }

    private static ArchiveRecipeOutputs recipeOutputs() {
        return outputs;
    }

    public static Target inside(ItemStack stack) {
        if (stack == null || stack.isEmpty() || key == null || key.isUnbound()) return null;
        return find(stack);
    }

    public static String insideHint(Target target) {
        if (target == null) return "";
        if (target.equals(insideTarget) && insideHold > 0) {
            int filled = Math.min(BAR, Math.round(insideHold * (float) BAR / HOLD_TICKS));
            return "§a" + "|".repeat(filled) + "§8" + "|".repeat(BAR - filled);
        }
        return "§8" + Component.translatable("computer.antos.lookup.hint", key.getTranslatedKeyMessage().getString()).getString();
    }

    public static boolean insideTick(Target target, boolean typing) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean down = down(minecraft);
        if (!down) insideWaitRelease = false;
        if (target == null || !down || insideWaitRelease || typing) {
            insideHold = 0;
            insideTarget = target;
            return false;
        }
        if (!target.equals(insideTarget)) insideHold = 0;
        insideTarget = target;
        if (++insideHold < HOLD_TICKS) return false;
        insideHold = 0;
        insideWaitRelease = true;
        return true;
    }
}
