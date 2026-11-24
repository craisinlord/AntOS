package com.craisinlord.antos.api.game;

import com.craisinlord.antos.content.computer.games.ComputerGameRecords;
import net.minecraft.resources.ResourceLocation;

public final class ComputerGameRecordsRegistry {
    private ComputerGameRecordsRegistry() { }

    public static boolean register(ResourceLocation mode) {
        return ComputerGameRecords.register(mode);
    }
}
