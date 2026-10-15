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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static Method managerGetAllEntryIds;
    private static Method managerResolveCanonicalEntry;
    private static Method progressManagerGetInstance;
    private static Method progressManagerGetProgress;
    private static Method progressGetUnlockedEntries;
    private static Method getEntryId;
    private static CatalogFingerprint catalogFingerprint;
    private static Map<Identifier, Identifier> canonicalEntryIdsByRawId = Map.of();
    private static int catalogChecks;
    private static final ClassValue<Map<String, Optional<Method>>> REFLECTED_METHODS = new ClassValue<>() {
        @Override
        protected Map<String, Optional<Method>> computeValue(Class<?> type) {
            return new java.util.concurrent.ConcurrentHashMap<>();
        }
    };

    private FieldGuideCompat() {
    }

    public static void initialize() {
        if (initialized) return;
        try {
            ClassLoader loader = FieldGuideCompat.class.getClassLoader();
            Class<?> managerClass = Class.forName("com.evandev.fieldguide.server.ServerFieldGuideManager", false, loader);
            Class<?> progressManagerClass = Class.forName("com.evandev.fieldguide.server.progress.FieldGuideProgressManager", false, loader);
            Class<?> progressClass = Class.forName("com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress", false, loader);
            managerGetInstance = managerClass.getMethod("getInstance");
            try {
                managerGetResolvedEntries = managerClass.getMethod("getResolvedEntries");
            } catch (NoSuchMethodException ignored) {
                managerGetResolvedEntries = null;
            }
            try {
                managerGetAllEntryIds = managerClass.getMethod("getAllEntryIds");
            } catch (NoSuchMethodException ignored) {
                managerGetAllEntryIds = null;
            }
            try {
                managerResolveCanonicalEntry = managerClass.getMethod("resolveCanonicalEntryId", Identifier.class);
            } catch (NoSuchMethodException ignored) {
                managerResolveCanonicalEntry = null;
            }
            if (managerGetResolvedEntries == null && managerGetAllEntryIds == null) {
                throw new NoSuchMethodException("Field Guide does not expose a supported entry catalog method");
            }
            if (managerGetResolvedEntries != null) {
                Class<?> autoPopulateClass = Class.forName("com.evandev.fieldguide.api.AutoPopulateRegistry", false, loader);
                getEntryId = autoPopulateClass.getMethod("getEntryId", Object.class, boolean.class);
            }
            progressManagerGetInstance = progressManagerClass.getMethod("getInstance");
            progressManagerGetProgress = progressManagerClass.getMethod("getProgress", ServerPlayer.class);
            progressGetUnlockedEntries = progressClass.getMethod("getUnlockedEntries");
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
            catalogFingerprint = null;
            canonicalEntryIdsByRawId = Map.of();
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
        catalogFingerprint = null;
        canonicalEntryIdsByRawId = Map.of();
        ComputerGuideData.replaceFieldGuideEntries(Map.of());
    }

    private static boolean refreshCatalog() throws ReflectiveOperationException {
        Object manager = managerGetInstance.invoke(null);
        if (managerGetResolvedEntries == null) return refreshCatalogFromEntryIds(manager);
        Object rawResolved = managerGetResolvedEntries.invoke(manager);
        if (!(rawResolved instanceof Map<?, ?> categories)) return refreshCatalogFromEntryIds(manager);
        CatalogFingerprint fingerprint = CatalogFingerprint.of(categories, ComputerGuideData.datapackRevision());
        if (++catalogChecks < 5 && fingerprint.equals(catalogFingerprint)) return false;
        catalogChecks = 0;
        Map<Identifier, ComputerGuideData.Entry> generated = new LinkedHashMap<>();
        Map<Identifier, Identifier> canonicalIds = new HashMap<>();
        for (Object categoryEntries : categories.values()) {
            if (!(categoryEntries instanceof Collection<?> resolvedEntries)) continue;
            for (Object resolvedEntry : resolvedEntries) {
                try {
                    Identifier sourceId = (Identifier) getEntryId.invoke(null, resolvedEntry, true);
                    if (sourceId == null) continue;
                    Identifier rawId = rawSourceId(sourceId);
                    if (rawId != null) canonicalIds.put(rawId, sourceId);
                    Identifier archiveId = archiveId(sourceId);
                    generated.putIfAbsent(archiveId, convertEntry(archiveId, sourceId, resolvedEntry));
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    String key = resolvedEntry == null ? "null" : resolvedEntry.getClass().getName();
                    if (WARNED_ENTRY_ERRORS.add(key)) {
                        AntOS.LOGGER.warn("A Field Guide entry could not be converted to an AntOS Archive page ({})", key, exception);
                    }
                }
            }
        }
        boolean changed = ComputerGuideData.replaceFieldGuideEntries(generated);
        catalogFingerprint = fingerprint;
        canonicalEntryIdsByRawId = Map.copyOf(canonicalIds);
        return changed;
    }

    private static boolean refreshCatalogFromEntryIds(Object manager) throws ReflectiveOperationException {
        if (managerGetAllEntryIds == null) return false;
        Object rawIds = managerGetAllEntryIds.invoke(manager);
        if (!(rawIds instanceof Collection<?> entryIds)) return false;
        List<Identifier> identifiers = new ArrayList<>();
        for (Object entryId : entryIds) {
            if (entryId instanceof Identifier identifier) identifiers.add(identifier);
        }
        identifiers.sort(java.util.Comparator.comparing(Identifier::toString));
        CatalogFingerprint fingerprint = CatalogFingerprint.ofIdentifiers(manager, ComputerGuideData.datapackRevision(), identifiers);
        if (++catalogChecks < 5 && fingerprint.equals(catalogFingerprint)) return false;
        catalogChecks = 0;
        Map<Identifier, ComputerGuideData.Entry> generated = new LinkedHashMap<>();
        Map<Identifier, Identifier> canonicalIds = new HashMap<>();
        for (Identifier sourceId : identifiers) {
            Identifier archiveId = archiveId(sourceId);
            generated.putIfAbsent(archiveId, convertEntry(archiveId, sourceId, null));
            Identifier rawId = rawSourceId(sourceId);
            if (rawId != null) canonicalIds.put(rawId, sourceId);
        }
        boolean changed = ComputerGuideData.replaceFieldGuideEntries(generated);
        catalogFingerprint = fingerprint;
        canonicalEntryIdsByRawId = Map.copyOf(canonicalIds);
        return changed;
    }

    private static ComputerGuideData.Entry convertEntry(Identifier archiveId, Identifier sourceId, Object resolvedEntry) {
        Identifier entityId = null;
        Identifier itemId = null;
        Identifier displayId = null;
        Identifier targetEntityType = null;
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
        List<Identifier> candidates = new ArrayList<>();
        if (targetEntityType != null) candidates.add(targetEntityType);
        if (displayId != null) candidates.add(displayId);
        Identifier rawId = rawSourceId(sourceId);
        if (rawId != null) candidates.add(rawId);
        for (Identifier candidate : candidates) {
            if (entityId == null && BuiltInRegistries.ENTITY_TYPE.containsKey(candidate)) {
                entityId = candidate;
            } else if (itemId == null && BuiltInRegistries.ITEM.containsKey(candidate)) {
                itemId = candidate;
            } else if (itemId == null && BuiltInRegistries.BLOCK.containsKey(candidate)) {
                Item blockItem = BuiltInRegistries.BLOCK.getValue(candidate).asItem();
                if (blockItem != net.minecraft.world.item.Items.AIR) itemId = BuiltInRegistries.ITEM.getKey(blockItem);
            }
        }
        if (itemId != null && BuiltInRegistries.ITEM.getValue(itemId) instanceof BlockItem blockItem) {
            Identifier blockItemId = BuiltInRegistries.ITEM.getKey(blockItem);
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

    private static String titleKey(Identifier entityId, Identifier itemId, Identifier sourceId) {
        if (entityId != null) return BuiltInRegistries.ENTITY_TYPE.getValue(entityId).getDescriptionId();
        if (itemId != null) return BuiltInRegistries.ITEM.getValue(itemId).getDescriptionId();
        String path = sourceId.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) path = path.substring(slash + 1);
        String title = path.replace('_', ' ').replace('-', ' ').trim();
        if (title.isEmpty()) title = sourceId.toString();
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        return "literal:" + title;
    }

    private static Identifier rawSourceId(Identifier sourceId) {
        String namespace = sourceId.getNamespace();
        if (!namespace.equals("entity") && !namespace.equals("item") && !namespace.equals("block")) return null;
        String path = sourceId.getPath();
        int slash = path.indexOf('/');
        if (slash <= 0 || slash == path.length() - 1) return null;
        try {
            return Identifier.fromNamespaceAndPath(path.substring(0, slash), path.substring(slash + 1));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Identifier reflectedResourceLocation(Object target, String methodName) {
        Method method = REFLECTED_METHODS.get(target.getClass()).computeIfAbsent(methodName, name -> {
            try {
                return Optional.of(target.getClass().getMethod(name));
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }).orElse(null);
        if (method == null) return null;
        try {
            Object value = method.invoke(target);
            return value instanceof Identifier id ? id : null;
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
        boolean pending = false;
        for (Object value : unlockedIds) {
            if (value instanceof String id && !state.processedIds.contains(id)) {
                pending = true;
                break;
            }
        }
        if (!pending) return;
        Set<String> currentIds = new LinkedHashSet<>();
        for (Object value : unlockedIds) {
            if (value instanceof String id) currentIds.add(id);
        }
        AccountWorkspace workspace = new AccountWorkspace(player, account);
        ComputerTasks.synchronizeSharedProgress(player, workspace);
        ComputerTaskProgress taskProgress = workspace.taskProgress();
        boolean changed = false;
        Set<String> processedThisPass = new LinkedHashSet<>();
        for (String unlockedId : currentIds) {
            if (state.processedIds.contains(unlockedId)) continue;
            Identifier sourceId = parseBaseId(unlockedId);
            if (sourceId == null) {
                processedThisPass.add(unlockedId);
                continue;
            }
            Identifier canonicalId = managerResolveCanonicalEntry == null
                    ? canonicalEntryIdsByRawId.get(sourceId)
                    : (Identifier) managerResolveCanonicalEntry.invoke(managerGetInstance.invoke(null), sourceId);
            if (canonicalId == null) canonicalId = sourceId;
            Identifier targetId = archiveId(canonicalId);
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

    private static Identifier parseBaseId(String unlockedId) {
        int variant = unlockedId.indexOf('#');
        String base = variant < 0 ? unlockedId : unlockedId.substring(0, variant);
        try {
            return Identifier.parse(base);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Identifier archiveId(Identifier sourceId) {
        return Identifier.fromNamespaceAndPath(FIELD_GUIDE_NAMESPACE, sourceId.toString().replace(':', '/'));
    }

    private static void logReflectionFailure(Throwable exception) {
        if (reflectionFailureLogged) return;
        reflectionFailureLogged = true;
        AntOS.LOGGER.error("Field Guide compatibility could not access the Field Guide API", exception);
    }

    private record CatalogFingerprint(Object catalog, long revision, int size, int[] sizes, Object[] collections) {
        private static CatalogFingerprint of(Map<?, ?> catalog, long revision) {
            int[] sizes = new int[catalog.size()];
            Object[] collections = new Object[catalog.size()];
            int index = 0;
            for (Object value : catalog.values()) {
                if (index >= sizes.length) break;
                collections[index] = value;
                sizes[index] = value instanceof Collection<?> collection ? collection.size() : -1;
                index++;
            }
            return new CatalogFingerprint(catalog, revision, catalog.size(), sizes, collections);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CatalogFingerprint that)) return false;
            if (catalog != that.catalog || revision != that.revision || size != that.size || !Arrays.equals(sizes, that.sizes)) return false;
            return Arrays.equals(collections, that.collections);
        }

        private static CatalogFingerprint ofIdentifiers(Object manager, long revision, Collection<Identifier> identifiers) {
            Object[] ids = identifiers.stream().map(Identifier::toString).toArray();
            return new CatalogFingerprint(manager, revision, ids.length, new int[]{ids.length}, ids);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(catalog) * 31 + Long.hashCode(revision);
        }
    }

    private static final class PlayerSyncState {
        private UUID accountId;
        private final Set<String> processedIds = new HashSet<>();
    }
}
