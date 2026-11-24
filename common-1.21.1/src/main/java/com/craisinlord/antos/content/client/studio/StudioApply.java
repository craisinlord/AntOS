package com.craisinlord.antos.content.client.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class StudioApply extends StudioPopup {
    private final AntStudioScreen screen;
    private final StudioStore store;
    private final Set<String> ignored = new LinkedHashSet<>();
    private int contentHeight = 100;
    private int x0;
    private int w0;
    private int at;

    StudioApply(AntStudioScreen screen) {
        this.screen = screen;
        store = screen.store;
    }

    private static String t(String key, Object... arguments) { return StudioText.t(key, arguments); }

    @Override
    boolean shade() { return true; }

    @Override
    void layout(StudioKit kit) { center(kit, 440, 310); }

    private List<StudioKey> broken() { return store.drafts().stream().map(doc -> doc.key).filter(store::broken).toList(); }
    private List<StudioKey> ready() { return store.drafts().stream().map(doc -> doc.key).filter(key -> !store.broken(key)).toList(); }

    private boolean fixable() {
        for (StudioKey key : broken()) for (StudioProblems.Problem problem : store.problems(key)) if (!problem.issue().warning() && problem.fix() == null) return false;
        return true;
    }

    @Override
    void render(StudioKit kit) {
        List<StudioKey> broken = broken();
        List<StudioKey> ready = ready();
        int total = broken.size() + ready.size();
        kit.box(x, y, w, h, StudioTheme.window, StudioTheme.accent);
        kit.text(t("save_apply"), x + 9, y + 9, w / 2, StudioTheme.strong);
        kit.right(t("apply.count", total, ready.size()), x + w - 9, y + 9, StudioTheme.muted);
        kit.fill(x + 1, y + 24, w - 2, 1, StudioTheme.line);
        int top = y + 25;
        int bottom = y + h - 25;
        int offset = kit.scroll("apply", x + 1, top, w - 2, bottom - top, contentHeight);
        kit.clip(x + 1, top, w - 2, bottom - top);
        x0 = x + 9;
        w0 = w - 20;
        at = top + 6 - offset;
        if (!broken.isEmpty()) {
            kit.text(t("apply.fix_first"), x0, at, w0, StudioTheme.muted);
            at += 12;
            for (StudioKey key : broken) card(kit, key, true);
        }
        if (!ready.isEmpty()) {
            kit.text(t("apply.ready"), x0, at, w0, StudioTheme.muted);
            at += 12;
            for (StudioKey key : ready) card(kit, key, false);
        }
        List<StudioProblems.Problem> warnings = store.problems().stream().filter(problem -> problem.issue().warning() && !ignored.contains(id(problem))).toList();
        if (!warnings.isEmpty()) {
            kit.text(t("apply.look"), x0, at, w0, StudioTheme.muted);
            at += 13;
            for (StudioProblems.Problem problem : warnings) warning(kit, problem);
        }
        if (total == 0) {
            kit.wrap(t("apply.nothing"), x0, at + 6, w0, StudioTheme.soft);
            at += 24;
        }
        contentHeight = at - (top - offset) + 8;
        kit.unclip();
        kit.fill(x + 1, bottom, w - 2, 24, StudioTheme.panel);
        kit.fill(x + 1, bottom, w - 2, 1, StudioTheme.line);
        boolean idle = !store.saving();
        int left = x + 8;
        left += kit.button(left, bottom + 4, t("apply.keep"), StudioKit.NORMAL, () -> screen.closePopup(this)) + 4;
        if (total > 0) kit.button(left, bottom + 4, kit.buttonWidth(t("apply.later")), 16, t("apply.later"), StudioKit.NORMAL, idle && !ready.isEmpty(), t("apply.later.tip"), () -> run(ready, false));
        int right = x + w - 8;
        if (!idle) {
            kit.right(t("saving"), right, bottom + 8, StudioTheme.accent);
            return;
        }
        if (total == 0) return;
        if (broken.isEmpty()) {
            String label = t("apply.all", total);
            int size = kit.buttonWidth(label);
            kit.button(right - size, bottom + 4, size, 16, label, StudioKit.PRIMARY, true, "", () -> run(null, true));
            return;
        }
        if (fixable()) {
            String label = t("apply.fix_all", total);
            int size = kit.buttonWidth(label);
            right -= size;
            kit.button(right, bottom + 4, size, 16, label, StudioKit.PRIMARY, true, t("apply.fix_all.tip"), this::fixAll);
            right -= 4;
        }
        String label = t("apply.some", ready.size());
        int size = kit.buttonWidth(label);
        kit.button(right - size, bottom + 4, size, 16, label, fixable() ? StudioKit.NORMAL : StudioKit.PRIMARY, !ready.isEmpty(), "", () -> run(ready, true));
    }

    private static String id(StudioProblems.Problem problem) { return problem.key() + problem.issue().code() + problem.issue().field(); }

    void fixAll() {
        for (int pass = 0; pass < 5; pass++) {
            List<StudioProblems.Problem> fixes = new ArrayList<>();
            for (StudioKey key : broken()) {
                StudioProblems.Problem first = store.problems(key).stream().filter(problem -> !problem.issue().warning() && problem.fix() != null).findFirst().orElse(null);
                if (first != null) fixes.add(first);
            }
            if (fixes.isEmpty()) break;
            fixes.forEach(problem -> problem.fix().run());
        }
        if (broken().isEmpty()) run(null, true);
    }

    private void run(List<StudioKey> keys, boolean reload) {
        store.save(keys, reload, success -> {
            if (success && broken().isEmpty()) screen.closePopup(this);
        });
    }

    private void card(StudioKit kit, StudioKey key, boolean error) {
        StudioStore.Doc doc = store.loaded(key);
        if (doc == null) return;
        int start = at;
        List<String[]> changes = changes(store, doc);
        List<StudioProblems.Problem> problems = store.problems(key).stream().filter(problem -> !problem.issue().warning()).toList();
        int height = 20 + (error ? problems.size() * 17 : Math.max(1, Math.min(3, changes.size())) * 11 + 4);
        if (kit.visible(start, height)) {
            kit.box(x0, start, w0, height, StudioTheme.section, error ? StudioTheme.error : StudioTheme.line);
            kit.dot(x0 + 6, start + 8, error, false);
            StudioIcons.of(store, key).draw(kit, x0 + 15, start + 3, 13);
            int nameWidth = kit.text(doc.removed ? t("apply.removing", store.label(key)) : store.label(key), x0 + 33, start + 6, w0 - 190, StudioTheme.strong);
            kit.text(StudioText.kind(key.kind()), x0 + 39 + nameWidth, start + 6, 90, StudioTheme.soft);
            int right = x0 + w0 - 5;
            int discard = kit.buttonWidth(t("discard"));
            kit.button(right - discard, start + 3, discard, 14, t("discard"), StudioKit.NORMAL, !store.saving(), "", () -> screen.confirm(t("discard.ask", store.label(key)), t("discard"), () -> store.discard(key)));
            right -= discard + 3;
            int open = kit.buttonWidth(t("open"));
            kit.button(right - open, start + 3, open, 14, t("open"), StudioKit.NORMAL, true, "", () -> {
                screen.closePopup(this);
                screen.open(key);
            });
            int rowY = start + 20;
            if (error) for (StudioProblems.Problem problem : problems) {
                int edge = x0 + w0 - 5;
                String topic = StudioHelp.topic(problem.issue().code());
                if (topic != null) {
                    int why = kit.buttonWidth(t("why"));
                    edge -= why;
                    kit.button(edge, rowY, why, 14, t("why"), StudioKit.GHOST, true, "", () -> screen.help(topic));
                    edge -= 2;
                }
                if (problem.fix() != null) {
                    int fix = Math.min(130, kit.buttonWidth(problem.fixLabel()));
                    edge -= fix;
                    kit.button(edge, rowY, fix, 14, problem.fixLabel(), StudioKit.NORMAL, true, "", problem.fix());
                    edge -= 4;
                }
                kit.text(problem.text(), x0 + 15, rowY + 3, edge - x0 - 18, StudioTheme.error);
                rowY += 17;
            }
            else for (int index = 0; index < Math.min(3, changes.size()); index++) {
                String[] change = changes.get(index);
                int cx = x0 + 15;
                cx += kit.text(change[0], cx, rowY, w0 / 3, StudioTheme.muted) + 5;
                if (!change[1].isEmpty()) {
                    cx += kit.text(change[1], cx, rowY, w0 / 4, StudioTheme.soft) + 4;
                    cx += kit.text("->", cx, rowY, 14, StudioTheme.muted) + 4;
                }
                kit.text(change[2], cx, rowY, x0 + w0 - 40 - cx, StudioTheme.accent);
                if (index == 2 && changes.size() > 3) kit.right(t("apply.more", changes.size() - 3), x0 + w0 - 6, rowY, StudioTheme.soft);
                rowY += 11;
            }
        }
        at += height + 5;
    }

    private void warning(StudioKit kit, StudioProblems.Problem problem) {
        int right = x0 + w0;
        int ignore = kit.buttonWidth(t("ignore"));
        kit.button(right - ignore, at, ignore, 14, t("ignore"), StudioKit.GHOST, true, "", () -> ignored.add(id(problem)));
        right -= ignore + 3;
        if (problem.fix() != null) {
            int fix = Math.min(120, kit.buttonWidth(problem.fixLabel()));
            kit.button(right - fix, at, fix, 14, problem.fixLabel(), StudioKit.NORMAL, true, "", problem.fix());
            right -= fix + 3;
        }
        int open = kit.buttonWidth(t("open"));
        kit.button(right - open, at, open, 14, t("open"), StudioKit.NORMAL, true, "", () -> {
            screen.closePopup(this);
            screen.open(problem.key());
        });
        right -= open + 4;
        kit.dot(x0, at + 5, true, true);
        kit.text("\"" + store.label(problem.key()) + "\": " + problem.text(), x0 + 9, at + 3, right - x0 - 10, StudioTheme.text);
        at += 17;
    }

    static List<String[]> changes(StudioStore store, StudioStore.Doc doc) {
        List<String[]> result = new ArrayList<>();
        if (doc.removed) {
            result.add(new String[]{t("apply.removed_note"), "", ""});
            return result;
        }
        if (doc.base == null) {
            result.add(new String[]{t("apply.new"), "", doc.idEdited ? doc.key.id() : ""});
            return result;
        }
        Set<String> names = new LinkedHashSet<>(doc.body.keySet());
        names.addAll(doc.base.keySet());
        for (String name : names) {
            JsonElement before = doc.base.get(name);
            JsonElement after = doc.body.get(name);
            if (java.util.Objects.equals(before, after)) continue;
            if (before != null && after != null && before.isJsonArray() && after.isJsonArray()) {
                JsonArray old = before.getAsJsonArray();
                JsonArray now = after.getAsJsonArray();
                if (old.size() != now.size()) result.add(new String[]{StudioText.field(name), t("apply.items", old.size()), t("apply.items", now.size())});
                else {
                    boolean detailed = false;
                    for (int index = 0; index < now.size() && !detailed; index++) if (!now.get(index).equals(old.get(index))) {
                        String[] inner = inner(old.get(index), now.get(index));
                        if (inner != null) {
                            result.add(new String[]{StudioText.field(name) + ": " + inner[0], inner[1], inner[2]});
                            detailed = true;
                        }
                    }
                    if (!detailed) result.add(new String[]{StudioText.field(name), "", t("apply.changed")});
                }
                continue;
            }
            result.add(new String[]{StudioText.field(name), shorten(before), shorten(after)});
        }
        if (result.isEmpty() && doc.conflict) result.add(new String[]{t("apply.changed"), "", ""});
        return result;
    }

    private static String[] inner(JsonElement before, JsonElement after) {
        if (!before.isJsonObject() || !after.isJsonObject()) return new String[]{"", shorten(before), shorten(after)};
        JsonObject old = before.getAsJsonObject();
        JsonObject now = after.getAsJsonObject();
        for (String name : now.keySet()) if (!now.get(name).equals(old.get(name))) return new String[]{StudioText.field(name), shorten(old.get(name)), shorten(now.get(name))};
        return null;
    }

    private static String shorten(JsonElement value) {
        if (value == null) return t("apply.unset");
        if (value.isJsonPrimitive()) {
            String text = value.getAsJsonPrimitive().isString() ? StudioText.read(value.getAsString()) : value.getAsString();
            text = text.replace('\n', ' ');
            if (text.contains(":") && !text.contains(" ") && net.minecraft.resources.ResourceLocation.tryParse(text) != null && !StudioIcon.stack(text).isEmpty()) return StudioText.itemName(text);
            return text.isEmpty() ? t("apply.unset") : text.length() > 60 ? text.substring(0, 57) + "..." : text;
        }
        return value.isJsonArray() ? t("apply.items", value.getAsJsonArray().size()) : t("apply.changed");
    }
}
