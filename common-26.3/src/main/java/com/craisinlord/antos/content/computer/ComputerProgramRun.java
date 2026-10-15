package com.craisinlord.antos.content.computer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class ComputerProgramRun {
    private final ComputerProgramData.Program program;
    private final String token = UUID.randomUUID().toString();
    private final List<QuizQuestion> questions = new ArrayList<>();
    private final Set<String> flags = new HashSet<>();
    private int question;
    private int score;
    private int sequence;
    private String node;
    private boolean aborted;

    public ComputerProgramRun(ComputerProgramData.Program program, RandomGenerator random) {
        this.program = program;
        if (program.type().equals("quiz")) {
            List<ComputerProgramData.Question> pool = new ArrayList<>(program.questions());
            shuffle(pool, random);
            for (var definition : pool.subList(0, program.questionsPerRun())) {
                List<Answer> answers = new ArrayList<>();
                for (int i = 0; i < definition.answers().size(); i++) answers.add(new Answer(definition.answers().get(i), i == definition.correct()));
                shuffle(answers, random);
                questions.add(new QuizQuestion(definition, List.copyOf(answers)));
            }
        } else enter(program.start());
    }

    public ComputerProgramData.Program program() { return program; }
    public String token() { return token; }
    public int sequence() { return sequence; }
    public boolean finished() { return aborted || (program.type().equals("quiz") ? question >= questions.size() : !currentNode().ending().isBlank()); }
    public boolean quizRewardEligible() { return !aborted && program.type().equals("quiz") && finished() && (program.rewardOn().equals("complete") || score == questions.size()); }
    public ComputerProgramData.Node currentNode() { return program.nodes().get(node); }

    public boolean choose(int choice, int expectedSequence) {
        if (expectedSequence != sequence || finished() || choice < 0) return false;
        if (program.type().equals("quiz")) {
            List<Answer> answers = questions.get(question).answers();
            if (choice >= answers.size()) return false;
            if (answers.get(choice).correct()) score++;
            question++;
        } else {
            List<ComputerProgramData.Choice> choices = choices();
            if (choice >= choices.size()) return false;
            enter(choices.get(choice).destination());
        }
        sequence++;
        if (sequence >= 4096 && !finished()) aborted = true;
        return true;
    }

    public List<ComputerProgramData.Choice> choices() {
        if (program.type().equals("quiz") || finished()) return List.of();
        return currentNode().choices().stream().filter(choice -> choice.requiresFlag().isBlank() || flags.contains(choice.requiresFlag()))
                .filter(choice -> choice.requiresNoFlag().isBlank() || !flags.contains(choice.requiresNoFlag())).toList();
    }

    private void enter(String destination) {
        node = destination;
        flags.removeAll(currentNode().clearFlags());
        flags.addAll(currentNode().setFlags());
    }

    public JsonObject snapshot() {
        JsonObject state = new JsonObject();
        state.addProperty("program", program.id().toString());
        state.addProperty("title", program.title());
        state.addProperty("run", token);
        state.addProperty("sequence", sequence);
        state.addProperty("status", aborted ? "aborted" : program.type() + (finished() ? "_done" : ""));
        JsonArray choices = new JsonArray();
        if (program.type().equals("quiz")) {
            state.addProperty("total", questions.size());
            state.addProperty("question_number", Math.min(question + 1, questions.size()));
            if (finished()) state.addProperty("score", score);
            else {
                var current = questions.get(question);
                state.addProperty("text", current.definition().text());
                state.addProperty("item", current.definition().item());
                state.addProperty("entity", current.definition().entity());
                state.addProperty("green_tint", current.definition().greenTint());
                current.answers().forEach(answer -> choices.add(answer.text()));
            }
        } else {
            state.addProperty("text", currentNode().text());
            if (finished() && !aborted) {
                state.addProperty("ending", currentNode().ending());
                if (!currentNode().password().isBlank()) state.addProperty("password", currentNode().password());
            } else choices().forEach(choice -> choices.add(choice.text()));
        }
        state.add("choices", choices);
        return state;
    }

    private static <T> void shuffle(List<T> values, RandomGenerator random) {
        for (int i = values.size() - 1; i > 0; i--) { int other = random.nextInt(i + 1); T value = values.get(i); values.set(i, values.get(other)); values.set(other, value); }
    }

    private record Answer(String text, boolean correct) { }
    private record QuizQuestion(ComputerProgramData.Question definition, List<Answer> answers) { }
}
