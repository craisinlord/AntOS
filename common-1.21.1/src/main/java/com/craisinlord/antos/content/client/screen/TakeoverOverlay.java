package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.antmail.AntmailTakeover;
import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.client.AntmailGlitchText;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

final class TakeoverOverlay {
    private static final long LINE_DELAY_MILLIS = 320L;
    private static final int LINE_ADVANCE = 10;
    private final AntmailTakeover takeover;
    private final long startedAt = System.currentTimeMillis();
    private List<FormattedCharSequence> wrapped = List.of();
    private List<Integer> sourceLine = List.of();
    private int wrappedWidth = -1;
    private int buttonX;
    private int buttonY;
    private int buttonWidth;
    private int buttonHeight;

    TakeoverOverlay(AntmailTakeover takeover) {
        this.takeover = takeover;
    }

    private boolean hijacked() {
        return takeover.hijacked();
    }

    private int accent() {
        return hijacked() ? Ui.HIJACK : Ui.CORRUPT;
    }

    private int dim() {
        return hijacked() ? Ui.HIJACK_DIM : Ui.CORRUPT_DIM;
    }

    private static String text(String raw) {
        if (raw == null) return "";
        String value = I18n.exists(raw) ? I18n.get(raw) : raw;
        value = value.replace("{username}", AntOSPlayerText.playerName());
        return AntOSPlayerText.apply(value);
    }

    private int visibleLines(long now) {
        if (AntOSSettings.reduceEffects()) return Integer.MAX_VALUE;
        return (int) Math.max(0L, (now - startedAt - 400L) / LINE_DELAY_MILLIS) + 1;
    }

    boolean ready() {
        return visibleLines(System.currentTimeMillis()) > takeover.lines().size();
    }

    private void wrap(Font font, int width) {
        if (width == wrappedWidth) return;
        AntmailGlitchText.Tone tone = hijacked() ? AntmailGlitchText.Tone.HIJACKED : AntmailGlitchText.Tone.CORRUPTED;
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> sources = new ArrayList<>();
        for (int index = 0; index < takeover.lines().size(); index++) {
            String line = text(takeover.lines().get(index));
            List<FormattedCharSequence> split = line.isBlank() ? List.of(FormattedCharSequence.EMPTY)
                    : font.split(AntmailGlitchText.body(line, tone, accent()), width);
            for (FormattedCharSequence row : split) {
                lines.add(row);
                sources.add(index);
            }
        }
        wrapped = lines;
        sourceLine = sources;
        wrappedWidth = width;
    }

    void render(GuiGraphics g, Font font, int left, int top, int width, int height, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 600.0F);
        g.fill(left, top, left + width, top + height, hijacked() ? Ui.HIJACK_BACKGROUND : Ui.CORRUPT_BACKGROUND);
        boolean calm = AntOSSettings.reduceEffects();
        int scanOffset = calm ? 0 : (int) (now / 60L % 4L);
        for (int y = top + scanOffset; y < top + height; y += 4) g.fill(left, y, left + width, y + 1, hijacked() ? Ui.HIJACK_SCANLINE : Ui.CORRUPT_SCANLINE);
        int inset = 8;
        g.fill(left + inset, top + inset, left + width - inset, top + inset + 1, accent());
        g.fill(left + inset, top + height - inset - 1, left + width - inset, top + height - inset, accent());
        g.fill(left + inset, top + inset, left + inset + 1, top + height - inset, accent());
        g.fill(left + width - inset - 1, top + inset, left + width - inset, top + height - inset, accent());
        String title = text(takeover.title());
        if (!hijacked()) title = AntmailGlitchText.flicker(title, 12, takeover.id().hashCode());
        int contentTop = top + 26;
        if (!title.isBlank()) {
            float scale = 1.5F;
            int titleWidth = (int) (font.width(title) * scale);
            g.fill(left + inset + 1, top + 18, left + width - inset - 1, top + 18 + (int) (12 * scale), Ui.HIJACK_TITLE_BAND);
            g.pose().pushPose();
            g.pose().translate(left + (width - titleWidth) / 2.0F, top + 21, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, Component.literal(title), 0, 0, hijacked() ? Ui.HIJACK_TEXT : accent(), false);
            g.pose().popPose();
            contentTop = top + 18 + (int) (12 * scale) + 12;
        }
        int textWidth = Math.max(40, width - 56);
        wrap(font, textWidth);
        int visible = visibleLines(now);
        int buttonTop = top + height - inset - 34;
        int y = contentTop;
        int lastShown = -1;
        for (int row = 0; row < wrapped.size(); row++) {
            if (sourceLine.get(row) >= visible) break;
            if (y + 8 > buttonTop - 4) break;
            int jitter = hijacked() ? 0 : AntmailGlitchText.jitter(row);
            g.drawString(font, AntmailGlitchText.animate(wrapped.get(row), row, startedAt), left + 28 + jitter, y, accent(), false);
            lastShown = row;
            y += LINE_ADVANCE;
        }
        boolean ready = visible > takeover.lines().size();
        if (!ready && !calm && now / 400L % 2L == 0L) {
            int cursorX = left + 28 + (lastShown < 0 ? 0 : font.width(wrapped.get(lastShown)) + 2);
            int cursorY = lastShown < 0 ? contentTop : y - LINE_ADVANCE;
            g.fill(cursorX, cursorY, cursorX + 5, cursorY + 8, accent());
        }
        String label = takeover.button().isBlank() ? Component.translatable("computer.antos.takeover.dismiss").getString() : text(takeover.button());
        buttonWidth = Math.max(70, font.width(label) + 24);
        buttonHeight = 18;
        buttonX = left + (width - buttonWidth) / 2;
        buttonY = buttonTop + 8;
        if (ready) {
            boolean hover = hit(mouseX, mouseY);
            g.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, hover ? accent() : Ui.HIJACK_BAND);
            g.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 1, accent());
            g.fill(buttonX, buttonY + buttonHeight - 1, buttonX + buttonWidth, buttonY + buttonHeight, accent());
            g.fill(buttonX, buttonY, buttonX + 1, buttonY + buttonHeight, accent());
            g.fill(buttonX + buttonWidth - 1, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, accent());
            g.drawCenteredString(font, Component.literal(label), buttonX + buttonWidth / 2, buttonY + 5, hover ? Ui.HIJACK_INK : Ui.HIJACK_TEXT);
        }
        g.pose().popPose();
    }

    boolean hit(double mouseX, double mouseY) {
        return ready() && buttonWidth > 0 && mouseX >= buttonX && mouseX < buttonX + buttonWidth && mouseY >= buttonY && mouseY < buttonY + buttonHeight;
    }
}
