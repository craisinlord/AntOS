package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.AnternetAccountClientState;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountResultPayload;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class ArchiveFolderTest {
    private static final String SNAPSHOT = """
            {
              "showIntroductionEntry": false,
              "unlockAllArchiveEntries": false,
              "archive_folders": [
                {"id":"pack:research","title":"literal:Research","icon_item":"minecraft:spyglass","sort_order":10},
                {"id":"pack:chapter","title":"literal:Chapter","sort_order":5},
                {"id":"pack:hidden","title":"literal:Hidden","sort_order":-10}
              ],
              "entries": [
                {"id":"pack:z_key","title":"literal:Alpha","folder":"pack:research","sort_order":10,"unlocked_by_default":true},
                {"id":"other:a_key","title":"literal:Zulu","folder":"pack:research","sort_order":10,"unlocked_by_default":true},
                {"id":"pack:first","title":"literal:First","folder":"pack:research","sort_order":-20,"unlocked_by_default":true},
                {"id":"pack:chapter_entry","title":"literal:Chapter Entry","folder":"pack:chapter","unlocked_by_default":true},
                {"id":"pack:loose","title":"literal:Loose","description":["literal:secret fossil"],"unlocked_by_default":true},
                {"id":"pack:missing","title":"literal:Missing","folder":"pack:unknown","unlocked_by_default":true},
                {"id":"pack:locked","title":"literal:Locked","folder":"pack:hidden"}
              ]
            }
            """;

    public static void main(String[] args) throws Exception {
        UUID account = UUID.randomUUID();
        AnternetAccountClientState.update(new AnternetAccountResultPayload(AnternetAccountResultPayload.SUCCESS, account, account, "ArchiveTester"));
        ComputerGuideData.applyNetworkSnapshot(SNAPSHOT);
        check(ComputerGuideData.entry(id("pack:z_key")).folder().equals(id("pack:research")), "folder assignment loads");
        check(ComputerGuideData.entry(id("pack:first")).sortOrder() == -20, "entry order loads");
        check(ComputerGuideData.archiveFolder(id("pack:research")).iconItem().equals("minecraft:spyglass"), "folder icon loads");
        var definitions = ComputerGuideData.archiveDefinitions();
        var encoded = JsonParser.parseString(definitions.json()).getAsJsonObject();
        check(encoded.getAsJsonArray("archive_folders").size() == 3, "folder definitions sync");
        ComputerGuideData.applyNetworkSnapshot(definitions.json());
        check(ComputerGuideData.entry(id("pack:z_key")).folder().equals(id("pack:research")), "entry folder survives sync round trip");
        check(ComputerGuideData.entry(id("pack:first")).sortOrder() == -20, "entry order survives sync round trip");

        ArchiveApp app = new ArchiveApp();
        List<?> root = browse(app, available());
        check(root.size() == 4, "root contains two visible folders and two loose entries");
        check(folder(root.get(0)).id().equals(id("pack:chapter")), "folder order is honored");
        check(folder(root.get(1)).id().equals(id("pack:research")), "one folder can collect entries from multiple namespaces");
        check(count(root.get(1)) == 3, "folder count includes only available entries");
        check(entry(root.get(2)).id().equals(id("pack:loose")), "unassigned entry stays at root");
        check(entry(root.get(3)).id().equals(id("pack:missing")), "missing folder falls back to root");

        app.state.search = "alpha";
        List<?> search = browse(app, available());
        check(search.size() == 1 && entry(search.get(0)).id().equals(id("pack:z_key")), "root search finds entries inside folders");
        check(invoke(search.get(0), "subtitle").equals("Research"), "root search result identifies its folder");
        app.state.search = "fossil";
        check(browse(app, available()).size() == 1, "root search includes description text");

        app.state.search = "";
        app.state.scroll = 3;
        invoke(app, "openFolder", ResourceLocation.class, id("pack:research"));
        List<?> research = browse(app, available());
        check(research.size() == 3, "folder restricts entries");
        check(entry(research.get(0)).id().equals(id("pack:first")), "explicit entry order precedes title order");
        check(entry(research.get(1)).id().equals(id("pack:z_key")), "ties use displayed titles rather than translation keys");
        app.state.search = "loose";
        check(browse(app, available()).isEmpty(), "folder search excludes other folders and root");
        app.state.search = "alpha";
        app.state.scroll = 2;
        app.state.entryId = id("pack:z_key");
        check(app.key(null, GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape leaves entry");
        check(app.state.entryId == null && app.state.folderId.equals(id("pack:research")) && app.state.search.equals("alpha") && app.state.scroll == 2,
                "entry back preserves folder, search, and scroll");
        check(app.key(null, GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape leaves folder");
        check(app.state.folderId == null && app.state.search.isEmpty() && app.state.scroll == 3, "folder back restores root state");

        List<?> unlocked = browse(app, ComputerGuideData.entriesFor(List.of(), List.of(id("pack:locked"))));
        check(unlocked.size() == 5 && folder(unlocked.get(0)).id().equals(id("pack:hidden")), "unlocking first entry reveals folder");
        invoke(app, "openFolder", ResourceLocation.class, id("pack:hidden"));
        browse(app, available());
        check(app.state.folderId == null, "folder losing all available entries returns to root");

        ComputerGuideData.applyNetworkSnapshot(SNAPSHOT.replace("literal:Research", "literal:Updated"));
        check(!ComputerGuideData.archiveDefinitions().hash().equals(definitions.hash()), "folder changes invalidate sync cache");
        app.state.search = "alpha";
        check(invoke(browse(app, available()).get(0), "subtitle").equals("Updated"), "reload refreshes folder labels");
        invoke(app, "openFolder", ResourceLocation.class, id("pack:research"));
        ComputerGuideData.applyNetworkSnapshot("{\"entries\":[{\"id\":\"pack:legacy\",\"title\":\"literal:Legacy\",\"unlocked_by_default\":true}]}");
        List<?> legacy = browse(app, available());
        app.state.search = "";
        legacy = browse(app, available());
        check(app.state.folderId == null && legacy.size() == 1 && entry(legacy.get(0)).folder() == null, "legacy snapshots clear folders and keep loose entries");
        nestedFolders();
        invalidHierarchy();
        folderTint();
        uiLayout();
        System.out.println("Archive folder tests passed: nested folders, sync, unlocks, ordering, search, breadcrumbs, navigation, validation, and reloads");
    }

    private static void folderTint() {
        String snapshot = """
                {
                  "archive_folders": [
                    {"id":"pack:default"},
                    {"id":"pack:colored","green_tint":false},
                    {"id":"pack:child","parent":"pack:colored"},
                    {"id":"pack:colored_child","parent":"pack:default","green_tint":false},
                    {"id":"pack:green","green_tint":true},
                    {"id":"pack:promoted","parent":"pack:missing","green_tint":false}
                  ]
                }
                """;
        ComputerGuideData.applyNetworkSnapshot(snapshot);
        check(ComputerGuideData.archiveFolder(id("pack:default")).greenTint(), "folder tint defaults to true");
        check(ComputerGuideData.archiveFolder(id("pack:green")).greenTint(), "explicit green folder tint is retained");
        check(!ComputerGuideData.archiveFolder(id("pack:colored")).greenTint(), "folder icon can use normal colors");
        check(ComputerGuideData.archiveFolder(id("pack:child")).greenTint(), "subfolder has an independent default tint");
        check(!ComputerGuideData.archiveFolder(id("pack:colored_child")).greenTint(), "subfolder icon can use normal colors");
        var promoted = ComputerGuideData.archiveFolder(id("pack:promoted"));
        check(promoted.parent() == null && !promoted.greenTint(), "invalid-parent repair preserves folder tint");
        var definitions = ComputerGuideData.archiveDefinitions();
        ComputerGuideData.applyNetworkSnapshot(definitions.json());
        check(!ComputerGuideData.archiveFolder(id("pack:colored")).greenTint()
                && !ComputerGuideData.archiveFolder(id("pack:colored_child")).greenTint()
                && ComputerGuideData.archiveFolder(id("pack:child")).greenTint(), "folder tint survives server sync");
        ComputerGuideData.applyNetworkSnapshot(snapshot.replace("\"green_tint\":false", "\"green_tint\":true"));
        check(!ComputerGuideData.archiveDefinitions().hash().equals(definitions.hash()), "tint changes invalidate definition cache");
        check(new ComputerGuideData.ArchiveFolder(id("pack:legacy"), "legacy", "minecraft:book", 0).greenTint(), "legacy folder constructor defaults to green");
    }

    private static void nestedFolders() throws Exception {
        String snapshot = """
                {
                  "showIntroductionEntry": false,
                  "unlockAllArchiveEntries": false,
                  "archive_folders": [
                    {"id":"pack:research","title":"literal:Research","sort_order":10},
                    {"id":"pack:creatures","title":"literal:Creatures","parent":"pack:research","sort_order":10},
                    {"id":"pack:plants","title":"literal:Plants","parent":"pack:research","sort_order":5},
                    {"id":"pack:hidden","title":"literal:Hidden","parent":"pack:research","sort_order":-10},
                    {"id":"pack:chapter","title":"literal:Chapter","sort_order":5}
                  ],
                  "entries": [
                    {"id":"other:moth","title":"literal:Moth","description":["literal:field discovery"],"folder":"pack:creatures","unlocked_by_default":true},
                    {"id":"pack:fern","title":"literal:Fern","description":["literal:field discovery"],"folder":"pack:plants","unlocked_by_default":true},
                    {"id":"pack:locked","title":"literal:Locked","folder":"pack:hidden"},
                    {"id":"pack:chapter_entry","title":"literal:Chapter","folder":"pack:chapter","unlocked_by_default":true},
                    {"id":"pack:loose","title":"literal:Loose","unlocked_by_default":true}
                  ]
                }
                """;
        ComputerGuideData.applyNetworkSnapshot(snapshot);
        var definitions = ComputerGuideData.archiveDefinitions();
        ComputerGuideData.applyNetworkSnapshot(definitions.json());
        check(ComputerGuideData.archiveFolder(id("pack:creatures")).parent().equals(id("pack:research")), "parent survives sync round trip");
        check(ComputerGuideData.archiveFolderPath(id("pack:creatures")).equals(List.of(id("pack:research"), id("pack:creatures"))), "folder path includes both levels");
        check(ComputerGuideData.archiveFolderPath(id("pack:missing")).isEmpty(), "missing folder has no path");
        check(!ComputerGuideData.archiveFolderContains(id("pack:plants"), id("pack:creatures")), "siblings have separate scopes");

        ArchiveApp app = new ArchiveApp();
        List<?> root = browse(app, available());
        check(root.size() == 3 && folder(root.get(0)).id().equals(id("pack:chapter")), "root shows top-level folders before loose entries");
        check(folder(root.get(1)).id().equals(id("pack:research")) && count(root.get(1)) == 2, "descendant entries reveal parent and contribute to its count");
        app.state.search = "field";
        List<?> globalSearch = browse(app, available());
        check(globalSearch.size() == 2, "global search includes both nested folders");
        check(invoke(globalSearch.get(0), "subtitle").equals("Research > Plants"), "search identifies complete folder path");

        app.state.search = "";
        app.state.scroll = 4;
        invoke(app, "openFolder", ResourceLocation.class, id("pack:research"));
        List<?> parent = browse(app, available());
        check(parent.size() == 2 && folder(parent.get(0)).id().equals(id("pack:plants")) && folder(parent.get(1)).id().equals(id("pack:creatures")),
                "parent shows ordered subfolders without leaking their entries");
        check(count(parent.get(0)) == 1 && count(parent.get(1)) == 1, "subfolder counts exclude locked entries");
        app.state.search = "field";
        check(browse(app, available()).size() == 2, "parent search includes descendants");
        app.state.search = "loose";
        check(browse(app, available()).isEmpty(), "parent search excludes root entries");

        app.state.search = "moth";
        app.state.scroll = 2;
        invoke(app, "openFolder", ResourceLocation.class, id("pack:creatures"));
        List<?> child = browse(app, available());
        check(child.size() == 1 && entry(child.get(0)).id().equals(id("other:moth")), "subfolder contains only its direct entries across namespaces");
        app.state.search = "fern";
        check(browse(app, available()).isEmpty(), "subfolder search excludes siblings");
        app.state.search = "moth";
        app.state.scroll = 1;
        app.state.entryId = id("other:moth");
        check(app.key(null, GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape leaves nested entry");
        check(app.state.folderId.equals(id("pack:creatures")) && app.state.search.equals("moth") && app.state.scroll == 1,
                "entry back preserves subfolder state");
        app.state.searchFocused = true;
        check(app.key(null, GLFW.GLFW_KEY_ESCAPE, 0, 0) && !app.state.searchFocused && app.state.folderId.equals(id("pack:creatures")),
                "Escape unfocuses search before navigating up");
        check(app.key(null, GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape leaves subfolder");
        check(app.state.folderId.equals(id("pack:research")) && app.state.search.equals("moth") && app.state.scroll == 2,
                "subfolder back restores parent state");
        check(app.key(null, GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape leaves parent folder");
        check(app.state.folderId == null && app.state.search.isEmpty() && app.state.scroll == 4, "parent back restores root state");

        invoke(app, "openFolder", ResourceLocation.class, id("pack:research"));
        app.state.search = "field";
        app.state.scroll = 3;
        invoke(app, "openFolder", ResourceLocation.class, id("pack:creatures"));
        app.state.entryId = id("other:moth");
        clickBreadcrumb(app, id("pack:research"), false);
        check(app.state.entryId == null && app.state.folderId.equals(id("pack:research")) && app.state.search.equals("field") && app.state.scroll == 3,
                "parent breadcrumb leaves entry and restores parent state");
        invoke(app, "openFolder", ResourceLocation.class, id("pack:creatures"));
        clickBreadcrumb(app, null, false);
        check(app.state.folderId == null && app.state.scroll == 4 && app.state.history.isEmpty(), "root breadcrumb skips both levels and restores root");

        invoke(app, "openFolder", ResourceLocation.class, id("pack:research"));
        invoke(app, "openFolder", ResourceLocation.class, id("pack:creatures"));
        clickBreadcrumb(app, null, true);
        check(app.state.folderId.equals(id("pack:research")), "back button moves up one level");
        app.state.search = "";
        List<?> unlocked = browse(app, ComputerGuideData.entriesFor(List.of(), List.of(id("pack:locked"))));
        check(unlocked.size() == 3 && folder(unlocked.get(0)).id().equals(id("pack:hidden")), "unlocking descendant reveals subfolder in parent");

        var mixed = JsonParser.parseString(snapshot).getAsJsonObject();
        mixed.getAsJsonArray("entries").add(JsonParser.parseString("{\"id\":\"pack:direct\",\"title\":\"literal:Direct\",\"folder\":\"pack:research\",\"unlocked_by_default\":true}"));
        ComputerGuideData.applyNetworkSnapshot(mixed.toString());
        parent = browse(app, available());
        check(parent.size() == 3 && folder(parent.get(0)) != null && folder(parent.get(1)) != null && entry(parent.get(2)).id().equals(id("pack:direct")),
                "parent mixes subfolders and direct entries with folders first");
        invoke(app, "openFolder", ResourceLocation.class, id("pack:creatures"));
        mixed.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("unlocked_by_default", false);
        ComputerGuideData.applyNetworkSnapshot(mixed.toString());
        browse(app, available());
        check(app.state.folderId.equals(id("pack:research")), "hidden subfolder returns to parent when siblings remain visible");
        for (var value : mixed.getAsJsonArray("entries")) value.getAsJsonObject().addProperty("unlocked_by_default", false);
        ComputerGuideData.applyNetworkSnapshot(mixed.toString());
        browse(app, available());
        check(app.state.folderId == null, "loss of all subtree entries returns through hidden ancestors to root");

        ComputerGuideData.applyNetworkSnapshot(snapshot);
        app = new ArchiveApp();
        app.state.search = "root query";
        app.state.scroll = 7;
        invoke(app, "openFolder", ResourceLocation.class, id("pack:research"));
        invoke(app, "openFolder", ResourceLocation.class, id("pack:creatures"));
        ComputerGuideData.applyNetworkSnapshot(snapshot.replace("\"parent\":\"pack:research\",\"sort_order\":10", "\"parent\":\"pack:chapter\",\"sort_order\":10"));
        browse(app, available());
        check(app.state.folderId.equals(id("pack:creatures")), "visible folder remains open after reparenting");
        invoke(app, "goUp");
        check(app.state.folderId.equals(id("pack:chapter")) && app.state.search.isEmpty(), "back uses reloaded parent rather than stale history");
        invoke(app, "goUp");
        check(app.state.folderId == null && app.state.search.equals("root query") && app.state.scroll == 7,
                "reparenting retains saved root state");
    }

    private static void invalidHierarchy() throws Exception {
        String snapshot = """
                {
                  "archive_folders": [
                    {"id":"pack:root"},
                    {"id":"pack:child","parent":"pack:root"},
                    {"id":"pack:deep","parent":"pack:child"},
                    {"id":"pack:deeper","parent":"pack:deep"},
                    {"id":"pack:self","parent":"pack:self"},
                    {"id":"pack:missing","parent":"pack:absent"},
                    {"id":"pack:orphan_child","parent":"pack:missing"},
                    {"id":"pack:a","parent":"pack:b"},
                    {"id":"pack:b","parent":"pack:c"},
                    {"id":"pack:c","parent":"pack:a"},
                    {"id":"pack:cycle_child","parent":"pack:a"},
                    {"id":"pack:invalid","parent":"Bad ID!"}
                  ],
                  "entries": [
                    {"id":"pack:recoverable","title":"literal:Recovered","folder":"pack:deep","unlocked_by_default":true}
                  ]
                }
                """;
        ComputerGuideData.applyNetworkSnapshot(snapshot);
        for (String folder : List.of("deep", "deeper", "self", "missing", "orphan_child", "a", "b", "c", "cycle_child", "invalid")) {
            check(ComputerGuideData.archiveFolder(id("pack:" + folder)).parent() == null, "invalid relationship is promoted to root: " + folder);
        }
        check(ComputerGuideData.archiveFolder(id("pack:child")).parent().equals(id("pack:root")), "valid parent survives unrelated errors");
        var definitions = ComputerGuideData.archiveDefinitions();
        ComputerGuideData.applyNetworkSnapshot(definitions.json());
        check(ComputerGuideData.archiveDefinitions().json().equals(definitions.json()), "hierarchy normalization is stable across server sync");
        ArchiveApp app = new ArchiveApp();
        List<?> root = browse(app, available());
        check(root.size() == 1 && folder(root.get(0)).id().equals(id("pack:deep")), "invalid folder remains accessible at root");
        invoke(app, "openFolder", ResourceLocation.class, id("pack:deep"));
        check(entry(browse(app, available()).get(0)).id().equals(id("pack:recoverable")), "promotion retains assigned entries");

        var reversed = JsonParser.parseString(snapshot).getAsJsonObject();
        var folders = reversed.getAsJsonArray("archive_folders");
        var reverse = new com.google.gson.JsonArray();
        for (int index = folders.size() - 1; index >= 0; index--) reverse.add(folders.get(index));
        reversed.add("archive_folders", reverse);
        ComputerGuideData.applyNetworkSnapshot(reversed.toString());
        check(ComputerGuideData.archiveDefinitions().json().equals(definitions.json()), "validation does not depend on load order");
    }

    @SuppressWarnings("unchecked")
    private static void clickBreadcrumb(ArchiveApp app, ResourceLocation folder, boolean back) throws Exception {
        var field = ArchiveApp.class.getDeclaredField("breadcrumbButtons");
        field.setAccessible(true);
        List<Object> buttons = (List<Object>) field.get(app);
        buttons.clear();
        var type = Class.forName(ArchiveApp.class.getName() + "$BreadcrumbButton");
        var constructor = type.getDeclaredConstructor(int.class, int.class, int.class, int.class, ResourceLocation.class, boolean.class);
        constructor.setAccessible(true);
        buttons.add(constructor.newInstance(10, 20, 60, 33, folder, back));
        Method method = ArchiveApp.class.getDeclaredMethod("clickBreadcrumb", double.class, double.class);
        method.setAccessible(true);
        check(!(Boolean) method.invoke(app, 5.0, 25.0), "click outside breadcrumb does not navigate");
        check((Boolean) method.invoke(app, 30.0, 25.0), "breadcrumb hitbox accepts click");
    }

    private static List<ComputerGuideData.Entry> available() {
        return ComputerGuideData.entriesFor(List.of(), List.of());
    }

    private static void uiLayout() throws Exception {
        for (int width : new int[]{214, 284, 396}) {
            for (int height : new int[]{176, 196, 204}) {
                for (boolean gallery : new boolean[]{false, true}) {
                    for (int folders : new int[]{0, 1, 5}) {
                        for (int entries : new int[]{0, 1, 20}) {
                            ArchiveLayout layout = ArchiveLayout.create(width, height, gallery, folders, entries);
                            check(layout.cells().size() == folders + entries, "all entries have one layout cell");
                            check(layout.viewportHeight() > 0 && layout.maximumScroll() >= 0, "viewport and scroll range are valid");
                            for (ArchiveLayout.Cell cell : layout.cells()) {
                                check(cell.x() >= 0 && cell.x() + cell.width() <= width - 8, "cells fit horizontally");
                                check(cell.y() >= 14 && cell.y() + cell.height() <= layout.contentHeight(), "cells fit content height");
                                int scroll = layout.reveal(cell.index(), 0);
                                check(scroll >= 0 && scroll <= layout.maximumScroll(), "focus reveal stays in scroll range");
                                check(cell.y() >= scroll && cell.y() + cell.height() <= scroll + layout.viewportHeight(), "focused cell is fully visible");
                                for (ArchiveLayout.Section section : layout.sections())
                                    check(cell.y() >= section.y() + ArchiveLayout.HEADING || cell.y() + cell.height() <= section.y(), "headings never overlap cells");
                            }
                            for (int index = 1; index < layout.cells().size(); index++) {
                                var first = layout.cells().get(index - 1);
                                var second = layout.cells().get(index);
                                check(first.y() != second.y() || first.x() + first.width() <= second.x(), "peer cells never overlap");
                            }
                        }
                    }
                }
            }
        }
        ArchiveLayout list = ArchiveLayout.create(284, 196, false, 2, 20);
        ArchiveLayout grid = ArchiveLayout.create(396, 204, true, 2, 20);
        check(list.columns() == 1 && grid.columns() == 3, "view mode controls columns independently of maximizing");
        check(grid.move(0, 1, 0) == 1 && grid.move(1, 1, 0) == 1, "horizontal navigation stays in its group row");
        check(grid.move(1, 0, 1) == 3 && grid.move(3, 0, -1) == 1, "vertical gallery navigation crosses section headings by column");
        check(list.move(-1, 0, 1) == 0 && list.move(0, 0, -1) == 0, "navigation handles missing focus and bounds");

        ComputerGuideData.applyNetworkSnapshot(SNAPSHOT);
        ArchiveApp app = new ArchiveApp();
        List<?> items = browse(app, available());
        set(app, "visibleItems", items);
        set(app, "layout", ArchiveLayout.create(284, 196, false, 2, 2));
        check(app.key(null, GLFW.GLFW_KEY_END, 0, 0), "End focuses last browser item");
        check(app.state.focusKey.equals("entry:pack:missing"), "keyboard focus uses stable entry identity");
        check(app.key(null, GLFW.GLFW_KEY_HOME, 0, 0), "Home focuses first browser item");
        check(app.state.focusKey.equals("folder:pack:chapter"), "keyboard focus includes folders");
        app.state.scroll = 4;
        app.state.search = "research";
        invoke(app, "openFolder", ResourceLocation.class, id("pack:research"));
        invoke(app, "goBack");
        check(app.state.folderId == null && app.state.search.equals("research") && app.state.scroll == 4
                && app.state.focusKey.equals("folder:pack:chapter"), "Back restores search, scroll, and focus");
        app.state.search = "";
        invoke(app, "openFolder", ResourceLocation.class, id("pack:research"));
        app.state.focusKey = "entry:pack:z_key";
        app.state.gallery = true;
        app.state.search = "alpha";
        app.state.scroll = 11;
        invoke(app, "rememberView");
        app.state.entryId = id("pack:z_key");
        app.state.detailScroll = 8;
        set(app, "openedEntry", id("pack:z_key"));
        invoke(app, "goBack");
        check(app.state.entryId == null && app.state.gallery && app.state.scroll == 11 && app.state.search.equals("alpha")
                && app.state.focusKey.equals("entry:pack:z_key"), "entry Back restores the exact gallery search view");
        check(app.key(null, GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL) && app.state.searchFocused, "Ctrl+F focuses search");
        app.state.search = "test\uD83D\uDC1C";
        app.key(null, GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        check(app.state.search.equals("test"), "Backspace removes an entire Unicode code point");
        invoke(app, "setGallery", boolean.class, false);
        check(!app.state.gallery && app.state.focusKey.equals("entry:pack:z_key"), "view changes retain focused identity");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static List<?> browse(ArchiveApp app, List<ComputerGuideData.Entry> entries) throws Exception {
        return (List<?>) invoke(app, "browseItems", List.class, entries);
    }

    private static ComputerGuideData.Entry entry(Object item) throws Exception {
        return (ComputerGuideData.Entry) invoke(item, "entry");
    }

    private static ComputerGuideData.ArchiveFolder folder(Object item) throws Exception {
        return (ComputerGuideData.ArchiveFolder) invoke(item, "folder");
    }

    private static int count(Object item) throws Exception {
        return (Integer) invoke(item, "count");
    }

    private static Object invoke(Object target, String name, Class<?> type, Object value) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        return method.invoke(target, value);
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static ResourceLocation id(String id) {
        return new ResourceLocation(id);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
