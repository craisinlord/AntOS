package com.craisinlord.antos.content.computer.terminal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TerminalCommandService {
    private static final int MAX_ARGUMENT_LENGTH = 128;
    private static final List<String> COMMANDS = List.of(
            "help", "clear", "pwd", "ls", "cd", "mkdir", "touch", "cat", "write", "rm", "rmdir", "mv", "open", "decrypt"
    );

    public TerminalResult execute(TerminalFileSystem fileSystem, String workingDirectory, String input) {
        String currentDirectory = normalizeWorkingDirectory(fileSystem, workingDirectory);
        TerminalCommandParser.ParseResult parsed = TerminalCommandParser.parse(input);
        if (!parsed.isSuccess()) {
            return TerminalResult.error(currentDirectory, parsed.error());
        }
        TerminalCommand command = parsed.command();
        if (command.arguments().stream().anyMatch(argument -> argument.length() > MAX_ARGUMENT_LENGTH)) {
            return TerminalResult.error(currentDirectory, TerminalText.of("error.argument_too_long"));
        }
        return switch (command.name().toLowerCase(Locale.ROOT)) {
            case "help" -> help(currentDirectory, command);
            case "clear" -> requireArguments(currentDirectory, command, 0, TerminalResult.clear(currentDirectory));
            case "pwd" -> requireArguments(currentDirectory, command, 0, TerminalResult.success(currentDirectory, currentDirectory));
            case "ls" -> list(fileSystem, currentDirectory, command);
            case "cd" -> changeDirectory(fileSystem, currentDirectory, command);
            case "mkdir" -> createDirectory(fileSystem, currentDirectory, command);
            case "touch" -> createFile(fileSystem, currentDirectory, command);
            case "cat" -> readFile(fileSystem, currentDirectory, command);
            case "write" -> writeFile(fileSystem, currentDirectory, command);
            case "rm" -> removeFile(fileSystem, currentDirectory, command);
            case "rmdir" -> removeDirectory(fileSystem, currentDirectory, command);
            case "mv" -> move(fileSystem, currentDirectory, command);
            case "open" -> open(fileSystem, currentDirectory, command);
            default -> TerminalResult.error(currentDirectory, TerminalText.of("error.invalid_command", command.name()));
        };
    }

    private TerminalResult help(String workingDirectory, TerminalCommand command) {
        if (command.arguments().isEmpty()) {
            List<String> lines = new ArrayList<>();
            lines.add(TerminalText.of("help.header"));
            for (String name : COMMANDS) lines.add(TerminalText.of("help.row", name));
            return new TerminalResult(TerminalResult.Status.SUCCESS, lines, workingDirectory, false, null);
        }
        if (command.arguments().size() != 1) {
            return usage(workingDirectory, "help");
        }
        String name = command.arguments().get(0).toLowerCase(Locale.ROOT);
        if (!COMMANDS.contains(name)) return TerminalResult.error(workingDirectory, TerminalText.of("error.no_help", command.arguments().get(0)));
        return TerminalResult.success(workingDirectory, TerminalText.of("help.row", name));
    }

    private TerminalResult list(TerminalFileSystem fileSystem, String workingDirectory, TerminalCommand command) {
        if (command.arguments().size() > 1) {
            return usage(workingDirectory, "ls");
        }
        String path = command.arguments().isEmpty() ? workingDirectory : command.arguments().get(0);
        Optional<TerminalFileSystem.Entry> entry = find(fileSystem, path, workingDirectory);
        if (entry.isEmpty()) {
            return missing(workingDirectory, path);
        }
        if (!entry.get().isDirectory()) {
            return TerminalResult.success(workingDirectory, entry.get().name());
        }
        List<TerminalFileSystem.Entry> entries = new ArrayList<>(fileSystem.list(path, workingDirectory));
        entries.sort(Comparator.comparing(TerminalFileSystem.Entry::isDirectory).reversed().thenComparing(TerminalFileSystem.Entry::name));
        if (entries.isEmpty()) {
            return TerminalResult.success(workingDirectory, TerminalText.of("ls.empty"));
        }
        return new TerminalResult(TerminalResult.Status.SUCCESS, entries.stream()
                .map(value -> TerminalText.of(value.isDirectory() ? "ls.directory" : "ls.file", value.name())).toList(), workingDirectory, false, null);
    }

    private TerminalResult changeDirectory(TerminalFileSystem fileSystem, String workingDirectory, TerminalCommand command) {
        if (command.arguments().size() > 1) {
            return usage(workingDirectory, "cd");
        }
        String path = command.arguments().isEmpty() ? TerminalFileSystem.ROOT_PATH : command.arguments().get(0);
        Optional<TerminalFileSystem.Entry> entry = find(fileSystem, path, workingDirectory);
        if (entry.isEmpty() || !entry.get().isDirectory()) {
            return missing(workingDirectory, path);
        }
        return TerminalResult.success(entry.get().path(), entry.get().path());
    }

    private TerminalResult createDirectory(TerminalFileSystem fileSystem, String workingDirectory, TerminalCommand command) {
        if (command.arguments().size() != 1) {
            return usage(workingDirectory, "mkdir");
        }
        return fileSystem.createDirectory(command.arguments().get(0), workingDirectory)
                ? TerminalResult.success(workingDirectory, TerminalText.of("mkdir.success", displayPath(fileSystem, command.arguments().get(0), workingDirectory)))
                : TerminalResult.error(workingDirectory, TerminalText.of("mkdir.failed"));
    }

    private TerminalResult createFile(TerminalFileSystem fileSystem, String workingDirectory, TerminalCommand command) {
        if (command.arguments().size() != 1) {
            return usage(workingDirectory, "touch");
        }
        return fileSystem.createFile(command.arguments().get(0), workingDirectory)
                ? TerminalResult.success(workingDirectory, TerminalText.of("touch.success", displayPath(fileSystem, command.arguments().get(0), workingDirectory)))
                : TerminalResult.error(workingDirectory, TerminalText.of("touch.failed"));
    }

    private TerminalResult readFile(TerminalFileSystem fileSystem, String workingDirectory, TerminalCommand command) {
        if (command.arguments().size() != 1) {
            return usage(workingDirectory, "cat");
        }
        Optional<String> contents = fileSystem.readText(command.arguments().get(0), workingDirectory);
        return contents.map(value -> TerminalResult.success(workingDirectory, value.split("\\R", -1)))
                .orElseGet(() -> missing(workingDirectory, command.arguments().get(0)));
    }

    private TerminalResult writeFile(TerminalFileSystem fileSystem, String workingDirectory, TerminalCommand command) {
        if (command.arguments().size() < 1 || command.arguments().size() > 2) {
            return usage(workingDirectory, "write");
        }
        String contents = command.arguments().size() == 2 ? command.arguments().get(1) : "";
        return fileSystem.writeText(command.arguments().get(0), workingDirectory, contents)
                ? TerminalResult.success(workingDirectory, TerminalText.of("write.success", displayPath(fileSystem, command.arguments().get(0), workingDirectory)))
                : TerminalResult.error(workingDirectory, TerminalText.of("write.failed"));
    }

    private TerminalResult removeFile(TerminalFileSystem fileSystem, String workingDirectory, TerminalCommand command) {
        if (command.arguments().size() != 1) {
            return usage(workingDirectory, "rm");
        }
        return fileSystem.deleteFile(command.arguments().get(0), workingDirectory)
                ? TerminalResult.success(workingDirectory, TerminalText.of("rm.success", displayPath(fileSystem, command.arguments().get(0), workingDirectory)))
                : TerminalResult.error(workingDirectory, TerminalText.of("rm.failed"));
    }

    private TerminalResult removeDirectory(TerminalFileSystem fileSystem, String workingDirectory, TerminalCommand command) {
        if (command.arguments().size() != 1) {
            return usage(workingDirectory, "rmdir");
        }
        return fileSystem.deleteEmptyDirectory(command.arguments().get(0), workingDirectory)
                ? TerminalResult.success(workingDirectory, TerminalText.of("rmdir.success", displayPath(fileSystem, command.arguments().get(0), workingDirectory)))
                : TerminalResult.error(workingDirectory, TerminalText.of("rmdir.failed"));
    }

    private TerminalResult move(TerminalFileSystem fileSystem, String workingDirectory, TerminalCommand command) {
        if (command.arguments().size() != 2) {
            return usage(workingDirectory, "mv");
        }
        return fileSystem.move(command.arguments().get(0), command.arguments().get(1), workingDirectory)
                ? TerminalResult.success(workingDirectory, TerminalText.of("mv.success", displayPath(fileSystem, command.arguments().get(0), workingDirectory),
                        displayPath(fileSystem, command.arguments().get(1), workingDirectory)))
                : TerminalResult.error(workingDirectory, TerminalText.of("mv.failed"));
    }

    private String displayPath(TerminalFileSystem fileSystem, String path, String workingDirectory) {
        try {
            return fileSystem.normalize(path, workingDirectory);
        } catch (RuntimeException ignored) {
            return path;
        }
    }

    private TerminalResult open(TerminalFileSystem fileSystem, String workingDirectory, TerminalCommand command) {
        if (command.arguments().size() != 1) {
            return usage(workingDirectory, "open");
        }
        Optional<TerminalFileSystem.Entry> entry = find(fileSystem, command.arguments().get(0), workingDirectory);
        if (entry.isEmpty() || entry.get().isDirectory()) {
            return missing(workingDirectory, command.arguments().get(0));
        }
        return TerminalResult.open(workingDirectory, entry.get().path());
    }

    private Optional<TerminalFileSystem.Entry> find(TerminalFileSystem fileSystem, String path, String workingDirectory) {
        try {
            return fileSystem.find(path, workingDirectory);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String normalizeWorkingDirectory(TerminalFileSystem fileSystem, String workingDirectory) {
        try {
            String normalized = fileSystem.normalize(workingDirectory, TerminalFileSystem.ROOT_PATH);
            Optional<TerminalFileSystem.Entry> entry = fileSystem.find(normalized, TerminalFileSystem.ROOT_PATH);
            return entry.filter(TerminalFileSystem.Entry::isDirectory).map(TerminalFileSystem.Entry::path).orElse(TerminalFileSystem.ROOT_PATH);
        } catch (RuntimeException ignored) {
            return TerminalFileSystem.ROOT_PATH;
        }
    }

    private TerminalResult missing(String workingDirectory, String path) {
        return TerminalResult.error(workingDirectory, TerminalText.of("error.path_not_found", path));
    }

    private TerminalResult usage(String workingDirectory, String command) {
        return TerminalResult.error(workingDirectory, TerminalText.of("error.usage", TerminalText.PREFIX + "help.usage." + command));
    }

    private TerminalResult requireArguments(String workingDirectory, TerminalCommand command, int count, TerminalResult result) {
        return command.arguments().size() == count ? result : usage(workingDirectory, command.name());
    }
}


