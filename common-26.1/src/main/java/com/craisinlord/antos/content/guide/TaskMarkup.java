package com.craisinlord.antos.content.guide;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class TaskMarkup {
    public static final int BASE_COLOR = -1;
    public static final int DEFAULT_ICON_SIZE = 10;
    private static final int MAX_TAG_LENGTH = 32;
    private static final int MAX_ICON_LENGTH = 192;
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("green", 0xFF65FF65), Map.entry("pale", 0xFFB8FFB8), Map.entry("pale_green", 0xFFB8FFB8),
            Map.entry("dim", 0xFF87B787), Map.entry("dark_green", 0xFF3E8E3E), Map.entry("lime", 0xFFA8FF4A),
            Map.entry("red", 0xFFFF5555), Map.entry("dark_red", 0xFFAA2222), Map.entry("orange", 0xFFFFAA33),
            Map.entry("gold", 0xFFFFC94A), Map.entry("yellow", 0xFFFFFF66), Map.entry("aqua", 0xFF55FFFF),
            Map.entry("dark_aqua", 0xFF22AAAA), Map.entry("blue", 0xFF6699FF), Map.entry("dark_blue", 0xFF3344CC),
            Map.entry("purple", 0xFFBB66FF), Map.entry("dark_purple", 0xFF8833AA), Map.entry("pink", 0xFFFF77CC),
            Map.entry("light_purple", 0xFFFF77FF), Map.entry("white", 0xFFFFFFFF), Map.entry("gray", 0xFFAAAAAA),
            Map.entry("grey", 0xFFAAAAAA), Map.entry("dark_gray", 0xFF666666), Map.entry("dark_grey", 0xFF666666),
            Map.entry("black", 0xFF101010));

    private TaskMarkup() { }

    public sealed interface Token permits Text, Icon, Break { }

    public record Style(int color, boolean bold, boolean italic, boolean underline, boolean strikethrough) {
        public static final Style BASE = new Style(BASE_COLOR, false, false, false, false);
    }

    public record Text(String text, Style style) implements Token { }

    public record Icon(String type, String id, Boolean greenTint, int size) implements Token { }

    public record Break() implements Token { }

    private record Frame(String name, Style previous) { }

    public static boolean hasMarkup(String input) {
        return input != null && (input.indexOf('<') >= 0 || input.indexOf('{') >= 0);
    }

    public static String strip(String input) {
        if (!hasMarkup(input)) return input == null ? "" : input;
        StringBuilder result = new StringBuilder(input.length());
        for (Token token : parse(input)) {
            if (token instanceof Text text) result.append(text.text());
            else if (token instanceof Break) result.append('\n');
        }
        return result.toString();
    }

    public static List<Token> parse(String input) {
        List<Token> tokens = new ArrayList<>();
        if (input == null || input.isEmpty()) return tokens;
        StringBuilder buffer = new StringBuilder();
        Deque<Frame> frames = new ArrayDeque<>();
        Style style = Style.BASE;
        int index = 0;
        while (index < input.length()) {
            char character = input.charAt(index);
            if (character == '\n') {
                flush(tokens, buffer, style);
                tokens.add(new Break());
                index++;
                continue;
            }
            if (character == '<' || character == '{') {
                char close = character == '<' ? '>' : '}';
                if (index + 1 < input.length() && input.charAt(index + 1) == character) {
                    buffer.append(character);
                    index += 2;
                    continue;
                }
                int end = input.indexOf(close, index + 1);
                int limit = character == '<' ? MAX_TAG_LENGTH : MAX_ICON_LENGTH;
                int nested = input.indexOf(character, index + 1);
                if (end > index && end - index <= limit && (nested < 0 || nested > end)) {
                    String body = input.substring(index + 1, end).trim();
                    if (character == '<') {
                        Style next = applyTag(body, style, frames);
                        if (next != null) {
                            flush(tokens, buffer, style);
                            style = next;
                            index = end + 1;
                            continue;
                        }
                    } else {
                        Icon icon = parseIcon(body);
                        if (icon != null) {
                            flush(tokens, buffer, style);
                            tokens.add(icon);
                            index = end + 1;
                            continue;
                        }
                    }
                }
            }
            buffer.append(character);
            index++;
        }
        flush(tokens, buffer, style);
        return tokens;
    }

    private static void flush(List<Token> tokens, StringBuilder buffer, Style style) {
        if (buffer.isEmpty()) return;
        tokens.add(new Text(buffer.toString(), style));
        buffer.setLength(0);
    }

    private static Style applyTag(String body, Style style, Deque<Frame> frames) {
        String tag = body.toLowerCase(Locale.ROOT);
        if (tag.equals("reset") || tag.equals("/all")) {
            frames.clear();
            return Style.BASE;
        }
        if (tag.equals("/")) {
            return frames.isEmpty() ? style : frames.pop().previous();
        }
        if (tag.startsWith("/")) {
            String name = canonical(tag.substring(1));
            if (name == null) return null;
            if (frames.stream().noneMatch(frame -> frame.name().equals(name))) return style;
            Style restored = style;
            while (!frames.isEmpty()) {
                Frame frame = frames.pop();
                restored = frame.previous();
                if (frame.name().equals(name)) break;
            }
            return restored;
        }
        String name = canonical(tag);
        if (name == null) return null;
        Style next = switch (name) {
            case "b" -> new Style(style.color(), true, style.italic(), style.underline(), style.strikethrough());
            case "i" -> new Style(style.color(), style.bold(), true, style.underline(), style.strikethrough());
            case "u" -> new Style(style.color(), style.bold(), style.italic(), true, style.strikethrough());
            case "s" -> new Style(style.color(), style.bold(), style.italic(), style.underline(), true);
            default -> new Style(color(tag), style.bold(), style.italic(), style.underline(), style.strikethrough());
        };
        frames.push(new Frame(name, style));
        return next;
    }

    private static String canonical(String tag) {
        return switch (tag) {
            case "b", "bold" -> "b";
            case "i", "italic" -> "i";
            case "u", "underline" -> "u";
            case "s", "strike", "strikethrough" -> "s";
            default -> color(tag) == null ? null : "color";
        };
    }

    private static Integer color(String tag) {
        Integer named = COLORS.get(tag);
        if (named != null) return named;
        if (tag.length() == 7 && tag.charAt(0) == '#') {
            try { return 0xFF000000 | Integer.parseInt(tag.substring(1), 16); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static Icon parseIcon(String body) {
        String[] parts = body.split(",");
        String head = parts[0].trim();
        int separator = head.indexOf(':');
        if (separator <= 0) return null;
        String type = head.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        if (type.equals("mob")) type = "entity";
        if (!type.equals("item") && !type.equals("entity") && !type.equals("enchantment") && !type.equals("potion")) return null;
        Identifier id = Identifier.tryParse(head.substring(separator + 1).trim());
        if (id == null) return null;
        Boolean greenTint = null;
        int size = DEFAULT_ICON_SIZE;
        for (int index = 1; index < parts.length; index++) {
            String option = parts[index].trim();
            int optionSeparator = option.indexOf(':');
            if (optionSeparator < 0) optionSeparator = option.indexOf('=');
            if (optionSeparator <= 0) return null;
            String key = option.substring(0, optionSeparator).trim().toLowerCase(Locale.ROOT);
            String value = option.substring(optionSeparator + 1).trim().toLowerCase(Locale.ROOT);
            switch (key) {
                case "green", "green_tint" -> {
                    if (!value.equals("true") && !value.equals("false")) return null;
                    greenTint = value.equals("true");
                }
                case "size" -> {
                    try { size = Math.max(6, Math.min(48, Integer.parseInt(value))); }
                    catch (NumberFormatException ignored) { return null; }
                }
                default -> { return null; }
            }
        }
        return new Icon(type, id.toString(), greenTint, size);
    }
}
