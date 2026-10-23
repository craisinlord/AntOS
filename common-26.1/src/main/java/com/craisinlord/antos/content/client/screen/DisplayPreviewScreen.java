package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

public final class DisplayPreviewScreen extends Screen {
    private final String id;
    private ComputerScreen host;
    private JsonObject document;
    private JsonArray diagnostics = new JsonArray();
    private boolean bounds, report;
    private int size, reportOffset;

    private DisplayPreviewScreen(String id) { super(Component.translatable("computer.antos.display.author_preview")); this.id = id; }

    public static void receive(String json) {
        try {
            JsonObject message = JsonParser.parseString(json).getAsJsonObject();
            String id = message.get("id").getAsString();
            var client = Minecraft.getInstance();
            DisplayPreviewScreen screen = client.screen instanceof DisplayPreviewScreen preview && preview.id.equals(id) ? preview : new DisplayPreviewScreen(id);
            screen.document = message.has("document") ? message.getAsJsonObject("document") : null;
            screen.diagnostics = message.has("diagnostics") ? message.getAsJsonArray("diagnostics") : new JsonArray();
            screen.reportOffset = Math.min(screen.reportOffset, Math.max(0, screen.diagnostics.size() - 1));
            if (screen.document != null) for (var diagnostic : DisplayAuthorDiagnostics.inspect(id, screen.document, 320)) screen.diagnostics.add(diagnostic.json());
            if (client.screen != screen) client.setScreen(screen);
            screen.host.displays.preview(id, screen.document);
        } catch (RuntimeException exception) { com.craisinlord.antos.AntOS.LOGGER.warn("Invalid display preview response: {}", exception.toString()); }
    }

    @Override protected void init() {
        if (host == null) host = new ComputerScreen(true);
        host.init(width, height); host.displays.preview(id, document);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor source, int mouseX, int mouseY, float partialTick) {
        Ui.refresh();
        ThemedGraphics g = new com.craisinlord.antos.content.client.ThemedGraphics(source);
        float scale = host.uiScale();
        int mx = (int) ((mouseX - width / 2F) / scale), my = (int) ((mouseY - height / 2F) / scale);
        host.session.mouseX = mx; host.session.mouseY = my;
        host.displays.beginFrame(); host.displays.boundsOverlay(bounds);
        g.pose().pushPose(); g.pose().translate(width / 2F, height / 2F, 0); g.pose().scale(scale, scale, 1);
        try {
            g.fill(-220, -143, 220, 143, Ui.BACKGROUND);
            host.box(g, -220, -143, 220, 143, Ui.BORDER_STRONG);
            g.drawString(font, Component.translatable("computer.antos.display.author_preview"), -212, -134, Ui.TEXT_STRONG, false);
            String[] controls = {tr("refresh"), tr("bounds"), tr("diagnostics"), new String[]{tr("standard"), tr("compact"), tr("expanded")}[size]};
            for (int i = 0; i < controls.length; i++) {
                int x = -211 + i * 104;
                g.fill(x, -119, x + 99, -105, host.inside(x, -119, 99, 14, mx, my) ? Ui.HOVER : Ui.PANEL);
                g.drawString(font, host.trimToWidth(controls[i], 93), x + 3, -116, Ui.TEXT, false);
            }
            g.drawString(font, host.trimToWidth(id, 413), -211, -99, Ui.TEXT_MUTED, false);
            if (report || document == null) {
                int y = -81;
                if (document == null) y = host.wrap(g, tr("preview_unavailable"), -209, y, 410, Ui.ERROR) + 8;
                else if (diagnostics.isEmpty()) g.drawString(font, tr("diagnostics_empty"), -209, y, Ui.TEXT_MUTED, false);
                for (int i = reportOffset; i < diagnostics.size() && y < 118; i++) {
                    JsonObject entry = diagnostics.get(i).getAsJsonObject();
                    y = host.wrap(g, entry.get("severity").getAsString() + " " + entry.get("path").getAsString() + ": " + entry.get("message").getAsString(), -209, y, 410, Ui.TEXT) + 5;
                }
            } else {
                int w = size == 1 ? 205 : size == 0 ? 320 : 416;
                host.displays.presentation(size == 1 ? "compact" : size == 2 ? "expanded" : "auto");
                host.displays.render(g, id, "preview/" + id, -w / 2, -83, w, -83, 134);
            }
            host.displays.renderOverlay(g);
        } finally { g.pose().popPose(); g.flush(); }
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double x = (event.x() - width / 2F) / host.uiScale(), y = (event.y() - height / 2F) / host.uiScale();
        int button = event.button();
        if (host.displays.modal()) return host.displays.click(x, y, button);
        if (button == 0 && x >= -211 && x < 200 && y >= -119 && y < -105 && (x + 211) % 104 < 99) {
            int index = (int) ((x + 211) / 104);
            if (index == 0) ComputerNetworking.previewDisplay(id);
            if (index == 1) bounds = !bounds;
            if (index == 2) report = !report;
            if (index == 3) size = (size + 1) % 3;
            return true;
        }
        return host.displays.click(x, y, button);
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) { return host.displays.drag((event.x() - width / 2F) / host.uiScale(), (event.y() - height / 2F) / host.uiScale()); }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) { host.displays.release(); return true; }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!host.displays.modal() && (report || document == null)) { reportOffset = Math.max(0, Math.min(Math.max(0, diagnostics.size() - 1), reportOffset - (int) Math.signum(dy))); return true; }
        return host.displays.scroll((x - width / 2F) / host.uiScale(), (y - height / 2F) / host.uiScale(), dy);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_F5) { ComputerNetworking.previewDisplay(id); return true; }
        if (host.displays.key(key)) return true;
        if (key == InputConstants.KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public void onClose() { ComputerNetworking.previewDisplay(""); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
    private static String tr(String key) { return Component.translatable("computer.antos.display." + key).getString(); }
}
