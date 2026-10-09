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
import net.minecraft.advancements.AdvancementHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
    private static volatile TriggerIndex triggers = new TriggerIndex(Map.of(), Map.of());

    private AntmailEventData() {
    }

    public static AntmailEventData instance() {
        return INSTANCE;
    }

    public static Definition definition(String id) {
        ResourceLocation key = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id);
        return key == null ? null : definitions.get(key);
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
        if (dayTime % 24000L < 20L && data.beginRandomMailDay(day)) rollRandomMail(server, data);
        int tick = server.getTickCount();
        if (tick % 20 == 0) processSchedule(server, data, null, null);
        if (tick % 40 == 0) {
            // Polling keeps advancement mail working on loaders without an advancement event, and backfills
            // anything earned before the player had an address.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                backfillAdvancements(player);
                deliverPendingPackages(player);
            }
        }
    }

    public static void onAdvancement(ServerPlayer player, AdvancementHolder advancement) {
        AntmailServerData data = AntmailServerData.access(player.server);
        AntmailAddress address = data.deliveryAddressFor(player);
        if (address == null) return;
        fireMatching(player.server, data, address, "advancement", advancement.id().toString());
    }

    /** Called when a player signs into an Anternet account (create, login or Face ID). */
    public static void onSession(ServerPlayer player, com.craisinlord.antos.content.computer.ComputerWorkspaceData.AccountInfo account) {
        AntmailServerData data = AntmailServerData.access(player.server);
        data.linkAccount(player.getUUID(), account.accountId());
        data.purgeReadSelfDeleting(AntmailAddress.ofUsername(account.username()));
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
        List<Earned> earned = new ArrayList<>();
        for (ResourceLocation definitionId : triggers.ofType("advancement")) {
            Definition definition = definitions.get(definitionId);
            if (definition == null || data.isClaimed(address, definitionId) || data.isScheduled(address, definitionId)) continue;
            ResourceLocation advancementId = ResourceLocation.tryParse(definition.trigger().value());
            AdvancementHolder holder = advancementId == null ? null : player.server.getAdvancements().get(advancementId);
            if (holder == null) continue;
            var progress = player.getAdvancements().getOrStartProgress(holder);
            if (!progress.isDone()) continue;
            java.time.Instant when = progress.getFirstProgressDate();
            earned.add(new Earned(definitionId, definition, when == null ? java.time.Instant.EPOCH : when));
        }
        earned.sort(Comparator.comparing(Earned::when).thenComparing(entry -> entry.id().toString()));
        for (Earned entry : earned) fire(player.server, data, address, entry.id(), entry.definition());
    }

    private static void rollRandomMail(MinecraftServer server, AntmailServerData data) {
        var random = server.overworld().random;
        Map<ResourceLocation, List<ResourceLocation>> poolMembers = new HashMap<>();
        List<ResourceLocation> standalone = new ArrayList<>();
        for (ResourceLocation id : triggers.ofType("random")) {
            Definition definition = definitions.get(id);
            if (definition == null) continue;
            if (definition.trigger().pool() != null) poolMembers.computeIfAbsent(definition.trigger().pool(), ignored -> new ArrayList<>()).add(id);
            else standalone.add(id);
        }
        Pass pass = new Pass(server, data);
        for (AntmailAddress address : data.profileAddresses(server)) {
            ServerPlayer player = pass.player(address);
            for (ResourceLocation id : standalone) {
                Definition definition = definitions.get(id);
                if (random.nextDouble() >= definition.trigger().chance()) continue;
                if (conditionsPass(pass, address, player, definition)) fire(server, data, address, id, definition);
            }
            for (Map.Entry<ResourceLocation, List<ResourceLocation>> poolEntry : poolMembers.entrySet()) {
                Pool pool = pools.get(poolEntry.getKey());
                if (pool == null || random.nextDouble() >= pool.chancePerDay()) continue;
                List<ResourceLocation> eligible = new ArrayList<>();
                for (ResourceLocation id : poolEntry.getValue()) {
                    Definition definition = definitions.get(id);
                    if (definition.once() && data.isClaimed(address, id) || data.isScheduled(address, id)) continue;
                    if (conditionsPass(pass, address, player, definition)) eligible.add(id);
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
        if (definition.trigger().delay() <= 0) processSchedule(server, data, address, id);
    }

    /** Delivers every due scheduled message, or only the one matching {@code onlyAddress}/{@code onlyId}. */
    private static void processSchedule(MinecraftServer server, AntmailServerData data, AntmailAddress onlyAddress, ResourceLocation onlyId) {
        long dayTime = server.overworld().getDayTime();
        long today = dayTime / 24000L;
        Pass pass = new Pass(server, data);
        for (AntmailServerData.ScheduledMail entry : data.scheduled()) {
            if (onlyAddress != null && (!entry.address().equals(onlyAddress) || !entry.definitionId().equals(onlyId))) continue;
            Definition definition = definitions.get(entry.definitionId());
            if (definition == null) {
                data.unschedule(entry);
                continue;
            }
            if (today < entry.dueDay()) continue;
            if (definition.window() != null && !definition.window().contains(dayTime % 24000L)) continue;
            ServerPlayer player = pass.player(entry.address());
            if (!conditionsPass(pass, entry.address(), player, definition)) continue;
            AntmailDeliveryResult.Status status = deliver(server, data, entry.address(), entry.definitionId(), definition);
            if (status == AntmailDeliveryResult.Status.MAILBOX_FULL) continue;
            data.unschedule(entry);
            if (status != AntmailDeliveryResult.Status.DELIVERED && status != AntmailDeliveryResult.Status.QUEUED) continue;
            if (definition.once()) data.claimTrigger(entry.address(), entry.definitionId());
            if (definition.delivery() != null && !definition.delivery().onRead()) {
                if (player == null) data.addPendingPackage(entry.address(), entry.definitionId());
                else if (data.claimPackage(entry.address(), entry.definitionId())) AntmailPackages.drop(player, definition.delivery());
            }
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
                                                        ResourceLocation id, Definition definition) {
        try {
            AntmailAddress sender = definition.sender().contains("@")
                    ? AntmailAddress.parse(definition.sender()) : AntmailAddress.ofUsername(definition.sender());
            AntmailMessage message = AntmailMessage.create(sender, recipient, definition.subject(), definition.body(),
                    server.overworld().getGameTime(), List.of(), id.toString());
            message.setPresentation(definition.style(), definition.senderDisplay(), definition.senderAvatarItem(),
                    definition.senderAvatarEntity(), definition.deleteAfterRead(), definition.notification());
            return data.deliver(server, message).status();
        } catch (IllegalArgumentException exception) {
            AntOS.LOGGER.warn("Antmail message {} could not be delivered: {}", id, exception.getMessage());
            return AntmailDeliveryResult.Status.FAILED;
        }
    }

    /**
     * Lookups shared across one scheduling or random-mail pass: the online player per address is resolved once, and
     * the task-progress fallback (which deserializes saved workspace progress) is memoized per address and task.
     */
    private static final class Pass {
        private final MinecraftServer server;
        private final AntmailServerData data;
        private Map<AntmailAddress, ServerPlayer> players;
        private final Map<AntmailAddress, Map<ResourceLocation, Boolean>> tasks = new HashMap<>();

        private Pass(MinecraftServer server, AntmailServerData data) {
            this.server = server;
            this.data = data;
        }

        ServerPlayer player(AntmailAddress address) {
            if (players == null) players = data.onlinePlayersByAddress(server);
            return players.get(address);
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
        AdvancementHolder holder = player.server.getAdvancements().get(id);
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
                loadedPools.put(id, new Pool(Math.max(0.0D, Math.min(1.0D, chance)), Math.max(0, max)));
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
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(prefix.length(), path.length() - 5));
    }

    private static Definition parse(ResourceLocation id, JsonObject object) {
        JsonObject triggerObject = object.getAsJsonObject("trigger");
        String type = triggerObject.get("type").getAsString();
        String value = triggerObject.has("value") ? triggerObject.get("value").getAsString()
                : triggerObject.has("day") ? String.valueOf(triggerObject.get("day").getAsInt()) : "";
        ResourceLocation pool = null;
        if (triggerObject.has("pool")) {
            String raw = triggerObject.get("pool").getAsString();
            pool = raw.contains(":") ? ResourceLocation.parse(raw) : ResourceLocation.fromNamespaceAndPath(id.getNamespace(), raw);
        }
        int weight = triggerObject.has("weight") ? Math.max(1, triggerObject.get("weight").getAsInt()) : 1;
        int delay = triggerObject.has("delay") ? Math.max(0, triggerObject.get("delay").getAsInt())
                : object.has("delay") ? Math.max(0, object.get("delay").getAsInt()) : 0;
        Trigger trigger = new Trigger(type, value, pool, weight, delay);
        boolean once = object.has("once") ? object.get("once").getAsBoolean() : !type.equals("random");

        Conditions conditions = null;
        if (object.has("conditions") && object.get("conditions").isJsonObject()) {
            JsonObject row = object.getAsJsonObject("conditions");
            conditions = new Conditions(ids(row, "advancements"), ids(row, "not_advancements"), ids(row, "mail_read"),
                    ids(row, "tasks_complete"), row.has("dimension") ? ResourceLocation.parse(row.get("dimension").getAsString()) : null);
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
                string(object, "notification"), delivery);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static List<ResourceLocation> ids(JsonObject object, String key) {
        if (!object.has(key)) return List.of();
        JsonElement element = object.get(key);
        List<ResourceLocation> result = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement item : (JsonArray) element) result.add(ResourceLocation.parse(item.getAsString()));
        } else {
            result.add(ResourceLocation.parse(element.getAsString()));
        }
        return List.copyOf(result);
    }

    @Override
    protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
        definitions = loaded.messages();
        pools = loaded.pools();
        triggers = TriggerIndex.of(loaded.messages());
    }

    private record TriggerIndex(Map<String, List<ResourceLocation>> byType, Map<String, List<ResourceLocation>> byValue) {
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
            return new TriggerIndex(Map.copyOf(byType), Map.copyOf(byValue));
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
                             String notification, AntmailPackages.Delivery delivery) {
    }

    public record Trigger(String type, String value, ResourceLocation pool, int weight, int delay) {
        double chance() {
            try {
                return Math.max(0.0D, Math.min(1.0D, Double.parseDouble(value)));
            } catch (NumberFormatException ignored) {
                return 0.0D;
            }
        }
    }

    public record Pool(double chancePerDay, int maxPerDay) {
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
