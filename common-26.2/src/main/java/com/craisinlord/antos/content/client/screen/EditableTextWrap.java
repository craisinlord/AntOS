package com.craisinlord.antos.content.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

final class EditableTextWrap {
    private EditableTextWrap() { }

    static List<Line> wrap(String text, int maxWidth, ToIntFunction<String> measure) {
        List<Line> lines = new ArrayList<>();
        int limit = Math.max(1, maxWidth);
        int start = 0;
        do {
            int newline = text.indexOf('\n', start);
            int lineEnd = newline < 0 ? text.length() : newline;
            do {
                int end = start;
                int width = 0;
                int lastBreak = -1;
                while (end < lineEnd) {
                    int point = text.codePointAt(end);
                    int next = end + Character.charCount(point);
                    int advance = measure.applyAsInt(text.substring(end, next));
                    if (width + advance > limit && end > start) break;
                    width += advance;
                    end = next;
                    if (Character.isWhitespace(point)) lastBreak = end;
                }
                if (end < lineEnd && lastBreak > start) end = lastBreak;
                boolean hyphen = end < lineEnd && splitsWord(text, start, end);
                if (hyphen) {
                    while (end > start && splitsWord(text, start, end)
                            && measure.applyAsInt(text.substring(start, end) + "-") > limit) {
                        int previous = text.offsetByCodePoints(end, -1);
                        if (previous == start) break;
                        end = previous;
                    }
                    hyphen = splitsWord(text, start, end) && measure.applyAsInt(text.substring(start, end) + "-") <= limit;
                }
                lines.add(new Line(start, end, hyphen));
                start = end;
            } while (start < lineEnd);
            if (newline < 0) break;
            start = newline + 1;
        } while (start <= text.length());
        return lines;
    }

    static boolean splitsWord(String text, int start, int end) {
        return end > start && end < text.length()
                && wordPart(text.codePointBefore(end)) && wordPart(text.codePointAt(end));
    }

    private static boolean wordPart(int point) {
        int type = Character.getType(point);
        return Character.isLetterOrDigit(point) || point == '_'
                || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK;
    }

    record Line(int start, int end, boolean hyphen) {
        String display(String text) {
            return text.substring(start, end) + (hyphen ? "-" : "");
        }
    }
}
