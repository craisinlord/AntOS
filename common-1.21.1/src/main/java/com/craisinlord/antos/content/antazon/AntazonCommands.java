package com.craisinlord.antos.content.antazon;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class AntazonCommands {
    private AntazonCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("antos").requires(source -> source.hasPermission(2))
                .then(Commands.literal("antazon").then(Commands.literal("deals").executes(context -> {
                    var metrics = AntazonServerData.access(context.getSource().getServer()).dealMetrics();
                    if (metrics.isEmpty()) {
                        context.getSource().sendSuccess(() -> Component.literal("No Antazon orders recorded."), false);
                        return 0;
                    }
                    metrics.forEach((key, value) -> context.getSource().sendSuccess(() -> Component.literal(
                            key + ": " + value.orders() + " orders, " + value.units() + " units, " + value.revenue()
                                    + " paid; deal " + value.dealOrders() + " orders / " + value.dealUnits()
                                    + " units / " + value.dealRevenue() + " paid; regular " + value.regularOrders() + " orders / " + value.regularUnits()
                                    + " units / " + value.regularRevenue() + " paid; "
                                    + (value.dealOrders() == 0 ? 0 : value.discountPercentTotal() / value.dealOrders())
                                    + "% average discount"), false));
                    return metrics.size();
                }))));
    }
}
