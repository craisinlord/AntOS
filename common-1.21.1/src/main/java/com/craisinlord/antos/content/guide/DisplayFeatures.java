package com.craisinlord.antos.content.guide;

import com.google.gson.*;
import java.util.*;
import java.util.function.Function;
import static com.craisinlord.antos.content.guide.CustomDisplays.*;

public final class DisplayFeatures {
    public record Bounds(double x, double y, double width, double height) { }
    public record Diagnostic(String severity, String source, String path, String message) {
        public JsonObject json() {
            JsonObject result = new JsonObject();
            result.addProperty("severity", severity); result.addProperty("source", source); result.addProperty("path", path); result.addProperty("message", message);
            return result;
        }
    }
    public record Material(String key, String item, String label, long count, int blocks, Set<String> contributors, boolean unresolved) { }
    public static final Set<String> ROLES = Set.of("generic", "input", "output", "tool", "catalyst", "optional", "material");
    public static final Set<String> PRESETS = Set.of("crafting", "shapeless", "cooking", "machine", "radial", "structure", "chain");

    private DisplayFeatures() { }

    public static Bounds bounds(JsonObject e) {
        String type = string(e, "type", "");
        double x = number(e, "x", 0), y = number(e, "y", 0);
        double w = number(e, "width", number(e, "size", type.equals("scene") ? 180 : type.equals("materials") || type.equals("text") ? 100 : 18));
        double h = number(e, "height", number(e, "size", type.equals("scene") || type.equals("materials") ? 140 : type.equals("text") ? 11 : 18));
        if (type.equals("circle")) { double radius = number(e, "radius", 20); return new Bounds(x - radius, y - radius, radius * 2 + 1, radius * 2 + 1); }
        if (type.equals("line") || type.equals("arrow")) { double x2 = number(e, "x2", x + w), y2 = number(e, "y2", y); return new Bounds(Math.min(x, x2), Math.min(y, y2) - (type.equals("arrow") ? 7 : 0), Math.abs(x2 - x) + 1, Math.abs(y2 - y) + (type.equals("arrow") ? 15 : 1)); }
        if (type.equals("polygon")) {
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (JsonElement value : e.getAsJsonArray("points")) { JsonArray p = value.getAsJsonArray(); minX = Math.min(minX, p.get(0).getAsDouble()); maxX = Math.max(maxX, p.get(0).getAsDouble()); minY = Math.min(minY, p.get(1).getAsDouble()); maxY = Math.max(maxY, p.get(1).getAsDouble()); }
            return new Bounds(x + minX, y + minY, maxX - minX + 1, maxY - minY + 1);
        }
        if (type.equals("group") && e.has("elements") && e.getAsJsonArray("elements").size() > 0) {
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (JsonElement value : e.getAsJsonArray("elements")) { Bounds b = bounds(value.getAsJsonObject()); minX = Math.min(minX, b.x); minY = Math.min(minY, b.y); maxX = Math.max(maxX, b.x + b.width); maxY = Math.max(maxY, b.y + b.height); }
            return new Bounds(x + minX, y + minY, maxX - minX, maxY - minY);
        }
        return new Bounds(x, y, w, h);
    }

    public static void normalize(JsonObject document) {
        Set<String> ids = new HashSet<>();
        identities(document.getAsJsonArray("elements"), "element", ids, 0);
        validateReferences(document.getAsJsonArray("elements"));
        if (document.has("layouts")) {
            JsonArray layouts = document.getAsJsonArray("layouts");
            check(layouts.size() <= 8, "layouts: maximum 8 presentations");
            Set<String> names = new HashSet<>();
            for (JsonElement value : layouts) {
                JsonObject layout = value.getAsJsonObject();
                check(names.add(string(layout, "id", "")) && !string(layout, "id", "").isBlank(), "layouts.id missing or duplicated");
                check(number(layout, "min_width", 0) >= 0 && number(layout, "min_width", 0) <= 2048, "layouts.min_width out of range");
                if (!layout.has("width")) layout.addProperty("width", number(document, "width", 320));
                if (!layout.has("height")) layout.addProperty("height", number(document, "height", 180));
                identities(layout.getAsJsonArray("elements"), "element", new HashSet<>(), 0);
                validateReferences(layout.getAsJsonArray("elements"));
            }
        }
    }

    private static void validateReferences(JsonArray elements) {
        Map<String, JsonObject> indexed = new HashMap<>();
        collect(elements, indexed);
        for (JsonObject element : indexed.values()) if (string(element, "type", "").equals("materials")) {
            JsonObject scene = indexed.get(string(element, "scene", ""));
            check(scene != null && string(scene, "type", "").equals("scene"), "materials.scene must reference a scene in the same presentation");
            check(Set.of("scene", "visible", "cumulative", "added").contains(string(element, "scope", "scene")), "materials.scope is unsupported");
            check(Set.of("grid", "list").contains(string(element, "layout", "grid")), "materials.layout must be grid or list");
        }
    }

    public static void resolveLayouts(JsonObject document) {
        if (!document.has("layouts")) return;
        Map<String, JsonObject> source = new HashMap<>(); collect(document.getAsJsonArray("elements"), source);
        for (JsonElement value : document.getAsJsonArray("layouts")) {
            JsonObject layout = value.getAsJsonObject();
            JsonArray elements = new JsonArray();
            for (JsonElement child : layout.getAsJsonArray("elements")) {
                JsonObject entry = child.getAsJsonObject();
                if (entry.has("ref")) {
                    check(entry.keySet().stream().allMatch(key -> Set.of("ref", "x", "y", "width", "height").contains(key)), "layout references accept presentation geometry only");
                    JsonObject original = source.get(string(entry, "ref", ""));
                    check(original != null, "layout.ref: missing element " + string(entry, "ref", ""));
                    JsonObject placed = original.deepCopy();
                    entry.entrySet().stream().filter(field -> !field.getKey().equals("ref")).forEach(field -> placed.add(field.getKey(), field.getValue().deepCopy()));
                    elements.add(placed);
                } else elements.add(entry);
            }
            layout.add("elements", elements);
        }
    }

    private static void collect(JsonArray elements, Map<String, JsonObject> source) {
        if (elements == null) return;
        for (JsonElement value : elements) {
            JsonObject element = value.getAsJsonObject(); if (element.has("id")) source.put(element.get("id").getAsString(), element);
            if (element.has("elements")) collect(element.getAsJsonArray("elements"), source);
        }
    }

    private static void identities(JsonArray elements, String prefix, Set<String> ids, int depth) {
        check(elements != null && depth < 12, prefix + ": invalid elements or nesting");
        int index = 0;
        for (JsonElement value : elements) {
            JsonObject element = value.getAsJsonObject();
            String id = string(element, "id", prefix + "/" + index);
            index++;
            check(!id.isBlank() && id.length() <= 128 && ids.add(id), "duplicate or invalid element id " + id);
            element.addProperty("id", id);
            String type = string(element, "type", "");
            if (type.equals("group")) identities(element.getAsJsonArray("elements"), id, ids, depth + 1);
            if (type.equals("scene")) {
                Set<String> blocks = new HashSet<>();
                JsonArray entries = element.has("blocks") ? element.getAsJsonArray("blocks") : new JsonArray();
                for (JsonElement blockValue : entries) {
                    JsonObject block = blockValue.getAsJsonObject();
                    String blockId = string(block, "id", positionId(block.getAsJsonArray("pos")));
                    check(blocks.add(blockId), id + ": duplicate block identity " + blockId);
                    block.addProperty("id", blockId);
                }
                if (element.has("steps")) {
                    JsonArray steps = element.getAsJsonArray("steps");
                    check(steps.size() <= 64, id + ": too many construction steps");
                    Set<String> stepIds = new HashSet<>();
                    for (JsonElement stepValue : steps) {
                        JsonObject step = stepValue.getAsJsonObject();
                        check(!string(step, "id", "").isBlank() && stepIds.add(string(step, "id", "")), id + ": missing or duplicate step id");
                        check(!step.has("remove") && !step.has("replace"), id + ": construction steps must be additive");
                        if (step.has("include")) {
                            check(step.getAsJsonArray("include").size() <= MAX_BLOCKS, id + ": step selection too large");
                            for (JsonElement selected : step.getAsJsonArray("include")) check(blocks.contains(selected.getAsString()), id + ": unknown step block " + selected.getAsString());
                        }
                        for (String field : List.of("yaw", "pitch", "zoom", "time")) if (step.has(field)) check(step.get(field).isJsonPrimitive() && step.get(field).getAsJsonPrimitive().isNumber(), id + ": step " + field + " must be a number");
                        if (step.has("pitch")) check(Math.abs(number(step, "pitch", 0)) <= 90, id + ": step pitch out of range");
                        if (step.has("zoom")) check(number(step, "zoom", 1) >= .3 && number(step, "zoom", 1) <= 4, id + ": step zoom out of range");
                        if (step.has("time")) check(number(step, "time", 2.5) >= .5 && number(step, "time", 2.5) <= 30, id + ": step time out of range");
                        if (step.has("markers")) {
                            check(step.get("markers").isJsonArray() && step.getAsJsonArray("markers").size() <= 8, id + ": step markers must be an array of at most 8");
                            for (JsonElement markerValue : step.getAsJsonArray("markers")) {
                                JsonObject marker = markerValue.getAsJsonObject();
                                check(marker.has("block") != marker.has("pos"), id + ": marker needs exactly one of block or pos");
                                if (marker.has("block")) check(blocks.contains(string(marker, "block", "")), id + ": unknown marker block " + string(marker, "block", ""));
                                if (marker.has("pos")) check(marker.get("pos").isJsonArray() && marker.getAsJsonArray("pos").size() == 3, id + ": marker pos needs three numbers");
                                check(Set.of("right_click", "left_click", "item", "look", "drop", "warning", "sneak").contains(string(marker, "icon", marker.has("item") ? "item" : "right_click")), id + ": unsupported marker icon");
                                if (marker.has("item")) check(net.minecraft.resources.ResourceLocation.tryParse(string(marker, "item", "")) != null, id + ": invalid marker item");
                            }
                        }
                        if (step.has("conditions")) CustomDisplays.validateConditions(step.getAsJsonArray("conditions"));
                        if (step.has("progress")) check(step.get("progress").isJsonObject(), id + ": step progress must be an object");
                        if (step.has("image")) check(step.get("image").isJsonObject() && net.minecraft.resources.ResourceLocation.tryParse(string(step.getAsJsonObject("image"), "texture", "")) != null, id + ": step image needs a texture");
                        if (step.has("states")) {
                            check(step.get("states").isJsonObject() && step.getAsJsonObject("states").size() <= MAX_BLOCKS, id + ": invalid step states");
                            for (var change : step.getAsJsonObject("states").entrySet()) {
                                check(blocks.contains(change.getKey()), id + ": unknown step block " + change.getKey());
                                String state = change.getValue().isJsonPrimitive() ? change.getValue().getAsString() : "";
                                check(!state.isBlank() && state.length() <= 512 && net.minecraft.resources.ResourceLocation.tryParse(state.split("\\[", 2)[0]) != null, id + ": invalid step state " + state);
                            }
                        }
                    }
                }
            }
            if (element.has("role")) check(ROLES.contains(string(element, "role", "")), id + ": unsupported ingredient role");
            if (type.equals("requirements")) {
                check(element.has("entries") && element.getAsJsonArray("entries").size() <= 64, id + ": invalid requirements");
                for (JsonElement entry : element.getAsJsonArray("entries")) check(entry.getAsJsonObject().has("text"), id + ": requirement text missing");
            }
            if (type.equals("materials")) check(!string(element, "scene", "").isBlank(), id + ": materials.scene missing");
        }
    }

    public static String positionId(JsonArray position) { return position.get(0).getAsString() + "," + position.get(1).getAsString() + "," + position.get(2).getAsString(); }

    public static int restoreStep(JsonObject scene, String retainedId, int previous) {
        if (!scene.has("steps")) return -1;
        JsonArray steps = scene.getAsJsonArray("steps");
        if (!retainedId.isBlank()) {
            for (int i = 0; i < steps.size(); i++) if (string(steps.get(i).getAsJsonObject(), "id", "").equals(retainedId)) return i;
            return -1;
        }
        return previous >= 0 && previous < steps.size() ? previous : -1;
    }

    public static Set<String> selectedBlocks(JsonObject scene, int step, boolean additions) {
        JsonArray blocks = scene.has("blocks") ? scene.getAsJsonArray("blocks") : new JsonArray();
        Set<String> result = new LinkedHashSet<>();
        if (step < 0 || !scene.has("steps")) {
            for (JsonElement value : blocks) result.add(blockId(value.getAsJsonObject()));
            return result;
        }
        JsonArray steps = scene.getAsJsonArray("steps");
        Set<String> previous = new LinkedHashSet<>();
        Set<String> changed = new LinkedHashSet<>();
        for (int i = 0; i <= Math.min(step, steps.size() - 1); i++) {
            if (i == step) previous.addAll(result);
            JsonObject selection = steps.get(i).getAsJsonObject();
            if (i == step && selection.has("states")) changed.addAll(selection.getAsJsonObject("states").keySet());
            for (JsonElement value : blocks) {
                JsonObject block = value.getAsJsonObject();
                String id = blockId(block);
                boolean include = selection.has("through_y") && block.getAsJsonArray("pos").get(1).getAsDouble() <= number(selection, "through_y", 0);
                if (selection.has("include")) for (JsonElement selected : selection.getAsJsonArray("include")) include |= selected.getAsString().equals(id);
                if (selection.has("groups") && block.has("groups")) for (JsonElement group : selection.getAsJsonArray("groups")) include |= block.getAsJsonArray("groups").contains(group);
                if (include) result.add(id);
            }
        }
        if (additions) {
            result.removeAll(previous);
            for (String id : changed) if (previous.contains(id)) result.add(id);
        }
        return Set.copyOf(result);
    }

    public static String stateAt(JsonObject scene, int step, JsonObject block) {
        String state = string(block, "state", "minecraft:air");
        if (!scene.has("steps")) return state;
        JsonArray steps = scene.getAsJsonArray("steps");
        int last = step < 0 ? steps.size() - 1 : Math.min(step, steps.size() - 1);
        String id = blockId(block);
        for (int i = 0; i <= last; i++) {
            JsonObject selection = steps.get(i).getAsJsonObject();
            if (selection.has("states") && selection.getAsJsonObject("states").has(id)) state = selection.getAsJsonObject("states").get(id).getAsString();
        }
        return state;
    }

    public static String blockId(JsonObject block) { return block.has("id") ? block.get("id").getAsString() : positionId(block.getAsJsonArray("pos")); }

    public static int visibility(JsonObject block, int layer, String mode, Set<String> stepBlocks) {
        if (!stepBlocks.contains(blockId(block))) return 0;
        if (layer == Integer.MIN_VALUE || mode.equals("all")) return 1;
        int y = (int) Math.floor(block.getAsJsonArray("pos").get(1).getAsDouble());
        return switch (mode) {
            case "through" -> y <= layer ? 1 : 0;
            case "context" -> y == layer ? 1 : 2;
            default -> y == layer ? 1 : 0;
        };
    }

    public static List<Material> materials(JsonObject scene, Set<String> selection, Function<String, String> itemResolver) {
        Map<String, MutableMaterial> result = new TreeMap<>();
        JsonArray blocks = scene.has("blocks") ? scene.getAsJsonArray("blocks") : new JsonArray();
        for (JsonElement value : blocks) {
            JsonObject block = value.getAsJsonObject();
            String identity = blockId(block), state = string(block, "state", "minecraft:air");
            if (!selection.contains(identity) || block.has("decorative") && block.get("decorative").getAsBoolean()) continue;
            String base = state.split("\\[", 2)[0];
            if (Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void").contains(base)) continue;
            JsonObject rule = new JsonObject();
            if (scene.has("material_rules")) for (JsonElement ruleValue : scene.getAsJsonArray("material_rules")) {
                JsonObject candidate = ruleValue.getAsJsonObject();
                if (string(candidate, "state", "").equals(state) || string(candidate, "state", "").equals(base)) rule = candidate;
            }
            if (block.has("material")) {
                rule = rule.deepCopy();
                for (var entry : block.getAsJsonObject("material").entrySet()) rule.add(entry.getKey(), entry.getValue());
            }
            if (rule.has("exclude") && rule.get("exclude").getAsBoolean()) continue;
            boolean secondary = state.contains("half=upper") || state.contains("part=head");
            String item = string(rule, "item", itemResolver.apply(base));
            boolean unresolved = item.isBlank() || item.equals("minecraft:air");
            String key = string(rule, "key", unresolved ? "unresolved:" + state : item);
            double authored = number(rule, "count", secondary ? 0 : 1);
            check(Double.isFinite(authored) && authored >= 0 && authored <= 1000000 && authored == Math.floor(authored), "material count must be an integer from 0 to 1000000");
            int quantity = (int) authored;
            String label = string(rule, "label", unresolved ? state : item);
            MutableMaterial row = result.computeIfAbsent(key, ignored -> new MutableMaterial(item, label, unresolved));
            row.count = Math.addExact(row.count, quantity); row.blocks++; row.contributors.add(identity);
        }
        List<Material> rows = new ArrayList<>();
        result.forEach((key, row) -> rows.add(new Material(key, row.item, row.label, row.count, row.blocks, Set.copyOf(row.contributors), row.unresolved)));
        return List.copyOf(rows);
    }

    private static final class MutableMaterial {
        final String item, label;
        final boolean unresolved;
        long count;
        int blocks;
        final Set<String> contributors = new LinkedHashSet<>();
        MutableMaterial(String item, String label, boolean unresolved) { this.item = item; this.label = label; this.unresolved = unresolved; }
    }

    public static JsonObject presentation(JsonObject definition, String variant, int width, String requested) {
        JsonObject result = definition;
        if (definition.has("variants")) for (JsonElement value : definition.getAsJsonArray("variants")) {
            JsonObject choice = value.getAsJsonObject();
            if (string(choice, "id", "").equals(variant) && choice.has("document")) { result = choice.getAsJsonObject("document"); break; }
        }
        if (!result.has("layouts")) return result;
        JsonObject selected = null;
        for (JsonElement value : result.getAsJsonArray("layouts")) {
            JsonObject layout = value.getAsJsonObject();
            if (!requested.equals("auto")) { if (string(layout, "id", "").equals(requested)) selected = layout; }
            else if (number(layout, "min_width", 0) <= width && (selected == null || number(layout, "min_width", 0) > number(selected, "min_width", 0))) selected = layout;
        }
        if (selected == null) return result;
        JsonObject merged = result.deepCopy();
        selected.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue().deepCopy()));
        return merged;
    }

    public static List<Diagnostic> lint(String source, JsonObject definition) {
        List<Diagnostic> result = new ArrayList<>();
        lintElements(source, definition.getAsJsonArray("elements"), "elements", 0, 0, number(definition, "width", 320), number(definition, "height", 180), result);
        return List.copyOf(result);
    }

    private static void lintElements(String source, JsonArray elements, String path, double originX, double originY, double width, double height, List<Diagnostic> result) {
        if (elements == null) return;
        for (int i = 0; i < elements.size() && result.size() < 128; i++) {
            JsonObject e = elements.get(i).getAsJsonObject();
            String field = path + "[" + i + "]";
            if (string(e, "type", "").equals("scene") && e.has("hero") && e.get("hero").getAsBoolean()) continue;
            Bounds b = bounds(e); double x = originX + b.x, y = originY + b.y, w = b.width, h = b.height;
            if (x < -0.5 || y < -0.5 || x + w > width + 0.5 || y + h > height + 0.5) result.add(new Diagnostic("warning", source, field, "Element extends outside its canvas; verify clipping at the intended viewport"));
            if (string(e, "type", "").equals("text") && w < 24) result.add(new Diagnostic("warning", source, field + ".width", "Narrow text area; verify translated text wrapping"));
            if (string(e, "type", "").equals("group") && e.has("elements")) lintElements(source, e.getAsJsonArray("elements"), field + ".elements", originX + number(e, "x", 0), originY + number(e, "y", 0), width, height, result);
        }
    }

    public static void expandRepeats(JsonObject object) { expandRepeats(object, 0, new int[]{0}); }

    private static void expandRepeats(JsonObject object, int depth, int[] count) {
        check(depth < 12, "repeat/group nesting exceeds limit");
        JsonArray elements = object.getAsJsonArray("elements");
        JsonArray expanded = new JsonArray();
        for (JsonElement value : elements) {
            JsonObject element = value.getAsJsonObject();
            if (string(element, "type", "").equals("repeat")) {
                JsonArray items = element.getAsJsonArray("items");
                check(items != null && items.size() <= 128, "repeat.items exceeds limit");
                JsonObject group = new JsonObject(); group.addProperty("type", "group");
                for (String field : List.of("id", "x", "y", "layout", "columns", "cell_width", "cell_height", "radius", "start_angle")) if (element.has(field)) group.add(field, element.get(field).deepCopy());
                JsonArray children = new JsonArray();
                for (int i = 0; i < items.size(); i++) {
                    JsonObject child = replaceItem(element.getAsJsonObject("element"), items.get(i), i).getAsJsonObject();
                    if (child.has("id")) child.addProperty("id", child.get("id").getAsString() + "/" + i);
                    children.add(child);
                }
                group.add("elements", children); element = group;
            }
            check(++count[0] <= MAX_ELEMENTS, "generated elements exceed limit");
            if (element.has("elements")) expandRepeats(element, depth + 1, count);
            expanded.add(element);
            check(expanded.size() <= MAX_ELEMENTS, "generated elements exceed limit");
        }
        object.add("elements", expanded);
    }

    private static JsonElement replaceItem(JsonElement value, JsonElement item, int index) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (text.equals("$item")) return item.deepCopy();
            if (text.equals("$index")) return new JsonPrimitive(index);
            if (text.startsWith("$item.") && item.isJsonObject() && item.getAsJsonObject().has(text.substring(6))) return item.getAsJsonObject().get(text.substring(6)).deepCopy();
        }
        if (value.isJsonObject()) { JsonObject result = new JsonObject(); value.getAsJsonObject().entrySet().forEach(entry -> result.add(entry.getKey(), replaceItem(entry.getValue(), item, index))); return result; }
        if (value.isJsonArray()) { JsonArray result = new JsonArray(); for (JsonElement child : value.getAsJsonArray()) result.add(replaceItem(child, item, index)); return result; }
        return value.deepCopy();
    }

    public static JsonObject preset(String name, JsonObject values) {
        name = name.replace("antos:presets/", "");
        check(PRESETS.contains(name), "unknown display preset " + name);
        JsonObject result = new JsonObject();
        result.addProperty("version", 1); result.addProperty("title", string(values, "heading", name)); result.addProperty("width", 320); result.addProperty("height", name.equals("structure") ? 200 : 150);
        JsonArray elements = new JsonArray(); result.add("elements", elements);
        if (name.equals("structure")) {
            JsonObject scene = values.getAsJsonObject("scene").deepCopy(); scene.addProperty("type", "scene"); scene.addProperty("id", "structure"); scene.addProperty("width", 205); scene.addProperty("height", 192); elements.add(scene);
            JsonObject materials = new JsonObject(); materials.addProperty("type", "materials"); materials.addProperty("scene", "structure"); materials.addProperty("x", 214); materials.addProperty("width", 100); materials.addProperty("height", 190); elements.add(materials);
            return result;
        }
        if (name.equals("chain")) {
            JsonArray stages = values.getAsJsonArray("stages"); check(stages != null && stages.size() <= 12, "chain.stages requires at most 12 stages");
            for (int i = 0; i < stages.size(); i++) {
                JsonObject group = stages.get(i).getAsJsonObject().deepCopy(); group.addProperty("type", "group"); group.addProperty("y", i * 72); elements.add(group);
            }
            result.addProperty("height", Math.max(24, stages.size() * 72)); return result;
        }
        JsonArray inputs = values.has("inputs") ? values.getAsJsonArray("inputs").deepCopy() : new JsonArray();
        JsonArray outputs = values.has("outputs") ? values.getAsJsonArray("outputs").deepCopy() : new JsonArray();
        check(inputs.size() <= 16 && outputs.size() <= 9, "preset ingredient capacity exceeded");
        for (JsonElement value : inputs) { JsonObject slot = value.getAsJsonObject(); slot.addProperty("type", "slot"); if (!slot.has("role")) slot.addProperty("role", "input"); }
        for (JsonElement value : outputs) { JsonObject slot = value.getAsJsonObject(); slot.addProperty("type", "slot"); slot.addProperty("role", "output"); }
        boolean radial = name.equals("radial");
        double authoredColumns = number(values, "columns", inputs.size() > 9 ? 4 : 3);
        check(Double.isFinite(authoredColumns) && authoredColumns >= 1 && authoredColumns <= 6 && authoredColumns == Math.floor(authoredColumns), "preset.columns must be an integer from 1 to 6");
        int columns = Math.max(1, Math.min((int) authoredColumns, inputs.size()));
        int margin = 6, inputRight, inputBottom, middle;
        JsonObject inputGroup;
        if (radial) {
            int radius = inputs.size() > 8 ? 44 : 36;
            middle = margin + radius + 9;
            inputGroup = group("inputs", middle, middle, inputs);
            inputGroup.addProperty("layout", "radial"); inputGroup.addProperty("radius", radius);
            inputRight = middle + radius + 9; inputBottom = inputRight;
        } else {
            int rows = Math.max(1, (inputs.size() + columns - 1) / columns);
            inputGroup = group("inputs", margin, margin, inputs);
            inputGroup.addProperty("layout", "grid"); inputGroup.addProperty("columns", columns);
            inputRight = margin + columns * 22 - 4; inputBottom = margin + rows * 22 - 4; middle = (margin + inputBottom) / 2;
        }
        elements.add(inputGroup);
        if (radial && values.has("focus")) { JsonObject focus = values.getAsJsonObject("focus").deepCopy(); focus.addProperty("type", "slot"); focus.addProperty("x", middle - 9); focus.addProperty("y", middle - 9); elements.add(focus); }
        JsonObject arrow = new JsonObject(); arrow.addProperty("type", "arrow"); arrow.addProperty("x", inputRight + 8); arrow.addProperty("y", middle); arrow.addProperty("x2", inputRight + 36); arrow.addProperty("y2", middle); elements.add(arrow);
        int outputColumns = Math.max(1, Math.min(3, outputs.size())), outputRows = Math.max(1, (outputs.size() + outputColumns - 1) / outputColumns);
        int outputX = inputRight + 46, outputY = Math.max(margin, middle - (outputRows * 22 - 4) / 2);
        JsonObject outputGroup = group("outputs", outputX, outputY, outputs); outputGroup.addProperty("layout", "grid"); outputGroup.addProperty("columns", outputColumns); elements.add(outputGroup);
        int right = outputX + outputColumns * 22 - 4, bottom = Math.max(inputBottom, outputY + outputRows * 22 - 4);
        if (values.has("fluids")) {
            JsonArray fluids = values.getAsJsonArray("fluids"); check(fluids.size() <= 4, "preset fluid capacity exceeded");
            int fluidY = inputBottom + 12;
            int i = 0; for (JsonElement value : fluids) { JsonObject fluid = value.getAsJsonObject().deepCopy(); fluid.addProperty("type", "fluid"); fluid.addProperty("x", margin + i++ * 24); fluid.addProperty("y", fluidY); fluid.addProperty("width", 18); fluid.addProperty("height", 40); elements.add(fluid); }
            if (!fluids.isEmpty()) { bottom = Math.max(bottom, fluidY + 40); right = Math.max(right, margin + fluids.size() * 24 - 6); }
        }
        int width = Math.max(150, right + margin), y = bottom + 8;
        for (String key : List.of("duration", "energy")) if (values.has(key)) { JsonObject label = new JsonObject(); label.addProperty("type", "text"); label.addProperty("x", margin); label.addProperty("y", y); label.addProperty("width", width - margin * 2); label.addProperty("color", "muted"); label.addProperty("text", values.get(key).getAsString()); elements.add(label); y += 12; }
        result.addProperty("width", width);
        result.addProperty("height", Math.max(24, y + 2));
        return result;
    }

    private static JsonObject group(String id, int x, int y, JsonArray elements) { JsonObject result = new JsonObject(); result.addProperty("type", "group"); result.addProperty("id", id); result.addProperty("x", x); result.addProperty("y", y); result.add("elements", elements); return result; }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
