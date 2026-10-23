package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.AnternetAccountClientState;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.client.ComputerWorkspaceClientKey;
import com.craisinlord.antos.content.computer.paint.AntPaintCanvas;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountResultPayload;
import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.lwjgl.glfw.GLFW;

public final class DesktopQolAuditTest {
    public static void main(String[] args) throws Exception {
        UUID account = UUID.randomUUID();
        AnternetAccountClientState.update(new AnternetAccountResultPayload(AnternetAccountResultPayload.SUCCESS, account, account, "QolAudit"));
        textRecovery();
        paintRecovery();
        archiveCollections();
        System.out.println("Desktop QoL audit checks passed");
    }

    private static void textRecovery() throws Exception {
        ComputerFileSystemClientState.clearAll();
        TextEditorApp app = new TextEditorApp();
        JsonObject draft = JsonParser.parseString("""
                {"path":"/documents/notes.txt","content":"unsaved notes","saved":"saved notes","cursor":999}
                """).getAsJsonObject();
        app.state.pendingOpenPath = "/documents/notes.txt";
        invoke(app, "recoverDraft", JsonObject.class, draft);
        check(app.state.content.equals("unsaved notes") && app.state.savedContent.equals("saved notes") && app.state.dirty, "Recovery retains unsaved text and its saved baseline");
        check(app.state.cursor == app.state.content.length() && app.state.pendingOpenPath.isEmpty(), "Recovered caret is valid and automatic resume requests cannot replace the draft");
        openResponse("/documents/notes.txt", "server copy");
        app.sync();
        check(app.state.content.equals("unsaved notes") && app.state.dirty, "Late file responses cannot overwrite recovered text");
        try {
            invoke(app, "recoverDraft", JsonObject.class, new JsonObject());
            throw new AssertionError("Incomplete text draft must be rejected");
        } catch (InvocationTargetException exception) {
            check(exception.getCause() instanceof IllegalArgumentException, "Invalid text drafts fail safely");
        }
        check(app.state.content.equals("unsaved notes"), "A failed recovery leaves the current document intact");
    }

    private static void paintRecovery() throws Exception {
        ComputerFileSystemClientState.clearAll();
        PaintApp app = new PaintApp();
        AntPaintCanvas canvas = new AntPaintCanvas();
        canvas.set(7, 9, true);
        AntPaintCanvas baseline = new AntPaintCanvas();
        baseline.set(2, 3, true);
        JsonObject draft = new JsonObject();
        draft.addProperty("path", "/pictures/sketch.antpaint");
        draft.addProperty("name", "sketch.antpaint");
        draft.addProperty("file_id", UUID.randomUUID().toString());
        draft.addProperty("pixels", Base64.getEncoder().encodeToString(canvas.pack()));
        draft.addProperty("saved_pixels", Base64.getEncoder().encodeToString(baseline.pack()));
        app.state.pendingOpenPath = "/pictures/sketch.antpaint";
        invoke(app, "recoverDraft", JsonObject.class, draft);
        check(app.state.canvas.get(7, 9) && !app.state.canvas.get(2, 3) && app.state.dirty, "Recovery preserves the unsaved painting");
        check(java.util.Arrays.equals(app.state.savedPixels, baseline.copyPixels()), "Recovery preserves Paint's saved baseline for undo and dirty tracking");
        openResponse("/pictures/sketch.antpaint", "late server copy");
        app.sync();
        check(app.state.canvas.get(7, 9) && app.state.dirty, "Late file responses cannot replace a recovered painting");
        draft.addProperty("saved_pixels", "corrupted");
        try {
            invoke(app, "recoverDraft", JsonObject.class, draft);
            throw new AssertionError("Corrupted paint draft must be rejected");
        } catch (InvocationTargetException exception) {
            check(exception.getCause() instanceof IllegalArgumentException, "Invalid paint drafts fail safely");
        }
        check(app.state.canvas.get(7, 9) && java.util.Arrays.equals(app.state.savedPixels, baseline.copyPixels()), "Failed painting recovery is atomic");
    }

    private static void archiveCollections() throws Exception {
        ComputerGuideData.applyNetworkSnapshot("""
                {"showIntroductionEntry":false,"unlockAllArchiveEntries":false,"entries":[
                  {"id":"audit:alpha","title":"literal:Alpha","unlocked_by_default":true},
                  {"id":"audit:beta","title":"literal:Beta","unlocked_by_default":true},
                  {"id":"audit:locked","title":"literal:Secret"}]}
                """);
        String workspace = ComputerWorkspaceClientKey.of();
        AntOSSettings.toggleArchiveBookmark(workspace, "audit:alpha");
        AntOSSettings.toggleArchiveBookmark(workspace, "audit:locked");
        ArchiveApp app = new ArchiveApp();
        app.loadResume(JsonParser.parseString("{\"favorites\":true,\"search\":\"alpha\",\"scroll\":40,\"detail_scroll\":25}").getAsJsonObject());
        List<?> rows = browse(app);
        check(rows.size() == 1 && entryId(rows.getFirst()).equals("audit:alpha"), "Bookmarks only expose accessible matching articles");
        check(app.state.detailScroll == 25, "Article scroll is restored before layout measurement");
        AntOSSettings.toggleArchiveBookmark(workspace, "audit:alpha");
        check(browse(app).isEmpty(), "Removing a bookmark invalidates the existing browse cache");
        app.state.searchFocused = false;
        check(app.key(null, GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape handles a restored bookmark view");
        check(!app.state.favoritesOnly && app.state.search.isEmpty() && app.state.scroll == 0, "Escape returns to an unfiltered Archive root");
        AntOSSettings.recordArchiveEntry(workspace, "audit:beta");
        AntOSSettings.recordArchiveEntry(workspace, "audit:alpha");
        AntOSSettings.recordArchiveEntry(workspace, "audit:locked");
        invoke(app, "toggleCollection", boolean.class, false);
        rows = browse(app);
        check(rows.size() == 2 && entryId(rows.getFirst()).equals("audit:alpha") && entryId(rows.getLast()).equals("audit:beta"), "Recent articles retain visit order and hide locked content");
        AntOSSettings.recordArchiveEntry(workspace, "audit:beta");
        check(entryId(browse(app).getFirst()).equals("audit:beta"), "Revisiting an article updates an already cached recent view");
        check(app.key(null, GLFW.GLFW_KEY_ESCAPE, 0, 0) && !app.state.recentOnly, "Escape restores the view before Recent");
    }

    private static List<?> browse(ArchiveApp app) throws Exception {
        return (List<?>) invoke(app, "browseItems", List.class, ComputerGuideData.entriesFor(List.of(), List.of()));
    }

    private static String entryId(Object row) throws Exception {
        Method method = row.getClass().getDeclaredMethod("entry");
        method.setAccessible(true);
        return ((ComputerGuideData.Entry) method.invoke(row)).id().toString();
    }

    private static Object invoke(Object target, String name, Class<?> type, Object value) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        return method.invoke(target, value);
    }

    private static void openResponse(String path, String content) {
        ComputerFileSystemClientState.update(new ComputerAccessResultPayload(ComputerAccessResultPayload.SUCCESS, false, true,
                ComputerAccessPayload.FILE_OPEN + "\0\0" + path + "\0" + content));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
