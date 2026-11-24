package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;

final class StudioNew extends StudioPopup {
    private record Option(String title, String note, StudioIcon icon, boolean typed, Runnable action) { }

    private final AntStudioScreen screen;
    private final StudioStore store;
    private final AuthoringKind preferred;
    private StudioKey disk;
    private String text = "";
    private int selected;
    private List<Option> options = List.of();

    StudioNew(AntStudioScreen screen, AuthoringKind preferred, StudioKey disk) {
        this.screen = screen;
        store = screen.store;
        this.preferred = preferred;
        this.disk = disk;
    }

    private static String t(String key, Object... arguments) { return StudioText.t(key, arguments); }

    String field() { return "new:text"; }

    @Override
    boolean shade() { return true; }

    @Override
    void layout(StudioKit kit) { center(kit, 390, 296); }

    void type(String value) {
        text = value;
        selected = 0;
        build();
    }

    private StudioKey made(AuthoringKind kind, String title) {
        StudioKey key = screen.create(kind, title);
        if (disk != null && !StudioStore.diskField(kind).isEmpty() && kind != AuthoringKind.MESSAGE) store.putOnDisk(key, disk);
        return key;
    }

    private String title(AuthoringKind kind) { return text.isBlank() ? t("untitled", StudioText.kind(kind)) : text.trim(); }

    private void build() {
        List<Option> result = new ArrayList<>();
        StudioParse.Amount amount = text.isBlank() ? null : StudioParse.amount(text);
        if (amount != null) {
            StudioIcon icon = amount.entity() ? StudioIcon.entity(amount.id()) : StudioIcon.item(amount.id());
            String verb = amount.verb().isEmpty() ? amount.entity() ? "kill" : "collect" : amount.verb();
            result.add(new Option(t("new.goal_task", t("goal." + verb), amount.count(), amount.name()), t("new.goal_task.note"), icon, true, () -> {
                StudioKey key = made(AuthoringKind.TASK, t("new.goal_title", t("goal." + verb), amount.name()));
                store.edit(key, "", body -> {
                    JsonArray goals = new JsonArray();
                    goals.add(amount.goal());
                    body.add("objectives", goals);
                });
                finish(key);
            }));
            if (!amount.entity()) result.add(new Option(t("new.sell_item", amount.count(), amount.name()), t("new.sell_item.note"), StudioCategory.ANTAZON.icon, true, () -> {
                StudioKey key = made(AuthoringKind.PRODUCT, amount.name());
                store.edit(key, "", body -> {
                    JsonArray rewards = new JsonArray();
                    JsonObject reward = new JsonObject();
                    reward.addProperty("item", amount.id());
                    reward.addProperty("count", Math.min(108, amount.count()));
                    rewards.add(reward);
                    body.add("rewards", rewards);
                    JsonObject thumbnail = new JsonObject();
                    thumbnail.addProperty("item", amount.id());
                    body.add("thumbnail", thumbnail);
                });
                finish(key);
            }));
        }
        List<AuthoringKind> order = new ArrayList<>(List.of(AuthoringKind.TASK, AuthoringKind.ARCHIVE_ENTRY, AuthoringKind.MESSAGE, AuthoringKind.PRODUCT, AuthoringKind.PROGRAM, AuthoringKind.TERMINAL_COMMAND));
        if (preferred != null && order.remove(preferred)) order.add(0, preferred);
        for (AuthoringKind kind : order) {
            StudioCategory category = StudioCategory.of(kind);
            String label = text.isBlank() || amount != null ? t("new." + kind.key()) : t("new.called", StudioText.kind(kind), text.trim());
            result.add(new Option(label, t("new." + kind.key() + ".note"), category.icon, false, () -> finish(kind == AuthoringKind.MESSAGE && disk != null
                    ? screen.createMail(disk, "disk_installed", title(kind)) : made(kind, title(kind)))));
        }
        options = List.copyOf(result);
        selected = Math.max(0, Math.min(options.size() - 1, selected));
    }

    private void finish(StudioKey key) {
        boolean again = screen.kit.control;
        if (again) {
            store.say(t("new.made", store.label(key)), true);
            text = "";
            selected = 0;
            build();
            screen.kit.focus(field());
            return;
        }
        screen.closePopup(this);
        screen.open(key, disk);
        screen.focusTitle(key);
    }

    @Override
    void render(StudioKit kit) {
        build();
        kit.box(x, y, w, h, StudioTheme.window, StudioTheme.accent);
        kit.field(field(), x + 7, y + 7, w - 14, text, new StudioKit.Field().hint(t("new.hint")).max(128).enter(() -> options.get(selected).action().run()), value -> {
            text = value;
            selected = 0;
        });
        kit.fill(x + 1, y + 28, w - 2, 1, StudioTheme.line);
        kit.text(t("new.put_on"), x + 8, y + 36, 60, StudioTheme.muted);
        int cx = x + 12 + kit.width(t("new.put_on"));
        String diskLabel = disk == null ? t("new.no_floppy") : store.label(disk);
        int size = Math.min(150, kit.width(diskLabel) + 34);
        int dropX = cx;
        kit.drop(cx, y + 32, size, diskLabel, disk == null ? null : StudioIcons.of(store, disk), click -> {
            StudioMenu menu = new StudioMenu(screen, dropX, y + 32, 16).check(t("new.no_floppy"), disk == null, () -> disk = null);
            for (StudioKey key : store.keys(AuthoringKind.DISK)) menu.item(store.label(key), StudioIcons.of(store, key), "", true, key.equals(disk), () -> disk = key);
            screen.popup(menu);
        });
        if (disk != null) kit.text(t("new.from_context"), cx + size + 8, y + 36, x + w - 12 - cx - size - 8, StudioTheme.soft);
        kit.fill(x + 1, y + 52, w - 2, 1, StudioTheme.line);
        int top = y + 56;
        int bottom = y + h - 24;
        int content = options.size() * 28 + (options.stream().anyMatch(Option::typed) ? 26 : 13);
        int offset = kit.scroll("new", x + 2, top, w - 4, bottom - top, content);
        kit.clip(x + 2, top, w - 4, bottom - top);
        int rowY = top - offset;
        boolean typed = false;
        boolean plain = false;
        for (int index = 0; index < options.size(); index++) {
            Option option = options.get(index);
            if (option.typed() && !typed) {
                kit.text(t("new.from_text"), x + 9, rowY + 2, w - 18, StudioTheme.soft);
                rowY += 13;
                typed = true;
            }
            if (!option.typed() && !plain) {
                kit.text(t(typed ? "new.or" : "new.start"), x + 9, rowY + 2, w - 18, StudioTheme.soft);
                rowY += 13;
                plain = true;
            }
            if (kit.pointed(x + 2, rowY, w - 6, 27)) selected = index;
            if (selected == index) {
                kit.fill(x + 2, rowY, w - 4, 27, StudioTheme.selected);
                kit.fill(x + 2, rowY, 2, 27, StudioTheme.accent);
            }
            option.icon().draw(kit, x + 11, rowY + 5, 16);
            kit.text(option.title(), x + 34, rowY + 4, w - 90, selected == index ? StudioTheme.strong : StudioTheme.text);
            kit.text(option.note(), x + 34, rowY + 15, w - 46, StudioTheme.soft);
            if (selected == index) kit.right("Enter", x + w - 10, rowY + 4, StudioTheme.soft);
            kit.press(x + 2, rowY, w - 6, 27, option.action());
            rowY += 28;
        }
        kit.unclip();
        kit.fill(x + 1, bottom, w - 2, 23, StudioTheme.panel);
        kit.fill(x + 1, bottom, w - 2, 1, StudioTheme.line);
        int left = x + 7;
        int anyX = left;
        left += kit.button(left, bottom + 4, t("new.any"), StudioKit.NORMAL, () -> {
            StudioMenu menu = new StudioMenu(screen, anyX, bottom + 4, 16);
            for (StudioCategory category : StudioCategory.values()) {
                menu.heading(category.title());
                for (AuthoringKind kind : category.kinds) menu.item(StudioText.kind(kind), () -> finish(made(kind, title(kind))));
            }
            screen.popup(menu);
        }) + 4;
        kit.button(left, bottom + 4, t("new.copy"), StudioKit.NORMAL, () -> {
            screen.closePopup(this);
            screen.popup(new StudioFind(screen, true));
        });
        int floppy = kit.buttonWidth(t("new.floppy"));
        kit.button(x + w - floppy - 7, bottom + 4, floppy, 16, t("new.floppy"), StudioKit.NORMAL, true, "", () -> finish(screen.create(AuthoringKind.DISK, title(AuthoringKind.DISK))));
    }

    @Override
    boolean key(int key, int modifiers) {
        if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
            selected = Math.floorMod(selected + (key == InputConstants.KEY_DOWN ? 1 : -1), Math.max(1, options.size()));
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            build();
            options.get(selected).action().run();
            return true;
        }
        return false;
    }
}
