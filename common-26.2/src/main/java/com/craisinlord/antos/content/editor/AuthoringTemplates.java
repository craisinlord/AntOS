package com.craisinlord.antos.content.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class AuthoringTemplates {
    private AuthoringTemplates() { }

    public static JsonObject create(AuthoringKind kind) {
        return JsonParser.parseString(switch (kind) {
            case TASK -> "{\"title\":\"New task\",\"description\":\"\",\"category\":\"antos:general\",\"availability\":\"automatic\",\"position\":{\"x\":0,\"y\":0},\"objectives\":[],\"rewards\":[]}";
            case CATEGORY -> "{\"title\":\"New category\",\"sort_order\":0}";
            case GROUP -> "{\"title\":\"New group\",\"sort_order\":0}";
            case REWARD_POOL -> "{\"entries\":[{\"item\":\"minecraft:bread\",\"weight\":1,\"count\":1}]}";
            case DISK -> "{\"title\":\"literal:New disk\",\"category\":\"antos:general\",\"entries\":[],\"tasks\":[],\"wallpaper\":\"\",\"description\":\"literal:New floppy disk\",\"hidden\":false,\"rewards\":[],\"files\":[],\"programs\":[],\"subscribe\":[],\"consumable\":false}";
            case DISK_CATEGORY -> "{\"title\":\"literal:New disk category\",\"texture\":\"antos:item/floppy_disk/floppy_disk\",\"color\":\"AAAAAA\"}";
            case PROGRAM -> "{\"type\":\"quiz\",\"title\":\"New quiz\",\"questions_per_run\":1,\"questions\":[{\"question\":\"New question\",\"answers\":[\"Answer 1\",\"Answer 2\"],\"correct\":0}],\"reward_on\":\"perfect\",\"reward_limit\":\"daily\",\"rewards\":[]}";
            case MESSAGE -> "{\"sender\":\"fieldoffice\",\"sender_display\":\"Field Office\",\"subject\":\"New message\",\"body\":\"Hello, {player}.\",\"trigger\":{\"type\":\"task_complete\",\"value\":\"antos:task\",\"delay_seconds\":0},\"once\":true}";
            case MAIL_POOL -> "{\"chance_per_day\":1.0,\"max_per_day\":1,\"subscription_only\":false,\"blockable\":true}";
            case TAKEOVER -> "{\"title\":\"New transmission\",\"lines\":[\"An incoming message.\"],\"style\":\"hijacked\",\"button\":\"CONTINUE\"}";
            case PRODUCT -> "{\"name\":\"New listing\",\"description\":\"\",\"category\":\"general\",\"tags\":[],\"thumbnail\":{\"item\":\"minecraft:chest\",\"entity\":\"\"},\"gallery\":[],\"quantity\":1,\"payments\":[{\"type\":\"antcoins\",\"resource\":\"antos:antcoins\",\"amount\":1}],\"unlock_mode\":\"all\",\"unlock_tasks\":[],\"availability\":{},\"rewards\":[{\"item\":\"minecraft:bread\",\"count\":1}],\"delivery\":\"falling_chest\",\"enabled\":true}";
            case SELL_RULE -> "{\"items\":[\"minecraft:rotten_flesh\"],\"value\":1,\"limit\":0,\"limit_reset\":\"none\",\"unlock_tasks\":[],\"unlock_mode\":\"all\"}";
            case DEAL_CAMPAIGN -> "{\"label\":\"New deal\",\"enabled\":true,\"picks\":1,\"every_minecraft_days\":1,\"order\":\"shuffled\",\"discount_percent\":{\"min\":10,\"max\":20},\"candidates\":{}}";
            case ARCHIVE_ENTRY -> "{\"type\":\"article\",\"category\":\"general\",\"title\":\"literal:New Archive entry\",\"subtitle\":\"\",\"description\":[\"Write your entry here.\"],\"cover_item\":\"minecraft:book\",\"green_tint\":true,\"unlocked_by_default\":true}";
            case ARCHIVE_FOLDER -> "{\"title\":\"literal:New folder\",\"icon_item\":\"minecraft:book\",\"sort_order\":0}";
            case TERMINAL_COMMAND -> "{\"command\":\"new_command\",\"aliases\":[],\"arguments\":[],\"usage\":\"\",\"description\":\"A custom terminal command.\",\"hint\":\"\",\"requires\":{\"disks\":[],\"tasks\":[],\"commands\":[],\"mode\":\"all\",\"directory\":\"\"},\"output\":[\"Command output\"],\"locked_output\":[],\"hidden\":true,\"once\":true,\"clear\":false,\"entries\":[],\"tasks\":[],\"wallpaper\":\"\",\"files\":[],\"programs\":[],\"subscribe\":[],\"rewards\":[],\"open\":\"\",\"action\":\"\",\"variadic\":false}";
        }).getAsJsonObject();
    }

    public static JsonObject fields(AuthoringKind kind) {
        JsonObject result = create(kind);
        JsonObject additions = JsonParser.parseString(switch (kind) {
            case TASK -> "{\"program\":\"\",\"sort_order\":0,\"requires\":[],\"requires_any\":[],\"connects\":[],\"archive_entries\":[],\"hide_until_dependencies_complete\":false,\"invisible_until_completed\":false,\"green_tint\":true,\"icon\":\"minecraft:paper\",\"icon_entity\":\"\",\"render_mob_from_spawn_egg\":true,\"shape\":\"square\",\"size\":1.0,\"icon_scale\":1.0,\"line_axis\":\"auto\",\"line_bend\":0.5,\"content\":[],\"type\":\"task\",\"link_category\":\"\",\"link_group\":\"\"}";
            case MESSAGE -> "{\"style\":\"\",\"sender_avatar_item\":\"\",\"sender_avatar_entity\":\"\",\"delete_after_read\":false,\"blockable\":true,\"notification\":\"\",\"antcoins\":0,\"conditions\":{\"advancements\":[],\"not_advancements\":[],\"mail_read\":[],\"tasks_complete\":[],\"dimension\":\"minecraft:overworld\"},\"deliver_window\":{\"from\":0,\"to\":24000},\"delivery\":{\"items\":[],\"on\":\"read\"},\"wallpaper\":{\"id\":\"antos:grid_ant\",\"unlock\":true},\"takeover\":\"\"}";
            case CATEGORY -> "{\"icon\":\"minecraft:paper\",\"icon_entity\":\"\",\"group\":\"\",\"green_tint\":true,\"render_mob_from_spawn_egg\":true}";
            case GROUP -> "{\"icon\":\"minecraft:paper\",\"icon_entity\":\"\",\"collapsed\":false,\"green_tint\":true,\"render_mob_from_spawn_egg\":true}";
            case DISK -> "{\"encryption\":{\"locked\":true,\"password\":\"password\",\"password_any\":[],\"hint\":\"\",\"case_sensitive\":false}}";
            case DISK_CATEGORY -> "{}";
            case PROGRAM -> "{\"green_tint\":true}";
            case TERMINAL_COMMAND -> "{\"case_sensitive\":false,\"hidden\":true,\"style\":\"output\",\"typing\":false,\"typing_speed\":40,\"variadic\":false}";
            case PRODUCT -> "{\"hidden_until_unlocked\":true,\"notify_on_unlock\":true,\"green_tint\":true,\"render_mob_from_spawn_egg\":true,\"unlock_disks\":[],\"unlock_commands\":[],\"variants\":{\"tag\":\"minecraft:planks\",\"mode\":\"choose\"},\"item_pool\":{\"items\":[],\"tags\":[],\"count\":1,\"mode\":\"all\"},\"delivery_location\":{\"x\":0,\"y\":64,\"z\":0}}";
            case SELL_RULE -> "{\"tags\":[],\"unlock_disks\":[],\"unlock_commands\":[],\"hidden_until_unlocked\":true,\"notify_on_unlock\":true,\"green_tint\":true,\"render_mob_from_spawn_egg\":true}";
            case DEAL_CAMPAIGN -> "{\"every_real_hours\":0,\"max_per_category\":0,\"deal_limit\":0,\"priority\":0}";
            case ARCHIVE_ENTRY -> "{\"folder\":\"\",\"sort_order\":0,\"item\":\"\",\"entity\":\"\",\"enchantment\":\"\",\"recipe\":\"\",\"structure\":\"\",\"structure_tag\":\"\",\"dimension\":\"minecraft:overworld\",\"search_radius\":0,\"locator\":\"\",\"cover_item\":\"minecraft:book\",\"cover_entity\":\"\",\"cover_potion\":\"\",\"rotation\":0,\"render_scale\":1,\"green_tint\":true,\"gallery\":[],\"gallery_title\":\"\",\"content\":[],\"lookup\":[],\"lookup_recipes\":[]}";
            case ARCHIVE_FOLDER -> "{\"parent\":\"\",\"green_tint\":true}";
            default -> "{}";
        }).getAsJsonObject();
        if (kind == AuthoringKind.MESSAGE) additions.add("conditions", new JsonObject());
        additions.entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
        return result;
    }

    public static JsonElement arrayEntry(AuthoringKind kind, String key, int size) {
        JsonElement result = JsonParser.parseString(switch (kind) {
            case PRODUCT -> switch (key) {
                case "payments" -> "{\"type\":\"antcoins\",\"resource\":\"antos:antcoins\",\"amount\":1}";
                case "rewards" -> "{\"item\":\"minecraft:bread\",\"count\":1}";
                default -> "\"\"";
            };
            case SELL_RULE -> "\"\"";
            case DEAL_CAMPAIGN -> "\"\"";
            case ARCHIVE_ENTRY -> switch (key) {
                case "content" -> "{\"text\":\"Write a paragraph here.\"}";
                case "gallery" -> "{\"id\":\"minecraft:book\",\"name\":\"\"}";
                case "description", "lookup", "lookup_recipes" -> "\"guide.example.entry.description\"";
                default -> "\"\"";
            };
            case ARCHIVE_FOLDER -> "\"\"";
            case DISK -> switch (key) {
                case "files" -> "{\"path\":\"readme.txt\",\"source\":\"antos:readme.txt\"}";
                case "entries", "tasks", "programs", "subscribe" -> "\"\"";
                case "rewards" -> "{\"type\":\"item\",\"item\":\"minecraft:bread\",\"count\":1}";
                default -> "\"\"";
            };
            case PROGRAM -> switch (key) {
                case "questions" -> "{\"question\":\"New question\",\"answers\":[\"Answer 1\",\"Answer 2\"],\"correct\":0}";
                case "answers" -> "\"New answer\"";
                case "rewards" -> "{\"type\":\"item\",\"item\":\"minecraft:bread\",\"count\":1}";
                default -> "\"\"";
            };
            case TERMINAL_COMMAND -> switch (key) {
                case "output", "locked_output" -> "{\"text\":\"Command output\",\"style\":\"output\",\"typing\":false,\"typing_speed\":40,\"delay\":0}";
                case "files" -> "{\"path\":\"readme.txt\",\"source\":\"antos:readme.txt\"}";
                case "rewards" -> "{\"type\":\"item\",\"item\":\"minecraft:bread\",\"count\":1}";
                default -> "\"\"";
            };
            default -> switch (key) {
            case "objectives" -> "{\"id\":\"objective_" + (size + 1) + "\",\"type\":\"item\",\"target\":\"minecraft:iron_ingot\",\"count\":1,\"optional\":false,\"sticky\":true,\"consume\":false,\"notify_player\":true}";
            case "rewards" -> "{\"type\":\"item\",\"item\":\"minecraft:bread\",\"count\":1}";
            case "entries" -> "{\"item\":\"minecraft:bread\",\"weight\":1,\"count\":1}";
            case "items" -> "{\"id\":\"minecraft:bread\",\"count\":1}";
            case "options" -> "{\"id\":\"option_" + (size + 1) + "\",\"title\":\"New option\",\"rewards\":[]}";
            default -> "\"\"";
            };
        });
        return result;
    }

    public static JsonObject objectFields(String key) {
        return JsonParser.parseString(switch (key) {
            case "position" -> "{\"x\":0,\"y\":0}";
            case "deliver_window" -> "{\"from\":0,\"to\":24000}";
            case "entries" -> "{\"item\":\"minecraft:bread\",\"pool\":\"\",\"weight\":1,\"count\":1}";
            case "wallpaper" -> "{\"id\":\"antos:grid_ant\",\"force\":false,\"restore\":false,\"unlock\":true}";
            case "items" -> "{\"id\":\"minecraft:bread\",\"count\":1,\"components\":{},\"explorer_map\":\"\",\"decoration\":\"minecraft:red_x\",\"name\":\"\"}";
            case "conditions" -> "{\"advancements\":[],\"not_advancements\":[],\"mail_read\":[],\"tasks_complete\":[],\"dimension\":\"minecraft:overworld\"}";
            case "trigger" -> "{\"type\":\"task_complete\",\"value\":\"\",\"day\":0,\"pool\":\"\",\"weight\":1,\"delay\":0,\"delay_seconds\":0,\"delay_minutes\":0,\"subscription_only\":false}";
            case "availability" -> "{\"server_stock\":0,\"restock_after_minecraft_days\":0,\"restock_after_real_hours\":0,\"cooldown_minecraft_days\":0,\"player_limit\":0,\"player_limit_reset\":\"none\"}";
            case "thumbnail" -> "{\"item\":\"minecraft:chest\",\"entity\":\"\"}";
            case "gallery" -> "{\"id\":\"minecraft:book\",\"item\":\"\",\"entity\":\"\",\"name\":\"\"}";
            case "item_pool" -> "{\"items\":[],\"tags\":[],\"count\":1,\"mode\":\"all\"}";
            case "variants" -> "{\"tag\":\"minecraft:planks\",\"mode\":\"choose\"}";
            case "delivery_location" -> "{\"x\":0,\"y\":64,\"z\":0}";
            case "candidates" -> "{\"products\":[],\"categories\":[],\"tags\":[],\"exclude_products\":[],\"exclude_categories\":[],\"exclude_tags\":[],\"min_price\":0,\"max_price\":0}";
            case "discount_percent" -> "{\"min\":10,\"max\":20}";
            case "content" -> "{\"text\":\"Write a paragraph here.\",\"display\":\"examplemod:workshop_variants\",\"recipe\":\"minecraft:stone_pickaxe\",\"beside\":false}";
            case "files" -> "{\"path\":\"readme.txt\",\"source\":\"antos:readme.txt\"}";
            case "questions" -> "{\"question\":\"New question\",\"answers\":[\"Answer 1\",\"Answer 2\"],\"correct\":0,\"item\":\"\",\"entity\":\"\",\"green_tint\":true,\"explanation\":\"\"}";
            case "requires" -> "{\"disks\":[],\"tasks\":[],\"commands\":[],\"mode\":\"all\",\"directory\":\"\"}";
            case "encryption" -> "{\"locked\":true,\"password\":\"password\",\"password_any\":[],\"hint\":\"\",\"case_sensitive\":false}";
            case "output", "locked_output" -> "{\"text\":\"Command output\",\"style\":\"output\",\"typing\":false,\"typing_speed\":40,\"delay\":0}";
            case "choices" -> "{\"text\":\"Continue\",\"goto\":\"ending\",\"requires_flag\":\"\",\"requires_no_flag\":\"\"}";
            case "objectives" -> "{\"id\":\"\",\"type\":\"item\",\"target\":\"\",\"count\":1,\"description\":\"\",\"stat_type\":\"custom\",\"optional\":false,\"sticky\":true,\"tag_mode\":\"any\",\"consume\":false,\"notify_player\":true,\"target_category\":\"\",\"ending\":\"\"}";
            case "rewards" -> "{\"type\":\"item\",\"item\":\"minecraft:bread\",\"count\":1,\"points\":1,\"levels\":1,\"amount\":1,\"pool\":\"\",\"mode\":\"random\",\"options\":[],\"id\":\"antos:grid_ant\",\"force\":false,\"restore\":false,\"unlock\":true,\"message\":\"\",\"entry\":\"\",\"advancement\":\"\",\"effect\":\"minecraft:speed\",\"duration_ticks\":200,\"amplifier\":0,\"sender\":\"fieldoffice\",\"subject\":\"Task completed\",\"body\":\"\"}";
            default -> "{}";
        }).getAsJsonObject();
    }

    public static void selectType(JsonObject object, String context) {
        String type = object.has("type") ? object.get("type").getAsString() : "";
        JsonObject defaults = objectFields(context);
        java.util.Set<String> required = switch (context) {
            case "rewards" -> switch (type) {
                case "item" -> java.util.Set.of("item", "count");
                case "experience" -> java.util.Set.of("points");
                case "experience_levels" -> java.util.Set.of("levels");
                case "antcoins" -> java.util.Set.of("amount");
                case "item_pool" -> java.util.Set.of("pool", "mode");
                case "choice" -> java.util.Set.of("options");
                case "archive" -> java.util.Set.of("entry");
                case "advancement" -> java.util.Set.of("advancement");
                case "effect" -> java.util.Set.of("effect", "duration_ticks", "amplifier");
                case "mail" -> java.util.Set.of("sender", "subject", "body");
                case "wallpaper" -> java.util.Set.of("id", "unlock");
                default -> java.util.Set.of();
            };
            case "objectives" -> type.equals("stat") ? java.util.Set.of("stat_type", "target", "count") : java.util.Set.of("target", "count");
            case "trigger" -> java.util.Set.of("value");
            default -> java.util.Set.of();
        };
        for (String key : required) if (!object.has(key) && defaults.has(key)) object.add(key, defaults.get(key).deepCopy());
        if (context.equals("trigger") && type.equals("random")) object.addProperty("value", "0.05");
    }
}
