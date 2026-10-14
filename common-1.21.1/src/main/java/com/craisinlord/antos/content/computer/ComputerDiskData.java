package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.guide.ComputerGuideData;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class ComputerDiskData {
    private static volatile Map<ResourceLocation, ComputerGuideData.Disk> disks = Map.of();
    private static volatile Map<ResourceLocation, String> files = Map.of();
    private static volatile Map<ResourceLocation, ComputerProgramData.Program> programs = Map.of();
    private static volatile long revision;

    private ComputerDiskData() { }

    public static void apply(Map<ResourceLocation, ComputerGuideData.Disk> loadedDisks, Map<ResourceLocation, String> loadedFiles,
                             Map<ResourceLocation, ComputerProgramData.Program> loadedPrograms) {
        disks = Map.copyOf(loadedDisks);
        files = Map.copyOf(loadedFiles);
        programs = Map.copyOf(loadedPrograms);
        revision++;
    }

    public static ComputerGuideData.Disk disk(ResourceLocation id) { return disks.get(id); }
    public static Map<ResourceLocation, ComputerGuideData.Disk> disks() { return disks; }
    public static Map<ResourceLocation, String> files() { return files; }
    public static Map<ResourceLocation, ComputerProgramData.Program> programs() { return programs; }
    public static long revision() { return revision; }
}
