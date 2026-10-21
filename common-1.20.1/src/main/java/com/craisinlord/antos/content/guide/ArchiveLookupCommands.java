package com.craisinlord.antos.content.guide;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.computer.AccountWorkspace;
import com.craisinlord.antos.content.computer.ComputerTasks;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ArchiveLookupCommands {
    private ArchiveLookupCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("antos").then(Commands.literal("lookup")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    return lookup(context.getSource(), player, BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()));
                })
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder))
                        .executes(context -> lookup(context.getSource(), context.getSource().getPlayerOrException(), ResourceLocationArgument.getId(context, "item"))))));
    }

    private static int lookup(CommandSourceStack source, ServerPlayer player, ResourceLocation item) {
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        ComputerWorkspaceData.AccountInfo account = AnternetAccountHandler.session(player);
        if (account == null) account = data.faceIdAccount(player.getUUID());
        if (account == null) {
            source.sendFailure(Component.literal("Look-up for " + item + ": no signed-in or Face ID account, so the key does nothing yet."));
            return 0;
        }
        AccountWorkspace workspace = new AccountWorkspace(player, account);
        if (AntOSSettings.archiveLookupRequiresPhone() && !player.getInventory().hasAnyMatching(stack -> stack.is(AntOSObjects.ANTROID_PHONE.get())))
            source.sendSuccess(() -> Component.literal("No phone in inventory, so the hint is hidden (archiveLookupRequiresPhone is on)."), false);
        var entries = ComputerGuideData.entriesFor(workspace.diskIds(), workspace.taskProgress().unlockedArchiveEntries());
        ArchiveLookupIndex.Match match = ArchiveLookupIndex.build(ComputerGuideData.archiveEntries(), entries).get(item.toString());
        if (match != null) {
            source.sendSuccess(() -> Component.literal("Look-up for " + item + " opens Archive page " + match.entry().id() + " (matched by " + match.field() + ")."), false);
            return 1;
        }
        var tasks = ComputerTasks.lookupTasks(data.effectiveProgress(account.accountId(), workspace.taskProgress()));
        if (tasks.has(item.toString())) {
            String task = tasks.get(item.toString()).getAsString();
            source.sendSuccess(() -> Component.literal("Look-up for " + item + " opens task " + task + " (no unlocked page covers it)."), false);
            return 1;
        }
        long pages = ComputerGuideData.archiveEntries().stream().filter(entry -> ArchiveLookupIndex.build(java.util.List.of(entry), java.util.List.of(entry)).containsKey(item.toString())).count();
        source.sendSuccess(() -> Component.literal("Look-up for " + item + " does nothing: " + (pages > 0 ? pages + " locked page(s) cover it" : "no page covers it") + ", and no open task asks for it."), false);
        return 0;
    }
}
