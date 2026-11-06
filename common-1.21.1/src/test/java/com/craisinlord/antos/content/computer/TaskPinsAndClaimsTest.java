package com.craisinlord.antos.content.computer;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.craisinlord.antos.content.network.AnternetComputerPayload;
import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerNetworking;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public final class TaskPinsAndClaimsTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        pins();
        parsedProgress();
        crates();
        requestsReachServer();
        System.out.println("TaskPinsAndClaimsTest passed");
    }

    private static void parsedProgress() {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        var account = data.completeAccountCreation("Bob", ComputerWorkspaceData.hashPassword("secret"), "").account();
        UUID workspace = account.workspaceId();
        check(!data.readWorkspaceProgress(workspace).hasRecoverableProgress(), "new workspaces start empty");
        check(!data.readWorkspaceProgress(UUID.randomUUID()).hasRecoverableProgress(), "unknown workspaces read as empty");
        ComputerTaskProgress saved = new ComputerTaskProgress();
        saved.markComplete(task("a"));
        saved.setObjectiveCount(task("a"), "one", 3);
        check(data.saveWorkspaceSection(workspace, "TaskProgress", saved.save()), "progress saves to the account");
        ComputerTaskProgress first = data.readWorkspaceProgress(workspace);
        check(first.isComplete(task("a")) && first.objectiveCount(task("a"), "one") == 3, "saved progress is read back");
        first.markComplete(task("b"));
        check(!data.readWorkspaceProgress(workspace).isComplete(task("b")), "changes to one read don't leak into the next");
        saved.markComplete(task("c"));
        data.saveWorkspaceSection(workspace, "TaskProgress", saved.save());
        check(data.readWorkspaceProgress(workspace).isComplete(task("c")), "a new save replaces the cached progress");
        check(data.savedProgress(workspace).isComplete(task("c")), "saved progress includes the account's own progress");
        ComputerTaskProgress legacy = new ComputerTaskProgress();
        legacy.markComplete(task("e"));
        data.save(workspace, legacy);
        check(data.savedProgress(workspace).isComplete(task("e")) && data.savedProgress(workspace).isComplete(task("c")), "saved progress also includes legacy progress");

        ComputerTaskProgress personal = new ComputerTaskProgress();
        check(!data.hasTeam(account.accountId()) && !data.mergeTeamProgressInto(account.accountId(), personal), "accounts without a team merge nothing");
        check(data.createTeam(account.accountId()) && data.hasTeam(account.accountId()), "created teams are found");
        ComputerTaskProgress shared = new ComputerTaskProgress();
        shared.markComplete(task("d"));
        data.mergeTeamProgress(account.accountId(), shared);
        check(data.mergeTeamProgressInto(account.accountId(), personal) && personal.isComplete(task("d")), "team progress merges into personal progress");
        check(!data.mergeTeamProgressInto(account.accountId(), personal), "merging twice reports no change");
    }

    private static void pins() throws Exception {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        UUID account = data.completeAccountCreation("Alice", ComputerWorkspaceData.hashPassword("secret"), "").account().accountId();
        List<ResourceLocation> ids = List.of(task("a"), task("b"), task("c"), task("d"), task("e"), task("f"));
        check(data.pinnedTasks(account).isEmpty(), "new accounts have no pins");
        for (int index = 0; index < ComputerWorkspaceData.MAX_PINNED_TASKS; index++)
            check(data.setTaskPinned(account, ids.get(index), true) == ComputerWorkspaceData.PinResult.CHANGED, "pin " + index + " is stored");
        check(data.setTaskPinned(account, ids.get(0), true) == ComputerWorkspaceData.PinResult.UNCHANGED, "pinning twice changes nothing");
        check(data.setTaskPinned(account, ids.get(5), true) == ComputerWorkspaceData.PinResult.FULL, "pins stop at the limit");
        check(data.pinnedTasks(account).equals(ids.subList(0, 5)), "pins keep the order they were added in");
        check(data.setTaskPinned(account, ids.get(1), false) == ComputerWorkspaceData.PinResult.CHANGED, "unpinning removes the task");
        check(data.setTaskPinned(account, ids.get(1), false) == ComputerWorkspaceData.PinResult.UNCHANGED, "unpinning twice changes nothing");
        check(data.setTaskPinned(account, ids.get(5), true) == ComputerWorkspaceData.PinResult.CHANGED, "a freed slot can be reused");
        check(data.setTaskPinned(UUID.randomUUID(), ids.get(0), true) == ComputerWorkspaceData.PinResult.INVALID, "unknown accounts can't pin");
        check(!data.isTaskPinned(account, ids.get(1)) && data.isTaskPinned(account, ids.get(5)), "pin lookups match the stored list");

        CompoundTag saved = data.save(new CompoundTag(), null);
        Method load = ComputerWorkspaceData.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        ComputerWorkspaceData reloaded = (ComputerWorkspaceData) load.invoke(null, saved, null);
        check(reloaded.pinnedTasks(account).equals(List.of(ids.get(0), ids.get(2), ids.get(3), ids.get(4), ids.get(5))), "pins survive a save in order");
        for (ResourceLocation id : reloaded.pinnedTasks(account)) reloaded.setTaskPinned(account, id, false);
        CompoundTag empty = reloaded.save(new CompoundTag(), null);
        ComputerWorkspaceData cleared = (ComputerWorkspaceData) load.invoke(null, empty, null);
        check(cleared.pinnedTasks(account).isEmpty(), "unpinning everything leaves nothing behind");
    }

    private static void crates() {
        check(ComputerTasks.packRewardCrates(List.of()).isEmpty(), "no items means no crates");
        List<List<ItemStack>> small = ComputerTasks.packRewardCrates(List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 5)));
        check(small.size() == 1 && count(small.get(0)) == 8, "small claims share one crate");
        List<ItemStack> stacks = List.of(new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64),
                new ItemStack(Items.STONE, 58));
        List<List<ItemStack>> crates = ComputerTasks.packRewardCrates(stacks);
        check(crates.size() == 3, "claims over the crate limit spill into more crates");
        check(count(crates.get(0)) == ComputerTasks.CRATE_ITEM_LIMIT && count(crates.get(1)) == ComputerTasks.CRATE_ITEM_LIMIT,
                "full crates hold exactly the limit");
        check(crates.stream().mapToInt(TaskPinsAndClaimsTest::count).sum() == 250, "no items are lost while packing");
        check(stacks.get(0).getCount() == 64, "packing doesn't change the original stacks");
    }

    private static void requestsReachServer() {
        List<AnternetComputerPayload> sent = new java.util.ArrayList<>();
        ComputerNetworking.setSender(payload -> {
            if (payload instanceof AnternetComputerPayload computer) sent.add(computer);
        });
        try {
            ComputerNetworking.claimAllTaskRewards(java.util.Map.of(task("a"), java.util.Map.of()));
            check(sent.size() == 1 && sent.get(0).action() == ComputerAccessPayload.TASK_CLAIM_ALL
                    && sent.get(0).value().contains(task("a").toString()), "claim all requests are sent to the server");
            ComputerNetworking.setTaskPinned(task("a"), true);
            check(sent.size() == 2 && sent.get(1).action() == ComputerAccessPayload.TASK_PIN, "pin requests are sent to the server");
        } finally {
            ComputerNetworking.setSender(payload -> { });
        }
    }

    private static int count(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }

    private static ResourceLocation task(String path) {
        return ResourceLocation.fromNamespaceAndPath("antos", "test/" + path);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
