package com.craisinlord.antos.content.antmail;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class AntmailDebugCommands {
    private AntmailDebugCommands() { }

    


 
    private static void registerSmokeCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (System.getenv("ANTOS_SMOKE") == null) return;
        try {
            Class.forName("com.craisinlord.antos.content.antmail.AntmailSmoke").getMethod("register", CommandDispatcher.class).invoke(null, dispatcher);
        } catch (ReflectiveOperationException | LinkageError exception) {
            com.craisinlord.antos.AntOS.LOGGER.warn("ANTOS_SMOKE is set but the smoke harness is not on the classpath", exception);
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("antos").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("mail")
                        .then(Commands.literal("trigger")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .then(Commands.argument("value", StringArgumentType.word())
                                                        .executes(AntmailDebugCommands::trigger)))))
                        .then(Commands.literal("send")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("message", IdentifierArgument.id()).suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(AntmailEventData.definitionIds().stream()
                                                        .map(Identifier::toString), builder))
                                                .executes(AntmailDebugCommands::send))))));
        dispatcher.register(Commands.literal("antos").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("takeover")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", IdentifierArgument.id()).suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(AntmailEventData.takeoverIds().stream()
                                                        .map(Identifier::toString), builder))
                                        .executes(AntmailDebugCommands::takeover)))));
        registerSmokeCommands(dispatcher);
    }

    private static int takeover(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        Identifier id = IdentifierArgument.getId(context, "id");
        if (AntmailEventData.takeover(id) == null) {
            throw new SimpleCommandExceptionType(Component.literal("That takeover is not loaded.")).create();
        }
        java.util.UUID profile = com.craisinlord.antos.content.computer.ComputerDesktopEffects.accountFor(player);
        if (profile == null) {
            throw new SimpleCommandExceptionType(Component.literal(player.getGameProfile().name() + " has no AntOS profile.")).create();
        }
        boolean queued = com.craisinlord.antos.content.computer.ComputerDesktopEffects.applyToAccount(player.level().getServer(), profile, null, id);
        context.getSource().sendSuccess(() -> Component.literal(queued
                ? "Queued takeover " + id + " for " + player.getGameProfile().name() + "; it shows the next time they open AntOS."
                : "Takeover " + id + " is already queued for " + player.getGameProfile().name() + "."), true);
        return 1;
    }

    private static int trigger(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String type = StringArgumentType.getString(context, "type");
        String value = StringArgumentType.getString(context, "value");
        int count = AntmailEventData.triggerEvent(player, type, value);
        context.getSource().sendSuccess(() -> Component.literal(count == 0
                ? "No Antmail definitions match " + type + " " + value + "."
                : "Triggered " + count + " matching Antmail definition(s) for " + player.getGameProfile().name() + "."), true);
        return count;
    }

    private static int send(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        Identifier id = IdentifierArgument.getId(context, "message");
        if (AntmailEventData.definition(id.toString()) == null) {
            throw new SimpleCommandExceptionType(Component.literal("That Antmail definition is not loaded.")).create();
        }
        AntmailDeliveryResult.Status status = AntmailEventData.sendDefinition(player, id);
        if (status != AntmailDeliveryResult.Status.DELIVERED && status != AntmailDeliveryResult.Status.QUEUED) {
            throw new SimpleCommandExceptionType(Component.literal("Could not send Antmail definition " + id + " (" + status + ").")).create();
        }
        context.getSource().sendSuccess(() -> Component.literal("Sent Antmail definition " + id + " to " + player.getGameProfile().name() + " (requirements bypassed)."), true);
        return 1;
    }
}
