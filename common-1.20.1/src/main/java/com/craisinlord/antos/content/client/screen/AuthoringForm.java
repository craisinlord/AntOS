package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.editor.AuthoringKind;
import com.craisinlord.antos.content.editor.AuthoringTemplates;
import com.craisinlord.antos.content.editor.AuthoringValidation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class AuthoringForm {
    private static final List<net.minecraft.resources.ResourceLocation> ITEM_IDS = java.util.stream.StreamSupport
            .stream(net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().spliterator(), false)
            .filter(id -> !id.getPath().equals("air")).sorted(java.util.Comparator.comparing(Object::toString)).toList();
    private final ContentAuthoring owner;
    private final Map<String, AbstractWidget> widgets = new HashMap<>();
    private final Set<String> collapsed = new HashSet<>();
    private final Set<String> optionalFields = new HashSet<>();
    private final Set<String> customFields = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<AbstractWidget> visible = new ArrayList<>();
    private final List<Placement> placements = new ArrayList<>();
    private AbstractWidget focused;
    private int scroll;
    private int maximum;
    private boolean rebuild = true;
    private boolean layoutDirty = true;
    private int layoutWidth = -1;
    private int layoutTotal;
    private JsonObject body;
    private AuthoringKind kind;
    private Row picking;
    private EditBox pickerSearch;
    private final List<net.minecraft.resources.ResourceLocation> pickerItems = new ArrayList<>();
    private final Map<String, net.minecraft.world.item.ItemStack> pickerStacks = new HashMap<>();
    private int pickerPage;
    private boolean itemPicker = true;
    private List<String> choiceValues = List.of();
    private List<String> filteredChoices = List.of();
    private int pickerX;
    private int pickerY;
    private int pickerWidth;
    private int pickerHeight;
    private int clipX;
    private int clipY;
    private int clipWidth;
    private int clipHeight;
    private Set<String> filter = Set.of();
    private record Row(String path, String key, JsonElement value, boolean present, int depth,
                       Consumer<JsonElement> set, Runnable remove, String context) { }
    private record Placement(Row row, int top, int left, int width) { }

    AuthoringForm(ContentAuthoring owner) { this.owner = owner; }

    void document(AuthoringKind kind, JsonObject body, Set<String> filter) {
        if (this.body == body && this.kind == kind && this.filter.equals(filter)) return;
        this.kind = kind;
        this.body = body;
        this.filter = filter;
        widgets.clear();
        collapsed.clear();
        optionalFields.clear();
        customFields.clear();
        focused = null;
        scroll = 0;
        rebuild = true;
    }

    private void build() {
        rows.clear();
        addObject(body, AuthoringTemplates.fields(kind), "", 0, "", true);
        rebuild = false;
        layoutDirty = true;
    }

    private void addObject(JsonObject object, JsonObject schema, String path, int depth, String context, boolean root) {
        if (rows.size() >= 8192) return;
        LinkedHashSet<String> keys = new LinkedHashSet<>(object.keySet());
        keys.addAll(schema.keySet());
        if (root && kind == AuthoringKind.MESSAGE && filter.equals(Set.of("subject", "body"))) {
            keys.clear(); keys.add("subject"); keys.add("body");
        }
        boolean missing = false;
        for (String key : keys) {
            if (rows.size() >= 8192) break;
            if (root && !filter.isEmpty() && !filter.contains(key)) continue;
            JsonElement actual = object.get(key);
            if (actual == null && !optionalFields.contains(path)) { missing = true; continue; }
            JsonElement fallback = schema.has(key) ? schema.get(key) : JsonNull.INSTANCE;
            JsonElement value = actual == null ? fallback : actual;
            String child = path + "/" + key;
            rows.add(new Row(child, key, value, actual != null, depth, replacement -> {
                object.add(key, replacement);
                if (key.equals("type")) AuthoringTemplates.selectType(object, context);
            }, () -> object.remove(key), context));
            if (actual != null && !collapsed.contains(child) && depth < 12) addChildren(actual, child, key, depth + 1);
        }
        if (missing || optionalFields.contains(path)) rows.add(new Row(path + "/@optional", "MORE FIELDS", new JsonObject(), true, depth,
                value -> { }, () -> { }, "@optional:" + path));
    }

    private void addChildren(JsonElement element, String path, String context, int depth) {
        if (element.isJsonObject()) {
            addObject(element.getAsJsonObject(), AuthoringTemplates.objectFields(context), path, depth, context, false);
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                if (rows.size() >= 8192) break;
                int position = index;
                JsonElement value = array.get(index);
                String child = path + "/" + index;
                rows.add(new Row(child, "#" + (index + 1), value, true, depth,
                        replacement -> array.set(position, replacement), () -> array.remove(position), context));
                if (!collapsed.contains(child) && depth < 12) addChildren(value, child, context, depth + 1);
            }
        }
    }

    void render(GuiGraphics graphics, int x, int y, int width, int height) {
        if (body == null || width < 40 || height < 20) return;
        if (rebuild) build();
        if (layoutDirty || layoutWidth != width) {
            placements.clear();
            int total = 0;
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                boolean pair = width >= 240 && index + 1 < rows.size() && pairable(row) && pairable(rows.get(index + 1))
                        && row.depth() == rows.get(index + 1).depth();
                int fieldWidth = pair ? (width - 16) / 2 : width - 8;
                placements.add(new Placement(row, total, 0, fieldWidth));
                if (pair) {
                    Row second = rows.get(++index);
                    placements.add(new Placement(second, total, fieldWidth + 8, fieldWidth));
                    total += Math.max(height(row), height(second));
                } else total += height(row);
            }
            layoutTotal = total;
            layoutWidth = width;
            layoutDirty = false;
        }
        int total = layoutTotal;
        maximum = Math.max(0, total - height + 6);
        scroll = Math.max(0, Math.min(maximum, scroll));
        visible.clear();
        clipX = x; clipY = y; clipWidth = width; clipHeight = height;
        graphics.flush();
        owner.screen.enableComputerScissor(graphics, x, y, x + width, y + height);
        owner.kit.clip(x, y, width, height);
        try {
            for (Placement placement : placements) {
                int top = y - scroll + placement.top();
                if (top + height(placement.row()) >= y && top < y + height)
                    renderRow(graphics, placement.row(), x + placement.left(), top, placement.width());
            }
        } finally {
            owner.kit.unclip();
            graphics.flush();
            graphics.disableScissor();
        }
        owner.kit.scrollbar(graphics, x + width - 3, y, height, height, total, scroll, maximum, value -> scroll = value);
    }

    private int height(Row row) {
        if (row.value().isJsonObject() || row.value().isJsonArray()) return 20;
        return multiline(row) ? 65 : 29;
    }

    private boolean pairable(Row row) { return row.value().isJsonPrimitive() && !multiline(row); }

    private boolean multiline(Row row) {
        return row.key().equals("body") || row.key().equals("description") || row.context().equals("lines");
    }

    private void renderRow(GuiGraphics graphics, Row row, int left, int top, int width) {
        int indent = Math.min(row.depth() * 6, Math.max(0, width / 4));
        int x = left + indent;
        int w = width - indent;
        String label = row.key().replace('_', ' ').toUpperCase(java.util.Locale.ROOT);
        int color = row.present() ? Ui.TEXT_STRONG : Ui.TEXT_MUTED;
        boolean group = row.value().isJsonObject() || row.value().isJsonArray();
        if (group) {
            if (row.context().startsWith("@optional:")) {
                String path = row.context().substring("@optional:".length());
                owner.kit.button(graphics, x, top, w, 16, optionalFields.contains(path) ? "LESS FIELDS" : "+ MORE FIELDS", false, true, () -> {
                    if (!optionalFields.add(path)) optionalFields.remove(path);
                    rebuild = true;
                });
                return;
            }
            String count = row.value().isJsonArray() ? " (" + row.value().getAsJsonArray().size() + ")" : "";
            boolean deliveryItems = row.key().equals("items") && row.value().isJsonArray();
            owner.kit.button(graphics, x, top, Math.max(22, w - (deliveryItems ? 63 : 42)), 16,
                    (collapsed.contains(row.path()) || !row.present() ? "+ " : "- ") + label + count,
                    false, true, () -> {
                        if (!row.present()) { owner.structuralChange(); row.set().accept(row.value().deepCopy()); }
                        else if (!collapsed.add(row.path())) collapsed.remove(row.path());
                        rebuild = true;
                        widgets.clear();
                    });
            if (row.value().isJsonArray()) owner.kit.button(graphics, x + w - 39, top, 17, 16, "+", false, true, () -> {
                owner.structuralChange();
                JsonArray array = row.present() ? row.value().getAsJsonArray() : new JsonArray();
                JsonElement entry = AuthoringTemplates.arrayEntry(kind, row.key().startsWith("#") ? row.context() : row.key(), array.size());
                if (entry.isJsonObject() && Set.of("objectives", "options").contains(row.key())) entry.getAsJsonObject().addProperty("id", "entry_" + java.util.UUID.randomUUID().toString().substring(0, 8));
                array.add(entry);
                row.set().accept(array);
                collapsed.remove(row.path());
                widgets.clear();
                rebuild = true;
            });
            if (deliveryItems) owner.kit.button(graphics, x + w - 60, top, 18, 16, "H", false, true, "Add the held item, including its components", () -> addHeldItem(row));
            remove(graphics, row, x + w - 19, top, 16);
            return;
        }
        graphics.drawString(owner.font, owner.screen.trimToWidth(label + (row.present() ? "" : " // DEFAULT"), Math.max(1, w - 22)), x, top + 1, color, false);
        remove(graphics, row, x + w - 19, top, 10);
        JsonElement value = row.value();
        boolean itemField = Set.of("item", "icon", "icon_item", "cover_item", "sender_avatar_item").contains(row.key())
                || row.key().equals("id") && Set.of("items", "gallery").contains(row.context())
                || row.key().equals("target") && row.context().equals("objectives");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            owner.kit.button(graphics, x, top + 12, w, 16, value.getAsBoolean() ? "ON" : "OFF", value.getAsBoolean(), true, () -> {
                owner.structuralChange();
                row.set().accept(new JsonPrimitive(!value.getAsBoolean()));
                rebuild = true;
            });
            return;
        }
        List<String> choices = choices(row);
        if (!choices.isEmpty()) {
            String current = value.isJsonPrimitive() ? value.getAsString() : "";
            owner.kit.button(graphics, x, top + 12, w, 16, current.isEmpty() ? "DEFAULT" : friendly(current), false, true,
                    "Click to choose; right-click to choose the previous value", () -> {
                        if (owner.kit.clickButton() == 1) {
                            String choice = choices.get(Math.floorMod(choices.indexOf(current) - 1, choices.size()));
                            if (choice.equals("@custom")) customFields.add(row.path());
                            else { owner.structuralChange(); row.set().accept(new JsonPrimitive(choice)); }
                            invalidate();
                        } else openChoice(row, choices);
                    });
            return;
        }
        AbstractWidget widget = widgets.computeIfAbsent(row.path(), ignored -> create(row));
        widget.setX(x + 4);
        widget.setY(top + 15);
        widget.setWidth(Math.max(8, w - 8 - (itemField ? 20 : 0)));
        widget.setHeight(Math.max(10, height(row) - 19));
        graphics.fill(x, top + 12, x + w, top + height(row) - 4, Ui.SURFACE);
        owner.kit.outline(graphics, x, top + 12, w, height(row) - 16, widget == focused ? Ui.FOCUS : Ui.LINE);
        widget.render(graphics, owner.screen.session.mouseX, owner.screen.session.mouseY, 0);
        visible.add(widget);
        if (itemField) owner.kit.button(graphics, x + w - 20, top + 12, 20, 17, "...", false, true, "Choose an item", () -> openPicker(row));
    }

    private void remove(GuiGraphics graphics, Row row, int x, int y, int height) {
        if (!row.present()) return;
        owner.kit.button(graphics, x, y, 17, height, "X", false, true, "Remove this field or list entry", () -> {
            owner.structuralChange();
            row.remove().run();
            widgets.clear();
            focused = null;
            rebuild = true;
        });
    }

    private AbstractWidget create(Row row) {
        String initial = row.value().isJsonPrimitive() ? row.value().getAsString() : "";
        JsonElement schema = (row.context().isEmpty() ? AuthoringTemplates.fields(kind) : AuthoringTemplates.objectFields(row.context())).get(row.key());
        boolean numeric = row.value().isJsonPrimitive() && row.value().getAsJsonPrimitive().isNumber()
                || schema != null && schema.isJsonPrimitive() && schema.getAsJsonPrimitive().isNumber() || owner.invalidNumber(row.path());
        Consumer<String> update = text -> {
            try {
                JsonElement value = numeric
                        ? new JsonPrimitive(new java.math.BigDecimal(text)) : new JsonPrimitive(text);
                owner.changed();
                row.set().accept(value);
                owner.fieldValid(row.path());
            } catch (NumberFormatException exception) { owner.changed(); row.set().accept(new JsonPrimitive(text)); owner.fieldInvalid(row.path()); }
        };
        if (multiline(row)) {
            AuthoringMultiline widget = new AuthoringMultiline(owner, row.key());
            widget.setCharacterLimit(row.key().equals("body") ? 4096 : 32768);
            widget.setValue(initial);
            widget.setValueListener(update);
            return widget;
        }
        EditBox widget = new EditBox(owner.font, 0, 0, 100, 14, Component.literal(row.key()));
        widget.setBordered(false);
        widget.setTextColor(Ui.TEXT);
        widget.setMaxLength(AuthoringValidation.MAX_DOCUMENT_CHARS);
        widget.setValue(initial);
        widget.setResponder(update);
        return widget;
    }

    private List<String> choices(Row row) {
        if (customFields.contains(row.path())) return List.of();
        List<String> references = referenceChoices(row);
        if (!references.isEmpty()) return references;
        List<String> result = switch (row.key()) {
            case "on" -> List.of("read", "deliver");
            case "availability" -> List.of("automatic", "disk");
            case "shape" -> List.of("square", "diamond", "hexagon", "circle", "pentagon", "octagon", "triangle");
            case "line_axis" -> List.of("auto", "vertical", "horizontal");
            case "tag_mode" -> List.of("any", "all");
            case "mode" -> switch (row.context()) {
                case "item_pool" -> List.of("all", "rotate");
                case "variants" -> List.of("all", "random", "choose");
                case "requires" -> List.of("all", "any");
                default -> List.of("random", "choice");
            };
            case "type" -> switch (row.context()) {
                case "" -> kind == AuthoringKind.PROGRAM ? List.of("quiz") : List.of();
                case "trigger" -> List.of("task_complete", "advancement", "mail_read", "random");
                case "objectives" -> List.of("item", "item_tag", "advancement", "stat", "disk_installed", "program_ending", "terminal_command", "mail_read", "archive_viewed", "dimension_entered", "biome_visited", "structure_entered");
                case "rewards" -> List.of("item", "experience", "experience_levels", "antcoins", "item_pool", "choice", "archive", "advancement", "effect", "mail", "wallpaper");
                case "payments" -> List.of("antcoins", "item");
                default -> List.of();
            };
            case "stat_type" -> List.of("custom", "block_mined", "item_crafted", "item_used", "item_broken", "item_picked_up", "item_dropped", "entity_killed", "entity_killed_by");
            case "delivery" -> List.of("falling_chest", "direct", "falling_chest_location");
            case "unlock_mode" -> List.of("all", "any");
            case "reward_on" -> List.of("perfect", "complete");
            case "reward_limit" -> List.of("daily", "once");
            case "style" -> kind == AuthoringKind.TERMINAL_COMMAND ? List.of("output", "highlight", "system", "error") : List.of();
            case "limit_reset", "player_limit_reset" -> List.of("none", "minecraft_day", "minecraft_week");
            case "order" -> List.of("shuffled", "sequential");
            default -> List.of();
        };
        if (kind == AuthoringKind.PROGRAM && row.key().equals("type") && row.value().isJsonPrimitive()) {
            String current = row.value().getAsString();
            if (current.equals("adventure") && !result.contains(current)) result = List.of("quiz", current);
        }
        if (row.key().equals("type") && (row.context().equals("trigger") || row.context().equals("objectives"))) {
            String current = row.value().isJsonPrimitive() ? row.value().getAsString() : "";
            if (!result.contains(current)) return List.of();
            List<String> extended = new ArrayList<>(result); extended.add("@custom"); return List.copyOf(extended);
        }
        return result;
    }

    private List<String> referenceChoices(Row row) {
        List<String> values = new ArrayList<>();
        if (row.key().startsWith("#") && row.value().isJsonPrimitive() && row.value().getAsJsonPrimitive().isString()) switch (row.context()) {
            case "entries" -> com.craisinlord.antos.content.guide.ComputerGuideData.archiveEntryIds().forEach(id -> values.add(id.toString()));
            case "tasks" -> com.craisinlord.antos.content.guide.ComputerGuideData.tasks().forEach(task -> values.add(task.id().toString()));
            case "disks" -> com.craisinlord.antos.content.guide.ComputerGuideData.disks().forEach(disk -> values.add(disk.id().toString()));
            case "programs" -> com.craisinlord.antos.content.computer.ComputerDiskData.programs().keySet().stream().sorted().forEach(id -> values.add(id.toString()));
            case "commands" -> com.craisinlord.antos.content.computer.terminal.TerminalCommandData.definitions().keySet().stream().sorted().forEach(id -> values.add(id.toString()));
            case "subscribe" -> {
                com.craisinlord.antos.content.antmail.AntmailEventData.definitionIds().forEach(id -> values.add(id.toString()));
                com.craisinlord.antos.content.antmail.AntmailEventData.poolIds().forEach(id -> values.add(id.toString()));
            }
            default -> { }
        }
        if (row.key().equals("source") && row.context().equals("files"))
            com.craisinlord.antos.content.computer.ComputerDiskData.files().keySet().stream().sorted().forEach(id -> values.add(id.toString()));
        boolean stringValue = row.value().isJsonPrimitive() && row.value().getAsJsonPrimitive().isString();
        if (stringValue && kind == AuthoringKind.DISK && row.key().equals("category"))
            com.craisinlord.antos.content.guide.ComputerGuideData.diskCategories().forEach(value -> values.add(value.id().toString()));
        if (stringValue && (kind == AuthoringKind.DISK || kind == AuthoringKind.TERMINAL_COMMAND) && row.key().equals("wallpaper"))
            com.craisinlord.antos.content.guide.ComputerGuideData.wallpapers().forEach(value -> values.add(value.id().toString()));
        if (values.isEmpty()) return List.of();
        if (stringValue && (kind == AuthoringKind.DISK || kind == AuthoringKind.TERMINAL_COMMAND) && row.key().equals("wallpaper")) values.add(0, "");
        String current = row.value().isJsonPrimitive() ? row.value().getAsString() : "";
        if (!current.isEmpty() && !values.contains(current)) values.add(current);
        values.add("@custom");
        return List.copyOf(values);
    }

    private void addHeldItem(Row row) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player == null || client.level == null || client.player.getMainHandItem().isEmpty()) { owner.notice("Hold an item in your main hand first"); return; }
        try {
            JsonElement item = net.minecraft.world.item.ItemStack.CODEC.encodeStart(
                    net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, client.level.registryAccess()), client.player.getMainHandItem().copy())
                    .getOrThrow(false, error -> { throw new IllegalArgumentException(error); });
            owner.structuralChange();
            JsonArray array = row.present() ? row.value().getAsJsonArray() : new JsonArray();
            array.add(item);
            row.set().accept(array);
            collapsed.remove(row.path());
            invalidate();
        } catch (RuntimeException exception) { owner.notice("This item could not be encoded for a delivery"); }
    }

    private void openPicker(Row row) {
        itemPicker = true;
        picking = row;
        pickerPage = 0;
        pickerSearch = new EditBox(owner.font, 0, 0, 100, 16, Component.literal("Search items"));
        pickerSearch.setBordered(false);
        pickerSearch.setTextColor(Ui.TEXT);
        pickerSearch.setMaxLength(128);
        pickerSearch.setHint(Component.literal("Search items..."));
        pickerSearch.setResponder(query -> filterItems(query));
        pickerSearch.setFocused(true);
        filterItems("");
    }

    private static String friendly(String value) { return value.equals("@custom") ? "CUSTOM TYPE..." : value.toUpperCase(java.util.Locale.ROOT).replace('_', ' '); }

    private void openChoice(Row row, List<String> choices) {
        openPicker(row);
        itemPicker = false;
        choiceValues = List.copyOf(choices);
        pickerSearch.setHint(Component.literal("Search choices..."));
        filterItems("");
    }

    private void filterItems(String query) {
        pickerItems.clear();
        String search = query.toLowerCase(java.util.Locale.ROOT);
        if (itemPicker) {
            for (net.minecraft.resources.ResourceLocation id : ITEM_IDS) if (id.toString().contains(search)) pickerItems.add(id);
        } else filteredChoices = choiceValues.stream().filter(value -> value.contains(search) || value.replace('_', ' ').contains(search)).toList();
        pickerPage = 0;
    }

    boolean pickerOpen() { return picking != null; }

    void renderPicker(GuiGraphics graphics, int x, int y, int width, int height) {
        if (picking == null) return;
        owner.kit.begin();
        pickerX = x; pickerY = y; pickerWidth = width; pickerHeight = height;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 210);
        try {
            graphics.fill(x, y, x + width, y + height, Ui.BACKGROUND);
            owner.kit.outline(graphics, x, y, width, height, Ui.ACCENT);
            owner.kit.block(x, y, width, height);
            graphics.drawString(owner.font, itemPicker ? "CHOOSE ITEM" : "CHOOSE " + friendly(picking.key()), x + 8, y + 7, Ui.ACCENT, false);
            owner.kit.button(graphics, x + width - 26, y + 3, 22, 16, "X", false, true, () -> picking = null);
            pickerSearch.setX(x + 11); pickerSearch.setY(y + 29); pickerSearch.setWidth(Math.max(20, width - 22));
            owner.kit.outline(graphics, x + 7, y + 24, width - 14, 20, Ui.LINE);
            pickerSearch.render(graphics, owner.screen.session.mouseX, owner.screen.session.mouseY, 0);
            if (!itemPicker) {
                int count = Math.max(1, (height - 76) / 19);
                int start = pickerPage * count;
                for (int index = start; index < Math.min(filteredChoices.size(), start + count); index++) {
                    String value = filteredChoices.get(index);
                    owner.kit.button(graphics, x + 8, y + 49 + (index - start) * 19, width - 16, 17, friendly(value), false, true, () -> {
                        if (value.equals("@custom")) customFields.add(picking.path());
                        else { owner.structuralChange(); picking.set().accept(new JsonPrimitive(value)); }
                        picking = null; invalidate();
                    });
                }
                int pages = Math.max(1, (filteredChoices.size() + count - 1) / count);
                renderPickerPages(graphics, x, y + height - 22, width, pages);
                return;
            }
            int columns = Math.max(1, (width - 16) / 29);
            int rowCount = Math.max(1, (height - 76) / 29);
            int pageSize = columns * rowCount;
            int start = pickerPage * pageSize;
            for (int index = start; index < Math.min(pickerItems.size(), start + pageSize); index++) {
                net.minecraft.resources.ResourceLocation id = pickerItems.get(index);
                int cell = index - start;
                int cellX = x + 8 + cell % columns * 29;
                int cellY = y + 49 + cell / columns * 29;
                owner.kit.button(graphics, cellX, cellY, 25, 25, "", false, true, id.toString(), () -> {
                    owner.structuralChange(); picking.set().accept(new JsonPrimitive(id.toString())); picking = null; invalidate();
                });
                net.minecraft.world.item.ItemStack stack = pickerStacks.computeIfAbsent(id.toString(), key -> new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)));
                graphics.renderItem(stack, cellX + 4, cellY + 4);
            }
            if (pickerStacks.size() > 256) pickerStacks.clear();
            int pages = Math.max(1, (pickerItems.size() + pageSize - 1) / pageSize);
            renderPickerPages(graphics, x, y + height - 22, width, pages);
        } finally { graphics.pose().popPose(); }
    }

    private void renderPickerPages(GuiGraphics graphics, int x, int bottom, int width, int pages) {
        owner.kit.button(graphics, x + 8, bottom, 22, 16, "<", false, pickerPage > 0, () -> pickerPage--);
        graphics.drawCenteredString(owner.font, Component.literal((pickerPage + 1) + "/" + pages), x + width / 2, bottom + 4, Ui.TEXT);
        owner.kit.button(graphics, x + width - 30, bottom, 22, 16, ">", false, pickerPage + 1 < pages, () -> pickerPage++);
    }

    boolean pickerClick(double x, double y, int button) {
        if (picking == null) return false;
        if (!ComputerScreen.inside(pickerX, pickerY, pickerWidth, pickerHeight, x, y)) { picking = null; return true; }
        if (pickerSearch.isMouseOver(x, y)) { pickerSearch.setFocused(true); pickerSearch.mouseClicked(x, y, button); }
        else owner.kit.click(x, y, button);
        return true;
    }

    boolean click(double x, double y, int button) {
        if (!ComputerScreen.inside(clipX, clipY, clipWidth, clipHeight, x, y)) return false;
        for (AbstractWidget widget : visible) if (widget.isMouseOver(x, y)) {
            if (focused != null && focused != widget) focused.setFocused(false);
            focused = widget;
            focused.setFocused(true);
            return widget.mouseClicked(x, y, button);
        }
        if (focused != null) focused.setFocused(false);
        focused = null;
        return false;
    }

    boolean key(int key, int scan, int modifiers) {
        if (picking != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) picking = null;
            else if (key == GLFW.GLFW_KEY_TAB) owner.kit.focusNext((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            else if (key == GLFW.GLFW_KEY_ENTER) owner.kit.activateFocused();
            else pickerSearch.keyPressed(key, scan, modifiers);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && !visible.isEmpty()) {
            int index = visible.indexOf(focused);
            if (focused != null) focused.setFocused(false);
            focused = visible.get(Math.floorMod(index + ((modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1), visible.size()));
            focused.setFocused(true);
            return true;
        }
        return focused != null && focused.keyPressed(key, scan, modifiers);
    }
    boolean type(char character, int modifiers) { return picking != null ? pickerSearch.charTyped(character, modifiers) : focused != null && focused.charTyped(character, modifiers); }
    void scroll(double amount) { scroll = Math.max(0, Math.min(maximum, scroll - (int) (amount * 25))); }
    boolean drag(double x, double y, double dx, double dy) { return focused != null && focused.mouseDragged(x, y, 0, dx, dy); }
    void scroll(double x, double y, double amount) {
        for (AbstractWidget widget : visible) if (widget instanceof AuthoringMultiline && widget.isMouseOver(x, y)) {
            widget.mouseScrolled(x, y, amount);
            return;
        }
        scroll(amount);
    }
    void invalidate() { widgets.clear(); focused = null; picking = null; rebuild = true; layoutDirty = true; }
}
