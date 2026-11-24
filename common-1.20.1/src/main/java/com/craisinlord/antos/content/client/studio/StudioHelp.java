package com.craisinlord.antos.content.client.studio;

import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

final class StudioHelp extends StudioPopup {
    static final List<String> TOPICS = List.of("start", "floppies", "tasks", "articles", "mail", "shop", "programs", "terminal", "saving", "errors", "keys");
    private static final Map<String, String> ISSUES = Map.ofEntries(
            Map.entry("goal_id", "tasks"), Map.entry("goal_duplicate", "tasks"), Map.entry("goal_target", "tasks"), Map.entry("task_no_goals", "tasks"),
            Map.entry("task_hidden", "floppies"), Map.entry("article_hidden", "floppies"), Map.entry("needs_itself", "tasks"), Map.entry("needs_loop", "tasks"),
            Map.entry("mail_sender", "mail"), Map.entry("mail_target", "mail"), Map.entry("mail_trigger", "mail"), Map.entry("mail_chance", "mail"),
            Map.entry("mail_unsubscribed", "mail"), Map.entry("changed_elsewhere", "saving"), Map.entry("id_taken", "saving"), Map.entry("reference_missing", "errors"),
            Map.entry("missing_reference", "errors"), Map.entry("reference_gone", "errors"), Map.entry("unknown_item", "errors"), Map.entry("server", "errors"),
            Map.entry("invalid", "errors"), Map.entry("product_price", "shop"), Map.entry("product_contents", "shop"), Map.entry("deal_cadence", "shop"),
            Map.entry("password_empty", "floppies"), Map.entry("file_source", "floppies"), Map.entry("quiz_empty", "programs"), Map.entry("question_answers", "programs"),
            Map.entry("page_stuck", "programs"), Map.entry("choice_destination", "programs"), Map.entry("command_letters", "terminal"), Map.entry("command_name", "terminal"),
            Map.entry("article_empty", "articles"), Map.entry("block_empty", "articles"), Map.entry("remove_inherited", "saving"));

    private final AntStudioScreen screen;
    private String topic;
    private int contentHeight = 100;

    StudioHelp(AntStudioScreen screen, String topic) {
        this.screen = screen;
        this.topic = TOPICS.contains(topic) ? topic : "start";
    }

    static String topic(String code) { return ISSUES.get(code); }

    @Override
    boolean shade() { return true; }

    @Override
    void layout(StudioKit kit) { center(kit, 440, 300); }

    @Override
    void render(StudioKit kit) {
        kit.box(x, y, w, h, StudioTheme.window, StudioTheme.accent);
        kit.text(StudioText.t("help.title"), x + 9, y + 8, w - 40, StudioTheme.strong);
        kit.button(x + w - 22, y + 4, 16, 16, "X", StudioKit.GHOST, true, "", () -> screen.closePopup(this));
        kit.fill(x + 1, y + 23, w - 2, 1, StudioTheme.line);
        int top = y + 24;
        int rail = Math.min(104, w / 3);
        for (String value : TOPICS) {
            boolean on = value.equals(topic);
            boolean hover = kit.hover(x + 1, top, rail, 16);
            if (on) {
                kit.fill(x + 1, top, rail, 16, StudioTheme.selected);
                kit.fill(x + 1, top, 2, 16, StudioTheme.accent);
            }
            kit.text(StudioText.t("help." + value + ".title"), x + 9, top + 4, rail - 12, on ? StudioTheme.strong : hover ? StudioTheme.accent : StudioTheme.muted);
            kit.press(x + 1, top, rail, 16, () -> {
                topic = value;
                kit.scrollTo("help", 0);
            });
            top += 16;
        }
        kit.fill(x + rail + 1, y + 24, 1, h - 25, StudioTheme.line);
        int left = x + rail + 10;
        int width = w - rail - 22;
        int offset = kit.scroll("help", x + rail + 2, y + 24, w - rail - 4, h - 26, contentHeight);
        kit.clip(x + rail + 2, y + 24, w - rail - 4, h - 26);
        int at = y + 32 - offset;
        int start = at;
        at = kit.wrap(StudioText.t("help." + topic + ".title"), left, at, width, StudioTheme.accent) + 4;
        for (int part = 1; StudioText.has("help." + topic + "." + part); part++)
            at = kit.wrap(Component.translatable("antos.studio.help." + topic + "." + part), left, at, width, StudioTheme.text, 11) + 6;
        contentHeight = at - start + 16;
        kit.unclip();
    }
}
