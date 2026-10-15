package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.guide.TaskMarkup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

final class TaskRichText {
    private static final int LINE_HEIGHT = 11;
    private static final int MAX_LAYOUTS = 256;
    private final ComputerScreen screen;
    private final Font font;
    private final Map<String, List<TaskMarkup.Token>> parsed = new HashMap<>();
    private Object layoutLanguage;
    private final Map<LayoutKey, List<Line>> layouts = new java.util.LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LayoutKey, List<Line>> eldest) {
            return size() > MAX_LAYOUTS;
        }
    };

    TaskRichText(ComputerScreen screen, Font font) {
        this.screen = screen;
        this.font = font;
    }

    void clearCache() {
        parsed.clear();
        layouts.clear();
    }

    int render(ThemedGraphics g, String markup, int x, int y, int width, int baseColor, boolean renderMobFromSpawnEgg) {
        if (!TaskMarkup.hasMarkup(markup)) return screen.wrap(g, Component.literal(markup), x, y, width, baseColor);
        int lineY = y;
        for (Line line : lines(markup, width)) {
            int height = line.height();
            int textY = lineY + (height - LINE_HEIGHT) / 2 + (height > LINE_HEIGHT ? 1 : 0);
            for (Piece piece : line.pieces()) {
                if (piece.icon() != null) renderIcon(g, piece.icon(), x + piece.x(), lineY + (height - piece.icon().size()) / 2, renderMobFromSpawnEgg);
                else {
                    int color = piece.style().color() == TaskMarkup.BASE_COLOR ? baseColor : piece.style().color();
                    g.drawString(font, piece.component(), x + piece.x(), textY, color, false);
                }
            }
            lineY += height;
        }
        return lineY;
    }

    int measure(String markup, int y, int width) {
        if (!TaskMarkup.hasMarkup(markup)) return y + screen.wrapLines(Component.literal(markup), width).size() * LINE_HEIGHT;
        int lineY = y;
        for (Line line : lines(markup, width)) lineY += line.height();
        return lineY;
    }

    private List<Line> lines(String markup, int width) {
        Object language = net.minecraft.locale.Language.getInstance();
        if (layoutLanguage != language) {
            layoutLanguage = language;
            layouts.clear();
        }
        int safeWidth = Math.max(1, width);
        LayoutKey key = new LayoutKey(markup, safeWidth);
        List<Line> lines = layouts.get(key);
        if (lines == null) {
            List<TaskMarkup.Token> tokens = parsed.computeIfAbsent(markup, TaskMarkup::parse);
            lines = new ArrayList<>();
            for (List<Piece> line : layout(tokens, safeWidth)) {
                int height = LINE_HEIGHT;
                for (Piece piece : line) if (piece.icon() != null) height = Math.max(height, piece.icon().size() + 1);
                List<Piece> pieces = new ArrayList<>(line.size());
                for (Piece piece : line) pieces.add(piece.icon() != null ? piece : new Piece(piece.text(), piece.style(), null, piece.x(), styled(piece.text(), piece.style())));
                lines.add(new Line(pieces, height));
            }
            layouts.put(key, lines);
        }
        return lines;
    }

    private void renderIcon(ThemedGraphics g, TaskMarkup.Icon icon, int left, int top, boolean renderMobFromSpawnEgg) {
        int size = icon.size();
        boolean previousTint = screen.archiveGreenTint;
        if (icon.greenTint() != null) screen.archiveGreenTint = icon.greenTint();
        try {
            int centerX = left + size / 2;
            int centerY = top + size / 2;
            switch (icon.type()) {
                case "entity" -> screen.renderArchiveAsset(g, "", icon.id(), "", "", centerX, centerY, size, 0.0F, 1.8F, renderMobFromSpawnEgg, 1.0F);
                case "enchantment" -> screen.renderArchiveAsset(g, "", "", icon.id(), "", centerX, centerY, 20, 0.0F, 1.0F, renderMobFromSpawnEgg, size / 16.0F);
                case "potion" -> screen.renderArchiveAsset(g, "", "", "", icon.id(), centerX, centerY, 20, 0.0F, 1.0F, renderMobFromSpawnEgg, size / 16.0F);
                default -> screen.renderArchiveAsset(g, icon.id(), "", "", "", centerX, centerY, 20, 0.0F, 1.8F, renderMobFromSpawnEgg, size / 16.0F);
            }
        } finally {
            screen.archiveGreenTint = previousTint;
        }
    }

    private Component styled(String text, TaskMarkup.Style style) {
        return Component.literal(text).withStyle(Style.EMPTY.withBold(style.bold()).withItalic(style.italic())
                .withUnderlined(style.underline()).withStrikethrough(style.strikethrough()));
    }

    private int width(String text, TaskMarkup.Style style) {
        return font.width(styled(text, style));
    }

    private List<List<Piece>> layout(List<TaskMarkup.Token> tokens, int width) {
        List<List<Piece>> lines = new ArrayList<>();
        List<Piece> line = new ArrayList<>();
        int cursor = 0;
        for (TaskMarkup.Token token : tokens) {
            if (token instanceof TaskMarkup.Break) {
                lines.add(line);
                line = new ArrayList<>();
                cursor = 0;
                continue;
            }
            if (token instanceof TaskMarkup.Icon icon) {
                int advance = icon.size() + 1;
                if (cursor > 0 && cursor + icon.size() > width) {
                    lines.add(line);
                    line = new ArrayList<>();
                    cursor = 0;
                }
                line.add(new Piece(null, null, icon, cursor, null));
                cursor += advance;
                continue;
            }
            TaskMarkup.Text text = (TaskMarkup.Text) token;
            for (String word : words(text.text())) {
                boolean space = word.isBlank();
                if (space) {
                    if (cursor == 0) continue;
                    int spaceWidth = width(word, text.style());
                    if (cursor + spaceWidth > width) {
                        lines.add(line);
                        line = new ArrayList<>();
                        cursor = 0;
                        continue;
                    }
                    line.add(new Piece(word, text.style(), null, cursor, null));
                    cursor += spaceWidth;
                    continue;
                }
                int wordWidth = width(word, text.style());
                if (cursor > 0 && cursor + wordWidth > width) {
                    int start = line.size();
                    while (start > 0 && line.get(start - 1).icon() == null && !line.get(start - 1).text().isBlank()) start--;
                    if (start == line.size() || start > 0) {
                        List<Piece> carried = new ArrayList<>(line.subList(start, line.size()));
                        line.subList(start, line.size()).clear();
                        trimTrailingSpace(line);
                        lines.add(line);
                        line = new ArrayList<>();
                        cursor = 0;
                        for (Piece piece : carried) {
                            line.add(new Piece(piece.text(), piece.style(), null, cursor, null));
                            cursor += width(piece.text(), piece.style());
                        }
                    }
                }
                String remaining = word;
                while (cursor + width(remaining, text.style()) > width) {
                    String part = fit(remaining, text.style(), width - cursor);
                    if (part.isEmpty()) {
                        if (cursor == 0) part = remaining.substring(0, 1);
                        else {
                            lines.add(line);
                            line = new ArrayList<>();
                            cursor = 0;
                            continue;
                        }
                    }
                    line.add(new Piece(part, text.style(), null, cursor, null));
                    lines.add(line);
                    line = new ArrayList<>();
                    cursor = 0;
                    remaining = remaining.substring(part.length());
                    if (remaining.isEmpty()) break;
                }
                if (!remaining.isEmpty()) {
                    line.add(new Piece(remaining, text.style(), null, cursor, null));
                    cursor += width(remaining, text.style());
                }
            }
        }
        lines.add(line);
        return lines;
    }

    private void trimTrailingSpace(List<Piece> line) {
        while (!line.isEmpty() && line.get(line.size() - 1).icon() == null && line.get(line.size() - 1).text().isBlank()) line.remove(line.size() - 1);
    }

    private String fit(String text, TaskMarkup.Style style, int available) {
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (width(text.substring(0, middle), style) <= available) low = middle;
            else high = middle - 1;
        }
        return text.substring(0, low);
    }

    private static List<String> words(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int index = 1; index <= text.length(); index++) {
            if (index == text.length() || Character.isWhitespace(text.charAt(index)) != Character.isWhitespace(text.charAt(start))) {
                result.add(text.substring(start, index));
                start = index;
            }
        }
        return result;
    }

    private record Piece(String text, TaskMarkup.Style style, TaskMarkup.Icon icon, int x, Component component) { }

    private record Line(List<Piece> pieces, int height) { }

    private record LayoutKey(String markup, int width) { }
}
