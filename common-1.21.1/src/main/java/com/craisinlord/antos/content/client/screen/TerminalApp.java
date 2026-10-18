package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.content.client.AntOSUiSounds.Cue;
import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.antmail.AntmailWire;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.client.ComputerTerminalClientState;
import com.craisinlord.antos.content.computer.terminal.TerminalText;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.BLACK;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.DARK_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HOVER_FILL;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;

final class TerminalApp extends ComputerApp {
    private static final int LINE = 11;
    private static final int MAX_ENTRIES = 500;
    private static final int MAX_HISTORY = 50;
    private static final int MAX_INPUT = 256;
    private static final int COMMAND = 0;
    private static final int OUTPUT = 1;
    private static final int ERROR = 2;
    private static final int SYSTEM = 3;
    private static final int DIRECTORY = 4;
    private static final int HELP = 5;
    private static final List<String> COMMANDS = List.of(
            "help", "clear", "pwd", "ls", "cd", "mkdir", "touch", "cat", "write", "rm", "rmdir", "mv", "open", "decrypt");
    private static final Pattern INLINE_PASSWORD = Pattern.compile("(?i)^\\s*decrypt\\s+\\S+\\s+(.+)$");

    final State state = new State();
    final AppKit kit = new AppKit(this);
    private final List<Visual> visuals = new ArrayList<>();
    private ResourceLocation passwordDisk;
    private long decryptionRevision;
    private int layoutVersion = -1;
    private int layoutWidth = -1;
    private Object layoutLanguage;
    private java.util.IdentityHashMap<Entry, Layout> entryLayouts = new java.util.IdentityHashMap<>();
    private java.util.IdentityHashMap<Entry, Layout> spareLayouts = new java.util.IdentityHashMap<>();
    private Entry typingPlainEntry;
    private Object typingPlainLanguage;
    private int typingPlainLength;
    private int textX;
    private int textTop;
    private int textWidth = 1;
    private int visibleRows = 1;
    private int promptStartRow;
    private List<int[]> promptRows = List.of();
    private String promptText = "";
    private int promptLength;
    private boolean busy;
    private long busySince;
    private String notice = "";
    private long noticeUntil;
    private int tabPresses;
    private boolean listed;

    void render(GuiGraphics g, int x, int y, int w, int h) {
        if (!listed) {
            listed = true;
            ComputerNetworking.listFiles();
            if (com.craisinlord.antos.config.AntOSSettings.preferences().terminalKeepHistory && state.history.isEmpty()) {
                state.history.addAll(com.craisinlord.antos.config.AntOSSettings.terminalHistory(com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of()));
            }
        }
        if (busy && System.currentTimeMillis() - busySince > 6000L) busy = false;
        pump();
        kit.begin();
        boolean active = screen.activeWindow != null && screen.activeWindow.type.equals("TERMINAL");
        int bottom = y + h - 12;
        g.fill(x, y, x + w, bottom, BLACK);
        screen.box(g, x, y, x + w, bottom, active ? PALE_GREEN : AppKit.SOFT);
        textX = x + 6;
        textTop = y + 6;
        textWidth = Math.max(20, w - 18);
        visibleRows = Math.max(1, (bottom - 3 - textTop) / LINE);
        layoutOutput();
        layoutPrompt();
        int total = visuals.size() + promptRows.size();
        state.maximumScroll = Math.max(0, total - visibleRows);
        if (state.follow) state.scroll = state.maximumScroll;
        state.scroll = Math.max(0, Math.min(state.maximumScroll, state.scroll));
        if (state.scroll >= state.maximumScroll) {
            state.follow = true;
            state.unseen = false;
        }
        kit.hit(x, y, w, bottom - y, this::pressConsole);
        for (int row = state.scroll; row < total && row < state.scroll + visibleRows; row++) {
            int lineY = textTop + (row - state.scroll) * LINE;
            if (row < visuals.size()) drawVisual(g, visuals.get(row), lineY);
            else drawPromptRow(g, row - visuals.size(), lineY);
        }
        if (active && screen.caretVisible() && !promptRows.isEmpty()) {
            int caret = promptLength + state.cursor;
            int caretRow = promptRows.size() - 1;
            for (int index = 0; index < promptRows.size(); index++) {
                int[] segment = promptRows.get(index);
                if (caret >= segment[0] && (caret < segment[1] || index == promptRows.size() - 1)) {
                    caretRow = index;
                    break;
                }
            }
            int visualRow = visuals.size() + caretRow;
            if (visualRow >= state.scroll && visualRow < state.scroll + visibleRows) {
                int[] segment = promptRows.get(caretRow);
                int caretX = textX + font.width(promptText.substring(segment[0], Math.min(caret, segment[1])));
                int caretY = textTop + (visualRow - state.scroll) * LINE;
                g.fill(caretX, caretY - 1, caretX + 1, caretY + 9, GREEN);
            }
        }
        if (state.unseen) {
            String label = tr("new_output");
            int width = font.width(label) + 10;
            int markerX = x + w - width - 10;
            int markerY = bottom - 18;
            boolean hover = kit.hovered(markerX, markerY, width, 14);
            g.fill(markerX, markerY, markerX + width, markerY + 14, hover ? HOVER_FILL : DARK_GREEN);
            kit.outline(g, markerX, markerY, width, 14, GREEN);
            g.drawString(font, Component.literal(label), markerX + 5, markerY + 3, PALE_GREEN, false);
            kit.hit(markerX, markerY, width, 14, this::jumpToBottom);
        }
        kit.scrollbar(g, x + w - 6, y + 4, bottom - y - 8, visibleRows, total, state.scroll, state.maximumScroll, value -> {
            state.scroll = value;
            state.follow = value >= state.maximumScroll;
        });
        renderFooter(g, x, y + h - 10, w);
        kit.drawTooltip(g, x, y, x + w, y + h);
    }

    private void renderFooter(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, DARK_GREEN);
        String right;
        int rightColor = AppKit.MUTED;
        if (System.currentTimeMillis() < noticeUntil) {
            right = notice;
            rightColor = PALE_GREEN;
        } else if (revealing()) {
            right = tr("footer.skip");
        } else if (busy) {
            right = tr("busy") + ".".repeat((int) (System.currentTimeMillis() / 300L % 4L));
            rightColor = PALE_GREEN;
        } else if (passwordDisk != null) {
            right = tr("footer.password");
        } else {
            right = tr("footer.hint");
        }
        String shownRight = screen.trimToWidth(right, Math.max(10, w / 2));
        int rightWidth = font.width(shownRight);
        String left = tr("footer.directory", state.directory);
        g.drawString(font, Component.literal(screen.trimToWidth(left, Math.max(10, w - rightWidth - 8))), x, y + 2, GREEN, false);
        g.drawString(font, Component.literal(shownRight), x + w - rightWidth, y + 2, rightColor, false);
    }

    private void drawVisual(GuiGraphics g, Visual visual, int lineY) {
        g.drawString(font, visual.line(), textX + visual.indent(), lineY, visual.color(), false);
        if (visual.second() != null) g.drawString(font, visual.second(), textX + visual.secondX(), lineY, visual.secondColor(), false);
    }

    private void drawPromptRow(GuiGraphics g, int index, int lineY) {
        int[] segment = promptRows.get(index);
        int start = segment[0];
        int end = segment[1];
        int split = Math.max(start, Math.min(end, promptLength));
        int promptColor = busy || revealing() ? AppKit.MUTED : passwordDisk != null ? AppKit.PLACEHOLDER : GREEN;
        if (split > start) g.drawString(font, Component.literal(promptText.substring(start, split) + (end == split && segment[2] == 1 ? "-" : "")), textX, lineY, promptColor, false);
        if (end > split) {
            int offset = font.width(promptText.substring(start, split));
            g.drawString(font, Component.literal(promptText.substring(split, end) + (segment[2] == 1 ? "-" : "")), textX + offset, lineY, PALE_GREEN, false);
        }
    }

    private void layoutOutput() {
        if (layoutVersion == state.version && layoutWidth == textWidth) return;
        Object language = net.minecraft.locale.Language.getInstance();
        if (layoutWidth != textWidth || layoutLanguage != language) {
            entryLayouts.clear();
            layoutLanguage = language;
        }
        layoutVersion = state.version;
        layoutWidth = textWidth;
        visuals.clear();
        int helpColumn = 0;
        for (String command : COMMANDS) helpColumn = Math.max(helpColumn, font.width(usage(command)));
        for (Entry entry : state.output) if (entry.kind() == HELP && custom(entry)) helpColumn = Math.max(helpColumn, font.width(helpUsage(entry)));
        helpColumn = Math.min(helpColumn + 10, textWidth / 2);
        java.util.IdentityHashMap<Entry, Layout> next = spareLayouts;
        next.clear();
        for (Entry entry : state.output) {
            int shown = entry == state.typing ? state.typingShown : -1;
            int column = entry.kind() == HELP ? helpColumn : 0;
            Layout layout = entryLayouts.get(entry);
            if (layout == null || layout.shown() != shown || layout.helpColumn() != column) layout = new Layout(column, shown, layoutEntry(entry, shown, column));
            next.put(entry, layout);
            visuals.addAll(layout.rows());
        }
        spareLayouts = entryLayouts;
        spareLayouts.clear();
        entryLayouts = next;
    }

    private List<Visual> layoutEntry(Entry entry, int shown, int helpColumn) {
        List<Visual> rows = new ArrayList<>();
        if (entry.kind() == HELP) {
            String usage = helpUsage(entry);
            String description = helpDescription(entry);
            if (font.width(usage) + 6 <= helpColumn && helpColumn + font.width(description) <= textWidth) {
                rows.add(new Visual(Component.literal(usage).getVisualOrderText(), GREEN, 0,
                        Component.literal(description).getVisualOrderText(), PALE_GREEN, helpColumn, entry));
            } else {
                for (FormattedCharSequence line : font.split(Component.literal(usage), textWidth)) rows.add(new Visual(line, GREEN, 0, null, 0, 0, entry));
                for (FormattedCharSequence line : font.split(Component.literal(description), Math.max(10, textWidth - 10))) rows.add(new Visual(line, PALE_GREEN, 10, null, 0, 0, entry));
            }
            return rows;
        }
        String text = TerminalText.plain(entry.text());
        if (shown >= 0) text = text.substring(0, Math.min(text.length(), shown));
        int color = switch (entry.kind()) {
            case COMMAND, DIRECTORY -> GREEN;
            case ERROR -> AppKit.ERROR;
            case SYSTEM -> AppKit.MUTED;
            default -> PALE_GREEN;
        };
        List<FormattedCharSequence> lines = font.split(Component.literal(text), textWidth);
        if (lines.isEmpty()) rows.add(new Visual(FormattedCharSequence.EMPTY, color, 0, null, 0, 0, entry));
        for (FormattedCharSequence line : lines) rows.add(new Visual(line, color, 0, null, 0, 0, entry));
        return rows;
    }

    private String usage(String command) {
        return Component.translatable(TerminalText.PREFIX + "help.usage." + command).getString();
    }

    private void layoutPrompt() {
        String prompt = passwordDisk != null ? tr("password_prompt") : state.directory + "> ";
        promptLength = prompt.length();
        promptText = prompt + displayInput();
        List<int[]> rows = new ArrayList<>();
        for (EditableTextWrap.Line line : EditableTextWrap.wrap(promptText, textWidth, font::width)) {
            rows.add(new int[]{line.start(), line.end(), line.hyphen() ? 1 : 0});
        }
        promptRows = rows;
        promptStartRow = visuals.size();
    }

    private String displayInput() {
        if (passwordDisk != null) return "*".repeat(state.input.length());
        Matcher matcher = INLINE_PASSWORD.matcher(state.input);
        if (matcher.matches()) return state.input.substring(0, matcher.start(1)) + "*".repeat(matcher.group(1).length());
        return state.input;
    }

    private void pressConsole() {
        int row = state.scroll + (int) Math.floor((kit.clickY() - textTop + 1) / LINE);
        if (kit.clickButton() == 1) {
            if (row >= 0 && row < visuals.size()) {
                Entry entry = visuals.get(row).entry();
                String text = entry.kind() == HELP ? helpText(entry) : TerminalText.plain(entry.text());
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
                notice(tr("copied"));
            }
            return;
        }
        if (kit.clickButton() != 0) return;
        int promptRow = row - promptStartRow;
        if (promptRow < 0 || promptRow >= promptRows.size()) return;
        int[] segment = promptRows.get(promptRow);
        double pixel = kit.clickX() - textX;
        int offset = segment[1];
        int width = 0;
        for (int index = segment[0]; index < segment[1]; index++) {
            int characterWidth = font.width(String.valueOf(promptText.charAt(index)));
            if (pixel < width + characterWidth / 2.0) {
                offset = index;
                break;
            }
            width += characterWidth;
        }
        state.cursor = Math.max(0, Math.min(state.input.length(), offset - promptLength));
    }

    private String helpText(Entry entry) {
        return helpUsage(entry) + "  " + helpDescription(entry);
    }

    private boolean custom(Entry entry) {
        return TerminalText.key(entry.text()).endsWith("help.custom");
    }

    private String helpUsage(Entry entry) {
        List<String> arguments = TerminalText.arguments(entry.text());
        String command = arguments.isEmpty() ? "" : arguments.get(0);
        return custom(entry) ? command : usage(command);
    }

    private String helpDescription(Entry entry) {
        List<String> arguments = TerminalText.arguments(entry.text());
        if (custom(entry)) {
            String description = arguments.size() > 1 ? arguments.get(1) : "";
            return description.isBlank() ? "" : net.minecraft.locale.Language.getInstance().getOrDefault(description, description);
        }
        String command = arguments.isEmpty() ? "" : arguments.get(0);
        return Component.translatable(TerminalText.PREFIX + "help.description." + command).getString();
    }

    void drag(double mouseX, double mouseY) {
        if (kit.draggingScrollbar()) kit.drag(mouseY);
    }

    void release() {
        kit.release();
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        kit.click(mouseX, mouseY, button);
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        scrollBy(-(int) Math.signum(scrollY) * 3);
        return true;
    }

    private void scrollBy(int amount) {
        state.scroll = Math.max(0, Math.min(state.maximumScroll, state.scroll + amount));
        state.follow = state.scroll >= state.maximumScroll;
    }

    private void jumpToBottom() {
        state.follow = true;
        state.unseen = false;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (codePoint < 32 || codePoint == 127) return false;
        if (state.input.length() >= MAX_INPUT) return true;
        state.input = state.input.substring(0, state.cursor) + codePoint + state.input.substring(state.cursor);
        state.cursor++;
        edited();
        return true;
    }

    private void edited() {
        tabPresses = 0;
        jumpToBottom();
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        boolean control = screen.hasControl(modifiers);
        if (keyCode != GLFW.GLFW_KEY_TAB) tabPresses = 0;
        if (control) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_V -> {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        String text = clip.replace("\r", "").replace("\n", " ").replace("\t", " ");
                        text = text.substring(0, Math.min(text.length(), MAX_INPUT - state.input.length()));
                        state.input = state.input.substring(0, state.cursor) + text + state.input.substring(state.cursor);
                        state.cursor += text.length();
                        edited();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_U -> {
                    state.input = state.input.substring(state.cursor);
                    state.cursor = 0;
                    edited();
                    return true;
                }
                case GLFW.GLFW_KEY_L -> {
                    if (revealing()) skipReveal();
                    state.output.clear();
                    state.version++;
                    jumpToBottom();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    if (revealing()) {
                        skipReveal();
                        return true;
                    }
                    if (!state.input.isEmpty() || passwordDisk != null) {
                        add(COMMAND, promptLabel() + displayInput() + "^C");
                        passwordDisk = null;
                        state.input = "";
                        state.cursor = 0;
                        state.historyIndex = -1;
                        jumpToBottom();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_A -> {
                    state.cursor = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_E -> {
                    state.cursor = state.input.length();
                    return true;
                }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    int start = previousWord(state.cursor);
                    state.input = state.input.substring(0, start) + state.input.substring(state.cursor);
                    state.cursor = start;
                    edited();
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    state.cursor = previousWord(state.cursor);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    state.cursor = nextWord(state.cursor);
                    return true;
                }
                default -> { }
            }
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                execute();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (state.cursor > 0) {
                    state.input = state.input.substring(0, state.cursor - 1) + state.input.substring(state.cursor);
                    state.cursor--;
                }
                edited();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (state.cursor < state.input.length()) state.input = state.input.substring(0, state.cursor) + state.input.substring(state.cursor + 1);
                edited();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                state.cursor = Math.max(0, state.cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                state.cursor = Math.min(state.input.length(), state.cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                state.cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                state.cursor = state.input.length();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                history(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                history(1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                scrollBy(-visibleRows);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scrollBy(visibleRows);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                complete();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (revealing()) {
                    skipReveal();
                    return true;
                }
                if (passwordDisk != null) {
                    passwordDisk = null;
                    state.input = "";
                    state.cursor = 0;
                    add(SYSTEM, TerminalText.of("client.decrypt_cancelled"));
                    return true;
                }
                if (!state.input.isEmpty()) {
                    state.input = "";
                    state.cursor = 0;
                    state.historyIndex = -1;
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private int previousWord(int offset) {
        int index = offset;
        while (index > 0 && state.input.charAt(index - 1) == ' ') index--;
        while (index > 0 && state.input.charAt(index - 1) != ' ' && state.input.charAt(index - 1) != '/') index--;
        if (index == offset && index > 0) index--;
        return index;
    }

    private int nextWord(int offset) {
        int index = offset;
        while (index < state.input.length() && state.input.charAt(index) == ' ') index++;
        while (index < state.input.length() && state.input.charAt(index) != ' ' && state.input.charAt(index) != '/') index++;
        if (index == offset && index < state.input.length()) index++;
        return index;
    }

    private void history(int direction) {
        if (passwordDisk != null || state.history.isEmpty()) return;
        if (direction < 0) {
            if (state.historyIndex < 0) {
                state.draft = state.input;
                state.historyIndex = state.history.size() - 1;
            } else state.historyIndex = Math.max(0, state.historyIndex - 1);
            state.input = state.history.get(state.historyIndex);
        } else {
            if (state.historyIndex < 0) return;
            state.historyIndex++;
            if (state.historyIndex >= state.history.size()) {
                state.historyIndex = -1;
                state.input = state.draft;
            } else state.input = state.history.get(state.historyIndex);
        }
        state.cursor = state.input.length();
        jumpToBottom();
    }

    private void complete() {
        if (passwordDisk != null) return;
        String before = state.input.substring(0, state.cursor);
        int tokenStart = 0;
        for (int index = before.length() - 1; index >= 0; index--) {
            if (before.charAt(index) == ' ' && (index == 0 || before.charAt(index - 1) != '\\')) {
                tokenStart = index + 1;
                break;
            }
        }
        String token = before.substring(tokenStart);
        boolean commandToken = before.substring(0, tokenStart).isBlank();
        String folderPart;
        String prefix;
        List<String> candidates = new ArrayList<>();
        List<Boolean> folders = new ArrayList<>();
        if (commandToken) {
            folderPart = "";
            prefix = token.toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>(COMMANDS);
            for (String command : com.craisinlord.antos.content.client.ComputerProgramClientState.terminalCommands()) if (!names.contains(command)) names.add(command);
            for (String command : names) {
                if (command.startsWith(prefix)) {
                    candidates.add(command);
                    folders.add(false);
                }
            }
        } else {
            String unescaped = token.replace("\\ ", " ");
            int slash = unescaped.lastIndexOf('/');
            folderPart = slash >= 0 ? unescaped.substring(0, slash + 1) : "";
            prefix = unescaped.substring(slash + 1);
            String base = resolve(folderPart.isEmpty() ? "." : folderPart);
            String lower = prefix.toLowerCase(Locale.ROOT);
            for (FileListing.Line line : FileListing.lines()) {
                String path = line.path();
                String parent = screen.parentDirectory(path);
                if (!parent.equals(base) || path.equals(base)) continue;
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                    candidates.add(name);
                    folders.add(line.type().equals("DIRECTORY"));
                }
            }
        }
        if (candidates.isEmpty()) return;
        String replacement;
        if (candidates.size() == 1) {
            replacement = folderPart + candidates.get(0) + (folders.get(0) ? "/" : " ");
        } else {
            String common = candidates.get(0);
            for (String candidate : candidates) {
                int length = 0;
                while (length < common.length() && length < candidate.length()
                        && Character.toLowerCase(common.charAt(length)) == Character.toLowerCase(candidate.charAt(length))) length++;
                common = common.substring(0, length);
            }
            if (common.length() > prefix.length()) replacement = folderPart + common;
            else {
                if (tabPresses++ > 0) {
                    List<String> shown = new ArrayList<>();
                    for (int index = 0; index < candidates.size(); index++) shown.add(candidates.get(index) + (folders.get(index) ? "/" : ""));
                    add(COMMAND, promptLabel() + displayInput());
                    add(SYSTEM, String.join("  ", shown));
                    jumpToBottom();
                }
                return;
            }
        }
        String escaped = commandToken ? replacement : replacement.replace(" ", "\\ ");
        if (!commandToken && replacement.endsWith(" ")) escaped = replacement.substring(0, replacement.length() - 1).replace(" ", "\\ ") + " ";
        String updated = state.input.substring(0, tokenStart) + escaped + state.input.substring(state.cursor);
        if (updated.length() > MAX_INPUT) return;
        state.input = updated;
        state.cursor = tokenStart + escaped.length();
        jumpToBottom();
    }

    private String resolve(String path) {
        List<String> parts = new ArrayList<>();
        String combined = path.startsWith("/") ? path : state.directory + "/" + path;
        for (String part : combined.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else parts.add(part);
        }
        return parts.isEmpty() ? "/" : "/" + String.join("/", parts);
    }

    private String promptLabel() {
        return passwordDisk != null ? tr("password_prompt") : state.directory + "> ";
    }

    void tick() {
        var decryption = com.craisinlord.antos.content.client.ComputerProgramClientState.lastDecryption();
        if (decryption != null && decryption.revision() != decryptionRevision) {
            decryptionRevision = decryption.revision();
            if (decryption.terminal()) {
                add(OUTPUT, TerminalText.raw("computer.antos.disks.decrypt." + decryption.code()));
                busy = false;
            }
        }
        ComputerAccessResultPayload terminalResult = ComputerTerminalClientState.consume();
        if (terminalResult == null) return;
        busy = false;
        boolean failed = terminalResult.result() == ComputerAccessResultPayload.INVALID;
        if (failed) AntOSUiSounds.play(Cue.ERROR);
        try {
            var terminal = AntmailWire.decodeTag(terminalResult.data().substring(3));
            state.directory = terminal.getString("Directory");
            if (terminal.getBoolean("Clear")) {
                if (revealing()) skipReveal();
                state.output.clear();
                state.version++;
            }
            var lines = terminal.getList("Lines", 8);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.getString(index);
                TerminalText.Styled styled = TerminalText.styled(line);
                if (styled != null) {
                    int kind = switch (styled.style()) {
                        case "error" -> ERROR;
                        case "system" -> SYSTEM;
                        case "highlight" -> DIRECTORY;
                        default -> OUTPUT;
                    };
                    if (styled.speed() > 0 || styled.delay() > 0 || !state.queue.isEmpty()) state.queue.add(new Pending(new Entry(line, kind), styled.speed(), styled.delay()));
                    else add(kind, line);
                    continue;
                }
                String key = TerminalText.key(line);
                int kind = failed ? ERROR
                        : key.endsWith("ls.directory") ? DIRECTORY
                        : key.endsWith("help.row") || key.endsWith("help.custom") ? HELP
                        : key.endsWith("help.header") ? SYSTEM
                        : OUTPUT;
                add(kind, line);
            }
            String openPath = terminal.getString("Open");
            if (!openPath.isBlank()) {
                if (state.queue.isEmpty()) openFile(openPath);
                else state.pendingOpen = openPath;
            }
        } catch (RuntimeException ignored) {
            add(ERROR, TerminalText.of("client.corrupted"));
        }
        ComputerNetworking.listFiles();
    }

    private void openFile(String path) {
        ComputerNetworking.openFile(path);
        screen.open(path.endsWith(".antpaint") ? "PAINT" : "TEXT");
    }

    private boolean revealing() {
        return state.typing != null || !state.queue.isEmpty();
    }

    private void pump() {
        long now = System.currentTimeMillis();
        while (true) {
            if (state.typing != null) {
                int total = typingLength(state.typing);
                int shown = (int) Math.min(total, (now - state.typingStart) * state.typingSpeed / 1000L);
                if (shown < total) {
                    if (shown != state.typingShown) {
                        state.typingShown = shown;
                        state.version++;
                    }
                    return;
                }
                state.typing = null;
                state.version++;
            }
            if (state.queue.isEmpty()) {
                if (state.pendingOpen != null) {
                    String path = state.pendingOpen;
                    state.pendingOpen = null;
                    openFile(path);
                }
                return;
            }
            Pending next = state.queue.get(0);
            if (state.waitUntil == 0L) state.waitUntil = now + next.delay();
            if (now < state.waitUntil) return;
            state.queue.remove(0);
            state.waitUntil = 0L;
            add(next.entry().kind(), next.entry().text());
            if (next.speed() > 0) {
                state.typing = state.output.get(state.output.size() - 1);
                state.typingStart = now;
                state.typingSpeed = next.speed();
                state.typingShown = 0;
                state.version++;
            }
        }
    }

    private int typingLength(Entry entry) {
        Object language = net.minecraft.locale.Language.getInstance();
        if (typingPlainEntry != entry || typingPlainLanguage != language) {
            typingPlainEntry = entry;
            typingPlainLanguage = language;
            typingPlainLength = TerminalText.plain(entry.text()).length();
        }
        return typingPlainLength;
    }

    private void skipReveal() {
        state.typing = null;
        for (Pending pending : state.queue) add(pending.entry().kind(), pending.entry().text());
        state.queue.clear();
        state.waitUntil = 0L;
        state.version++;
        jumpToBottom();
        if (state.pendingOpen != null) {
            String path = state.pendingOpen;
            state.pendingOpen = null;
            openFile(path);
        }
    }

    private void execute() {
        if (revealing()) {
            skipReveal();
            return;
        }
        if (busy && passwordDisk == null) return;
        if (passwordDisk != null) {
            if (state.input.isEmpty()) return;
            AntOSUiSounds.play(Cue.COMMAND);
            add(COMMAND, promptLabel() + displayInput());
            if (state.input.equalsIgnoreCase("cancel")) add(SYSTEM, TerminalText.of("client.decrypt_cancelled"));
            else {
                ComputerNetworking.decryptDisk(passwordDisk, state.input, true);
                add(SYSTEM, TerminalText.of("client.validating"));
                busy = true;
                busySince = System.currentTimeMillis();
            }
            passwordDisk = null;
            clearInput();
            return;
        }
        String input = state.input.trim();
        String echo = promptLabel() + displayInput();
        clearInput();
        add(COMMAND, echo);
        if (input.isEmpty()) return;
        AntOSUiSounds.play(Cue.COMMAND);
        remember(input);
        String[] parts = input.split("\\s+", 3);
        if (parts[0].equalsIgnoreCase("decrypt")) {
            ResourceLocation disk = parts.length > 1 ? ResourceLocation.tryParse(parts[1]) : null;
            if (disk == null) add(ERROR, TerminalText.of("error.usage", TerminalText.PREFIX + "help.usage.decrypt"));
            else if (parts.length == 3) {
                ComputerNetworking.decryptDisk(disk, parts[2], true);
                busy = true;
                busySince = System.currentTimeMillis();
            } else {
                passwordDisk = disk;
                add(SYSTEM, TerminalText.of("client.enter_password"));
            }
            return;
        }
        ComputerNetworking.terminalCommand(state.directory, input);
        busy = true;
        busySince = System.currentTimeMillis();
    }

    private void remember(String input) {
        if (INLINE_PASSWORD.matcher(input).matches()) return;
        if (state.history.isEmpty() || !state.history.get(state.history.size() - 1).equals(input)) state.history.add(input);
        while (state.history.size() > MAX_HISTORY) state.history.remove(0);
        if (com.craisinlord.antos.config.AntOSSettings.preferences().terminalKeepHistory) {
            com.craisinlord.antos.config.AntOSSettings.setTerminalHistory(com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of(), state.history);
        }
    }

    void clearHistory() {
        state.history.clear();
        state.historyIndex = -1;
        com.craisinlord.antos.config.AntOSSettings.clearTerminalHistory();
    }

    private void clearInput() {
        state.input = "";
        state.cursor = 0;
        state.historyIndex = -1;
        state.draft = "";
        jumpToBottom();
    }

    private void add(int kind, String text) {
        state.output.add(new Entry(text, kind));
        while (state.output.size() > MAX_ENTRIES) state.output.remove(0);
        state.version++;
        if (!state.follow) state.unseen = true;
    }

    private void notice(String text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + 2000L;
    }

    private String tr(String key, Object... arguments) {
        return Component.translatable(TerminalText.PREFIX + "client." + key, arguments).getString();
    }

    record Entry(String text, int kind) { }

    private record Pending(Entry entry, int speed, int delay) { }

    private record Visual(FormattedCharSequence line, int color, int indent, FormattedCharSequence second, int secondColor, int secondX, Entry entry) { }

    private record Layout(int helpColumn, int shown, List<Visual> rows) { }

    static final class State {
        final List<Entry> output = new ArrayList<>(List.of(
                new Entry(TerminalText.of("client.ready"), SYSTEM),
                new Entry(TerminalText.of("client.help_hint"), SYSTEM)));
        final List<String> history = new ArrayList<>();
        int version;
        int scroll;
        int maximumScroll;
        boolean follow = true;
        boolean unseen;
        String input = "";
        int cursor;
        int historyIndex = -1;
        String draft = "";
        String directory = "/";
        final List<Pending> queue = new ArrayList<>();
        Entry typing;
        long typingStart;
        int typingSpeed;
        int typingShown;
        long waitUntil;
        String pendingOpen;
    }
}
