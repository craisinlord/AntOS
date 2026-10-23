package com.craisinlord.antos.compat.jei;

import com.craisinlord.antos.content.client.ArchiveLookup;
import com.craisinlord.antos.content.client.palette.Ui;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

final class JeiOpenButton implements IJeiGuiEventListener {
    static final int WIDTH = 30;
    static final int HEIGHT = 11;
    private final ScreenRectangle area;
    private final ArchiveLookup.Target target;

    JeiOpenButton(int recipeWidth, ArchiveLookup.Target target) {
        this.area = new ScreenRectangle(left(recipeWidth), 3, WIDTH, HEIGHT);
        this.target = target;
    }

    static int left(int recipeWidth) { return recipeWidth - WIDTH - 3; }

    static int titleWidth(int recipeWidth) { return recipeWidth - WIDTH - 13; }

    static void draw(GuiGraphicsExtractor g, int recipeWidth, double mouseX, double mouseY) {
        int x = left(recipeWidth);
        boolean hovered = over(recipeWidth, mouseX, mouseY);
        g.fill(x, 3, x + WIDTH, 3 + HEIGHT, hovered ? Ui.HOVER : Ui.SURFACE);
        g.outline(x, 3, WIDTH, HEIGHT, hovered ? Ui.ACCENT : Ui.LINE);
        String label = Component.translatable("jei.antos.open").getString();
        g.text(JeiDrawing.font(), label, x + (WIDTH - JeiDrawing.font().width(label)) / 2, 5, hovered ? Ui.ACCENT : Ui.TEXT, false);
    }

    static boolean over(int recipeWidth, double mouseX, double mouseY) {
        return JeiDrawing.inside(mouseX, mouseY, left(recipeWidth), 3, WIDTH, HEIGHT);
    }

    static boolean tooltip(ITooltipBuilder tooltip, int recipeWidth, double mouseX, double mouseY) {
        if (!over(recipeWidth, mouseX, mouseY)) return false;
        tooltip.add(Component.translatable("jei.antos.open.tooltip"));
        return true;
    }

    @Override
    public ScreenRectangle getArea() {
        return area;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        ArchiveLookup.openTarget(target);
        return true;
    }
}
