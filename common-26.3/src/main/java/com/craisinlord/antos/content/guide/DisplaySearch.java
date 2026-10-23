package com.craisinlord.antos.content.guide;

import com.google.gson.*;
import java.util.*;
import java.util.function.Function;
import static com.craisinlord.antos.content.guide.CustomDisplays.*;

public final class DisplaySearch {
    public record Ingredient(String role, String item, String tag, String variant) { }
    private DisplaySearch() { }

    public static List<Ingredient> ingredients(JsonObject display, Set<String> accessible) {
        return ingredients(display, accessible, id -> id);
    }

    public static List<Ingredient> ingredients(JsonObject display, Set<String> accessible, Function<String, String> itemResolver) {
        List<Ingredient> result = new ArrayList<>();
        visit(display, accessible, "", result, 0, itemResolver);
        return List.copyOf(new LinkedHashSet<>(result));
    }

    public static List<String> labels(JsonObject display, Set<String> accessible) {
        List<String> result = new ArrayList<>(); labels(display, accessible, result, 0); return List.copyOf(result);
    }

    private static void labels(JsonObject document, Set<String> accessible, List<String> result, int depth) {
        if (depth > 16 || result.size() > 4096) return;
        for (String key : List.of("title", "text", "consumption")) if (document.has(key)) result.add(string(document, key, ""));
        for (String key : List.of("elements", "entries", "steps", "rows")) if (document.has(key)) for (JsonElement value : document.getAsJsonArray(key)) if (value.isJsonObject()) labels(value.getAsJsonObject(), accessible, result, depth + 1);
        if (document.has("variants")) for (JsonElement value : document.getAsJsonArray("variants")) {
            JsonObject choice = value.getAsJsonObject();
            if (choice.has("hidden") && choice.get("hidden").getAsBoolean() || choice.has("requires_entry") && !accessible.contains(string(choice, "requires_entry", ""))) continue;
            if (choice.has("document")) labels(choice.getAsJsonObject("document"), accessible, result, depth + 1);
        }
    }

    private static void visit(JsonObject display, Set<String> accessible, String variant, List<Ingredient> result, int depth, Function<String, String> itemResolver) {
        if (depth > 16 || result.size() >= 4096) return;
        elements(display.getAsJsonArray("elements"), variant, result, itemResolver);
        if (display.has("variants")) for (JsonElement value : display.getAsJsonArray("variants")) {
            JsonObject choice = value.getAsJsonObject();
            if (choice.has("hidden") && choice.get("hidden").getAsBoolean()) continue;
            if (choice.has("requires_entry") && !accessible.contains(string(choice, "requires_entry", ""))) continue;
            if (choice.has("document")) visit(choice.getAsJsonObject("document"), accessible, string(choice, "id", ""), result, depth + 1, itemResolver);
        }
    }

    private static void elements(JsonArray elements, String variant, List<Ingredient> result, Function<String, String> itemResolver) {
        if (elements == null) return;
        for (JsonElement value : elements) {
            if (result.size() >= 4096) return;
            JsonObject e = value.getAsJsonObject();
            String type = string(e, "type", "");
            String role = string(e, "role", "generic");
            if (type.equals("slot") || type.equals("item")) {
                if (e.has("item")) result.add(new Ingredient(role, string(e, "item", ""), "", variant));
                if (e.has("tag")) result.add(new Ingredient(role, "", string(e, "tag", ""), variant));
                if (e.has("choices")) for (JsonElement choice : e.getAsJsonArray("choices")) result.add(new Ingredient(role, choice.getAsString(), "", variant));
                if (e.has("stack")) stack(e.getAsJsonObject("stack"), role, variant, result);
                if (e.has("stacks")) for (JsonElement stack : e.getAsJsonArray("stacks")) stack(stack.getAsJsonObject(), role, variant, result);
            }
            if (type.equals("recipe_list") && e.has("rows")) for (JsonElement row : e.getAsJsonArray("rows")) {
                JsonObject entry = row.getAsJsonObject();
                for (String field : List.of("inputs", "outputs")) if (entry.has(field)) for (JsonElement slot : entry.getAsJsonArray(field)) {
                    String item = slot.isJsonPrimitive() ? slot.getAsString() : string(slot.getAsJsonObject(), "item", "");
                    if (!item.isBlank() && !item.startsWith("#")) result.add(new Ingredient(field.equals("inputs") ? "input" : "output", item, "", variant));
                    if (result.size() >= 4096) return;
                }
            }
            if (type.equals("scene") && e.has("blocks")) {
                for (var row : DisplayFeatures.materials(e, DisplayFeatures.selectedBlocks(e, -1, false), itemResolver)) {
                    if (!row.item().isBlank() && !row.unresolved()) result.add(new Ingredient("material", row.item(), "", variant));
                    if (result.size() >= 4096) break;
                }
            }
            if (e.has("elements")) elements(e.getAsJsonArray("elements"), variant, result, itemResolver);
        }
    }

    private static void stack(JsonObject stack, String role, String variant, List<Ingredient> result) {
        if (stack.has("id")) result.add(new Ingredient(role, string(stack, "id", ""), "", variant));
    }
}
