package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonObject;

record StudioKey(AuthoringKind kind, String id) {
    JsonObject encode() {
        JsonObject object = new JsonObject();
        object.addProperty("kind", kind.key());
        object.addProperty("id", id);
        return object;
    }

    static StudioKey decode(JsonObject object) {
        return new StudioKey(AuthoringKind.parse(object.get("kind").getAsString()), object.get("id").getAsString());
    }

    String namespace() {
        int separator = id.indexOf(':');
        return separator < 0 ? "minecraft" : id.substring(0, separator);
    }

    String path() { return id.substring(id.indexOf(':') + 1); }

    @Override
    public String toString() { return kind.key() + "/" + id; }
}
