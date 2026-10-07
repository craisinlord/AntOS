package com.craisinlord.antos.compat.fieldguide;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.computer.AccountWorkspace;
import com.craisinlord.antos.content.computer.ComputerTaskProgress;
import com.craisinlord.antos.content.computer.ComputerTasks;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.craisinlord.antos.content.network.ComputerAccessHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FieldGuideCompat {
    private static final String FIELD_GUIDE_NAMESPACE = "fieldguide_compat";
    private static final String GUIDE_ENTRY_CLASS = "com.evandev.fieldguide.api.GuideEntry";
    private static final Map<UUID, PlayerSyncState> SYNC_STATE = new HashMap<>();
    private static final Set<String> WARNED_ENTRY_ERRORS = new HashSet<>();
    private static boolean initialized;
    private static boolean reflectionFailureLogged;
    private static MinecraftServer tickServer;
    private static int tickCounter;
    private static Method managerGetInstance;
    private static Method managerGetResolvedEntries;
    private static Method managerResolveCanonicalEntry;
    private static Method progressManagerGetInstance;
    private static Method progressManagerGetProgress;
    private static Method progressGetUnlockedEntries;
    private static Method getEntryId;

    private FieldGuideCompat() {
    }

    public static void initialize() {
        if (initialized) return;
        try {
            ClassLoader loader = FieldGuideCompat.class.getClassLoader();
            Class<?> managerClass = Class.forName("com.evandev.fieldguide.server.ServerFieldGuideManager", false, loader);
            Class<?> progressManagerClass = Class.forName("com.evandev.fieldguide.server.progress.FieldGuideProgressManager", false, loader);
            Class<?> progressClass = Class.forName("com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress", false, loader);
            Class<?> autoPopulateClass = Class.forName("com.evandev.fieldguide.api.AutoPopulateRegistry", false, loader);
            managerGetInstance = managerClass.getMethod("getInstance");
            managerGetResolvedEntries = managerClass.getMethod("getResolvedEntries");
            managerResolveCanonicalEntry = managerClass.getMethod("resolveCanonicalEntryId", ResourceLocation.class);
            progressManagerGetInstance = progressManagerClass.getMethod("getInstance");
            progressManagerGetProgress = progressManagerClass.getMethod("getProgress", ServerPlayer.class);
            progressGetUnlockedEntries = progressClass.getMethod("getUnlockedEntries");
            getEntryId = autoPopulateClass.getMethod("getEntryId", Object.class, boolean.class);
            initialized = true;
            AntOS.LOGGER.info("Field Guide compatibility initialized");
        } catch (ReflectiveOperationException | LinkageError exception) {
            logReflectionFailure(exception);
        }
    }

    public static void tick(MinecraftServer server) {
        if (!initialized) return;
        if (tickServer != server) {
            tickServer = server;
            tickCounter = 0;
            SYNC_STATE.clear();
        }
        if (++tickCounter < 20) return;
        tickCounter = 0;
        try {
            boolean catalogChanged = refreshCatalog();
            if (catalogChanged) SYNC_STATE.clear();
            Set<UUID> onlinePlayers = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                onlinePlayers.add(player.getUUID());
                synchronizePlayer(player);
            }
            SYNC_STATE.keySet().removeIf(id -> !onlinePlayers.contains(id));
        } catch (ReflectiveOperationException exception) {
            logReflectionFailure(exception);
        } catch (LinkageError error) {
            logReflectionFailure(error);
        } catch (RuntimeException exception) {
            AntOS.LOGGER.error("Field Guide compatibility failed while synchronizing Archive data", exception);
        }
    }

    public static void shutdown() {
        SYNC_STATE.clear();
        WARNED_ENTRY_ERRORS.clear();
        tickServer = null;
        tickCounter = 0;
        ComputerGuideData.replaceFieldGuideEntries(Map.of());
    }

    private static boolean refreshCatalog() throws ReflectiveOperationException {
        Object manager = managerGetInstance.invoke(null);
        Object rawResolved = managerGetResolvedEntries.invoke(manager);
        if (!(rawResolved instanceof Map<?, ?> categories)) return false;
        Map<ResourceLocation, ComputerGuideData.Entry> generated = new LinkedHashMap<>();
        for (Object categoryEntries : categories.values()) {
            if (!(categoryEntries instanceof Collection<?> resolvedEntries)) continue;
            for (Object resolvedEntry : resolvedEntries) {
                try {
                    ResourceLocation sourceId = (ResourceLocation) getEntryId.invoke(null, resolvedEntry, true);
                    if (sourceId == null) continue;
                    ResourceLocation archiveId = archiveId(sourceId);
                    generated.putIfAbsent(archiveId, convertEntry(archiveId, sourceId, resolvedEntry));
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    String key = resolvedEntry == null ? "null" : resolvedEntry.getClass().getName();
                    if (WARNED_ENTRY_ERRORS.add(key)) {
                        AntOS.LOGGER.warn("A Field Guide entry could not be converted to an AntOS Archive page ({})", key, exception);
                    }
                }
            }
        }
        return ComputerGuideData.replaceFieldGuideEntries(generated);
    }

    private static ComputerGuideData.Entry convertEntry(ResourceLocation archiveId, ResourceLocation sourceId, Object resolvedEntry) {
        ResourceLocation entityId = null;
        ResourceLocation itemId = null;
        ResourceLocation displayId = null;
        ResourceLocation targetEntityType = null;
        if (resolvedEntry != null && resolvedEntry.getClass().getName().equals(GUIDE_ENTRY_CLASS)) {
            displayId = reflectedResourceLocation(resolvedEntry, "displayId");
            targetEntityType = reflectedResourceLocation(resolvedEntry, "targetEntityType");
        }
        if (resolvedEntry instanceof EntityType<?> entityType) {
            entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        } else if (resolvedEntry instanceof Item item) {
            itemId = BuiltInRegistries.ITEM.getKey(item);
        } else if (resolvedEntry instanceof Block block) {
            Item blockItem = block.asItem();
            if (blockItem != net.minecraft.world.item.Items.AIR) itemId = BuiltInRegistries.ITEM.getKey(blockItem);
        }
        List<ResourceLocation> candidates = new ArrayList<>();
        if (targetEntityType != null) candidates.add(targetEntityType);
        if (displayId != null) candidates.add(displayId);
        ResourceLocation rawId = rawSourceId(sourceId);
        if (rawId != null) candidates.add(rawId);
        for (ResourceLocation candidate : candidates) {
            if (entityId == null && BuiltInRegistries.ENTITY_TYPE.containsKey(candidate)) {
                entityId = candidate;
            } else if (itemId == null && BuiltInRegistries.ITEM.containsKey(candidate)) {
                itemId = candidate;
            } else if (itemId == null && BuiltInRegistries.BLOCK.containsKey(candidate)) {
                Item blockItem = BuiltInRegistries.BLOCK.get(candidate).asItem();
                if (blockItem != net.minecraft.world.item.Items.AIR) itemId = BuiltInRegistries.ITEM.getKey(blockItem);
            }
        }
        if (itemId != null && BuiltInRegistries.ITEM.get(itemId) instanceof BlockItem blockItem) {
            ResourceLocation blockItemId = BuiltInRegistries.ITEM.getKey(blockItem);
            if (blockItemId != null) itemId = blockItemId;
        }
        String titleKey = titleKey(entityId, itemId, sourceId);
        String entityValue = entityId == null ? "" : entityId.toString();
        String itemValue = itemId == null ? "" : itemId.toString();
        return new ComputerGuideData.Entry(archiveId, "article", "field_guide", titleKey,
                "guide.antos.field_guide.subtitle", List.of("guide.antos.field_guide.description"),
                itemValue, entityValue, "", "", "", "", "", 0, "", itemValue,
                entityValue, "", 0.0F, 1.0F, true, false);
    }

    private static String titleKey(ResourceLocation entityId, ResourceLocation itemId, ResourceLocation sourceId) {
        if (entityId != null) return BuiltInRegistries.ENTITY_TYPE.get(entityId).getDescriptionId();
        if (itemId != null) return BuiltInRegistries.ITEM.get(itemId).getDescriptionId();
        String path = sourceId.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) path = path.substring(slash + 1);
        String title = path.replace('_', ' ').replace('-', ' ').trim();
        if (title.isEmpty()) title = sourceId.toString();
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        return "literal:" + title;
    }

    private static ResourceLocation rawSourceId(ResourceLocation sourceId) {
        String namespace = sourceId.getNamespace();
        if (!namespace.equals("entity") && !namespace.equals("item") && !namespace.equals("block")) return null;
        String path = sourceId.getPath();
        int slash = path.indexOf('/');
        if (slash <= 0 || slash == path.length() - 1) return null;
        try {
            return ResourceLocation.fromNamespaceAndPath(path.substring(0, slash), path.substring(slash + 1));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ResourceLocation reflectedResourceLocation(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof ResourceLocation id ? id : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void synchronizePlayer(ServerPlayer player) throws ReflectiveOperationException {
        Object progressManager = progressManagerGetInstance.invoke(null);
        Object progress = progressManagerGetProgress.invoke(progressManager, player);
        if (progress == null) return;
        Object rawUnlocked = progressGetUnlockedEntries.invoke(progress);
        if (!(rawUnlocked instanceof Collection<?> unlockedIds)) return;
        ComputerWorkspaceData.AccountInfo account = AnternetAccountHandler.session(player);
        if (account == null) {
            PlayerSyncState previous = SYNC_STATE.get(player.getUUID());
            if (previous != null) {
                previous.accountId = null;
                previous.processedIds.clear();
            }
            return;
        }
        PlayerSyncState state = SYNC_STATE.computeIfAbsent(player.getUUID(), ignored -> new PlayerSyncState());
        if (!account.accountId().equals(state.accountId)) {
            state.accountId = account.accountId();
            state.processedIds.clear();
        }
        Set<String> currentIds = new LinkedHashSet<>();
        for (Object value : unlockedIds) {
            if (value instanceof String id) currentIds.add(id);
        }
        if (state.processedIds.containsAll(currentIds)) return;
        AccountWorkspace workspace = new AccountWorkspace(player, account);
        ComputerTasks.synchronizeSharedProgress(player, workspace);
        ComputerTaskProgress taskProgress = workspace.taskProgress();
        boolean changed = false;
        Set<String> processedThisPass = new LinkedHashSet<>();
        for (String unlockedId : currentIds) {
            if (state.processedIds.contains(unlockedId)) continue;
            ResourceLocation sourceId = parseBaseId(unlockedId);
            if (sourceId == null) {
                processedThisPass.add(unlockedId);
                continue;
            }
            ResourceLocation canonicalId = (ResourceLocation) managerResolveCanonicalEntry.invoke(
                    managerGetInstance.invoke(null), sourceId);
            if (canonicalId == null) canonicalId = sourceId;
            ResourceLocation targetId = archiveId(canonicalId);
            if (ComputerGuideData.entry(targetId) != null) {
                changed |= taskProgress.unlockArchiveEntry(targetId);
            }
            processedThisPass.add(unlockedId);
        }
        if (changed) {
            ComputerTasks.synchronizeSharedProgress(player, workspace);
            workspace.saveWorkspaceProgress();
            ComputerAccessHandler.sendArchive(player, workspace);
        }
        state.processedIds.addAll(processedThisPass);
    }

    private static ResourceLocation parseBaseId(String unlockedId) {
        int variant = unlockedId.indexOf('#');
        String base = variant < 0 ? unlockedId : unlockedId.substring(0, variant);
        try {
            return ResourceLocation.parse(base);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ResourceLocation archiveId(ResourceLocation sourceId) {
        return ResourceLocation.fromNamespaceAndPath(FIELD_GUIDE_NAMESPACE, sourceId.toString().replace(':', '/'));
    }

    private static void logReflectionFailure(Throwable exception) {
        if (reflectionFailureLogged) return;
        reflectionFailureLogged = true;
        AntOS.LOGGER.error("Field Guide compatibility could not access the Field Guide 1.21.1 API", exception);
    }

    private static final class PlayerSyncState {
        private UUID accountId;
        private final Set<String> processedIds = new HashSet<>();
    }
}
