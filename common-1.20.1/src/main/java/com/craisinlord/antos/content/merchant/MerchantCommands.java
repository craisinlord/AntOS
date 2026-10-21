package com.craisinlord.antos.content.merchant;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

public final class MerchantCommands {
    private MerchantCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("antos").requires(source -> source.hasPermission(2))
                .then(Commands.literal("merchant")
                        .then(Commands.literal("status").executes(context -> perform(context.getSource(), () -> MerchantService.report(context.getSource().getServer()))))
                        .then(Commands.literal("recover").then(Commands.argument("player", EntityArgument.player()).executes(context -> perform(context.getSource(), () -> {
                            MerchantService.resume(EntityArgument.getPlayer(context, "player"));
                            return "MERCHANT recovery completed.";
                        }))))
                        .then(Commands.literal("return").then(Commands.argument("listing", StringArgumentType.word()).executes(context -> perform(context.getSource(), () -> {
                            MerchantService.returnListing(context.getSource().getServer(), StringArgumentType.getString(context, "listing"));
                            return "Unsold stock returned to the seller's delivery queue.";
                        }))))));
    }

    private static int perform(CommandSourceStack source, Operation operation) {
        try {
            String result = operation.run();
            source.sendSuccess(() -> Component.literal(result), false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("MERCHANT: " + exception.getMessage()));
            return 0;
        }
    }

    private interface Operation { String run() throws Exception; }
}
