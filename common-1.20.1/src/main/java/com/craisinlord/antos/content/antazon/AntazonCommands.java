package com.craisinlord.antos.content.antazon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AntazonCommands {
    private AntazonCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("antos").requires(source -> source.hasPermission(2))
                .then(Commands.literal("antazon").then(Commands.literal("deals")
                        .executes(AntazonCommands::report)
                        .then(Commands.literal("preview")
                                .executes(context -> preview(context, 3))
                                .then(Commands.argument("periods", IntegerArgumentType.integer(1, 30))
                                        .executes(context -> preview(context, IntegerArgumentType.getInteger(context, "periods"))))))));
    }

    private static int report(CommandContext<CommandSourceStack> context) {
        AntazonServerData data = AntazonServerData.access(context.getSource().getServer());
        var metrics = data.dealMetrics();
        if (metrics.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No Antazon orders recorded."), false);
            return 0;
        }
        data.campaignMetrics().forEach((key, value) -> context.getSource().sendSuccess(() -> Component.literal(
                "Campaign " + key + ": " + value.orders() + " deal orders, " + value.dealUnits() + " deal units, " + value.revenue()
                        + " paid; " + (value.orders() == 0 ? 0 : value.discountPercentTotal() / value.orders()) + "% average discount"), false));
        metrics.forEach((key, value) -> context.getSource().sendSuccess(() -> Component.literal(
                key + ": " + value.orders() + " orders, " + value.units() + " units, " + value.revenue()
                        + " paid; deal " + value.dealOrders() + " orders / " + value.dealUnits()
                        + " units / " + value.dealRevenue() + " paid; regular " + value.regularOrders() + " orders / " + value.regularUnits()
                        + " units / " + value.regularRevenue() + " paid; "
                        + (value.dealOrders() == 0 ? 0 : value.discountPercentTotal() / value.dealOrders())
                        + "% average discount"), false));
        return metrics.size();
    }

    private static int preview(CommandContext<CommandSourceStack> context, int periods) {
        var campaigns = AntazonDealData.campaigns();
        if (campaigns.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No Antazon deal campaigns loaded."), false);
            return 0;
        }
        long gameTime = context.getSource().getServer().overworld().getGameTime();
        long day = gameTime / 24000L;
        long now = System.currentTimeMillis();
        context.getSource().sendSuccess(() -> Component.literal("Deal picks before unlock filtering. Players only get products they have unlocked."), false);
        for (AntazonDealData.Campaign campaign : campaigns) {
            String cadence = campaign.realTime() ? "every " + campaign.everyRealHours() + " real hours" : "every " + campaign.everyMinecraftDays() + " Minecraft days";
            String header = campaign.id() + " (" + (campaign.enabled() ? "" : "disabled, ") + campaign.picks() + " picks "
                    + cadence + ", " + AntazonDeals.candidateCount(campaign) + " candidates)";
            context.getSource().sendSuccess(() -> Component.literal(header), false);
            if (!campaign.enabled()) continue;
            for (int ahead = 0; ahead < periods; ahead++) {
                Map<ResourceLocation, Integer> picks = AntazonDeals.discountsFor(campaign, day, gameTime, now, ahead);
                List<String> parts = new ArrayList<>();
                picks.forEach((key, discount) -> parts.add(key + " -" + discount + "%"));
                String line = (ahead == 0 ? "  now: " : "  +" + ahead + ": ") + (parts.isEmpty() ? "nothing" : String.join(", ", parts));
                context.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        }
        return campaigns.size();
    }
}
