package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.guide.ComputerGuideData;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class ComputerDiskData {
    private static volatile Map<Identifier, ComputerGuideData.Disk> disks = Map.of();
    private static volatile Map<Identifier, String> files = Map.of();
    private static volatile Map<Identifier, ComputerProgramData.Program> programs = Map.of();
    private static volatile long revision;

    private ComputerDiskData() { }

    public static void apply(Map<Identifier, ComputerGuideData.Disk> loadedDisks, Map<Identifier, String> loadedFiles,
                             Map<Identifier, ComputerProgramData.Program> loadedPrograms) {
        disks = Map.copyOf(loadedDisks);
        files = Map.copyOf(loadedFiles);
        programs = Map.copyOf(loadedPrograms);
        revision++;
    }

    public static ComputerGuideData.Disk disk(Identifier id) { return disks.get(id); }
    public static Map<Identifier, ComputerGuideData.Disk> disks() { return disks; }
    public static Map<Identifier, String> files() { return files; }
    public static Map<Identifier, ComputerProgramData.Program> programs() { return programs; }
    public static long revision() { return revision; }
}
