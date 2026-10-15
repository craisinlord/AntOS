package com.craisinlord.antos.content.antmail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts explicit Archive-style render markers in mail into portable attachments. */
public final class AntmailRenderMarkers {
    private static final Pattern MARKER = Pattern.compile("@(item|entity|enchantment|potion|recipe):([a-z0-9_.-]+:[a-z0-9_./-]+)");
    private static final Set<String> KINDS = Set.of("item", "entity", "enchantment", "potion", "recipe");

    private AntmailRenderMarkers() { }

    public static boolean isSupportedKind(String kind) { return KINDS.contains(kind); }

    public static Extracted extract(String body, List<AntmailAttachment> existing) {
        if (body == null || body.isEmpty()) return new Extracted(body == null ? "" : body, existing == null ? List.of() : existing);
        List<AntmailAttachment> attachments = new ArrayList<>(existing == null ? List.of() : existing);
        Matcher matcher = MARKER.matcher(body);
        StringBuffer cleaned = new StringBuffer();
        Set<String> seen = new LinkedHashSet<>();
        for (AntmailAttachment attachment : attachments) {
            if (attachment instanceof AntmailAttachment.Render render) seen.add(render.kind() + "\u0000" + render.resourceId());
        }
        while (matcher.find()) {
            String kind = matcher.group(1);
            String id = matcher.group(2);
            if (seen.add(kind + "\u0000" + id)) attachments.add(new AntmailAttachment.Render(kind, id));
            matcher.appendReplacement(cleaned, "");
        }
        matcher.appendTail(cleaned);
        return new Extracted(cleaned.toString().replaceAll("[ \\t]{2,}", " ").replaceAll("[ \\t]+\\n", "\\n"), List.copyOf(attachments));
    }

    public record Extracted(String body, List<AntmailAttachment> attachments) { }

    private static final Pattern STYLE_MARKER = Pattern.compile("\\[(glitch|redact)](.*?)\\[/\\1]", Pattern.DOTALL);

    public enum SegmentKind { PLAIN, GLITCH, REDACT }

    public record Segment(SegmentKind kind, String text) { }

    /** Splits {@code [glitch]text[/glitch]} and {@code [redact]text[/redact]} presentation markup out of mail text. */
    public static List<Segment> segments(String text) {
        List<Segment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) return segments;
        Matcher matcher = STYLE_MARKER.matcher(text);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) segments.add(new Segment(SegmentKind.PLAIN, text.substring(last, matcher.start())));
            segments.add(new Segment(matcher.group(1).equals("glitch") ? SegmentKind.GLITCH : SegmentKind.REDACT, matcher.group(2)));
            last = matcher.end();
        }
        if (last < text.length()) segments.add(new Segment(SegmentKind.PLAIN, text.substring(last)));
        return segments;
    }

    /** Mail text with presentation markup removed, for single-line previews. */
    public static String stripStyleMarkers(String text) {
        return text == null ? "" : STYLE_MARKER.matcher(text).replaceAll("$2");
    }
}
