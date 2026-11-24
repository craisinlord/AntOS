package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class StudioText {
    private static final Set<AuthoringKind> LITERAL = Set.of(AuthoringKind.DISK, AuthoringKind.DISK_CATEGORY, AuthoringKind.ARCHIVE_ENTRY, AuthoringKind.ARCHIVE_FOLDER);

    private StudioText() { }

    static String t(String key, Object... arguments) { return Component.translatable("antos.studio." + key, arguments).getString(); }
    static boolean has(String key) { return I18n.exists("antos.studio." + key); }

    static String kind(AuthoringKind kind) { return t("kind." + kind.key()); }

    static String read(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.startsWith("literal:")) return value.substring(8);
        return I18n.exists(value) ? Component.translatable(value).getString() : value;
    }

    static boolean translated(String value) { return value != null && !value.startsWith("literal:") && I18n.exists(value); }

    static String write(AuthoringKind kind, String key, String previous, String text) {
        boolean literal = previous.startsWith("literal:") || LITERAL.contains(kind) && Set.of("title", "description", "subtitle").contains(key);
        return literal && !text.isEmpty() ? "literal:" + text : text;
    }

    static String titleKey(AuthoringKind kind) {
        return switch (kind) {
            case MESSAGE -> "subject";
            case PRODUCT -> "name";
            case DEAL_CAMPAIGN -> "label";
            case TERMINAL_COMMAND -> "command";
            case REWARD_POOL, MAIL_POOL, SELL_RULE -> "";
            default -> "title";
        };
    }

    static String label(StudioKey key, JsonObject body) {
        String field = titleKey(key.kind());
        String value = body == null || field.isEmpty() ? "" : read(StudioJson.string(body, field));
        if (!value.isBlank()) return value;
        if (key.kind() == AuthoringKind.SELL_RULE && body != null) {
            String first = StudioJson.string(body, "item");
            if (first.isEmpty() && !StudioJson.list(body, "items").isEmpty()) first = StudioJson.list(body, "items").get(0).getAsString();
            if (first.isEmpty() && !StudioJson.list(body, "tags").isEmpty()) first = "#" + StudioJson.list(body, "tags").get(0).getAsString();
            if (!first.isEmpty()) return first.startsWith("#") ? tag(first) : itemName(first);
        }
        return pretty(key.path());
    }

    static String pretty(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ').replace('-', ' ').trim();
        if (last.isEmpty()) return path;
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    static String prettyId(String id) {
        String value = id.startsWith("#") ? id.substring(1) : id;
        return pretty(value.substring(value.indexOf(':') + 1));
    }

    static String tag(String id) { return t("any_tag", prettyId(id)); }

    static String itemName(String id) {
        if (id == null || id.isBlank()) return t("pick_item");
        var stack = StudioIcon.stack(id);
        return stack.isEmpty() ? prettyId(id) : stack.getHoverName().getString();
    }

    static String entityName(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        var type = parsed == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(parsed).orElse(null);
        return type == null ? prettyId(id) : type.getDescription().getString();
    }

    static String blockName(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        var block = parsed == null ? null : BuiltInRegistries.BLOCK.getOptional(parsed).orElse(null);
        return block == null ? prettyId(id) : block.getName().getString();
    }

    static String effectName(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        var effect = parsed == null ? null : BuiltInRegistries.MOB_EFFECT.getOptional(parsed).orElse(null);
        return effect == null ? prettyId(id) : effect.getDisplayName().getString();
    }

    static String gameName(String mode) {
        String key = "game." + mode.substring(mode.indexOf(':') + 1).replace('/', '.');
        if (mode.startsWith("antos:") && has(key)) return t(key);
        if (mode.startsWith("antos:piston_push/")) return t("game.piston_level", mode.substring("antos:piston_push/".length()));
        return prettyId(mode);
    }

    static String slug(String title) {
        String value = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return value.length() > 48 ? value.substring(0, 48).replaceAll("_+$", "") : value;
    }

    static String field(String key) { return has("field." + key) ? t("field." + key) : pretty(key); }
}
