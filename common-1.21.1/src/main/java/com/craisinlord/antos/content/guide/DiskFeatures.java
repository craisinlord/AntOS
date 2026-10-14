package com.craisinlord.antos.content.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public record DiskFeatures(List<File> files, List<ResourceLocation> programs, List<ResourceLocation> subscriptions, Encryption encryption, boolean consumable) {
    public static final DiskFeatures EMPTY = new DiskFeatures(List.of(), List.of(), List.of(), Encryption.NONE, false);

    public DiskFeatures(List<File> files, List<ResourceLocation> programs, List<ResourceLocation> subscriptions, Encryption encryption) {
        this(files, programs, subscriptions, encryption, false);
    }

    public static DiskFeatures parse(JsonObject object) {
        List<File> files = new ArrayList<>();
        if (object.has("files")) for (JsonElement element : object.getAsJsonArray("files")) {
            if (files.size() >= 32) break;
            JsonObject file = element.getAsJsonObject();
            String path = file.get("path").getAsString();
            if (path.isBlank() || path.length() > 64) throw new IllegalArgumentException("Invalid disk file path");
            files.add(new File(path, ResourceLocation.parse(file.get("source").getAsString())));
        }
        Encryption encryption = Encryption.NONE;
        if (object.has("encryption")) {
            JsonObject block = object.getAsJsonObject("encryption");
            List<String> passwords = new ArrayList<>();
            if (block.has("password")) passwords.add(block.get("password").getAsString());
            if (block.has("password_any")) for (JsonElement value : block.getAsJsonArray("password_any")) passwords.add(value.getAsString());
            if (passwords.size() > 8 || passwords.stream().anyMatch(password -> password.isEmpty() || password.length() > 128)
                    || passwords.isEmpty() && !block.has("locked")) throw new IllegalArgumentException("Invalid disk encryption passwords");
            encryption = new Encryption(true, List.copyOf(passwords), block.has("hint") ? block.get("hint").getAsString() : "",
                    block.has("case_sensitive") && block.get("case_sensitive").getAsBoolean());
        }
        boolean consumable = object.has("consumable") && object.get("consumable").isJsonPrimitive()
                && object.get("consumable").getAsJsonPrimitive().isBoolean() && object.get("consumable").getAsBoolean();
        return new DiskFeatures(List.copyOf(files), ids(object, "programs"), ids(object, "subscribe"), encryption, consumable);
    }

    private static List<ResourceLocation> ids(JsonObject object, String key) {
        if (!object.has(key)) return List.of();
        List<ResourceLocation> result = new ArrayList<>();
        for (JsonElement value : object.getAsJsonArray(key)) {
            if (result.size() >= 64) throw new IllegalArgumentException("Too many disk references");
            result.add(ResourceLocation.parse(value.getAsString()));
        }
        return result.stream().distinct().toList();
    }

    public void encodeClient(JsonObject object) {
        JsonArray filesJson = new JsonArray();
        for (File file : files) {
            JsonObject row = new JsonObject();
            row.addProperty("path", file.path());
            row.addProperty("source", file.source().toString());
            filesJson.add(row);
        }
        object.add("files", filesJson);
        JsonArray programsJson = new JsonArray();
        programs.forEach(id -> programsJson.add(id.toString()));
        object.add("programs", programsJson);
        JsonArray subscriptionsJson = new JsonArray();
        subscriptions.forEach(id -> subscriptionsJson.add(id.toString()));
        object.add("subscribe", subscriptionsJson);
        if (consumable) object.addProperty("consumable", true);
        if (encryption.enabled()) {
            JsonObject block = new JsonObject();
            block.addProperty("locked", true);
            block.addProperty("hint", encryption.hint());
            block.addProperty("case_sensitive", encryption.caseSensitive());
            object.add("encryption", block);
        }
    }

    public record File(String path, ResourceLocation source) { }

    public record Encryption(boolean enabled, List<String> passwords, String hint, boolean caseSensitive) {
        public static final Encryption NONE = new Encryption(false, List.of(), "", false);

        public boolean accepts(String guess) {
            if (guess == null || guess.length() > 128) return false;
            String normalized = caseSensitive ? guess : guess.toLowerCase(Locale.ROOT);
            boolean matched = false;
            for (String password : passwords) {
                String expected = caseSensitive ? password : password.toLowerCase(Locale.ROOT);
                matched |= MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), normalized.getBytes(StandardCharsets.UTF_8));
            }
            return matched;
        }
    }
}
