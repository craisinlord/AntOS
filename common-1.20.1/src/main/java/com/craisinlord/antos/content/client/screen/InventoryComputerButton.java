package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.AntOSClientHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class InventoryComputerButton extends AbstractButton {
    public InventoryComputerButton(int x, int y) {
        super(x, y, 74, 20, Component.translatable("button.antos.open_computer"));
    }

    @Override
    public void onPress() {
        AntOSClientHooks.openComputer();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int fill = this.isHoveredOrFocused() ? 0xFF173817 : 0xFF102010;
        int text = this.active ? 0xFF65FF65 : 0xFF507750;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF65FF65);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
        graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                getX() + width / 2, getY() + 6, text);
    }
}
