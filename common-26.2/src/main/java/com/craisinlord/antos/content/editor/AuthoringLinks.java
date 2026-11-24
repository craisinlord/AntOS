package com.craisinlord.antos.content.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import java.util.Set;

public final class AuthoringLinks {
    private AuthoringLinks() { }

    public static boolean links(AuthoringKind owner, String namespace, JsonObject body, AuthoringKind target, String id) {
        return find(owner, namespace, body, null, "", target, id, true);
    }

    public static boolean loose(AuthoringKind owner, String namespace, JsonObject body, String id) {
        return find(owner, namespace, body, null, "", null, id, false);
    }

    public static JsonObject relink(AuthoringKind owner, String namespace, JsonObject body, AuthoringKind target, String old, String next, Set<AuthoringKind> alsoUsing) {
        return replace(owner, namespace, body, null, "", target, old, next, alsoUsing).getAsJsonObject();
    }

    public static void strings(JsonElement element, Set<String> result) {
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) result.add(element.getAsString());
        } else if (element.isJsonArray()) element.getAsJsonArray().forEach(child -> strings(child, result));
        else if (element.isJsonObject()) element.getAsJsonObject().entrySet().forEach(entry -> strings(entry.getValue(), result));
    }

    private static boolean find(AuthoringKind owner, String namespace, JsonElement element, JsonObject parent, String field, AuthoringKind target, String id, boolean typed) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) if (find(owner, namespace, child, parent, field, target, id, typed)) return true;
            return false;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
                if (find(owner, namespace, entry.getValue(), object, entry.getKey(), target, id, typed)) return true;
            return false;
        }
        if (parent == null || !same(owner, namespace, element, field, id)) return false;
        Set<AuthoringKind> kinds = targets(owner, parent, field);
        return typed ? kinds.contains(target) : kinds.isEmpty();
    }

    private static JsonElement replace(AuthoringKind owner, String namespace, JsonElement element, JsonObject parent, String field,
                                       AuthoringKind target, String old, String next, Set<AuthoringKind> alsoUsing) {
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                JsonElement changed = replace(owner, namespace, child, parent, field, target, old, next, alsoUsing);
                boolean keep = !changed.equals(child) && child.isJsonPrimitive()
                        && targets(owner, parent, field).stream().anyMatch(kind -> kind != target && alsoUsing.contains(kind));
                if (keep) result.add(child);
                result.add(changed);
            }
            return result;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
                result.add(entry.getKey(), replace(owner, namespace, entry.getValue(), object, entry.getKey(), target, old, next, alsoUsing));
            return result;
        }
        return parent != null && same(owner, namespace, element, field, old) && targets(owner, parent, field).contains(target) ? new JsonPrimitive(next) : element;
    }

    private static boolean same(AuthoringKind owner, String namespace, JsonElement element, String field, String id) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return false;
        String value = element.getAsString();
        if (value.equals(id)) return true;
        boolean local = owner == AuthoringKind.DISK && field.equals("category") || owner == AuthoringKind.MESSAGE && field.equals("pool");
        return local && !value.isBlank() && !value.contains(":") && id.equals(namespace + ":" + value);
    }

    private static Set<AuthoringKind> targets(AuthoringKind owner, JsonObject parent, String field) {
        return switch (field) {
            case "requires", "requires_any", "connects" -> owner == AuthoringKind.TASK ? Set.of(AuthoringKind.TASK) : Set.of();
            case "tasks", "unlock_tasks", "tasks_complete" -> Set.of(AuthoringKind.TASK);
            case "disks", "unlock_disks" -> Set.of(AuthoringKind.DISK);
            case "commands", "unlock_commands" -> Set.of(AuthoringKind.TERMINAL_COMMAND);
            case "entries" -> owner == AuthoringKind.DISK || owner == AuthoringKind.TERMINAL_COMMAND ? Set.of(AuthoringKind.ARCHIVE_ENTRY) : Set.of();
            case "archive_entries" -> Set.of(AuthoringKind.ARCHIVE_ENTRY);
            case "programs" -> Set.of(AuthoringKind.PROGRAM);
            case "subscribe" -> Set.of(AuthoringKind.MESSAGE, AuthoringKind.MAIL_POOL);
            case "products", "exclude_products" -> owner == AuthoringKind.DEAL_CAMPAIGN ? Set.of(AuthoringKind.PRODUCT) : Set.of();
            case "mail_read" -> Set.of(AuthoringKind.MESSAGE);
            case "folder" -> owner == AuthoringKind.ARCHIVE_ENTRY ? Set.of(AuthoringKind.ARCHIVE_FOLDER) : Set.of();
            case "parent" -> owner == AuthoringKind.ARCHIVE_FOLDER ? Set.of(AuthoringKind.ARCHIVE_FOLDER) : Set.of();
            case "link_category" -> Set.of(AuthoringKind.CATEGORY);
            case "group", "link_group" -> owner == AuthoringKind.TASK ? Set.of(AuthoringKind.GROUP) : Set.of();
            case "category" -> switch (owner) {
                case TASK, GROUP -> Set.of(AuthoringKind.CATEGORY);
                case DISK -> Set.of(AuthoringKind.DISK_CATEGORY);
                default -> Set.of();
            };
            case "target_category" -> Set.of(AuthoringKind.DISK_CATEGORY);
            case "pool" -> owner == AuthoringKind.MESSAGE ? Set.of(AuthoringKind.MAIL_POOL)
                    : owner == AuthoringKind.REWARD_POOL || type(parent).equals("item_pool") && owner != AuthoringKind.PRODUCT ? Set.of(AuthoringKind.REWARD_POOL) : Set.of();
            case "entry" -> type(parent).equals("archive") ? Set.of(AuthoringKind.ARCHIVE_ENTRY) : Set.of();
            case "message" -> type(parent).equals("mail") ? Set.of(AuthoringKind.MESSAGE) : Set.of();
            case "value" -> owner != AuthoringKind.MESSAGE ? Set.of() : switch (type(parent)) {
                case "task_complete" -> Set.of(AuthoringKind.TASK);
                case "mail_read" -> Set.of(AuthoringKind.MESSAGE);
                case "disk_installed" -> Set.of(AuthoringKind.DISK);
                default -> Set.of();
            };
            case "target" -> owner != AuthoringKind.TASK ? Set.of() : switch (type(parent)) {
                case "disk_installed" -> Set.of(AuthoringKind.DISK);
                case "program_ending" -> Set.of(AuthoringKind.PROGRAM);
                case "terminal_command" -> Set.of(AuthoringKind.TERMINAL_COMMAND);
                case "mail_read" -> Set.of(AuthoringKind.MESSAGE);
                case "archive_viewed" -> Set.of(AuthoringKind.ARCHIVE_ENTRY);
                default -> Set.of();
            };
            default -> Set.of();
        };
    }

    private static String type(JsonObject object) {
        JsonElement value = object.get("type");
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
}
