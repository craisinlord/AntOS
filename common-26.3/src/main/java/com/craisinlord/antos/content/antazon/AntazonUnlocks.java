package com.craisinlord.antos.content.antazon;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;

public final class AntazonUnlocks {
    private AntazonUnlocks() { }

    public static boolean test(List<Identifier> tasks, List<Identifier> disks, String mode,
                               Predicate<Identifier> completedTask, Predicate<Identifier> collectedDisk) {
        return test(tasks, disks, List.of(), mode, completedTask, collectedDisk, command -> false);
    }

    public static boolean test(List<Identifier> tasks, List<Identifier> disks, List<Identifier> commands, String mode,
                               Predicate<Identifier> completedTask, Predicate<Identifier> collectedDisk, Predicate<Identifier> ranCommand) {
        if (tasks.isEmpty() && disks.isEmpty() && commands.isEmpty()) return true;
        boolean any = "any".equals(mode);
        for (Identifier id : tasks) {
            boolean unlocked = completedTask.test(id);
            if (any && unlocked) return true;
            if (!any && !unlocked) return false;
        }
        for (Identifier id : disks) {
            boolean unlocked = collectedDisk.test(id);
            if (any && unlocked) return true;
            if (!any && !unlocked) return false;
        }
        for (Identifier id : commands) {
            boolean unlocked = ranCommand.test(id);
            if (any && unlocked) return true;
            if (!any && !unlocked) return false;
        }
        return !any;
    }
}
