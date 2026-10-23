package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.palette.Ui;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Objects;
import java.util.function.Consumer;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

final class LocalDraftRecovery {
    private final ComputerApp app;
    private final AppKit kit;
    private final String kind;
    private String workspace = "";
    private String stored;
    private JsonObject pending;
    private boolean failed;

    LocalDraftRecovery(ComputerApp app, AppKit kit, String kind) {
        this.app = app;
        this.kit = kit;
        this.kind = kind;
    }

    private void load() {
        String current = app.screen.workspaceKey();
        if (workspace.equals(current)) return;
        workspace = current;
        stored = AntOSSettings.editorDraft(workspace, kind);
        pending = null;
        failed = false;
        if (stored == null || stored.isBlank()) return;
        try { pending = JsonParser.parseString(stored).getAsJsonObject(); }
        catch (RuntimeException ignored) { }
    }

    boolean active() { load(); return pending != null; }

    void checkpoint(JsonObject draft) {
        load();
        if (pending != null) return;
        String json = draft == null ? null : draft.toString();
        if (Objects.equals(stored, json)) return;
        stored = json;
        AntOSSettings.setEditorDraft(workspace, kind, json);
    }

    boolean key(int key) {
        if (!active()) return false;
        if (key == InputConstants.KEY_ESCAPE) { app.screen.onClose(); return true; }
        return key != InputConstants.KEY_TAB && key != InputConstants.KEY_RETURN && key != InputConstants.KEY_NUMPADENTER && key != InputConstants.KEY_SPACE;
    }

    void render(ThemedGraphics g, int x, int y, int w, int h, Consumer<JsonObject> recover) {
        if (!active()) return;
        kit.begin();
        g.fill(x, y, x + w, y + h, Ui.PANEL);
        int lineY = y + 10;
        for (var line : app.font.split(Component.translatable("computer.antos.recovery.message"), Math.max(1, w - 16))) {
            g.drawString(app.font, line, x + 8, lineY, Ui.TEXT, false);
            lineY += 11;
        }
        if (failed) {
            for (var line : app.font.split(Component.translatable("computer.antos.recovery.failed"), Math.max(1, w - 16))) {
                g.drawString(app.font, line, x + 8, lineY, Ui.ERROR, false);
                lineY += 11;
            }
        }
        int buttonY = Math.min(y + h - 25, lineY + 12);
        int half = Math.max(1, (w - 22) / 2);
        kit.button(g, x + 8, buttonY, half, 18, tr("restore"), true, true, () -> {
            JsonObject draft = pending;
            try { recover.accept(draft); }
            catch (RuntimeException exception) {
                com.craisinlord.antos.AntOS.LOGGER.warn("Could not recover {} draft", kind, exception);
                failed = true;
                return;
            }
            pending = null;
        });
        kit.button(g, x + 14 + half, buttonY, half, 18, tr("discard"), false, true, () -> {
            pending = null;
            stored = null;
            AntOSSettings.setEditorDraft(workspace, kind, null);
        });
    }

    private String tr(String key) { return Component.translatable("computer.antos.recovery." + key).getString(); }
}
