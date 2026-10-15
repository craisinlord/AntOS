package com.craisinlord.antos.content.computer.terminal;

import com.craisinlord.antos.content.antazon.AntazonUnlockMail;
import com.craisinlord.antos.content.computer.ComputerDiskData;
import com.craisinlord.antos.content.computer.ComputerDiskFiles;
import com.craisinlord.antos.content.computer.ComputerTasks;
import com.craisinlord.antos.content.computer.ComputerWorkspace;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.computer.DiskProfileState;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.craisinlord.antos.content.network.ComputerAccessHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class TerminalCommands {
    private TerminalCommands() { }

    public static TerminalResult execute(ServerPlayer player, ComputerWorkspace computer, TerminalFileSystem fileSystem, String directory, String input) {
        TerminalCommandParser.ParseResult parsed = TerminalCommandParser.parse(input);
        if (!parsed.isSuccess()) return null;
        TerminalCommand command = parsed.command();
        Context context = context(player, computer);
        if (command.name().equals("help")) return help(context, fileSystem, directory, command);
        TerminalCommandData.Definition definition = TerminalCommandData.named(command.name()).stream()
                .filter(candidate -> candidate.matches(command.arguments())).findFirst().orElse(null);
        if (definition == null) return null;
        boolean needsProfile = definition.requires().any() || definition.hasEffects();
        if (needsProfile && context == null || !requirementsMet(definition, context, fileSystem, directory)) {
            if (definition.lockedOutput().isEmpty()) return null;
            return new TerminalResult(TerminalResult.Status.SUCCESS, lines(definition.lockedOutput(), player, context, directory), directory, false, null);
        }
        boolean changed = false;
        if (context != null) {
            boolean first = context.state().recordCommand(definition.id());
            changed = first;
            if (first || !definition.once()) {
                apply(player, computer, context, definition);
                changed = true;
            }
            context.data().setDirty();
            if (first) AntazonUnlockMail.onCommandRun(player, computer, definition.id(), input.trim());
            if (changed) ComputerAccessHandler.refreshAfterDisk(player, computer);
        }
        String open = null;
        if (!definition.open().isBlank()) {
            try {
                Optional<TerminalFileSystem.Entry> entry = fileSystem.find(definition.open(), directory);
                if (entry.isPresent() && !entry.get().isDirectory()) open = entry.get().path();
            } catch (RuntimeException ignored) { }
        }
        List<String> output = lines(definition.output(), player, context, directory);
        return new TerminalResult(open == null ? TerminalResult.Status.SUCCESS : TerminalResult.Status.OPEN, output, directory, definition.clear(), open);
    }

    public static List<String> visibleNames(ServerPlayer player, ComputerWorkspace computer) {
        Context context = context(player, computer);
        List<String> names = new ArrayList<>();
        for (TerminalCommandData.Definition definition : TerminalCommandData.definitions().values()) {
            if (definition.hidden() || definition.requires().any() && context == null) continue;
            if (!requirementsMet(definition, context, null, null)) continue;
            for (String name : definition.names()) if (!names.contains(name)) names.add(name);
        }
        names.sort(String::compareTo);
        return List.copyOf(names);
    }

    public static boolean ran(ServerPlayer player, ResourceLocation command) {
        var account = AnternetAccountHandler.session(player);
        return account != null && ComputerWorkspaceData.access(player.server).diskState(account.accountId()).commandRun(command);
    }

    public static String hint(ResourceLocation command) {
        TerminalCommandData.Definition definition = TerminalCommandData.definition(command);
        return definition == null || definition.hint().isBlank() ? TerminalText.PREFIX + "secret_requirement" : definition.hint();
    }

    private static TerminalResult help(Context context, TerminalFileSystem fileSystem, String directory, TerminalCommand command) {
        List<TerminalCommandData.Definition> visible = TerminalCommandData.definitions().values().stream()
                .filter(definition -> !definition.hidden() && (!definition.requires().any() || context != null) && requirementsMet(definition, context, fileSystem, directory))
                .sorted(java.util.Comparator.comparing(TerminalCommandData.Definition::usage)).toList();
        if (command.arguments().isEmpty()) {
            if (visible.isEmpty()) return null;
            TerminalResult builtIn = new TerminalCommandService().execute(fileSystem, directory, "help");
            List<String> lines = new ArrayList<>(builtIn.lines());
            for (TerminalCommandData.Definition definition : visible) lines.add(TerminalText.of("help.custom", definition.usage(), definition.description()));
            return new TerminalResult(TerminalResult.Status.SUCCESS, lines, directory, false, null);
        }
        if (command.arguments().size() != 1) return null;
        String name = command.arguments().get(0).toLowerCase(Locale.ROOT);
        if (TerminalCommandData.BUILT_IN.contains(name)) return null;
        List<String> rows = visible.stream().filter(definition -> definition.names().contains(name))
                .map(definition -> TerminalText.of("help.custom", definition.usage(), definition.description())).toList();
        return rows.isEmpty() ? null : new TerminalResult(TerminalResult.Status.SUCCESS, rows, directory, false, null);
    }

    private static void apply(ServerPlayer player, ComputerWorkspace computer, Context context, TerminalCommandData.Definition definition) {
        DiskProfileState state = context.state();
        definition.tasks().forEach(computer.taskProgress()::grant);
        definition.entries().forEach(computer.taskProgress()::unlockArchiveEntry);
        if (definition.wallpaper() != null && ComputerGuideData.wallpaper(definition.wallpaper()) != null) computer.desktopState().unlockWallpaper(definition.wallpaper());
        if (!definition.files().isEmpty()) ComputerDiskFiles.copy(computer.fileSystem(), state, "terminal:" + definition.id(), "Terminal", definition.command(),
                definition.files(), ComputerDiskData.files(), player.getName().getString(), player.server.overworld().getGameTime());
        definition.subscriptions().forEach(state::subscribe);
        definition.programs().forEach(state::grantProgram);
        computer.saveAccountWorkspace();
        context.data().setDirty();
        if (definition.rewards().isEmpty()) return;
        var receipt = ComputerGuideData.encodeRewards(ComputerTasks.deliverRewards(player, definition.id(), definition.rewards(), Map.of()));
        for (var element : receipt) if (element.getAsJsonObject().has("body")) element.getAsJsonObject().addProperty("body", "");
        if (!receipt.isEmpty()) ComputerAccessHandler.sendRewardNotice(player, context.account(), definition.id(), definition.usage(), receipt);
    }

    private static boolean requirementsMet(TerminalCommandData.Definition definition, Context context, TerminalFileSystem fileSystem, String directory) {
        TerminalCommandData.Requirements requires = definition.requires();
        if (!requires.directory().isBlank() && fileSystem != null && directory != null) {
            try {
                if (!fileSystem.normalize(requires.directory(), "/").equalsIgnoreCase(fileSystem.normalize(directory, "/"))) return false;
            } catch (RuntimeException exception) {
                return false;
            }
        }
        if (requires.disks().isEmpty() && requires.tasks().isEmpty() && requires.commands().isEmpty()) return true;
        if (context == null) return false;
        boolean any = requires.mode().equals("any");
        List<Boolean> results = new ArrayList<>();
        requires.disks().forEach(id -> results.add(context.state().activated(id)));
        requires.tasks().forEach(id -> results.add(ComputerTasks.isTaskComplete(context.player(), context.computer(), id)));
        requires.commands().forEach(id -> results.add(context.state().commandRun(id)));
        return any ? results.contains(true) : !results.contains(false);
    }

    private static List<String> lines(List<TerminalCommandData.Line> lines, ServerPlayer player, Context context, String directory) {
        long dayTime = player.server.overworld().getDayTime();
        long ticks = (dayTime + 6000L) % 24000L;
        String time = String.format(Locale.ROOT, "%02d:%02d", ticks / 1000L, ticks % 1000L * 60L / 1000L);
        List<String> values = List.of(player.getName().getString(), context == null ? player.getName().getString() : context.account().displayUsername(),
                directory, time, String.valueOf(dayTime / 24000L + 1));
        List<String> encoded = new ArrayList<>();
        for (TerminalCommandData.Line line : lines) {
            String text = line.text();
            for (int index = 0; index < TerminalText.PLACEHOLDERS.size(); index++) text = text.replace(TerminalText.PLACEHOLDERS.get(index), values.get(index));
            encoded.add(TerminalText.styled(line.style(), line.speed(), line.delay(), text, values));
        }
        return encoded;
    }

    private static Context context(ServerPlayer player, ComputerWorkspace computer) {
        var account = AnternetAccountHandler.session(player);
        if (account == null || computer == null || !computer.canUseFileSystem(player)
                || !account.accountId().equals(computer.workspaceOwner()) || !account.workspaceId().equals(computer.workspaceId())) return null;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        return new Context(player, computer, account, data, data.diskState(account.accountId()));
    }

    private record Context(ServerPlayer player, ComputerWorkspace computer, ComputerWorkspaceData.AccountInfo account, ComputerWorkspaceData data, DiskProfileState state) { }
}
