package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.antmail.AntmailWire;
import com.craisinlord.antos.content.client.ComputerTerminalClientState;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;

final class TerminalApp extends ComputerApp {
    final State state = new State();

    void render(GuiGraphics g, int x, int y, int w, int h) {
        g.drawString(font, Component.literal("ANTOS TERMINAL [READY]"), x, y, GREEN, false);
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        for (String output : state.output) lines.addAll(font.split(Component.literal(output), Math.max(1, w - 10)));
        int visible = Math.max(1, (h - 68) / 11);
        int maximum = Math.max(0, lines.size() - visible);
        state.scroll = Math.max(0, Math.min(state.scroll, maximum));
        state.maximumScroll = maximum;
        screen.drawScrollbar(g, x + w - 4, y + 17, Math.max(1, h - 68), visible, lines.size(), state.scroll, maximum);
        int end = Math.min(lines.size(), state.scroll + visible);
        for (int i = state.scroll; i < end; i++) {
            g.drawString(font, lines.get(i), x, y + 17 + (i - state.scroll) * 11, PALE_GREEN, false);
        }
        g.drawString(font, Component.literal(screen.trimToWidth(state.directory + "> " + state.input + (screen.caretVisible() ? "|" : ""), 208)), x, y + h - 26, GREEN, false);
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        if (screen.inside(contentX + contentW - 5, contentY + 17, 5, Math.max(1, contentH - 68), mouseX, mouseY)) {
            int trackTop = contentY + 17;
            int trackHeight = Math.max(1, contentH - 68);
            state.scroll = (int) Math.round(Math.max(0.0, Math.min(1.0, (mouseY - trackTop) / trackHeight)) * state.maximumScroll);
            return true;
        }
        state.focused = true;
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        state.scroll = Math.max(0, Math.min(state.maximumScroll,
                state.scroll - (int) Math.signum(scrollY)));
        return true;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (state.focused && codePoint >= 32) { state.input += codePoint; return true; }
        return false;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (state.focused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (!state.input.isEmpty()) state.input = state.input.substring(0, state.input.length() - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { execute(); return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { state.focused = false; return true; }
        }
        return false;
    }

    void tick() {
        ComputerAccessResultPayload terminalResult = ComputerTerminalClientState.consume();
        if (terminalResult != null) {
            try {
                var terminal = AntmailWire.decodeTag(terminalResult.data().substring(3));
                state.directory = terminal.getString("Directory");
                if (terminal.getBoolean("Clear")) state.output.clear();
                var lines = terminal.getList("Lines", 8);
                for (int index = 0; index < lines.size(); index++) state.output.add(lines.getString(index));
                String openPath = terminal.getString("Open");
                if (!openPath.isBlank()) {
                    ComputerNetworking.openFile(openPath);
                    screen.open(openPath.endsWith(".antpaint") ? "PAINT" : "TEXT");
                }
                while (state.output.size() > 40) state.output.remove(0);
            } catch (RuntimeException ignored) {
                state.output.add("ERROR: CORRUPTED SERVER RESPONSE");
            }
        }
    }

    private void execute() {
        String input = state.input.trim();
        if (input.isEmpty()) return;
        state.output.add(state.directory + "> " + input);
        ComputerNetworking.terminalCommand(state.directory, input);
        state.input = "";
    }

    static final class State {
        final List<String> output = new ArrayList<>(List.of("ANTOS TERMINAL [READY]", "TYPE HELP FOR COMMANDS"));
        int scroll;
        int maximumScroll;
        String input = "";
        String directory = "/";
        boolean focused;
    }
}
