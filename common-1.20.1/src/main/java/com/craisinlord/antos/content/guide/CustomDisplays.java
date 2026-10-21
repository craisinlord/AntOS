package com.craisinlord.antos.content.guide;

import com.craisinlord.antos.AntOS;
import com.google.gson.*;
import java.io.InputStream;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class CustomDisplays {
    public static final class Problem extends IllegalArgumentException {
        public final String source, path;
        Problem(String source, String path, String message) { super(message); this.source = source; this.path = path; }
    }
    public static final int MAX_ELEMENTS = 512;
    public static final int MAX_BLOCKS = 4096;
    public static final int MAX_DEFINITION_CHARS = 262144;
    public static final int MAX_RESOLVED_CHARS = 4_000_000;
    public static final int MAX_TOTAL_CHARS = 24_000_000;
    public static final int MAX_DEFINITIONS = 2048;
    public static final int MAX_VARIANTS = 1024;
    private static volatile Map<ResourceLocation, JsonObject> definitions = Map.of();
    private static volatile long revision;
    private static volatile Map<String, List<DisplayFeatures.Diagnostic>> diagnostics = Map.of();
    private static ResourceManager serverResources;
    private static int serverBudget;
    private static boolean recompiling;
    private static final Set<String> TYPES = Set.of("slot", "fluid", "text", "image", "rect", "circle", "polygon", "line", "arrow", "bar", "recipe", "scene", "item", "entity", "group", "materials", "requirements", "conditions", "recipe_list");

    private CustomDisplays() { }

    public static long revision() { return revision; }
    public static Set<ResourceLocation> ids() { return Set.copyOf(definitions.keySet()); }
    public static Set<String> sourceIds() { return Set.copyOf(diagnostics.keySet()); }
    public static void serverStopped() { serverResources = null; serverBudget = 0; }
    public static Map<String, List<DisplayFeatures.Diagnostic>> diagnostics() { return diagnostics; }
    public static boolean pending(ResourceLocation id) { return diagnostics.getOrDefault(id.toString(), List.of()).stream().anyMatch(value -> value.severity().equals("info") && value.path().equals("recipe_binding")); }

    public static synchronized void recompile() {
        if (serverResources == null || recompiling) return;
        recompiling = true;
        try {
            Map<ResourceLocation, JsonObject> replacement = load(serverResources, serverBudget);
            if (!replacement.equals(definitions)) apply(replacement);
        } finally { recompiling = false; }
    }

    public static JsonObject get(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        JsonObject object = location == null ? null : definitions.get(location);
        return object == null ? null : object.deepCopy();
    }

    public static void apply(Map<ResourceLocation, JsonObject> replacement) {
        Map<ResourceLocation, JsonObject> copy = new LinkedHashMap<>();
        replacement.forEach((id, value) -> copy.put(id, value.deepCopy()));
        definitions = Collections.unmodifiableMap(copy);
        revision++;
    }

    public static JsonObject encode() { return encode(definitions); }

    public static JsonObject encode(Map<ResourceLocation, JsonObject> source) {
        JsonObject result = new JsonObject();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> result.add(entry.getKey().toString(), entry.getValue().deepCopy()));
        return result;
    }

    public static Map<ResourceLocation, JsonObject> decode(JsonElement encoded) {
        if (encoded == null) return Map.of();
        if (!encoded.isJsonObject() || encoded.toString().length() > MAX_TOTAL_CHARS) throw new IllegalArgumentException("Display snapshot exceeds its limit");
        Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
        for (var entry : encoded.getAsJsonObject().entrySet()) {
            if (result.size() >= MAX_DEFINITIONS) throw new IllegalArgumentException("Too many displays");
            JsonObject object = entry.getValue().getAsJsonObject().deepCopy();
            validate(object);
            result.put(new ResourceLocation(entry.getKey()), object);
        }
        return Map.copyOf(result);
    }

    public static Map<ResourceLocation, JsonObject> load(ResourceManager resources) {
        return load(resources, MAX_TOTAL_CHARS);
    }

    public static Map<ResourceLocation, JsonObject> load(ResourceManager resources, int budget) {
        serverResources = resources; serverBudget = budget;
        Map<String, List<DisplayFeatures.Diagnostic>> report = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        resources.listResources("computer/display", path -> path.getPath().endsWith(".json")).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation path = entry.getKey();
            ResourceLocation id = new ResourceLocation(path.getNamespace(), path.getPath().substring("computer/display/".length(), path.getPath().length() - 5));
            try (var reader = entry.getValue().openAsReader()) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    text.append(buffer, 0, read);
                    require(text.length() <= MAX_DEFINITION_CHARS, "definition exceeds size limit");
                }
                require(raw.size() < MAX_DEFINITIONS, "too many display definitions");
                raw.put(id, JsonParser.parseString(text.toString()).getAsJsonObject());
            } catch (Exception exception) {
                report.put(id.toString(), List.of(diagnostic(id.toString(), exception)));
                AntOS.LOGGER.error("Invalid AntOS display {}: {}", id, exception.getMessage());
            }
        });
        Map<ResourceLocation, JsonObject> resolved = new LinkedHashMap<>();
        int total = 2;
        for (ResourceLocation id : raw.keySet()) {
            try {
                JsonObject object = resolve(id, raw, new LinkedHashSet<>());
                if (object.has("variables")) {
                    JsonObject bindings = new JsonObject();
                    boolean complete = true;
                    for (var entry : object.getAsJsonObject("variables").entrySet()) {
                        JsonObject declaration = entry.getValue().getAsJsonObject();
                        if (!declaration.has("default")) { complete = false; continue; }
                        require(matches(string(declaration, "type", "string"), declaration.get("default")), "incorrect default type for " + entry.getKey());
                        bindings.add(entry.getKey(), declaration.get("default"));
                    }
                    if (!complete) { report.put(id.toString(), List.of(new DisplayFeatures.Diagnostic("info", id.toString(), "variables", "Template requires instance values and is not directly embeddable"))); continue; }
                    object.remove("variables");
                    object = substitute(object, bindings, 0).getAsJsonObject();
                }
                compile(object, resources);
                validate(object);
                report.put(id.toString(), DisplayFeatures.lint(id.toString(), object));
                int length = object.toString().length() + id.toString().length() + 4;
                require(total + length <= Math.min(budget, MAX_TOTAL_CHARS), "total display data exceeds network budget");
                total += length;
                resolved.put(id, object);
            } catch (Exception exception) {
                boolean waiting = !DisplayRecipes.ready() && String.valueOf(exception.getMessage()).contains("not yet loaded");
                report.put(id.toString(), List.of(waiting ? new DisplayFeatures.Diagnostic("info", id.toString(), "recipe_binding", "Waiting for recipes to load") : diagnostic(id.toString(), exception)));
                if (!waiting) AntOS.LOGGER.error("Invalid AntOS display {}: {}", id, exception.getMessage());
            }
        }
        diagnostics = Map.copyOf(report);
        return Map.copyOf(resolved);
    }

    private static DisplayFeatures.Diagnostic diagnostic(String id, Exception exception) {
        if (exception instanceof Problem problem) return new DisplayFeatures.Diagnostic("error", problem.source.isBlank() ? id : problem.source, problem.path, problem.getMessage());
        return new DisplayFeatures.Diagnostic("error", id, "$", exception.getMessage() == null ? exception.toString() : exception.getMessage());
    }

    public static void compile(JsonObject object, ResourceManager resources) throws Exception {
        compile(object, resources, 0);
    }

    private static void compile(JsonObject object, ResourceManager resources, int depth) throws Exception {
        require(depth < 16, "display compilation nesting limit");
        DisplayFeatures.expandRepeats(object);
        normalizeScenes(object, resources);
        DisplayFeatures.resolveLayouts(object);
        if (object.has("layouts")) for (JsonElement value : object.getAsJsonArray("layouts")) compile(value.getAsJsonObject(), resources, depth + 1);
        if (object.has("variants")) for (JsonElement value : object.getAsJsonArray("variants")) compile(value.getAsJsonObject().getAsJsonObject("document"), resources, depth + 1);
        DisplayFeatures.normalize(object);
    }

    public static JsonObject resolve(ResourceLocation id, Map<ResourceLocation, JsonObject> source, Set<ResourceLocation> path) {
        require(path.size() < 16 && path.add(id), "template cycle or nesting limit at " + id);
        JsonObject object = source.get(id);
        require(object != null, "missing template " + id);
        object = resolveBody(id, object.deepCopy(), source, path);
        path.remove(id);
        return object;
    }

    private static final Set<String> VARIANT_FIELDS = Set.of("id", "title", "group", "icon", "hidden", "requires_entry", "display", "document");

    private static JsonObject resolveBody(ResourceLocation id, JsonObject object, Map<ResourceLocation, JsonObject> source, Set<ResourceLocation> path) {
        require(number(object, "version", 1) == 1, "unsupported version at " + id);
        require(!object.has("preset") || !object.has("template") && !object.has("elements"), "preset displays cannot also declare template or elements");
        require(!object.has("recipe_binding") || object.has("preset") || object.has("template"), "recipe_binding requires a preset or template");
        JsonObject bound = object.has("recipe_binding") ? DisplayRecipes.values(object.getAsJsonObject("recipe_binding")) : new JsonObject();
        if (object.has("preset")) {
            JsonObject values = object.has("values") ? object.getAsJsonObject("values").deepCopy() : new JsonObject();
            for (var entry : bound.entrySet()) { require(!values.has(entry.getKey()), "recipe_binding: conflicting value " + entry.getKey()); values.add(entry.getKey(), entry.getValue()); }
            JsonObject generated = DisplayFeatures.preset(string(object, "preset", ""), values);
            for (String field : List.of("title", "minimum_scale", "maximum_scale", "variants", "layouts", "style")) if (object.has(field)) generated.add(field, object.get(field));
            object = generated;
        }
        if (object.has("template")) {
            require(object.keySet().stream().allMatch(key -> Set.of("$schema", "template", "values", "version", "recipe_binding").contains(key)), "template instances accept only template, values, version, and recipe_binding");
            JsonObject base = resolve(new ResourceLocation(string(object, "template", "")), source, path);
            JsonObject variables = base.has("variables") ? base.getAsJsonObject("variables") : new JsonObject();
            JsonObject values = object.has("values") ? object.getAsJsonObject("values").deepCopy() : new JsonObject();
            for (var entry : bound.entrySet()) { require(!values.has(entry.getKey()), "recipe_binding: conflicting value " + entry.getKey()); if (variables.has(entry.getKey())) values.add(entry.getKey(), entry.getValue()); }
            for (String name : values.keySet()) require(variables.has(name), "unknown variable " + name);
            JsonObject bindings = new JsonObject();
            for (var entry : variables.entrySet()) {
                JsonObject declaration = entry.getValue().getAsJsonObject();
                JsonElement value = values.has(entry.getKey()) ? values.get(entry.getKey()) : declaration.get("default");
                if (value == null || !matches(string(declaration, "type", "string"), value)) throw new Problem(string(object, "template", id.toString()), "variables." + entry.getKey(), "Missing or incorrectly typed variable for instance " + id);
                bindings.add(entry.getKey(), value);
            }
            base.remove("variables");
            object = substitute(base, bindings, 0).getAsJsonObject();
        }
        if (!object.has("elements")) object.add("elements", new JsonArray());
        if (object.has("variants")) {
            JsonArray variants = object.getAsJsonArray("variants");
            require(variants.size() > 0 && variants.size() <= MAX_VARIANTS, "variants: expected 1 to " + MAX_VARIANTS + " alternatives");
            Set<String> variantIds = new HashSet<>();
            for (JsonElement value : variants) {
                JsonObject variant = value.getAsJsonObject();
                require(variantIds.add(string(variant, "id", "")) && !string(variant, "id", "").isBlank(), "variants: missing or duplicate identity");
                JsonObject nested;
                if (variant.has("display")) nested = resolve(new ResourceLocation(variant.get("display").getAsString()), source, path);
                else {
                    JsonObject inline = new JsonObject();
                    for (var field : variant.entrySet()) if (!VARIANT_FIELDS.contains(field.getKey())) inline.add(field.getKey(), field.getValue().deepCopy());
                    if (!inline.has("template") && variant.has("title")) inline.add("title", variant.get("title").deepCopy());
                    require(inline.has("template") || inline.has("preset") || inline.has("elements"), "variants: needs display, template, preset or elements");
                    nested = resolveBody(id, inline, source, path);
                    for (String field : new ArrayList<>(variant.keySet())) if (!VARIANT_FIELDS.contains(field)) variant.remove(field);
                }
                require(!nested.has("variants"), "variants: flatten nested variant selectors into one list");
                variant.add("document", nested);
            }
        }
        return object;
    }

    private static boolean matches(String type, JsonElement value) {
        return switch (type) {
            case "array" -> value.isJsonArray();
            case "object" -> value.isJsonObject();
            case "number" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
            case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            default -> false;
        };
    }

    private static JsonElement substitute(JsonElement value, JsonObject bindings, int depth) {
        require(depth < 32, "variable nesting exceeds limit");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (text.startsWith("$") && bindings.has(text.substring(1))) return bindings.get(text.substring(1)).deepCopy();
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : value.getAsJsonArray()) result.add(substitute(child, bindings, depth + 1));
            return result;
        }
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (var entry : value.getAsJsonObject().entrySet()) result.add(entry.getKey(), substitute(entry.getValue(), bindings, depth + 1));
            return result;
        }
        return value.deepCopy();
    }

    public static void validate(JsonObject object) {
        require(object.toString().length() <= MAX_RESOLVED_CHARS, "definition exceeds size limit");
        validate(object, 0, new int[]{0});
    }

    private static void validate(JsonObject object, int depth, int[] count) {
        require(depth < 16, "display reference depth limit");
        require(number(object, "version", 1) == 1, "unsupported version");
        bounded(object, "width", 320, 32, 2048);
        bounded(object, "height", 180, 24, 2048);
        bounded(object, "minimum_scale", .6, .25, 1.5);
        bounded(object, "maximum_scale", 1, .25, 4);
        require(object.has("elements") && object.get("elements").isJsonArray(), "elements must be an array");
        validateElements(object.getAsJsonArray("elements"), 0, count, "elements");
        if (object.has("layouts")) {
            require(object.getAsJsonArray("layouts").size() <= 8, "layouts exceeds limit");
            for (JsonElement layout : object.getAsJsonArray("layouts")) validate(layout.getAsJsonObject(), depth + 1, count);
        }
        if (object.has("variants")) {
            require(object.getAsJsonArray("variants").size() <= MAX_VARIANTS, "variants exceeds limit");
            for (JsonElement variant : object.getAsJsonArray("variants")) validate(variant.getAsJsonObject().getAsJsonObject("document"), depth + 1, new int[]{0});
        }
    }

    private static void validateElements(JsonArray elements, int depth, int[] count, String path) {
        require(depth < 12, "elements: group nesting limit");
        int index = 0;
        for (JsonElement value : elements) {
            String location = path + "[" + index++ + "]";
            try {
            require(++count[0] <= MAX_ELEMENTS && value.isJsonObject(), "elements: invalid element or too many elements");
            JsonObject element = value.getAsJsonObject();
            String type = string(element, "type", "");
            require(TYPES.contains(type), "elements[" + (count[0] - 1) + "].type: unknown type " + type);
            for (String field : List.of("x", "y", "x2", "y2", "rotation")) bounded(element, field, 0, -4096, 4096);
            for (String field : List.of("width", "height", "size", "radius")) bounded(element, field, 16, 1, 2048);
            bounded(element, "count", 1, 1, 1000000);
            require(number(element, "count", 1) == Math.floor(number(element, "count", 1)), "count must be an integer");
            if (type.equals("group")) validateElements(element.getAsJsonArray("elements"), depth + 1, count, location + ".elements");
            if (type.equals("polygon")) {
                JsonArray points = element.getAsJsonArray("points");
                require(points != null && points.size() >= 3 && points.size() <= 64, "polygon.points requires 3 to 64 points");
                for (JsonElement point : points) position(point, 2, 4096);
            }
            if (type.equals("scene")) validateScene(element);
            if (type.equals("scene")) {
                bounded(element, "width", 180, 96, 2048);
                bounded(element, "height", 140, 70, 2048);
                bounded(element, "yaw", 45, -3600, 3600);
                bounded(element, "pitch", 25, -80, 80);
                bounded(element, "scale", 1, 0.3, 4);
                bounded(element, "step_time", 2.5, 0.5, 30);
                if (element.has("autoplay")) require(element.get("autoplay").isJsonPrimitive() && element.get("autoplay").getAsJsonPrimitive().isBoolean(), "scene.autoplay must be true or false");
                if (element.has("hero")) require(element.get("hero").isJsonPrimitive() && element.get("hero").getAsJsonPrimitive().isBoolean(), "scene.hero must be true or false");
                bounded(element, "ratio", .62, .3, 1.5);
                bounded(element, "max_screen", .6, .2, .95);
            }
            if (type.equals("bar")) { bounded(element, "value", 0, 0, 1); bounded(element, "animate", 0, 0, 600); }
            if (type.equals("conditions")) validateConditions(element.getAsJsonArray("entries"));
            if (type.equals("recipe_list")) {
                JsonArray rows = element.getAsJsonArray("rows");
                require(rows != null && rows.size() <= 1024, "recipe_list.rows requires at most 1024 rows");
                for (JsonElement row : rows) {
                    JsonObject entry = row.getAsJsonObject();
                    if (entry.has("inputs")) require(entry.getAsJsonArray("inputs").size() <= 16, "recipe_list.inputs exceeds 16");
                    if (entry.has("outputs")) require(entry.getAsJsonArray("outputs").size() <= 9, "recipe_list.outputs exceeds 9");
                    for (String field : List.of("entity", "archive", "task")) if (entry.has(field)) require(ResourceLocation.tryParse(string(entry, field, "")) != null, "recipe_list." + field + " invalid identifier");
                }
            }
            if (type.equals("fluid")) {
                bounded(element, "capacity", 1000, 1, 1000000000);
                bounded(element, "amount", 1000, 0, number(element, "capacity", 1000));
            }
            if (element.has("choices")) {
                require(element.getAsJsonArray("choices").size() <= 128, "slot.choices exceeds limit");
                for (JsonElement choice : element.getAsJsonArray("choices")) require(ResourceLocation.tryParse(choice.getAsString()) != null, "slot.choices: invalid item identifier");
            }
            for (String field : List.of("u", "v")) bounded(element, field, 0, 0, 8192);
            for (String field : List.of("source_width", "source_height", "texture_width", "texture_height")) bounded(element, field, 256, 1, 8192);
            for (String field : List.of("item", "entity", "fluid", "texture", "recipe", "tag", "archive", "task")) {
                if (element.has(field)) require(ResourceLocation.tryParse(string(element, field, "")) != null, "invalid " + field + " identifier");
            }
            } catch (Problem problem) { throw problem; }
            catch (RuntimeException exception) { throw new Problem("", location, exception.getMessage() == null ? "Invalid element" : exception.getMessage()); }
        }
    }

    private static void validateScene(JsonObject element) {
        if (element.has("origin")) position(element.get("origin"), 3, 64);
        JsonArray blocks = element.has("blocks") ? element.getAsJsonArray("blocks") : new JsonArray();
        require(blocks.size() <= MAX_BLOCKS, "scene.blocks exceeds limit");
        for (JsonElement value : blocks) {
            JsonObject block = value.getAsJsonObject();
            position(block.get("pos"), 3, 64);
            require(block.has("state") && block.get("state").getAsString().length() <= 512, "scene.blocks.state missing or too long");
            require(ResourceLocation.tryParse(block.get("state").getAsString().split("\\[", 2)[0]) != null, "scene.blocks.state has invalid block identifier");
            if (block.has("material")) validateMaterialRule(block.getAsJsonObject("material"));
            if (block.has("ghost")) require(block.get("ghost").isJsonPrimitive() && block.get("ghost").getAsJsonPrimitive().isBoolean(), "scene.blocks.ghost must be true or false");
            if (block.has("nbt")) require(block.get("nbt").isJsonPrimitive() && block.get("nbt").getAsString().length() <= 4096, "scene.blocks.nbt must be a string up to 4096 characters");
        }
        if (element.has("material_rules")) {
            require(element.getAsJsonArray("material_rules").size() <= MAX_BLOCKS, "scene.material_rules exceeds limit");
            for (JsonElement value : element.getAsJsonArray("material_rules")) {
                JsonObject rule = value.getAsJsonObject();
                require(ResourceLocation.tryParse(string(rule, "state", "").split("\\[", 2)[0]) != null, "material_rules.state has invalid block identifier");
                validateMaterialRule(rule);
            }
        }
        for (String kind : List.of("items", "entities")) {
            if (!element.has(kind)) continue;
            require(element.getAsJsonArray(kind).size() <= 64, "scene." + kind + " exceeds limit");
            for (JsonElement value : element.getAsJsonArray(kind)) {
                JsonObject entry = value.getAsJsonObject();
                position(entry.get("pos"), 3, 64);
                require(ResourceLocation.tryParse(string(entry, "id", "")) != null, "scene." + kind + ".id invalid");
                for (String field : List.of("held", "offhand", "helmet", "chestplate", "leggings", "boots")) if (entry.has(field)) require(ResourceLocation.tryParse(string(entry, field, "")) != null, "scene." + kind + "." + field + " invalid identifier");
                bounded(entry, "scale", 1, .05, 8);
                if (entry.has("nbt")) require(string(entry, "nbt", "").length() <= 4096, "scene." + kind + ".nbt too long");
            }
        }
        if (element.has("moves")) {
            require(element.getAsJsonArray("moves").size() <= 32, "scene.moves exceeds limit");
            for (JsonElement value : element.getAsJsonArray("moves")) {
                JsonObject move = value.getAsJsonObject();
                require(ResourceLocation.tryParse(string(move, "item", "")) != null, "scene.moves.item invalid");
                position(move.get("from"), 3, 64); position(move.get("to"), 3, 64);
                bounded(move, "duration", 1.5, .2, 30);
                bounded(move, "arc", 0, -16, 16);
            }
        }
        if (element.has("effects")) {
            require(element.getAsJsonArray("effects").size() <= 64, "scene.effects exceeds limit");
            for (JsonElement value : element.getAsJsonArray("effects")) {
                JsonObject effect = value.getAsJsonObject();
                require(EFFECTS.contains(string(effect, "kind", "")), "scene.effects.kind unsupported: " + string(effect, "kind", ""));
                if (string(effect, "kind", "").equals("beam")) { position(effect.get("from"), 3, 64); position(effect.get("to"), 3, 64); }
                else position(effect.get("pos"), 3, 64);
                bounded(effect, "count", 6, 1, 32);
                bounded(effect, "size", 1, .1, 8);
            }
        }
        if (element.has("paths")) {
            require(element.getAsJsonArray("paths").size() <= 32, "scene.paths exceeds limit");
            for (JsonElement value : element.getAsJsonArray("paths")) {
                JsonObject line = value.getAsJsonObject();
                JsonArray points = line.getAsJsonArray("points");
                require(points != null && points.size() >= 2 && points.size() <= 64, "scene.paths.points requires 2 to 64 points");
                for (JsonElement point : points) position(point, 3, 64);
                require(Set.of("cable", "pipe", "redstone", "fluid", "item", "data", "beam").contains(string(line, "kind", "cable")), "scene.paths.kind unsupported");
            }
        }
    }

    public static final Set<String> CONDITIONS = Set.of("day", "night", "full_moon", "new_moon", "rain", "thunder", "clear", "open_sky", "underground", "altar_power", "in_liquid", "light", "dark", "dimension", "biome", "time", "fire", "redstone", "power", "custom");
    public static final Set<String> EFFECTS = Set.of("beam", "fire", "soul_fire", "glow", "particles", "smoke", "spark");

    static void validateConditions(JsonArray entries) {
        require(entries != null && entries.size() <= 16, "conditions.entries requires at most 16 entries");
        for (JsonElement value : entries) {
            JsonObject entry = value.getAsJsonObject();
            require(CONDITIONS.contains(string(entry, "kind", "custom")), "conditions.kind unsupported: " + string(entry, "kind", ""));
            require(entry.has("kind") || entry.has("text"), "conditions entry needs kind or text");
            if (entry.has("item")) require(ResourceLocation.tryParse(string(entry, "item", "")) != null, "conditions.item invalid identifier");
        }
    }

    private static void validateMaterialRule(JsonObject rule) {
        bounded(rule, "count", 1, 0, 1000000);
        require(number(rule, "count", 1) == Math.floor(number(rule, "count", 1)), "material count must be an integer");
        if (rule.has("item")) require(ResourceLocation.tryParse(string(rule, "item", "")) != null, "material.item has invalid identifier");
        if (rule.has("key")) require(!string(rule, "key", "").isBlank(), "material.key cannot be empty");
    }

    private static void position(JsonElement value, int size, double limit) {
        require(value != null && value.isJsonArray() && value.getAsJsonArray().size() == size, "invalid position");
        for (JsonElement coordinate : value.getAsJsonArray()) require(Double.isFinite(coordinate.getAsDouble()) && Math.abs(coordinate.getAsDouble()) <= limit, "position exceeds bounds");
    }

    private static void normalizeScenes(JsonObject object, ResourceManager resources) throws Exception {
        for (JsonElement value : object.getAsJsonArray("elements")) {
            JsonObject element = value.getAsJsonObject();
            if (string(element, "type", "").equals("group")) {
                layoutGroup(element);
                normalizeScenes(element, resources);
            }
            if (!string(element, "type", "").equals("scene")) continue;
            int sources = (element.has("blocks") ? 1 : 0) + (element.has("pattern") ? 1 : 0) + (element.has("structure") ? 1 : 0);
            require(sources <= 1, "scene: choose blocks, pattern, or structure");
            if (element.has("structure")) {
                ResourceLocation id = new ResourceLocation(element.get("structure").getAsString());
                ResourceLocation path = new ResourceLocation(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
                var resource = resources.getResource(path).orElseThrow(() -> new IllegalArgumentException("missing structure " + id));
                try (InputStream input = resource.open()) {
                    element.add("blocks", structureBlocks(NbtIo.read(new java.io.DataInputStream(new java.io.BufferedInputStream(new java.util.zip.GZIPInputStream(input))), new NbtAccounter(4194304))));
                }
                element.remove("structure");
            }
            if (element.has("pattern")) {
                element.add("blocks", patternBlocks(element.getAsJsonArray("pattern"), element.getAsJsonObject("mapping")));
                element.remove("pattern");
                element.remove("mapping");
            }
            validateScene(element);
        }
    }

    public static JsonArray patternBlocks(JsonArray layers, JsonObject mapping) {
        require(layers.size() > 0 && layers.size() <= 64 && mapping != null, "scene.pattern: invalid layers or missing mapping");
        JsonArray result = new JsonArray();
        int rows = -1, columns = -1;
        for (int y = 0; y < layers.size(); y++) {
            JsonArray layer = layers.get(y).getAsJsonArray();
            if (rows < 0) rows = layer.size();
            require(layer.size() == rows && rows > 0 && rows <= 64, "scene.pattern: unequal layer depths");
            for (int z = 0; z < layer.size(); z++) {
                String row = layer.get(z).getAsString();
                if (columns < 0) columns = row.length();
                require(row.length() == columns && columns > 0 && columns <= 64, "scene.pattern: unequal row widths");
                for (int x = 0; x < row.length(); x++) {
                    String symbol = row.substring(x, x + 1);
                    if (symbol.equals(" ") || symbol.equals("_")) continue;
                    require(mapping.has(symbol), "scene.mapping: missing symbol " + symbol);
                    result.add(block(x, y, z, mapping.get(symbol).getAsString()));
                    require(result.size() <= MAX_BLOCKS, "scene.pattern exceeds block limit");
                }
            }
        }
        return result;
    }

    public static void layoutGroup(JsonObject group) {
        String layout = string(group, "layout", "manual");
        require(Set.of("manual", "grid", "radial", "column", "row").contains(layout), "group.layout must be manual, grid, radial, column, or row");
        if (layout.equals("column") || layout.equals("row")) {
            bounded(group, "gap", 4, 0, 256);
            require(group.getAsJsonArray("elements") != null && group.getAsJsonArray("elements").size() <= MAX_ELEMENTS, "group.elements missing or exceeds limit");
            return;
        }
        JsonArray elements = group.getAsJsonArray("elements");
        require(elements != null && elements.size() <= MAX_ELEMENTS, "group.elements missing or exceeds limit");
        bounded(group, "columns", 3, 1, 64);
        require(number(group, "columns", 3) == Math.floor(number(group, "columns", 3)), "group.columns must be an integer");
        bounded(group, "cell_width", 22, 1, 2048);
        bounded(group, "cell_height", 22, 1, 2048);
        bounded(group, "radius", 40, 1, 2048);
        bounded(group, "start_angle", -90, -3600, 3600);
        int columns = (int) number(group, "columns", 3);
        for (int i = 0; i < elements.size(); i++) {
            JsonObject child = elements.get(i).getAsJsonObject();
            double x = number(child, "x", 0), y = number(child, "y", 0);
            if (layout.equals("grid")) {
                x += i % columns * number(group, "cell_width", 22);
                y += i / columns * number(group, "cell_height", 22);
            } else if (layout.equals("radial")) {
                double angle = Math.toRadians(number(group, "start_angle", -90) + i * 360.0 / Math.max(1, elements.size()));
                x += Math.cos(angle) * number(group, "radius", 40) - number(child, "size", number(child, "width", 18)) / 2;
                y += Math.sin(angle) * number(group, "radius", 40) - number(child, "size", number(child, "height", 18)) / 2;
            }
            child.addProperty("x", x); child.addProperty("y", y);
        }
        group.remove("layout");
    }

    public static JsonArray structureBlocks(CompoundTag root) {
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty() && root.contains("palettes")) palette = root.getList("palettes", Tag.TAG_LIST).getList(0);
        require(!palette.isEmpty() && palette.size() <= MAX_BLOCKS, "structure palette missing or too large");
        ListTag source = root.getList("blocks", Tag.TAG_COMPOUND);
        require(source.size() <= MAX_BLOCKS, "structure exceeds block limit");
        JsonArray result = new JsonArray();
        for (int i = 0; i < source.size(); i++) {
            CompoundTag entry = source.getCompound(i);
            int stateIndex = entry.getInt("state");
            require(stateIndex >= 0 && stateIndex < palette.size(), "structure palette index out of range");
            CompoundTag state = palette.getCompound(stateIndex);
            String name = state.getString("Name");
            if (Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void").contains(name)) continue;
            CompoundTag properties = state.getCompound("Properties");
            StringJoiner values = new StringJoiner(",");
            properties.getAllKeys().stream().sorted().forEach(key -> values.add(key + "=" + properties.getString(key)));
            if (!properties.isEmpty()) name += "[" + values + "]";
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            require(pos.size() == 3, "invalid structure block position");
            result.add(block(pos.getInt(0), pos.getInt(1), pos.getInt(2), name));
        }
        return result;
    }

    private static JsonObject block(int x, int y, int z, String state) {
        JsonObject result = new JsonObject();
        JsonArray pos = new JsonArray();
        pos.add(x); pos.add(y); pos.add(z);
        result.add("pos", pos);
        result.addProperty("state", state);
        return result;
    }

    public static List<String> content(JsonElement value) {
        if (value == null) return List.of();
        require(value.isJsonArray() && value.getAsJsonArray().size() <= 128, "content must be an array of at most 128 blocks");
        List<String> result = new ArrayList<>();
        for (JsonElement part : value.getAsJsonArray()) {
            if (part.isJsonPrimitive()) result.add(part.getAsString());
            else {
                JsonObject object = part.getAsJsonObject();
                if (object.has("display")) {
                    String id = object.get("display").getAsString();
                    require(ResourceLocation.tryParse(id) != null, "content.display: invalid identifier");
                    if (object.has("beside") && object.get("beside").getAsBoolean()) result.add("@beside");
                    result.add("@display:" + id);
                } else if (object.has("recipe")) result.add("@recipe:" + object.get("recipe").getAsString());
                else if (object.has("text")) result.add(object.get("text").getAsString());
                else throw new IllegalArgumentException("content: expected text, display, or recipe");
            }
        }
        return List.copyOf(result);
    }

    public static String string(JsonObject object, String key, String fallback) { return object.has(key) ? object.get(key).getAsString() : fallback; }
    public static double number(JsonObject object, String key, double fallback) { return object.has(key) ? object.get(key).getAsDouble() : fallback; }

    private static void bounded(JsonObject object, String key, double fallback, double minimum, double maximum) {
        double value = number(object, key, fallback);
        require(Double.isFinite(value) && value >= minimum && value <= maximum, key + " must be between " + minimum + " and " + maximum);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
