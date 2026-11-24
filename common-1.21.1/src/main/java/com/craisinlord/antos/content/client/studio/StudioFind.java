package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;

final class StudioFind extends StudioPopup {
    private record Result(String title, String note, StudioIcon icon, int rank, Runnable action) { }

    private final AntStudioScreen screen;
    private final StudioStore store;
    private final boolean copy;
    private String query = "";
    private String builtFor = null;
    private int selected;
    private List<Result> results = List.of();

    StudioFind(AntStudioScreen screen, boolean copy) {
        this.screen = screen;
        this.store = screen.store;
        this.copy = copy;
    }

    private static String t(String key, Object... arguments) { return StudioText.t(key, arguments); }
    String field() { return "find:text"; }

    @Override
    boolean shade() { return true; }

    @Override
    void layout(StudioKit kit) {
        center(kit, 360, 250);
        y = Math.min(y, 40);
    }

    private void build() {
        String state = query + "|" + store.revision();
        if (state.equals(builtFor)) return;
        builtFor = state;
        String text = query.toLowerCase(Locale.ROOT).trim();
        String[] words = text.split("\\s+");
        List<Result> found = new ArrayList<>();
        for (AuthoringKind kind : AuthoringKind.values()) for (StudioKey key : store.keys(kind)) {
            String label = store.label(key);
            String haystack = (label + " " + key.id() + " " + StudioText.kind(kind)).toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String word : words) if (!haystack.contains(word)) { matches = false; break; }
            if (!matches) continue;
            String plain = label.toLowerCase(Locale.ROOT);
            int rank = plain.equals(text) ? 0 : plain.startsWith(text) ? 1 : plain.contains(text) ? 2 : 3;
            if (text.isEmpty()) rank = store.dirty(key) ? 0 : 4;
            found.add(new Result(label, StudioText.kind(kind) + "  " + key.id(), StudioIcons.of(store, key), rank, () -> choose(key)));
        }
        if (!copy) for (Result command : commands()) {
            String haystack = command.title().toLowerCase(Locale.ROOT);
            boolean matches = !text.isEmpty();
            for (String word : words) if (!haystack.contains(word)) { matches = false; break; }
            if (matches) found.add(command);
        }
        found.sort(Comparator.comparingInt(Result::rank).thenComparing(result -> result.title().toLowerCase(Locale.ROOT)));
        results = found.size() > 400 ? List.copyOf(found.subList(0, 400)) : List.copyOf(found);
        selected = Math.max(0, Math.min(results.size() - 1, selected));
    }

    private List<Result> commands() {
        List<Result> result = new ArrayList<>();
        for (StudioCategory category : StudioCategory.values()) result.add(new Result(category.title(), t("find.go"), category.icon, 1, () -> screen.go(category)));
        result.add(new Result(t("save_apply"), t("find.action"), null, 1, screen::saveApply));
        result.add(new Result(t("review_changes"), t("find.action"), null, 1, screen::review));
        result.add(new Result(t(screen.prefs.advanced ? "simple" : "advanced"), t("find.action"), null, 1, () -> screen.setAdvanced(!screen.prefs.advanced)));
        result.add(new Result(t("help"), t("find.action"), null, 1, () -> screen.help("start")));
        for (AuthoringKind kind : AuthoringKind.values()) result.add(new Result(t("new_kind", StudioText.kind(kind)), t("find.action"), StudioCategory.of(kind).icon, 2,
                () -> screen.make(kind, t("untitled", StudioText.kind(kind)), true)));
        return result;
    }

    private void choose(StudioKey key) {
        if (!copy) {
            screen.open(key);
            return;
        }
        store.with(List.of(key), () -> {
            StudioKey made = store.duplicate(key);
            if (made != null) {
                screen.open(made);
                screen.focusTitle(made);
            }
        });
    }

    private void run() {
        build();
        if (results.isEmpty()) return;
        Result result = results.get(selected);
        screen.closePopup(this);
        result.action().run();
    }

    void type(String value) {
        query = value;
        selected = 0;
    }

    void choose() { run(); }

    @Override
    void render(StudioKit kit) {
        build();
        kit.box(x, y, w, h, StudioTheme.window, StudioTheme.accent);
        kit.field(field(), x + 7, y + 7, w - 14, query, new StudioKit.Field().hint(t(copy ? "find.copy" : "find.hint")).max(128).enter(this::run), value -> {
            query = value;
            selected = 0;
            kit.scrollTo("find", 0);
        });
        int top = y + 28;
        int bottom = y + h - 2;
        int offset = kit.scroll("find", x + 1, top, w - 2, bottom - top, results.size() * 22);
        kit.clip(x + 1, top, w - 2, bottom - top);
        for (int index = Math.max(0, offset / 22); index < results.size(); index++) {
            int rowY = top - offset + index * 22;
            if (rowY > bottom) break;
            Result result = results.get(index);
            int choice = index;
            if (kit.pointed(x + 1, rowY, w - 6, 22)) selected = index;
            if (selected == index) {
                kit.fill(x + 1, rowY, w - 2, 22, StudioTheme.selected);
                kit.fill(x + 1, rowY, 2, 22, StudioTheme.accent);
            }
            if (result.icon() != null) result.icon().draw(kit, x + 8, rowY + 4, 14);
            kit.text(result.title(), x + 28, rowY + 3, w - 40, selected == index ? StudioTheme.strong : StudioTheme.text);
            kit.text(result.note(), x + 28, rowY + 12, w - 40, StudioTheme.soft);
            kit.press(x + 1, rowY, w - 6, 22, () -> {
                selected = choice;
                run();
            });
        }
        kit.unclip();
        if (results.isEmpty()) kit.wrap(t("picker.nothing"), x + 10, top + 8, w - 20, StudioTheme.soft);
    }

    @Override
    boolean key(int key, int modifiers) {
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            selected = Math.max(0, Math.min(results.size() - 1, selected + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            int view = h - 30;
            int at = selected * 22;
            int scroll = screen.kit.scrollOf("find");
            if (at < scroll) screen.kit.scrollTo("find", at);
            if (at + 22 > scroll + view) screen.kit.scrollTo("find", at + 22 - view);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            run();
            return true;
        }
        return false;
    }
}
