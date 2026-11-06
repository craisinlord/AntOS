package com.craisinlord.antos.content.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class OpenAntOSKey {
    private static KeyMapping key;

    private OpenAntOSKey() { }

    public static KeyMapping createKey(KeyMapping.Category category) {
        key = new KeyMapping("key.antos.open_antos", InputConstants.Type.KEYSYM, InputConstants.KEY_N, category);
        return key;
    }

    static boolean isOpenKey(net.minecraft.client.input.KeyEvent event) {
        return key != null && !key.isUnbound() && key.matches(event);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (key != null) while (key.consumeClick()) {
            if (minecraft.screen == null) ComputerScreen.openComputer();
        }
    }
}
