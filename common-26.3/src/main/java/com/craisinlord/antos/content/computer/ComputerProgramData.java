package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ComputerProgramData {
    private ComputerProgramData() { }

    public static Map<Identifier, Program> load(ResourceManager manager) {
        Map<Identifier, Program> programs = new LinkedHashMap<>();
        for (var entry : manager.listResources("computer/program", id -> id.getPath().endsWith(".json")).entrySet()) {
            String path = entry.getKey().getPath();
            Identifier id = Identifier.fromNamespaceAndPath(entry.getKey().getNamespace(), path.substring("computer/program/".length(), path.length() - 5));
            try (var reader = entry.getValue().openAsReader()) { programs.put(id, parse(id, JsonParser.parseReader(reader).getAsJsonObject())); }
            catch (Exception exception) { AntOS.LOGGER.warn("Ignoring malformed computer program {}", id, exception); }
        }
        return Map.copyOf(programs);
    }

    public static Program parse(Identifier id, JsonObject object) {
        String type = text(object, "type", "", 16), title = text(object, "title", "", 128);
        if (title.isBlank()) throw new IllegalArgumentException("Program title is required");
        if (type.equals("quiz")) {
            boolean greenTint = !object.has("green_tint") || object.get("green_tint").getAsBoolean();
            List<Question> questions = new ArrayList<>();
            for (JsonElement element : object.getAsJsonArray("questions")) {
                if (questions.size() >= 128) throw new IllegalArgumentException("Too many quiz questions");
                JsonObject question = element.getAsJsonObject();
                List<String> answers = strings(question, "answers", 8, 512);
                int correct = question.get("correct").getAsInt();
                if (answers.size() < 2 || correct < 0 || correct >= answers.size()) throw new IllegalArgumentException("Invalid quiz answers");
                questions.add(new Question(text(question, "question", "", 2048), answers, correct, asset(question, "item"), asset(question, "entity"),
                        question.has("green_tint") ? question.get("green_tint").getAsBoolean() : greenTint));
            }
            if (questions.isEmpty()) throw new IllegalArgumentException("A quiz needs questions");
            int perRun = object.has("questions_per_run") ? object.get("questions_per_run").getAsInt() : questions.size();
            if (perRun < 1) throw new IllegalArgumentException("A quiz run needs questions");
            String rewardOn = text(object, "reward_on", "perfect", 16);
            if (!rewardOn.equals("perfect") && !rewardOn.equals("complete")) throw new IllegalArgumentException("Invalid quiz reward condition");
            String rewardLimit = text(object, "reward_limit", "daily", 16);
            if (!rewardLimit.equals("daily") && !rewardLimit.equals("once")) throw new IllegalArgumentException("Invalid quiz reward limit");
            return new Program(id, type, title, Math.min(perRun, questions.size()), List.copyOf(questions), rewardOn,
                    ComputerGuideData.parseRewards(id, object.has("rewards") ? object.get("rewards") : object.get("daily_reward"), false), "", Map.of(),
                    rewardLimit.equals("once"));
        }
        if (!type.equals("adventure")) throw new IllegalArgumentException("Unsupported program type");
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (var entry : object.getAsJsonObject("nodes").entrySet()) {
            if (nodes.size() >= 128 || !identifier(entry.getKey())) throw new IllegalArgumentException("Invalid adventure node");
            JsonObject node = entry.getValue().getAsJsonObject();
            List<Choice> choices = new ArrayList<>();
            if (node.has("choices")) for (JsonElement element : node.getAsJsonArray("choices")) {
                if (choices.size() >= 8) throw new IllegalArgumentException("Too many adventure choices");
                JsonObject choice = element.getAsJsonObject();
                choices.add(new Choice(text(choice, "text", "", 512), text(choice, "goto", "", 64),
                        text(choice, "requires_flag", "", 64), text(choice, "requires_no_flag", "", 64)));
            }
            String ending = text(node, "ending", "", 64);
            if (!ending.isBlank() && (!identifier(ending) || !choices.isEmpty())) throw new IllegalArgumentException("Invalid ending node");
            if (ending.isBlank() && choices.isEmpty()) throw new IllegalArgumentException("A node needs choices or an ending");
            String password = text(node, "password", "", 128);
            if (!password.isEmpty() && ending.isBlank()) throw new IllegalArgumentException("Passwords belong to ending nodes");
            nodes.put(entry.getKey(), new Node(text(node, "text", "", 8192), List.copyOf(choices), flags(node, "set_flags"), flags(node, "clear_flags"),
                    ending, ComputerGuideData.parseRewards(id, node.get("rewards"), false), password));
        }
        String start = text(object, "start", "", 64);
        if (!nodes.containsKey(start)) throw new IllegalArgumentException("Adventure start node is missing");
        for (Node node : nodes.values()) for (Choice choice : node.choices()) if (!nodes.containsKey(choice.destination())) throw new IllegalArgumentException("Adventure choice has no destination");
        return new Program(id, type, title, 0, List.of(), "", List.of(), start, Map.copyOf(nodes), false);
    }

    private static List<String> flags(JsonObject object, String key) {
        List<String> values = strings(object, key, 64, 64);
        if (values.stream().anyMatch(flag -> !identifier(flag))) throw new IllegalArgumentException("Invalid program flag");
        return values;
    }

    private static boolean identifier(String value) { return value.matches("[a-zA-Z0-9_.-]{1,64}"); }

    private static String asset(JsonObject object, String key) {
        String value = text(object, key, "", 256);
        if (!value.isBlank() && Identifier.tryParse(value) == null) throw new IllegalArgumentException("Invalid quiz preview");
        return value;
    }

    private static List<String> strings(JsonObject object, String key, int maximum, int length) {
        if (!object.has(key)) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray(key)) {
            String value = element.getAsString();
            if (result.size() >= maximum || value.length() > length) throw new IllegalArgumentException("Program list exceeds limits");
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static String text(JsonObject object, String key, String fallback, int maximum) {
        String text = object.has(key) ? object.get(key).getAsString() : fallback;
        if (text.length() > maximum) throw new IllegalArgumentException("Program text exceeds limits");
        return text;
    }

    public record Program(Identifier id, String type, String title, int questionsPerRun, List<Question> questions,
                          String rewardOn, List<ComputerGuideData.TaskReward> rewards, String start, Map<String, Node> nodes, boolean rewardOnce) { }
    public record Question(String text, List<String> answers, int correct, String item, String entity, boolean greenTint) { }
    public record Choice(String text, String destination, String requiresFlag, String requiresNoFlag) { }
    public record Node(String text, List<Choice> choices, List<String> setFlags, List<String> clearFlags, String ending,
                       List<ComputerGuideData.TaskReward> rewards, String password) { }
}
