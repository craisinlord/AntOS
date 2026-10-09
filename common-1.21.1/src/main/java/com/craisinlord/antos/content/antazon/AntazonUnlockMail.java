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
        if (!AntOSSettings.sendAntazonUnlockEmail()) return;
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

        List<ListingLine> buyLines = AntazonData.products().stream()
                .filter(product -> product.enabled() && product.notifyOnUnlock())
                .filter(product -> unlocked(product.unlockTasks(), product.unlockMode(), progress))
                .filter(product -> !AntOSSettings.hasAntazonUnlockEmail(account.accountId(), "buy:" + product.id()))
                .map(product -> new ListingLine("buy:" + product.id(), productName(product)))
                .sorted((first, second) -> String.CASE_INSENSITIVE_ORDER.compare(first.name(), second.name()))
                .toList();
        List<ListingLine> sellLines = AntazonSellData.rules().stream()
                .filter(AntazonSellData.Rule::notifyOnUnlock)
                .filter(rule -> unlocked(rule.unlockTasks(), rule.unlockMode(), progress))
                .filter(rule -> !AntOSSettings.hasAntazonUnlockEmail(account.accountId(), "sell:" + rule.item()))
                .map(rule -> new ListingLine("sell:" + rule.item(), new ItemStack(BuiltInRegistries.ITEM.get(rule.item())).getHoverName().getString()))
                .sorted((first, second) -> String.CASE_INSENSITIVE_ORDER.compare(first.name(), second.name()))
                .toList();
        if (buyLines.isEmpty() && sellLines.isEmpty()) return;

        String taskName = Component.translatable(task.titleKey()).getString();
        StringBuilder body = new StringBuilder("Good work, ").append(player.getName().getString())
                .append("! Completing ").append(taskName).append(" got you access to new Antazon listings.\n\n");
        appendSection(body, "NOW AVAILABLE TO BUY", buyLines.stream().map(ListingLine::name).toList());
        appendSection(body, "NOW AVAILABLE TO SELL", sellLines.stream().map(ListingLine::name).toList());
        body.append("Need something? Put it in your shipping chest and send it our way. ")
                .append("Antazon appreciates your business and your increasingly useful pile of supplies.\n\n")
                .append("Antazon: Ant-icipate great deals.");

        String subject = "New listings unlocked: " + taskName;
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
            AntOSSettings.markAntazonUnlockEmailsSent(account.accountId(), delivered);
        }
    }

    private static boolean unlocked(List<ResourceLocation> tasks, String mode, ComputerTaskProgress progress) {
        if (tasks.isEmpty()) return false;
        boolean any = "any".equals(mode);
        for (ResourceLocation task : tasks) {
            boolean complete = progress.isComplete(task);
            if (any && complete) return true;
            if (!any && !complete) return false;
        }
        return !any;
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
