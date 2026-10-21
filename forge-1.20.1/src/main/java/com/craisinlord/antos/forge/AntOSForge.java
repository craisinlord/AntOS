package com.craisinlord.antos.forge;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.compat.fieldguide.FieldGuideCompat;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.antmail.AntmailEventData;
import com.craisinlord.antos.content.antazon.AntazonData;
import com.craisinlord.antos.content.antazon.AntazonDealData;
import com.craisinlord.antos.content.antazon.AntazonSellData;
import com.craisinlord.antos.content.computer.TaskDebugCommands;
import com.craisinlord.antos.content.computer.blockle.BlockleAnswers;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.FloppyTextureSync;
import com.craisinlord.antos.forge.network.AntOSForgeNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod(AntOS.MODID)
public final class AntOSForge {
    public AntOSForge() {
        AntOSSettings.loadConfig(FMLPaths.CONFIGDIR.get().resolve("antos.json"));
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        AntOSForgeContent.register(modBus);
        AntOSForgeNetworking.register();
        com.craisinlord.antos.content.network.JeiSync.register();
        MinecraftForge.EVENT_BUS.addListener(this::registerReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onAdvancementEarned);
        AntmailEventData.setAdvancementEvents(true);
        AntmailEventData.registerListeners();
        if (ModList.get().isLoaded("ftbquests")) com.craisinlord.antos.compat.ftbquests.FTBQuestsCompat.initialize();
        if (ModList.get().isLoaded("fieldguide")) FieldGuideCompat.initialize();
        AntOS.LOGGER.info("AntOS Forge initialized");
    }

    private void registerCommands(RegisterCommandsEvent event) {
        TaskDebugCommands.register(event.getDispatcher());
        com.craisinlord.antos.content.guide.DisplayCommands.register(event.getDispatcher());
        com.craisinlord.antos.content.antmail.AntmailDebugCommands.register(event.getDispatcher());
        com.craisinlord.antos.content.antazon.AntazonCommands.register(event.getDispatcher());
        com.craisinlord.antos.content.antazon.AntcoinCommands.register(event.getDispatcher());
        com.craisinlord.antos.content.computer.AccountCommands.register(event.getDispatcher());
    }

    private void registerReloadListeners(AddReloadListenerEvent event) {
        event.addListener(ComputerGuideData.instance());
        event.addListener(BlockleAnswers.instance());
        event.addListener(AntmailEventData.instance());
        event.addListener(AntazonData.instance());
        event.addListener(AntazonSellData.instance());
        event.addListener(AntazonDealData.instance());
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        com.craisinlord.antos.content.guide.DisplayCommands.tick(server);
        FieldGuideCompat.tick(server);
        FloppyTextureSync.tick(server);
        com.craisinlord.antos.content.network.JeiSync.tick(server);
        com.craisinlord.antos.content.network.DisplaySync.tick(server);
        AntmailEventData.onTick(server);
        com.craisinlord.antos.content.computer.ComputerLocationObjectives.tick(server);
        com.craisinlord.antos.content.computer.ComputerPrograms.tick(server);
    }

    private void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AntmailEventData.onAdvancement(player, event.getAdvancement());
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        com.craisinlord.antos.content.guide.DisplayCommands.serverStopped();
        FieldGuideCompat.shutdown();
        com.craisinlord.antos.content.network.AnternetAccountHandler.serverStopped();
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.craisinlord.antos.content.network.ComputerAccessHandler.sendLookup(player);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.craisinlord.antos.content.network.AnternetAccountHandler.disconnect(player);
        }
    }
}
