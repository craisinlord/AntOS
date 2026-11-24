package com.craisinlord.antos.content.editor;

import java.util.Arrays;

public enum AuthoringKind {
    TASK("computer/task", "Task", "TASKS"),
    CATEGORY("computer/task/category", "Category", "TASKS"),
    GROUP("computer/task/group", "Group", "TASKS"),
    REWARD_POOL("computer/task/reward_pool", "Reward pool", "TASKS"),
    DISK("computer/disk", "Disk", "DISKS"),
    DISK_CATEGORY("computer/category", "Disk category", "DISKS"),
    PROGRAM("computer/program", "Program", "DISKS"),
    MESSAGE("antmail/message", "Message", "ANTMAIL"),
    MAIL_POOL("antmail/pool", "Mail pool", "ANTMAIL"),
    TAKEOVER("computer/takeover", "Desktop takeover", "ANTMAIL"),
    PRODUCT("antazon/product", "Product", "ANTAZON"),
    SELL_RULE("antazon/sell", "Sell rule", "ANTAZON"),
    DEAL_CAMPAIGN("antazon/deal", "Deal campaign", "ANTAZON"),
    ARCHIVE_ENTRY("computer/entry", "Archive entry", "ARCHIVE"),
    ARCHIVE_FOLDER("computer/archive_folder", "Archive folder", "ARCHIVE"),
    TERMINAL_COMMAND("computer/terminal_command", "Terminal command", "TERMINAL");

    private final String directory;
    private final String title;
    private final String app;
    AuthoringKind(String directory, String title, String app) { this.directory = directory; this.title = title; this.app = app; }
    public String directory() { return directory; }
    public String title() { return title; }
    public String app() { return app; }
    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    public static AuthoringKind parse(String key) {
        return Arrays.stream(values()).filter(value -> value.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported content type"));
    }
    public boolean includes(String path) {
        if (!path.startsWith(directory + "/") || !path.endsWith(".json")) return false;
        if (this != TASK) return true;
        return !path.startsWith(CATEGORY.directory + "/") && !path.startsWith(GROUP.directory + "/")
                && !path.startsWith(REWARD_POOL.directory + "/");
    }
}
