package com.craisinlord.antos.content.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

abstract class ComputerApp {
    ComputerScreen screen;
    Font font;

    void attach(ComputerScreen screen) {
        this.screen = screen;
        this.font = Minecraft.getInstance().font;
    }
}
