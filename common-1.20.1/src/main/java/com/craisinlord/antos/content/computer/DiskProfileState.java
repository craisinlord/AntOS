package com.craisinlord.antos.content.computer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

public final class DiskProfileState {
    private final Set<ResourceLocation> activated = new LinkedHashSet<>();
    private final Map<ResourceLocation, ResourceLocation> categories = new LinkedHashMap<>();
    private final Set<ResourceLocation> subscriptions = new LinkedHashSet<>();
    private final Map<String, String> copiedFiles = new LinkedHashMap<>();
    private final Set<String> endings = new LinkedHashSet<>();
    private final Map<ResourceLocation, Long> quizClaims = new LinkedHashMap<>();
    private final Set<ResourceLocation> commands = new LinkedHashSet<>();
    private final Set<ResourceLocation> grantedPrograms = new LinkedHashSet<>();

    public List<ResourceLocation> activatedDisks() { return List.copyOf(activated); }
    public Set<ResourceLocation> subscriptions() { return Set.copyOf(subscriptions); }
    public boolean activated(ResourceLocation disk) { return activated.contains(disk); }
    public boolean activate(ResourceLocation disk, ResourceLocation category) {
        categories.putIfAbsent(disk, category);
        return activated.add(disk);
    }
    public void seed(List<ResourceLocation> disks) { activated.addAll(disks); }
    public int categoryCount(ResourceLocation category) { return (int) categories.values().stream().filter(category::equals).count(); }
    public boolean subscribe(ResourceLocation id) { return subscriptions.add(id); }
    public boolean hasFile(String key) { return copiedFiles.containsKey(key); }
    public void recordFile(String key, String path) { copiedFiles.put(key, path); }
    public List<String> files(ResourceLocation disk) { return copiedFiles.entrySet().stream().filter(entry -> entry.getKey().startsWith(disk + "|" )).map(Map.Entry::getValue).distinct().toList(); }
    public boolean recordEnding(ResourceLocation program, String ending) { return endings.add(program + "#" + ending); }
    public int endingCount(ResourceLocation program, String ending) {
        return ending.isBlank() ? (int) endings.stream().filter(key -> key.startsWith(program + "#")).count() : endings.contains(program + "#" + ending) ? 1 : 0;
    }
    public boolean commandRun(ResourceLocation command) { return commands.contains(command); }
    public boolean recordCommand(ResourceLocation command) { return commands.add(command); }
    public Set<ResourceLocation> grantedPrograms() { return Set.copyOf(grantedPrograms); }
    public boolean programGranted(ResourceLocation program) { return grantedPrograms.contains(program); }
    public boolean grantProgram(ResourceLocation program) { return grantedPrograms.add(program); }
    public boolean claimQuiz(ResourceLocation program, long day) {
        Long previous = quizClaims.get(program);
        if (previous != null && day <= previous) return false;
        quizClaims.put(program, day);
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Activated", strings(activated.stream().map(ResourceLocation::toString).toList()));
        tag.put("Subscriptions", strings(subscriptions.stream().map(ResourceLocation::toString).toList()));
        tag.put("Endings", strings(List.copyOf(endings)));
        tag.put("Commands", strings(commands.stream().map(ResourceLocation::toString).toList()));
        tag.put("GrantedPrograms", strings(grantedPrograms.stream().map(ResourceLocation::toString).toList()));
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
        for (String id : values(tag.getList("Activated", 8))) { ResourceLocation parsed = ResourceLocation.tryParse(id); if (parsed != null) state.activated.add(parsed); }
        for (String id : values(tag.getList("Subscriptions", 8))) { ResourceLocation parsed = ResourceLocation.tryParse(id); if (parsed != null) state.subscriptions.add(parsed); }
        state.endings.addAll(values(tag.getList("Endings", 8)));
        for (String id : values(tag.getList("Commands", 8))) { ResourceLocation parsed = ResourceLocation.tryParse(id); if (parsed != null) state.commands.add(parsed); }
        for (String id : values(tag.getList("GrantedPrograms", 8))) { ResourceLocation parsed = ResourceLocation.tryParse(id); if (parsed != null) state.grantedPrograms.add(parsed); }
        ListTag rows = tag.getList("Categories", 10);
        for (int i = 0; i < rows.size(); i++) { CompoundTag row = rows.getCompound(i); ResourceLocation disk = ResourceLocation.tryParse(row.getString("Disk")), category = ResourceLocation.tryParse(row.getString("Category")); if (disk != null && category != null) state.categories.putIfAbsent(disk, category); }
        rows = tag.getList("CopiedFiles", 10);
        for (int i = 0; i < rows.size(); i++) { CompoundTag row = rows.getCompound(i); state.copiedFiles.put(row.getString("Key"), row.getString("Path")); }
        rows = tag.getList("QuizClaims", 10);
        for (int i = 0; i < rows.size(); i++) { CompoundTag row = rows.getCompound(i); ResourceLocation program = ResourceLocation.tryParse(row.getString("Program")); if (program != null) state.quizClaims.put(program, row.getLong("Day")); }
        return state;
    }

    private static ListTag strings(List<String> values) { ListTag tag = new ListTag(); values.forEach(value -> tag.add(StringTag.valueOf(value))); return tag; }
    private static List<String> values(ListTag list) { java.util.ArrayList<String> values = new java.util.ArrayList<>(); for (int i = 0; i < list.size(); i++) values.add(list.getString(i)); return List.copyOf(values); }
}
