package com.craisinlord.antos.content.antazon;

import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class AntcoinCommands {
    private AntcoinCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("antcoin").requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            long amount = LongArgumentType.getLong(context, "amount");
                                            AntazonServerData data = AntazonServerData.access(context.getSource().getServer());
                                            UUID owner = walletOwner(target);
                                            data.credit(owner, amount);
                                            long balance = data.wallet(owner);
                                            context.getSource().sendSuccess(() -> Component.translatable("computer.antos.currency.command.given", amount, Component.translatable("computer.antos.currency.name"), target.getGameProfile().getName(), balance), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    long balance = AntazonServerData.access(context.getSource().getServer()).wallet(walletOwner(target));
                                    context.getSource().sendSuccess(() -> Component.translatable("computer.antos.currency.command.balance", target.getGameProfile().getName(), balance, Component.translatable("computer.antos.currency.name")), false);
                                    return 1;
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            long amount = LongArgumentType.getLong(context, "amount");
                                            AntazonServerData.access(context.getSource().getServer()).setWallet(walletOwner(target), amount);
                                            context.getSource().sendSuccess(() -> Component.translatable("computer.antos.currency.command.set", target.getGameProfile().getName(), Component.translatable("computer.antos.currency.name"), amount), true);
                                            return 1;
                                        })))));
    }

    private static UUID walletOwner(ServerPlayer player) {
        var account = AnternetAccountHandler.session(player);
        return account == null ? player.getUUID() : account.accountId();
    }
}
