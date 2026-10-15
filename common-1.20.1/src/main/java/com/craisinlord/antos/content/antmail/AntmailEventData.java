package com.craisinlord.antos.content.antmail;

import com.craisinlord.antos.AntOS;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.advancements.Advancement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;

/**
 * Data-driven Antmail: {@code data/<ns>/antmail/message/*.json} definitions and
 * {@code data/<ns>/antmail/pool/*.json} random-mail pools.
 *
 * <p>Every trigger goes through the per-address schedule: a fired definition is queued with a due day
 * ({@code delay}), then delivered once its {@code conditions} pass and its {@code deliver_window} is open.
 * Definitions with {@code once} (the default for every trigger except {@code random}) are claimed on
 * delivery and never queue twice.
 */
public final class AntmailEventData extends SimplePreparableReloadListener<AntmailEventData.Loaded> {
    private static final AntmailEventData INSTANCE = new AntmailEventData();
    private static volatile Map<ResourceLocation, Definition> definitions = Map.of();
    private static volatile Map<ResourceLocation, Pool> pools = Map.of();
    /** Definition ids grouped by trigger type, and by "type value" for value-keyed triggers; rebuilt on reload. */
    private static volatile TriggerIndex triggers = new TriggerIndex(Map.of(), Map.of(), Map.of());
    private static final int RANDOM_MAIL_BATCH = 16;
    private static final int ADVANCEMENT_EVENT_BACKFILL_TICKS = 40;
    private static volatile boolean advancementEvents;

    private AntmailEventData() {
    }

    public static AntmailEventData instance() {
        return INSTANCE;
    }

    public static Definition definition(String id) {
        ResourceLocation key = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id);
        return key == null ? null : definitions.get(key);
    }

    public static List<ResourceLocation> definitionIds() {
        return definitions.keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }

    /** Fires the configured event path, preserving each matching definition's normal conditions and delay. */
    public static int triggerEvent(ServerPlayer player, String type, String value) {
        if (player == null || type == null || value == null) return 0;
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = data.deliveryAddressFor(player);
        if (address == null) return 0;
        List<ResourceLocation> matches = triggers.matching(type, value);
        fireMatching(player.server, data, address, type, value);
        return matches.size();
    }

    /** Delivers a specific definition immediately, bypassing its trigger conditions, delay, and delivery window. */
    public static AntmailDeliveryResult.Status sendDefinition(ServerPlayer player, ResourceLocation id) {
        if (player == null || id == null) return AntmailDeliveryResult.Status.FAILED;
        Definition definition = definitions.get(id);
        if (definition == null) return AntmailDeliveryResult.Status.FAILED;
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = data.deliveryAddressFor(player);
        if (address == null) return AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND;
        if (definition.once() && data.isClaimed(address, id)) return AntmailDeliveryResult.Status.FAILED;
        AntmailDeliveryResult.Status status = deliver(player.server, data, address, id, definition, true);
        if (status == AntmailDeliveryResult.Status.DELIVERED || status == AntmailDeliveryResult.Status.QUEUED) {
            if (definition.once()) data.claimTrigger(address, id);
            if (definition.delivery() != null && !definition.delivery().onRead()
                    && data.claimPackage(address, id)) AntmailPackages.drop(player, definition.delivery());
        }
        return status;
    }

    public static void registerListeners() {
        com.craisinlord.antos.content.computer.ComputerTaskCompletionEvents.register(AntmailEventData::onTaskComplete);
        com.craisinlord.antos.content.antazon.AntazonUnlockMail.registerListener();
    }

    // ---------------------------------------------------------------- triggers

    public static void onTick(MinecraftServer server) {
        long dayTime = server.overworld().getDayTime();
        long day = dayTime / 24000L;
        AntmailServerData data = AntmailServerData.access(server);
        if (data.beginRandomMailDay(day)) data.queueRandomMail(data.profileAddresses(server));
        List<AntmailAddress> randomBatch = data.takeRandomMail(RANDOM_MAIL_BATCH);
        if (!randomBatch.isEmpty()) rollRandomMail(server, data, randomBatch);
        int tick = server.getTickCount();
        if (tick % 20 == 0) {
            Map<AntmailAddress, ServerPlayer> online = data.onlinePlayersByAddress(server);
            data.updateOnline(online.keySet());
            processSchedule(server, data, online);
        }
        if (tick % 40 == 0) {
            // Polling keeps advancement mail working on loaders without an advancement event, and backfills
            // anything earned before the player had an address.
            boolean backfill = !advancementEvents || tick % ADVANCEMENT_EVENT_BACKFILL_TICKS == 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (backfill) backfillAdvancements(player);
                deliverPendingPackages(player);
            }
        }
    }

    public static void setAdvancementEvents(boolean available) {
        advancementEvents = available;
    }

    public static void onAdvancement(ServerPlayer player, Advancement advancement) {
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = data.deliveryAddressFor(player);
        if (address == null) return;
        fireMatching(player.server, data, address, "advancement", advancement.getId().toString());
    }

    /** Called when a player signs into an Anternet account (create, login or Face ID). */
    public static void onSession(ServerPlayer player, com.craisinlord.antos.content.computer.ComputerWorkspaceData.AccountInfo account) {
        AntmailServerData data = AntmailServerData.access(player.server);
        data.linkAccount(player.getUUID(), account.accountId());
        AntmailAddress address = AntmailAddress.ofUsername(account.username());
        data.purgeReadSelfDeleting(address);
        data.wakeOnline(address);
        backfillAdvancements(player);
        deliverPendingPackages(player);
    }

    public static void onMailRead(ServerPlayer player, AntmailAddress address, AntmailMessage message) {
        ResourceLocation readId = ResourceLocation.tryParse(message.definitionId());
        if (readId == null) return;
        AntmailServerData data = AntmailServerData.access(player.server);
        data.markDefinitionRead(address, readId);
        Definition read = definitions.get(readId);
        if (read != null && read.delivery() != null && read.delivery().onRead() && data.claimPackage(address, readId)) {
            AntmailPackages.drop(player, read.delivery());
        }
        fireMatching(player.server, data, address, "mail_read", readId.toString());
    }

    public static void onTaskComplete(ServerPlayer player, ResourceLocation taskId) {
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = data.deliveryAddressFor(player);
        if (address == null) return;
        data.markTaskCompleted(address, taskId);
        fireMatching(player.server, data, address, "task_complete", taskId.toString());
    }

    private static void fireMatching(MinecraftServer server, AntmailServerData data, AntmailAddress address, String type, String value) {
        for (ResourceLocation definitionId : triggers.matching(type, value)) {
            fire(server, data, address, definitionId, definitions.get(definitionId));
        }
    }

    private record Earned(ResourceLocation id, Definition definition, java.time.Instant when) {
    }

    private static void backfillAdvancements(ServerPlayer player) {
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = data.deliveryAddressFor(player);
        if (address == null) return;
        TriggerIndex index = triggers;
        if (index.advancements().isEmpty()) return;
        Set<ResourceLocation> remaining;
        synchronized (data) {
            if (data.advancementPendingKey != index) {
                data.advancementPending.clear();
                data.advancementPendingKey = index;
            }
            remaining = data.advancementPending.computeIfAbsent(address, ignored -> new java.util.LinkedHashSet<>(index.advancements().keySet()));
        }
        if (remaining.isEmpty()) return;
        List<Earned> earned = new ArrayList<>();
        for (java.util.Iterator<ResourceLocation> iterator = remaining.iterator(); iterator.hasNext(); ) {
            ResourceLocation definitionId = iterator.next();
            Definition definition = definitions.get(definitionId);
            if (definition == null || data.isClaimed(address, definitionId)) {
                iterator.remove();
                continue;
            }
            if (data.isScheduled(address, definitionId)) continue;
            Advancement holder = player.server.getAdvancements().getAdvancement(index.advancements().get(definitionId));
            if (holder == null) continue;
            var progress = player.getAdvancements().getOrStartProgress(holder);
            if (!progress.isDone()) continue;
            java.util.Date earnedAt = progress.getFirstProgressDate();
            java.time.Instant when = earnedAt == null ? null : earnedAt.toInstant();
            earned.add(new Earned(definitionId, definition, when == null ? java.time.Instant.EPOCH : when));
        }
        earned.sort(Comparator.comparing(Earned::when).thenComparing(entry -> entry.id().toString()));
        for (Earned entry : earned) fire(player.server, data, address, entry.id(), entry.definition());
    }

    private static void rollRandomMail(MinecraftServer server, AntmailServerData data, List<AntmailAddress> addresses) {
        var random = server.overworld().random;
        Map<ResourceLocation, List<ResourceLocation>> poolMembers = new HashMap<>();
        List<ResourceLocation> standalone = new ArrayList<>();
        for (ResourceLocation id : triggers.ofType("random")) {
            Definition definition = definitions.get(id);
            if (definition == null) continue;
            if (definition.trigger().pool() != null) poolMembers.computeIfAbsent(definition.trigger().pool(), ignored -> new ArrayList<>()).add(id);
            else standalone.add(id);
        }
        if (standalone.isEmpty() && poolMembers.isEmpty()) return;
        Pass pass = new Pass(server, data, null);
        for (AntmailAddress address : addresses) {
            if (data.inboxFull(address)) continue;
            ServerPlayer player = pass.player(address);
            for (ResourceLocation id : standalone) {
                Definition definition = definitions.get(id);
                if (random.nextDouble() >= definition.trigger().chance()) continue;
                if (subscriptionEligible(pass, address, id, definition) && conditionsPass(pass, address, player, definition)) fire(server, data, address, id, definition);
            }
            for (Map.Entry<ResourceLocation, List<ResourceLocation>> poolEntry : poolMembers.entrySet()) {
                Pool pool = pools.get(poolEntry.getKey());
                if (pool == null || random.nextDouble() >= pool.chancePerDay()) continue;
                List<ResourceLocation> eligible = new ArrayList<>();
                for (ResourceLocation id : poolEntry.getValue()) {
                    Definition definition = definitions.get(id);
                    if (definition.once() && data.isClaimed(address, id) || data.isScheduled(address, id)) continue;
                    if (subscriptionEligible(pass, address, id, definition) && conditionsPass(pass, address, player, definition)) eligible.add(id);
                }
                if (eligible.isEmpty()) continue;
                Set<ResourceLocation> seen = data.poolSeen(address);
                List<ResourceLocation> unseen = new ArrayList<>(eligible.stream().filter(id -> !seen.contains(id)).toList());
                if (unseen.isEmpty()) {
                    data.resetPoolSeen(address, poolEntry.getValue());
                    unseen = new ArrayList<>(eligible);
                }
                for (int drawn = 0; drawn < pool.maxPerDay() && !unseen.isEmpty(); drawn++) {
                    ResourceLocation picked = weightedPick(unseen, random);
                    unseen.remove(picked);
                    data.markPoolSeen(address, picked);
                    fire(server, data, address, picked, definitions.get(picked));
                }
            }
        }
    }

    public static boolean subscriptionEligible(boolean definitionOnly, boolean poolOnly, ResourceLocation definition, ResourceLocation pool,
                                                java.util.Set<ResourceLocation> subscriptions) {
        return !definitionOnly && !poolOnly || subscriptions.contains(definition) || pool != null && subscriptions.contains(pool);
    }

    private static boolean subscriptionEligible(Pass pass, AntmailAddress address, ResourceLocation id, Definition definition) {
        if (!definition.trigger().type().equals("random")) return true;
        Pool pool = definition.trigger().pool() == null ? null : pools.get(definition.trigger().pool());
        boolean poolOnly = pool != null && pool.subscriptionOnly();
        if (!definition.trigger().subscriptionOnly() && !poolOnly) return true;
        return subscriptionEligible(definition.trigger().subscriptionOnly(), poolOnly, id, definition.trigger().pool(), pass.subscriptions(address));
    }

    private static ResourceLocation weightedPick(List<ResourceLocation> candidates, net.minecraft.util.RandomSource random) {
        int total = 0;
        for (ResourceLocation id : candidates) total += definitions.get(id).trigger().weight();
        int roll = random.nextInt(Math.max(1, total));
        for (ResourceLocation id : candidates) {
            roll -= definitions.get(id).trigger().weight();
            if (roll < 0) return id;
        }
        return candidates.get(candidates.size() - 1);
    }

    // ---------------------------------------------------------------- scheduling + delivery

    private static void fire(MinecraftServer server, AntmailServerData data, AntmailAddress address, ResourceLocation id, Definition definition) {
        if (definition == null || data.isScheduled(address, id) || definition.once() && data.isClaimed(address, id)) return;
        long today = server.overworld().getDayTime() / 24000L;
        data.schedule(address, id, today + Math.max(0, definition.trigger().delay()));
        if (definition.trigger().delay() <= 0) processScheduled(server, data, address, id);
    }

    private static void processSchedule(MinecraftServer server, AntmailServerData data, Map<AntmailAddress, ServerPlayer> online) {
        long dayTime = server.overworld().getDayTime();
        List<AntmailServerData.ScheduledMail> due = data.dueSchedule(dayTime / 24000L);
        if (due.isEmpty()) return;
        Pass pass = new Pass(server, data, online);
        for (AntmailServerData.ScheduledMail entry : due) attempt(server, data, pass, entry, dayTime);
    }

    private static void processScheduled(MinecraftServer server, AntmailServerData data, AntmailAddress address, ResourceLocation id) {
        List<AntmailServerData.ScheduledMail> entries = data.scheduledFor(address, id);
        if (entries.isEmpty()) return;
        long dayTime = server.overworld().getDayTime();
        Pass pass = new Pass(server, data, null);
        for (AntmailServerData.ScheduledMail entry : entries) attempt(server, data, pass, entry, dayTime);
    }

    private static void attempt(MinecraftServer server, AntmailServerData data, Pass pass, AntmailServerData.ScheduledMail entry, long dayTime) {
        if (!data.isScheduled(entry)) return;
        Definition definition = definitions.get(entry.definitionId());
        if (definition == null) {
            data.unschedule(entry);
            return;
        }
        if (dayTime / 24000L < entry.dueDay()) return;
        ServerPlayer player = pass.player(entry.address());
        if (player == null && definition.conditions() != null && definition.conditions().needsPlayer()) {
            data.parkUntilOnline(entry);
            return;
        }
        if (definition.window() != null && !definition.window().contains(dayTime % 24000L)) return;
        if (!subscriptionEligible(pass, entry.address(), entry.definitionId(), definition) || !conditionsPass(pass, entry.address(), player, definition)) return;
        AntmailDeliveryResult.Status status = deliver(server, data, entry.address(), entry.definitionId(), definition, false);
        if (status == AntmailDeliveryResult.Status.MAILBOX_FULL) {
            data.parkUntilSpace(entry);
            return;
        }
        data.unschedule(entry);
        if (status != AntmailDeliveryResult.Status.DELIVERED && status != AntmailDeliveryResult.Status.QUEUED) return;
        if (definition.once()) data.claimTrigger(entry.address(), entry.definitionId());
        if (definition.delivery() != null && !definition.delivery().onRead()) {
            if (player == null) data.addPendingPackage(entry.address(), entry.definitionId());
            else if (data.claimPackage(entry.address(), entry.definitionId())) AntmailPackages.drop(player, definition.delivery());
        }
    }

    private static void deliverPendingPackages(ServerPlayer player) {
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = data.deliveryAddressFor(player);
        if (address == null) return;
        for (ResourceLocation id : data.takePendingPackages(address)) {
            Definition definition = definitions.get(id);
            if (definition != null && definition.delivery() != null && data.claimPackage(address, id)) {
                AntmailPackages.drop(player, definition.delivery());
            }
        }
    }

    private static AntmailDeliveryResult.Status deliver(MinecraftServer server, AntmailServerData data, AntmailAddress recipient,
                                                        ResourceLocation id, Definition definition, boolean recordFullMailbox) {
        try {
            AntmailAddress sender = definition.sender().contains("@")
                    ? AntmailAddress.parse(definition.sender()) : AntmailAddress.ofUsername(definition.sender());
            java.util.UUID coinAccount = definition.antcoins() > 0 && com.craisinlord.antos.config.AntOSSettings.appEnabled("ANTAZON")
                    ? data.accountIdForAddress(server, recipient) : null;
            List<AntmailAttachment> attachments = coinAccount == null ? List.of() : List.of(new AntmailAttachment.Antcoins(definition.antcoins()));
            AntmailMessage message = AntmailMessage.create(sender, recipient, definition.subject(), definition.body(),
                    server.overworld().getGameTime(), attachments, id.toString());
            message.setPresentation(definition.style(), definition.senderDisplay(), definition.senderAvatarItem(),
                    definition.senderAvatarEntity(), definition.deleteAfterRead(), definition.notification());
            AntmailDeliveryResult.Status status = data.deliver(server, message, recordFullMailbox).status();
            if (coinAccount != null && (status == AntmailDeliveryResult.Status.DELIVERED || status == AntmailDeliveryResult.Status.QUEUED)) {
                com.craisinlord.antos.content.antazon.AntazonServerData.access(server).credit(coinAccount, definition.antcoins());
            }
            return status;
        } catch (IllegalArgumentException exception) {
            AntOS.LOGGER.warn("Antmail message {} could not be delivered: {}", id, exception.getMessage());
            return AntmailDeliveryResult.Status.FAILED;
        }
    }

    private static final class Pass {
        private final MinecraftServer server;
        private final AntmailServerData data;
        private Map<AntmailAddress, ServerPlayer> players;
        private final Map<AntmailAddress, Map<ResourceLocation, Boolean>> tasks = new HashMap<>();
        private final Map<AntmailAddress, Set<ResourceLocation>> subscriptions = new HashMap<>();

        private Pass(MinecraftServer server, AntmailServerData data, Map<AntmailAddress, ServerPlayer> players) {
            this.server = server;
            this.data = data;
            this.players = players;
        }

        ServerPlayer player(AntmailAddress address) {
            if (players == null) players = data.onlinePlayersByAddress(server);
            return players.get(address);
        }

        Set<ResourceLocation> subscriptions(AntmailAddress address) {
            return subscriptions.computeIfAbsent(address, ignored -> {
                UUID profile = data.accountIdForAddress(server, address);
                return profile == null ? Set.of()
                        : com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(server).diskState(profile).subscriptions();
            });
        }

        boolean taskComplete(AntmailAddress address, ResourceLocation taskId) {
            if (data.hasCompletedTask(address, taskId)) return true;
            return tasks.computeIfAbsent(address, ignored -> new HashMap<>())
                    .computeIfAbsent(taskId, ignored -> AntmailEventData.taskComplete(server, address, taskId));
        }
    }

    // ---------------------------------------------------------------- conditions

    private static boolean conditionsPass(Pass pass, AntmailAddress address, ServerPlayer player, Definition definition) {
        Conditions conditions = definition.conditions();
        if (conditions == null) return true;
        for (ResourceLocation id : conditions.mailRead()) if (!pass.data.hasReadDefinition(address, id)) return false;
        for (ResourceLocation id : conditions.tasksComplete()) if (!pass.taskComplete(address, id)) return false;
        if (!conditions.needsPlayer()) return true;
        if (player == null) return false;
        for (ResourceLocation id : conditions.advancements()) if (!hasAdvancement(player, id)) return false;
        for (ResourceLocation id : conditions.notAdvancements()) if (hasAdvancement(player, id)) return false;
        return conditions.dimension() == null || player.level().dimension().location().equals(conditions.dimension());
    }

    private static boolean hasAdvancement(ServerPlayer player, ResourceLocation id) {
        Advancement holder = player.server.getAdvancements().getAdvancement(id);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static boolean taskComplete(MinecraftServer server, AntmailAddress address, ResourceLocation taskId) {
        var workspaces = com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(server);
        var account = workspaces.accountByUsername(address.username());
        if (account == null) return false;
        var progress = new com.craisinlord.antos.content.computer.ComputerTaskProgress();
        var saved = workspaces.workspaceSection(account.workspaceId(), "TaskProgress");
        if (saved != null) progress.load(saved);
        progress.mergeFrom(workspaces.progress(account.workspaceId()));
        return workspaces.effectiveProgress(account.accountId(), progress).isComplete(taskId);
    }

    // ---------------------------------------------------------------- loading

    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Pool> loadedPools = new HashMap<>();
        for (var resource : manager.listResources("antmail/pool", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = idFor(resource.getKey(), "antmail/pool/");
            try (var reader = resource.getValue().openAsReader()) {
                JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
                double chance = object.has("chance_per_day") ? object.get("chance_per_day").getAsDouble() : 1.0D;
                int max = object.has("max_per_day") ? object.get("max_per_day").getAsInt() : 1;
                loadedPools.put(id, new Pool(Math.max(0.0D, Math.min(1.0D, chance)), Math.max(0, max), object.has("subscription_only") && object.get("subscription_only").getAsBoolean()));
            } catch (Exception exception) {
                AntOS.LOGGER.warn("Ignoring malformed Antmail pool {}: {}", id, exception.getMessage());
            }
        }
        Map<ResourceLocation, Definition> loaded = new HashMap<>();
        for (var resource : manager.listResources("antmail/message", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = idFor(resource.getKey(), "antmail/message/");
            try (var reader = resource.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) continue;
                Definition definition = parse(id, element.getAsJsonObject());
                if (definition.trigger().pool() != null && !loadedPools.containsKey(definition.trigger().pool())) {
                    AntOS.LOGGER.warn("Antmail message {} uses missing pool {}; it will never be drawn", id, definition.trigger().pool());
                }
                loaded.put(id, definition);
            } catch (Exception exception) {
                AntOS.LOGGER.warn("Ignoring malformed Antmail message {}: {}", id, exception.getMessage());
            }
        }
        return new Loaded(Map.copyOf(loaded), Map.copyOf(loadedPools));
    }

    private static ResourceLocation idFor(ResourceLocation file, String prefix) {
        String path = file.getPath();
        return new ResourceLocation(file.getNamespace(), path.substring(prefix.length(), path.length() - 5));
    }

    private static Definition parse(ResourceLocation id, JsonObject object) {
        JsonObject triggerObject = object.getAsJsonObject("trigger");
        String type = triggerObject.get("type").getAsString();
        String value = triggerObject.has("value") ? triggerObject.get("value").getAsString()
                : triggerObject.has("day") ? String.valueOf(triggerObject.get("day").getAsInt()) : "";
        ResourceLocation pool = null;
        if (triggerObject.has("pool")) {
            String raw = triggerObject.get("pool").getAsString();
            pool = raw.contains(":") ? ResourceLocation.tryParse(raw) : new ResourceLocation(id.getNamespace(), raw);
        }
        int weight = triggerObject.has("weight") ? Math.max(1, triggerObject.get("weight").getAsInt()) : 1;
        int delay = triggerObject.has("delay") ? Math.max(0, triggerObject.get("delay").getAsInt())
                : object.has("delay") ? Math.max(0, object.get("delay").getAsInt()) : 0;
        Trigger trigger = new Trigger(type, value, pool, weight, delay, triggerObject.has("subscription_only") && triggerObject.get("subscription_only").getAsBoolean());
        boolean once = object.has("once") ? object.get("once").getAsBoolean() : !type.equals("random");

        Conditions conditions = null;
        if (object.has("conditions") && object.get("conditions").isJsonObject()) {
            JsonObject row = object.getAsJsonObject("conditions");
            conditions = new Conditions(ids(row, "advancements"), ids(row, "not_advancements"), ids(row, "mail_read"),
                    ids(row, "tasks_complete"), row.has("dimension") ? ResourceLocation.tryParse(row.get("dimension").getAsString()) : null);
        }
        DeliverWindow window = null;
        if (object.has("deliver_window") && object.get("deliver_window").isJsonObject()) {
            JsonObject row = object.getAsJsonObject("deliver_window");
            window = new DeliverWindow(row.has("from") ? row.get("from").getAsInt() : 0, row.has("to") ? row.get("to").getAsInt() : 24000);
        }
        AntmailPackages.Delivery delivery = object.has("delivery") && object.get("delivery").isJsonObject()
                ? AntmailPackages.parse(object.getAsJsonObject("delivery")) : null;
        return new Definition(object.get("sender").getAsString(), object.get("subject").getAsString(), object.get("body").getAsString(),
                trigger, once, conditions, string(object, "style"), string(object, "sender_display"),
                string(object, "sender_avatar_item"), string(object, "sender_avatar_entity"),
                object.has("delete_after_read") && object.get("delete_after_read").getAsBoolean(), window,
                string(object, "notification"), delivery,
                object.has("antcoins") && object.get("antcoins").isJsonPrimitive() ? Math.max(0L, object.get("antcoins").getAsLong()) : 0L);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static List<ResourceLocation> ids(JsonObject object, String key) {
        if (!object.has(key)) return List.of();
        JsonElement element = object.get(key);
        List<ResourceLocation> result = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement item : (JsonArray) element) result.add(ResourceLocation.tryParse(item.getAsString()));
        } else {
            result.add(ResourceLocation.tryParse(element.getAsString()));
        }
        return List.copyOf(result);
    }

    @Override
    protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
        definitions = loaded.messages();
        pools = loaded.pools();
        triggers = TriggerIndex.of(loaded.messages());
    }

    private record TriggerIndex(Map<String, List<ResourceLocation>> byType, Map<String, List<ResourceLocation>> byValue,
                                Map<ResourceLocation, ResourceLocation> advancements) {
        static TriggerIndex of(Map<ResourceLocation, Definition> definitions) {
            Map<String, List<ResourceLocation>> byType = new HashMap<>();
            Map<String, List<ResourceLocation>> byValue = new HashMap<>();
            List<ResourceLocation> ids = new ArrayList<>(definitions.keySet());
            ids.sort(Comparator.comparing(ResourceLocation::toString));
            for (ResourceLocation id : ids) {
                Trigger trigger = definitions.get(id).trigger();
                byType.computeIfAbsent(trigger.type(), ignored -> new ArrayList<>()).add(id);
                byValue.computeIfAbsent(key(trigger.type(), trigger.value()), ignored -> new ArrayList<>()).add(id);
            }
            byType.replaceAll((type, list) -> List.copyOf(list));
            byValue.replaceAll((type, list) -> List.copyOf(list));
            Map<ResourceLocation, ResourceLocation> advancements = new java.util.LinkedHashMap<>();
            for (ResourceLocation id : byType.getOrDefault("advancement", List.of())) {
                ResourceLocation advancementId = ResourceLocation.tryParse(definitions.get(id).trigger().value());
                if (advancementId != null) advancements.put(id, advancementId);
            }
            return new TriggerIndex(Map.copyOf(byType), Map.copyOf(byValue), java.util.Collections.unmodifiableMap(advancements));
        }

        List<ResourceLocation> ofType(String type) {
            return byType.getOrDefault(type, List.of());
        }

        List<ResourceLocation> matching(String type, String value) {
            return byValue.getOrDefault(key(type, value), List.of());
        }

        private static String key(String type, String value) {
            return type + "\0" + value;
        }
    }

    public record Loaded(Map<ResourceLocation, Definition> messages, Map<ResourceLocation, Pool> pools) {
    }

    public record Definition(String sender, String subject, String body, Trigger trigger, boolean once, Conditions conditions,
                             String style, String senderDisplay, String senderAvatarItem, String senderAvatarEntity,
                             boolean deleteAfterRead, DeliverWindow window,
                             String notification, AntmailPackages.Delivery delivery, long antcoins) {
        public Definition(String sender, String subject, String body, Trigger trigger, boolean once, Conditions conditions,
                          String style, String senderDisplay, String senderAvatarItem, String senderAvatarEntity,
                          boolean deleteAfterRead, DeliverWindow window, String notification, AntmailPackages.Delivery delivery) {
            this(sender, subject, body, trigger, once, conditions, style, senderDisplay, senderAvatarItem, senderAvatarEntity,
                    deleteAfterRead, window, notification, delivery, 0L);
        }
    }

    public record Trigger(String type, String value, ResourceLocation pool, int weight, int delay, boolean subscriptionOnly) {
        public Trigger(String type, String value, ResourceLocation pool, int weight, int delay) { this(type, value, pool, weight, delay, false); }
        double chance() {
            try {
                return Math.max(0.0D, Math.min(1.0D, Double.parseDouble(value)));
            } catch (NumberFormatException ignored) {
                return 0.0D;
            }
        }
    }

    public record Pool(double chancePerDay, int maxPerDay, boolean subscriptionOnly) {
        public Pool(double chancePerDay, int maxPerDay) { this(chancePerDay, maxPerDay, false); }
    }

    public record Conditions(List<ResourceLocation> advancements, List<ResourceLocation> notAdvancements,
                             List<ResourceLocation> mailRead, List<ResourceLocation> tasksComplete, ResourceLocation dimension) {
        boolean needsPlayer() {
            return !advancements.isEmpty() || !notAdvancements.isEmpty() || dimension != null;
        }
    }

    public record DeliverWindow(int from, int to) {
        boolean contains(long timeOfDay) {
            return from <= to ? timeOfDay >= from && timeOfDay <= to : timeOfDay >= from || timeOfDay <= to;
        }
    }
}
