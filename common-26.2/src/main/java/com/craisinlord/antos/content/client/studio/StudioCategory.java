package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import java.util.List;

enum StudioCategory {
    FLOPPIES("disks", AuthoringKind.DISK, AuthoringKind.DISK_CATEGORY),
    TASKS("tasks", AuthoringKind.TASK, AuthoringKind.CATEGORY, AuthoringKind.GROUP, AuthoringKind.REWARD_POOL),
    ARCHIVE("archive", AuthoringKind.ARCHIVE_ENTRY, AuthoringKind.ARCHIVE_FOLDER),
    MAIL("antmail", AuthoringKind.MESSAGE, AuthoringKind.MAIL_POOL),
    ANTAZON("antazon", AuthoringKind.PRODUCT, AuthoringKind.SELL_RULE, AuthoringKind.DEAL_CAMPAIGN),
    PROGRAMS("games", AuthoringKind.PROGRAM),
    TERMINAL("terminal", AuthoringKind.TERMINAL_COMMAND);

    final StudioIcon icon;
    final List<AuthoringKind> kinds;

    StudioCategory(String icon, AuthoringKind... kinds) {
        this.icon = StudioIcon.pixel("desktop/" + icon);
        this.kinds = List.of(kinds);
    }

    String title() { return StudioText.t("category." + name().toLowerCase(java.util.Locale.ROOT)); }

    static StudioCategory of(AuthoringKind kind) {
        for (StudioCategory category : values()) if (category.kinds.contains(kind)) return category;
        return FLOPPIES;
    }

    static StudioCategory parse(String name) {
        for (StudioCategory category : values()) if (category.name().equals(name)) return category;
        return FLOPPIES;
    }
}
