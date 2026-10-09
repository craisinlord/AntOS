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
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class AntmailDebugCommands {
    private AntmailDebugCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("antos").requires(source -> source.hasPermission(2))
                .then(Commands.literal("mail")
                        .then(Commands.literal("trigger")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .then(Commands.argument("value", StringArgumentType.word())
                                                        .executes(AntmailDebugCommands::trigger)))))
                        .then(Commands.literal("send")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("message", ResourceLocationArgument.id()).suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(AntmailEventData.definitionIds().stream()
                                                        .map(ResourceLocation::toString), builder))
                                                .executes(AntmailDebugCommands::send))))));
    }

    private static int trigger(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String type = StringArgumentType.getString(context, "type");
        String value = StringArgumentType.getString(context, "value");
        int count = AntmailEventData.triggerEvent(player, type, value);
        context.getSource().sendSuccess(() -> Component.literal(count == 0
                ? "No Antmail definitions match " + type + " " + value + "."
                : "Triggered " + count + " matching Antmail definition(s) for " + player.getGameProfile().getName() + "."), true);
        return count;
    }

    private static int send(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation id = ResourceLocationArgument.getId(context, "message");
        if (AntmailEventData.definition(id.toString()) == null) {
            throw new SimpleCommandExceptionType(Component.literal("That Antmail definition is not loaded.")).create();
        }
        AntmailDeliveryResult.Status status = AntmailEventData.sendDefinition(player, id);
        if (status != AntmailDeliveryResult.Status.DELIVERED && status != AntmailDeliveryResult.Status.QUEUED) {
            throw new SimpleCommandExceptionType(Component.literal("Could not send Antmail definition " + id + " (" + status + ").")).create();
        }
        context.getSource().sendSuccess(() -> Component.literal("Sent Antmail definition " + id + " to " + player.getGameProfile().getName() + " (requirements bypassed)."), true);
        return 1;
    }
}
