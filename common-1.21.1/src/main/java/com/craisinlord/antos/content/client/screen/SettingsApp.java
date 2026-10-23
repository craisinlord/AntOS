package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.PaletteColors;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.client.palette.TerminalPalette;
import com.google.gson.JsonObject;
import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.content.client.AntOSUiSounds.Cue;
import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.AntazonClientState;
import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.AnternetAccountClientState;
import com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.client.ComputerNotificationClientState;
import com.craisinlord.antos.content.client.ComputerTasksClientState;
import com.craisinlord.antos.content.client.ComputerTheme;
import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.computer.ComputerFileSystem;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AntmailNetworking;
import com.craisinlord.antos.content.network.AnternetAccountNetworking;
import com.craisinlord.antos.content.network.AnternetAccountResultPayload;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;


final class SettingsApp extends ComputerApp {
    private static final int SIDEBAR = 96;
    private static final int ROW = 18;
    private static final String[] CATEGORIES = {"account", "personalize", "accessibility", "apps", "notifications", "storage", "help", "about"};
    private static final String[] ALERT_STYLES = {"toast", "actionbar", "both", "off"};
    private static final String[][] SHORTCUTS = {
            {"text", "save"}, {"text", "save_as"}, {"text", "open"}, {"text", "new"}, {"text", "find"}, {"text", "rename"},
            {"text", "delete"}, {"text", "undo"}, {"text", "redo"}, {"text", "select_all"}, {"text", "word"},
            {"paint", "pencil"}, {"paint", "eraser"}, {"paint", "fill"}, {"paint", "line"}, {"paint", "rectangle"},
            {"paint", "ellipse"}, {"paint", "grid"}, {"paint", "brush"}, {"paint", "undo"}, {"paint", "redo"},
            {"paint", "invert"}, {"paint", "save"},
            {"files", "open"}, {"files", "up"}, {"files", "rename"}, {"files", "delete"}, {"files", "new_folder"},
            {"files", "move"}, {"files", "copy_path"}, {"files", "refresh"},
            {"terminal", "complete"}, {"terminal", "history"}, {"terminal", "clear_line"}, {"terminal", "clear_screen"},
            {"terminal", "cancel"}, {"terminal", "paste"}, {"terminal", "scroll"}
    };

    final State state = new State();
    final AppKit kit = new AppKit(this);
    private final Field displayName = new Field(32, false);
    private final Field iconItem = new Field(64, false);
    private final Field search = new Field(32, false);
    private final Field settingsSearch = new Field(64, false);
    private String revealTarget = "";
    private int revealY = -1;
    private final Field currentPassword = new Field(128, true);
    private final Field newPassword = new Field(128, true);
    private final Field confirmPassword = new Field(128, true);
    private final Field hintField = new Field(64, false);
    private final Field[] customFields = newCustomFields();
    private Field focused;
    private boolean draggingScale;
    private boolean scaleTrackVisible;
    private int pendingScale;
    private int scaleTrackX;
    private int scaleTrackY;
    private int scaleTrackWidth;
    private int contentTop;
    private int contentBottom;
    private int paneX;
    private int paneW;
    private int cursorY;
    private boolean requested;
    private boolean profileLoaded;
    private boolean profilePending;
    private Object profileBaseline;
    private String profileStatus = "";
    private int profileStatusColor = Ui.TEXT_MUTED;
    private boolean passwordDialog;
    private boolean hintMode;
    private long hintRevision;
    private boolean passwordPending;
    private long passwordRevision;
    private String passwordError = "";
    private String notice = "";
    private long noticeUntil;
    private String confirmAction = "";
    private long confirmUntil;

    void render(GuiGraphics g, int x, int y, int w, int h) {
        if (!requested) {
            requested = true;
            ComputerNetworking.listFiles();
            if (AntOSSettings.appEnabled("ANTMAIL") && AntmailClientState.get() == null) AntmailNetworking.requestState();
            if (AntOSSettings.appEnabled("ANTAZON")) ComputerNetworking.requestAntazonWallet();
        }
        syncProfile();
        syncPassword();
        syncHint();
        if (AnternetAccountClientState.mustChangePassword() && (!passwordDialog || hintMode)) {
            state.category = "account";
            state.showList = false;
            openPasswordDialog();
        }
        if (!confirmAction.isEmpty() && System.currentTimeMillis() > confirmUntil) confirmAction = "";
        kit.begin();
        scaleTrackVisible = false;
        kit.suppressHover(passwordDialog);
        if (!passwordDialog) {
            kit.field(g, x, y, w - 22, 17, settingsSearch.shown(), settingsSearch.cursor,
                    focused == settingsSearch, tr("search.placeholder"));
            kit.hit(x, y, w - 22, 17, () -> focus(settingsSearch));
            kit.button(g, x + w - 19, y, 19, 17, "×", false, !settingsSearch.value.isEmpty(), () -> {
                settingsSearch.set("");
                state.scroll = 0;
                focused = settingsSearch;
            });
        }
        y += 22;
        h -= 22;
        boolean narrow = w < 300;
        if (narrow && state.showList && settingsSearch.value.isBlank()) {
            renderCategoryList(g, x, y, w, h);
        } else {
            int left = x;
            int width = w;
            if (!narrow) {
                renderSidebar(g, x, y, h);
                left = x + SIDEBAR + 8;
                width = w - SIDEBAR - 8;
            }
            renderPane(g, left, y, width, h, narrow);
        }
        if (passwordDialog) renderPasswordDialog(g, x, y, w, h);
        kit.drawTooltip(g, x, y, x + w, y + h);
    }

    private void renderSidebar(GuiGraphics g, int x, int y, int h) {
        g.fill(x, y, x + SIDEBAR, y + h, Ui.PANEL);
        g.fill(x + SIDEBAR, y, x + SIDEBAR + 1, y + h, Ui.SURFACE);
        g.drawString(font, Component.literal(tr("title")), x + 6, y + 5, Ui.TEXT_STRONG, false);
        g.fill(x + 4, y + 16, x + SIDEBAR - 4, y + 17, Ui.SURFACE);
        int rowY = y + 21;
        int spacing = Math.min(21, Math.max(16, (h - 21) / CATEGORIES.length));
        for (String category : CATEGORIES) {
            boolean selected = state.category.equals(category);
            boolean hover = kit.hovered(x + 2, rowY, SIDEBAR - 4, spacing - 2);
            if (selected || hover) g.fill(x + 2, rowY, x + SIDEBAR - 2, rowY + spacing - 2, selected ? Ui.SELECTED_FILL : Ui.HOVER);
            if (selected) g.fill(x + 2, rowY, x + 4, rowY + spacing - 2, Ui.ACCENT);
            g.drawString(font, Component.literal(screen.trimToWidth(tr("category." + category), SIDEBAR - 14)), x + 8, rowY + (spacing - 10) / 2, selected ? Ui.TEXT : Ui.ACCENT, false);
            kit.hit(x + 2, rowY, SIDEBAR - 4, spacing - 2, () -> select(category));
            rowY += spacing;
        }
    }

    private void renderCategoryList(GuiGraphics g, int x, int y, int w, int h) {
        g.drawString(font, Component.literal(tr("title")), x, y + 3, Ui.TEXT_STRONG, false);
        g.fill(x, y + 14, x + w, y + 15, Ui.SURFACE);
        int rowY = y + 19;
        int spacing = Math.min(22, Math.max(18, (h - 19) / CATEGORIES.length));
        for (String category : CATEGORIES) {
            boolean hover = kit.hovered(x, rowY, w, spacing - 2);
            g.fill(x, rowY, x + w, rowY + spacing - 2, hover ? Ui.HOVER : Ui.PANEL);
            g.drawString(font, Component.literal(tr("category." + category)), x + 6, rowY + (spacing - 10) / 2, hover ? Ui.ACCENT : Ui.TEXT, false);
            g.drawString(font, Component.literal(">"), x + w - 10, rowY + (spacing - 10) / 2, Ui.TEXT_MUTED, false);
            kit.hit(x, rowY, w, spacing - 2, () -> select(category));
            rowY += spacing;
        }
    }

    void openNotifications() {
        settingsSearch.set("");
        select("notifications");
    }

    private void select(String category) {
        settingsSearch.set("");
        revealTarget = "";
        draggingScale = false;
        scaleTrackVisible = false;
        state.category = category;
        state.scroll = 0;
        state.showList = false;
        focused = null;
    }

    private void renderPane(GuiGraphics g, int x, int y, int w, int h, boolean narrow) {
        paneX = x;
        paneW = w;
        int titleX = x;
        if (narrow) {
            int backWidth = kit.labelWidth("<");
            kit.button(g, x, y, backWidth, 15, "<", false, true, tr("tip.back"), () -> {
                state.showList = true;
                settingsSearch.set("");
                state.scroll = 0;
                focused = null;
            });
            titleX += backWidth + 6;
        }
        g.drawString(font, Component.literal((settingsSearch.value.isBlank() ? tr("category." + state.category) : tr("search.title"))), titleX, y + 4, Ui.TEXT_STRONG, false);
        if (System.currentTimeMillis() < noticeUntil) {
            String shown = screen.trimToWidth(notice, Math.max(10, w / 2));
            g.drawString(font, Component.literal(shown), x + w - font.width(shown), y + 4, Ui.TEXT, false);
        }
        g.fill(x, y + 15, x + w, y + 16, Ui.SURFACE);
        contentTop = y + 19;
        contentBottom = y + h;
        int viewHeight = contentBottom - contentTop;
        screen.enableComputerScissor(g, x, contentTop, x + w, contentBottom);
        kit.clip(x, contentTop, w, viewHeight);
        cursorY = contentTop + 2 - state.scroll;
        try {
            revealY = -1;
            if (!settingsSearch.value.isBlank()) renderSearchResults(g);
            else switch (state.category) {
                case "account" -> renderAccount(g);
                case "personalize" -> renderPersonalize(g);
                case "accessibility" -> renderAccessibility(g);
                case "apps" -> renderApps(g);
                case "notifications" -> renderNotifications(g);
                case "storage" -> renderStorage(g);
                case "help" -> renderHelp(g);
                default -> renderAbout(g);
            }
        } finally {
            kit.unclip();
            g.disableScissor();
        }
        int contentHeight = cursorY + state.scroll - contentTop;
        state.maximumScroll = Math.max(0, contentHeight - viewHeight);
        if (revealY >= 0) {
            state.scroll = Math.max(0, Math.min(state.maximumScroll, revealY));
            revealTarget = "";
        }
        state.scroll = Math.max(0, Math.min(state.maximumScroll, state.scroll));
        kit.scrollbar(g, x + w - 3, contentTop, viewHeight, viewHeight, contentHeight, state.scroll, state.maximumScroll, value -> state.scroll = value);
    }

    private static final String[][] SEARCH_SETTINGS = {
            {"account", "account.signed_in"},
            {"account", "account.username"},
            {"account", "account.profile"},
            {"account", "account.display_name"},
            {"account", "account.icon_item"},
            {"account", "account.security"},
            {"account", "account.password"},
            {"account", "account.hint"},
            {"account", "face_id.label"},
            {"account", "account.session"},
            {"account", "account.log_out_hint"},
            {"personalize", "accessibility.look"},
            {"personalize", "accessibility.icons"},
            {"personalize", "accessibility.wallpaper"},
            {"personalize", "personalize.theme"},
            {"personalize", "personalize.wallpaper"},
            {"personalize", "personalize.reset_hint"},
            {"accessibility", "accessibility.display"},
            {"accessibility", "accessibility.brightness"},
            {"accessibility", "accessibility.fullscreen"},
            {"accessibility", "accessibility.layout_hint"},
            {"accessibility", "accessibility.comfort"},
            {"accessibility", "accessibility.reduce_effects"},
            {"accessibility", "accessibility.resume"},
            {"accessibility", "accessibility.gameplay"},
            {"accessibility", "accessibility.pause"},
            {"accessibility", "accessibility.preview"},
            {"accessibility", "accessibility.reset_hint"},
            {"apps", "apps.text"},
            {"apps", "apps.line_numbers"},
            {"apps", "apps.paint"},
            {"apps", "apps.paint_grid"},
            {"apps", "apps.paint_brush"},
            {"apps", "apps.terminal"},
            {"apps", "apps.terminal_history"},
            {"apps", "apps.terminal_clear_hint"},
            {"apps", "apps.archive"},
            {"apps", "apps.archive_text"},
            {"apps", "apps.archive_clear_recent_hint"},
            {"apps", "apps.tasks"},
            {"apps", "apps.tasks_zoom"},
            {"apps", "apps.tasks_sidebar_behavior"},
            {"apps", "apps.tasks_sidebar_width"},
            {"apps", "apps.tasks_sidebar_text"},
            {"apps", "apps.tasks_sidebar_spacing"},
            {"apps", "apps.tasks_sidebar_counts"},
            {"apps", "apps.tasks_sidebar_reset_hint"},
            {"apps", "apps.tasks_tracker"},
            {"apps", "apps.tasks_tracker_show"},
            {"apps", "apps.tasks_tracker_position"},
            {"apps", "apps.tasks_tracker_style"},
            {"apps", "apps.tasks_tracker_opacity"},
            {"apps", "apps.tasks_auto_unpin"},
            {"apps", "apps.files"},
            {"apps", "apps.files_places"},
            {"apps", "apps.files_sort"},
            {"notifications", "notifications.panel_hint"},
            {"notifications", "notifications.quiet"},
            {"notifications", "notifications.interface"},
            {"notifications", "notifications.interface_sounds"},
            {"notifications", "notifications.interface_volume"},
            {"notifications", "notifications.interface_preview_hint"},
            {"notifications", "notifications.mail"},
            {"notifications", "notifications.style"},
            {"notifications", "notifications.sound"},
            {"notifications", "notifications.test_hint"},
            {"notifications", "notifications.tasks"},
            {"storage", "storage.usage"},
            {"storage", "storage.used"},
            {"storage", "storage.items"},
            {"storage", "storage.breakdown"},
            {"storage", "storage.manage_hint"},
            {"help", "help.tours"},
            {"help", "help.antos_tour"},
            {"help", "help.antazon_tour"},
            {"help", "help.shortcuts"},
            {"help", "help.search"},
            {"about", "about.system"},
            {"about", "about.version"},
            {"about", "about.account"},
            {"about", "about.theme"},
            {"about", "about.stats"},
            {"about", "about.files"},
            {"about", "about.disks"},
            {"about", "about.tasks"},
            {"about", "about.antcoins"},
            {"accessibility", "accessibility.ui_size"}
    };

    private void reveal(String label) {
        if (!revealTarget.isEmpty() && revealTarget.equals(label) && revealY < 0) revealY = cursorY + state.scroll - contentTop;
    }

    private void renderSearchResults(GuiGraphics g) {
        String query = settingsSearch.value.toLowerCase(Locale.ROOT).trim();
        String[] words = query.split("\\s+");
        int shown = 0;
        for (String[] item : SEARCH_SETTINGS) {
            if (item[1].equals("accessibility.brightness") && !AntOSSettings.preferences().displayLook.equals("terminal")) continue;
            String category = tr("category." + item[0]);
            String title = tr(item[1]);
            String text = (category + " " + title).toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String word : words) if (!text.contains(word)) { matches = false; break; }
            if (!matches) continue;
            int rowY = cursorY;
            int width = innerWidth();
            boolean hover = kit.hovered(paneX, rowY, width, 31);
            g.fill(paneX, rowY, paneX + width, rowY + 31, hover ? Ui.HOVER : Ui.PANEL);
            g.drawString(font, Component.literal(screen.trimToWidth(title, width - 12)), paneX + 5, rowY + 4, Ui.TEXT, false);
            g.drawString(font, Component.literal(category), paneX + 5, rowY + 17, Ui.TEXT_MUTED, false);
            kit.hit(paneX, rowY, width, 31, () -> {
                select(item[0]);
                revealTarget = title;
            });
            cursorY += 35;
            shown++;
        }
        if (shown == 0) label(g, tr("search.empty"), Ui.TEXT_MUTED);
    }

    private int innerWidth() {
        return paneW - 8;
    }

    private void heading(GuiGraphics g, String key) {
        reveal(tr(key));
        cursorY += 4;
        g.drawString(font, Component.literal(tr(key)), paneX, cursorY, Ui.TEXT_MUTED, false);
        g.fill(paneX, cursorY + 10, paneX + innerWidth(), cursorY + 11, Ui.SURFACE);
        cursorY += 15;
    }

    private void label(GuiGraphics g, String text, int color) {
        for (var line : font.split(Component.literal(text), innerWidth())) {
            g.drawString(font, line, paneX, cursorY, color, false);
            cursorY += 11;
        }
    }

    private void valueRow(GuiGraphics g, String label, String value) {
        reveal(label);
        int valueWidth = Math.min(font.width(value), innerWidth() / 2);
        String shown = screen.trimToWidth(value, valueWidth);
        g.drawString(font, Component.literal(screen.trimToWidth(label, innerWidth() - valueWidth - 8)), paneX, cursorY + 4, Ui.TEXT, false);
        g.drawString(font, Component.literal(shown), paneX + innerWidth() - font.width(shown), cursorY + 4, Ui.TEXT_STRONG, false);
        cursorY += ROW;
    }

    private void toggleRow(GuiGraphics g, String label, boolean value, Consumer<Boolean> change) {
        reveal(label);
        int width = innerWidth();
        boolean hover = kit.hovered(paneX, cursorY, width, ROW - 2);
        if (hover) g.fill(paneX, cursorY, paneX + width, cursorY + ROW - 2, Ui.HOVER);
        g.drawString(font, Component.literal(screen.trimToWidth(label, width - 40)), paneX + 3, cursorY + 4, Ui.TEXT, false);
        int trackX = paneX + width - 28;
        int trackY = cursorY + 2;
        g.fill(trackX, trackY, trackX + 24, trackY + 12, value ? Ui.TOGGLE_ON : Ui.SURFACE);
        kit.outline(g, trackX, trackY, 24, 12, value ? Ui.ACCENT : Ui.LINE);
        int knobX = value ? trackX + 14 : trackX + 2;
        g.fill(knobX, trackY + 2, knobX + 8, trackY + 10, value ? Ui.TEXT : Ui.TEXT_MUTED);
        kit.hit(paneX, cursorY, width, ROW - 2, () -> AntOSUiSounds.action(() -> change.accept(!value)));
        cursorY += ROW;
    }

    private void choiceRow(GuiGraphics g, String label, String[] values, String prefix, String selected, Consumer<String> change) {
        reveal(label);
        int width = innerWidth();
        int[] widths = new int[values.length];
        int total = 0;
        for (int index = 0; index < values.length; index++) {
            widths[index] = Math.max(30, font.width(tr(prefix + values[index])) + 10);
            total += widths[index] + 3;
        }
        int labelWidth = font.width(label);
        boolean inline = labelWidth + 10 + total <= width;
        g.drawString(font, Component.literal(screen.trimToWidth(label, width)), paneX + 3, cursorY + 5, Ui.TEXT, false);
        int chipX = inline ? paneX + width - total + 3 : paneX + 3;
        int chipY = inline ? cursorY : cursorY + 14;
        for (int index = 0; index < values.length; index++) {
            String value = values[index];
            screen.drawFilterButton(g, chipX, chipY, widths[index], value.equals(selected), tr(prefix + value));
            kit.hit(chipX, chipY, widths[index], 17, () -> AntOSUiSounds.action(() -> change.accept(value)));
            chipX += widths[index] + 3;
        }
        cursorY += inline ? ROW + 2 : ROW + 16;
    }

    private void buttonRow(GuiGraphics g, String label, String button, boolean danger, Runnable action) {
        reveal(label);
        int width = innerWidth();
        int buttonWidth = Math.max(56, kit.labelWidth(button));
        g.drawString(font, Component.literal(screen.trimToWidth(label, width - buttonWidth - 8)), paneX + 3, cursorY + 5, danger ? Ui.ERROR : Ui.TEXT, false);
        kit.button(g, paneX + width - buttonWidth, cursorY, buttonWidth, 16, button, false, true, action);
        cursorY += ROW + 2;
    }

    private void field(GuiGraphics g, String label, Field field, String placeholder) {
        reveal(label);
        g.drawString(font, Component.literal(label), paneX + 3, cursorY, Ui.TEXT_MUTED, false);
        cursorY += 10;
        kit.field(g, paneX, cursorY, innerWidth(), 18, field.shown(), field.cursor, focused == field, placeholder);
        kit.hit(paneX, cursorY, innerWidth(), 18, () -> focus(field));
        cursorY += 22;
    }

    private void focus(Field field) {
        focused = field;
        field.cursor = field.value.length();
    }

    private void renderAccount(GuiGraphics g) {
        var account = AnternetAccountClientState.latest();
        String username = account == null || account.username() == null ? "-" : account.username();
        heading(g, "account.signed_in");
        valueRow(g, tr("account.username"), username);
        heading(g, "account.profile");
        if (!AntOSSettings.appEnabled("ANTMAIL")) {
            label(g, tr("account.profile_unavailable"), Ui.TEXT_MUTED);
        } else if (!profileLoaded) {
            label(g, tr("account.profile_loading"), Ui.TEXT_MUTED);
        } else {
            field(g, tr("account.display_name"), displayName, tr("account.display_name_placeholder"));
            field(g, tr("account.icon_item"), iconItem, tr("account.icon_item_placeholder"));
            int saveWidth = Math.max(70, kit.labelWidth(tr("account.save_profile")));
            kit.button(g, paneX + innerWidth() - saveWidth, cursorY, saveWidth, 16, tr("account.save_profile"), false, !profilePending, this::saveProfile);
            int mailWidth = Math.max(70, kit.labelWidth(tr("account.open_mail")));
            kit.button(g, paneX + innerWidth() - saveWidth - mailWidth - 4, cursorY, mailWidth, 16, tr("account.open_mail"), false, true, () -> screen.open("ANTMAIL"));
            if (!profileStatus.isEmpty()) g.drawString(font, Component.literal(screen.trimToWidth(profileStatus, innerWidth() - saveWidth - mailWidth - 10)), paneX + 3, cursorY + 4, profileStatusColor, false);
            cursorY += ROW + 2;
        }
        heading(g, "account.security");
        buttonRow(g, tr("account.password"), tr("account.change_password"), false, this::openPasswordDialog);
        buttonRow(g, tr("account.hint"), tr("account.set_hint"), false, this::openHintDialog);
        label(g, tr("account.hint_detail"), Ui.TEXT_MUTED);
        boolean linked = AnternetAccountClientState.faceIdLinked();
        boolean mine = screen.faceIdLinkedToCurrentAccount();
        String faceStatus = linked ? mine ? tr("face_id.on") : tr("face_id.in_use") : tr("face_id.off");
        String faceButton = linked && mine ? tr("face_id.unlink") : tr("face_id.link");
        if (linked && !mine) valueRow(g, tr("face_id.label"), faceStatus);
        else buttonRow(g, tr("face_id.label") + "  " + faceStatus, faceButton, false, this::toggleFaceId);
        if (!screen.faceIdMessage.isBlank()) label(g, screen.faceIdMessage, Ui.TEXT_MUTED);
        heading(g, "account.session");
        buttonRow(g, tr("account.log_out_hint"), tr("account.log_out"), true, this::logout);
    }

    private void renderPersonalize(GuiGraphics g) {
        var preferences = AntOSSettings.preferences();
        choiceRow(g, tr("accessibility.look"), new String[]{"terminal", "graphite", "custom"}, "accessibility.look_", preferences.displayLook,
                value -> AntOSSettings.updatePreferences(p -> p.displayLook = value));
        label(g, tr("accessibility.look_hint_" + preferences.displayLook), Ui.TEXT_MUTED);
        if (!preferences.displayLook.equals("custom")) {
            choiceRow(g, tr("accessibility.icons"), new String[]{"accent", "neutral"}, "accessibility.icons_", preferences.iconColor,
                    value -> AntOSSettings.updatePreferences(p -> p.iconColor = value));
        }
        choiceRow(g, tr("accessibility.wallpaper"), new String[]{"match", "original"}, "accessibility.wallpaper_", preferences.wallpaperColors,
                value -> AntOSSettings.updatePreferences(p -> p.wallpaperColors = value));
        if (preferences.displayLook.equals("custom")) renderCustomColors(g, preferences);
        heading(g, "personalize.theme");
        int width = innerWidth();
        int columns = Math.max(2, Math.min(3, width / 64));
        int tileWidth = (width - (columns - 1) * 4) / columns;
        ComputerTheme active = ComputerTheme.active();
        ComputerTheme[] themes = ComputerTheme.values();
        for (int index = 0; index < themes.length; index++) {
            ComputerTheme theme = themes[index];
            int column = index % columns;
            int row = index / columns;
            int tileX = paneX + column * (tileWidth + 4);
            int tileY = cursorY + row * 34;
            boolean selected = theme == active;
            boolean hover = kit.hovered(tileX, tileY, tileWidth, 30);
            g.fill(tileX, tileY, tileX + tileWidth, tileY + 30, hover || selected ? Ui.HOVER : Ui.PANEL);
            kit.outline(g, tileX, tileY, tileWidth, 30, selected ? Ui.FOCUS : hover ? Ui.ACCENT : Ui.BORDER);
            g.fill(tileX + 5, tileY + 5, tileX + 25, tileY + 25, TerminalPalette.themeSwatch(theme, 0));
            if (theme == ComputerTheme.RAINBOW) {
                for (int colorIndex = 0; colorIndex < TerminalPalette.rainbowSwatchCount(); colorIndex++) {
                    g.fill(tileX + 7 + colorIndex * 3, tileY + 9, tileX + 9 + colorIndex * 3, tileY + 21, TerminalPalette.rainbowSwatch(colorIndex));
                }
            } else {
                g.fill(tileX + 7, tileY + 9, tileX + 23, tileY + 11, TerminalPalette.themeSwatch(theme, 1));
                g.fill(tileX + 7, tileY + 14, tileX + 19, tileY + 16, TerminalPalette.themeSwatch(theme, 2));
                g.fill(tileX + 7, tileY + 19, tileX + 21, tileY + 21, TerminalPalette.themeSwatch(theme, 3));
            }
            kit.outline(g, tileX + 5, tileY + 5, 20, 20, TerminalPalette.themeSwatch(theme, 1));
            g.drawString(font, Component.literal(screen.trimToWidth(tr("theme." + theme.id()), tileWidth - 32)), tileX + 30, tileY + 11, selected ? Ui.TEXT : Ui.ACCENT, false);
            kit.hit(tileX, tileY, tileWidth, 30, () -> ComputerTheme.select(theme));
        }
        cursorY += ((themes.length + columns - 1) / columns) * 34;
        if (!Ui.terminal()) label(g, tr(AntOSSettings.preferences().displayLook.equals("custom") ? "personalize.theme_hint_custom" : "personalize.theme_hint_graphite"), Ui.TEXT_MUTED);
        heading(g, "personalize.wallpaper");
        List<ComputerGuideData.Wallpaper> wallpapers = screen.session.wallpaperPicker.unlockedWallpapers();
        int thumbWidth = 64;
        int thumbHeight = 36;
        int perRow = Math.max(1, (width + 6) / (thumbWidth + 6));
        for (int index = 0; index < wallpapers.size(); index++) {
            ComputerGuideData.Wallpaper wallpaper = wallpapers.get(index);
            int thumbX = paneX + (index % perRow) * (thumbWidth + 6);
            int thumbY = cursorY + (index / perRow) * (thumbHeight + 6);
            boolean selected = wallpaper.id().toString().equals(screen.session.wallpaperId);
            boolean hover = kit.hovered(thumbX - 2, thumbY - 2, thumbWidth + 4, thumbHeight + 4);
            screen.session.wallpaperPicker.drawWallpaperPreview(g, thumbX, thumbY, thumbWidth, thumbHeight, wallpaper.id().toString());
            if (selected || hover) kit.outline(g, thumbX - 2, thumbY - 2, thumbWidth + 4, thumbHeight + 4, selected ? Ui.FOCUS : Ui.ACCENT);
            if (hover) kit.tooltip(Component.translatable(wallpaper.titleKey()).getString());
            kit.hit(thumbX - 2, thumbY - 2, thumbWidth + 4, thumbHeight + 4, () -> ComputerNetworking.selectWallpaper(wallpaper.id()));
        }
        cursorY += ((wallpapers.size() + perRow - 1) / perRow) * (thumbHeight + 6) + 2;
        label(g, tr("personalize.wallpaper_hint"), Ui.TEXT_MUTED);
        buttonRow(g, tr("personalize.reset_hint"), tr("personalize.reset"), false, () ->
                AntOSSettings.updatePreferences(p -> {
                    p.displayLook = "terminal";
                    p.iconColor = "accent";
                    p.wallpaperColors = "match";
                }));
    }

    private void renderAccessibility(GuiGraphics g) {
        heading(g, "accessibility.display");
        label(g, tr("accessibility.hint"), Ui.TEXT_MUTED);
        var preferences = AntOSSettings.preferences();
        if (preferences.displayLook.equals("terminal")) {
            choiceRow(g, tr("accessibility.brightness"), new String[]{"standard", "soft", "dim"}, "accessibility.brightness_", preferences.displayBrightness,
                    value -> AntOSSettings.updatePreferences(p -> p.displayBrightness = value));
            label(g, tr("accessibility.brightness_hint_" + preferences.displayBrightness), Ui.TEXT_MUTED);
        }
        toggleRow(g, tr("accessibility.fullscreen"), preferences.fullscreenDesktop,
                value -> AntOSSettings.updatePreferences(p -> p.fullscreenDesktop = value));
        label(g, tr("accessibility.fullscreen_hint"), Ui.TEXT_MUTED);
        buttonRow(g, tr("accessibility.layout_hint"), tr("accessibility.layout_reset"), false, screen::resetWindowLayout);
        heading(g, "accessibility.comfort");
        renderScaleSlider(g);
        toggleRow(g, tr("accessibility.reduce_effects"), preferences.reduceEffects,
                value -> AntOSSettings.updatePreferences(p -> p.reduceEffects = value));
        label(g, tr("accessibility.reduce_effects_hint"), Ui.TEXT_MUTED);
        toggleRow(g, tr("accessibility.resume"), preferences.resumeSession,
                value -> AntOSSettings.updatePreferences(p -> p.resumeSession = value));
        label(g, tr("accessibility.resume_hint"), Ui.TEXT_MUTED);
        heading(g, "accessibility.gameplay");
        toggleRow(g, tr("accessibility.pause"), preferences.pauseWhileOpen,
                value -> AntOSSettings.updatePreferences(p -> p.pauseWhileOpen = value));
        label(g, tr("accessibility.pause_hint"), Ui.TEXT_MUTED);
        heading(g, "accessibility.preview");
        renderDisplayPreview(g);
        buttonRow(g, tr("accessibility.reset_hint"), tr("accessibility.reset"), false, () ->
                AntOSSettings.updatePreferences(p -> {
                    p.displayBrightness = "standard";
                    p.uiSize = "standard";
                    p.uiScalePercent = 100;
                    p.reduceEffects = false;
                    p.resumeSession = true;
                    p.fullscreenDesktop = false;
                }));
    }

    private void renderScaleSlider(GuiGraphics g) {
        reveal(tr("accessibility.ui_size"));
        scaleTrackVisible = true;
        int value = draggingScale ? pendingScale : AntOSSettings.uiScalePercent();
        int width = innerWidth();
        String percentage = tr("accessibility.ui_scale_value", value);
        g.drawString(font, Component.literal(screen.trimToWidth(tr("accessibility.ui_size"), width - font.width(percentage) - 10)), paneX + 3, cursorY + 4, Ui.TEXT, false);
        g.drawString(font, Component.literal(percentage), paneX + width - font.width(percentage) - 3, cursorY + 4, Ui.ACCENT, false);
        cursorY += 18;
        scaleTrackX = paneX + 27;
        scaleTrackY = cursorY;
        scaleTrackWidth = Math.max(1, width - 54);
        int range = AntOSSettings.MAX_UI_SCALE - AntOSSettings.MIN_UI_SCALE;
        int thumbX = scaleTrackX + Math.round((value - AntOSSettings.MIN_UI_SCALE) * (scaleTrackWidth - 1) / (float) range);
        g.fill(scaleTrackX, cursorY + 7, scaleTrackX + scaleTrackWidth, cursorY + 10, Ui.BORDER);
        g.fill(scaleTrackX, cursorY + 7, thumbX, cursorY + 10, Ui.ACCENT);
        g.fill(thumbX - 4, cursorY + 2, thumbX + 5, cursorY + 15, Ui.ACCENT);
        kit.outline(g, thumbX - 4, cursorY + 2, 9, 13, draggingScale ? Ui.FOCUS : Ui.BORDER_STRONG);
        kit.button(g, paneX, cursorY, 20, 17, "-", false, value > AntOSSettings.MIN_UI_SCALE, () -> setScale(AntOSSettings.uiScalePercent() - AntOSSettings.UI_SCALE_STEP));
        kit.button(g, paneX + width - 20, cursorY, 20, 17, "+", false, value < AntOSSettings.MAX_UI_SCALE, () -> setScale(AntOSSettings.uiScalePercent() + AntOSSettings.UI_SCALE_STEP));
        cursorY += 22;
        buttonRow(g, tr("accessibility.ui_scale_range"), tr("accessibility.ui_scale_reset"), false, () -> setScale(100));
        label(g, tr("accessibility.ui_scale_hint"), Ui.TEXT_MUTED);
    }

    private void setScale(int percentage) {
        int normalized = AntOSSettings.normalizeUiScale(percentage);
        if (normalized != AntOSSettings.uiScalePercent()) AntOSSettings.updatePreferences(p -> p.uiScalePercent = normalized);
    }

    private void dragScale(double mouseX) {
        double progress = Math.max(0, Math.min(1, (mouseX - scaleTrackX) / Math.max(1, scaleTrackWidth - 1)));
        pendingScale = AntOSSettings.normalizeUiScale(AntOSSettings.MIN_UI_SCALE + (int) Math.round(progress * (AntOSSettings.MAX_UI_SCALE - AntOSSettings.MIN_UI_SCALE)));
    }

    private void renderCustomColors(GuiGraphics g, AntOSSettings.Preferences preferences) {
        int[] colors = PaletteColors.parse(preferences.customColors);
        heading(g, "accessibility.custom");
        label(g, tr("accessibility.custom_hint"), Ui.TEXT_MUTED);
        int width = innerWidth();
        int resetWidth = Math.max(44, kit.labelWidth(tr("accessibility.custom_reset_one")));
        int fieldWidth = 62;
        for (int index = 0; index < colors.length; index++) {
            Field field = customFields[index];
            if (focused != field) {
                field.set(PaletteColors.hex(colors[index]));
            } else {
                Integer typed = PaletteColors.parseHex(field.value);
                if (typed != null && typed != colors[index]) {
                    colors[index] = typed;
                    storeCustomColors(colors);
                }
            }
            int rowY = cursorY;
            int fieldX = paneX + width - resetWidth - 4 - fieldWidth;
            int swatchX = fieldX - 20;
            g.drawString(font, Component.literal(screen.trimToWidth(tr("accessibility.custom_" + PaletteColors.KEYS[index]), Math.max(1, swatchX - paneX - 8))),
                    paneX + 3, rowY + 5, Ui.TEXT, false);
            g.fill(swatchX, rowY + 1, swatchX + 16, rowY + 15, colors[index]);
            kit.outline(g, swatchX, rowY + 1, 16, 14, Ui.BORDER_STRONG);
            kit.field(g, fieldX, rowY, fieldWidth, 16, field.value, field.cursor, focused == field, "");
            kit.hit(fieldX, rowY, fieldWidth, 16, () -> focus(field));
            int slot = index;
            kit.button(g, paneX + width - resetWidth, rowY, resetWidth, 16, tr("accessibility.custom_reset_one"), false, true, () -> {
                int[] current = PaletteColors.parse(AntOSSettings.preferences().customColors);
                current[slot] = PaletteColors.graphite()[slot];
                storeCustomColors(current);
            });
            cursorY += ROW;
            String warning = contrastWarning(slot, colors);
            if (!warning.isEmpty()) label(g, warning, Ui.ERROR);
        }
        buttonRow(g, tr("accessibility.custom_start_hint"), tr("accessibility.custom_start_graphite"), false, () -> storeCustomColors(PaletteColors.graphite()));
        buttonRow(g, "", tr("accessibility.custom_start_terminal"), false, () -> storeCustomColors(PaletteColors.terminal()));
        buttonRow(g, tr("accessibility.custom_share_hint"), tr("accessibility.custom_copy"), false, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(PaletteColors.shareCode(PaletteColors.parse(AntOSSettings.preferences().customColors)));
            notice(tr("accessibility.custom_copied"));
        });
        buttonRow(g, "", tr("accessibility.custom_paste"), false, () -> {
            int[] pasted = PaletteColors.parseShareCode(Minecraft.getInstance().keyboardHandler.getClipboard());
            if (pasted == null) {
                notice(tr("accessibility.custom_paste_invalid"));
                return;
            }
            focused = null;
            storeCustomColors(pasted);
            notice(tr("accessibility.custom_pasted"));
        });
    }

    private void storeCustomColors(int[] colors) {
        String stored = PaletteColors.format(colors);
        AntOSSettings.updatePreferences(p -> p.customColors = stored);
    }

    private String contrastWarning(int slot, int[] colors) {
        if (slot != PaletteColors.TEXT && slot != PaletteColors.MUTED && slot != PaletteColors.ACCENT) return "";
        double ratio = PaletteColors.contrast(colors[slot], colors[PaletteColors.WINDOW]);
        if (ratio >= 4.5) return "";
        return tr("accessibility.custom_low_contrast", String.format(java.util.Locale.ROOT, "%.1f", ratio));
    }

    private static Field[] newCustomFields() {
        Field[] fields = new Field[PaletteColors.KEYS.length];
        for (int index = 0; index < fields.length; index++) fields[index] = new Field(7, false);
        return fields;
    }

    private void renderDisplayPreview(GuiGraphics g) {
        int previewX = paneX + 2;
        int previewWidth = innerWidth() - 4;
        int textWidth = previewWidth - 12;
        g.fill(previewX, cursorY, previewX + previewWidth, cursorY + 58, Ui.PANEL);
        kit.outline(g, previewX, cursorY, previewWidth, 58, Ui.BORDER_STRONG);
        g.drawString(font, Component.literal(screen.trimToWidth(tr("accessibility.sample"), textWidth)), previewX + 6, cursorY + 6, Ui.TEXT_STRONG, false);
        g.drawString(font, Component.literal(screen.trimToWidth(tr("accessibility.sample_body"), textWidth)), previewX + 6, cursorY + 17, Ui.TEXT, false);
        g.drawString(font, Component.literal(screen.trimToWidth(tr("accessibility.sample_muted"), textWidth)), previewX + 6, cursorY + 28, Ui.TEXT_MUTED, false);
        int buttonY = cursorY + 39;
        int buttonWidth = Math.max(1, Math.min(70, (previewWidth - 18) / 3));
        kit.button(g, previewX + 6, buttonY, buttonWidth, 14, tr("accessibility.sample_button"), false, true, () -> { });
        kit.button(g, previewX + 10 + buttonWidth, buttonY, buttonWidth, 14, tr("accessibility.sample_disabled"), false, false, () -> { });
        g.drawString(font, Component.literal(screen.trimToWidth(tr("accessibility.sample_error"), Math.max(1, previewWidth - 2 * buttonWidth - 20))),
                previewX + 14 + 2 * buttonWidth, buttonY + 3, Ui.ERROR, false);
        cursorY += 63;
    }

    private void renderApps(GuiGraphics g) {
        var preferences = AntOSSettings.preferences();
        heading(g, "apps.text");
        choiceRow(g, tr("apps.line_numbers"), new String[]{"auto", "on", "off"}, "choice.", preferences.editorLineNumbers,
                value -> AntOSSettings.updatePreferences(p -> p.editorLineNumbers = value));
        heading(g, "apps.paint");
        toggleRow(g, tr("apps.paint_grid"), preferences.paintGrid, value -> {
            AntOSSettings.updatePreferences(p -> p.paintGrid = value);
            screen.session.paint.state.grid = value;
        });
        choiceRow(g, tr("apps.paint_brush"), new String[]{"1", "2", "3"}, "choice.brush_", String.valueOf(preferences.paintBrush), value -> {
            int size = Integer.parseInt(value);
            AntOSSettings.updatePreferences(p -> p.paintBrush = size);
            screen.session.paint.state.brushSize = size;
        });
        heading(g, "apps.terminal");
        toggleRow(g, tr("apps.terminal_history"), preferences.terminalKeepHistory, value -> {
            AntOSSettings.updatePreferences(p -> p.terminalKeepHistory = value);
            if (!value) AntOSSettings.clearTerminalHistory();
        });
        boolean confirmClear = confirmAction.equals("history");
        buttonRow(g, tr("apps.terminal_clear_hint"), confirmClear ? tr("confirm") : tr("apps.terminal_clear"), confirmClear, () -> {
            if (!confirmAction.equals("history")) {
                confirm("history");
                return;
            }
            confirmAction = "";
            screen.session.terminal.clearHistory();
            notice(tr("notice.history_cleared"));
        });
        heading(g, "apps.archive");
        choiceRow(g, tr("apps.archive_text"), new String[]{"small", "normal", "large"}, "choice.sidebar_", preferences.archiveText,
                value -> AntOSSettings.updatePreferences(p -> p.archiveText = value));
        buttonRow(g, tr("apps.archive_clear_recent_hint"), tr("apps.archive_clear_recent"), false, () -> {
            com.craisinlord.antos.config.AntOSSettings.clearRecentArchiveEntries(
                    com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of());
            notice(tr("apps.archive_clear_recent"));
        });
        heading(g, "apps.tasks");
        choiceRow(g, tr("apps.tasks_zoom"), AntOSSettings.Preferences.TASK_MAP_ZOOMS.toArray(String[]::new), "choice.zoom_", preferences.taskMapZoom, value -> {
            AntOSSettings.updatePreferences(p -> p.taskMapZoom = value);
            screen.tasks.resetMapZoom();
        });
        choiceRow(g, tr("apps.tasks_sidebar_behavior"), new String[]{"click", "hover", "always"}, "choice.sidebar_", preferences.taskSidebarBehavior, value -> {
            AntOSSettings.updatePreferences(p -> p.taskSidebarBehavior = value);
            screen.tasks.resetSidebar();
        });
        choiceRow(g, tr("apps.tasks_sidebar_width"), new String[]{"narrow", "standard", "wide"}, "choice.sidebar_", preferences.taskSidebarWidth, value -> {
            AntOSSettings.updatePreferences(p -> p.taskSidebarWidth = value);
            screen.tasks.resetSidebar();
        });
        choiceRow(g, tr("apps.tasks_sidebar_text"), new String[]{"small", "normal", "large"}, "choice.sidebar_", preferences.taskSidebarText, value -> {
            AntOSSettings.updatePreferences(p -> p.taskSidebarText = value);
            screen.tasks.resetSidebar();
        });
        choiceRow(g, tr("apps.tasks_sidebar_spacing"), new String[]{"compact", "comfortable"}, "choice.sidebar_", preferences.taskSidebarSpacing, value -> {
            AntOSSettings.updatePreferences(p -> p.taskSidebarSpacing = value);
            screen.tasks.resetSidebar();
        });
        toggleRow(g, tr("apps.tasks_sidebar_counts"), preferences.taskSidebarCounts,
                value -> AntOSSettings.updatePreferences(p -> p.taskSidebarCounts = value));
        buttonRow(g, tr("apps.tasks_sidebar_reset_hint"), tr("apps.tasks_sidebar_reset"), false, () -> {
            AntOSSettings.updatePreferences(p -> {
                p.taskSidebarBehavior = "click";
                p.taskSidebarWidth = "narrow";
                p.taskSidebarText = "small";
                p.taskSidebarSpacing = "compact";
                p.taskSidebarCounts = true;
            });
            screen.tasks.resetSidebar();
        });
        heading(g, "apps.tasks_tracker");
        toggleRow(g, tr("apps.tasks_tracker_show"), preferences.taskTracker,
                value -> AntOSSettings.updatePreferences(p -> p.taskTracker = value));
        choiceRow(g, tr("apps.tasks_tracker_position"), AntOSSettings.Preferences.TASK_TRACKER_POSITIONS.toArray(String[]::new), "choice.tracker_",
                preferences.taskTrackerPosition, value -> AntOSSettings.updatePreferences(p -> p.taskTrackerPosition = value));
        choiceRow(g, tr("apps.tasks_tracker_style"), new String[]{"detailed", "compact"}, "choice.tracker_", preferences.taskTrackerStyle,
                value -> AntOSSettings.updatePreferences(p -> p.taskTrackerStyle = value));
        choiceRow(g, tr("apps.tasks_tracker_opacity"), AntOSSettings.Preferences.TASK_TRACKER_OPACITIES.toArray(String[]::new), "choice.opacity_",
                preferences.taskTrackerOpacity, value -> AntOSSettings.updatePreferences(p -> p.taskTrackerOpacity = value));
        toggleRow(g, tr("apps.tasks_auto_unpin"), preferences.taskAutoUnpin,
                value -> AntOSSettings.updatePreferences(p -> p.taskAutoUnpin = value));
        label(g, tr("apps.tasks_tracker_hint"), Ui.TEXT_MUTED);
        heading(g, "apps.files");
        toggleRow(g, tr("apps.files_places"), preferences.explorerPlaces, value -> {
            AntOSSettings.updatePreferences(p -> p.explorerPlaces = value);
            screen.session.files.state.sidebar = value;
        });
        choiceRow(g, tr("apps.files_sort"), new String[]{"name", "size"}, "choice.sort_", preferences.explorerSortBySize ? "size" : "name", value -> {
            boolean size = value.equals("size");
            AntOSSettings.updatePreferences(p -> p.explorerSortBySize = size);
            screen.session.files.state.sortBySize = size;
            screen.session.files.state.descending = false;
        });
    }

    private void renderNotifications(GuiGraphics g) {
        buttonRow(g, tr("notifications.panel_hint"), tr("notifications.panel_open"), false, screen::openNotifications);
        toggleRow(g, tr("notifications.quiet"), AntOSSettings.preferences().quietNotifications,
                value -> AntOSSettings.updatePreferences(p -> p.quietNotifications = value));
        label(g, tr("notifications.quiet_hint"), Ui.TEXT_MUTED);
        var preferences = AntOSSettings.preferences();
        heading(g, "notifications.interface");
        toggleRow(g, tr("notifications.interface_sounds"), preferences.interfaceSounds,
                value -> AntOSSettings.updatePreferences(p -> p.interfaceSounds = value));
        choiceRow(g, tr("notifications.interface_volume"), new String[]{"0", "20", "40", "60", "80", "100"}, "choice.volume_", Integer.toString(preferences.interfaceVolume),
                value -> AntOSSettings.updatePreferences(p -> p.interfaceVolume = Integer.parseInt(value)));
        buttonRow(g, tr("notifications.interface_preview_hint"), tr("notifications.interface_preview"), false, AntOSUiSounds::preview);
        label(g, tr("notifications.interface_hint"), Ui.TEXT_MUTED);
        heading(g, "notifications.mail");
        choiceRow(g, tr("notifications.style"), ALERT_STYLES, "choice.alert_", preferences.mailAlerts,
                value -> AntOSSettings.updatePreferences(p -> p.mailAlerts = value));
        toggleRow(g, tr("notifications.sound"), preferences.mailSound, value -> AntOSSettings.updatePreferences(p -> p.mailSound = value));
        buttonRow(g, tr("notifications.test_hint"), tr("notifications.test"), false, () -> ComputerNotificationClientState.test("mail"));
        heading(g, "notifications.tasks");
        choiceRow(g, tr("notifications.style"), ALERT_STYLES, "choice.alert_", preferences.taskAlerts,
                value -> AntOSSettings.updatePreferences(p -> p.taskAlerts = value));
        toggleRow(g, tr("notifications.sound"), preferences.taskSound, value -> AntOSSettings.updatePreferences(p -> p.taskSound = value));
        buttonRow(g, tr("notifications.test_hint"), tr("notifications.test"), false, () -> ComputerNotificationClientState.test("task"));
        cursorY += 2;
        label(g, tr("notifications.footer"), Ui.TEXT_MUTED);
    }

    private void renderStorage(GuiGraphics g) {
        int textCount = 0;
        int textBytes = 0;
        int imageCount = 0;
        int imageBytes = 0;
        int folders = 0;
        for (FileListing.Line line : FileListing.lines()) {
            int size = 0;
            if (line.size() != null) {
                try {
                    size = Integer.parseInt(line.size().trim());
                } catch (NumberFormatException ignored) {
                }
            }
            switch (line.type()) {
                case "DIRECTORY" -> folders++;
                case "IMAGE" -> {
                    imageCount++;
                    imageBytes += size;
                }
                default -> {
                    textCount++;
                    textBytes += size;
                }
            }
        }
        int used = textBytes + imageBytes;
        int limit = ComputerFileSystem.MAX_TOTAL_SIZE;
        heading(g, "storage.usage");
        int width = innerWidth();
        g.fill(paneX, cursorY, paneX + width, cursorY + 8, Ui.SURFACE);
        int textWidth = (int) Math.round(width * Math.min(1.0, textBytes / (double) limit));
        int imageWidth = (int) Math.round(width * Math.min(1.0, imageBytes / (double) limit));
        g.fill(paneX, cursorY, paneX + textWidth, cursorY + 8, Ui.ACCENT);
        g.fill(paneX + textWidth, cursorY, Math.min(paneX + width, paneX + textWidth + imageWidth), cursorY + 8, Ui.TEXT);
        kit.outline(g, paneX, cursorY, width, 8, Ui.LINE);
        cursorY += 12;
        valueRow(g, tr("storage.used"), tr("storage.kilobytes", kilobytes(used), kilobytes(limit)));
        valueRow(g, tr("storage.items"), tr("storage.count", ComputerFileSystemClientState.get().files().size(), ComputerFileSystem.MAX_NODES));
        heading(g, "storage.breakdown");
        legendRow(g, Ui.ACCENT, tr("storage.text"), tr("storage.detail", textCount, kilobytes(textBytes)));
        legendRow(g, Ui.TEXT, tr("storage.paintings"), tr("storage.detail", imageCount, kilobytes(imageBytes)));
        legendRow(g, Ui.LINE, tr("storage.folders"), String.valueOf(folders));
        cursorY += 4;
        buttonRow(g, tr("storage.manage_hint"), tr("storage.open_files"), false, () -> screen.open("FILES"));
    }

    private void legendRow(GuiGraphics g, int color, String label, String value) {
        g.fill(paneX + 3, cursorY + 4, paneX + 9, cursorY + 10, color);
        int valueWidth = font.width(value);
        g.drawString(font, Component.literal(label), paneX + 14, cursorY + 4, Ui.TEXT, false);
        g.drawString(font, Component.literal(value), paneX + innerWidth() - valueWidth, cursorY + 4, Ui.TEXT_STRONG, false);
        cursorY += ROW - 2;
    }

    private void renderHelp(GuiGraphics g) {
        heading(g, "help.tours");
        buttonRow(g, tr("help.antos_tour"), tr("help.replay"), false, this::replayTour);
        if (AntOSSettings.appEnabled("ANTAZON")) buttonRow(g, tr("help.antazon_tour"), tr("help.replay"), false, this::replayAntazon);
        if (AntOSSettings.appEnabled("MERCHANT")) buttonRow(g, tr("help.merchant_tour"), tr("help.replay"), false, this::replayMerchant);
        heading(g, "help.shortcuts");
        field(g, tr("help.search"), search, tr("help.search_placeholder"));
        String query = search.value.toLowerCase(Locale.ROOT).trim();
        String currentApp = "";
        int shown = 0;
        for (String[] shortcut : SHORTCUTS) {
            String app = shortcut[0];
            String base = "shortcut." + app + "." + shortcut[1];
            String keys = tr(base + ".keys");
            String action = tr(base + ".action");
            if (!query.isEmpty() && !keys.toLowerCase(Locale.ROOT).contains(query) && !action.toLowerCase(Locale.ROOT).contains(query)
                    && !tr("shortcut_app." + app).toLowerCase(Locale.ROOT).contains(query)) continue;
            if (!app.equals(currentApp)) {
                currentApp = app;
                cursorY += 3;
                g.drawString(font, Component.literal(tr("shortcut_app." + app)), paneX, cursorY, Ui.TEXT_STRONG, false);
                cursorY += 12;
            }
            int keyWidth = Math.min(innerWidth() / 2, font.width(keys) + 8);
            g.fill(paneX + 3, cursorY, paneX + 3 + keyWidth, cursorY + 12, Ui.PANEL);
            kit.outline(g, paneX + 3, cursorY, keyWidth, 12, Ui.LINE);
            g.drawString(font, Component.literal(screen.trimToWidth(keys, keyWidth - 6)), paneX + 7, cursorY + 2, Ui.TEXT, false);
            g.drawString(font, Component.literal(screen.trimToWidth(action, innerWidth() - keyWidth - 14)), paneX + keyWidth + 10, cursorY + 2, Ui.TEXT_MUTED, false);
            cursorY += 14;
            shown++;
        }
        if (shown == 0) label(g, tr("help.no_matches"), Ui.TEXT_MUTED);
    }

    private void renderAbout(GuiGraphics g) {
        heading(g, "about.system");
        valueRow(g, tr("about.version"), AntOS.MOD_VERSION);
        var account = AnternetAccountClientState.latest();
        valueRow(g, tr("about.account"), account == null || account.username() == null ? "-" : account.username());
        valueRow(g, tr("about.theme"), tr("theme." + ComputerTheme.active().id()));
        heading(g, "about.stats");
        int fileCount = 0;
        for (String line : ComputerFileSystemClientState.get().files()) if (!line.startsWith("DIRECTORY\t")) fileCount++;
        valueRow(g, tr("about.files"), String.valueOf(fileCount));
        valueRow(g, tr("about.disks"), String.valueOf(ComputerArchiveUnlockClientState.collectedDisks().size()));
        if (AntOSSettings.appEnabled("TASKS")) {
            if (ComputerTasksClientState.hasSnapshot()) {
                int total = 0;
                int complete = 0;
                for (var task : ComputerTasksClientState.get()) {
                    if (!task.visible()) continue;
                    total++;
                    if (task.complete()) complete++;
                }
                valueRow(g, tr("about.tasks"), tr("about.tasks_value", complete, total));
            } else valueRow(g, tr("about.tasks"), "-");
        }
        if (AntOSSettings.appEnabled("ANTAZON")) valueRow(g, tr("about.antcoins"), String.valueOf(AntazonClientState.wallet()));
    }

    private void renderPasswordDialog(GuiGraphics g, int x, int y, int w, int h) {
        kit.suppressHover(false);
        g.fill(x, y, x + w, y + h, Ui.alpha(Ui.BACKGROUND, 0xB0));
        kit.block(x, y, w, h);
        int pw = Math.min(w - 12, 240);
        boolean required = !hintMode && AnternetAccountClientState.mustChangePassword();
        int ph = hintMode ? 121 : 150;
        int px = x + (w - pw) / 2;
        int py = y + Math.max(4, (h - ph) / 2);
        g.fill(px, py, px + pw, py + ph, Ui.BACKGROUND);
        screen.box(g, px, py, px + pw, py + ph, Ui.BORDER_STRONG);
        g.drawString(font, Component.literal(screen.trimToWidth(tr(hintMode ? "hint.title" : required ? "password.required_title" : "password.title"), pw - 16)), px + 8, py + 7, Ui.TEXT_STRONG, false);
        int fieldY = py + 21;
        Field[] fields = dialogFields();
        String[] labels = hintMode ? new String[]{tr("password.current"), tr("hint.label")}
                : new String[]{tr("password.current"), tr("password.new"), tr("password.confirm")};
        for (int index = 0; index < fields.length; index++) {
            Field field = fields[index];
            g.drawString(font, Component.literal(labels[index]), px + 8, fieldY, Ui.TEXT_MUTED, false);
            kit.field(g, px + 8, fieldY + 9, pw - 16, 16, field.shown(), field.cursor, focused == field, "");
            kit.hit(px + 8, fieldY + 9, pw - 16, 16, () -> focus(field));
            fieldY += 29;
        }
        String message = passwordPending ? tr("password.working") : !passwordError.isEmpty() ? passwordError
                : required ? tr("password.required_detail") : hintMode ? tr("hint.detail") : "";
        g.drawString(font, Component.literal(screen.trimToWidth(message, pw - 16)), px + 8, py + ph - 37, passwordPending || passwordError.isEmpty() ? Ui.TEXT_MUTED : Ui.ERROR, false);
        int buttonWidth = Math.max(56, kit.labelWidth(tr("password.submit")));
        kit.button(g, px + pw - 8 - buttonWidth, py + ph - 24, buttonWidth, 18, tr("password.submit"), false, !passwordPending, this::submitDialog);
        if (!required) kit.button(g, px + pw - 12 - buttonWidth * 2, py + ph - 24, buttonWidth, 18, tr("password.cancel"), false, true, this::closePasswordDialog);
    }

    private Field[] dialogFields() {
        return hintMode ? new Field[]{currentPassword, hintField} : new Field[]{currentPassword, newPassword, confirmPassword};
    }

    private void openHintDialog() {
        openPasswordDialog();
        hintMode = true;
    }

    private void submitDialog() {
        if (hintMode) submitHint();
        else submitPassword();
    }

    private void submitHint() {
        if (passwordPending) return;
        if (currentPassword.value.isEmpty()) passwordError = tr("password.need_current");
        else if (!com.craisinlord.antos.content.computer.ComputerWorkspaceData.validHint(hintField.value, currentPassword.value)) passwordError = tr("hint.contains_password");
        else {
            passwordError = "";
            passwordPending = true;
            hintRevision = AnternetAccountClientState.hintRevision();
            AnternetAccountNetworking.setHint(currentPassword.value, hintField.value.strip());
        }
    }

    private void syncHint() {
        if (!passwordPending || !hintMode || AnternetAccountClientState.hintRevision() == hintRevision) return;
        passwordPending = false;
        int result = AnternetAccountClientState.hintResult();
        if (result == AnternetAccountResultPayload.HINT_SAVED) {
            boolean cleared = hintField.value.isBlank();
            closePasswordDialog();
            notice(tr(cleared ? "hint.cleared" : "hint.saved"));
            return;
        }
        passwordError = switch (result) {
            case AnternetAccountResultPayload.HINT_DENIED -> tr("password.denied");
            case AnternetAccountResultPayload.HINT_RATE_LIMITED -> tr("password.rate_limited");
            default -> tr("hint.invalid");
        };
    }

    private void openPasswordDialog() {
        hintMode = false;
        hintField.set("");
        currentPassword.set("");
        newPassword.set("");
        confirmPassword.set("");
        passwordError = "";
        passwordPending = false;
        passwordDialog = true;
        focused = currentPassword;
    }

    private void closePasswordDialog() {
        passwordDialog = false;
        hintMode = false;
        hintField.set("");
        passwordPending = false;
        currentPassword.set("");
        newPassword.set("");
        confirmPassword.set("");
        focused = null;
    }

    private void submitPassword() {
        if (passwordPending) return;
        if (currentPassword.value.isEmpty()) passwordError = tr("password.need_current");
        else if (newPassword.value.isEmpty()) passwordError = tr("password.need_new");
        else if (!newPassword.value.equals(confirmPassword.value)) passwordError = tr("password.mismatch");
        else {
            passwordError = "";
            passwordPending = true;
            passwordRevision = AnternetAccountClientState.passwordChangeRevision();
            AnternetAccountNetworking.changePassword(currentPassword.value, newPassword.value);
        }
    }

    private void syncPassword() {
        if (!passwordPending || AnternetAccountClientState.passwordChangeRevision() == passwordRevision) return;
        passwordPending = false;
        int result = AnternetAccountClientState.passwordChangeResult();
        if (result == AnternetAccountResultPayload.PASSWORD_CHANGED) {
            closePasswordDialog();
            notice(tr("password.changed"));
            return;
        }
        passwordError = switch (result) {
            case AnternetAccountResultPayload.PASSWORD_CHANGE_DENIED -> tr("password.denied");
            case AnternetAccountResultPayload.PASSWORD_CHANGE_RATE_LIMITED -> tr("password.rate_limited");
            default -> tr("password.invalid");
        };
    }

    private void syncProfile() {
        if (!AntOSSettings.appEnabled("ANTMAIL")) return;
        var mailbox = AntmailClientState.getMailbox();
        var result = mailbox != null ? mailbox : AntmailClientState.get();
        if (!profileLoaded && result != null && result.address() != null && !result.address().isBlank()) {
            var profile = AntmailClientState.getProfile(result.address());
            if (profile != null) {
                displayName.set(profile.displayName());
                iconItem.set(profile.avatarItem());
                profileLoaded = true;
            }
        }
        var operation = AntmailClientState.get();
        if (profilePending && operation != null && operation != profileBaseline
                && (operation.detail().startsWith("profile_") || operation.detail().startsWith("invalid_")
                || operation.detail().equals("unauthorized") || operation.detail().equals("unconfigured"))) {
            profilePending = false;
            profileBaseline = null;
            boolean saved = operation.detail().equals("profile_saved");
            profileStatus = saved ? tr("account.profile_saved")
                    : operation.detail().equals("invalid_item_id") ? tr("account.profile_bad_item")
                    : operation.detail().equals("invalid_display_name") ? tr("account.profile_bad_name")
                    : tr("account.profile_failed");
            profileStatusColor = saved ? Ui.TEXT : Ui.ERROR;
        }
    }

    private void saveProfile() {
        if (profilePending) return;
        profileBaseline = AntmailClientState.get();
        profilePending = true;
        profileStatus = tr("account.profile_saving");
        profileStatusColor = Ui.TEXT_MUTED;
        AntmailNetworking.updateProfile(displayName.value.trim(), iconItem.value.trim());
    }

    private void toggleFaceId() {
        if (screen.faceIdWaiting) return;
        screen.faceIdMessage = "";
        if (AnternetAccountClientState.faceIdLinked()) {
            if (!screen.faceIdLinkedToCurrentAccount()) return;
            screen.faceIdWaiting = true;
            screen.faceIdExpectedLinked = false;
            screen.faceIdRevisionAtRequest = AnternetAccountClientState.faceIdStatusRevision();
            screen.faceIdMessage = tr("face_id.unlinking");
            AnternetAccountNetworking.unlinkFaceId();
        } else {
            screen.submitFaceIdLink();
        }
    }

    private void logout() {
        screen.session.text.checkpoint();
        screen.session.paint.checkpoint();
        AntOSUiSounds.play(Cue.CLOSE);
        ComputerNetworking.logout();
        AnternetAccountNetworking.logout();
        AnternetAccountClientState.clear();
        AnternetAccountNetworking.checkFaceId();
        screen.loggedIn = false;
        screen.session.authenticated = false;
        screen.session.windows.clear();
        screen.session.antmail.state.pickingAttachment = false;
        screen.activeWindow = null;
    }

    private void replayTour() {
        screen.session.windows.clear();
        screen.activeWindow = null;
        screen.session.onboardingCompleted = false;
        screen.session.onboardingStep = 0;
    }

    private void replayAntazon() {
        screen.session.windows.clear();
        screen.activeWindow = null;
        ComputerNetworking.resetAntazonOnboarding();
        AntazonClientState.clear();
        screen.open("ANTAZON");
    }

    private void replayMerchant() {
        screen.session.merchant.replayTour();
        screen.open("MERCHANT");
    }

    private void confirm(String action) {
        confirmAction = action;
        confirmUntil = System.currentTimeMillis() + 3000L;
    }

    private void notice(String text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + 2500L;
    }

    private static String kilobytes(int size) {
        double value = size / 1024.0;
        return value >= 10 || value == Math.floor(value) ? String.valueOf((int) Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        if (!passwordDialog && scaleTrackVisible && button == 0
                && mouseY >= contentTop && mouseY < contentBottom
                && ComputerScreen.inside(scaleTrackX - 4, scaleTrackY, scaleTrackWidth + 8, 17, mouseX, mouseY)) {
            focused = null;
            draggingScale = true;
            dragScale(mouseX);
            AntOSUiSounds.play(Cue.CLICK);
            return true;
        }
        Field before = focused;
        if (!passwordDialog) focused = null;
        kit.click(mouseX, mouseY, button);
        if (passwordDialog && focused == null) focused = before;
        return true;
    }

    void drag(double mouseX, double mouseY) {
        if (draggingScale) dragScale(mouseX);
        else kit.drag(mouseY);
    }

    void release() {
        if (draggingScale) {
            draggingScale = false;
            setScale(pendingScale);
        }
        kit.release();
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        if (passwordDialog || draggingScale) return true;
        state.scroll = Math.max(0, Math.min(state.maximumScroll, state.scroll - (int) Math.signum(scrollY) * 16));
        return true;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (focused == null) return false;
        focused.type(codePoint);
        if (focused == search || focused == settingsSearch) state.scroll = 0;
        return true;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        boolean control = screen.hasControl(modifiers);
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (passwordDialog) {
            Field[] order = dialogFields();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (hintMode || !AnternetAccountClientState.mustChangePassword()) closePasswordDialog();
            }
            else if (keyCode == GLFW.GLFW_KEY_TAB) {
                int index = 0;
                for (int i = 0; i < order.length; i++) if (order[i] == focused) index = i;
                focused = order[Math.floorMod(index + (shift ? -1 : 1), order.length)];
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (focused == order[order.length - 1]) submitDialog();
                else {
                    int index = 0;
                    for (int i = 0; i < order.length; i++) if (order[i] == focused) index = i;
                    focused = order[Math.min(order.length - 1, index + 1)];
                }
            } else if (focused != null) focused.key(keyCode, control);
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_F) {
            focused = settingsSearch;
            return true;
        }
        if (focused == null && keyCode == GLFW.GLFW_KEY_ESCAPE && !settingsSearch.value.isEmpty()) {
            settingsSearch.set("");
            state.scroll = 0;
            return true;
        }
        if (focused == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (focused == settingsSearch) { settingsSearch.set(""); state.scroll = 0; }
            focused = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (focused == displayName || focused == iconItem) saveProfile();
            focused = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && (focused == displayName || focused == iconItem)) {
            focused = focused == displayName ? iconItem : displayName;
            return true;
        }
        focused.key(keyCode, control);
        if (focused == search || focused == settingsSearch) state.scroll = 0;
        return true;
    }

    private String tr(String key, Object... arguments) {
        return Component.translatable("computer.antos.settings." + key, arguments).getString();
    }

    private static final class Field {
        final int limit;
        final boolean masked;
        String value = "";
        int cursor;

        Field(int limit, boolean masked) {
            this.limit = limit;
            this.masked = masked;
        }

        void set(String text) {
            value = text == null ? "" : text.substring(0, Math.min(limit, text.length()));
            cursor = value.length();
        }

        String shown() {
            return masked ? "*".repeat(value.length()) : value;
        }

        void type(char codePoint) {
            if (codePoint < 32 || codePoint == 127 || value.length() >= limit) return;
            value = value.substring(0, cursor) + codePoint + value.substring(cursor);
            cursor++;
        }

        void key(int keyCode, boolean control) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (cursor > 0) {
                        value = value.substring(0, cursor - 1) + value.substring(cursor);
                        cursor--;
                    }
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (cursor < value.length()) value = value.substring(0, cursor) + value.substring(cursor + 1);
                }
                case GLFW.GLFW_KEY_LEFT -> cursor = Math.max(0, cursor - 1);
                case GLFW.GLFW_KEY_RIGHT -> cursor = Math.min(value.length(), cursor + 1);
                case GLFW.GLFW_KEY_HOME -> cursor = 0;
                case GLFW.GLFW_KEY_END -> cursor = value.length();
                case GLFW.GLFW_KEY_V -> {
                    if (!control) return;
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip == null) return;
                    for (char character : clip.replace("\r", "").replace("\n", " ").toCharArray()) type(character);
                }
                case GLFW.GLFW_KEY_A -> {
                    if (control) cursor = value.length();
                }
                default -> { }
            }
        }
    }

    static final class State {
        String category = "account";
        boolean showList;
        int scroll;
        int maximumScroll;
    }

    JsonObject saveResume() {
        JsonObject json = new JsonObject();
        json.addProperty("category", state.category);
        return json;
    }

    void loadResume(JsonObject json) {
        String category = ComputerResume.string(json, "category");
        if (java.util.Arrays.asList(CATEGORIES).contains(category)) state.category = category;
    }

}
