package com.craisinlord.antos.content.client;

import net.minecraft.core.BlockPos;
import java.util.function.Consumer;

public final class AntOSClientHooks {
    private static Consumer<BlockPos> computerOpener = pos -> {};
    private AntOSClientHooks() {}
    public static void setComputerOpener(Consumer<BlockPos> opener) { computerOpener = opener == null ? pos -> {} : opener; }
    public static void openComputer(BlockPos pos) { computerOpener.accept(pos); }
}

