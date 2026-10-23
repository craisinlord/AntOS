package com.craisinlord.antos.config;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DesktopPreferencesTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("antos-desktop-preferences");
        Path config = directory.resolve("antos.json");
        Path state = directory.resolve("antos").resolve("client.json");
        try {
            Files.writeString(config, "{\"preferences\":{\"theme\":\"amber\",\"mailSound\":false,\"taskSound\":false}}");
            AntOSSettings.loadClientState(state, config);
            var defaults = AntOSSettings.preferences();
            check(!defaults.pauseWhileOpen, "Old configs preserve running worlds");
            check(!defaults.fullscreenDesktop && !defaults.quietNotifications, "New display and notification options default to off");
            check(defaults.interfaceSounds && defaults.interfaceVolume == 60, "Old configs receive sound defaults");
            check(defaults.theme.equals("amber") && !defaults.mailSound && !defaults.taskSound, "Existing preferences survive migration");
            check(defaults.taskSidebarBehavior.equals("click") && defaults.taskSidebarWidth.equals("narrow")
                    && defaults.taskSidebarText.equals("small") && defaults.taskSidebarSpacing.equals("compact")
                    && defaults.taskSidebarCounts, "Older preferences receive compact sidebar defaults");
            defaults.taskSidebarWidth = "wide";
            check(AntOSSettings.preferences().taskSidebarWidth.equals("narrow"), "Sidebar preferences are copied independently");
            defaults.pauseWhileOpen = true;
            check(!AntOSSettings.preferences().pauseWhileOpen, "Reading preferences returns an independent copy");
            AntOSSettings.updatePreferences(p -> {
                p.pauseWhileOpen = true;
                p.fullscreenDesktop = true;
                p.quietNotifications = true;
                p.interfaceSounds = false;
                p.interfaceVolume = 37;
                p.taskSidebarBehavior = "always";
                p.taskSidebarWidth = "wide";
                p.taskSidebarText = "large";
                p.taskSidebarSpacing = "comfortable";
                p.taskSidebarCounts = false;
            });
            AntOSSettings.loadClientState(state, config);
            var saved = AntOSSettings.preferences();
            check(saved.pauseWhileOpen && !saved.interfaceSounds && saved.interfaceVolume == 37, "Preferences persist across reloads");
            check(saved.fullscreenDesktop && saved.quietNotifications, "Display and quiet mode persist across reloads");
            AntOSSettings.toggleArchiveBookmark("workspace:a", "test:article");
            AntOSSettings.setNotificationHistory("workspace:a", "[]");
            AntOSSettings.setEditorDraft("workspace:a", "text", "{\"content\":\"unsaved\"}");
            AntOSSettings.setEditorDraft("workspace:a", "paint", "{\"pixels\":\"draft\"}");
            for (int i = 0; i < 25; i++) AntOSSettings.recordArchiveEntry("workspace:a", "test:article_" + i);
            AntOSSettings.recordArchiveEntry("workspace:a", "test:article_20");
            AntOSSettings.loadClientState(state, config);
            check(AntOSSettings.archiveBookmarks("workspace:a").contains("test:article"), "Bookmarks persist");
            check(AntOSSettings.archiveBookmarks("workspace:b").isEmpty(), "Bookmarks are isolated by workspace");
            check(AntOSSettings.notificationHistory("workspace:a").equals("[]") && AntOSSettings.notificationHistory("workspace:b") == null, "Notification history is isolated and persists");
            check(AntOSSettings.editorDraft("workspace:a", "text").contains("unsaved") && AntOSSettings.editorDraft("workspace:a", "paint").contains("pixels"), "Editor drafts persist independently");
            check(AntOSSettings.editorDraft("workspace:b", "text") == null, "Drafts are isolated by workspace");
            AntOSSettings.setEditorDraft("workspace:a", "text", null);
            check(AntOSSettings.editorDraft("workspace:a", "text") == null && AntOSSettings.editorDraft("workspace:a", "paint") != null, "Discarding one draft leaves other apps alone");
            var recent = AntOSSettings.recentArchiveEntries("workspace:a");
            check(recent.size() == 20 && recent.getFirst().equals("test:article_20") && recent.stream().distinct().count() == 20, "Recent articles are bounded and deduplicated");
            check(AntOSSettings.recentArchiveEntries("workspace:b").isEmpty(), "Recent articles are isolated");
            AntOSSettings.toggleArchiveBookmark("workspace:a", "test:article");
            check(AntOSSettings.archiveBookmarks("workspace:a").isEmpty(), "Bookmarks can be removed");
            AntOSSettings.clearRecentArchiveEntries("workspace:a");
            check(AntOSSettings.recentArchiveEntries("workspace:a").isEmpty(), "Recent history can be cleared");
            check(saved.taskSidebarBehavior.equals("always") && saved.taskSidebarWidth.equals("wide")
                    && saved.taskSidebarText.equals("large") && saved.taskSidebarSpacing.equals("comfortable")
                    && !saved.taskSidebarCounts, "Sidebar customization persists across reloads");
            AntOSSettings.updatePreferences(p -> {
                p.taskSidebarBehavior = null;
                p.taskSidebarWidth = "invalid";
                p.taskSidebarText = null;
                p.taskSidebarSpacing = "invalid";
            });
            var normalized = AntOSSettings.preferences();
            check(normalized.taskSidebarBehavior.equals("click") && normalized.taskSidebarWidth.equals("narrow")
                    && normalized.taskSidebarText.equals("small") && normalized.taskSidebarSpacing.equals("compact"),
                    "Invalid sidebar options normalize to compact defaults");
            check(!saved.mailSound && !saved.taskSound, "Interface controls leave notification preferences alone");
            AntOSSettings.updatePreferences(p -> p.interfaceVolume = 200);
            check(AntOSSettings.preferences().interfaceVolume == 100, "Volume is clamped above 100");
            AntOSSettings.updatePreferences(p -> p.interfaceVolume = -1);
            check(AntOSSettings.preferences().interfaceVolume == 0, "Volume is clamped below zero");
            Files.writeString(state, "{\"preferences\":null}");
            AntOSSettings.loadClientState(state, config);
            check(!AntOSSettings.preferences().pauseWhileOpen && AntOSSettings.preferences().interfaceVolume == 60, "Null preferences receive defaults");
            System.out.println("Desktop preference checks passed");
        } finally {
            Files.walk(directory).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
