package com.craisinlord.antos.content.antazon;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

public final class AntazonUnlocks {
    private AntazonUnlocks() { }

    public static boolean test(List<ResourceLocation> tasks, List<ResourceLocation> disks, String mode,
                               Predicate<ResourceLocation> completedTask, Predicate<ResourceLocation> collectedDisk) {
        return test(tasks, disks, List.of(), mode, completedTask, collectedDisk, command -> false);
    }

    public static boolean test(List<ResourceLocation> tasks, List<ResourceLocation> disks, List<ResourceLocation> commands, String mode,
                               Predicate<ResourceLocation> completedTask, Predicate<ResourceLocation> collectedDisk, Predicate<ResourceLocation> ranCommand) {
        if (tasks.isEmpty() && disks.isEmpty() && commands.isEmpty()) return true;
        boolean any = "any".equals(mode);
        for (ResourceLocation id : tasks) {
            boolean unlocked = completedTask.test(id);
            if (any && unlocked) return true;
            if (!any && !unlocked) return false;
        }
        for (ResourceLocation id : disks) {
            boolean unlocked = collectedDisk.test(id);
            if (any && unlocked) return true;
            if (!any && !unlocked) return false;
        }
        for (ResourceLocation id : commands) {
            boolean unlocked = ranCommand.test(id);
            if (any && unlocked) return true;
            if (!any && !unlocked) return false;
        }
        return !any;
    }
}
