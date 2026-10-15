package com.craisinlord.antos.content.computer.terminal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class TerminalText {
    public static final String PREFIX = "computer.antos.terminal.";
    private static final char MARK = '\u0001';
    private static final char SEPARATOR = '\u0002';
    private static final char STYLE = '\u0003';
    public static final List<String> PLACEHOLDERS = List.of("{player}", "{username}", "{directory}", "{time}", "{day}");

    private TerminalText() {
    }

    public static String of(String key, String... arguments) {
        return raw(PREFIX + key, arguments);
    }

    public static String raw(String fullKey, String... arguments) {
        StringBuilder builder = new StringBuilder().append(MARK).append(fullKey);
        for (String argument : arguments) builder.append(SEPARATOR).append(clean(argument));
        return builder.toString();
    }

    public static String styled(String style, int speed, int delay, String text, List<String> values) {
        StringBuilder builder = new StringBuilder().append(STYLE).append(clean(style)).append(SEPARATOR).append(speed).append(SEPARATOR).append(delay)
                .append(SEPARATOR).append(clean(text));
        for (String value : values) builder.append(SEPARATOR).append(clean(value));
        return builder.toString();
    }

    public static Styled styled(String line) {
        if (line == null || line.isEmpty() || line.charAt(0) != STYLE) return null;
        String[] parts = line.substring(1).split(String.valueOf(SEPARATOR), -1);
        if (parts.length < 4) return null;
        try {
            return new Styled(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3], List.of(parts).subList(4, parts.length));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static String resolve(Styled line) {
        String text = net.minecraft.locale.Language.getInstance().getOrDefault(line.text(), line.text());
        for (int index = 0; index < PLACEHOLDERS.size() && index < line.values().size(); index++) text = text.replace(PLACEHOLDERS.get(index), line.values().get(index));
        return text;
    }

    public static boolean translated(String line) {
        return line != null && !line.isEmpty() && line.charAt(0) == MARK;
    }

    public static String key(String line) {
        if (!translated(line)) return "";
        int end = line.indexOf(SEPARATOR);
        return end < 0 ? line.substring(1) : line.substring(1, end);
    }

    public static List<String> arguments(String line) {
        List<String> arguments = new ArrayList<>();
        if (!translated(line)) return arguments;
        int start = line.indexOf(SEPARATOR);
        while (start >= 0) {
            int next = line.indexOf(SEPARATOR, start + 1);
            arguments.add(next < 0 ? line.substring(start + 1) : line.substring(start + 1, next));
            start = next;
        }
        return arguments;
    }

    public static Component component(String line) {
        Styled styled = styled(line);
        if (styled != null) return Component.literal(resolve(styled));
        if (!translated(line)) return Component.literal(line == null ? "" : line);
        Object[] values = arguments(line).stream()
                .map(argument -> argument.startsWith(PREFIX) ? Component.translatable(argument) : (Object) argument)
                .toArray();
        return Component.translatable(key(line), values);
    }

    public static String plain(String line) {
        return component(line).getString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace(MARK, ' ').replace(SEPARATOR, ' ').replace(STYLE, ' ');
    }

    public record Styled(String style, int speed, int delay, String text, List<String> values) { }
}
