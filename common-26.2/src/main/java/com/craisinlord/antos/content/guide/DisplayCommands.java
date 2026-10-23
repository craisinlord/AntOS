package com.craisinlord.antos.content.guide;

import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class DisplayCommands {
    private record Subscription(String id, long revision) { }
    private static final Map<UUID, Subscription> previews = new HashMap<>();
    private static MinecraftServer activeServer;
    private static long datapackRevision = -1;
    private DisplayCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ArchiveLookupCommands.register(dispatcher);
        dispatcher.register(Commands.literal("antos").then(Commands.literal("display").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("preview").then(Commands.argument("id", IdentifierArgument.id()).suggests((context, builder) -> SharedSuggestionProvider.suggest(CustomDisplays.sourceIds(), builder)).executes(context -> {
                    preview(context.getSource().getPlayerOrException(), IdentifierArgument.getId(context, "id").toString()); return 1;
                })))
                .then(Commands.literal("capture").then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> DisplayCapture.capture(context.getSource(),
                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(context, "from"), net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(context, "to"),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name")))))))
                .then(Commands.literal("validate").executes(context -> validate(context.getSource(), null, false))
                        .then(Commands.argument("id", IdentifierArgument.id()).executes(context -> validate(context.getSource(), IdentifierArgument.getId(context, "id").toString(), false))))
                .then(Commands.literal("report").executes(context -> validate(context.getSource(), null, true))
                        .then(Commands.argument("id", IdentifierArgument.id()).executes(context -> validate(context.getSource(), IdentifierArgument.getId(context, "id").toString(), true))))));
    }

    private static int validate(CommandSourceStack source, String id, boolean machine) {
        DisplayRecipes.refresh(source.getServer());
        JsonArray report = new JsonArray();
        CustomDisplays.diagnostics().forEach((key, values) -> { if (id == null || id.equals(key)) for (var value : values) report.add(value.json()); });
        if (machine) {
            JsonObject output = new JsonObject(); JsonArray shown = new JsonArray();
            for (int i = 0; i < Math.min(128, report.size()); i++) shown.add(report.get(i));
            output.add("diagnostics", shown); output.addProperty("total", report.size()); output.addProperty("omitted", Math.max(0, report.size() - shown.size()));
            source.sendSuccess(() -> Component.literal(output.toString()), false);
        }
        else {
            source.sendSuccess(() -> Component.literal("Display validation: " + report.size() + " diagnostics, " + CustomDisplays.ids().size() + " compiled definitions"), false);
            for (int i = 0; i < Math.min(24, report.size()); i++) {
                JsonObject diagnostic = report.get(i).getAsJsonObject();
                source.sendSuccess(() -> Component.literal(diagnostic.get("severity").getAsString() + " " + diagnostic.get("source").getAsString() + " " + diagnostic.get("path").getAsString() + ": " + diagnostic.get("message").getAsString()), false);
            }
            if (report.size() > 24) source.sendSuccess(() -> Component.literal("Additional diagnostics omitted; use /antos display report"), false);
        }
        return report.size();
    }

    public static void preview(ServerPlayer player, String id) {
        if (!player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) return;
        if (id.isEmpty()) { previews.remove(player.getUUID()); return; }
        if (net.minecraft.resources.Identifier.tryParse(id) == null) return;
        DisplayRecipes.refresh(player.level().getServer());
        JsonObject response = new JsonObject(); response.addProperty("id", id); response.addProperty("revision", CustomDisplays.revision());
        JsonObject document = CustomDisplays.get(id);
        if (document != null) response.add("document", document);
        JsonArray report = new JsonArray();
        for (var diagnostic : CustomDisplays.diagnostics().getOrDefault(id, List.of())) report.add(diagnostic.json());
        if (document == null && report.isEmpty()) report.add(new DisplayFeatures.Diagnostic("error", id, "$", "Display missing, template requires values, or definition failed compilation").json());
        response.add("diagnostics", report);
        com.craisinlord.antos.content.network.ComputerAccessHandler.sendDisplayPreview(player, response.toString());
        previews.put(player.getUUID(), new Subscription(id, CustomDisplays.revision()));
    }

    public static void tick(MinecraftServer server) {
        if (activeServer != server) { activeServer = server; datapackRevision = -1; previews.clear(); }
        boolean reload = datapackRevision != ComputerGuideData.datapackRevision();
        datapackRevision = ComputerGuideData.datapackRevision();
        DisplayRecipes.refresh(server);
        if (reload) for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
            if (account != null) com.craisinlord.antos.content.network.ComputerAccessHandler.sendArchive(player, new com.craisinlord.antos.content.computer.AccountWorkspace(player, account));
        }
        for (var entry : new ArrayList<>(previews.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) { previews.remove(entry.getKey()); continue; }
            if (entry.getValue().revision != CustomDisplays.revision()) preview(player, entry.getValue().id);
        }
    }

    public static void serverStopped() { previews.clear(); activeServer = null; datapackRevision = -1; DisplayRecipes.serverStopped(); CustomDisplays.serverStopped(); }
}
