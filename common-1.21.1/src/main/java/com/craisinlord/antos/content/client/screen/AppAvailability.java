package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.config.AntOSSettings;
import net.minecraft.client.Minecraft;

final class AppAvailability {
    private AppAvailability() {
    }

    static boolean enabled(String appId) {
        if ((appId.equals("TASKS") || appId.equals("ANTMAIL")) && com.craisinlord.antos.content.client.AuthoringClientState.available()) return true;
        if (appId.equals("MERCHANT") && Minecraft.getInstance().hasSingleplayerServer()) return false;
        return AntOSSettings.appEnabled(appId);
    }
}
