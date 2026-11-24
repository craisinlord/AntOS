package com.craisinlord.antos.content.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AuthoringCoreTest {
    private static int checks;
    private AuthoringCoreTest() { }

    public static void main(String[] args) throws Exception {
        transferTests();
        documentTests();
        fileTests();
        performance();
        System.out.println("Authoring core: " + checks + " checks passed");
    }

    private static void transferTests() {
        for (String source : List.of("", "Hello", "x".repeat(AuthoringWire.CHUNK_CHARS + 1), "\u2603".repeat(AuthoringWire.MAX_CHARS))) {
            List<AuthoringWire.Chunk> chunks = AuthoringWire.split("test", source);
            AuthoringWire.Assembly assembly = new AuthoringWire.Assembly(chunks.getFirst(), 0);
            String result = null;
            for (AuthoringWire.Chunk chunk : chunks) result = assembly.accept(chunk, 1);
            check(source.equals(result), "Transfer must preserve every character");
        }
        rejects(() -> AuthoringWire.split("test", "x".repeat(AuthoringWire.MAX_CHARS + 1)), "Oversized transfer");
        rejects(() -> new AuthoringWire.Chunk("test", 0, AuthoringWire.MAX_CHUNKS + 1, ""), "Oversized packet count");
        rejects(() -> new AuthoringWire.Chunk("test", 0, 1, "x".repeat(AuthoringWire.CHUNK_CHARS + 1)), "Oversized packet");
        var first = new AuthoringWire.Chunk("test", 0, 2, "a");
        var next = new AuthoringWire.Chunk("test", 1, 2, "b");
        rejects(() -> new AuthoringWire.Assembly(next, 0), "Out-of-order start");
        var assembly = new AuthoringWire.Assembly(first, 0);
        check(assembly.accept(first, 1) == null, "Partial transfer must not be processed");
        rejects(() -> assembly.accept(first, 2), "Duplicate packet");
        rejects(() -> assembly.accept(new AuthoringWire.Chunk("other", 1, 2, "b"), 2), "Mixed request IDs");
        rejects(() -> assembly.accept(next, 15_001), "Expired transfer");
        String emojiBoundary = "a".repeat(AuthoringWire.CHUNK_CHARS - 1) + "\ud83d\udc1c";
        List<AuthoringWire.Chunk> emojiChunks = AuthoringWire.split("emoji", emojiBoundary);
        check(!Character.isHighSurrogate(emojiChunks.getFirst().data().charAt(emojiChunks.getFirst().data().length() - 1)), "Packets must not split a Unicode surrogate pair");
    }

    private static void documentTests() {
        JsonObject task = AuthoringTemplates.create(AuthoringKind.TASK);
        JsonObject extension = new JsonObject();
        extension.addProperty("arbitrary", "preserved");
        task.add("third_party_field", extension);
        task.addProperty("title", "Changed title");
        JsonObject roundTrip = AuthoringValidation.parse(task.toString());
        AuthoringValidation.validate(AuthoringKind.TASK, roundTrip);
        check(roundTrip.getAsJsonObject("third_party_field").get("arbitrary").getAsString().equals("preserved"), "Unknown nested fields must survive");
        for (AuthoringKind kind : AuthoringKind.values()) AuthoringValidation.validate(kind, AuthoringTemplates.create(kind));
        check(true, "Every creation template must pass structural validation");
        JsonObject objective = AuthoringTemplates.arrayEntry(AuthoringKind.TASK, "objectives", 0).getAsJsonObject();
        JsonArray objectives = new JsonArray();
        objectives.add(objective); objectives.add(objective.deepCopy());
        task.add("objectives", objectives);
        rejects(() -> AuthoringValidation.validate(AuthoringKind.TASK, task), "Duplicate objective IDs");
        task.add("objectives", new JsonArray());
        task.getAsJsonObject("position").addProperty("x", 129);
        rejects(() -> AuthoringValidation.validate(AuthoringKind.TASK, task), "Coordinates outside runtime bounds");
        task.getAsJsonObject("position").addProperty("x", 0);
        task.getAsJsonObject("position").addProperty("y", 0.5);
        rejects(() -> AuthoringValidation.validate(AuthoringKind.TASK, task), "Fractional grid coordinates");
        task.getAsJsonObject("position").addProperty("y", 0);
        JsonObject reward = AuthoringTemplates.arrayEntry(AuthoringKind.TASK, "rewards", 0).getAsJsonObject();
        reward.addProperty("count", 0.5);
        task.getAsJsonArray("rewards").add(reward);
        rejects(() -> AuthoringValidation.validate(AuthoringKind.TASK, task), "Fractional item rewards");
        task.add("rewards", new JsonArray());
        task.addProperty("size", 4);
        rejects(() -> AuthoringValidation.validate(AuthoringKind.TASK, task), "Appearance outside runtime bounds");
        JsonObject mail = AuthoringTemplates.create(AuthoringKind.MESSAGE);
        mail.addProperty("body", "a".repeat(4097));
        rejects(() -> AuthoringValidation.validate(AuthoringKind.MESSAGE, mail), "Oversized mail body");
        mail.addProperty("body", "");
        JsonObject delivery = new JsonObject(); delivery.addProperty("on", "on_read"); mail.add("delivery", delivery);
        rejects(() -> AuthoringValidation.validate(AuthoringKind.MESSAGE, mail), "Unsupported delivery timing");
        for (String type : List.of("experience", "experience_levels", "antcoins", "effect", "mail", "wallpaper")) {
            JsonObject selected = new JsonObject(); selected.addProperty("type", type); AuthoringTemplates.selectType(selected, "rewards");
            check(selected.size() > 1, "Changing reward type must expose its required fields");
        }
        rejects(() -> AuthoringValidation.parse("{\"nested\":".repeat(33) + "0" + "}".repeat(33)), "Excessive JSON depth");
        rejects(() -> AuthoringValidation.parse("[]"), "Non-object root");
        rejects(() -> AuthoringValidation.parse("{]"), "Mismatched JSON delimiters");
        rejects(() -> AuthoringValidation.parse("{\"value\":\"unfinished}"), "Unclosed JSON string");
        rejects(() -> AuthoringValidation.parse("}"), "Unmatched JSON close delimiter");
        check(AuthoringValidation.parse("{\"body\":\"escaped \\\" brackets [ {\"}").has("body"), "Quoted brackets must not count as nesting");
        check(AuthoringKind.TASK.includes("computer/task/research.json"), "Task discovery");
        check(!AuthoringKind.TASK.includes("computer/task/category/research.json"), "Categories must not be discovered as tasks");
        check(!AuthoringKind.TASK.includes("computer/task/reward_pool/research.json"), "Reward pools must not be discovered as tasks");
        check(AuthoringKind.PRODUCT.includes("antazon/product/food/bread.json"), "Antazon product discovery");
        check(AuthoringKind.SELL_RULE.includes("antazon/sell/food.json"), "Antazon sell rule discovery");
        check(AuthoringKind.DEAL_CAMPAIGN.includes("antazon/deal/weekly.json"), "Antazon deal campaign discovery");
        check(AuthoringKind.ARCHIVE_ENTRY.includes("computer/entry/field_notes.json"), "Archive article discovery");
        check(AuthoringKind.ARCHIVE_FOLDER.includes("computer/archive_folder/research.json"), "Archive folder discovery");
        JsonObject product = AuthoringTemplates.create(AuthoringKind.PRODUCT);
        AuthoringValidation.validate(AuthoringKind.PRODUCT, product);
        product.getAsJsonArray("payments").get(0).getAsJsonObject().addProperty("amount", 0);
        rejects(() -> AuthoringValidation.validate(AuthoringKind.PRODUCT, product), "Zero-value product payment");
        JsonObject sellRule = AuthoringTemplates.create(AuthoringKind.SELL_RULE);
        AuthoringValidation.validate(AuthoringKind.SELL_RULE, sellRule);
        sellRule.remove("value");
        rejects(() -> AuthoringValidation.validate(AuthoringKind.SELL_RULE, sellRule), "Sell rules without a value");
        JsonObject campaign = AuthoringTemplates.create(AuthoringKind.DEAL_CAMPAIGN);
        AuthoringValidation.validate(AuthoringKind.DEAL_CAMPAIGN, campaign);
        campaign.getAsJsonObject("discount_percent").addProperty("max", 91);
        rejects(() -> AuthoringValidation.validate(AuthoringKind.DEAL_CAMPAIGN, campaign), "Discounts above the runtime limit");
        check(AuthoringTemplates.arrayEntry(AuthoringKind.PRODUCT, "payments", 1).getAsJsonObject().has("resource"), "Product payment template");
        check(AuthoringTemplates.arrayEntry(AuthoringKind.ARCHIVE_ENTRY, "content", 1).getAsJsonObject().has("text"), "Archive article block template");
        JsonObject article = AuthoringTemplates.create(AuthoringKind.ARCHIVE_ENTRY);
        AuthoringValidation.validate(AuthoringKind.ARCHIVE_ENTRY, article);
        article.addProperty("title", "");
        rejects(() -> AuthoringValidation.validate(AuthoringKind.ARCHIVE_ENTRY, article), "Archive entries without a title");
        JsonObject folder = AuthoringTemplates.create(AuthoringKind.ARCHIVE_FOLDER);
        AuthoringValidation.validate(AuthoringKind.ARCHIVE_FOLDER, folder);
    }

    private static void fileTests() throws Exception {
        Path root = Files.createTempDirectory("antos-authoring-test-");
        try {
            for (String path : List.of("../escape", "safe/../escape", "/absolute", "a//b", ".", "a/./b", "trailing/", ""))
                rejects(() -> AuthoringFiles.document(root, AuthoringKind.TASK, "pack", path), "Unsafe path " + path);
            rejects(() -> AuthoringFiles.document(root, AuthoringKind.TASK, "../outside", "task"), "Unsafe namespace");
            rejects(() -> AuthoringFiles.document(root, AuthoringKind.TASK, "..", "task"), "Parent namespace");
            rejects(() -> AuthoringFiles.document(root, AuthoringKind.TASK, "pack", "category/task"), "Task/category directory collision");
            AuthoringFiles.save(root, AuthoringKind.TASK, "pack", "nested/task", "first\n");
            Path file = AuthoringFiles.document(root, AuthoringKind.TASK, "pack", "nested/task");
            check(Files.readString(file).equals("first\n"), "Saved file contents");
            AuthoringFiles.save(root, AuthoringKind.TASK, "pack", "nested/task", "second\n");
            check(Files.readString(file).equals("second\n"), "Replacement contents");
            check(Files.readString(file.resolveSibling("task.json.previous")).equals("first\n"), "Recoverable previous version");
            JsonObject metadata = AuthoringValidation.parse(Files.readString(root.resolve(AuthoringFiles.PACK_NAME).resolve("pack.mcmeta")));
            check(metadata.getAsJsonObject("pack").get("pack_format").getAsInt() == 48, "Minecraft 1.21.1 pack format");
            try (var files = Files.walk(root)) { check(files.noneMatch(value -> value.getFileName().toString().endsWith(".tmp")), "Temporary writes must be cleaned up"); }
            check(!AuthoringFiles.hash("first").equals(AuthoringFiles.hash("second")), "External changes must produce distinct revisions");
        } finally {
            try (var files = Files.walk(root)) { for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }

    private static void performance() {
        String sample = AuthoringTemplates.fields(AuthoringKind.TASK).toString();
        long start = System.nanoTime();
        for (int index = 0; index < 2048; index++) AuthoringFiles.hash(AuthoringValidation.parse(sample).toString());
        System.out.println("2048 source-document parse/hash operations: " + ((System.nanoTime() - start) / 1_000_000) + " ms");
    }

    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); checks++; }
    private static void rejects(ThrowingRunnable action, String message) {
        try { action.run(); } catch (Exception expected) { checks++; return; }
        throw new AssertionError(message + " was accepted");
    }
    private interface ThrowingRunnable { void run() throws Exception; }
}
