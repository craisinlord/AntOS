package com.craisinlord.antos.content.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class OpenAntOSKey {
    private static KeyMapping key;

    private OpenAntOSKey() { }

    public static KeyMapping createKey() {
        key = new KeyMapping("key.antos.open_antos", InputConstants.Type.KEYSYM, InputConstants.KEY_N, "key.categories.antos");
        return key;
    }

    static boolean isOpenKey(int keyCode, int scanCode) {
        return key != null && !key.isUnbound() && key.matches(keyCode, scanCode);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (key != null) while (key.consumeClick()) {
            if (minecraft.screen == null) ComputerScreen.openComputer();
        }
    }
}
