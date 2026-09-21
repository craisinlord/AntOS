package com.craisinlord.antos.neoforge;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.antmail.AntmailEventData;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.antazon.AntazonData;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.computer.blockle.BlockleAnswers;
import com.craisinlord.antos.content.computer.TaskDebugCommands;
import com.craisinlord.antos.content.network.FloppyTextureSync;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.neoforge.network.AntOSNeoForgeNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(AntOS.MODID)
public final class AntOSNeoForge {
    public AntOSNeoForge(IEventBus modBus) {
        AntOSSettings.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("antos.json"));
        AntOSNeoForgeContent.register(modBus);
        AntOSNeoForgeNetworking.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        AntOS.LOGGER.info("AntOS NeoForge initialized");
    }

    private void registerCommands(RegisterCommandsEvent event) {
        TaskDebugCommands.register(event.getDispatcher());
    }
    private void registerReloadListeners(AddReloadListenerEvent event) {
        event.addListener(ComputerGuideData.instance());
        event.addListener(BlockleAnswers.instance());
        event.addListener(AntmailEventData.instance());
        event.addListener(AntazonData.instance());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        FloppyTextureSync.tick(server);
        AntmailServerData.access(server).drainAvailable(server);
        AntmailEventData.onTick(server);
    }
}
