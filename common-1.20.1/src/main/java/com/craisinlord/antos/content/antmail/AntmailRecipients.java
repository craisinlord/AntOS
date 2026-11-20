package com.craisinlord.antos.content.antmail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AntmailRecipients {
    public static final int MAX_RECIPIENTS = 64;
    public enum Kind { ACCOUNT, PERSONAL, MAILING, CONVERSATION, TEAM }
    public record Selection(Kind kind, String reference) {
        public Selection {
            if (kind == null || reference == null || reference.isBlank() || reference.length() > 80) throw new IllegalArgumentException("invalid_recipient");
            if (kind != Kind.ACCOUNT) UUID.fromString(reference);
        }
        public String token() {
            return kind == Kind.ACCOUNT ? reference : kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + reference;
        }
    }
    private AntmailRecipients() { }
    public static List<Selection> parse(String input) {
        if (input == null || input.isBlank()) return List.of();
        if (input.length() > 8192) throw new IllegalArgumentException("recipient_limit");
        List<Selection> result = new ArrayList<>();
        for (String value : input.split("[,;]", -1)) {
            String token = value.trim();
            int separator = token.indexOf(':');
            if (separator < 0) result.add(new Selection(Kind.ACCOUNT, token));
            else result.add(new Selection(Kind.valueOf(token.substring(0, separator).toUpperCase(java.util.Locale.ROOT)), token.substring(separator + 1)));
            if (result.size() > MAX_RECIPIENTS) throw new IllegalArgumentException("recipient_limit");
        }
        return List.copyOf(result);
    }
    public static String format(List<Selection> selections) {
        return String.join(", ", selections.stream().map(Selection::token).toList());
    }
    public static boolean valid(String input) {
        try {
            List<Selection> selections = parse(input);
            return !selections.isEmpty() && selections.stream().allMatch(value -> value.kind() != Kind.ACCOUNT
                    || AntmailAddress.isValidAddress(value.reference()) || isId(value.reference()));
        } catch (RuntimeException exception) { return false; }
    }
    public static boolean direct(String input) {
        try {
            List<Selection> values = parse(input);
            return values.size() == 1 && values.get(0).kind() == Kind.ACCOUNT;
        } catch (RuntimeException exception) { return false; }
    }
    private static boolean isId(String value) {
        try { UUID.fromString(value); return true; } catch (IllegalArgumentException exception) { return false; }
    }
}
