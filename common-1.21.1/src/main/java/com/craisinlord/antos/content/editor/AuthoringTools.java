package com.craisinlord.antos.content.editor;

import com.craisinlord.antos.api.task.TaskObjectiveRegistry;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailDeliveryResult;
import com.craisinlord.antos.content.antmail.AntmailEventData;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.computer.AccountWorkspace;
import com.craisinlord.antos.content.computer.ComputerDiskData;
import com.craisinlord.antos.content.computer.ComputerTasks;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.computer.games.ComputerGameRecords;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.item.FloppyDiskItem;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.craisinlord.antos.content.network.ComputerAccessHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class AuthoringTools {
    private AuthoringTools() { }

    static JsonObject references(MinecraftServer server, String type) {
        List<String> values = new ArrayList<>();
        switch (type) {
            case "advancement" -> server.getAdvancements().getAllAdvancements().forEach(holder -> {
                if (!holder.id().getPath().startsWith("recipes/")) values.add(holder.id().toString());
            });
            case "structure" -> keys(server, Registries.STRUCTURE).forEach(values::add);
            case "structure_tag" -> server.registryAccess().registryOrThrow(Registries.STRUCTURE).getTagNames().map(tag -> tag.location().toString()).forEach(values::add);
            case "biome" -> keys(server, Registries.BIOME).forEach(values::add);
            case "biome_tag" -> server.registryAccess().registryOrThrow(Registries.BIOME).getTagNames().map(tag -> tag.location().toString()).forEach(values::add);
            case "dimension" -> server.levelKeys().forEach(key -> values.add(key.location().toString()));
            case "recipe" -> server.getRecipeManager().getRecipeIds().map(ResourceLocation::toString).forEach(values::add);
            case "wallpaper" -> ComputerGuideData.wallpapers().forEach(wallpaper -> values.add(wallpaper.id().toString()));
            case "file" -> ComputerDiskData.files().keySet().forEach(id -> values.add(id.toString()));
            case "game" -> values.addAll(ComputerGameRecords.modes());
            case "objective_type" -> TaskObjectiveRegistry.registeredTypes().forEach(id -> values.add(id.toString()));
            case "display" -> com.craisinlord.antos.content.guide.CustomDisplays.ids().forEach(id -> values.add(id.toString()));
            case "stat" -> server.registryAccess().registryOrThrow(Registries.CUSTOM_STAT).keySet().forEach(id -> values.add(id.toString()));
            case "sender" -> {
                AntmailEventData.definitionIds().forEach(id -> {
                    AntmailEventData.Definition definition = AntmailEventData.definition(id.toString());
                    if (definition != null && !values.contains(definition.sender())) values.add(definition.sender());
                });
            }
            default -> throw new IllegalArgumentException("Unknown reference list " + type);
        }
        if (!type.equals("game")) values.sort(null);
        JsonArray ids = new JsonArray();
        int length = 0;
        boolean cut = false;
        for (String value : values) {
            length += value.length() + 3;
            if (length > 250_000) { cut = true; break; }
            ids.add(value);
        }
        JsonObject result = new JsonObject();
        result.addProperty("type", type);
        result.addProperty("shortened", cut);
        result.add("ids", ids);
        return result;
    }

    private static <T> Stream<String> keys(MinecraftServer server, net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> key) {
        return server.registryAccess().registryOrThrow(key).keySet().stream().map(ResourceLocation::toString);
    }

    static JsonObject run(ServerPlayer player, String operation, JsonObject request) {
        JsonObject result = new JsonObject();
        ResourceLocation id = ResourceLocation.parse(AuthoringValidation.required(request, "id", 240));
        switch (operation) {
            case "give_disk" -> giveDisk(player, id);
            case "reset_disk" -> resetDisk(player, id);
            case "send_test" -> sendTest(player, id, AuthoringValidation.object(request, "body").deepCopy());
            default -> throw new IllegalArgumentException("Unsupported editor operation");
        }
        result.addProperty("id", id.toString());
        return result;
    }

    private static void giveDisk(ServerPlayer player, ResourceLocation id) {
        if (ComputerDiskData.disk(id) == null) throw new IllegalArgumentException("Save and apply this floppy first");
        ItemStack stack = FloppyDiskItem.freshCopy(id);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void resetDisk(ServerPlayer player, ResourceLocation id) {
        ComputerGuideData.Disk disk = ComputerDiskData.disk(id);
        if (disk == null) throw new IllegalArgumentException("Save and apply this floppy first");
        var account = AnternetAccountHandler.session(player);
        if (account == null) throw new IllegalArgumentException("Sign in to AntOS on a computer or phone first, so there is progress to reset");
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        AccountWorkspace workspace = new AccountWorkspace(player, account);
        List<ResourceLocation> tasks = new ArrayList<>(disk.tasks());
        for (int index = 0; index < tasks.size(); index++) {
            ResourceLocation current = tasks.get(index);
            for (ComputerGuideData.Task task : ComputerGuideData.tasks())
                if (!tasks.contains(task.id()) && (task.requires().contains(current) || task.requiresAny().contains(current))) tasks.add(task.id());
        }
        for (ResourceLocation task : tasks) {
            workspace.taskProgress().forgetTask(task);
            data.forgetTaskReward(account.accountId(), player.getUUID(), task);
        }
        for (ResourceLocation entry : disk.entries()) workspace.taskProgress().lockArchiveEntry(entry);
        data.diskState(account.accountId()).forget(disk.id(), disk.features().subscriptions());
        data.forgetDisk(account.accountId(), player.getUUID(), disk.id());
        workspace.saveWorkspaceProgress();
        AntmailServerData mail = AntmailServerData.access(player.server);
        AntmailAddress address = AntmailAddress.ofUsername(account.username());
        mail.forgetTasks(address, tasks);
        for (ResourceLocation message : AntmailEventData.definitionIds()) {
            AntmailEventData.Definition definition = AntmailEventData.definition(message.toString());
            if (definition == null) continue;
            String type = definition.trigger().type();
            String value = definition.trigger().value();
            if (type.equals("disk_installed") && value.equals(id.toString())
                    || type.equals("task_complete") && tasks.stream().anyMatch(task -> task.toString().equals(value))) mail.forgetTrigger(address, message);
        }
        ComputerTasks.invalidateEvaluations();
        ComputerAccessHandler.refreshAfterDisk(player, workspace);
    }

    private static void sendTest(ServerPlayer player, ResourceLocation id, JsonObject body) {
        List<AuthoringIssue> issues = AuthoringChecks.check(AuthoringKind.MESSAGE, body);
        if (issues.stream().anyMatch(issue -> !issue.warning())) throw new IllegalArgumentException("Fix this mail's problems before sending a test");
        AntmailEventData.validateAuthoring(id, body);
        AntmailDeliveryResult.Status status = AntmailEventData.sendTest(player, id, body);
        switch (status) {
            case DELIVERED, QUEUED -> { }
            case ADDRESS_NOT_FOUND -> throw new IllegalArgumentException("Sign in to AntOS on a computer or phone first, so there is an inbox to send to");
            case MAILBOX_FULL -> throw new IllegalArgumentException("Your Antmail inbox is full");
            default -> throw new IllegalArgumentException("The test mail could not be delivered");
        }
    }
}
