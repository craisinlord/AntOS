package com.craisinlord.antos.content.antazon;

import com.craisinlord.antos.content.computer.ComputerTaskProgress;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;

public final class AntazonDiskUnlockTest {
    private static final Identifier TASK = Identifier.parse("examplemod:research");
    private static final Identifier DISK = Identifier.parse("examplemod:catalog");

    public static void main(String[] args) throws Exception {
        check(unlocked("all", true, true), "all accepts completed task and collected disk");
        check(!unlocked("all", true, false), "all requires the disk even when the task is complete");
        check(!unlocked("all", false, true), "all requires the task even when the disk is collected");
        check(!unlocked("all", false, false), "all rejects an empty profile");
        check(unlocked("any", true, false), "any accepts a task without its disk");
        check(unlocked("any", false, true), "any accepts a disk without its task");
        check(!unlocked("any", false, false), "any rejects a profile meeting neither requirement");
        check(AntazonUnlocks.test(List.of(), List.of(), "any", id -> false, id -> false), "empty any requirements remain unlocked");
        check(AntazonUnlocks.test(List.of(), List.of(DISK), "all", id -> false, Set.of(DISK)::contains), "disk-only listings use permanent collection history");
        check(AntazonUnlocks.test(List.of(TASK), List.of(), "all", Set.of(TASK)::contains, id -> false), "old task-only listings remain compatible");
        ComputerTaskProgress progress = new ComputerTaskProgress();
        progress.markComplete(TASK);
        check(AntazonUnlockMail.newlyUnlocked(List.of(TASK), List.of(DISK), "all", progress, List.of(DISK), null, DISK), "final disk causes an all unlock notification");
        check(AntazonUnlockMail.newlyUnlocked(List.of(TASK), List.of(DISK), "all", progress, List.of(DISK), TASK, null), "final task causes an all unlock notification");
        check(!AntazonUnlockMail.newlyUnlocked(List.of(TASK), List.of(DISK), "any", progress, List.of(DISK), null, DISK), "already satisfied any listings do not notify again");
        check(!AntazonUnlockMail.newlyUnlocked(List.of(), List.of(), "all", progress, List.of(DISK), null, DISK), "ungated listings do not produce unlock mail");
        dealsRespectDiskUnlocks();
        System.out.println("Antazon disk unlock tests passed");
    }

    private static void dealsRespectDiskUnlocks() throws Exception {
        Identifier productId = Identifier.parse("examplemod:disk_catalog");
        var product = new AntazonData.Product(productId, "Catalog Supplies", "", "research", List.of(),
                new AntazonData.PreviewAsset("", ""), List.of(), 1, List.of(new AntazonData.Payment("antcoins", "antcoins", 10)), "all", List.of(),
                new AntazonData.Availability(0, 0, 0, 0, 0, "none"), List.of(), List.of(), "direct", null, true, true, true, false, true, null, null, null, List.of(), List.of(DISK));
        set(AntazonData.class, "loaded", Map.of(productId, product));
        set(AntazonData.class, "catalog", null);
        var candidates = new AntazonDealData.Candidates(Set.of(productId), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), 0, 0);
        var campaign = new AntazonDealData.Campaign(Identifier.parse("examplemod:daily"), "Daily", true, 1, 1, 0, false, 10, 10, candidates, 0, 0, 0);
        set(AntazonDealData.class, "campaigns", List.of(campaign));
        AntazonDeals.clear();
        UUID profile = UUID.randomUUID();
        check(AntazonDeals.deals(profile, listing -> AntazonUnlocks.test(listing.unlockTasks(), listing.unlockDisks(), listing.unlockMode(), id -> false, id -> false), 0).isEmpty(), "disk-locked products are absent from per-profile deals");
        check(AntazonData.catalogRow(productId).getAsJsonArray("unlock_disks").get(0).getAsString().equals(DISK.toString()), "disk requirements reach the product network snapshot");
        AntazonDeals.invalidate(profile);
        check(AntazonDeals.deals(profile, listing -> AntazonUnlocks.test(listing.unlockTasks(), listing.unlockDisks(), listing.unlockMode(), id -> false, Set.of(DISK)::contains), 0).containsKey(productId), "new disk unlock refreshes deals without waiting for another day");
        check(AntazonDeals.deals(UUID.randomUUID(), listing -> false, 0).isEmpty(), "another profile cannot inherit unlocked deals");
    }

    private static void set(Class<?> type, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static boolean unlocked(String mode, boolean task, boolean disk) {
        return AntazonUnlocks.test(List.of(TASK), List.of(DISK), mode, id -> task, id -> disk);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
