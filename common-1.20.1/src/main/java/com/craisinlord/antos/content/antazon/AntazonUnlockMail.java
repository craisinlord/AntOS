package com.craisinlord.antos.content.antazon;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.computer.ComputerTaskCompletionEvents;
import com.craisinlord.antos.content.computer.ComputerTaskProgress;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class AntazonUnlockMail {
    private static final AntmailAddress SENDER = AntmailAddress.ofUsername("antazondispatch");

    private AntazonUnlockMail() { }

    public static void registerListener() {
        ComputerTaskCompletionEvents.register(AntazonUnlockMail::onTaskComplete);
    }

    private static void onTaskComplete(ServerPlayer player, ResourceLocation taskId) {

        ComputerGuideData.Task task = ComputerGuideData.task(taskId);
        if (task == null) return;
        AntmailServerData mailData = AntmailServerData.access(player.server);
        AntmailAddress recipient = mailData.deliveryAddressFor(player);
        var account = mailData.deliveryAccount(player);
        if (recipient == null || account == null) return;
        ComputerWorkspaceData workspaceData = ComputerWorkspaceData.access(player.server);
        UUID workspaceId = workspaceData.activeWorkspace(account.accountId());
        ComputerTaskProgress progress = workspaceData.effectiveProgress(account.accountId(), workspaceData.progress(workspaceId));
        if (!progress.isComplete(taskId)) return;

        notifyUnlock(player, account.accountId(), recipient, progress, taskId, null, null, com.craisinlord.antos.content.guide.TaskMarkup.strip(Component.translatable(task.titleKey()).getString()));
    }

    public static void onDiskInstalled(ServerPlayer player, com.craisinlord.antos.content.computer.ComputerWorkspace workspace, ResourceLocation diskId) {
        var account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        ComputerGuideData.Disk disk = ComputerGuideData.disk(diskId);
        if (account == null || disk == null) return;
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress recipient = data.deliveryAddressFor(player);
        ComputerTaskProgress progress = ComputerWorkspaceData.access(player.server).effectiveProgress(account.accountId(), workspace.taskProgress());
        notifyUnlock(player, account.accountId(), recipient, progress, null, diskId, null, Component.translatable(disk.titleKey()).getString());
    }

    public static void onCommandRun(ServerPlayer player, com.craisinlord.antos.content.computer.ComputerWorkspace workspace, ResourceLocation commandId, String typed) {
        var account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        if (account == null) return;
        AntmailAddress recipient = AntmailServerData.access(player.server).deliveryAddressFor(player);
        ComputerTaskProgress progress = ComputerWorkspaceData.access(player.server).effectiveProgress(account.accountId(), workspace.taskProgress());
        notifyUnlock(player, account.accountId(), recipient, progress, null, null, commandId, typed);
    }

    private static void notifyUnlock(ServerPlayer player, UUID profile, AntmailAddress recipient, ComputerTaskProgress progress,
                                     ResourceLocation completedTask, ResourceLocation installedDisk, ResourceLocation ranCommand, String triggerName) {
        AntazonDeals.invalidate(profile);
        if (!AntOSSettings.sendAntazonUnlockEmail() || recipient == null) return;
        AntmailServerData mailData = AntmailServerData.access(player.server);
        AntazonServerData antazonData = AntazonServerData.access(player.server);
        if (!antazonData.legacyUnlockMailImported()) {
            ComputerWorkspaceData accounts = ComputerWorkspaceData.access(player.server);
            antazonData.importLegacyUnlockMail(AntOSSettings.legacyAntazonUnlockEmails(), account -> accounts.account(account) != null);
        }
        List<ResourceLocation> collected = ComputerWorkspaceData.access(player.server).activatedDisks(profile);
        com.craisinlord.antos.content.computer.DiskProfileState state = ComputerWorkspaceData.access(player.server).diskState(profile);
        List<ListingLine> buyLines = AntazonData.products().stream()
                .filter(product -> product.enabled() && product.notifyOnUnlock())
                .filter(product -> newlyUnlocked(product.unlockTasks(), product.unlockDisks(), product.unlockCommands(), product.unlockMode(), progress, collected, state, completedTask, installedDisk, ranCommand))
                .filter(product -> !antazonData.unlockMailSent(profile, "buy:" + product.id()))
                .map(product -> new ListingLine("buy:" + product.id(), productName(product)))
                .sorted((first, second) -> String.CASE_INSENSITIVE_ORDER.compare(first.name(), second.name())).toList();
        List<ListingLine> sellLines = AntazonSellData.rules().stream()
                .filter(AntazonSellData.Rule::notifyOnUnlock)
                .filter(rule -> newlyUnlocked(rule.unlockTasks(), rule.unlockDisks(), rule.unlockCommands(), rule.unlockMode(), progress, collected, state, completedTask, installedDisk, ranCommand))
                .filter(rule -> !antazonData.unlockMailSent(profile, "sell:" + rule.item()))
                .map(rule -> new ListingLine("sell:" + rule.item(), new ItemStack(BuiltInRegistries.ITEM.get(rule.item())).getHoverName().getString()))
                .sorted((first, second) -> String.CASE_INSENSITIVE_ORDER.compare(first.name(), second.name())).toList();
        if (buyLines.isEmpty() && sellLines.isEmpty()) return;
        StringBuilder body = new StringBuilder("Good work, ").append(player.getName().getString())
                .append("! ").append(completedTask != null ? "Completing " : ranCommand != null ? "Running " : "Installing ").append(triggerName)
                .append(" got you access to new Antazon listings.\n\n");
        appendSection(body, "NOW AVAILABLE TO BUY", buyLines.stream().map(ListingLine::name).toList());
        appendSection(body, "NOW AVAILABLE TO SELL", sellLines.stream().map(ListingLine::name).toList());
        body.append("Need something? Put it in your shipping chest and send it our way. ")
                .append("Antazon appreciates your business and your increasingly useful pile of supplies.\n\n")
                .append("Antazon: Ant-icipate great deals.");

        String subject = "New listings unlocked: " + triggerName;
        if (subject.length() > com.craisinlord.antos.content.antmail.AntmailValidation.MAX_SUBJECT_LENGTH) {
            subject = subject.substring(0, com.craisinlord.antos.content.antmail.AntmailValidation.MAX_SUBJECT_LENGTH);
        }
        AntmailMessage message = AntmailMessage.create(SENDER, recipient, subject,
                body.toString(), player.server.overworld().getGameTime(), List.of());
        message.setPresentation("", "Antazon Dispatch", false, "");
        var result = mailData.deliver(player.server, message);
        if (result.status() == com.craisinlord.antos.content.antmail.AntmailDeliveryResult.Status.DELIVERED) {
            Set<String> delivered = new LinkedHashSet<>();
            buyLines.forEach(line -> delivered.add(line.id()));
            sellLines.forEach(line -> delivered.add(line.id()));
            antazonData.markUnlockMailSent(profile, delivered);
        }
    }

    public static boolean newlyUnlocked(List<ResourceLocation> tasks, List<ResourceLocation> disks, String mode,
                                        ComputerTaskProgress progress, List<ResourceLocation> collected,
                                        ResourceLocation completedTask, ResourceLocation installedDisk) {
        return newlyUnlocked(tasks, disks, List.of(), mode, progress, collected, null, completedTask, installedDisk, null);
    }

    public static boolean newlyUnlocked(List<ResourceLocation> tasks, List<ResourceLocation> disks, List<ResourceLocation> commands, String mode,
                                        ComputerTaskProgress progress, List<ResourceLocation> collected, com.craisinlord.antos.content.computer.DiskProfileState state,
                                        ResourceLocation completedTask, ResourceLocation installedDisk, ResourceLocation ranCommand) {
        java.util.function.Predicate<ResourceLocation> ran = command -> state != null && state.commandRun(command);
        boolean after = AntazonUnlocks.test(tasks, disks, commands, mode, progress::isComplete, collected::contains, ran);
        boolean before = AntazonUnlocks.test(tasks, disks, commands, mode,
                task -> !task.equals(completedTask) && progress.isComplete(task),
                disk -> !disk.equals(installedDisk) && collected.contains(disk),
                command -> !command.equals(ranCommand) && ran.test(command));
        return after && !before;
    }
    private static String productName(AntazonData.Product product) {
        if (product.poolEntry() != null) {
            return new ItemStack(BuiltInRegistries.ITEM.get(product.poolEntry().item())).getHoverName().getString();
        }
        return Component.translatable(product.name()).getString();
    }

    private static void appendSection(StringBuilder body, String title, List<String> entries) {
        if (entries.isEmpty()) return;
        body.append(title).append('\n');
        for (String entry : entries) body.append("- ").append(entry).append('\n');
        body.append('\n');
    }

    private record ListingLine(String id, String name) { }
}
