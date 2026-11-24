package com.craisinlord.antos.api.game;

import com.craisinlord.antos.content.computer.games.ComputerGameRecords;
import net.minecraft.resources.Identifier;

public final class ComputerGameRecordsRegistry {
    private ComputerGameRecordsRegistry() { }

    public static boolean register(Identifier mode) {
        return ComputerGameRecords.register(mode);
    }
}
