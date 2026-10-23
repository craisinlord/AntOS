package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.config.NotificationHistory;
import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.content.client.ComputerNotificationClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class NotificationPanel extends ComputerApp {
    static final int BELL_SIZE = 17;
    private static final int WIDTH = 236;
    private static final int ROW_HEIGHT = 26;
    private static final int MAX_ROWS = 6;
    private static final String[] FILTERS = {"all", "mail", "task", "antazon"};
    private static final String[] BELL = {
            ".....#.....",
            "....###....",
            "...#####...",
            "..#######..",
            "..#######..",
            "..#######..",
            ".#########.",
            "###########",
            "...........",
            "....###....",
            ".....#....."
    };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    final AppKit kit = new AppKit(this);
    private boolean open;
    private boolean confirmClear;
    private String filter = "all";
    private int offset;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    boolean isOpen() { return open; }

    boolean contains(double mouseX, double mouseY) {
        return open && ComputerScreen.inside(panelX, panelY, panelW, panelH, mouseX, mouseY);
    }

    void open() {
        open = true;
        confirmClear = false;
        offset = 0;
    }

    void close() {
        open = false;
        confirmClear = false;
    }

    void toggle() {
        if (open) close(); else open();
    }

    static int bellX(int l, int desktopWidth) { return l + desktopWidth - 76; }

    static int bellY(int t) { return t + 11; }

    void renderBell(GuiGraphics g, int l, int t) {
        int x = bellX(l, screen.desktopWidth());
        int y = bellY(t);
        boolean hover = screen.hovered(x, y, BELL_SIZE, BELL_SIZE);
        g.fill(x, y, x + BELL_SIZE, y + BELL_SIZE, open ? Ui.TEXT : hover ? Ui.HOVER : Ui.SURFACE);
        screen.box(g, x, y, x + BELL_SIZE, y + BELL_SIZE, open || hover ? Ui.ACCENT : Ui.LINE);
        int color = open ? Ui.BACKGROUND : Ui.ACCENT;
        for (int row = 0; row < BELL.length; row++) {
            String line = BELL[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) == '#') g.fill(x + 3 + column, y + 3 + row, x + 4 + column, y + 4 + row, color);
            }
        }
        long unread = ComputerNotificationClientState.unreadCount();
        if (unread <= 0) return;
        String count = unread > 9 ? "9+" : Long.toString(unread);
        float scale = 0.6F;
        int badgeW = Math.max(7, Math.round(font.width(count) * scale) + 4);
        int badgeX = x + BELL_SIZE - badgeW + 3;
        int badgeY = y - 2;
        g.fill(badgeX - 1, badgeY - 1, badgeX + badgeW + 1, badgeY + 8, Ui.BACKGROUND);
        g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 7, Ui.ACCENT);
        g.pose().pushPose();
        try {
            g.pose().translate(badgeX + badgeW / 2.0F, badgeY + 1.5F, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, Component.literal(count), -font.width(count) / 2, 0, Ui.BACKGROUND, false);
        } finally {
            g.pose().popPose();
        }
    }

    boolean bellClicked(int l, int t, double mouseX, double mouseY) {
        if (!ComputerScreen.inside(bellX(l, screen.desktopWidth()), bellY(t), BELL_SIZE, BELL_SIZE, mouseX, mouseY)) return false;
        AntOSUiSounds.action(this::toggle);
        return true;
    }

    void render(GuiGraphics g, int l, int t) {
        kit.begin();
        if (!open) return;
        List<NotificationHistory.Entry> entries = filtered();
        offset = Math.max(0, Math.min(offset, Math.max(0, entries.size() - MAX_ROWS)));
        int rows = Math.max(1, Math.min(MAX_ROWS, entries.size()));
        panelW = Math.min(WIDTH, screen.desktopWidth() - 24);
        panelX = bellX(l, screen.desktopWidth()) + BELL_SIZE - panelW;
        panelY = t + 34;
        panelH = 22 + 21 + rows * ROW_HEIGHT + 26;
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, Ui.BACKGROUND);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, Ui.PANEL);
        screen.box(g, panelX, panelY, panelX + panelW, panelY + panelH, Ui.BORDER_STRONG);
        kit.block(panelX, panelY, panelW, panelH);
        renderHeader(g);
        renderFilters(g);
        int listY = panelY + 43;
        int listH = rows * ROW_HEIGHT;
        if (entries.isEmpty()) {
            String empty = tr("empty");
            g.drawString(font, Component.literal(screen.trimToWidth(empty, panelW - 16)), panelX + (panelW - Math.min(panelW - 16, font.width(empty))) / 2,
                    listY + (ROW_HEIGHT - 8) / 2, Ui.TEXT_MUTED, false);
        } else {
            boolean scrolls = entries.size() > MAX_ROWS;
            int rowW = panelW - 8 - (scrolls ? 7 : 0);
            for (int i = 0; i < rows && offset + i < entries.size(); i++) {
                renderRow(g, entries.get(offset + i), panelX + 4, listY + i * ROW_HEIGHT, rowW);
            }
            if (scrolls) kit.scrollbar(g, panelX + panelW - 9, listY + 1, listH - 2, MAX_ROWS, entries.size(), offset,
                    entries.size() - MAX_ROWS, value -> offset = value);
        }
        renderFooter(g, listY + listH + 4);
        kit.drawTooltip(g, l + 9, t + 9, l + screen.desktopWidth() - 9, t + screen.desktopHeight() - 9);
    }

    private void renderHeader(GuiGraphics g) {
        g.drawString(font, Component.literal(tr("title")), panelX + 7, panelY + 7, Ui.TEXT_STRONG, false);
        long unread = ComputerNotificationClientState.unreadCount();
        if (unread > 0) {
            String label = Component.translatable("computer.antos.notifications.new", unread).getString();
            g.drawString(font, Component.literal(label), panelX + panelW - 7 - font.width(label), panelY + 7, Ui.ACCENT, false);
        }
        g.fill(panelX + 4, panelY + 20, panelX + panelW - 4, panelY + 21, Ui.LINE);
    }

    private void renderFilters(GuiGraphics g) {
        int gap = 3;
        int chipW = (panelW - 8 - gap * (FILTERS.length - 1)) / FILTERS.length;
        for (int i = 0; i < FILTERS.length; i++) {
            String key = FILTERS[i];
            int x = panelX + 4 + i * (chipW + gap);
            int y = panelY + 23;
            long unread = ComputerNotificationClientState.history().entries().stream().filter(entry -> !entry.read() && matches(entry, key)).count();
            String label = tr("filter_" + key) + (unread > 0 && !key.equals("all") ? " " + unread : "");
            screen.drawFilterButton(g, x, y, chipW, filter.equals(key), label);
            kit.hit(x, y, chipW, 17, () -> AntOSUiSounds.action(() -> {
                filter = key;
                offset = 0;
                confirmClear = false;
            }));
        }
    }

    private void renderRow(GuiGraphics g, NotificationHistory.Entry entry, int x, int y, int w) {
        boolean hover = kit.hovered(x, y, w, ROW_HEIGHT - 1);
        if (hover) g.fill(x, y, x + w, y + ROW_HEIGHT - 1, Ui.HOVER);
        if (!entry.read()) g.fill(x, y + 3, x + 2, y + ROW_HEIGHT - 4, Ui.ACCENT);
        renderIcon(g, entry, x + 5, y + 4);
        String time = relativeTime(entry.receivedAt());
        int timeW = font.width(time);
        int textX = x + 26;
        int textW = w - 26 - 4;
        g.drawString(font, Component.literal(screen.trimToWidth(entry.title(), textW - timeW - 6)), textX, y + 4,
                entry.read() ? Ui.TEXT : Ui.TEXT_STRONG, false);
        g.drawString(font, Component.literal(time), x + w - 4 - timeW, y + 4, Ui.TEXT_MUTED, false);
        g.drawString(font, Component.literal(screen.trimToWidth(entry.message(), textW)), textX, y + 15, Ui.TEXT_MUTED, false);
        g.fill(x + 26, y + ROW_HEIGHT - 1, x + w, y + ROW_HEIGHT, Ui.SURFACE);
        if (hover) kit.tooltip(entry.message() + "\n" + DATE.format(Instant.ofEpochMilli(entry.receivedAt())));
        kit.hit(x, y, w, ROW_HEIGHT - 1, () -> AntOSUiSounds.action(() -> activate(entry)));
    }

    private void renderIcon(GuiGraphics g, NotificationHistory.Entry entry, int x, int y) {
        if (entry.kind().equals("mail") && !entry.source().isBlank()) {
            ProfileAvatar.draw(this, g, entry.source(), entry.iconItem(), entry.iconEntity(), x, y, 16);
            return;
        }
        if ((!entry.iconItem().isBlank() || !entry.iconEntity().isBlank()) && asset(g, entry, x, y)) return;
        String app = app(entry.kind());
        PixelIcons.draw(g, new ResourceLocation(AntOS.MODID, "textures/gui/desktop/"
                + (app.isEmpty() ? "settings" : app.toLowerCase(Locale.ROOT)) + ".png"), x, y, 16);
    }

    private boolean asset(GuiGraphics g, NotificationHistory.Entry entry, int x, int y) {
        boolean previousTint = screen.archiveGreenTint;
        screen.archiveGreenTint = false;
        try {
            return screen.renderArchiveAsset(g, entry.iconItem(), entry.iconEntity(), "", "", x + 8, y + 8, 16, 0.0F, 0.9F);
        } finally {
            screen.archiveGreenTint = previousTint;
        }
    }

    private void renderFooter(GuiGraphics g, int y) {
        g.fill(panelX + 4, y - 3, panelX + panelW - 4, y - 2, Ui.LINE);
        var history = ComputerNotificationClientState.history();
        boolean settings = AntOSSettings.appEnabled("SETTINGS");
        int gap = 4;
        int count = settings ? 3 : 2;
        int buttonW = (panelW - 8 - gap * (count - 1)) / count;
        int x = panelX + 4;
        kit.button(g, x, y, buttonW, 17, tr("read_all"), false, history.unreadCount() > 0, ComputerNotificationClientState::markAllRead);
        x += buttonW + gap;
        kit.button(g, x, y, buttonW, 17, tr(confirmClear ? "confirm_clear" : "clear"), confirmClear, !history.entries().isEmpty(), () -> {
            if (!confirmClear) {
                confirmClear = true;
                return;
            }
            ComputerNotificationClientState.clearHistory();
            confirmClear = false;
            offset = 0;
        });
        if (!settings) return;
        x += buttonW + gap;
        kit.button(g, x, y, buttonW, 17, tr("settings"), false, true, () -> {
            close();
            screen.openNotificationSettings();
        });
    }

    private void activate(NotificationHistory.Entry entry) {
        ComputerNotificationClientState.markRead(entry.id());
        String app = app(entry.kind());
        if (app.isEmpty() || !AntOSSettings.appEnabled(app)) return;
        close();
        screen.open(app);
        if (app.equals("ANTAZON")) screen.session.antazon.openNotification(entry.kind());
    }

    boolean click(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (ComputerScreen.inside(panelX, panelY, panelW, panelH, mouseX, mouseY)) {
            if (button == 0) kit.click(mouseX, mouseY, button);
            return true;
        }
        close();
        return true;
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (!open || !ComputerScreen.inside(panelX, panelY, panelW, panelH, mouseX, mouseY)) return false;
        offset = Math.max(0, Math.min(Math.max(0, filtered().size() - MAX_ROWS), offset - (int) Math.signum(amount)));
        return true;
    }

    boolean drag(double mouseY) {
        if (!kit.draggingScrollbar()) return false;
        kit.drag(mouseY);
        return true;
    }

    boolean release() {
        if (!kit.draggingScrollbar()) return false;
        kit.release();
        return true;
    }

    private List<NotificationHistory.Entry> filtered() {
        return ComputerNotificationClientState.history().entries().stream().filter(entry -> matches(entry, filter)).toList();
    }

    private static boolean matches(NotificationHistory.Entry entry, String filter) {
        return switch (filter) {
            case "mail" -> entry.kind().equals("mail");
            case "task" -> entry.kind().equals("task");
            case "antazon" -> entry.kind().equals("order") || entry.kind().equals("sale");
            default -> true;
        };
    }

    private static String app(String kind) {
        return switch (kind) {
            case "mail" -> "ANTMAIL";
            case "task" -> "TASKS";
            case "order", "sale" -> "ANTAZON";
            default -> "";
        };
    }

    private static String relativeTime(long receivedAt) {
        long seconds = Math.max(0, (System.currentTimeMillis() - receivedAt) / 1000L);
        if (seconds < 60) return tr("time_now");
        if (seconds < 3600) return Component.translatable("computer.antos.notifications.time_minutes", seconds / 60).getString();
        if (seconds < 86400) return Component.translatable("computer.antos.notifications.time_hours", seconds / 3600).getString();
        return Component.translatable("computer.antos.notifications.time_days", seconds / 86400).getString();
    }

    private static String tr(String key) {
        return Component.translatable("computer.antos.notifications." + key).getString();
    }
}
