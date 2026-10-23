package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;

public final class ComputerProgramClientState {
    private static volatile Map<Identifier, Definition> definitions = Map.of();
    private static volatile java.util.Set<Identifier> grantedPrograms = java.util.Set.of();
    private static volatile List<String> terminalCommands = List.of();
    private static final Map<String, JsonObject> RUNS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Decryption>> DECRYPTIONS = new ConcurrentHashMap<>();
    private static final Map<String, Decryption> LAST_DECRYPTION = new ConcurrentHashMap<>();
    private static long revision;

    private ComputerProgramClientState() { }

    public static void updateArchive(JsonObject root) {
        Map<Identifier, Definition> loaded = new LinkedHashMap<>();
        if (root.has("programs")) for (var element : root.getAsJsonArray("programs")) {
            JsonObject row = element.getAsJsonObject();
            Identifier id = Identifier.tryParse(row.get("id").getAsString());
            if (id != null) loaded.put(id, new Definition(id, row.get("title").getAsString(), row.get("type").getAsString()));
        }
        definitions = Map.copyOf(loaded);
        java.util.Set<Identifier> granted = new java.util.LinkedHashSet<>();
        if (root.has("granted_programs")) for (var element : root.getAsJsonArray("granted_programs")) {
            Identifier id = Identifier.tryParse(element.getAsString());
            if (id != null) granted.add(id);
        }
        grantedPrograms = java.util.Set.copyOf(granted);
        List<String> commands = new java.util.ArrayList<>();
        if (root.has("terminal_commands")) for (var element : root.getAsJsonArray("terminal_commands")) commands.add(element.getAsString());
        terminalCommands = List.copyOf(commands);
    }

    public static void update(ComputerAccessResultPayload result) {
        boolean program = result.data().startsWith(ComputerAccessPayload.PROGRAM_STATE + "\0");
        if (!program && !result.data().startsWith(ComputerAccessPayload.DECRYPT_DISK + "\0")) return;
        try {
            var root = JsonParser.parseString(result.data().split("\u0000", 3)[2]).getAsJsonObject();
            String key = "workspace:" + root.get("workspace").getAsString();
            if (program) RUNS.put(key, root);
            else {
                Decryption value = new Decryption(root.get("disk").getAsString(), root.get("code").getAsString(), root.get("terminal").getAsBoolean(), ++revision);
                Map<String, Decryption> entries = new LinkedHashMap<>(DECRYPTIONS.getOrDefault(key, Map.of()));
                entries.put(value.disk(), value);
                DECRYPTIONS.put(key, Map.copyOf(entries));
                LAST_DECRYPTION.put(key, value);
                if (key.equals(ComputerWorkspaceClientKey.of())) AntOSUiSounds.play(value.code().equals("decrypted") || value.code().equals("already_unlocked") ? AntOSUiSounds.Cue.DISK : AntOSUiSounds.Cue.ERROR);
            }
        } catch (RuntimeException ignored) { }
    }

    public static List<Definition> definitions() { return definitions.values().stream().sorted(java.util.Comparator.comparing(definition -> definition.id().toString())).toList(); }
    public static Definition definition(Identifier id) { return definitions.get(id); }
    public static java.util.Set<Identifier> grantedPrograms() { return grantedPrograms; }
    public static List<String> terminalCommands() { return terminalCommands; }
    public static JsonObject run() { return RUNS.get(ComputerWorkspaceClientKey.of()); }
    public static void clearRun() { RUNS.remove(ComputerWorkspaceClientKey.of()); }
    public static Decryption decryption(Identifier id) { return DECRYPTIONS.getOrDefault(ComputerWorkspaceClientKey.of(), Map.of()).get(id.toString()); }
    public static Decryption lastDecryption() { return LAST_DECRYPTION.get(ComputerWorkspaceClientKey.of()); }
    public static void clearAll() { definitions = Map.of(); grantedPrograms = java.util.Set.of(); terminalCommands = List.of(); RUNS.clear(); DECRYPTIONS.clear(); LAST_DECRYPTION.clear(); }
    public static void clear() { String key = ComputerWorkspaceClientKey.of(); RUNS.remove(key); DECRYPTIONS.remove(key); LAST_DECRYPTION.remove(key); }

    public record Definition(Identifier id, String title, String type) { }
    public record Decryption(String disk, String code, boolean terminal, long revision) { }
}
