package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.api.client.game.ComputerGameRegistry;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.*;

final class DisksApp extends ComputerApp {
    private final List<Hit> hits = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();
    private Track dragging;
    private ResourceLocation category;
    private ResourceLocation selected;
    private String query = "";
    private String password = "";
    private boolean passwordFocused;
    private boolean searching;
    private int filter;
    private int shelfScroll;
    private int categoryScroll;
    private int detailScroll;
    private int shelfMaximum;
    private int categoryMaximum;
    private int detailMaximum;
    private int left;
    private int top;
    private int width;
    private int height;
    private Set<ResourceLocation> collected = Set.of();
    private Set<ResourceLocation> installed = Set.of();
    private DiskCollection collection;
    private ShelfView shelfView;
    private int clipTop;
    private int clipBottom;

    void render(GuiGraphics g, int x, int y, int w, int h) {
        left = x;
        top = y;
        width = w;
        height = h;
        hits.clear();
        tracks.clear();
        if (!ComputerArchiveUnlockClientState.hasSnapshot()) {
            text(g, "loading", x + 12, y + 22, Ui.TEXT_STRONG);
            text(g, "loading_hint", x + 12, y + 40, Ui.TEXT_MUTED);
            return;
        }
        DiskCollection data = collection();
        installed = data.installed();
        collected = data.collected();
        List<ComputerGuideData.Disk> disks = data.disks();
        List<ResourceLocation> categories = data.categories();
        if (category != null && !categories.contains(category)) category = null;
        drawSidebar(g, x, y, h, disks, categories);
        int contentX = x + 96;
        int contentW = w - 96;
        ComputerGuideData.Disk disk = selected == null ? null : data.byId().get(selected);
        if (disk != null && collected.contains(disk.id())) {
            drawDetail(g, contentX, y, contentW, h, disk);
        } else {
            selected = null;
            drawShelf(g, contentX, y, contentW, h, disks);
        }
    }

    void show(ResourceLocation id) {
        password = "";
        passwordFocused = false;
        var disk = ComputerGuideData.disk(id);
        category = disk == null ? null : disk.category();
        selected = ComputerArchiveUnlockClientState.collectedDisks().contains(id) ? id : null;
        query = "";
        filter = 0;
        shelfScroll = 0;
        detailScroll = 0;
    }

    private void drawSidebar(GuiGraphics g, int x, int y, int h, List<ComputerGuideData.Disk> disks, List<ResourceLocation> categories) {
        g.fill(x, y, x + 87, y + h, Ui.PANEL);
        g.fill(x + 87, y, x + 88, y + h, Ui.SURFACE);
        text(g, "collection", x + 6, y + 4, Ui.TEXT_STRONG);
        DiskCollection data = collection;
        String count = data.count();
        g.drawString(font, count, x + 6, y + 18, Ui.TEXT, false);
        long total = data.total();
        long owned = data.owned();
        g.fill(x + 6, y + 31, x + 81, y + 34, Ui.SURFACE);
        if (total > 0) g.fill(x + 6, y + 31, x + 6 + (int) (75 * owned / total), y + 34, Ui.ACCENT);
        button(g, x + 4, y + 42, 78, 19, tr("all_disks"), category == null, true, () -> chooseCategory(null));
        int visible = Math.max(1, (h - 86) / 28);
        categoryMaximum = Math.max(0, categories.size() - visible);
        categoryScroll = Math.min(categoryScroll, categoryMaximum);
        for (int i = categoryScroll; i < Math.min(categories.size(), categoryScroll + visible); i++) {
            ResourceLocation id = categories.get(i);
            int rowY = y + 68 + (i - categoryScroll) * 28;
            boolean active = id.equals(category);
            button(g, x + 4, rowY, 78, 25, "", active, true, () -> chooseCategory(id));
            g.drawString(font, screen.trimToWidth(categoryName(id), 68), x + 9, rowY + 4, active ? Ui.ACCENT : Ui.TEXT, false);
            g.drawString(font, data.categoryCounts().get(id), x + 9, rowY + 15, Ui.TEXT_MUTED, false);
        }
        scrollbar(g, x + 84, y + 68, visible * 28 - 3, visible, categories.size(), categoryScroll, categoryMaximum, 0);
        text(g, "profile_library", x + 6, y + h - 10, Ui.TEXT_MUTED);
    }

    private void chooseCategory(ResourceLocation id) {
        category = id;
        selected = null;
        shelfScroll = 0;
        password = "";
        passwordFocused = false;
    }

    private void drawShelf(GuiGraphics g, int x, int y, int w, int h, List<ComputerGuideData.Disk> disks) {
        int searchW = Math.min(104, w / 2);
        g.drawString(font, screen.trimToWidth(category == null ? tr("all_disks") : categoryName(category), w - searchW - 10), x, y + 4, Ui.TEXT_STRONG, false);
        button(g, x + w - searchW, y, searchW - 20, 17,
                query.isBlank() ? tr("search") + (searching ? "_" : "") : query + (searching ? "_" : ""), searching, true, () -> searching = true);
        button(g, x + w - 17, y, 17, 17, "X", false, !query.isEmpty(), () -> {
            query = "";
            shelfScroll = 0;
            searching = true;
        });
        String[] filters = {"all", "collected", "installed", "ejected"};
        int tabW = w / filters.length;
        for (int i = 0; i < filters.length; i++) {
            final int index = i;
            button(g, x + i * tabW, y + 23, tabW - 3, 16, tr(filters[i]), filter == i, true, () -> {
                filter = index;
                shelfScroll = 0;
                searching = false;
            });
        }
        String term = query.strip().toLowerCase(Locale.ROOT);
        ShelfView shelfView = shelfView(term);
        List<ComputerGuideData.Disk> visible = shelfView.visible();
        int columns = Math.max(1, (w - 5) / 85);
        int cellW = (w - 5) / columns;
        int gridY = y + 47;
        int gridH = h - 62;
        List<ResourceLocation> shelfCategories = shelfView.categories();
        int shelfHeight = 0;
        for (ResourceLocation id : shelfCategories) {
            long size = shelfView.shelves().get(id).size();
            shelfHeight += 19 + ((size + columns - 1) / columns) * 82 + 5;
        }
        shelfMaximum = Math.max(0, shelfHeight - gridH);
        shelfScroll = Math.min(shelfScroll, shelfMaximum);
        screen.enableComputerScissor(g, x, gridY, x + w - 5, gridY + gridH);
        int sectionY = gridY - shelfScroll;
        for (ResourceLocation id : shelfCategories) {
            List<ComputerGuideData.Disk> shelf = shelfView.shelves().get(id);
            String shelfCount = collection.categoryCounts().get(id);
            int shelfBottom = sectionY + 19 + ((shelf.size() + columns - 1) / columns) * 82 + 5;
            if (shelfBottom <= gridY || sectionY >= gridY + gridH) {
                sectionY = shelfBottom;
                continue;
            }
            g.drawString(font, screen.trimToWidth(categoryName(id), w - font.width(shelfCount) - 17), x + 2, sectionY + 2, Ui.TEXT, false);
            g.drawString(font, shelfCount, x + w - font.width(shelfCount) - 8, sectionY + 2, Ui.TEXT_MUTED, false);
            g.fill(x, sectionY + 13, x + w - 8, sectionY + 14, Ui.SURFACE);
            sectionY += 19;
            for (int i = 0; i < shelf.size(); i++) {
                int cardY = sectionY + (i / columns) * 82;
                if (cardY + 76 <= gridY || cardY >= gridY + gridH) continue;
                drawCard(g, shelf.get(i), x + (i % columns) * cellW, cardY, cellW - 5, gridY, gridY + gridH);
            }
            sectionY += ((shelf.size() + columns - 1) / columns) * 82 + 5;
        }
        g.disableScissor();
        if (visible.isEmpty()) {
            text(g, disks.isEmpty() ? "empty" : "no_matches", x + 8, gridY + 25, Ui.TEXT_STRONG);
            g.drawString(font, screen.trimToWidth(tr(disks.isEmpty() ? "empty_hint" : "no_matches_hint"), w - 16), x + 8, gridY + 41, Ui.TEXT_MUTED, false);
        }
        scrollbar(g, x + w - 3, gridY, gridH, gridH, shelfHeight, shelfScroll, shelfMaximum, 1);
        g.fill(x, y + h - 14, x + w, y + h - 13, Ui.SURFACE);
        g.drawString(font, screen.trimToWidth(tr("shelf_hint"), w), x, y + h - 9, Ui.TEXT_MUTED, false);
    }

    private void drawCard(GuiGraphics g, ComputerGuideData.Disk disk, int x, int y, int w, int clipTop, int clipBottom) {
        boolean owned = collected.contains(disk.id());
        boolean hover = screen.hovered(x, Math.max(y, clipTop), w, Math.min(y + 76, clipBottom) - Math.max(y, clipTop));
        g.fill(x, y, x + w, y + 76, hover && owned ? Ui.HOVER : Ui.PANEL);
        screen.box(g, x, y, x + w, y + 76, hover && owned ? Ui.ACCENT : Ui.BORDER);
        drawDisk(g, disk, x + w / 2 - 14, y + 7, 28, owned);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(owned ? title(disk) : "???"), w - 10);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), x + 5, y + 40 + i * 10, owned ? Ui.TEXT : Ui.TEXT_MUTED, false);
        g.drawString(font, screen.trimToWidth(tr(owned ? locked(disk) ? "locked" : installed.contains(disk.id()) ? "installed" : "ejected" : "undiscovered"), w - 10), x + 5, y + 64, owned ? Ui.ACCENT : Ui.TEXT_MUTED, false);
        if (owned) hits.add(new Hit(x, Math.max(y, clipTop), w, Math.min(y + 76, clipBottom) - Math.max(y, clipTop), () -> {
            selected = disk.id();
            detailScroll = 0;
            searching = false;
            password = "";
            passwordFocused = false;
        }));
    }

    private void drawDetail(GuiGraphics g, int x, int y, int w, int h, ComputerGuideData.Disk disk) {
        button(g, x, y, 76, 17, tr("back"), false, true, () -> selected = null);
        String status = tr(locked(disk) ? "locked" : installed.contains(disk.id()) ? "installed" : "ejected");
        g.drawString(font, status, x + w - font.width(status), y + 4, Ui.TEXT_STRONG, false);
        drawDisk(g, disk, x + 4, y + 27, 36, true);
        List<net.minecraft.util.FormattedCharSequence> titleLines = font.split(Component.literal(title(disk)), w - 53);
        for (int i = 0; i < Math.min(2, titleLines.size()); i++) g.drawString(font, titleLines.get(i), x + 49, y + 28 + i * 11, Ui.TEXT_STRONG, false);
        g.drawString(font, screen.trimToWidth(categoryName(disk.category()), w - 53), x + 49, y + 55, Ui.TEXT_MUTED, false);
        if (locked(disk)) { drawLocked(g, x, y, w, h, disk); return; }
        int bodyY = y + 79;
        int bodyH = h - 111;
        screen.enableComputerScissor(g, x, bodyY, x + w - 5, bodyY + bodyH);
        clipTop = bodyY;
        clipBottom = bodyY + bodyH;
        int line = bodyY - detailScroll;
        if (titleLines.size() > 2) line = wrapClipped(g, title(disk), x + 2, line, w - 12, Ui.ACCENT) + 9;
        if (!disk.descriptionKey().isBlank()) line = wrapClipped(g, Component.translatable(disk.descriptionKey()).getString(), x + 2, line, w - 12, Ui.TEXT) + 9;
        line = section(g, x, line, w - 8, "contents", bodyY, bodyY + bodyH);
        int contentCount = 0;
        List<ResourceLocation> workspaceDiskIds = disk.entries().isEmpty() ? List.of() : screen.workspaceDiskIds();
        List<ResourceLocation> unlockedEntries = ComputerArchiveUnlockClientState.get();
        for (ResourceLocation entryId : disk.entries()) {
            ComputerGuideData.Entry entry = ComputerGuideData.availableEntry(workspaceDiskIds, unlockedEntries, entryId);
            if (entry == null) continue;
            line = detailLink(g, x, line, w - 8, tr("archive") + " / " + ComputerGuideData.archiveTitle(entry).getString(), "ARCHIVE", bodyY, bodyY + bodyH, () -> {
                screen.open("ARCHIVE");
                screen.session.archive.select(entry);
            });
            contentCount++;
        }
        for (ResourceLocation taskId : disk.tasks()) {
            ComputerGuideData.Task task = ComputerGuideData.task(taskId);
            if (task == null) continue;
            line = detailLink(g, x, line, w - 8, tr("task") + " / " + com.craisinlord.antos.content.guide.TaskMarkup.strip(Component.translatable(task.titleKey()).getString()), "TASKS", bodyY, bodyY + bodyH, () -> {
                screen.open("TASKS");
                screen.tasks.select(taskId.toString());
            });
            contentCount++;
        }
        if (!disk.wallpaper().isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(disk.wallpaper());
            ComputerGuideData.Wallpaper wallpaper = id == null ? null : ComputerGuideData.wallpaper(id);
            if (wallpaper != null) {
                line = detailLink(g, x, line, w - 8, tr("wallpaper") + " / " + Component.translatable(wallpaper.titleKey()).getString(), "SETTINGS", bodyY, bodyY + bodyH, () -> screen.open("WALLPAPERS"));
                contentCount++;
            }
        }
        for (var game : ComputerGameRegistry.all()) {
            if (!disk.id().equals(game.diskId())) continue;
            boolean available = installed.contains(disk.id()) || ComputerGuideData.unlockAllGameEntries() || AntOSSettings.gameIncluded(game.id().toString(), game.includedByDefault());
            line = detailLink(g, x, line, w - 8, tr("game") + " / " + game.title() + (available ? "" : " / " + tr("reinsert")), available ? "GAMES" : "", bodyY, bodyY + bodyH, () -> screen.open("GAMES"));
            contentCount++;
        }
        if (contentCount == 0 && disk.features().files().isEmpty() && disk.features().programs().isEmpty() && disk.features().subscriptions().isEmpty() && ComputerArchiveUnlockClientState.diskRewards(disk.id()).isEmpty()) line = wrapClipped(g, tr("no_contents"), x + 2, line + 3, w - 12, Ui.TEXT_MUTED);
        var files = ComputerArchiveUnlockClientState.diskFiles(disk.id());
        if (!files.isEmpty()) {
            line = section(g, x, line + 9, w - 8, "files", bodyY, bodyY + bodyH);
            for (String path : files) line = detailLink(g, x, line, w - 8, path, "FILES", bodyY, bodyY + bodyH, () -> {
                screen.open("FILES");
                screen.session.files.reveal(path);
            });
        }
        if (!disk.features().programs().isEmpty()) {
            line = section(g, x, line + 9, w - 8, "programs", bodyY, bodyY + bodyH);
            for (var id : disk.features().programs()) {
                var program = com.craisinlord.antos.content.client.ComputerProgramClientState.definition(id);
                if (program == null) continue;
                boolean available = installed.contains(disk.id());
                line = detailLink(g, x, line, w - 8, Component.translatable(program.title()).getString() + (available ? "" : " / " + tr("reinsert")), available ? "GAMES" : "", bodyY, bodyY + bodyH, () -> {
                    screen.open("GAMES");
                    screen.games.openProgram(id);
                });
            }
        }
        if (!disk.features().subscriptions().isEmpty()) line = detailLink(g, x, line + 9, w - 8,
                tr("newsletters") + " / " + disk.features().subscriptions().size(), "ANTMAIL", bodyY, bodyY + bodyH, () -> screen.open("ANTMAIL"));
        var rewards = ComputerArchiveUnlockClientState.diskRewards(disk.id());
        if (!rewards.isEmpty()) {
            line = section(g, x, line + 9, w - 8, "rewards_received", bodyY, bodyY + bodyH);
            for (var reward : rewards) line = wrapClipped(g, "+ " + com.craisinlord.antos.content.client.DiskRewardsClientState.label(reward), x + 2, line, w - 12, Ui.TEXT) + 3;
        }
        var listings = com.craisinlord.antos.content.client.AntazonClientState.products().stream().filter(product -> product.unlockDisks().contains(disk.id().toString())).toList();
        if (!listings.isEmpty()) {
            line = section(g, x, line + 9, w - 8, "antazon_listings", bodyY, bodyY + bodyH);
            for (var product : listings) {
                line = detailLink(g, x, line, w - 8, Component.translatable(product.name()).getString(), "ANTAZON", bodyY, bodyY + bodyH, () -> {
                    screen.open("ANTAZON");
                    screen.session.antazon.openProduct(product.id(), "catalog");
                });
            }
        }
        var sellRules = com.craisinlord.antos.content.client.AntazonClientState.prices().stream().filter(rule -> rule.unlockDisks().contains(disk.id().toString())).toList();
        if (!sellRules.isEmpty()) {
            line = section(g, x, line + 9, w - 8, "antazon_sell", bodyY, bodyY + bodyH);
            for (var rule : sellRules) {
                ResourceLocation itemId = ResourceLocation.tryParse(rule.item());
                var item = itemId == null ? java.util.Optional.<net.minecraft.world.item.Item>empty() : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId);
                if (item.isPresent()) line = detailLink(g, x, line, w - 8, new net.minecraft.world.item.ItemStack(item.get()).getHoverName().getString(), "ANTAZON", bodyY, bodyY + bodyH, () -> {
                    screen.open("ANTAZON");
                    screen.session.antazon.showPrices();
                });
            }
        }
        detailMaximum = Math.max(0, line + detailScroll - bodyY - bodyH + 4);
        detailScroll = Math.min(detailScroll, detailMaximum);
        g.disableScissor();
        scrollbar(g, x + w - 3, bodyY, bodyH, bodyH, bodyH + detailMaximum, detailScroll, detailMaximum, 2);
        g.fill(x, y + h - 27, x + w, y + h - 26, Ui.SURFACE);
        boolean ejectable = installed.contains(disk.id()) && !disk.id().equals(ResourceLocation.fromNamespaceAndPath("antos", "introduction"));
        button(g, x + w - 101, y + h - 21, 101, 19, tr("eject"), false, ejectable, () -> {
            ComputerNetworking.eject(disk.id());
            screen.loginMessage = Component.translatable("computer.antos.status.ejecting_disk").getString();
            screen.loginMessageTicks = 100;
        });
        g.drawString(font, screen.trimToWidth(tr(ejectable ? "keeps_unlocks" : "collection_saved"), w - 109), x, y + h - 15, Ui.TEXT_MUTED, false);
    }

    private int detailLink(GuiGraphics g, int x, int y, int w, String label, String app, int clipTop, int clipBottom, Runnable action) {
        boolean enabled = !app.isBlank() && AntOSSettings.appEnabled(app);
        if (y + 19 > clipTop && y < clipBottom) {
            int hitTop = Math.max(y, clipTop);
            int hitH = Math.min(y + 19, clipBottom) - hitTop;
            boolean hover = enabled && screen.hovered(x, hitTop, w, hitH);
            g.fill(x, y, x + w, y + 19, hover ? Ui.HOVER : Ui.PANEL);
            g.drawString(font, screen.trimToWidth(label, w - 19), x + 5, y + 5, enabled ? Ui.TEXT : Ui.TEXT_MUTED, false);
            if (enabled) {
                g.drawString(font, ">", x + w - 10, y + 5, Ui.TEXT_STRONG, false);
                hits.add(new Hit(x, hitTop, w, hitH, action));
            }
        }
        return y + 23;
    }

    private boolean locked(ComputerGuideData.Disk disk) {
        return disk.features().encryption().enabled() && !ComputerArchiveUnlockClientState.activatedDisks().contains(disk.id());
    }

    private void drawLocked(GuiGraphics g, int x, int y, int w, int h, ComputerGuideData.Disk disk) {
        int textY = y + 79 - detailScroll;
        screen.enableComputerScissor(g, x, y + 79, x + w, y + h - 65);
        textY = screen.wrap(g, tr("locked_help"), x + 2, textY, w - 8, Ui.TEXT) + 8;
        if (!disk.features().encryption().hint().isBlank()) textY = screen.wrap(g, tr("hint") + " " + Component.translatable(disk.features().encryption().hint()).getString(), x + 2, textY, w - 8, Ui.TEXT_STRONG) + 8;
        if (!disk.descriptionKey().isBlank()) textY = screen.wrap(g, Component.translatable(disk.descriptionKey()).getString(), x + 2, textY, w - 8, Ui.TEXT_MUTED);
        detailMaximum = Math.max(0, textY + detailScroll - (y + h - 65));
        detailScroll = Math.min(detailScroll, detailMaximum);
        g.disableScissor();
        scrollbar(g, x + w - 3, y + 79, h - 144, h - 144, h - 144 + detailMaximum, detailScroll, detailMaximum, 2);
        boolean inserted = installed.contains(disk.id());
        button(g, x, y + h - 60, w - 77, 18, password.isEmpty() ? tr("password") : "*".repeat(Math.min(26, password.length())) + (passwordFocused ? "_" : ""), passwordFocused, inserted, () -> passwordFocused = true);
        button(g, x + w - 72, y + h - 60, 72, 18, tr("decrypt"), false, inserted && !password.isEmpty(), () -> submitPassword(disk));
        var result = com.craisinlord.antos.content.client.ComputerProgramClientState.decryption(disk.id());
        String message = !inserted ? tr("reinsert_decrypt") : result == null ? "" : tr("decrypt." + result.code());
        g.drawString(font, screen.trimToWidth(message, w), x, y + h - 37, result != null && result.code().equals("access_denied") ? Ui.ERROR : Ui.TEXT_MUTED, false);
        g.fill(x, y + h - 27, x + w, y + h - 26, Ui.SURFACE);
        button(g, x + w - 101, y + h - 21, 101, 19, tr("eject"), false, inserted, () -> ComputerNetworking.eject(disk.id()));
        g.drawString(font, screen.trimToWidth(tr("locked_footer"), w - 109), x, y + h - 15, Ui.TEXT_MUTED, false);
    }

    private void submitPassword(ComputerGuideData.Disk disk) {
        if (password.isEmpty() || !installed.contains(disk.id())) return;
        ComputerNetworking.decryptDisk(disk.id(), password, false);
        password = "";
        passwordFocused = false;
    }

    private int section(GuiGraphics g, int x, int y, int w, String key, int clipTop, int clipBottom) {
        if (y + 13 > clipTop && y < clipBottom) {
            text(g, key, x + 2, y, Ui.TEXT_STRONG);
            g.fill(x, y + 12, x + w, y + 13, Ui.SURFACE);
        }
        return y + 19;
    }

    private int wrapClipped(GuiGraphics g, String text, int x, int y, int width, int color) {
        for (var line : font.split(Component.literal(text), width)) {
            if (y + 11 > clipTop && y < clipBottom) g.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }

    private DiskCollection collection() {
        List<ComputerGuideData.Disk> source = ComputerGuideData.disks();
        List<ResourceLocation> installedSource = screen.physicalDisks();
        List<ResourceLocation> collectedSource = ComputerArchiveUnlockClientState.collectedDisks();
        Object language = net.minecraft.locale.Language.getInstance();
        long revision = ComputerGuideData.definitionRevision();
        DiskCollection cached = collection;
        if (cached != null && cached.source() == source && cached.installedSource() == installedSource && cached.collectedSource() == collectedSource
                && cached.language() == language && cached.revision() == revision) {
            return cached;
        }
        Set<ResourceLocation> installedIds = new HashSet<>(installedSource);
        Set<ResourceLocation> collectedIds = new HashSet<>(collectedSource);
        collectedIds.addAll(installedIds);
        List<ComputerGuideData.Disk> definitions = new ArrayList<>(source);
        for (ResourceLocation id : collectedIds) {
            if (ComputerGuideData.disk(id) == null) definitions.add(new ComputerGuideData.Disk(id,
                    ResourceLocation.fromNamespaceAndPath("antos", "general"), "guide.antos.missing_disk_title",
                    List.of(), "", List.of(), "computer.antos.disks.missing_definition", true));
        }
        Map<ResourceLocation, String> titles = new HashMap<>();
        Map<ResourceLocation, String> categoryNames = new HashMap<>();
        for (ComputerGuideData.Disk disk : definitions) {
            titles.putIfAbsent(disk.id(), Component.translatable(disk.titleKey()).getString());
            categoryNames.computeIfAbsent(disk.category(), this::translateCategoryName);
        }
        List<ComputerGuideData.Disk> disks = definitions.stream()
                .filter(disk -> !disk.hidden() || collectedIds.contains(disk.id()))
                .sorted(Comparator.comparing((ComputerGuideData.Disk disk) -> categoryNames.get(disk.category()))
                        .thenComparing(disk -> collectedIds.contains(disk.id()) ? titles.get(disk.id()) : disk.id().toString()))
                .toList();
        List<ResourceLocation> categories = disks.stream().map(ComputerGuideData.Disk::category).distinct().toList();
        Map<ResourceLocation, ComputerGuideData.Disk> byId = new HashMap<>();
        for (ComputerGuideData.Disk disk : disks) byId.putIfAbsent(disk.id(), disk);
        Map<ResourceLocation, String> categoryCounts = new HashMap<>();
        for (ResourceLocation id : categories) {
            categoryCounts.put(id, count(disks.stream().filter(disk -> disk.category().equals(id)).toList(), collectedIds));
        }
        long total = disks.stream().filter(disk -> !disk.hidden()).count();
        long owned = disks.stream().filter(disk -> !disk.hidden() && collectedIds.contains(disk.id())).count();
        cached = new DiskCollection(source, installedSource, collectedSource, language, revision, Set.copyOf(installedIds), Set.copyOf(collectedIds),
                disks, categories, Map.copyOf(byId), Map.copyOf(categoryCounts), owned + " / " + total, total, owned, Map.copyOf(titles), Map.copyOf(categoryNames));
        collection = cached;
        shelfView = null;
        return cached;
    }

    private ShelfView shelfView(String term) {
        DiskCollection data = collection;
        ShelfView cached = shelfView;
        if (cached != null && cached.collection() == data && java.util.Objects.equals(cached.category(), category) && cached.filter() == filter && cached.term().equals(term)) {
            return cached;
        }
        List<ComputerGuideData.Disk> visible = data.disks().stream()
                .filter(disk -> category == null || disk.category().equals(category))
                .filter(disk -> filter == 0 || filter == 1 && collected.contains(disk.id())
                        || filter == 2 && installed.contains(disk.id())
                        || filter == 3 && collected.contains(disk.id()) && !installed.contains(disk.id()))
                .filter(disk -> term.isEmpty() || collected.contains(disk.id()) && title(disk).toLowerCase(Locale.ROOT).contains(term)
                        || categoryName(disk.category()).toLowerCase(Locale.ROOT).contains(term)).toList();
        List<ResourceLocation> shelfCategories = visible.stream().map(ComputerGuideData.Disk::category).distinct().toList();
        Map<ResourceLocation, List<ComputerGuideData.Disk>> shelves = new HashMap<>();
        for (ResourceLocation id : shelfCategories) shelves.put(id, visible.stream().filter(disk -> disk.category().equals(id)).toList());
        cached = new ShelfView(data, category, filter, term, visible, shelfCategories, Map.copyOf(shelves));
        shelfView = cached;
        return cached;
    }

    private void drawDisk(GuiGraphics g, ComputerGuideData.Disk disk, int x, int y, int size, boolean owned) {
        if (owned) {
            ResourceLocation texture = ComputerGuideData.diskTexture(disk.id());
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), "textures/" + texture.getPath() + ".png");
            g.blit(file, x, y, size, size, 0, 0, 16, 16, 16, 16);
        } else {
            g.fill(x + 1, y + 3, x + size - 1, y + size - 1, Ui.DISK_SLOT);
            g.fill(x + 4, y + 1, x + size - 4, y + size - 1, Ui.DISK_SLOT);
            g.fill(x + size / 4, y + 3, x + size * 3 / 4, y + size / 3, Ui.PANEL);
            g.fill(x + size / 4, y + size * 2 / 3, x + size * 3 / 4, y + size - 4, Ui.PANEL);
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, boolean enabled, Runnable action) {
        boolean hover = enabled && screen.hovered(x, y, w, h);
        g.fill(x, y, x + w, y + h, active || hover ? Ui.HOVER : Ui.PANEL);
        screen.box(g, x, y, x + w, y + h, active || hover ? Ui.ACCENT : Ui.BORDER);
        g.drawString(font, screen.trimToWidth(label, w - 8), x + 4, y + (h - 8) / 2, enabled ? active || hover ? Ui.ACCENT : Ui.TEXT : Ui.TEXT_MUTED, false);
        if (enabled) hits.add(new Hit(x, y, w, h, () -> AntOSUiSounds.action(action)));
    }

    private static String count(List<ComputerGuideData.Disk> disks, Set<ResourceLocation> collected) {
        long total = disks.stream().filter(disk -> !disk.hidden()).count();
        long owned = disks.stream().filter(disk -> !disk.hidden() && collected.contains(disk.id())).count();
        return owned + " / " + total;
    }

    private String title(ComputerGuideData.Disk disk) {
        DiskCollection data = collection;
        String cached = data == null ? null : data.titles().get(disk.id());
        if (cached != null && data.byId().get(disk.id()) == disk) return cached;
        return Component.translatable(disk.titleKey()).getString();
    }

    private String categoryName(ResourceLocation id) {
        DiskCollection data = collection;
        String cached = data == null ? null : data.categoryNames().get(id);
        return cached != null ? cached : translateCategoryName(id);
    }

    private String translateCategoryName(ResourceLocation id) {
        var definition = ComputerGuideData.category(id);
        return definition == null ? Component.translatable("guide.antos.category.general").getString() : Component.translatable(definition.titleKey()).getString();
    }

    private String tr(String key) {
        return Component.translatable("computer.antos.disks." + key).getString();
    }

    private void text(GuiGraphics g, String key, int x, int y, int color) {
        g.drawString(font, tr(key), x, y, color, false);
    }

    boolean click(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        searching = false;
        passwordFocused = false;
        for (Track track : tracks) {
            if (inside(track.x() - 1, track.y(), 5, track.h(), mouseX, mouseY)) {
                dragging = track;
                drag(mouseY);
                return true;
            }
        }
        for (Hit hit : List.copyOf(hits)) {
            if (inside(hit.x(), hit.y(), hit.w(), hit.h(), mouseX, mouseY)) {
                hit.action().run();
                break;
            }
        }
        return true;
    }

    private void scrollbar(GuiGraphics g, int x, int y, int h, int visible, int total, int scroll, int maximum, int area) {
        if (maximum <= 0) return;
        screen.drawScrollbar(g, x, y, h, visible, total, scroll, maximum);
        int thumb = screen.scrollbarThumbHeight(h, visible, total);
        tracks.add(new Track(x, y, h, thumb, maximum, area));
    }

    void drag(double mouseY) {
        if (dragging == null) return;
        double progress = Math.max(0, Math.min(1, (mouseY - dragging.y() - dragging.thumb() / 2.0) / Math.max(1, dragging.h() - dragging.thumb())));
        int value = (int) Math.round(progress * dragging.maximum());
        switch (dragging.area()) {
            case 0 -> categoryScroll = value;
            case 1 -> shelfScroll = value;
            case 2 -> detailScroll = value;
        }
    }

    void release() {
        dragging = null;
    }

    boolean scroll(ComputerScreen.Window window, double mouseX, double mouseY, double amount) {
        double localX = (mouseX - screen.width / 2.0F) / screen.uiScale();
        double localY = (mouseY - screen.height / 2.0F) / screen.uiScale();
        if (!inside(left, top, width, height, localX, localY)) return false;
        int delta = (int) Math.signum(amount);
        if (localX < left + 88) categoryScroll = Math.max(0, Math.min(categoryMaximum, categoryScroll - delta));
        else if (selected != null) detailScroll = Math.max(0, Math.min(detailMaximum, detailScroll - delta * 16));
        else shelfScroll = Math.max(0, Math.min(shelfMaximum, shelfScroll - delta * 22));
        return true;
    }

    boolean type(char codePoint) {
        if (passwordFocused && !Character.isISOControl(codePoint)) {
            if (password.length() < 128) password += codePoint;
            return true;
        }
        if (!searching || Character.isISOControl(codePoint)) return false;
        if (query.length() < 64) query += codePoint;
        shelfScroll = 0;
        return true;
    }

    boolean key(int keyCode, int modifiers) {
        if (passwordFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !password.isEmpty()) password = password.substring(0, password.offsetByCodePoints(password.length(), -1));
            else if (keyCode == GLFW.GLFW_KEY_ENTER && selected != null) { var disk = ComputerGuideData.disk(selected); if (disk != null) submitPassword(disk); }
            else if (keyCode == GLFW.GLFW_KEY_ESCAPE) { passwordFocused = false; password = ""; }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && screen.hasControl(modifiers)) {
            selected = null;
            searching = true;
            return true;
        }
        if (searching) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
                query = screen.hasControl(modifiers) ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1));
                shelfScroll = 0;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) searching = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && selected != null) {
            selected = null;
            return true;
        }
        return false;
    }

    private record DiskCollection(List<ComputerGuideData.Disk> source, List<ResourceLocation> installedSource, List<ResourceLocation> collectedSource, Object language, long revision,
                                  Set<ResourceLocation> installed, Set<ResourceLocation> collected, List<ComputerGuideData.Disk> disks, List<ResourceLocation> categories,
                                  Map<ResourceLocation, ComputerGuideData.Disk> byId, Map<ResourceLocation, String> categoryCounts, String count, long total, long owned,
                                  Map<ResourceLocation, String> titles, Map<ResourceLocation, String> categoryNames) { }
    private record ShelfView(DiskCollection collection, ResourceLocation category, int filter, String term, List<ComputerGuideData.Disk> visible,
                             List<ResourceLocation> categories, Map<ResourceLocation, List<ComputerGuideData.Disk>> shelves) { }
    private record Hit(int x, int y, int w, int h, Runnable action) { }
    private record Track(int x, int y, int h, int thumb, int maximum, int area) { }
}
