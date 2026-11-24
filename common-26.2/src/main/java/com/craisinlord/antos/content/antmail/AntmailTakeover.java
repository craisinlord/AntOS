package com.craisinlord.antos.content.antmail;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record AntmailTakeover(Identifier id, String title, List<String> lines, String style, String button) {
    public static final int MAX_LINES = 32;
    public static final int MAX_LINE_LENGTH = 512;

    public boolean hijacked() {
        return !style.equals("corrupted");
    }

    static AntmailTakeover parse(Identifier id, JsonObject object) {
        List<String> lines = new ArrayList<>();
        JsonElement rows = object.get("lines");
        if (rows != null && rows.isJsonArray()) {
            for (JsonElement row : rows.getAsJsonArray()) {
                if (!row.isJsonPrimitive() || lines.size() >= MAX_LINES) continue;
                String line = row.getAsString();
                lines.add(line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) : line);
            }
        } else if (rows != null && rows.isJsonPrimitive()) {
            lines.add(rows.getAsString());
        }
        String style = string(object, "style").toLowerCase(Locale.ROOT);
        if (!style.equals("corrupted")) style = "hijacked";
        String title = string(object, "title");
        if (title.isBlank() && lines.isEmpty()) throw new IllegalArgumentException("a takeover needs a title or lines");
        return new AntmailTakeover(id, title, List.copyOf(lines), style, string(object, "button"));
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id.toString());
        tag.putString("Title", title);
        ListTag list = new ListTag();
        for (String line : lines) list.add(StringTag.valueOf(line));
        tag.put("Lines", list);
        tag.putString("Style", style);
        tag.putString("Button", button);
        return tag;
    }

    public static AntmailTakeover fromTag(CompoundTag tag) {
        Identifier id = Identifier.tryParse(tag.getStringOr("Id", ""));
        if (id == null) return null;
        List<String> lines = new ArrayList<>();
        ListTag list = tag.getListOrEmpty("Lines");
        for (int index = 0; index < list.size() && index < MAX_LINES; index++) lines.add(list.getStringOr(index, ""));
        String style = tag.getStringOr("Style", "").equals("corrupted") ? "corrupted" : "hijacked";
        return new AntmailTakeover(id, tag.getStringOr("Title", ""), List.copyOf(lines), style, tag.getStringOr("Button", ""));
    }
}
