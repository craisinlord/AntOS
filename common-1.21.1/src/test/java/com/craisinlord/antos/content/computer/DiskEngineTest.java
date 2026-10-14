package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.antmail.AntmailEventData;
import com.craisinlord.antos.content.computer.paint.AntPaintFileCodec;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.guide.DiskFeatures;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class DiskEngineTest {
    private static final ResourceLocation DISK = id("lab"), PROGRAM = id("escape"), CATEGORY = id("research");

    public static void main(String[] args) throws Exception {
        encryption();
        persistenceAndAvailability();
        fileDelivery();
        objectives();
        quizzes();
        adventures();
        newsletters();
        examples(Path.of(args[0]));
        System.out.println("Disk engine tests passed: encryption, profile persistence, files, objectives, programs, newsletters, and examples");
    }

    private static void encryption() throws Exception {
        var disk = disk("{\"encryption\":{\"password\":\"Redstone\",\"password_any\":[\"red stone\"],\"hint\":\"hint\"}}");
        var encryption = disk.features().encryption();
        check(encryption.accepts("REDSTONE") && encryption.accepts("red stone"), "alternative answers and case insensitive passwords are accepted");
        check(!encryption.accepts("wrong") && !encryption.accepts(null) && !encryption.accepts("x".repeat(129)), "invalid passwords are rejected");
        var sensitive = DiskFeatures.parse(json("{\"encryption\":{\"password\":\"Redstone\",\"case_sensitive\":true}}"));
        check(sensitive.encryption().accepts("Redstone") && !sensitive.encryption().accepts("redstone"), "case sensitive passwords are exact");
        JsonObject publicDisk = new JsonObject();
        publicDisk.addProperty("id", DISK.toString());
        disk.features().encodeClient(publicDisk);
        check(!publicDisk.toString().contains("Redstone") && !publicDisk.toString().contains("red stone"), "disk snapshot contains no accepted passwords");
        var client = DiskFeatures.parse(publicDisk);
        check(client.encryption().enabled() && client.encryption().passwords().isEmpty() && !client.encryption().accepts("Redstone"), "client keeps lock metadata without the secret");
        ComputerDiskData.apply(Map.of(DISK, disk), Map.of(), Map.of());
        ComputerGuideData.applyNetworkSnapshot("{\"disks\":[" + publicDisk + "]}");
        check(ComputerGuideData.disk(DISK).features().encryption().passwords().isEmpty(), "singleplayer client receives redacted definitions");
        check(ComputerDiskData.disk(DISK).features().encryption().accepts("Redstone"), "singleplayer server authority survives client snapshot application");
        check(!ComputerGuideData.encodeNetworkSnapshot().contains("Redstone"), "subsequent guide snapshot cannot expose server secrets");
        rejects(() -> DiskFeatures.parse(json("{\"encryption\":{\"password\":\"\"}}")), "empty passwords are invalid");
    }

    private static void persistenceAndAvailability() throws Exception {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        var profile = data.createAccount("disk_engine", "x").account();
        var other = data.createAccount("disk_other", "x").account();
        var definition = disk("{\"programs\":[\"examplemod:escape\"],\"encryption\":{\"password\":\"secret\"}}");
        ComputerDiskData.apply(Map.of(DISK, definition), Map.of(), Map.of());
        data.addProfileDisk(profile.accountId(), DISK);
        check(data.collectedDisks(profile.accountId()).contains(DISK), "encrypted insertion records physical collection");
        check(!ComputerPrograms.available(data, profile.accountId(), PROGRAM), "locked installed disks do not make programs available");
        DiskProfileState state = data.diskState(profile.accountId());
        check(state.activate(DISK, CATEGORY) && !state.activate(DISK, CATEGORY), "activation counts a disk once");
        check(ComputerPrograms.available(data, profile.accountId(), PROGRAM), "activated installed disks make programs available");
        state.subscribe(id("newsletter"));
        state.recordFile(DISK + "|notes.txt", "/Disks/Lab/notes.txt");
        check(state.recordEnding(PROGRAM, "escaped") && !state.recordEnding(PROGRAM, "escaped"), "an ending pays once");
        state.recordEnding(PROGRAM, "detour");
        check(state.claimQuiz(id("quiz"), 7) && !state.claimQuiz(id("quiz"), 7) && !state.claimQuiz(id("quiz"), 6), "daily payouts reject repeats and clock rollback");
        check(state.claimQuiz(id("quiz"), 8), "a later Minecraft day is eligible");
        data.removeProfileDisk(profile.accountId(), DISK);
        check(!ComputerPrograms.available(data, profile.accountId(), PROGRAM), "ejection removes program access");
        check(state.activated(DISK) && state.categoryCount(CATEGORY) == 1 && state.subscriptions().contains(id("newsletter")), "ejection preserves activation, category progress, and subscriptions");
        var loader = ComputerWorkspaceData.class.getDeclaredMethod("load", net.minecraft.nbt.CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        loader.setAccessible(true);
        var restored = (ComputerWorkspaceData) loader.invoke(null, data.save(new net.minecraft.nbt.CompoundTag(), null), null);
        var loaded = restored.diskState(profile.accountId());
        check(loaded.activated(DISK) && loaded.categoryCount(CATEGORY) == 1 && loaded.files(DISK).size() == 1, "activated disks, category progress, and file receipts survive reload");
        check(loaded.subscriptions().contains(id("newsletter")) && loaded.endingCount(PROGRAM, "") == 2 && loaded.endingCount(PROGRAM, "escaped") == 1, "subscriptions and ending progress survive reload");
        check(!loaded.recordEnding(PROGRAM, "escaped") && !loaded.claimQuiz(id("quiz"), 8), "reward limits survive reload");
        check(restored.diskState(other.accountId()).activatedDisks().isEmpty() && restored.diskState(other.accountId()).endingCount(PROGRAM, "") == 0, "profile progress is isolated");
        restored.addProfileDisk(profile.accountId(), DISK);
        check(ComputerPrograms.available(restored, profile.accountId(), PROGRAM), "reinsertion restores program access");
    }

    private static void fileDelivery() throws Exception {
        var disk = disk("{\"title\":\"Lab\",\"files\":[{\"path\":\"notes.txt\",\"source\":\"examplemod:disk_files/lab/notes.txt\"},{\"path\":\"Disks/Lab/map.paint\",\"source\":\"examplemod:lab/map.json\"}]}");
        ComputerFileSystem files = new ComputerFileSystem();
        DiskProfileState state = new DiskProfileState();
        JsonArray rows = new JsonArray();
        for (int i = 0; i < 24; i++) rows.add(i == 0 ? "1" + "0".repeat(31) : "0".repeat(32));
        JsonObject paint = new JsonObject(); paint.add("pixels", rows);
        Map<ResourceLocation, String> sources = Map.of(id("lab/notes.txt"), "Hello {player}, café!", id("lab/map.json"), paint.toString());
        ComputerDiskFiles.copy(files, state, disk, sources, "Collector", 100);
        check(files.get("Disks/Lab/notes.txt").contents().equals("Hello Collector, café!"), "UTF-8 text, player placeholder, and default title folder are delivered");
        var image = files.get("Disks/Lab/map.antpaint");
        check(image.type() == ComputerFileSystem.ComputerFile.Type.IMAGE, "paint alias creates a native image file");
        var decoded = AntPaintFileCodec.decode(Base64.getDecoder().decode(image.contents()));
        check(decoded.canvas().get(0, 0) && !decoded.canvas().get(1, 0), "paint JSON round trips through the native codec");
        var nativeCopy = AntPaintFileCodec.decode(Base64.getDecoder().decode(ComputerDiskFiles.paint(image.contents(), "/map.antpaint", 200)));
        check(nativeCopy.canvas().get(0, 0) && !nativeCopy.fileId().equals(decoded.fileId()), "native encoded paint sources get their own file identity");
        files.writeTextFile("Disks/Lab/notes.txt", "Player edits");
        ComputerDiskFiles.copy(files, state, disk, sources, "Other name", 200);
        check(files.get("Disks/Lab/notes.txt").contents().equals("Player edits"), "reinsert preserves player edits");
        files.delete("Disks/Lab/notes.txt");
        ComputerDiskFiles.copy(files, DiskProfileState.load(state.save()), disk, sources, "Collector", 200);
        check(files.get("Disks/Lab/notes.txt") == null, "deleted copies are not recreated after reload");
        var changedSource = disk("{\"title\":\"Lab\",\"files\":[{\"path\":\"notes.txt\",\"source\":\"examplemod:new.txt\"}]}");
        ComputerDiskFiles.copy(files, state, changedSource, Map.of(id("new.txt"), "Updated source"), "Collector", 200);
        check(files.get("Disks/Lab/notes.txt") == null, "changing a source cannot recreate a deleted delivered path");
        DiskProfileState preexisting = new DiskProfileState();
        files.createTextFile("Disks/Lab/notes.txt", "Existing notes");
        ComputerDiskFiles.copy(files, preexisting, disk, sources, "Collector", 100);
        check(files.get("Disks/Lab/notes.txt").contents().equals("Existing notes"), "existing paths are never overwritten on first install");
        var unsafe = disk("{\"files\":[{\"path\":\"system/password.txt\",\"source\":\"examplemod:x\"},{\"path\":\"../notes.txt\",\"source\":\"examplemod:x\"},{\"path\":\"missing.txt\",\"source\":\"examplemod:missing\"}]}");
        DiskProfileState rejected = new DiskProfileState();
        ComputerDiskFiles.copy(files, rejected, unsafe, Map.of(id("x"), "bad"), "Collector", 1);
        check(rejected.files(DISK).isEmpty() && files.get("system/password.txt") == null, "unsafe and unavailable sources are not copied or claimed");
        check(ComputerDiskFiles.sourceId(id("computer/disk_file/lab/notes.txt")).equals(id("lab/notes.txt")), "resource path aliases resolve correctly");
        rejects(() -> ComputerDiskFiles.paint("{\"pixels\":[]}", "/bad.antpaint", 1), "malformed paint is rejected");
    }

    private static void objectives() throws Exception {
        var task = task("{\"objectives\":[{\"id\":\"one\",\"type\":\"disk_installed\",\"target\":\"examplemod:lab\",\"count\":5},{\"id\":\"set\",\"type\":\"disk_installed\",\"target_category\":\"examplemod:research\",\"count\":3},{\"id\":\"ending\",\"type\":\"program_ending\",\"target\":\"examplemod:escape\",\"ending\":\"escaped\"},{\"id\":\"bad\",\"type\":\"disk_installed\",\"target\":\"examplemod:lab\",\"target_category\":\"examplemod:research\"}]}");
        check(task.objectives().size() == 3, "ambiguous disk objectives are rejected");
        check(task.objectives().get(0).count() == 1 && task.objectives().get(1).count() == 3, "single disk and category counts parse correctly");
        check(task.objectives().get(1).targetCategory().equals(CATEGORY.toString()) && task.objectives().get(2).ending().equals("escaped"), "category and ending selectors are preserved");
    }

    private static void quizzes() {
        var quiz = program("{\"type\":\"quiz\",\"title\":\"Quiz\",\"questions_per_run\":2,\"questions\":[{\"question\":\"A\",\"answers\":[\"right\",\"wrong\",\"other\"],\"correct\":0},{\"question\":\"B\",\"answers\":[\"wrong\",\"right\",\"other\"],\"correct\":1},{\"question\":\"C\",\"answers\":[\"other\",\"wrong\",\"right\"],\"correct\":2}],\"daily_reward\":[{\"type\":\"antcoins\",\"amount\":10}],\"reward_on\":\"perfect\"}");
        Set<String> firstQuestions = new HashSet<>(), firstAnswers = new HashSet<>();
        for (int seed = 0; seed < 20; seed++) {
            var run = new ComputerProgramRun(quiz, new Random(seed));
            firstQuestions.add(run.snapshot().get("text").getAsString());
            firstAnswers.add(run.snapshot().getAsJsonArray("choices").toString());
            check(!run.snapshot().has("correct") && !run.snapshot().has("questions") && !run.snapshot().has("score"), "active quiz snapshots contain neither answers nor future questions");
            int sequence = 0;
            while (!run.finished()) {
                JsonArray answers = run.snapshot().getAsJsonArray("choices");
                int correct = 0;
                while (!answers.get(correct).getAsString().equals("right")) correct++;
                check(!run.choose(99, sequence), "invalid answer index does not advance");
                check(run.choose(correct, sequence++), "valid shuffled answer advances");
                check(!run.choose(0, sequence - 1), "replayed answer sequence does not advance");
            }
            check(run.snapshot().get("score").getAsInt() == 2 && run.quizRewardEligible(), "perfect quiz is scored by the server");
            check(!run.choose(0, sequence), "completed quizzes cannot be advanced");
        }
        check(firstQuestions.size() > 1 && firstAnswers.size() > 1, "both questions and answer order vary across runs");
        var wrong = new ComputerProgramRun(quiz, new Random(1));
        while (!wrong.finished()) { var answers = wrong.snapshot().getAsJsonArray("choices"); int index = answers.get(0).getAsString().equals("right") ? 1 : 0; wrong.choose(index, wrong.sequence()); }
        check(!wrong.quizRewardEligible(), "imperfect runs cannot claim perfect rewards");
        var complete = new ComputerProgramData.Program(quiz.id(), quiz.type(), quiz.title(), quiz.questionsPerRun(), quiz.questions(), "complete", quiz.rewards(), "", Map.of());
        var any = new ComputerProgramRun(complete, new Random(1));
        while (!any.finished()) any.choose(0, any.sequence());
        check(any.quizRewardEligible(), "complete mode rewards a finished run regardless of score");
        rejects(() -> program("{\"type\":\"quiz\",\"title\":\"Bad\",\"questions\":[{\"answers\":[\"a\",\"b\"],\"correct\":2}]}"), "out of bounds correct answer is invalid");
    }

    private static void adventures() {
        var story = program("{\"type\":\"adventure\",\"title\":\"Escape\",\"start\":\"intro\",\"nodes\":{\"intro\":{\"text\":\"Lab\",\"choices\":[{\"text\":\"Get key\",\"goto\":\"key\",\"requires_no_flag\":\"key\"},{\"text\":\"Escape\",\"goto\":\"win\",\"requires_flag\":\"key\"}]},\"key\":{\"text\":\"Key\",\"set_flags\":[\"key\"],\"choices\":[{\"text\":\"Return\",\"goto\":\"intro\"},{\"text\":\"Drop\",\"goto\":\"drop\"}]},\"drop\":{\"clear_flags\":[\"key\"],\"choices\":[{\"text\":\"Return\",\"goto\":\"intro\"}]},\"win\":{\"text\":\"Freedom\",\"ending\":\"escaped\",\"password\":\"future-secret\",\"rewards\":[{\"type\":\"antcoins\",\"amount\":50}]}}}");
        var run = new ComputerProgramRun(story, new Random(0));
        check(run.choices().size() == 1 && !run.snapshot().toString().contains("future-secret"), "hidden flag choices and ending passwords are not exposed");
        run.choose(0, 0);
        run.choose(1, 1);
        run.choose(0, 2);
        check(run.choices().getFirst().text().equals("Get key"), "clear_flags restores the initial choice");
        run.choose(0, 3);
        run.choose(0, 4);
        check(run.choices().size() == 1 && run.choices().getFirst().text().equals("Escape"), "set_flags unlocks and filters choices");
        run.choose(0, 5);
        check(run.finished() && run.snapshot().get("ending").getAsString().equals("escaped") && run.snapshot().get("password").getAsString().equals("future-secret"), "password appears only after reaching the ending");
        check(!run.snapshot().has("nodes") && !run.snapshot().has("rewards"), "story snapshots never include the graph or payout definitions");
        var fresh = new ComputerProgramRun(story, new Random(0));
        check(fresh.choices().getFirst().text().equals("Get key"), "flags reset on a new run");
        var loop = program("{\"type\":\"adventure\",\"title\":\"Loop\",\"start\":\"loop\",\"nodes\":{\"loop\":{\"choices\":[{\"text\":\"Again\",\"goto\":\"loop\"}]}}}");
        var bounded = new ComputerProgramRun(loop, new Random(0));
        for (int i = 0; i < 4096; i++) bounded.choose(0, i);
        check(bounded.finished() && bounded.snapshot().get("status").getAsString().equals("aborted") && !bounded.snapshot().has("ending"), "infinite runs terminate without an ending payout");
        rejects(() -> program("{\"type\":\"adventure\",\"title\":\"Bad\",\"start\":\"a\",\"nodes\":{\"a\":{\"choices\":[{\"text\":\"Go\",\"goto\":\"missing\"}]}}}"), "missing story destinations are invalid");
        rejects(() -> program("{\"type\":\"adventure\",\"title\":\"Bad\",\"start\":\"a\",\"nodes\":{\"a\":{\"password\":\"secret\",\"choices\":[{\"text\":\"Go\",\"goto\":\"a\"}]}}}"), "passwords on non-ending nodes are invalid");
    }

    private static void newsletters() {
        ResourceLocation definition = id("news"), pool = id("pool");
        check(AntmailEventData.subscriptionEligible(false, false, definition, pool, Set.of()), "legacy random mail requires no subscription");
        check(!AntmailEventData.subscriptionEligible(true, false, definition, null, Set.of()), "standalone restricted mail excludes non-subscribers");
        check(!AntmailEventData.subscriptionEligible(false, true, definition, pool, Set.of()), "restricted pools exclude non-subscribers");
        check(AntmailEventData.subscriptionEligible(true, true, definition, pool, Set.of(pool)), "a pool subscription admits a pool message");
        check(AntmailEventData.subscriptionEligible(true, true, definition, pool, Set.of(definition)), "an individual subscription admits its message");
        check(!AntmailEventData.subscriptionEligible(true, true, definition, pool, Set.of(id("unrelated"))), "unrelated subscriptions grant no eligibility");
    }

    private static void examples(Path root) throws Exception {
        Path data = root.resolve("examples/datapack-template/data/examplemod");
        Map<ResourceLocation, ComputerProgramData.Program> programs = new HashMap<>();
        for (String name : List.of("lab_quiz", "lab_escape")) programs.put(id(name), ComputerProgramData.parse(id(name), json(Files.readString(data.resolve("computer/program/" + name + ".json")))));
        for (String name : List.of("lab_programs", "lab_secret")) {
            var example = disk(Files.readString(data.resolve("computer/disk/" + name + ".json")));
            for (var file : example.features().files()) check(Files.exists(data.resolve("computer/disk_file/" + ComputerDiskFiles.sourceId(file.source()).getPath())), "example source exists: " + file.source());
            for (var program : example.features().programs()) check(programs.containsKey(program), "example program exists: " + program);
            for (var subscription : example.features().subscriptions()) check(Files.exists(data.resolve("antmail/pool/" + subscription.getPath() + ".json")) || Files.exists(data.resolve("antmail/message/" + subscription.getPath() + ".json")), "example subscription exists: " + subscription);
        }
        var collector = task(Files.readString(data.resolve("computer/task/disk_collector.json")));
        check(collector.objectives().size() == 2 && collector.availability().equals("automatic"), "example collection task is available without a separate disk grant");
        var endingTask = task(Files.readString(data.resolve("computer/task/lab_escape.json")));
        check(endingTask.objectives().getFirst().type().equals("program_ending") && endingTask.availability().equals("automatic"), "example ending task is available without a separate disk grant");
        ComputerDiskFiles.paint(Files.readString(data.resolve("computer/disk_file/lab/map.json")), "/map.antpaint", 1);
    }

    private static ComputerGuideData.Disk disk(String text) throws Exception {
        Method parser = ComputerGuideData.class.getDeclaredMethod("parseDisk", ResourceLocation.class, JsonElement.class, Map.class);
        parser.setAccessible(true);
        Map<ResourceLocation, ComputerGuideData.Disk> parsed = new HashMap<>();
        parser.invoke(null, DISK, json(text), parsed);
        return parsed.get(DISK);
    }

    private static ComputerGuideData.Task task(String text) throws Exception {
        Method parser = ComputerGuideData.class.getDeclaredMethod("parseTask", ResourceLocation.class, JsonElement.class, Map.class);
        parser.setAccessible(true);
        Map<ResourceLocation, ComputerGuideData.Task> parsed = new HashMap<>();
        parser.invoke(null, id("task"), json(text), parsed);
        return parsed.get(id("task"));
    }

    private static ComputerProgramData.Program program(String text) { return ComputerProgramData.parse(PROGRAM, json(text)); }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("examplemod", path); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action, String message) { try { action.run(); } catch (RuntimeException expected) { return; } throw new AssertionError(message); }
}
