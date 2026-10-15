package com.craisinlord.antos.content.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class ThemedGraphics extends GuiGraphics {
    public ThemedGraphics(GuiGraphics source) {
        super(Minecraft.getInstance(), source.bufferSource());
    }

    @Override
    public void fill(RenderType renderType, int minX, int minY, int maxX, int maxY, int z, int color) {
        super.fill(renderType, minX, minY, maxX, maxY, z, ComputerTheme.remap(color));
    }

    @Override
    public void fillGradient(RenderType renderType, int minX, int minY, int maxX, int maxY, int colorFrom, int colorTo, int z) {
        super.fillGradient(renderType, minX, minY, maxX, maxY, ComputerTheme.remap(colorFrom), ComputerTheme.remap(colorTo), z);
    }

    @Override
    public int drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        return super.drawString(font, text, x, y, ComputerTheme.remap(color), dropShadow);
    }

    @Override
    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
        return super.drawString(font, text, x, y, ComputerTheme.remap(color), dropShadow);
    }

    @Override
    public int drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) {
        return super.drawString(font, text, x, y, ComputerTheme.remap(color), dropShadow);
    }
}
