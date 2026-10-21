package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class BlockleRenderer {
    private static Identifier answerStackId;
    private static ItemStack answerStack = ItemStack.EMPTY;

    private BlockleRenderer() {
    }

    public static void render(ThemedGraphics graphics, Font font, int x, int y, int width, int height,
                              List<String> guesses, List<String> colors, String input, String status, String phase,
                              Identifier answerId) {
        renderBoard(graphics, font, x, y, width, height, guesses, colors, input, status, phase, answerId);
    }

    private static void renderBoard(ThemedGraphics graphics, Font font, int x, int y, int width, int height,
                                    List<String> guesses, List<String> colors, String input, String status, String phase,
                                    Identifier answerId) {
        int cell = 18;
        int gap = 3;
        int boardX = x + Math.max(0, (width - cell * 5 - gap * 4) / 2);
        int boardY = y + 4;
        for (int row = 0; row < 6; row++) {
            String guess = row < guesses.size() ? guesses.get(row) : row == guesses.size() ? input : "";
            String rowColors = row < colors.size() ? colors.get(row) : "";
            for (int column = 0; column < 5; column++) {
                int left = boardX + column * (cell + gap);
                int top = boardY + row * (cell + gap);
                int fill = Ui.SURFACE;
                if (column < rowColors.length()) fill = switch (rowColors.charAt(column)) {
                    case 'G' -> Ui.GAME_CORRECT;
                    case 'Y' -> Ui.GAME_PRESENT;
                    default -> Ui.GAME_ABSENT;
                };
                graphics.fill(left, top, left + cell, top + cell, fill);
                if (column < guess.length()) {
                    String letter = Character.toString(Character.toUpperCase(guess.charAt(column)));
                    graphics.drawCenteredString(font, letter, left + cell / 2, top + 5, Ui.TEXT);
                }
            }
        }
        String message = status.isBlank() ? "ENTER TO SUBMIT" : status;
        if (phase.equals("PLAYING") && input.length() < 5) message = input.length() + "/5 LETTERS";
        graphics.drawString(font, Component.literal(message), x, y + 136, Ui.TEXT, false);
        if (!phase.equals("PLAYING") && answerId != null) {
            if (!answerId.equals(answerStackId)) {
                answerStack = BuiltInRegistries.ITEM.getOptional(answerId).map(ItemStack::new).orElse(ItemStack.EMPTY);
                answerStackId = answerId;
            }
            if (!answerStack.isEmpty()) {
                float[] answerTint = Ui.tint(Ui.ANSWER_TINT);
                graphics.setColor(answerTint[0], answerTint[1], answerTint[2], 1.0F);
                graphics.renderItem(answerStack, x + width - 28, y + 132);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }
}
