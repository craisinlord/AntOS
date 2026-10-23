package com.craisinlord.antos.content.client;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.client.screen.ComputerScreen;
import com.craisinlord.antos.content.guide.TaskMarkup;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TaskTrackerHud {
    private static final int POLL_TICKS = 40;
    private static final long AUTO_UNPIN_DELAY = 5000L;
    private static final long FLASH_MILLIS = 900L;
    private static final int DETAILED_WIDTH = 168;
    private static final int COMPACT_WIDTH = 132;
    private static final int MAX_OBJECTIVES = 2;
    private static KeyMapping key;
    private static int pollTicks;
    private static String bootstrappedKey = "";
    private static final Map<String, Long> finishedAt = new HashMap<>();
    private static final Set<String> unpinSent = new HashSet<>();
    private static final Map<String, Integer> lastProgress = new HashMap<>();
    private static final Map<String, Long> flashUntil = new HashMap<>();
    private static final Map<String, ItemStack> icons = new HashMap<>();

    private TaskTrackerHud() { }

    public static KeyMapping createKey() {
        key = new KeyMapping("key.antos.toggle_task_tracker", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.antos");
        return key;
    }

    public static void clear() {
        pollTicks = 0;
        bootstrappedKey = "";
        finishedAt.clear();
        unpinSent.clear();
        lastProgress.clear();
        flashUntil.clear();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        var preferences = AntOSSettings.preferences();
        if (key != null) while (key.consumeClick()) {
            boolean enabled = !AntOSSettings.preferences().taskTracker;
            AntOSSettings.updatePreferences(p -> p.taskTracker = enabled);
            minecraft.gui.setOverlayMessage(Component.translatable(enabled ? "computer.antos.tracker.shown" : "computer.antos.tracker.hidden"), false);
        }
        String workspaceKey = ComputerWorkspaceClientKey.of();
        if (workspaceKey.equals("workspace:unbound")) return;
        List<String> pinned = ComputerTasksClientState.pinned();
        if (preferences.taskTracker && !(minecraft.screen instanceof ComputerScreen)) {
            if (!workspaceKey.equals(bootstrappedKey)) {
                bootstrappedKey = workspaceKey;
                if (!ComputerTasksClientState.hasSnapshot()) ComputerNetworking.requestTasks(ComputerTasksClientState.requestToken());
                pollTicks = POLL_TICKS;
            } else if (!pinned.isEmpty() && --pollTicks <= 0) {
                ComputerNetworking.requestTasks(ComputerTasksClientState.requestToken());
                pollTicks = POLL_TICKS;
            }
        }
        if (pinned.isEmpty()) return;
        Map<String, ComputerTasksClientState.TaskRow> rows = rowsById();
        long now = System.currentTimeMillis();
        for (String taskId : pinned) {
            var row = rows.get(taskId);
            if (row == null) continue;
            int progress = row.objectives().stream().mapToInt(ComputerTasksClientState.TaskObjective::progress).sum() + row.done();
            Integer previous = lastProgress.put(taskId, progress);
            if (previous != null && progress > previous) flashUntil.put(taskId, now + FLASH_MILLIS);
            boolean finished = row.complete() && !row.claimable();
            if (!finished) {
                finishedAt.remove(taskId);
                continue;
            }
            long since = finishedAt.computeIfAbsent(taskId, ignored -> now);
            if (preferences.taskAutoUnpin && now - since >= AUTO_UNPIN_DELAY && unpinSent.add(taskId)) {
                ResourceLocation id = ResourceLocation.tryParse(taskId);
                if (id != null) ComputerNetworking.setTaskPinned(id, false);
                ComputerTasksClientState.setPinnedLocally(taskId, false);
            }
        }
        unpinSent.retainAll(pinned);
    }

    public static void render(GuiGraphics g) {
        Minecraft minecraft = Minecraft.getInstance();
        var preferences = AntOSSettings.preferences();
        if (!preferences.taskTracker || minecraft.player == null || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen()) return;
        if (minecraft.screen != null && !(minecraft.screen instanceof ChatScreen)) return;
        if (ComputerWorkspaceClientKey.of().equals("workspace:unbound") || !ComputerTasksClientState.hasSnapshot()) return;
        List<String> pinned = ComputerTasksClientState.pinned();
        if (pinned.isEmpty()) return;
        Map<String, ComputerTasksClientState.TaskRow> rows = rowsById();
        List<ComputerTasksClientState.TaskRow> tracked = new ArrayList<>();
        for (String taskId : pinned) {
            var row = rows.get(taskId);
            if (row != null && row.visible() && !row.isLink()) tracked.add(row);
        }
        if (tracked.isEmpty()) return;
        Font font = minecraft.font;
        boolean compact = preferences.taskTrackerStyle.equals("compact");
        int width = compact ? COMPACT_WIDTH : DETAILED_WIDTH;
        List<Block> blocks = new ArrayList<>();
        int height = 3;
        for (var row : tracked) {
            Block block = layout(font, row, rows, compact, width);
            blocks.add(block);
            height += block.height() + 3;
        }
        int screenWidth = g.guiWidth();
        int screenHeight = g.guiHeight();
        String position = preferences.taskTrackerPosition;
        int x = position.endsWith("left") ? 4 : screenWidth - width - 4;
        int y = switch (position) {
            case "top_left" -> 4;
            case "top_right" -> minecraft.player.getActiveEffects().isEmpty() ? 4 : 54;
            default -> Math.max(4, Math.min(screenHeight / 3, screenHeight - height - 40));
        };
        int alpha = Math.round(Integer.parseInt(preferences.taskTrackerOpacity) * 2.55F);
        boolean shadow = alpha < 96;
        if (alpha > 0) g.fill(x, y, x + width, y + height, alpha << 24 | Ui.BACKGROUND & 0xFFFFFF);
        long now = System.currentTimeMillis();
        int cursor = y + 3;
        for (Block block : blocks) {
            var row = block.row();
            long flash = flashUntil.getOrDefault(row.id(), 0L) - now;
            if (flash > 0) {
                int flashAlpha = (int) (0x70 * flash / FLASH_MILLIS);
                g.fill(x + 1, cursor - 1, x + width - 1, cursor + block.height() + 1, flashAlpha << 24 | Ui.ACCENT & 0xFFFFFF);
            }
            g.fill(x + 1, cursor, x + 2, cursor + block.height(), block.stripe());
            renderIcon(g, row, x + 5, cursor);
            g.drawString(font, block.title(), x + 19, cursor + 2, block.titleColor(), shadow);
            if (!block.tag().isEmpty()) g.drawString(font, block.tag(), x + width - 4 - font.width(block.tag()), cursor + 2, block.tagColor(), shadow);
            int line = cursor + 14;
            for (Line text : block.lines()) {
                g.drawString(font, text.left(), x + 19, line, text.color(), shadow);
                if (!text.right().isEmpty()) g.drawString(font, text.right(), x + width - 4 - font.width(text.right()), line, text.color(), shadow);
                if (text.goal() > 0) {
                    int barLeft = x + 19;
                    int barRight = x + width - 4;
                    g.fill(barLeft, line + 9, barRight, line + 10, Ui.alpha(Ui.SURFACE, 0xC0));
                    int filled = (int) ((long) (barRight - barLeft) * Math.min(text.progress(), text.goal()) / text.goal());
                    if (filled > 0) g.fill(barLeft, line + 9, barLeft + filled, line + 10, text.progress() >= text.goal() ? Ui.TEXT : Ui.ACCENT);
                    line += 12;
                } else line += 10;
            }
            cursor += block.height() + 3;
        }
    }

    public static void drawPin(GuiGraphics g, int centerX, int top, int color) {
        g.fill(centerX - 2, top, centerX + 3, top + 1, color);
        g.fill(centerX - 1, top + 1, centerX + 2, top + 3, color);
        g.fill(centerX - 2, top + 3, centerX + 3, top + 4, color);
        g.fill(centerX - 3, top + 4, centerX + 4, top + 5, color);
        g.fill(centerX, top + 5, centerX + 1, top + 8, color);
    }

    private static Block layout(Font font, ComputerTasksClientState.TaskRow row, Map<String, ComputerTasksClientState.TaskRow> rows,
                                boolean compact, int width) {
        boolean finished = row.complete() && !row.claimable();
        String tag;
        int tagColor;
        int titleColor = Ui.TEXT_STRONG;
        int stripe = Ui.ACCENT;
        List<Line> lines = new ArrayList<>();
        if (row.claimable()) {
            tag = tr("ready");
            tagColor = Ui.ACCENT;
            stripe = Ui.FOCUS;
            lines.add(new Line(tr("claim_hint"), "", Ui.TEXT, 0, 0));
        } else if (finished) {
            tag = tr("done");
            tagColor = Ui.TEXT;
            titleColor = Ui.TEXT;
            stripe = Ui.TEXT;
        } else if (!row.available()) {
            tag = tr("locked");
            tagColor = Ui.TEXT_DIM;
            titleColor = Ui.TEXT_SOFT;
            stripe = Ui.LOCKED;
            String needs = row.requires().stream().map(rows::get)
                    .filter(required -> required != null && !required.complete() && required.visible())
                    .map(required -> plain(required.title())).findFirst().orElse("");
            lines.add(new Line(needs.isEmpty() ? tr("locked_hint") : tr("needs", needs), "", Ui.TEXT_DIM, 0, 0));
        } else if (compact || row.objectives().isEmpty()) {
            tag = row.total() > 0 ? row.done() + "/" + row.total() : "";
            tagColor = Ui.TEXT;
            if (compact && row.total() > 0) lines.add(new Line("", "", Ui.TEXT, row.done(), row.total()));
        } else {
            tag = row.total() > 0 ? row.done() + "/" + row.total() : "";
            tagColor = Ui.TEXT;
            List<ComputerTasksClientState.TaskObjective> remaining = row.objectives().stream()
                    .filter(objective -> !objective.complete() && !objective.optional()).toList();
            for (int index = 0; index < Math.min(MAX_OBJECTIVES, remaining.size()); index++) {
                var objective = remaining.get(index);
                String count = objective.progress() + "/" + objective.displayGoal();
                String label = trim(font, plain(objective.description()), width - 27 - font.width(count));
                lines.add(new Line(label, count, Ui.TEXT, objective.progress(), Math.max(1, objective.displayGoal())));
            }
            if (remaining.size() > MAX_OBJECTIVES) lines.add(new Line(tr("more", remaining.size() - MAX_OBJECTIVES), "", Ui.TEXT_SOFT, 0, 0));
        }
        String title = trim(font, plain(row.title()), width - 25 - (tag.isEmpty() ? 0 : font.width(tag) + 4));
        int height = 13 + lines.stream().mapToInt(line -> line.goal() > 0 ? 12 : 10).sum();
        return new Block(row, title, titleColor, tag, tagColor, stripe, List.copyOf(lines), height);
    }

    private static void renderIcon(GuiGraphics g, ComputerTasksClientState.TaskRow row, int x, int y) {
        ItemStack stack = icons.computeIfAbsent(row.iconItem(), TaskTrackerHud::iconStack);
        if (stack.isEmpty()) {
            drawPin(g, x + 6, y + 2, Ui.ACCENT);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static ItemStack iconStack(String itemId) {
        ResourceLocation id = itemId == null || itemId.isBlank() ? null : ResourceLocation.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static Map<String, ComputerTasksClientState.TaskRow> rowsById() {
        Map<String, ComputerTasksClientState.TaskRow> result = new HashMap<>();
        for (var row : ComputerTasksClientState.get()) result.put(row.id(), row);
        return result;
    }

    private static String plain(String key) {
        return TaskMarkup.strip(AntOSPlayerText.apply(Component.translatable(key).getString()));
    }

    private static String trim(Font font, String text, int width) {
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable("computer.antos.tracker." + key, arguments).getString();
    }

    private record Line(String left, String right, int color, int progress, int goal) { }

    private record Block(ComputerTasksClientState.TaskRow row, String title, int titleColor, String tag, int tagColor, int stripe,
                         List<Line> lines, int height) { }
}
