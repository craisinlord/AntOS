package com.craisinlord.antos.neoforge;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.compat.fieldguide.FieldGuideCompat;
import com.craisinlord.antos.content.antmail.AntmailEventData;
import com.craisinlord.antos.content.antazon.AntazonData;
import com.craisinlord.antos.content.antazon.AntazonSellData;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(AntOS.MODID)
public final class AntOSNeoForge {
    public AntOSNeoForge(IEventBus modBus) {
        AntOSSettings.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("antos.json"));
        AntOSNeoForgeContent.register(modBus);
        AntOSNeoForgeNetworking.register(modBus);
        if (net.neoforged.fml.ModList.get().isLoaded("ftbquests")) {
            com.craisinlord.antos.compat.ftbquests.FTBQuestsCompat.initialize();
        }
        if (net.neoforged.fml.ModList.get().isLoaded("fieldguide")) {
            FieldGuideCompat.initialize();
        }
        NeoForge.EVENT_BUS.addListener(this::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onAdvancementEarned);
        AntmailEventData.registerListeners();
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
        event.addListener(AntazonSellData.instance());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        FieldGuideCompat.tick(server);
        FloppyTextureSync.tick(server);
        AntmailEventData.onTick(server);
        com.craisinlord.antos.content.computer.ComputerLocationObjectives.tick(server);
    }

    private void onAdvancementEarned(net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            AntmailEventData.onAdvancement(player, event.getAdvancement());
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        FieldGuideCompat.shutdown();
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.craisinlord.antos.content.network.AnternetAccountHandler.disconnect(player);
        }
    }
}
