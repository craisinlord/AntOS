package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.network.AntmailNetworking;
import com.craisinlord.antos.content.network.AntmailAnternetResultPayload;
import net.minecraft.client.Minecraft;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.network.chat.Component;

final class AntmailDeliveryPanel {
    private AntmailMessage message;
    private int scroll;
    private boolean pending;
    private long requestedAt;
    private AntmailAnternetResultPayload baseline;
    private String error = "";
    private boolean blocked;
    void open(AntmailMessage message) { this.message = message; scroll = 0; pending = false; error = ""; blocked = false; baseline = AntmailClientState.getOperationResult(); }
    void scroll(double amount) { scroll = Math.max(0, scroll - (int) Math.signum(amount)); }
    private boolean canRetry() { return !blocked && !message.batch().cancelled() && message.batch().deliveries().stream().anyMatch(row -> !row.successful() && !row.status().equals("MEMBERSHIP_CHANGED")); }
    void sync() {
        var result = AntmailClientState.getOperationResult();
        if (result != null && result != baseline && result.messageId().equals(message.id().toString())) {
            baseline = result; pending = false;
            if (result.status() > 1) {
                String key = switch (result.detail()) { case "membership_changed" -> "retry_membership_changed"; case "rate_limited" -> "retry_rate_limited"; case "not_found", "send_deleted" -> "retry_deleted"; default -> "retry_failed_status"; };
                error = Component.translatable("computer.antos.mail." + key).getString();
                blocked = result.detail().equals("membership_changed") || result.detail().equals("not_found") || result.detail().equals("send_deleted");
            }
        }
    }
    void render(ComputerScreen screen, ThemedGraphics g, int x, int y, int w, int h) {
        sync();
        var updated = AntmailClientState.getMessage(message.id());
        if (updated != null && updated != message && updated.batch() != null) { message = updated; pending = false; }
        if (pending && System.nanoTime() - requestedAt > 5_000_000_000L) pending = false;
        screen.box(g, x, y, x + 40, y + 18, Ui.FOCUS);
        screen.drawTabLabel(g, Component.translatable("computer.antos.mail.back").getString(), x, y - 1, 40, Ui.TEXT);
        g.drawString(Minecraft.getInstance().font, screen.trimToWidth(Component.translatable("computer.antos.mail.delivery_results").getString(), w - 48), x + 46, y + 5, Ui.TEXT_STRONG, false);
        var rows = message.batch().deliveries(); int statusHeight = error.isBlank() ? 0 : 22; int count = Math.max(0, (h - 50 - statusHeight) / 24);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - count)));
        for (int i = scroll; i < Math.min(rows.size(), scroll + count); i++) {
            var row = rows.get(i); int top = y + 25 + (i - scroll) * 24;
            g.drawString(Minecraft.getInstance().font, screen.trimToWidth(row.address(), w - 5), x, top, Ui.TEXT_STRONG, false);
            String status = Component.translatable("computer.antos.mail.outcome." + row.status().toLowerCase(java.util.Locale.ROOT)).getString();
            g.drawString(Minecraft.getInstance().font, screen.trimToWidth(status, w - 5), x, top + 11, Ui.TEXT, false);
        }
        screen.drawScrollbar(g, x + w - 4, y + 25, Math.max(0, h - 50 - statusHeight), count, rows.size(), scroll, Math.max(0, rows.size() - count));
        String footer = Component.translatable("computer.antos.mail." + (pending ? "retrying" : canRetry() ? "retry_failed" : message.batch().failedCount() == 0 ? "all_delivered" : "retry_unavailable")).getString();
        if (!error.isBlank()) {
            var lines = Minecraft.getInstance().font.split(Component.literal(error), w - 8);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(Minecraft.getInstance().font, lines.get(i), x + 4, y + h - 40 + i * 10, Ui.CORRUPT, false);
        }
        if (canRetry()) screen.box(g, x, y + h - 18, x + w, y + h, pending ? Ui.LINE : Ui.FOCUS);
        g.drawString(Minecraft.getInstance().font, screen.trimToWidth(footer, w - 8), x + 4, y + h - 13, Ui.TEXT, false);
    }
    void retry() {
        if (!canRetry() || pending) return;
        pending = true; requestedAt = System.nanoTime(); error = ""; baseline = AntmailClientState.getOperationResult();
        AntmailNetworking.retry(message.id()); AntmailNetworking.requestMessage(message.id());
    }
}
