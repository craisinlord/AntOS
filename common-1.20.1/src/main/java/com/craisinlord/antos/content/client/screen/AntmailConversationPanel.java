package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.network.AntmailAnternetResultPayload;
import com.craisinlord.antos.content.network.AntmailNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

final class AntmailConversationPanel {
    private UUID id;
    private int page;
    private int scroll;
    private int selection;
    private int visible = 1;
    private int keyboardHit = -1;
    private CompoundTag response;
    private AntmailAnternetResultPayload baseline;
    private final List<AntmailMessage> messages = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private record Hit(int x, int y, int w, int h, Runnable action) { }
     
    private String token;
    UUID id() { return id; }
    String replyToken() { return token != null ? token : "conversation:" + id; }
     
    String latestSubject() {
        sync();
        if (!loaded()) return null;
        return messages.stream().max(java.util.Comparator.comparingLong(AntmailMessage::createdAt)).map(AntmailMessage::subject).orElse(null);
    }
    private boolean loaded() { return response != null && id.toString().equals(response.getString("Conversation")) && response.getInt("Page") == page; }
    AntmailMessage selected() { sync(); return !loaded() || messages.isEmpty() ? null : messages.get(Math.min(selection, messages.size() - 1)); }
    AntmailMessage find(UUID messageId) { return messages.stream().filter(message -> message.id().equals(messageId)).findFirst().orElse(null); }
    void move(int direction) {
        sync(); if (!loaded()) return;
        keyboardHit = -1; selection = Math.max(0, Math.min(messages.size() - 1, selection + direction));
        if (selection < scroll) scroll = selection;
        if (selection >= scroll + visible) scroll = selection - visible + 1;
    }
    private void clearPage() { messages.clear(); hits.clear(); response = null; scroll = 0; selection = 0; keyboardHit = -1; }
    void open(UUID id) { open(id, "conversation:" + id); }
    void open(UUID id, String token) { this.id = id; this.token = token; title = null; page = 0; clearPage(); refresh(); }
    void refresh() { if (id != null) { baseline = AntmailClientState.get(); AntmailNetworking.conversation(id, page); } }
    void page(boolean forward) {
        sync();
        if (forward && (!loaded() || !response.getBoolean("More")) || !forward && page == 0) return;
        page += forward ? 1 : -1; clearPage(); refresh();
    }
    void scroll(double amount) { scroll = Math.max(0, scroll - (int) Math.signum(amount)); }
    private void sync() {
        var current = AntmailClientState.conversation();
        if (id == null || current == null || current == response || !id.toString().equals(current.getString("Conversation")) || current.getInt("Page") != page) return;
        response = current; messages.clear();
        var rows = current.getList("Messages", 10);
        for (int i = 0; i < rows.size(); i++) { try { messages.add(AntmailMessage.fromTag(rows.getCompound(i))); } catch (RuntimeException ignored) { } }
        selection = Math.max(0, Math.min(selection, messages.size() - 1));
        title = null;
    }
     
    private String title;
    private String title() {
        if (title != null) return title;
        boolean team = replyToken().startsWith("team:");
        String name = messages.stream().map(AntmailMessage::conversationName).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        title = name == null ? tr(team ? "kind_team" : "kind_conversation") : team ? AntmailDirectoryPanel.teamLabel(name) : name;
        return title;
    }
    private static String tr(String key) { return Component.translatable("computer.antos.mail." + key).getString(); }
    private void button(ComputerScreen screen, GuiGraphics g, int x, int y, int w, String key, Runnable action) {
        screen.box(g, x, y, x + w, y + 18, Ui.FOCUS); screen.drawTabLabel(g, tr(key), x, y - 1, w, Ui.TEXT);
        hits.add(new Hit(x, y, w, 18, action));
    }
    void render(ComputerScreen screen, GuiGraphics g, int x, int y, int w, int h, Runnable back, Runnable reply, Consumer<AntmailMessage> open) {
        sync(); hits.clear();
        int replyWidth = Math.min(80, w - 100);
        button(screen, g, x, y, 44, "back", back);
        button(screen, g, x + 48, y, replyWidth, "reply_all", reply);
        button(screen, g, x + w - 48, y, 48, "refresh", this::refresh);
        int titleX = x + 48 + replyWidth + 6, titleWidth = x + w - 54 - titleX;
        if (titleWidth > 20 && loaded()) {
            var font = Minecraft.getInstance().font;
            String text = screen.trimToWidth(title(), titleWidth);
            g.drawString(font, text, titleX + (titleWidth - font.width(text)) / 2, y + 5, Ui.TEXT_STRONG, false);
        }
        if (!loaded()) {
            var result = AntmailClientState.get();
            String state = result != null && result != baseline && "conversation_error".equals(result.detail()) ? "load_failed" : "loading";
            g.drawString(Minecraft.getInstance().font, screen.trimToWidth(tr(state), w), x, y + 27, Ui.TEXT_PLACEHOLDER, false);
        } else {
            visible = Math.max(0, (h - 48) / 30); scroll = Math.max(0, Math.min(scroll, Math.max(0, messages.size() - visible)));
            for (int i = scroll; i < Math.min(messages.size(), scroll + visible); i++) {
                AntmailMessage message = messages.get(i); int rowY = y + 25 + (i - scroll) * 30;
                if (i == selection) g.fill(x, rowY - 1, x + w - 5, rowY + 25, Ui.HOVER);
                g.drawString(Minecraft.getInstance().font, screen.trimToWidth(message.displaySender(), w - 5), x, rowY, Ui.TEXT_STRONG, false);
                g.drawString(Minecraft.getInstance().font, screen.trimToWidth(message.subject(), w - 5), x, rowY + 11, Ui.TEXT, false);
                hits.add(new Hit(x, rowY, w - 5, 27, () -> open.accept(message)));
            }
            if (messages.isEmpty()) g.drawString(Minecraft.getInstance().font, tr("empty_conversation"), x, y + 27, Ui.TEXT_PLACEHOLDER, false);
            screen.drawScrollbar(g, x + w - 4, y + 25, Math.max(0, h - 48), visible, messages.size(), scroll, Math.max(0, messages.size() - visible));
            if (page > 0) button(screen, g, x, y + h - 18, 50, "previous", () -> page(false));
            if (response.getBoolean("More")) button(screen, g, x + w - 50, y + h - 18, 50, "next", () -> page(true));
        }
        if (keyboardHit >= 0 && keyboardHit < hits.size()) { Hit focus = hits.get(keyboardHit); screen.box(g, focus.x - 1, focus.y - 1, focus.x + focus.w + 1, focus.y + focus.h + 1, Ui.ACCENT); }
    }
    boolean key(int key, int modifiers) {
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) { page(key == GLFW.GLFW_KEY_PAGE_DOWN); return true; }
        if (key == GLFW.GLFW_KEY_F5) { refresh(); return true; }
        if (key == GLFW.GLFW_KEY_TAB) { int delta = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1; keyboardHit = hits.isEmpty() ? -1 : Math.floorMod(keyboardHit + delta, hits.size()); return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && keyboardHit >= 0 && keyboardHit < hits.size()) { hits.get(keyboardHit).action.run(); return true; }
        return false;
    }
    boolean click(double x, double y) {
        keyboardHit = -1;
        for (Hit hit : hits) if (x >= hit.x && y >= hit.y && x < hit.x + hit.w && y < hit.y + hit.h) { hit.action.run(); return true; }
        return true;
    }
}
