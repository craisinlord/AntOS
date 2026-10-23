package com.craisinlord.antos.content.client.screen;

import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;

public final class EditableTextWrapTest {
    private static final ToIntFunction<String> WIDTH = value -> value.codePointCount(0, value.length());

    public static void main(String[] args) {
        check(display("abcdef", 4).equals(List.of("abc-", "def")), "reserve space for hyphen");
        check(display("abc def", 4).equals(List.of("abc ", "def")), "wrap at whitespace");
        check(display("abc\ndef\n", 4).equals(List.of("abc", "def", "")), "preserve explicit newlines");
        check(display("", 4).equals(List.of("")), "empty input retains a caret row");
        check(display("ab-cdef", 3).equals(List.of("ab-", "cd-", "ef")), "existing hyphen is not doubled");
        check(display("abcdef", 1).equals(List.of("a", "b", "c", "d", "e", "f")), "tiny widths make progress");
        check(display("abc def", 7).equals(List.of("abc def")), "exact fit has no marker");
        String unicode = "\uD801\uDC00\uD801\uDC01\uD801\uDC02\uD801\uDC03";
        check(display(unicode, 3).equals(List.of(unicode.substring(0, 4) + "-", unicode.substring(4))), "surrogate pairs stay intact");
        Random random = new Random(3L);
        String[] tokens = {"a", "W", "i", " ", "\n", "-", "_", "\uD801\uDC00", "\u0301", "9"};
        ToIntFunction<String> variableWidth = value -> value.codePoints().map(point -> point == 'W' ? 3 : 1).sum();
        for (int sample = 0; sample < 1000; sample++) {
            StringBuilder text = new StringBuilder();
            for (int i = random.nextInt(100); i > 0; i--) text.append(tokens[random.nextInt(tokens.length)]);
            verify(text.toString(), random.nextInt(30) + 3, variableWidth);
        }
        System.out.println("EditableTextWrapTest passed (1000 randomized layouts and boundary cases)");
    }

    private static List<String> display(String text, int width) {
        return EditableTextWrap.wrap(text, width, WIDTH).stream().map(line -> line.display(text)).toList();
    }

    private static void verify(String text, int width, ToIntFunction<String> measure) {
        List<EditableTextWrap.Line> lines = EditableTextWrap.wrap(text, width, measure);
        StringBuilder restored = new StringBuilder();
        int offset = 0;
        for (EditableTextWrap.Line line : lines) {
            if (line.start() > offset) {
                check(line.start() == offset + 1 && text.charAt(offset) == '\n', "only explicit newlines may be skipped");
                restored.append('\n');
            }
            check(line.end() >= line.start(), "valid source range");
            check(line.start() == text.length() || !Character.isLowSurrogate(text.charAt(line.start())), "code point boundary");
            check(measure.applyAsInt(line.display(text)) <= width, "display fits including marker");
            check(!line.hyphen() || EditableTextWrap.splitsWord(text, line.start(), line.end()), "marker only splits a word");
            restored.append(text, line.start(), line.end());
            offset = line.end();
        }
        check(restored.toString().equals(text), "source text round trips without display markers");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
