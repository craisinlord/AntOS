package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

final class StudioIcons {
    private StudioIcons() { }

    static StudioIcon of(StudioStore store, StudioKey key) {
        JsonObject body = store.view(key);
        if (body == null) return StudioIcon::unknown;
        try { return of(store, key.kind(), body); }
        catch (RuntimeException exception) { return StudioIcon::unknown; }
    }

    static StudioIcon of(StudioStore store, AuthoringKind kind, JsonObject body) {
        return switch (kind) {
            case TASK -> task(store, body);
            case CATEGORY, GROUP -> named(body, "icon", "icon_entity", "minecraft:paper");
            case REWARD_POOL -> StudioIcon.item("minecraft:bundle");
            case DISK -> disk(store, body);
            case DISK_CATEGORY -> StudioIcon.texture(StudioJson.string(body, "texture", "antos:item/floppy_disk/floppy_disk"));
            case PROGRAM -> StudioIcon.pixel("desktop/games");
            case MESSAGE -> named(body, "sender_avatar_item", "sender_avatar_entity", "minecraft:paper");
            case MAIL_POOL -> StudioIcon.pixel("desktop/antmail");
            case PRODUCT -> product(body);
            case SELL_RULE -> {
                String item = StudioJson.string(body, "item");
                if (item.isEmpty() && !StudioJson.list(body, "items").isEmpty()) item = StudioJson.list(body, "items").get(0).getAsString();
                yield item.isEmpty() ? StudioIcon.coin() : StudioIcon.item(item);
            }
            case DEAL_CAMPAIGN -> StudioIcon.coin();
            case ARCHIVE_ENTRY -> {
                for (String field : new String[]{"cover_item", "item"}) if (!StudioJson.string(body, field).isBlank()) yield StudioIcon.item(StudioJson.string(body, field));
                for (String field : new String[]{"cover_entity", "entity"}) if (!StudioJson.string(body, field).isBlank()) yield StudioIcon.entity(StudioJson.string(body, field));
                yield StudioIcon.item("minecraft:book");
            }
            case ARCHIVE_FOLDER -> StudioIcon.item(StudioJson.string(body, "icon_item", "minecraft:book"));
            case TERMINAL_COMMAND -> StudioIcon.pixel("desktop/terminal");
        };
    }

    private static StudioIcon named(JsonObject body, String item, String entity, String fallback) {
        if (!StudioJson.string(body, item).isBlank()) return StudioIcon.item(StudioJson.string(body, item));
        if (!StudioJson.string(body, entity).isBlank()) return StudioIcon.entity(StudioJson.string(body, entity));
        return StudioIcon.item(fallback);
    }

    static StudioIcon task(StudioStore store, JsonObject body) {
        try { return taskIcon(store, body); }
        catch (RuntimeException exception) { return StudioIcon.item("minecraft:paper"); }
    }

    private static StudioIcon taskIcon(StudioStore store, JsonObject body) {
        if (!StudioJson.string(body, "icon").isBlank()) return StudioIcon.item(StudioJson.string(body, "icon"));
        if (!StudioJson.string(body, "icon_entity").isBlank()) return StudioIcon.entity(StudioJson.string(body, "icon_entity"));
        JsonArray objectives = StudioJson.list(body, "objectives");
        for (int index = 0; index < objectives.size(); index++) {
            StudioIcon icon = objective(store, objectives.get(index).getAsJsonObject());
            if (icon != null) return icon;
        }
        JsonArray rewards = StudioJson.list(body, "rewards");
        for (int index = 0; index < rewards.size(); index++) {
            JsonObject reward = rewards.get(index).getAsJsonObject();
            if (StudioJson.string(reward, "type").equals("item")) return StudioIcon.item(StudioJson.string(reward, "item"));
        }
        return StudioIcon.item("minecraft:paper");
    }

    static StudioIcon objective(StudioStore store, JsonObject row) {
        String target = StudioJson.string(row, "target");
        return switch (StudioJson.string(row, "type")) {
            case "item" -> StudioIcon.item(target);
            case "item_tag" -> StudioIcon.item("minecraft:bundle");
            case "disk_installed" -> diskIcon(store, target);
            case "game_record" -> StudioIcon.pixel("desktop/games");
            case "mail_read" -> StudioIcon.pixel("desktop/antmail");
            case "archive_viewed" -> StudioIcon.pixel("desktop/archive");
            case "terminal_command" -> StudioIcon.pixel("desktop/terminal");
            case "program_ending" -> StudioIcon.pixel("desktop/games");
            case "advancement" -> StudioIcon.item("minecraft:knowledge_book");
            case "dimension_entered", "biome_visited", "structure_entered" -> StudioIcon.item("minecraft:filled_map");
            case "stat" -> switch (StudioJson.string(row, "stat_type")) {
                case "entity_killed", "entity_killed_by" -> StudioIcon.entity(target);
                case "custom" -> StudioIcon.item("minecraft:clock");
                default -> StudioIcon.item(target);
            };
            default -> null;
        };
    }

    static StudioIcon diskIcon(StudioStore store, String id) {
        JsonObject body = store.view(new StudioKey(AuthoringKind.DISK, id));
        return body == null ? StudioIcon.texture("antos:item/floppy_disk/floppy_disk") : disk(store, body);
    }

    static StudioIcon disk(StudioStore store, JsonObject body) { return StudioIcon.texture(diskTexture(store, body)); }

    static String diskTexture(StudioStore store, JsonObject body) {
        String category = StudioJson.string(body, "category", "antos:general");
        JsonObject found = store.view(new StudioKey(AuthoringKind.DISK_CATEGORY, category.contains(":") ? category : "antos:" + category));
        return StudioJson.string(found, "texture", "antos:item/floppy_disk/floppy_disk");
    }

    static StudioIcon product(JsonObject body) {
        JsonObject thumbnail = StudioJson.object(body, "thumbnail");
        if (!StudioJson.string(thumbnail, "item").isBlank()) return StudioIcon.item(StudioJson.string(thumbnail, "item"));
        if (!StudioJson.string(thumbnail, "entity").isBlank()) return StudioIcon.entity(StudioJson.string(thumbnail, "entity"));
        return StudioIcon.item("minecraft:chest");
    }

    static StudioIcon reward(StudioStore store, JsonObject reward) {
        return switch (StudioJson.string(reward, "type", "item")) {
            case "item" -> StudioIcon.item(StudioJson.string(reward, "item"));
            case "antcoins" -> StudioIcon.coin();
            case "experience", "experience_levels" -> StudioIcon.experience();
            case "item_pool" -> StudioIcon.item("minecraft:bundle");
            case "choice" -> StudioIcon.item("minecraft:chest");
            case "archive" -> StudioIcon.pixel("desktop/archive");
            case "advancement" -> StudioIcon.item("minecraft:knowledge_book");
            case "effect" -> StudioIcon.item("minecraft:potion");
            case "mail" -> StudioIcon.pixel("desktop/antmail");
            default -> StudioIcon::unknown;
        };
    }
}
