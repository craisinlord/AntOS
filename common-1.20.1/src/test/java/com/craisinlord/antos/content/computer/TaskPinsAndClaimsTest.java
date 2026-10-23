package com.craisinlord.antos.content.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import com.craisinlord.antos.content.network.AnternetComputerPayload;
import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerNetworking;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public final class TaskPinsAndClaimsTest {
    public static void main(String[] args) throws Exception {
        pins();
        requestsReachServer();
        System.out.println("TaskPinsAndClaimsTest passed");
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

        CompoundTag saved = data.save(new CompoundTag());
        Method load = ComputerWorkspaceData.class.getDeclaredMethod("load", CompoundTag.class);
        load.setAccessible(true);
        ComputerWorkspaceData reloaded = (ComputerWorkspaceData) load.invoke(null, saved);
        check(reloaded.pinnedTasks(account).equals(List.of(ids.get(0), ids.get(2), ids.get(3), ids.get(4), ids.get(5))), "pins survive a save in order");
        for (ResourceLocation id : reloaded.pinnedTasks(account)) reloaded.setTaskPinned(account, id, false);
        CompoundTag empty = reloaded.save(new CompoundTag());
        ComputerWorkspaceData cleared = (ComputerWorkspaceData) load.invoke(null, empty);
        check(cleared.pinnedTasks(account).isEmpty(), "unpinning everything leaves nothing behind");
    }

    private static ResourceLocation task(String path) {
        return new ResourceLocation("antos", "test/" + path);
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
