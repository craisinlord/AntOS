package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.api.client.game.ComputerGameRegistry;
import com.craisinlord.antos.api.client.game.GameIcon;
import com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState;
import com.craisinlord.antos.content.client.ComputerProgramClientState;
import com.craisinlord.antos.content.client.ComputerWorkspaceClientKey;
import com.craisinlord.antos.content.client.game.GameIcons;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;


final class GamesApp extends ComputerApp {
    private final Map<ResourceLocation, ComputerGame.Session> gameSessions = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final AppKit kit = new AppKit(this);
    private List<Entry> entries = List.of();
    private ArchiveLayout layout;
    private boolean layoutGallery;
    private int layoutGames = -1;
    private int libraryScroll;
    private int focus = -1;
    private boolean keyboardFocus;
    private boolean revealFocus;
    private ResourceLocation programId;
    private int scroll;
    private int maximumScroll;
    private int ticks;
    private String programKey = "";
    private String pendingRun = "";
    private int pendingSequence = -1;
    private int gameX;
    private int gameY;
    private int gameWidth;
    private int gameHeight;

    void render(GuiGraphics g, Window window, int x, int y, int w, int h, int mouseX, int mouseY) {
        hits.clear();
        if (window.gameOpen) {
            button(g, x, y, 48, 18, "< BACK", mouseX, mouseY, () -> closeGame(window));
            ResourceLocation id = ResourceLocation.tryParse(window.gameId);
            ComputerGame game = id == null ? null : ComputerGameRegistry.get(id);
            if (game != null) {
                GameIconRenderer.render(screen, g, game.icon(), x + 55, y + 3, 12, (int) (net.minecraft.Util.getMillis() / 50L), false);
                g.drawString(font, Component.literal(screen.trimToWidth(game.title(), w - 76)), x + 71, y + 5, Ui.TEXT_STRONG, false);
            }
            gameX = x;
            gameY = y + 26;
            gameWidth = w;
            gameHeight = Math.max(1, h - 26);
            screen.enableComputerScissor(g, gameX, gameY, gameX + gameWidth, gameY + gameHeight);
            try {
                gameSession(window).ifPresent(session -> session.render(g, font, gameX, gameY, gameWidth, gameHeight, mouseX - gameX, mouseY - gameY));
            } finally {
                g.disableScissor();
            }
            return;
        }
        if (window.programOpen) { renderProgram(g, x, y, w, h, mouseX, mouseY); return; }
        renderLibrary(g, window, x, y, w, h);
    }

    private void renderLibrary(GuiGraphics g, Window window, int x, int y, int w, int h) {
        boolean gallery = screen.session.gamesGallery;
        List<Entry> games = new ArrayList<>();
        installedGames().forEach(game -> games.add(new Entry(game.id(), game.title(), tr("kind.game"), game.icon(), false)));
        List<Entry> programs = new ArrayList<>();
        installedPrograms().forEach(program -> programs.add(new Entry(program.id(), Component.translatable(program.title()).getString(),
                program.type().toUpperCase(java.util.Locale.ROOT), GameIcons.program(program.type()), true)));
        List<Entry> all = new ArrayList<>(games);
        all.addAll(programs);
        entries = List.copyOf(all);
        if (layout == null || layout.width() != w || layout.height() != h || layoutGallery != gallery || layoutGames != games.size() || layout.cells().size() != entries.size()) {
            layout = ArchiveLayout.create(w, h, gallery, games.size(), programs.size());
            layoutGallery = gallery;
            layoutGames = games.size();
        }
        focus = Math.min(focus, entries.size() - 1);
        libraryScroll = Math.max(0, Math.min(layout.maximumScroll(), libraryScroll));
        if (revealFocus) {
            libraryScroll = layout.reveal(focus, libraryScroll);
            revealFocus = false;
        }
        kit.begin();
        g.drawString(font, Component.literal(screen.trimToWidth(Component.translatable("computer.antos.programs.library").getString(), w - 50)), x, y + 5, Ui.TEXT_STRONG, false);
        kit.iconButton(g, x + w - 39, y, 18, FileExplorerApp.icon("archive_list"), !gallery, true, tr("list_view"), () -> setGallery(false));
        kit.iconButton(g, x + w - 18, y, 18, FileExplorerApp.icon("archive_grid"), gallery, true, tr("icon_view"), () -> setGallery(true));
        g.drawString(font, Component.literal(screen.trimToWidth(Component.translatable("computer.antos.programs.library_hint").getString(), w - 8)), x, y + 25, Ui.TEXT_MUTED, false);
        int top = y + ArchiveLayout.BODY_TOP;
        int bottom = top + layout.viewportHeight();
        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("computer.antos.programs.empty"), x + 6, top + 12, Ui.TEXT, false);
            g.drawString(font, Component.translatable("computer.antos.programs.find_disks"), x + 6, top + 27, Ui.TEXT_MUTED, false);
        }
        int ticks = (int) (net.minecraft.Util.getMillis() / 50L);
        screen.enableComputerScissor(g, x, top, x + w - 8, bottom);
        kit.clip(x, top, w - 8, layout.viewportHeight());
        try {
            for (ArchiveLayout.Section section : layout.sections()) {
                int sectionY = top + section.y() - libraryScroll;
                if (sectionY + ArchiveLayout.HEADING <= top || sectionY >= bottom) continue;
                String label = tr(section.folders() ? "section.games" : "section.programs");
                g.drawString(font, Component.literal(label), x + 3, sectionY + 3, Ui.TEXT_MUTED, false);
                g.fill(x + 8 + font.width(label), sectionY + 7, x + w - 12, sectionY + 8, Ui.LINE);
            }
            for (ArchiveLayout.Cell cell : layout.cells()) {
                int cellY = top + cell.y() - libraryScroll;
                if (cellY + cell.height() <= top || cellY >= bottom) continue;
                renderCell(g, window, entries.get(cell.index()), cell.index(), gallery, x + cell.x(), cellY, cell.width() - 3, cell.height() - 2, ticks);
            }
        } finally {
            kit.unclip();
            g.disableScissor();
        }
        kit.scrollbar(g, x + w - 4, top, layout.viewportHeight(), layout.viewportHeight(), layout.contentHeight(), libraryScroll, layout.maximumScroll(), value -> libraryScroll = value);
        g.fill(x, y + h - 11, x + w, y + h - 10, Ui.LINE);
        g.drawString(font, Component.literal(screen.trimToWidth(tr("summary", games.size(), programs.size()), w - 8)), x + 3, y + h - 8, Ui.TEXT_MUTED, false);
        kit.drawTooltip(g, x, y, x + w, y + h);
    }

    private void renderCell(GuiGraphics g, Window window, Entry entry, int index, boolean gallery, int x, int y, int w, int h, int ticks) {
        boolean hover = kit.hovered(x, y, w, h);
        boolean focused = keyboardFocus && index == focus;
        if (gallery || hover || focused) g.fill(x, y, x + w, y + h, hover ? Ui.HOVER : Ui.PANEL);
        if (focused) kit.outline(g, x, y, w, h, Ui.FOCUS);
        int art = gallery ? 36 : 26;
        int artX = gallery ? x + (w - art) / 2 : x + 3;
        int artY = gallery ? y + (hover ? 3 : 4) : y + 2;
        GameIconRenderer.render(screen, g, entry.icon(), artX, artY, art, ticks, hover || focused);
        int textWidth = gallery ? w - 10 : w - 52;
        String title = screen.trimToWidth(entry.title(), textWidth);
        String kind = screen.trimToWidth(entry.kind(), textWidth);
        if (gallery) {
            g.drawString(font, Component.literal(title), x + (w - font.width(title)) / 2, y + 45, Ui.TEXT_STRONG, false);
            g.drawString(font, Component.literal(kind), x + (w - font.width(kind)) / 2, y + 56, Ui.TEXT_MUTED, false);
        } else {
            g.drawString(font, Component.literal(title), x + 36, y + 5, Ui.TEXT_STRONG, false);
            g.drawString(font, Component.literal(kind), x + 36, y + 16, Ui.TEXT_MUTED, false);
            g.drawString(font, Component.literal(">"), x + w - 10, y + 11, hover || focused ? Ui.ACCENT : Ui.TEXT, false);
        }
        if (hover && !title.equals(entry.title())) kit.tooltip(entry.title());
        kit.hit(x, y, w, h, () -> {
            focus = index;
            keyboardFocus = false;
            open(window, entry);
        });
    }

    private void open(Window window, Entry entry) {
        scroll = 0;
        if (entry.program()) openProgram(entry.id());
        else {
            window.gameId = entry.id().toString();
            window.gameOpen = true;
        }
    }

    private void setGallery(boolean gallery) {
        if (screen.session.gamesGallery == gallery) return;
        if (focus < 0 && layout != null) {
            layout.cells().stream().filter(cell -> cell.y() + cell.height() > libraryScroll).findFirst().ifPresent(cell -> focus = cell.index());
        }
        screen.session.gamesGallery = gallery;
        revealFocus = true;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("computer.antos.programs." + key, args).getString();
    }

    private JsonObject layoutRun;
    private int layoutWidth;
    private Object layoutLanguage;
    private List<net.minecraft.util.FormattedCharSequence> layoutLines = List.of();
    private List<List<net.minecraft.util.FormattedCharSequence>> layoutWrapped = List.of();

    private void renderProgram(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        button(g, x, y, 48, 18, "< BACK", mouseX, mouseY, this::back);
        var definition = ComputerProgramClientState.definition(programId);
        String title = definition == null ? String.valueOf(programId) : Component.translatable(definition.title()).getString();
        g.drawString(font, Component.literal(screen.trimToWidth(title, w - 60)), x + 58, y + 5, Ui.TEXT_STRONG, false);
        JsonObject run = ComputerProgramClientState.run();
        if (run == null || run.has("program") && !value(run, "program").equals(String.valueOf(programId))) {
            g.drawString(font, Component.translatable("computer.antos.programs.loading"), x + 8, y + 42, Ui.TEXT, false);
            return;
        }
        String status = value(run, "status");
        if (!value(run, "run").equals(pendingRun) || number(run, "sequence") != pendingSequence) pendingSequence = -1;
        boolean done = status.endsWith("_done") || status.equals("aborted") || !run.has("choices");
        Object language = net.minecraft.locale.Language.getInstance();
        if (run != layoutRun || w != layoutWidth || language != layoutLanguage) {
            List<net.minecraft.util.FormattedCharSequence> builtLines = new ArrayList<>();
            String text = value(run, "text");
            if (!text.isEmpty()) builtLines.addAll(font.split(Component.translatable(text), w - 30));
            if (status.equals("quiz_done")) builtLines.addAll(font.split(Component.translatable("computer.antos.programs.score", number(run, "score"), number(run, "total")), w - 30));
            if (run.has("ending")) builtLines.addAll(font.split(Component.translatable("computer.antos.programs.ending", value(run, "ending")), w - 30));
            if (run.has("password")) builtLines.addAll(font.split(Component.translatable("computer.antos.programs.password", value(run, "password")), w - 30));
            if (!status.equals("quiz") && !status.equals("adventure") && !status.endsWith("_done")) builtLines.addAll(font.split(Component.translatable("computer.antos.programs.status." + status), w - 30));
            List<String> builtChoices = new ArrayList<>();
            if (run.has("choices")) run.getAsJsonArray("choices").forEach(choice -> builtChoices.add(choice.getAsString()));
            layoutLines = List.copyOf(builtLines);
            layoutWrapped = builtChoices.stream().map(choice -> font.split(Component.translatable(choice), w - 48)).toList();
            layoutRun = run;
            layoutWidth = w;
            layoutLanguage = language;
        }
        List<net.minecraft.util.FormattedCharSequence> lines = layoutLines;
        if (status.equals("quiz")) g.drawString(font, Component.translatable("computer.antos.programs.question", number(run, "question_number"), number(run, "total")), x + 8, y + 27, Ui.TEXT, false);
        else g.drawString(font, Component.translatable("computer.antos.programs.console"), x + 8, y + 27, Ui.TEXT, false);
        int top = y + 44;
        int bodyHeight = Math.max(1, h - 50);
        panel(g, x, top, w, bodyHeight, false);
        int preview = !value(run, "item").isBlank() || !value(run, "entity").isBlank() ? (value(run, "entity").isBlank() ? 60 : 108) : 0;
        int total = preview + lines.size() * 11 + 14;
        List<List<net.minecraft.util.FormattedCharSequence>> wrapped = layoutWrapped;
        for (var row : wrapped) total += Math.max(26, row.size() * 11 + 12) + 4;
        if (done) total += 34;
        maximumScroll = Math.max(0, total - bodyHeight + 12);
        scroll = Math.max(0, Math.min(scroll, maximumScroll));
        screen.enableComputerScissor(g, x + 2, top + 2, x + w - 7, top + bodyHeight - 2);
        int cursor = top + 8 - scroll;
        if (preview > 0) {
            boolean previousTint = screen.archiveGreenTint;
            screen.archiveGreenTint = !run.has("green_tint") || run.get("green_tint").getAsBoolean();
            screen.renderArchivePreview(g, value(run, "item"), value(run, "entity"), "", x + 8, cursor, w - 24);
            screen.archiveGreenTint = previousTint;
            cursor += preview;
        }
        for (var line : lines) { g.drawString(font, line, x + 10, cursor, Ui.TEXT, false); cursor += 11; }
        cursor += 14;
        for (int index = 0; index < wrapped.size(); index++) {
            var row = wrapped.get(index);
            int height = Math.max(26, row.size() * 11 + 12);
            int rowTop = cursor;
            int choice = index;
            int hitTop = Math.max(rowTop, top + 2);
            int hitBottom = Math.min(rowTop + height, top + bodyHeight - 2);
            panel(g, x + 8, rowTop, w - 24, height, pendingSequence < 0 && screen.inside(x + 8, rowTop, w - 24, height, mouseX, mouseY));
            g.drawString(font, Component.literal((index + 1) + ">"), x + 14, rowTop + 7, Ui.TEXT_STRONG, false);
            for (int line = 0; line < row.size(); line++) g.drawString(font, row.get(line), x + 34, rowTop + 7 + line * 11, Ui.TEXT, false);
            if (hitBottom > hitTop) hits.add(new Hit(x + 8, hitTop, w - 24, hitBottom - hitTop, () -> choose(choice)));
            cursor += height + 4;
        }
        if (done) {
            int restartTop = cursor;
            panel(g, x + 8, restartTop, w - 24, 26, screen.inside(x + 8, restartTop, w - 24, 26, mouseX, mouseY));
            g.drawString(font, Component.translatable("computer.antos.programs.restart"), x + 16, restartTop + 8, Ui.TEXT_STRONG, false);
            if (restartTop >= top + 2 && restartTop + 26 <= top + bodyHeight - 2) hits.add(new Hit(x + 8, restartTop, w - 24, 26, () -> openProgram(programId)));
        }
        g.disableScissor();
        screen.drawScrollbar(g, x + w - 4, top + 4, bodyHeight - 8, bodyHeight, total, scroll, maximumScroll);
    }

    void openProgram(ResourceLocation id) {
        Window window = screen.activeWindow;
        if (window == null || !window.type.equals("GAMES")) return;
        programId = id;
        window.programOpen = true;
        window.gameOpen = false;
        scroll = 0;
        ticks = 0;
        pendingSequence = -1;
        programKey = ComputerWorkspaceClientKey.of();
        ComputerProgramClientState.clearRun();
        ComputerNetworking.startProgram(id);
    }

    private void choose(int index) {
        JsonObject run = ComputerProgramClientState.run();
        if (run == null || pendingSequence >= 0 || !run.has("run") || !run.has("choices") || index >= run.getAsJsonArray("choices").size()) return;
        pendingRun = value(run, "run");
        pendingSequence = number(run, "sequence");
        scroll = 0;
        ComputerNetworking.chooseProgram(pendingRun, pendingSequence, index);
    }

    private void closeGame(Window window) {
        gameSession(window).ifPresent(ComputerGame.Session::close);
        gameSessions.remove(ResourceLocation.tryParse(window.gameId));
        window.gameOpen = false;
    }

    private void back() { if (screen.activeWindow != null) screen.activeWindow.programOpen = false; scroll = 0; pendingSequence = -1; }

    private List<ComputerGame> installedGames() {
        List<ResourceLocation> disks = screen.physicalDisks();
        var activated = ComputerArchiveUnlockClientState.activatedDisks();
        if (ComputerGuideData.unlockAllGameEntries()) return ComputerGameRegistry.all().stream().toList();
        return ComputerGameRegistry.all().stream().filter(game -> game.includedByDefault() || game.diskId() != null && disks.contains(game.diskId()) && activated.contains(game.diskId())).toList();
    }

    private List<ComputerProgramClientState.Definition> installedPrograms() {
        var activated = ComputerArchiveUnlockClientState.activatedDisks();
        List<ResourceLocation> available = screen.physicalDisks().stream().filter(activated::contains).map(ComputerGuideData::disk).filter(java.util.Objects::nonNull).flatMap(disk -> disk.features().programs().stream()).distinct().toList();
        var granted = ComputerProgramClientState.grantedPrograms();
        return ComputerProgramClientState.definitions().stream().filter(program -> available.contains(program.id()) || granted.contains(program.id())).toList();
    }

    void tick(Window window) {
        if (window.gameOpen) gameSession(window).ifPresent(ComputerGame.Session::tick);
        if (!window.programOpen) return;
        ticks++;
        String key = ComputerWorkspaceClientKey.of();
        if (!key.equals(programKey)) { programKey = key; ticks = 0; ComputerNetworking.requestProgramState(); }
        else if (ticks % (ComputerProgramClientState.run() == null ? 40 : 400) == 0) ComputerNetworking.requestProgramState();
    }

    java.util.Optional<ComputerGame.Session> gameSession(Window window) {
        if (window == null || window.gameId == null) return java.util.Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(window.gameId);
        ComputerGame game = id == null ? null : ComputerGameRegistry.get(id);
        if (game == null) return java.util.Optional.empty();
        return java.util.Optional.of(gameSessions.computeIfAbsent(id, ignored -> game.create()));
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        if (window.gameOpen) {
            if (button == 0) for (Hit hit : List.copyOf(hits)) if (screen.inside(hit.x(), hit.y(), hit.width(), hit.height(), mouseX, mouseY)) { hit.action().run(); return true; }
            if (screen.inside(gameX, gameY, gameWidth, gameHeight, mouseX, mouseY)) gameSession(window).ifPresent(game -> game.mouseClicked(mouseX - gameX, mouseY - gameY, button));
            return true;
        }
        if (window.programOpen) {
            if (button == 0) for (Hit hit : List.copyOf(hits)) if (screen.inside(hit.x(), hit.y(), hit.width(), hit.height(), mouseX, mouseY)) { hit.action().run(); break; }
            return true;
        }
        if (button == 0) kit.click(mouseX, mouseY, button);
        return true;
    }

    void drag(double mouseY) { kit.drag(mouseY); }
    void release() { kit.release(); }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        if (window.programOpen) scroll = Math.max(0, Math.min(maximumScroll, scroll - (int) Math.signum(scrollY) * 22));
        else if (!window.gameOpen && layout != null) libraryScroll = Math.max(0, Math.min(layout.maximumScroll(), libraryScroll - (int) Math.signum(scrollY) * 16));
        return true;
    }
    boolean type(Window window, char codePoint, int modifiers) { return window.gameOpen && gameSession(window).map(game -> game.charTyped(codePoint)).orElse(false); }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (window.programOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { back(); return true; }
            if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_8) { choose(keyCode - GLFW.GLFW_KEY_1); return true; }
            if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_8) { choose(keyCode - GLFW.GLFW_KEY_KP_1); return true; }
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) { scroll = Math.max(0, Math.min(maximumScroll, scroll + (keyCode == GLFW.GLFW_KEY_DOWN ? 22 : -22))); return true; }
        }
        if (window.gameOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeGame(window); return true; }
            return gameSession(window).map(game -> game.keyPressed(keyCode)).orElse(false);
        }
        if (window.programOpen || layout == null || entries.isEmpty()) return false;
        int dx = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
        int dy = keyCode == GLFW.GLFW_KEY_UP ? -1 : keyCode == GLFW.GLFW_KEY_DOWN ? 1 : 0;
        if (dx != 0 || dy != 0) {
            focus = !keyboardFocus || focus < 0 ? Math.max(0, focus) : layout.move(focus, dx, dy);
            keyboardFocus = true;
            revealFocus = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END) {
            focus = keyCode == GLFW.GLFW_KEY_HOME ? 0 : entries.size() - 1;
            keyboardFocus = true;
            revealFocus = true;
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) && keyboardFocus && focus >= 0 && focus < entries.size()) {
            Entry entry = entries.get(focus);
            AntOSUiSounds.action(() -> open(window, entry));
            return true;
        }
        return false;
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, boolean hover) { g.fill(x, y, x + w, y + h, hover ? Ui.GAME_HOVER : Ui.GAME_FILL); g.renderOutline(x, y, w, h, Ui.GAME_OUTLINE); }
    private void button(GuiGraphics g, int x, int y, int w, int h, String text, int mouseX, int mouseY, Runnable action) { panel(g, x, y, w, h, screen.inside(x, y, w, h, mouseX, mouseY)); g.drawString(font, Component.literal(text), x + 5, y + 5, Ui.TEXT_STRONG, false); hits.add(new Hit(x, y, w, h, () -> AntOSUiSounds.action(action))); }
    private static String value(JsonObject root, String key) { return root.has(key) ? root.get(key).getAsString() : ""; }
    private static int number(JsonObject root, String key) { return root.has(key) ? root.get(key).getAsInt() : 0; }
    private record Entry(ResourceLocation id, String title, String kind, GameIcon icon, boolean program) { }
    private record Hit(int x, int y, int width, int height, Runnable action) { }
}
