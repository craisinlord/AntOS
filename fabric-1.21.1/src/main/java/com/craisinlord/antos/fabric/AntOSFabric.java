package com.craisinlord.antos.fabric;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.antmail.AntmailEventData;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.antazon.AntazonData;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.computer.blockle.BlockleAnswers;
import com.craisinlord.antos.content.computer.TaskDebugCommands;
import com.craisinlord.antos.content.network.FloppyTextureSync;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.fabric.network.AntOSFabricNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class AntOSFabric implements ModInitializer {
    @Override public void onInitialize() {
        AntOSSettings.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("antos.json"));
        AntOSFabricContent.register();
        AntOSFabricNetworking.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TaskDebugCommands.register(dispatcher));
        registerReloadListener("computer_data", ComputerGuideData.instance());
        registerReloadListener("blockle_answers", BlockleAnswers.instance());
        registerReloadListener("antmail_data", AntmailEventData.instance());
        registerReloadListener("antazon_data", AntazonData.instance());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FloppyTextureSync.tick(server);
            AntmailServerData.access(server).drainAvailable(server);
            AntmailEventData.onTick(server);
        });
        AntOS.LOGGER.info("AntOS initialized");
    }

    private static void registerReloadListener(String path, PreparableReloadListener delegate) {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return AntOSFabricContent.id(path); }
            @Override public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                    ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler, Executor prepareExecutor, Executor applyExecutor) {
                return delegate.reload(barrier, manager, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor);
            }
        });
    }
}

