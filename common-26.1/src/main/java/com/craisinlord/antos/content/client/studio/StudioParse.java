package com.craisinlord.antos.content.client.studio;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

final class StudioParse {
    record Amount(String verb, int count, boolean entity, String id, String name) {
        JsonObject goal() {
            String wanted = verb.isEmpty() ? entity ? "kill" : "collect" : verb;
            for (StudioEditors.GoalType type : StudioEditors.GOALS) if (type.id().equals(wanted)) {
                JsonObject goal = StudioEditors.newGoal(type);
                goal.addProperty("target", id);
                goal.addProperty("count", Math.max(1, Math.min(1_000_000, count)));
                return goal;
            }
            throw new IllegalStateException(wanted);
        }

        JsonObject reward() {
            JsonObject reward = StudioEditors.newReward("item");
            reward.addProperty("item", id);
            reward.addProperty("count", Math.max(1, Math.min(108, count)));
            return reward;
        }
    }

    private static Map<String, String> items;
    private static Map<String, String> entities;
    private static Object language;

    private StudioParse() { }

    private static void index() {
        Object current = net.minecraft.locale.Language.getInstance();
        if (items != null && language == current) return;
        items = new HashMap<>();
        entities = new HashMap<>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            items.put(id, id);
            items.putIfAbsent(plain(id.substring(id.indexOf(':') + 1)), id);
            items.putIfAbsent(plain(StudioIcon.stack(id).getHoverName().getString()), id);
        }
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            entities.put(id, id);
            entities.putIfAbsent(plain(id.substring(id.indexOf(':') + 1)), id);
            entities.putIfAbsent(plain(type.getDescription().getString()), id);
        }
        language = current;
    }

    private static String plain(String value) { return value.toLowerCase(Locale.ROOT).replace('_', ' ').trim(); }

    private static String find(Map<String, String> source, String name) {
        String value = source.get(name);
        if (value != null) return value;
        for (String ending : new String[]{"es", "s"}) if (name.endsWith(ending) && name.length() > ending.length() + 2) {
            value = source.get(name.substring(0, name.length() - ending.length()));
            if (value != null) return value;
        }
        if (name.contains(" of ")) return source.get(name.replace(" of ", " "));
        return name.endsWith("ies") ? source.get(name.substring(0, name.length() - 3) + "y") : null;
    }

    static Amount amount(String text) {
        index();
        String value = text.trim();
        String verb = "";
        String[] verbs = {"collect", "gather", "get", "craft", "make", "mine", "break", "kill", "defeat", "use", "bring"};
        String lower = value.toLowerCase(Locale.ROOT);
        for (String word : verbs) if (lower.startsWith(word + " ")) {
            verb = switch (word) {
                case "craft", "make" -> "craft";
                case "mine", "break" -> "mine";
                case "kill", "defeat" -> "kill";
                case "use" -> "use";
                default -> "collect";
            };
            value = value.substring(word.length() + 1).trim();
            break;
        }
        int count = 1;
        java.util.regex.Matcher front = java.util.regex.Pattern.compile("^(\\d{1,7})\\s*[xX×]?\\s+(.+)$").matcher(value);
        java.util.regex.Matcher back = java.util.regex.Pattern.compile("^(.+?)\\s+[xX×]?\\s*(\\d{1,7})$").matcher(value);
        boolean numbered = false;
        if (front.matches()) {
            count = Integer.parseInt(front.group(1));
            value = front.group(2);
            numbered = true;
        } else if (back.matches()) {
            count = Integer.parseInt(back.group(2));
            value = back.group(1);
            numbered = true;
        }
        for (String filler : new String[]{"a ", "an ", "the ", "some "}) if (value.toLowerCase(Locale.ROOT).startsWith(filler)) value = value.substring(filler.length());
        String name = plain(value);
        if (name.isEmpty()) return null;
        String item = verb.equals("kill") ? null : find(items, value.contains(":") ? value.toLowerCase(Locale.ROOT) : name);
        if (item != null) return new Amount(verb, count, false, item, StudioText.itemName(item));
        String entity = verb.isEmpty() && !numbered || verb.equals("kill") || verb.isEmpty() ? find(entities, value.contains(":") ? value.toLowerCase(Locale.ROOT) : name) : null;
        if (entity != null) return new Amount("kill", count, true, entity, StudioText.entityName(entity));
        return null;
    }

    static JsonObject reward(String text) {
        String value = text.trim();
        java.util.regex.Matcher number = java.util.regex.Pattern.compile("^(\\d{1,9})\\s*(\\S+)(\\s+levels?)?$").matcher(value);
        if (number.matches()) {
            String unit = number.group(2).toLowerCase(Locale.ROOT);
            int count = Integer.parseInt(number.group(1));
            String currency = com.craisinlord.antos.content.client.AntOSPlayerText.currencyName().toLowerCase(Locale.ROOT);
            if (unit.equals("antcoins") || unit.equals("antcoin") || unit.equals("coins") || unit.equals(currency)) {
                JsonObject reward = StudioEditors.newReward("antcoins");
                reward.addProperty("amount", Math.max(1, count));
                return reward;
            }
            if (unit.equals("xp") || unit.equals("experience")) {
                JsonObject reward = StudioEditors.newReward(number.group(3) == null ? "experience" : "experience_levels");
                reward.addProperty(number.group(3) == null ? "points" : "levels", Math.max(1, Math.min(number.group(3) == null ? 1_000_000 : 1000, count)));
                return reward;
            }
            if (unit.equals("levels") || unit.equals("level")) {
                JsonObject reward = StudioEditors.newReward("experience_levels");
                reward.addProperty("levels", Math.max(1, Math.min(1000, count)));
                return reward;
            }
        }
        Amount amount = amount(value);
        return amount == null || amount.entity() ? null : amount.reward();
    }
}
