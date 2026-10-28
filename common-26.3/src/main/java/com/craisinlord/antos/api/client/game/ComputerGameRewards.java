package com.craisinlord.antos.api.client.game;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.network.ComputerNetworking;
import net.minecraft.resources.Identifier;

public final class ComputerGameRewards {
    private ComputerGameRewards() {}

    public static long reward(Identifier mode) {
        if (mode == null || !AntOSSettings.appEnabled("ANTAZON")) return 0L;
        return Math.max(0L, AntOSSettings.gameAntcoinReward(mode.toString()));
    }

    public static void claim(Identifier mode) {
        if (mode != null) ComputerNetworking.claimGameReward(mode.toString());
    }
}
