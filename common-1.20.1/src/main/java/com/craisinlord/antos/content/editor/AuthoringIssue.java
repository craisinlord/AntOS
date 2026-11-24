package com.craisinlord.antos.content.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public record AuthoringIssue(String code, String field, boolean warning, List<String> arguments) {
    public AuthoringIssue {
        arguments = List.copyOf(arguments);
    }

    public static AuthoringIssue error(String code, String field, String... arguments) {
        return new AuthoringIssue(code, field, false, List.of(arguments));
    }

    public static AuthoringIssue warning(String code, String field, String... arguments) {
        return new AuthoringIssue(code, field, true, List.of(arguments));
    }

    public JsonObject encode() {
        JsonObject object = new JsonObject();
        object.addProperty("code", code);
        object.addProperty("field", field);
        object.addProperty("warning", warning);
        JsonArray values = new JsonArray();
        arguments.forEach(values::add);
        object.add("args", values);
        return object;
    }

    public static AuthoringIssue decode(JsonObject object) {
        List<String> arguments = new ArrayList<>();
        if (object.has("args")) object.getAsJsonArray("args").forEach(value -> arguments.add(value.getAsString()));
        return new AuthoringIssue(object.get("code").getAsString(), object.get("field").getAsString(),
                object.has("warning") && object.get("warning").getAsBoolean(), arguments);
    }
}
