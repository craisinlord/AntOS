package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.antmail.AntmailRecipients;
import com.craisinlord.antos.content.client.AntmailClientState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;

final class AntmailRecipientPicker {
    private AntmailRecipientPicker() { }
    static String summary(String input) {
        try {
            var selections = AntmailRecipients.parse(input);
            List<String> labels = new ArrayList<>();
            for (var selection : selections.stream().limit(2).toList()) {
                String label = AntmailClientState.recipientLabel(selection.token());
                if (!label.isBlank() && selection.kind() == AntmailRecipients.Kind.TEAM) label = AntmailDirectoryPanel.teamLabel(label);
                if (label.isBlank()) {
                    label = selection.reference();
                    if (selection.kind() != AntmailRecipients.Kind.ACCOUNT || !label.contains("@")) label = Component.translatable("computer.antos.mail." + selection.kind().name().toLowerCase(java.util.Locale.ROOT)).getString();
                }
                labels.add("[" + label + "]");
            }
            return String.join(" ", labels) + (selections.size() > 2 ? " +" + (selections.size() - 2) : "");
        } catch (RuntimeException exception) { return input; }
    }
     
    
    private record DisplayKey(String input, int cursor, boolean editing, boolean caret, int width, int labels, Class<?> measure) { }
    private static DisplayKey lastKey;
    private static String lastDisplay = "";
    static String display(String input, int cursor, boolean editing, boolean caret, int width, ToIntFunction<String> measure) {
        DisplayKey key = new DisplayKey(input, editing ? cursor : 0, editing, editing && caret, width, AntmailClientState.recipientLabelsVersion(), measure.getClass());
        if (key.equals(lastKey)) return lastDisplay;
        String result;
        if (editing) result = viewport(input, cursor, caret, width, measure);
        else {
            String count = "";
            try { int size = AntmailRecipients.parse(input).size(); if (size > 1) count = " (" + size + ")"; } catch (RuntimeException ignored) { }
            result = fit(summary(input), Math.max(0, width - measure.applyAsInt(count)), measure) + count;
        }
        lastKey = key; lastDisplay = result;
        return result;
    }
     
    private static String fit(String text, int width, ToIntFunction<String> measure) {
        if (measure.applyAsInt(text) <= width) return text;
        if (measure.applyAsInt("…") > width) return "";
        int low = 0, high = text.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (measure.applyAsInt(text.substring(0, middle) + "…") <= width) low = middle; else high = middle - 1;
        }
        return text.substring(0, low) + "…";
    }
    static String viewport(String input, int cursor, boolean caret, int width, ToIntFunction<String> measure) {
        cursor = Math.max(0, Math.min(cursor, input.length()));
        String marker = caret ? "|" : "";
        int start = 0;
        while (start < cursor && measure.applyAsInt(input.substring(start, cursor) + marker) > width) start++;
        int end = cursor;
        while (end < input.length() && measure.applyAsInt(input.substring(start, cursor) + marker + input.substring(cursor, end + 1)) <= width) end++;
        return input.substring(start, cursor) + marker + input.substring(cursor, end);
    }
}
