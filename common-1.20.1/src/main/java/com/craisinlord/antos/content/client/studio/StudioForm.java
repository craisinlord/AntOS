package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.world.item.ItemStack;

class StudioForm {
    record RowDrag(StudioKey key, String path, int index) { }
    interface Content { void draw(int x, int w); }

    final AntStudioScreen screen;
    final StudioStore store;
    final StudioKit kit;
    StudioKey key;
    StudioNode root;
    boolean advanced;
    int x;
    int w;
    int y;
    String hovered = "";
    private int left;
    private int full;
    private int formTop;
    private int formY;
    private int formH;
    private List<StudioProblems.Problem> problems = List.of();
    private final Map<String, Integer> heights = new HashMap<>();
    private final Map<String, Integer> tops = new HashMap<>();
    private Set<String> claimed = new HashSet<>();
    private Set<String> claiming = new HashSet<>();
    private String jump = "";
    private final Set<String> reported = new HashSet<>();

    StudioForm(AntStudioScreen screen) {
        this.screen = screen;
        this.store = screen.store;
        this.kit = screen.kit;
    }

    String scrollId() { return "form:" + key; }

    void render(StudioKey shown, int fx, int fy, int fw, int fh, Runnable content) {
        key = shown;
        root = new StudioNode(store, shown);
        advanced = screen.prefs.advanced;
        problems = store.problems(shown);
        claiming = new HashSet<>();
        formY = fy;
        formH = fh;
        String totalId = "total:" + shown;
        int total = heights.getOrDefault(totalId, fh);
        if (!jump.isEmpty() && tops.containsKey(shown + jump)) {
            kit.scrollTo(scrollId(), tops.get(shown + jump) - 4);
            jump = "";
        }
        int offset = kit.scroll(scrollId(), fx, fy, fw, fh, total);
        kit.clip(fx, fy, fw, fh);
        left = fx + 6;
        full = fw - 14;
        formTop = fy + 4 - offset;
        y = formTop;
        x = left;
        w = full;
        strip();
        int depth = kit.depth();
        try { content.run(); }
        catch (RuntimeException exception) {
            kit.unwind(depth);
            x = left;
            w = full;
            if (reported.add(shown + exception.toString())) com.craisinlord.antos.AntOS.LOGGER.warn("AntStudio could not show the form for {}", shown, exception);
            y = kit.wrap(StudioText.t("form.broken"), x, y + 6, w, StudioTheme.error) + 4;
            kit.button(x, y, StudioText.t("edit_json"), StudioKit.NORMAL, screen::editJson);
            y += 20;
        }
        heights.put(totalId, y - formTop + 6);
        kit.unclip();
        claimed = claiming;
    }

    void jump(String section) {
        jump = section;
        hovered = section;
    }

    int viewTop() { return formY; }
    int viewBottom() { return formY + formH; }

    private void strip() {
        for (StudioProblems.Problem problem : problems) {
            String field = problem.issue().field();
            if (problem.issue().code().equals("changed_elsewhere")) continue;
            if (!field.isEmpty() && claimed.stream().anyMatch(prefix -> matches(field, prefix))) continue;
            problemLine(problem);
            y += 2;
        }
    }

    private static boolean matches(String field, String prefix) { return field.equals(prefix) || field.startsWith(prefix + "/"); }

    void problemLine(StudioProblems.Problem problem) {
        int width = w;
        int right = x + width;
        int color = problem.issue().warning() ? StudioTheme.warning : StudioTheme.error;
        boolean why = StudioHelp.topic(problem.issue().code()) != null;
        if (why) {
            int size = kit.buttonWidth(StudioText.t("why"));
            right -= size;
            String topic = StudioHelp.topic(problem.issue().code());
            kit.button(right, y, size, 14, StudioText.t("why"), StudioKit.GHOST, true, "", () -> screen.help(topic));
            right -= 2;
        }
        if (problem.fix() != null) {
            int size = Math.min(width / 2, kit.buttonWidth(problem.fixLabel()));
            right -= size;
            kit.button(right, y, size, 14, problem.fixLabel(), StudioKit.NORMAL, !store.saving(), "", problem.fix());
            right -= 4;
        }
        kit.dot(x, y + 5, true, problem.issue().warning());
        int end = kit.wrap(problem.text(), x + 9, y + 3, Math.max(40, right - x - 10), color);
        y = Math.max(y + 16, end + 2);
    }

    boolean titleBad(List<StudioProblems.Problem> found, String name) {
        for (StudioProblems.Problem problem : found) if (!problem.issue().warning() && problem.issue().field().equals(name)) return true;
        return false;
    }

    boolean bad(String field) {
        for (StudioProblems.Problem problem : problems) if (!problem.issue().warning() && problem.issue().field().equals(field)) return true;
        return false;
    }

    void section(String id, String title, String note, List<String> fields, Runnable content) {
        claiming.addAll(fields);
        tops.put(key + id, y - formTop);
        String heightId = key + ":" + advanced + ":" + full + ":" + id;
        Integer known = heights.get(heightId);
        int height = known == null ? 34 : known;
        if (known != null && !kit.visible(y, height)) {
            y += height + 4;
            return;
        }
        int top = y;
        boolean error = false;
        for (StudioProblems.Problem problem : problems)
            if (!problem.issue().warning() && fields.stream().anyMatch(prefix -> matches(problem.issue().field(), prefix))) error = true;
        boolean over = kit.over(left, top, full, height);
        if (over) hovered = id;
        kit.box(left, top, full, height, StudioTheme.section, error ? StudioTheme.error : hovered.equals(id) ? StudioTheme.strongLine : StudioTheme.line);
        x = left + 6;
        w = full - 12;
        y = top + 4;
        if (!title.isEmpty()) {
            int noteWidth = note.isEmpty() ? 0 : Math.min(w / 2, kit.width(note));
            kit.text(title, x, y, w - noteWidth - 6, StudioTheme.muted);
            if (!note.isEmpty()) kit.text(note, x + w - noteWidth, y, noteWidth, StudioTheme.soft);
            y += 13;
        }
        content.run();
        for (StudioProblems.Problem problem : problems)
            if (fields.stream().anyMatch(prefix -> matches(problem.issue().field(), prefix))) problemLine(problem);
        y += 4;
        heights.put(heightId, y - top);
        y += 4;
        x = left;
        w = full;
    }

    void row(String label, String hint, Content content) {
        int labelWidth = Math.max(50, Math.min(92, w * 2 / 5));
        kit.text(label, x, y + 5, labelWidth - 4, StudioTheme.muted);
        if (!hint.isEmpty() && kit.hover(x, y, labelWidth, 16)) kit.tooltip(hint);
        content.draw(x + labelWidth, w - labelWidth);
        y += 19;
    }

    private String label(String name) { return StudioText.field(name); }
    private String hint(String name) { return StudioText.has("hint." + name) ? StudioText.t("hint." + name) : ""; }

    void text(StudioNode node, String name, String placeholder, int limit) {
        row(label(name), hint(name), (cx, cw) -> textField(node, name, cx, y, cw, placeholder, limit));
    }

    void textField(StudioNode node, String name, int cx, int cy, int cw, String placeholder, int limit) {
        String raw = node.string(name);
        String shown = StudioText.read(raw);
        boolean translated = StudioText.translated(raw);
        kit.field(node.id(name), cx, cy, cw, shown, new StudioKit.Field().hint(placeholder).max(limit).bad(bad(node.field(name))),
                value -> node.put(name, StudioText.write(node.key.kind(), name, raw, value)));
        if (translated && kit.hover(cx, cy, cw, 16)) kit.tooltip(StudioText.t("from_language", raw));
    }

    void optionalText(StudioNode node, String name, String placeholder, int limit) {
        row(label(name), hint(name), (cx, cw) -> kit.field(node.id(name), cx, y, cw, node.string(name),
                new StudioKit.Field().hint(placeholder).max(limit).bad(bad(node.field(name))), value -> node.putOrDrop(name, value)));
    }

    void area(StudioNode node, String name, String placeholder, int limit, int minimum) {
        String raw = node.string(name);
        int height = kit.areaHeight(node.id(name), minimum, 120);
        kit.area(node.id(name), x, y, w, height, StudioText.read(raw), limit, placeholder, bad(node.field(name)),
                value -> node.put(name, StudioText.write(node.key.kind(), name, raw, value)));
        y += height + 3;
    }

    void lines(StudioNode node, String name, String placeholder) {
        String value = String.join("\n", node.strings(name));
        int height = kit.areaHeight(node.id(name), 34, 120);
        kit.area(node.id(name), x, y, w, height, value, 16384, placeholder, bad(node.field(name)), text -> {
            JsonArray array = new JsonArray();
            if (!text.isEmpty()) for (String line : text.split("\n", -1)) array.add(line);
            node.put(name, array);
        });
        y += height + 3;
    }

    void number(StudioNode node, String name, double fallback, double minimum, double maximum, boolean whole) {
        row(label(name), hint(name), (cx, cw) -> numberField(node, name, cx, y, Math.min(cw, 60), fallback, minimum, maximum, whole));
    }

    void numberField(StudioNode node, String name, int cx, int cy, int cw, double fallback, double minimum, double maximum, boolean whole) {
        kit.number(node.id(name) + "!number", cx, cy, cw, node.number(name, fallback), minimum, maximum, whole,
                value -> node.put(name, whole ? (Number) (long) value : (Number) value));
    }

    void pair(Runnable first, Runnable second) {
        int startX = x;
        int startY = y;
        int width = w;
        if (w < 240) {
            first.run();
            second.run();
            return;
        }
        w = (width - 8) / 2;
        first.run();
        int bottom = y;
        y = startY;
        x = startX + (width - 8) / 2 + 8;
        second.run();
        y = Math.max(bottom, y);
        x = startX;
        w = width;
    }

    final class Checks {
        private int cx = x;

        Checks add(StudioNode node, String name, boolean fallback) {
            boolean value = node.bool(name, fallback);
            return add(label(name), hint(name), value, next -> {
                if (next == fallback && !node.has(name)) return;
                node.put(name, next);
            });
        }

        Checks add(String label, String tip, boolean value, Consumer<Boolean> action) {
            int width = kit.width(label) + 14;
            if (cx > x && cx + width > x + w) {
                cx = x;
                y += 14;
            }
            kit.check(cx, y, label, value, action);
            if (!tip.isEmpty() && kit.hover(cx, y, width, 13)) kit.tooltip(tip);
            cx += width + 10;
            return this;
        }

        void end() { y += 16; }
    }

    Checks checks() { return new Checks(); }

    String choiceLabel(String name, String value) {
        if (value.isEmpty()) return StudioText.t("choice.default");
        if (StudioText.has("choice." + name + "." + value)) return StudioText.t("choice." + name + "." + value);
        return StudioText.has("choice." + value) ? StudioText.t("choice." + value) : StudioText.prettyId(value);
    }

    void choice(StudioNode node, String name, String fallback, List<String> values) {
        row(label(name), hint(name), (cx, cw) -> choiceField(node, name, fallback, values, cx, y, cw));
    }

    void choiceField(StudioNode node, String name, String fallback, List<String> values, int cx, int cy, int cw) {
        String current = node.string(name, fallback);
        int total = 0;
        for (String value : values) total += kit.width(choiceLabel(name, value)) + 10;
        if (total + 2 <= cw) {
            List<String> labels = values.stream().map(value -> choiceLabel(name, value)).toList();
            kit.segments(cx, cy, labels, values.indexOf(current), index -> node.put(name, values.get(index)));
            return;
        }
        kit.drop(cx, cy, cw, choiceLabel(name, current), null, click -> {
            StudioMenu menu = new StudioMenu(screen, cx, cy, 16);
            for (String value : values) menu.check(choiceLabel(name, value), value.equals(current), () -> node.put(name, value));
            screen.popup(menu);
        });
    }

    String refLabel(String type, String value) {
        if (value.isEmpty()) return StudioText.t("picker.none");
        if (type.startsWith("kind:")) {
            StudioKey target = new StudioKey(AuthoringKind.parse(type.substring(5)), value);
            return store.exists(target) ? store.label(target) : value.equals("antos:general") ? StudioText.prettyId(value) : value;
        }
        if (type.equals("effect")) return StudioText.effectName(value);
        if (type.equals("game")) return StudioText.gameName(value);
        if (type.equals("sender")) return StudioProblems.senderName(store, value);
        return StudioText.prettyId(value);
    }

    StudioIcon refIcon(String type, String value) {
        if (!type.startsWith("kind:") || value.isEmpty()) return null;
        StudioKey target = new StudioKey(AuthoringKind.parse(type.substring(5)), value);
        return store.exists(target) ? StudioIcons.of(store, target) : null;
    }

    void ref(StudioNode node, String name, String type, boolean optional) {
        row(label(name), hint(name), (cx, cw) -> refField(node, name, type, optional, cx, y, cw));
    }

    void refField(StudioNode node, String name, String type, boolean optional, int cx, int cy, int cw) {
        String current = node.string(name);
        String shown = current.isEmpty() ? StudioText.t(optional ? "picker.none" : "picker.choose") : refLabel(type, current);
        boolean wrong = bad(node.field(name));
        kit.drop(cx, cy, cw, shown, refIcon(type, current), click -> {
            if (click.button() == 1) {
                refMenu(type, current, optional ? () -> node.drop(name) : null, click);
                return;
            }
            pickRef(type, cx, cy, optional, value -> {
                if (value.isEmpty()) node.drop(name);
                else node.put(name, value);
            });
        });
        if (wrong) kit.outline(cx, cy, cw, 16, StudioTheme.error);
        if (!current.isEmpty() && kit.hover(cx, cy, cw, 16)) kit.tooltip(current);
    }

    private void refMenu(String type, String value, Runnable clear, StudioKit.Click click) {
        StudioMenu menu = new StudioMenu(screen, click.x(), click.y(), 0);
        if (type.startsWith("kind:") && !value.isEmpty()) {
            StudioKey target = new StudioKey(AuthoringKind.parse(type.substring(5)), value);
            if (store.exists(target)) menu.item(StudioText.t("open"), () -> screen.open(target));
        }
        if (clear != null && !value.isEmpty()) menu.danger(StudioText.t("remove"), true, clear);
        if (!menu.empty()) screen.popup(menu);
    }

    void pickRef(String type, int cx, int cy, boolean clearable, Consumer<String> action) {
        StudioPicker picker = new StudioPicker(screen, cx, cy, 16, picked -> action.accept(picked.id()));
        if (type.equals("item")) picker.items(false, false);
        else if (type.equals("entity")) picker.entities();
        else picker.choices(type, StudioText.t("picker.search")).custom();
        if (clearable) picker.clearable();
        if (type.startsWith("kind:")) {
            AuthoringKind kind = AuthoringKind.parse(type.substring(5));
            picker.create(StudioText.t("new_kind", StudioText.kind(kind)), typed -> {
                StudioKey made = screen.make(kind, typed.isBlank() ? StudioText.t("untitled", StudioText.kind(kind)) : typed, false);
                action.accept(made.id());
            });
        }
        screen.popup(picker);
    }

    void refList(StudioNode node, String name, String type) {
        row(label(name), hint(name), (cx, cw) -> {
            List<String> values = node.strings(name);
            int at = cx;
            for (int index = 0; index < values.size(); index++) {
                String value = values.get(index);
                int position = index;
                String shown = refLabel(type, value);
                int size = Math.min(cw, kit.chipWidth(shown, refIcon(type, value) != null, false));
                if (at > cx && at + size > cx + cw) {
                    at = cx;
                    y += 17;
                }
                kit.chip(at, y, cw, shown, refIcon(type, value), false, value, click -> refMenu(type, value, () -> node.at(name).removeAt(position), click));
                at += size + 3;
            }
            String add = "+ " + StudioText.t("add");
            int size = kit.buttonWidth(add);
            if (at > cx && at + size > cx + cw) {
                at = cx;
                y += 17;
            }
            int addX = at;
            int addY = y;
            kit.button(addX, addY, size, 15, add, StudioKit.ADD, true, "", () -> pickRef(type, addX, addY, false, value -> {
                if (!node.strings(name).contains(value)) node.at(name).add(new JsonPrimitive(value));
            }));
        });
    }

    void strings(StudioNode node, String name, String placeholder) {
        row(label(name), hint(name), (cx, cw) -> {
            JsonArray values = node.at(name).array();
            for (int index = 0; index < values.size(); index++) {
                int position = index;
                StudioNode list = node.at(name);
                String value = values.get(index).isJsonPrimitive() ? values.get(index).getAsString() : "";
                kit.field(list.id(Integer.toString(index)), cx, y, cw - 18, value, new StudioKit.Field().hint(placeholder).max(512), text -> list.set(position, new JsonPrimitive(text)));
                kit.button(cx + cw - 16, y, 16, 16, "X", StudioKit.GHOST, true, StudioText.t("remove"), () -> list.removeAt(position));
                y += 18;
            }
            kit.button(cx, y, kit.buttonWidth("+ " + StudioText.t("add")), 15, "+ " + StudioText.t("add"), StudioKit.ADD, true, "", () -> node.at(name).add(new JsonPrimitive("")));
        });
    }

    void item(StudioNode node, String itemName, String entityName, String label) {
        row(label, hint(itemName), (cx, cw) -> {
            String item = node.string(itemName);
            String entity = entityName.isEmpty() ? "" : node.string(entityName);
            StudioIcon icon = !item.isEmpty() ? StudioIcon.item(item) : !entity.isEmpty() ? StudioIcon.entity(entity) : null;
            kit.slot(cx, y - 1, 18, icon, false, item.isEmpty() ? entity : item, click -> {
                if (click.button() == 1) {
                    clear(node, itemName, entityName);
                    return;
                }
                pickAsset(node, itemName, entityName, cx, y);
            });
            kit.text(!item.isEmpty() ? StudioText.itemName(item) : !entity.isEmpty() ? StudioText.entityName(entity) : StudioText.t("picker.none"),
                    cx + 23, y + 4, cw - 44, item.isEmpty() && entity.isEmpty() ? StudioTheme.soft : StudioTheme.text);
            if (!item.isEmpty() || !entity.isEmpty()) kit.button(cx + cw - 16, y, 16, 16, "X", StudioKit.GHOST, true, StudioText.t("picker.none"), () -> clear(node, itemName, entityName));
        });
    }

    private static void clear(StudioNode node, String itemName, String entityName) {
        node.change("", target -> {
            target.getAsJsonObject().remove(itemName);
            if (!entityName.isEmpty()) target.getAsJsonObject().remove(entityName);
        });
    }

    void pickAsset(StudioNode node, String itemName, String entityName, int cx, int cy) {
        screen.popup(new StudioPicker(screen, cx, cy, 18, picked -> node.change("", target -> {
            JsonObject object = target.getAsJsonObject();
            if (picked.type() == StudioPicker.Type.ENTITY) {
                object.addProperty(entityName, picked.id());
                object.remove(itemName);
            } else {
                object.addProperty(itemName, picked.id());
                if (!entityName.isEmpty()) object.remove(entityName);
            }
        })).items(false, !entityName.isEmpty()));
    }

    void add(String label, Consumer<int[]> action) {
        String shown = "+ " + StudioText.t("add_thing", label);
        int size = kit.buttonWidth(shown);
        int bx = x;
        int by = y;
        kit.button(bx, by, size, 14, shown, StudioKit.ADD, !store.saving(), "", () -> action.accept(new int[]{bx, by}));
        y += 15;
    }

    boolean grip(StudioNode list, int index, int rowY, int rowH, String label) {
        boolean hover = kit.hover(x, rowY, w, rowH);
        int color = kit.hover(x - 5, rowY, 6, 16) ? StudioTheme.accent : hover ? StudioTheme.soft : StudioTheme.line;
        for (int dot = 0; dot < 3; dot++) {
            kit.fill(x - 4, rowY + 4 + dot * 3, 1, 1, color);
            kit.fill(x - 2, rowY + 4 + dot * 3, 1, 1, color);
        }
        kit.grab(x - 6, rowY, 7, 16, new StudioKit.Payload(new RowDrag(key, list.path(), index), label), click -> { });
        kit.dropZone(x - 5, rowY - 1, w + 6, rowH, value -> value instanceof RowDrag drag && drag.key().equals(key) && drag.path().equals(list.path()) && drag.index() != index,
                value -> list.move(((RowDrag) value).index(), index));
        return hover;
    }

    void rowMenu(StudioNode list, int index, int size, StudioKit.Click click) {
        StudioMenu menu = new StudioMenu(screen, click.x(), click.y(), 0)
                .item(StudioText.t("move_up"), null, "", index > 0, false, () -> list.move(index, index - 1))
                .item(StudioText.t("move_down"), null, "", index + 1 < size, false, () -> list.move(index, index + 1))
                .item(StudioText.t("duplicate"), () -> {
                    JsonElement copy = list.array().get(index).deepCopy();
                    if (copy.isJsonObject() && list.path().equals("objectives")) copy.getAsJsonObject().addProperty("id", StudioProblems.goalId());
                    list.add(copy);
                })
                .separator().danger(StudioText.t("remove"), true, () -> list.removeAt(index));
        screen.popup(menu);
    }

    void removeButton(int bx, int by, Runnable action) { kit.button(bx, by, 14, 16, "X", StudioKit.GHOST, !store.saving(), StudioText.t("remove"), action); }

    void items(StudioNode node, String name, boolean stacks, boolean tags) {
        StudioNode list = node.at(name);
        JsonArray values = list.array();
        for (int index = 0; index < values.size(); index++) {
            int position = index;
            JsonElement value = values.get(index);
            if (stacks && value.isJsonObject()) {
                StudioNode row = list.at(index);
                boolean map = row.has("explorer_map");
                String id = row.string("id");
                grip(list, index, y, 19, StudioText.itemName(id));
                ItemStack decoded = map ? ItemStack.EMPTY : StudioItems.decode(value.getAsJsonObject());
                StudioIcon icon = map ? StudioIcon.item("minecraft:filled_map") : decoded.isEmpty() ? StudioIcon.item(id) : StudioIcon.stack(decoded);
                int slotX = x + 2;
                int slotY = y;
                kit.slot(slotX, slotY, 18, icon, bad(list.field(Integer.toString(index))), id, click -> {
                    if (map) return;
                    screen.popup(new StudioPicker(screen, slotX, slotY, 18, picked -> list.set(position, StudioItems.encode(picked, row.integer("Count", 1)))).items(false, false));
                });
                String shown = map ? StudioText.t("explorer_map", refLabel("structure", row.string("explorer_map"))) : decoded.isEmpty() ? StudioText.itemName(id) : decoded.getHoverName().getString();
                kit.text(shown + (row.has("tag") ? " *" : ""), x + 25, y + 5, w - 85, StudioTheme.strong);
                if (row.has("tag") && kit.hover(x + 25, y, w - 85, 16)) kit.tooltip(StudioText.t("keeps_data"));
                if (!map) {
                    kit.text("x", x + w - 57, y + 5, 8, StudioTheme.soft);
                    numberField(row, "Count", x + w - 48, y, 32, 1, 1, 108, true);
                }
                removeButton(x + w - 14, y, () -> list.removeAt(position));
                y += 20;
                if (map && advanced) {
                    int keptX = x;
                    int keptW = w;
                    x += 20;
                    w -= 20;
                    ref(row, "explorer_map", "structure", false);
                    optionalText(row, "name", "", 128);
                    x = keptX;
                    w = keptW;
                }
                continue;
            }
            String id = value.isJsonPrimitive() ? value.getAsString() : "";
            boolean tag = id.startsWith("#");
            grip(list, index, y, 19, id);
            int slotX = x + 2;
            int slotY = y;
            kit.slot(slotX, slotY, 18, tag ? StudioIcon.item("minecraft:bundle") : StudioIcon.item(id), bad(list.field(Integer.toString(index))), id, click ->
                    screen.popup(new StudioPicker(screen, slotX, slotY, 18, picked -> list.set(position, new JsonPrimitive((picked.type() == StudioPicker.Type.TAG ? "#" : "") + picked.id()))).items(tags, false)));
            kit.text(tag ? StudioText.tag(id) : StudioText.itemName(id), x + 25, y + 5, w - 45, StudioTheme.strong);
            removeButton(x + w - 14, y, () -> list.removeAt(position));
            y += 20;
        }
        add(StudioText.t("item"), at -> screen.popup(new StudioPicker(screen, at[0], at[1], 14, picked ->
                list.add(stacks ? StudioItems.encode(picked, 1) : new JsonPrimitive((picked.type() == StudioPicker.Type.TAG ? "#" : "") + picked.id()))).items(tags, false)));
    }

    void chips(IntConsumer draw) { draw.accept(x); }

    final class Flow {
        private int cx = x;

        void chip(String label, StudioIcon icon, boolean on, String tip, StudioKit.Action action) {
            int size = Math.min(w, kit.chipWidth(label, icon != null, on));
            if (cx > x && cx + size > x + w) {
                cx = x;
                y += 18;
            }
            kit.chip(cx, y, w, label, icon, on, tip, action);
            cx += size + 4;
        }

        int[] place() { return new int[]{cx, y}; }
        void end() { y += 18; }
    }

    Flow flow() { return new Flow(); }

    void leftovers(Set<String> shown) {
        List<String> extra = new ArrayList<>();
        for (String name : root.object().keySet()) if (!shown.contains(name)) extra.add(name);
        if (extra.isEmpty()) return;
        section("other", StudioText.t("other_settings", extra.size()), "", List.of(), () -> {
            y = kit.wrap(String.join(", ", extra.stream().map(StudioText::field).toList()), x, y + 1, w, StudioTheme.soft) + 2;
            kit.button(x, y, kit.buttonWidth(StudioText.t("edit_json")), 15, StudioText.t("edit_json"), StudioKit.NORMAL, true, "", screen::editJson);
            y += 18;
        });
    }
}
