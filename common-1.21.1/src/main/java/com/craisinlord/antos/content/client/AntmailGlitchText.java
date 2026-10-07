package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.antmail.AntmailRenderMarkers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Animated presentation for {@code "style": "corrupted"} mail and the {@code [glitch]}/{@code [redact]} markup.
 * Wrapping is done on the clean text and characters are swapped per frame afterwards, so lines never reflow.
 */
public final class AntmailGlitchText {
    public static final int CORRUPT_RED = 0xFFFF3B3B;
    public static final int CORRUPT_DIM = 0xFF9A1414;
    public static final int CORRUPT_BACKGROUND = 0xFF080000;
    private static final String NOISE = "#$%&*@!?/\\<>=+~^:;";
    private static final String TAG_GLITCH = "antos:glitch";
    private static final String TAG_REDACT = "antos:redact";
    private static final String TAG_CORRUPT = "antos:corrupt";
    private static final long REDACT_VISIBLE_MILLIS = 2500L;

    private AntmailGlitchText() { }

    /** Builds the styled body; pass the result to {@code font.split} and then {@link #animate} each line. */
    public static Component body(String text, boolean corrupted, int plainColor) {
        MutableComponent root = Component.empty();
        for (AntmailRenderMarkers.Segment segment : AntmailRenderMarkers.segments(text)) {
            Style style = switch (segment.kind()) {
                case GLITCH -> Style.EMPTY.withColor(TextColor.fromRgb(CORRUPT_RED & 0xFFFFFF)).withInsertion(TAG_GLITCH);
                case REDACT -> Style.EMPTY.withColor(TextColor.fromRgb((corrupted ? CORRUPT_RED : plainColor) & 0xFFFFFF)).withInsertion(TAG_REDACT);
                case PLAIN -> corrupted
                        ? Style.EMPTY.withColor(TextColor.fromRgb(CORRUPT_RED & 0xFFFFFF)).withInsertion(TAG_CORRUPT)
                        : Style.EMPTY.withColor(TextColor.fromRgb(plainColor & 0xFFFFFF));
            };
            root.append(Component.literal(segment.text()).withStyle(style));
        }
        return root;
    }

    /** Applies this frame's character substitution and redaction to one wrapped line. */
    public static FormattedCharSequence animate(FormattedCharSequence line, int lineIndex, long openedAtMillis) {
        long now = System.currentTimeMillis();
        long bucket = now / 120L;
        boolean redacted = now - openedAtMillis > REDACT_VISIBLE_MILLIS;
        return sink -> {
            int[] position = {0};
            return line.accept((index, style, codePoint) -> {
                int charIndex = lineIndex * 512 + position[0]++;
                String tag = style.getInsertion();
                int output = codePoint;
                if (TAG_REDACT.equals(tag)) {
                    if (redacted && !Character.isWhitespace(codePoint)) output = '█';
                } else if (TAG_GLITCH.equals(tag)) {
                    output = noise(codePoint, charIndex, bucket, 35);
                } else if (TAG_CORRUPT.equals(tag)) {
                    output = noise(codePoint, charIndex, bucket, 4);
                }
                return sink.accept(index, style, output);
            });
        };
    }

    /** Horizontal jitter for a corrupted line this frame: usually 0, occasionally one pixel either way. */
    public static int jitter(int lineIndex) {
        long bucket = System.currentTimeMillis() / 90L;
        int roll = hash(lineIndex, bucket) % 100;
        return roll < 6 ? -1 : roll < 12 ? 1 : 0;
    }

    /** Single-line glitch for subjects and senders; {@code percent} of characters flicker. */
    public static String flicker(String text, int percent, int salt) {
        if (text == null || text.isEmpty()) return "";
        long bucket = System.currentTimeMillis() / 150L;
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            builder.append((char) noise(text.charAt(index), salt * 131 + index, bucket, percent));
        }
        return builder.toString();
    }

    /** Sender label for corrupted rows: mostly stable, with short bursts of heavy garbling. */
    public static String garbledSender(String sender, int salt) {
        long second = System.currentTimeMillis() / 1000L;
        boolean burst = hash(salt, second) % 100 < 25;
        return flicker(sender, burst ? 60 : 8, salt);
    }

    private static int noise(int codePoint, int index, long bucket, int percent) {
        if (Character.isWhitespace(codePoint)) return codePoint;
        int roll = hash(index, bucket);
        return roll % 100 < percent ? NOISE.charAt((roll / 100) % NOISE.length()) : codePoint;
    }

    private static int hash(int index, long bucket) {
        long value = bucket * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 29;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 32;
        return (int) (value & 0x7FFFFFFF);
    }
}
