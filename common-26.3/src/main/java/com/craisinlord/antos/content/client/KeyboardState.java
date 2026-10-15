package com.craisinlord.antos.content.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

public final class KeyboardState {
    public static final int NUMPAD_SUBTRACT = 333;
    private KeyboardState() {}

    public static boolean isDown(int key) {
        for (var method : InputConstants.class.getMethods()) {
            if (!method.getName().equals("isKeyDown")) continue;
            try {
                if (method.getParameterCount() == 1) return (boolean) method.invoke(null, key);
                if (method.getParameterCount() == 2) return (boolean) method.invoke(null, Minecraft.getInstance().getWindow(), key);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to query keyboard state", exception);
            }
        }
        throw new IllegalStateException("No keyboard state query is available");
    }
}