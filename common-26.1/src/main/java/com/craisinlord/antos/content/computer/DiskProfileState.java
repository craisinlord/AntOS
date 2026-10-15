package com.craisinlord.antos.content.computer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;

public final class DiskProfileState {
    private final Set<Identifier> activated = new LinkedHashSet<>();
    private final Map<Identifier, Identifier> categories = new LinkedHashMap<>();
    private final Set<Identifier> subscriptions = new LinkedHashSet<>();
    private final Map<String, String> copiedFiles = new LinkedHashMap<>();
    private final Set<String> endings = new LinkedHashSet<>();
    private final Map<Identifier, Long> quizClaims = new LinkedHashMap<>();
    private final Set<Identifier> commands = new LinkedHashSet<>();
    private final Set<Identifier> grantedPrograms = new LinkedHashSet<>();

    public List<Identifier> activatedDisks() { return List.copyOf(activated); }
    public Set<Identifier> subscriptions() { return Set.copyOf(subscriptions); }
    public boolean activated(Identifier disk) { return activated.contains(disk); }
    public boolean activate(Identifier disk, Identifier category) {
        categories.putIfAbsent(disk, category);
        return activated.add(disk);
    }
    public void seed(List<Identifier> disks) { activated.addAll(disks); }
    public int categoryCount(Identifier category) { return (int) categories.values().stream().filter(category::equals).count(); }
    public boolean subscribe(Identifier id) { return subscriptions.add(id); }
    public boolean hasFile(String key) { return copiedFiles.containsKey(key); }
    public void recordFile(String key, String path) { copiedFiles.put(key, path); }
    public List<String> files(Identifier disk) { return copiedFiles.entrySet().stream().filter(entry -> entry.getKey().startsWith(disk + "|" )).map(Map.Entry::getValue).distinct().toList(); }
    public boolean recordEnding(Identifier program, String ending) { return endings.add(program + "#" + ending); }
    public int endingCount(Identifier program, String ending) {
        return ending.isBlank() ? (int) endings.stream().filter(key -> key.startsWith(program + "#")).count() : endings.contains(program + "#" + ending) ? 1 : 0;
    }
    public boolean commandRun(Identifier command) { return commands.contains(command); }
    public boolean recordCommand(Identifier command) { return commands.add(command); }
    public Set<Identifier> grantedPrograms() { return Set.copyOf(grantedPrograms); }
    public boolean programGranted(Identifier program) { return grantedPrograms.contains(program); }
    public boolean grantProgram(Identifier program) { return grantedPrograms.add(program); }
    public boolean claimQuiz(Identifier program, long day) {
        Long previous = quizClaims.get(program);
        if (previous != null && day <= previous) return false;
        quizClaims.put(program, day);
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Activated", strings(activated.stream().map(Identifier::toString).toList()));
        tag.put("Subscriptions", strings(subscriptions.stream().map(Identifier::toString).toList()));
        tag.put("Endings", strings(List.copyOf(endings)));
        tag.put("Commands", strings(commands.stream().map(Identifier::toString).toList()));
        tag.put("GrantedPrograms", strings(grantedPrograms.stream().map(Identifier::toString).toList()));
        ListTag categoryRows = new ListTag();
        categories.forEach((disk, category) -> { CompoundTag row = new CompoundTag(); row.putString("Disk", disk.toString()); row.putString("Category", category.toString()); categoryRows.add(row); });
        tag.put("Categories", categoryRows);
        ListTag fileRows = new ListTag();
        copiedFiles.forEach((key, path) -> { CompoundTag row = new CompoundTag(); row.putString("Key", key); row.putString("Path", path); fileRows.add(row); });
        tag.put("CopiedFiles", fileRows);
        ListTag claims = new ListTag();
        quizClaims.forEach((program, day) -> { CompoundTag row = new CompoundTag(); row.putString("Program", program.toString()); row.putLong("Day", day); claims.add(row); });
        tag.put("QuizClaims", claims);
        return tag;
    }

    public static DiskProfileState load(CompoundTag tag) {
        DiskProfileState state = new DiskProfileState();
        for (String id : values(tag.getListOrEmpty("Activated"))) { Identifier parsed = Identifier.tryParse(id); if (parsed != null) state.activated.add(parsed); }
        for (String id : values(tag.getListOrEmpty("Subscriptions"))) { Identifier parsed = Identifier.tryParse(id); if (parsed != null) state.subscriptions.add(parsed); }
        state.endings.addAll(values(tag.getListOrEmpty("Endings")));
        for (String id : values(tag.getListOrEmpty("Commands"))) { Identifier parsed = Identifier.tryParse(id); if (parsed != null) state.commands.add(parsed); }
        for (String id : values(tag.getListOrEmpty("GrantedPrograms"))) { Identifier parsed = Identifier.tryParse(id); if (parsed != null) state.grantedPrograms.add(parsed); }
        ListTag rows = tag.getListOrEmpty("Categories");
        for (int i = 0; i < rows.size(); i++) { CompoundTag row = rows.getCompoundOrEmpty(i); Identifier disk = Identifier.tryParse(row.getStringOr("Disk", "")), category = Identifier.tryParse(row.getStringOr("Category", "")); if (disk != null && category != null) state.categories.putIfAbsent(disk, category); }
        rows = tag.getListOrEmpty("CopiedFiles");
        for (int i = 0; i < rows.size(); i++) { CompoundTag row = rows.getCompoundOrEmpty(i); state.copiedFiles.put(row.getStringOr("Key", ""), row.getStringOr("Path", "")); }
        rows = tag.getListOrEmpty("QuizClaims");
        for (int i = 0; i < rows.size(); i++) { CompoundTag row = rows.getCompoundOrEmpty(i); Identifier program = Identifier.tryParse(row.getStringOr("Program", "")); if (program != null) state.quizClaims.put(program, row.getLongOr("Day", 0L)); }
        return state;
    }

    private static ListTag strings(List<String> values) { ListTag tag = new ListTag(); values.forEach(value -> tag.add(StringTag.valueOf(value))); return tag; }
    private static List<String> values(ListTag list) { java.util.ArrayList<String> values = new java.util.ArrayList<>(); for (int i = 0; i < list.size(); i++) values.add(list.getStringOr(i, "")); return List.copyOf(values); }
}
