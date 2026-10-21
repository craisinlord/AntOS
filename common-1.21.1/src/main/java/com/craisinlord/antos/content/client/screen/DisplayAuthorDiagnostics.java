package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.guide.DisplayFeatures;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import static com.craisinlord.antos.content.guide.CustomDisplays.*;

final class DisplayAuthorDiagnostics {
    private DisplayAuthorDiagnostics() { }

    static List<DisplayFeatures.Diagnostic> inspect(String source, JsonObject document, int viewportWidth) {
        List<DisplayFeatures.Diagnostic> result = new ArrayList<>();
        inspect(source, document, "elements", viewportWidth / number(document, "width", 320), result, 0);
        return List.copyOf(result);
    }

    private static void inspect(String source, JsonObject document, String path, double scale, List<DisplayFeatures.Diagnostic> report, int depth) {
        if (depth > 16 || report.size() >= 128) return;
        List<DisplayFeatures.Bounds> interactive = new ArrayList<>();
        if (document.has("elements")) {
            int index = 0;
            for (JsonElement value : document.getAsJsonArray("elements")) {
                JsonObject e = value.getAsJsonObject(); String field = path + "[" + index++ + "]", type = string(e, "type", "");
                if (report.size() >= 128) break;
                if (type.equals("image")) {
                    ResourceLocation texture = ResourceLocation.tryParse(string(e, "texture", ""));
                    if (texture == null || Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) report.add(new DisplayFeatures.Diagnostic("warning", source, field + ".texture", "Texture is missing from this client's active resource packs"));
                }
                if (type.equals("text")) {
                    String text = string(e, "text", "");
                    if (!text.startsWith("literal:") && !text.contains(" ") && text.contains(".") && !net.minecraft.locale.Language.getInstance().has(text)) report.add(new DisplayFeatures.Diagnostic("warning", source, field + ".text", "Possible missing translation in this client's active language"));
                    if (scale * Minecraft.getInstance().font.lineHeight < 6) report.add(new DisplayFeatures.Diagnostic("warning", source, field, "Text is small at this viewport; provide a compact layout or use the expanded viewer"));
                    String translated = text.startsWith("literal:") ? text.substring(8) : Component.translatable(text).getString();
                    int rows = Minecraft.getInstance().font.split(Component.literal(translated), Math.max(1, (int) number(e, "width", 100))).size();
                    if (e.has("height") && rows * Minecraft.getInstance().font.lineHeight > number(e, "height", 11)) report.add(new DisplayFeatures.Diagnostic("warning", source, field + ".height", "Translated text exceeds its authored height in this language"));
                }
                if (type.equals("slot") || e.has("archive") || e.has("task")) {
                    var b = DisplayFeatures.bounds(e);
                    if (interactive.stream().anyMatch(other -> b.x() < other.x() + other.width() && b.x() + b.width() > other.x() && b.y() < other.y() + other.height() && b.y() + b.height() > other.y())) report.add(new DisplayFeatures.Diagnostic("warning", source, field, "Interactive regions overlap; later targets take input priority"));
                    interactive.add(b);
                }
                if (e.has("elements")) inspect(source, e, field + ".elements", scale, report, depth + 1);
            }
        }
        if (document.has("layouts")) for (JsonElement value : document.getAsJsonArray("layouts")) inspect(source, value.getAsJsonObject(), "layouts." + string(value.getAsJsonObject(), "id", ""), scale, report, depth + 1);
        if (document.has("variants")) for (JsonElement value : document.getAsJsonArray("variants")) if (value.getAsJsonObject().has("document")) inspect(source, value.getAsJsonObject().getAsJsonObject("document"), "variants." + string(value.getAsJsonObject(), "id", ""), scale, report, depth + 1);
    }
}
