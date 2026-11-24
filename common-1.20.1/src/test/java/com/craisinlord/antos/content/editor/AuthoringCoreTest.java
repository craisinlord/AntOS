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
        linkTests();
        fileTests();
        issueTests();
        summaryTests();
        recordTests();
        performance();
        System.out.println("Authoring core: " + checks + " checks passed");
    }

    private static void transferTests() {
        for (String source : List.of("", "Hello", "x".repeat(AuthoringWire.CHUNK_CHARS + 1), "\u2603".repeat(AuthoringWire.MAX_CHARS))) {
            List<AuthoringWire.Chunk> chunks = AuthoringWire.split("test", source);
            AuthoringWire.Assembly assembly = new AuthoringWire.Assembly(chunks.get(0), 0);
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
        check(!Character.isHighSurrogate(emojiChunks.get(0).data().charAt(emojiChunks.get(0).data().length() - 1)), "Packets must not split a Unicode surrogate pair");
    }

    private static boolean has(AuthoringKind kind, JsonObject body, String code, String field) {
        return AuthoringChecks.check(kind, body).stream().anyMatch(issue -> issue.code().equals(code) && issue.field().equals(field));
    }

    private static void issueTests() {
        for (AuthoringKind kind : AuthoringKind.values())
            check(AuthoringChecks.check(kind, AuthoringTemplates.create(kind)).stream().allMatch(AuthoringIssue::warning), "Templates must have no blocking issues: " + kind);
        JsonObject task = AuthoringTemplates.create(AuthoringKind.TASK);
        task.addProperty("title", "");
        check(has(AuthoringKind.TASK, task, "title_missing", "title"), "A missing title must name its field");
        JsonArray goals = new JsonArray();
        for (int index = 0; index < 2; index++) {
            JsonObject goal = new JsonObject();
            goal.addProperty("id", "same");
            goal.addProperty("type", "item");
            goal.addProperty("target", index == 0 ? "minecraft:stone" : "");
            goal.addProperty("count", index == 0 ? 1 : 0);
            goals.add(goal);
        }
        task.add("objectives", goals);
        check(has(AuthoringKind.TASK, task, "goal_duplicate", "objectives/1/id"), "A repeated goal ID must point at the second goal");
        check(has(AuthoringKind.TASK, task, "goal_target", "objectives/1/target"), "An empty target must be reported");
        check(has(AuthoringKind.TASK, task, "range", "objectives/1/count"), "A count below one must be reported");
        check(AuthoringChecks.check(AuthoringKind.TASK, task).size() >= 4, "Every problem must be reported at once");
        JsonObject mail = AuthoringTemplates.create(AuthoringKind.MESSAGE);
        mail.addProperty("sender", "");
        mail.getAsJsonObject("trigger").addProperty("value", "");
        check(has(AuthoringKind.MESSAGE, mail, "mail_sender", "sender") && has(AuthoringKind.MESSAGE, mail, "mail_target", "trigger/value"), "Mail needs a sender and a target");
        JsonObject pool = AuthoringTemplates.create(AuthoringKind.REWARD_POOL);
        pool.add("entries", new JsonArray());
        check(has(AuthoringKind.REWARD_POOL, pool, "pool_empty", "entries"), "An empty pool must be reported");
        JsonObject quiz = AuthoringTemplates.create(AuthoringKind.PROGRAM);
        quiz.getAsJsonArray("questions").get(0).getAsJsonObject().addProperty("correct", 5);
        check(has(AuthoringKind.PROGRAM, quiz, "question_correct", "questions/0/correct"), "A wrong answer index must be reported");
        JsonObject command = AuthoringTemplates.create(AuthoringKind.TERMINAL_COMMAND);
        command.addProperty("command", "Bad Name");
        check(has(AuthoringKind.TERMINAL_COMMAND, command, "command_letters", "command"), "Command names must be lowercase words");
        JsonObject broken = new JsonObject();
        broken.addProperty("objectives", "not a list");
        check(AuthoringChecks.check(AuthoringKind.TASK, broken).stream().anyMatch(issue -> !issue.warning()), "Malformed documents must be reported instead of throwing");
        AuthoringIssue issue = AuthoringIssue.error("range", "count", "1", "108");
        check(AuthoringIssue.decode(issue.encode()).equals(issue), "Issues must survive the network");
    }

    private static void summaryTests() {
        JsonObject disk = AuthoringTemplates.create(AuthoringKind.DISK);
        disk.getAsJsonArray("tasks").add("pack:one");
        disk.addProperty("unknown", "x".repeat(50_000));
        JsonObject summary = AuthoringSummary.of(AuthoringKind.DISK, disk);
        check(summary.getAsJsonArray("tasks").size() == 1 && !summary.has("unknown"), "Summaries keep links and drop everything else");
        check(summary.toString().length() < 4096, "Summaries must stay small");
        JsonObject task = AuthoringTemplates.create(AuthoringKind.TASK);
        JsonArray goals = new JsonArray();
        for (int index = 0; index < 20; index++) goals.add(AuthoringTemplates.arrayEntry(AuthoringKind.TASK, "objectives", index));
        task.add("objectives", goals);
        JsonObject shortened = AuthoringSummary.of(AuthoringKind.TASK, task);
        check(shortened.getAsJsonArray("objectives").size() == 3 && shortened.get("objectives_count").getAsInt() == 20, "Long lists must be shortened with a count");
    }

    private static void recordTests() {
        var report = com.craisinlord.antos.content.computer.games.ComputerGameRecords.Report.parse("antos:chess_hard|wins=1|moves=18");
        check(report != null && report.mode().equals("antos:chess_hard") && report.values().get("moves") == 18, "Game results must parse");
        for (String bad : new String[]{"", "antos:chess_hard", "chess|wins=1", "antos:x|wins=2", "antos:x|wins=-1", "antos:x|speed=1", "antos:x|time=abc", "antos:x|time=999999999999"})
            check(com.craisinlord.antos.content.computer.games.ComputerGameRecords.Report.parse(bad) == null, "Invalid game result must be refused: " + bad);
        check(com.craisinlord.antos.api.game.ComputerGameRecordsRegistry.register(new net.minecraft.resources.ResourceLocation("othermod:golf")) && com.craisinlord.antos.content.computer.games.ComputerGameRecords.known("othermod:golf"), "Other mods can register games from common code");
        check(!com.craisinlord.antos.api.game.ComputerGameRecordsRegistry.register(new net.minecraft.resources.ResourceLocation("antos:golf")), "AntOS game IDs are reserved");
        check(com.craisinlord.antos.content.computer.games.ComputerGameRecords.meets(55, "time", 60)
                && !com.craisinlord.antos.content.computer.games.ComputerGameRecords.meets(61, "time", 60)
                && com.craisinlord.antos.content.computer.games.ComputerGameRecords.meets(100, "score", 100)
                && !com.craisinlord.antos.content.computer.games.ComputerGameRecords.meets(-1, "time", 60), "Lower is better for time, higher for score");
    }

    private static void linkTests() {
        String old = "pack:welcome";
        String next = "pack:welcome_guide";
        JsonObject mail = AuthoringTemplates.create(AuthoringKind.MESSAGE);
        JsonObject trigger = new JsonObject();
        trigger.addProperty("type", "disk_installed");
        trigger.addProperty("value", old);
        trigger.addProperty("pool", "welcome");
        mail.add("trigger", trigger);
        check(AuthoringLinks.links(AuthoringKind.MESSAGE, "pack", mail, AuthoringKind.DISK, old), "Mail links to the floppy that sends it");
        check(!AuthoringLinks.links(AuthoringKind.MESSAGE, "pack", mail, AuthoringKind.ARCHIVE_ENTRY, old), "An article with the floppy's ID isn't linked by the mail");
        JsonObject same = AuthoringLinks.relink(AuthoringKind.MESSAGE, "pack", mail, AuthoringKind.ARCHIVE_ENTRY, old, next, java.util.Set.of());
        check(same.equals(mail), "Renaming an article must not change links to a floppy");
        JsonObject moved = AuthoringLinks.relink(AuthoringKind.MESSAGE, "pack", mail, AuthoringKind.DISK, old, next, java.util.Set.of());
        check(moved.getAsJsonObject("trigger").get("value").getAsString().equals(next) && moved.getAsJsonObject("trigger").get("pool").getAsString().equals("welcome"), "Renaming a floppy changes only its own links");
        JsonObject pooled = AuthoringLinks.relink(AuthoringKind.MESSAGE, "pack", mail, AuthoringKind.MAIL_POOL, old, next, java.util.Set.of());
        check(pooled.getAsJsonObject("trigger").get("pool").getAsString().equals(next), "Links without a namespace follow a rename");
        JsonObject disk = AuthoringTemplates.create(AuthoringKind.DISK);
        for (String field : List.of("tasks", "entries", "programs", "subscribe")) {
            JsonArray ids = new JsonArray();
            ids.add(old);
            disk.add(field, ids);
        }
        JsonObject relinked = AuthoringLinks.relink(AuthoringKind.DISK, "pack", disk, AuthoringKind.ARCHIVE_ENTRY, old, next, java.util.Set.of());
        check(relinked.getAsJsonArray("entries").get(0).getAsString().equals(next) && relinked.getAsJsonArray("tasks").get(0).getAsString().equals(old)
                && relinked.getAsJsonArray("programs").get(0).getAsString().equals(old), "A floppy keeps its links to other types");
        JsonObject both = AuthoringLinks.relink(AuthoringKind.DISK, "pack", disk, AuthoringKind.MESSAGE, old, next, java.util.Set.of(AuthoringKind.MAIL_POOL));
        check(both.getAsJsonArray("subscribe").size() == 2, "A subscription shared by a mail and a pool keeps both");
        JsonObject task = AuthoringTemplates.create(AuthoringKind.TASK);
        JsonObject goal = new JsonObject();
        goal.addProperty("id", "read");
        goal.addProperty("type", "archive_viewed");
        goal.addProperty("target", old);
        JsonArray goals = new JsonArray();
        goals.add(goal);
        task.add("objectives", goals);
        JsonObject reward = new JsonObject();
        reward.addProperty("type", "archive");
        reward.addProperty("entry", old);
        JsonArray options = new JsonArray();
        options.add(reward);
        JsonObject choice = new JsonObject();
        choice.addProperty("type", "choice");
        choice.add("options", options);
        JsonArray rewards = new JsonArray();
        rewards.add(choice);
        task.add("rewards", rewards);
        task.addProperty("other_mod", old);
        String changed = AuthoringLinks.relink(AuthoringKind.TASK, "pack", task, AuthoringKind.ARCHIVE_ENTRY, old, next, java.util.Set.of()).toString();
        check(changed.contains("\"target\":\"" + next) && changed.contains("\"entry\":\"" + next) && changed.contains("\"other_mod\":\"" + old + "\""), "Goals and nested rewards follow a rename, unknown fields don't");
        check(AuthoringLinks.loose(AuthoringKind.TASK, "pack", task, old), "An ID in an unknown field is noticed");
        task.remove("other_mod");
        check(!AuthoringLinks.loose(AuthoringKind.TASK, "pack", task, old), "Known links aren't loose");
        java.util.Set<String> used = new java.util.HashSet<>();
        AuthoringLinks.strings(disk, used);
        check(used.contains(old), "IDs used by saved content are found");
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
        for (String type : List.of("experience", "experience_levels", "antcoins", "effect", "mail")) {
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
            check(metadata.getAsJsonObject("pack").get("pack_format").getAsInt() == 15, "Minecraft 1.20.1 pack format");
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
