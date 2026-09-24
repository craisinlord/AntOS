package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class TaskDebugCommands {
    private static final SimpleCommandExceptionType NOT_COMPUTER = new SimpleCommandExceptionType(
            Component.literal("Sign in to an Anternet account first."));
    private static final SimpleCommandExceptionType UNKNOWN_TASK = new SimpleCommandExceptionType(
            Component.literal("That task ID is not loaded."));

    private TaskDebugCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("antos")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("tasks")
                        .then(Commands.literal("complete")
                                .then(taskArgument("task").executes(TaskDebugCommands::complete)))
                        .then(Commands.literal("grant")
                                .then(taskArgument("task").executes(TaskDebugCommands::grant)))
                        .then(Commands.literal("setprogress")
                                .then(taskArgument("task")
                                        .then(Commands.argument("objective", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    ResourceLocation taskId = ResourceLocationArgument.getId(context, "task");
                                                    return SharedSuggestionProvider.suggest(ComputerGuideData.tasks().stream()
                                                            .filter(task -> task.id().equals(taskId))
                                                            .flatMap(task -> task.objectives().stream())
                                                            .map(ComputerGuideData.Objective::id), builder);
                                                })
                                                .then(Commands.argument("count", IntegerArgumentType.integer(0, 1_000_000))
                                                        .executes(TaskDebugCommands::setProgress)))))
                        .then(Commands.literal("reset").executes(TaskDebugCommands::reset))
                        .then(Commands.literal("inspect")
                                .then(taskArgument("task").executes(TaskDebugCommands::inspect)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> taskArgument(String name) {
        return Commands.argument(name, ResourceLocationArgument.id()).suggests((context, builder) ->
                SharedSuggestionProvider.suggest(ComputerGuideData.tasks().stream()
                        .map(task -> task.id().toString()), builder));
    }

    private static int complete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ComputerWorkspace computer = computer(context);
        ResourceLocation taskId = ResourceLocationArgument.getId(context, "task");
        requireTask(taskId);
        ComputerTasks.updateAndEncode(player, computer);
        if (computer.taskProgress().isComplete(taskId)) {
            context.getSource().sendSuccess(() -> Component.literal("Task already complete: " + taskId), false);
            return 0;
        }
        ComputerTasks.forceCompleteTask(player, computer, taskId);
        context.getSource().sendSuccess(() -> Component.literal("Force-completed task and processed its rewards: " + taskId), false);
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ComputerWorkspace computer = computer(context);
        ResourceLocation taskId = ResourceLocationArgument.getId(context, "task");
        requireTask(taskId);
        boolean changed = ComputerTasks.grantTask(player, computer, taskId);
        context.getSource().sendSuccess(() -> Component.literal((changed ? "Granted task: " : "Task was already granted: ") + taskId), false);
        return changed ? 1 : 0;
    }

    private static int setProgress(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ComputerWorkspace computer = computer(context);
        ResourceLocation taskId = ResourceLocationArgument.getId(context, "task");
        requireTask(taskId);
        String objectiveId = StringArgumentType.getString(context, "objective");
        int count = IntegerArgumentType.getInteger(context, "count");
        if (!ComputerTasks.setEventObjectiveProgress(player, computer, taskId, objectiveId, count)) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Unknown objective, or objective progress is derived from player state; use complete for a forced completion.")).create();
        }
        context.getSource().sendSuccess(() -> Component.literal("Set event objective progress for " + taskId + "/" + objectiveId), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        context.getSource().getPlayerOrException();
        ComputerWorkspace computer = computer(context);
        boolean changed = ComputerTasks.resetTaskProgress(computer);
        context.getSource().sendSuccess(() -> Component.literal(changed
                ? "Cleared task grants, completions, and objective progress. Archive unlocks were preserved."
                : "This computer has no saved task progress to clear."), false);
        return changed ? 1 : 0;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ComputerWorkspace computer = computer(context);
        ResourceLocation taskId = ResourceLocationArgument.getId(context, "task");
        ComputerGuideData.Task task = requireTask(taskId);
        ComputerTasks.updateAndEncode(player, computer);
        ComputerTaskProgress progress = computer.taskProgress();
        String state = progress.isComplete(taskId) ? "COMPLETE" : progress.isGranted(taskId) ? "GRANTED" : "NOT GRANTED";
        context.getSource().sendSuccess(() -> Component.literal(taskId + " // " + state), false);
        for (ComputerGuideData.Objective objective : task.objectives()) {
            int current = progress.objectiveCount(taskId, objective.id());
            context.getSource().sendSuccess(() -> Component.literal("  " + objective.id() + " = " + current + "/" + objective.count()
                    + (objective.optional() ? " (optional)" : "")), false);
        }
        return 1;
    }

    private static ComputerWorkspace computer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        if (account != null) return new AccountWorkspace(player, account);
        throw NOT_COMPUTER.create();
    }

    private static ComputerGuideData.Task requireTask(ResourceLocation id) throws CommandSyntaxException {
        return ComputerGuideData.tasks().stream().filter(task -> task.id().equals(id)).findFirst().orElseThrow(UNKNOWN_TASK::create);
    }
}
