package com.craisinlord.antos.config;

public final class NotificationHistoryTest {
    public static void main(String[] args) {
        NotificationHistory history = new NotificationHistory();
        for (int i = 0; i < 120; i++) history.add("mail", "Title " + i, "Message " + i, i);
        check(history.entries().size() == 100 && history.unreadCount() == 100, "History retains the latest 100 notices");
        check(history.entries().get(0).receivedAt() == 119 && history.entries().get(history.entries().size() - 1).receivedAt() == 20, "Latest notices appear first");
        String first = history.entries().get(0).id();
        history.markRead(first);
        history.markRead(first);
        check(history.unreadCount() == 99, "Opening a notice marks only that notice read");
        history.markRead("missing");
        check(history.unreadCount() == 99, "Stale links leave history intact");
        NotificationHistory restored = NotificationHistory.decode(history.encode());
        check(restored.entries().equals(history.entries()), "Content, order, and read state survive reload");
        history.markAllRead();
        check(history.unreadCount() == 0 && history.entries().size() == 100, "Mark all read preserves history");
        history.clear();
        check(history.entries().isEmpty() && history.unreadCount() == 0, "Clear removes notices and badges");
        check(restored.entries().size() == 100, "Histories are independent");
        check(NotificationHistory.decode("not json").entries().isEmpty(), "Malformed history is recoverable");
        check(NotificationHistory.decode("null").entries().isEmpty(), "Null history is recoverable");
        check(NotificationHistory.decode("[null,{}]").entries().isEmpty(), "Incomplete notices are skipped");
        String entry = restored.entries().get(0).id();
        String valid = "{\"id\":\"" + entry + "\",\"kind\":\"task\",\"title\":\"A\",\"message\":\"B\",\"receivedAt\":1}";
        check(NotificationHistory.decode("[null," + valid + "," + valid + "]").entries().size() == 1, "Duplicate notices are skipped");
        NotificationHistory icons = new NotificationHistory();
        icons.add("mail", "Mail", "From Ant", 5, "Ant", "minecraft:apple", "");
        var iconEntry = NotificationHistory.decode(icons.encode()).entries().get(0);
        check(iconEntry.source().equals("Ant") && iconEntry.iconItem().equals("minecraft:apple") && iconEntry.iconEntity().isEmpty(), "Notice icons survive reload");
        var legacy = NotificationHistory.decode("[" + valid + "]").entries().get(0);
        check(legacy.source().isEmpty() && legacy.iconItem().isEmpty() && legacy.iconEntity().isEmpty(), "Older notices load without icons");
        System.out.println("Notification history checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
