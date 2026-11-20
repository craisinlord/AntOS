package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.antmail.AntmailDirectory;
import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.network.AntmailNetworking;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Contacts, lists, groups and the viewer's team. Rows are built once per server response and only re-fitted when the
 * panel width changes, so a frame costs one pass over the visible rows.
 */
final class AntmailDirectoryPanel {
    private static final Gson JSON = new Gson();
    private static final int CONTROL = 17;
    private static final int ROW = 15;
    private static final int PITCH = 16;
    private static final int FOOTER = 20;
    private static final java.util.Set<String> DESTRUCTIVE = java.util.Set.of("delete", "transfer", "remove", "leave");

    private final List<Hit> hits = new ArrayList<>();
    private final List<Row> members = new ArrayList<>();
    private final List<Row> teamMembers = new ArrayList<>();
    private List<Row> rows = List.of();
    private Object rowsSource;
    private String mode = "contacts";
    private String query = "";
    private String value = "";
    private int field;
    private int cursor;
    private int page;
    private int scroll;
    private AntmailDirectory.View selected;
    private AntmailDirectory.Page decodedPage;
    private JsonObject response;
    private UUID account;
    private String expectedOperation = "page";
    private String expectedTarget = "";
    private String expectedQuery = "";
    private int expectedPage;
    private long searchAt;
    private int keyboardHit = -1;
    private Consumer<String> choose;
    /** Opens a conversation thread by recipient token ("conversation:&lt;id&gt;" or "team:&lt;id&gt;"). */
    private Consumer<String> conversation;
    private Runnable close;
    /** The viewer's team from the latest Groups/picker response, and whether its detail view is open. */
    private JsonObject team;
    private boolean teamOpen;
    /** Destructive operation waiting for a second click, and the one armed for the click being handled. */
    private String confirm;
    private String armed;
    private Object seenResult;
    private String notice = "";

    /** A click target. {@code label} names it for keyboard focus and the dev smoke driver. */
    private record Hit(int x, int y, int w, int h, Runnable action, String label) { }
    /** Centre of the first click target with this label, or null. Used by the dev smoke driver to click real controls. */
    int[] hitCenter(String label) {
        for (Hit hit : hits) if (hit.label.equals(label)) return new int[] {hit.x + hit.w / 2, hit.y + hit.h / 2};
        return null;
    }
    private enum Kind { HEADER, ITEM, PERSON }
    /** One list line: label on the left, muted detail on the right. Fitted text is cached per width. */
    private static final class Row {
        final Kind kind; final String label; final String detail; final String avatar; final Runnable action;
        private int fittedFor = -1; private String fittedLabel = ""; private String fittedDetail = ""; private int detailWidth;
        Row(Kind kind, String label, String detail, String avatar, Runnable action) {
            this.kind = kind; this.label = label; this.detail = detail == null ? "" : detail; this.avatar = avatar; this.action = action;
        }
        static Row header(String label) { return new Row(Kind.HEADER, label.toUpperCase(java.util.Locale.ROOT), "", null, null); }
        void fit(ComputerScreen screen, int width) {
            if (fittedFor == width) return;
            var font = Minecraft.getInstance().font;
            fittedDetail = detail.isEmpty() ? "" : screen.trimToWidth(detail, Math.max(0, width / 2));
            detailWidth = fittedDetail.isEmpty() ? 0 : font.width(fittedDetail) + 8;
            fittedLabel = screen.trimToWidth(label, Math.max(0, width - detailWidth));
            fittedFor = width;
        }
    }

    void open(String mode, Consumer<String> choose, Consumer<String> conversation, Runnable close) {
        this.mode = mode; this.choose = choose; this.conversation = conversation; this.close = close;
        query = ""; value = ""; page = 0; scroll = 0; selected = null; field = 0; cursor = 0; keyboardHit = -1;
        team = null; teamOpen = false;
        confirm = null; notice = ""; seenResult = AntmailClientState.get();
        clearResponse(); request("page", "", "");
    }
    private void clearResponse() { response = null; decodedPage = null; members.clear(); rows = List.of(); rowsSource = null; }
    private void request(String operation, String id, String input) {
        searchAt = 0; expectedOperation = operation; expectedTarget = id;
        expectedQuery = query.trim().toLowerCase(java.util.Locale.ROOT); expectedPage = page;
        AntmailNetworking.directory(operation, id, input, mode, query, selected == null ? 0 : selected.revision(), page);
    }
    private void searchChanged() {
        if (!mode.equals("picker") || selected != null) return;
        page = 0; scroll = 0; clearResponse(); searchAt = System.nanoTime() + 200_000_000L;
    }
    void tick() { if (searchAt != 0 && System.nanoTime() >= searchAt) request("page", "", ""); }
    private void change(String operation) { request(operation, selected.id().toString(), operation.equals("rename") ? query : value); }
    /** Runs a destructive operation only on its second consecutive activation. */
    private void guarded(String operation, Runnable action) {
        if (!operation.equals(armed)) { confirm = operation; return; }
        action.run();
    }
    private void activate(Runnable action) {
        armed = confirm; confirm = null; notice = "";
        try { action.run(); } finally { armed = null; }
    }
    /** Shows directory errors as readable text and reloads the open list after a concurrent edit. */
    private void observeErrors() {
        var result = AntmailClientState.get();
        if (result == null || result == seenResult) return;
        seenResult = result;
        if (!result.detail().startsWith("directory_error:")) return;
        String key = result.detail().substring("directory_error:".length());
        String translation = "computer.antos.mail.directory_error." + key;
        notice = net.minecraft.locale.Language.getInstance().has(translation) ? Component.translatable(translation).getString() : tr("directory_error.generic");
        if (key.equals("revision_conflict") && selected != null) { clearResponse(); request("view", selected.id().toString(), ""); }
    }
    private void back() {
        field = 0; keyboardHit = -1; confirm = null; notice = "";
        if (teamOpen) { teamOpen = false; scroll = 0; }
        else if (selected != null) { selected = null; query = ""; value = ""; scroll = 0; clearResponse(); request("page", "", ""); }
        else { searchAt = 0; close.run(); }
    }
    private void sync() {
        JsonObject current = AntmailClientState.directory();
        if (current == null || current == response || searchAt != 0 || !current.has("page")) return;
        var pageTag = current.getAsJsonObject("page");
        if (!pageTag.get("mode").getAsString().equals(mode) || !pageTag.get("query").getAsString().equals(expectedQuery)
                || pageTag.get("page").getAsInt() != expectedPage || !current.has("operation")
                || !current.get("operation").getAsString().equals(expectedOperation) || !current.get("target").getAsString().equals(expectedTarget)) return;
        response = current; decodedPage = JSON.fromJson(current.get("page"), AntmailDirectory.Page.class);
        account = UUID.fromString(current.get("account").getAsString());
        if (mode.equals("conversation") || mode.equals("picker")) {
            team = current.has("team") && current.get("team").isJsonObject() ? current.getAsJsonObject("team") : null;
            if (team == null) teamOpen = false;
            else AntmailClientState.rememberRecipient(teamToken(), team.get("ownerName").getAsString());
            buildTeamMembers();
        }
        if (current.has("selected")) {
            selected = JSON.fromJson(current.get("selected"), AntmailDirectory.View.class);
            AntmailClientState.rememberRecipient(selected.kind() + ":" + selected.id(), selected.name());
        }
        members.clear();
        if (selected != null && current.has("members")) for (var element : current.getAsJsonArray("members")) {
            var person = element.getAsJsonObject(); UUID id = UUID.fromString(person.get("id").getAsString());
            String address = person.get("address").isJsonNull() ? id.toString() : person.get("address").getAsString();
            String role = id.equals(selected.owner()) ? tr("role_owner") : selected.managers().contains(id) ? tr("role_manager") : "";
            if (id.equals(account)) role = role.isEmpty() ? tr("role_you") : role + " · " + tr("role_you");
            boolean editable = selected.canManage();
            members.add(new Row(Kind.PERSON, address, role, address, editable ? () -> { value = address; field = 2; keyboardHit = -1; cursor = value.length(); } : null));
        }
    }
    private void buildTeamMembers() {
        teamMembers.clear();
        if (team == null || !team.has("members")) return;
        String owner = team.has("owner") ? team.get("owner").getAsString() : "";
        for (var element : team.getAsJsonArray("members")) {
            var person = element.getAsJsonObject();
            String id = person.get("id").getAsString();
            String address = person.get("address").getAsString();
            String role = id.equals(owner) ? tr("role_owner") : "";
            if (account != null && id.equals(account.toString())) role = role.isEmpty() ? tr("role_you") : role + " · " + tr("role_you");
            teamMembers.add(new Row(Kind.PERSON, address, role, address, null));
        }
    }

    private static String tr(String key) { return Component.translatable("computer.antos.mail." + key).getString(); }
    private static String countLabel(int count) { return count == 1 ? tr("members_one") : Component.translatable("computer.antos.mail.members_many", count).getString(); }
    private static String kindName(String kind) { return tr("kind_" + kind); }
    private String teamToken() { return "team:" + team.get("id").getAsString(); }
    private int teamSize() { return team.has("members") ? team.getAsJsonArray("members").size() : 0; }
    /** "Team · Owner" in the viewer's language. */
    static String teamLabel(String ownerName) { return Component.translatable("computer.antos.mail.team_label", ownerName).getString(); }

    /** List rows for the current response, rebuilt only when a new response arrives. */
    private List<Row> rows() {
        if (rowsSource == response && rowsSource != null) return rows;
        List<Row> built = new ArrayList<>();
        if (decodedPage != null) {
            boolean picker = mode.equals("picker");
            for (var person : decodedPage.people()) {
                boolean aliased = !person.alias().isBlank();
                String address = person.address();
                built.add(new Row(Kind.PERSON, aliased ? person.alias() : address, aliased ? address : "", address, () -> {
                    if (picker) choose.accept(person.id().toString());
                    else { value = address; query = person.alias(); field = 2; keyboardHit = -1; cursor = value.length(); }
                }));
            }
            if (team != null && page == 0 && (mode.equals("conversation") || picker)) {
                String token = teamToken();
                if (!picker) built.add(Row.header(tr("your_team")));
                built.add(new Row(Kind.ITEM, teamLabel(team.get("ownerName").getAsString()), picker ? kindName("team") : countLabel(teamSize()), null,
                        () -> { if (picker) choose.accept(token); else { teamOpen = true; scroll = 0; keyboardHit = -1; } }));
            }
            if (!decodedPage.lists().isEmpty() && mode.equals("conversation") && team != null && page == 0) built.add(Row.header(tr("conversation")));
            for (var list : decodedPage.lists()) {
                built.add(new Row(Kind.ITEM, list.name(), picker ? kindName(list.kind()) : countLabel(list.memberCount()), null, () -> {
                    if (picker) choose.accept(list.kind() + ":" + list.id());
                    else { selected = list; query = list.name(); value = ""; scroll = 0; keyboardHit = -1; clearResponse(); request("view", list.id().toString(), ""); }
                }));
            }
            if (mode.equals("conversation") && response.has("history") && response.getAsJsonArray("history").size() > 0) {
                built.add(Row.header(tr("history")));
                for (var history : response.getAsJsonArray("history")) {
                    var row = history.getAsJsonObject();
                    UUID id = UUID.fromString(row.get("id").getAsString());
                    String kind = row.has("kind") && !row.get("kind").isJsonNull() ? row.get("kind").getAsString() : "group";
                    String name = row.has("name") && !row.get("name").isJsonNull() ? row.get("name").getAsString() : "";
                    boolean isTeam = "team".equals(kind);
                    String label = name.isBlank() ? id.toString().substring(0, 8) : isTeam ? teamLabel(name) : name;
                    String token = (isTeam ? "team:" : "conversation:") + id;
                    built.add(new Row(Kind.ITEM, label, kindName(isTeam ? "team" : "conversation"), null, () -> conversation.accept(token)));
                }
            }
        }
        rows = built; rowsSource = response;
        return rows;
    }

    private void button(ComputerScreen screen, ThemedGraphics g, int x, int y, int w, String label, Runnable action) {
        button(screen, g, x, y, w, label, Ui.FOCUS, Ui.TEXT, action);
    }
    private void button(ComputerScreen screen, ThemedGraphics g, int x, int y, int w, String label, int frame, int text, Runnable action) {
        if (screen.hovered(x, y, w, CONTROL)) g.fill(x, y, x + w, y + CONTROL, Ui.HOVER);
        screen.box(g, x, y, x + w, y + CONTROL, frame);
        screen.drawTabLabel(g, label, x, y - 1, w, text);
        hits.add(new Hit(x, y, w, CONTROL, action, label));
    }
    private void input(ComputerScreen screen, ThemedGraphics g, int x, int y, int w, int target, String label, String text) {
        g.fill(x, y, x + w, y + CONTROL, Ui.SURFACE);
        screen.box(g, x, y, x + w, y + CONTROL, field == target ? Ui.ACCENT : Ui.LINE);
        boolean placeholder = text.isBlank() && field != target;
        String visible = placeholder ? screen.trimToWidth(tr(label), w - 8) : AntmailRecipientPicker.viewport(text, field == target ? cursor : 0, field == target && screen.caretVisible(), w - 8, Minecraft.getInstance().font::width);
        g.drawString(Minecraft.getInstance().font, visible, x + 4, y + 5, placeholder ? Ui.TEXT_PLACEHOLDER : Ui.TEXT, false);
        hits.add(new Hit(x, y, w, CONTROL, () -> { field = target; keyboardHit = -1; cursor = text.length(); }, label));
    }
    /** Draws rows [scroll, scroll + capacity) from top; returns how many rows fit. */
    private int drawRows(AntmailApp app, ThemedGraphics g, List<Row> source, int x, int top, int w, int bottom) {
        ComputerScreen screen = app.screen;
        var font = Minecraft.getInstance().font;
        int capacity = Math.max(0, (bottom - top) / PITCH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, source.size() - capacity)));
        int rowWidth = w - 6;
        for (int i = scroll; i < Math.min(source.size(), scroll + capacity); i++) {
            Row row = source.get(i);
            int rowY = top + (i - scroll) * PITCH;
            if (row.kind == Kind.HEADER) {
                row.fit(screen, rowWidth - 4);
                g.drawString(font, row.fittedLabel, x + 2, rowY + 5, Ui.TEXT_MUTED, false);
                g.fill(x, rowY + ROW - 1, x + rowWidth, rowY + ROW, Ui.LINE);
                continue;
            }
            boolean interactive = row.action != null;
            if (interactive && screen.hovered(x, rowY, rowWidth, ROW)) g.fill(x, rowY, x + rowWidth, rowY + ROW, Ui.HOVER);
            int textX = x + 4;
            if (row.kind == Kind.PERSON && row.avatar != null) {
                var profile = AntmailClientState.getProfile(row.avatar);
                ProfileAvatar.draw(app, g, row.avatar, profile.avatarItem(), "", x + 1, rowY, ROW - 1);
                textX = x + ROW + 3;
            }
            row.fit(screen, rowWidth - (textX - x) - 4);
            g.drawString(font, row.fittedLabel, textX, rowY + 4, Ui.TEXT, false);
            if (!row.fittedDetail.isEmpty()) g.drawString(font, row.fittedDetail, x + rowWidth - 4 - (row.detailWidth - 8), rowY + 4, Ui.TEXT_MUTED, false);
            if (interactive) hits.add(new Hit(x, rowY, rowWidth, ROW, row.action, row.label));
        }
        screen.drawScrollbar(g, x + w - 4, top, Math.max(0, bottom - top), capacity, source.size(), scroll, Math.max(0, source.size() - capacity));
        return capacity;
    }

    /** Member rows that fit in an owner's detail view of {@code height} with {@code actions} buttons. */
    static int memberRows(int height, int actions) { return Math.max(0, (height - detailMembersTop(actions, true) - 12) / PITCH); }
    private static int actionsTop(boolean editable) { return editable ? 54 : 33; }
    private static int detailMembersTop(int actions, boolean editable) { return actionsTop(editable) + ((actions + 3) / 4) * 20 + 2; }

    void render(AntmailApp app, ThemedGraphics g, int x, int y, int w, int h) {
        ComputerScreen screen = app.screen;
        tick(); observeErrors(); sync(); hits.clear();
        button(screen, g, x, y, 40, tr("back"), this::back);
        if (teamOpen && team != null) renderTeam(app, g, x, y, w, h);
        else if (selected != null) { teamOpen = false; renderDetail(app, g, x, y, w, h); }
        else { teamOpen = false; renderList(app, g, x, y, w, h); }
        if (keyboardHit >= 0 && keyboardHit < hits.size()) { Hit focus = hits.get(keyboardHit); screen.box(g, focus.x - 1, focus.y - 1, focus.x + focus.w + 1, focus.y + focus.h + 1, Ui.ACCENT); }
    }
    private void title(ComputerScreen screen, ThemedGraphics g, int x, int y, int w, String title, String subtitle) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, screen.trimToWidth(title, w - 48), x + 46, y + 5, Ui.TEXT_STRONG, false);
        g.drawString(font, screen.trimToWidth(subtitle, w), x, y + 22, Ui.TEXT_MUTED, false);
    }
    private void drawNotice(ComputerScreen screen, ThemedGraphics g, int x, int y, int w) {
        if (notice.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        String text = screen.trimToWidth(notice, w);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y, Ui.ERROR, false);
    }

    private void renderList(AntmailApp app, ThemedGraphics g, int x, int y, int w, int h) {
        ComputerScreen screen = app.screen;
        var font = Minecraft.getInstance().font;
        boolean picker = mode.equals("picker");
        if (!picker) {
            String[] modes = {"contacts", "personal", "mailing", "conversation"};
            int width = Math.max(1, (w - 44) / 4);
            for (int i = 0; i < modes.length; i++) {
                String tab = modes[i]; int tabX = x + 44 + i * width; boolean active = mode.equals(tab);
                if (active) g.fill(tabX, y, tabX + width - 2, y + CONTROL, Ui.HOVER);
                button(screen, g, tabX, y, width - 2, tr(tab), active ? Ui.ACCENT : Ui.FOCUS, active ? Ui.TEXT_STRONG : Ui.TEXT_MUTED,
                        () -> { mode = tab; selected = null; query = ""; value = ""; scroll = 0; page = 0; keyboardHit = -1; field = 0; clearResponse(); request("page", "", ""); });
            }
        } else g.drawString(font, screen.trimToWidth(tr("picker"), w - 48), x + 46, y + 5, Ui.TEXT_STRONG, false);
        int top = y + 21;
        if (picker) {
            input(screen, g, x, top, w, 1, "search_name", query);
        } else if (mode.equals("contacts")) {
            int split = w * 2 / 5;
            input(screen, g, x, top, split - 2, 1, "alias", query);
            input(screen, g, x + split, top, w - split, 2, "address", value);
            top += CONTROL + 4;
            button(screen, g, x, top, w / 2 - 2, tr("save_contact"), () -> request("contact", value, query));
            button(screen, g, x + w / 2, top, w - w / 2, tr("remove"), () -> request("uncontact", value, ""));
        } else {
            input(screen, g, x, top, w - 66, 1, "search_or_create", query);
            button(screen, g, x + w - 62, top, 62, tr("create"), () -> request("create", UUID.randomUUID().toString(), query));
        }
        top += CONTROL + 4;
        int bottom = y + h - FOOTER;
        if (decodedPage == null) {
            g.drawString(font, tr("loading"), x + 4, top + 4, Ui.TEXT_PLACEHOLDER, false);
        } else {
            List<Row> list = rows();
            if (list.isEmpty()) g.drawString(font, screen.trimToWidth(tr(emptyKey()), w - 8), x + 4, top + 4, Ui.TEXT_PLACEHOLDER, false);
            else drawRows(app, g, list, x, top, w, bottom);
            boolean more = decodedPage.more() || response.has("historyMore") && response.get("historyMore").getAsBoolean();
            if (page > 0) button(screen, g, x, y + h - CONTROL, 50, tr("previous"), () -> { page--; scroll = 0; clearResponse(); request("page", "", ""); });
            if (more) button(screen, g, x + w - 50, y + h - CONTROL, 50, tr("next"), () -> { page++; scroll = 0; clearResponse(); request("page", "", ""); });
        }
        drawNotice(screen, g, x + 54, y + h - 12, w - 108);
    }
    private String emptyKey() {
        return switch (mode) {
            case "contacts" -> "empty_contacts";
            case "conversation" -> "empty_groups";
            case "picker" -> "empty_picker";
            default -> "empty_lists";
        };
    }

    private void renderDetail(AntmailApp app, ThemedGraphics g, int x, int y, int w, int h) {
        ComputerScreen screen = app.screen;
        var font = Minecraft.getInstance().font;
        String role = selected.isOwner() ? tr("role_owner") : selected.canManage() ? tr("role_manager") : selected.role() != null && !selected.role().isEmpty() ? tr("role_member") : "";
        String subtitle = kindName(selected.kind()) + " · " + countLabel(selected.memberCount()) + (role.isEmpty() ? "" : " · " + role);
        title(screen, g, x, y, w, selected.name(), subtitle);
        boolean editable = selected.canManage();
        if (editable) {
            input(screen, g, x, y + 33, w / 2 - 2, 1, "name", query);
            input(screen, g, x + w / 2, y + 33, w - w / 2, 2, "address", value);
        }
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>(); List<String> operations = new ArrayList<>();
        if (selected.kind().equals("conversation")) { String token = "conversation:" + selected.id(); labels.add(tr("open_thread")); actions.add(() -> conversation.accept(token)); operations.add(""); }
        else { labels.add(tr("use")); actions.add(() -> choose.accept(selected.kind() + ":" + selected.id())); operations.add(""); }
        List<String> allowed = new ArrayList<>();
        if (editable) allowed.addAll(List.of("add", "remove", "rename"));
        if (selected.isOwner()) allowed.addAll(selected.kind().equals("personal") ? List.of("delete") : List.of("promote", "demote", "transfer", "delete"));
        if (!selected.isOwner() && !selected.kind().equals("personal") && selected.role() != null && !selected.role().isEmpty()) allowed.add("leave");
        for (String operation : allowed) {
            boolean closes = operation.equals("delete") || operation.equals("leave");
            Runnable run = () -> { change(operation); if (closes) { selected = null; clearResponse(); } };
            labels.add(tr(operation.equals(confirm) ? "confirm" : operation));
            actions.add(DESTRUCTIVE.contains(operation) ? () -> guarded(operation, run) : run);
            operations.add(operation);
        }
        int actionsTop = y + actionsTop(editable);
        int cell = w / 4;
        for (int i = 0; i < labels.size(); i++) {
            String operation = operations.get(i);
            boolean armedHere = operation.equals(confirm);
            boolean danger = DESTRUCTIVE.contains(operation);
            button(screen, g, x + i % 4 * cell, actionsTop + i / 4 * 20, cell - 2, labels.get(i),
                    armedHere ? Ui.ERROR : Ui.FOCUS, armedHere ? Ui.ERROR : danger ? Ui.TEXT_MUTED : Ui.TEXT, actions.get(i));
        }
        int top = y + detailMembersTop(labels.size(), editable);
        if (members.isEmpty() && selected.memberCount() > 0) g.drawString(font, screen.trimToWidth(tr("roster_hidden"), w - 8), x + 4, top + 4, Ui.TEXT_PLACEHOLDER, false);
        else drawRows(app, g, members, x, top, w, y + h - 12);
        drawNotice(screen, g, x, y + h - 10, w);
    }

    /** Read-only team detail: the team is managed in Tasks, so this only opens the thread or starts a message. */
    private void renderTeam(AntmailApp app, ThemedGraphics g, int x, int y, int w, int h) {
        ComputerScreen screen = app.screen;
        title(screen, g, x, y, w, teamLabel(team.get("ownerName").getAsString()), countLabel(teamSize()) + " · " + tr("managed_in_tasks"));
        String token = teamToken();
        button(screen, g, x, y + 33, w / 2 - 2, tr("open_thread"), () -> conversation.accept(token));
        button(screen, g, x + w / 2, y + 33, w - w / 2, tr("message_team"), Ui.ACCENT, Ui.TEXT_STRONG, () -> choose.accept(token));
        drawRows(app, g, teamMembers, x, y + 56, w, y + h - 12);
        drawNotice(screen, g, x, y + h - 10, w);
    }

    boolean click(double x, double y) {
        for (Hit hit : hits) if (x >= hit.x && y >= hit.y && x < hit.x + hit.w && y < hit.y + hit.h) { activate(hit.action); return true; }
        field = 0; keyboardHit = -1; confirm = null; return true;
    }
    void scroll(double amount) { scroll = Math.max(0, scroll - (int) Math.signum(amount)); }
    boolean type(char character) { if (character >= 32 && field > 0) { confirm = null; replace(String.valueOf(character)); } return true; }
    private void replace(String text) {
        String current = field == 1 ? query : value; cursor = Math.min(cursor, current.length());
        String insert = text.replaceAll("[\\p{Cntrl}]", ""); insert = insert.substring(0, Math.min(insert.length(), Math.max(0, 64 - current.length())));
        String updated = current.substring(0, cursor) + insert + current.substring(cursor); cursor += insert.length();
        if (field == 1) { query = updated; if (!updated.equals(current)) searchChanged(); } else value = updated;
    }
    boolean key(int key, int modifiers) {
        if (key == InputConstants.KEY_ESCAPE) { if (field > 0) field = 0; else back(); return true; }
        if (key == InputConstants.KEY_TAB) { int delta = (modifiers & 1) != 0 ? -1 : 1; keyboardHit = hits.isEmpty() ? -1 : Math.floorMod(keyboardHit + delta, hits.size()); field = 0; return true; }
        if (field == 0 && keyboardHit >= 0 && keyboardHit < hits.size() && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) { activate(hits.get(keyboardHit).action); return true; }
        if (field == 0) { if (key == InputConstants.KEY_DOWN) scroll++; if (key == InputConstants.KEY_UP) scroll = Math.max(0, scroll - 1); return true; }
        confirm = null;
        if (key == InputConstants.KEY_V && (modifiers & 2) != 0) { replace(Minecraft.getInstance().keyboardHandler.getClipboard()); return true; }
        String current = field == 1 ? query : value; String updated = current;
        if (key == InputConstants.KEY_BACKSPACE && cursor > 0) { updated = current.substring(0, cursor - 1) + current.substring(cursor); cursor--; }
        if (key == InputConstants.KEY_DELETE && cursor < current.length()) updated = current.substring(0, cursor) + current.substring(cursor + 1);
        if (key == InputConstants.KEY_LEFT) cursor = Math.max(0, cursor - 1);
        if (key == InputConstants.KEY_RIGHT) cursor = Math.min(current.length(), cursor + 1);
        if (key == InputConstants.KEY_HOME) cursor = 0;
        if (key == InputConstants.KEY_END) cursor = current.length();
        if (field == 1) { query = updated; if (!updated.equals(current)) searchChanged(); } else value = updated;
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { page = 0; clearResponse(); request("page", "", ""); }
        return true;
    }
}
