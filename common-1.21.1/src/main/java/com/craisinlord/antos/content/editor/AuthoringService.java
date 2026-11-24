package com.craisinlord.antos.content.editor;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AuthoringRequestPayload;
import com.craisinlord.antos.content.network.AuthoringResultPayload;
import com.craisinlord.antos.content.network.ComputerRequestLimiter;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class AuthoringService {
    private static final Map<UUID, AuthoringWire.Assembly> TRANSFERS = new HashMap<>();
    private static final Set<UUID> EDITORS = new HashSet<>();
    private static final ComputerRequestLimiter LIMITER = new ComputerRequestLimiter(12, 4);
    private static BiConsumer<ServerPlayer, AuthoringResultPayload> sender = (player, payload) -> { };
    private static MinecraftServer activeServer;
    private static Store activeStore;
    private static final class Store {
        final MinecraftServer server;
        final java.util.concurrent.ThreadPoolExecutor worker;
        final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        long catalogRevision = -1;
        long writeRevision;
        List<Summary> catalog = List.of();
        final Map<String, JsonObject> pages = new java.util.LinkedHashMap<>();
        Store(MinecraftServer server) {
            this.server = server;
            worker = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(8), runnable -> {
                        Thread thread = new Thread(runnable, "AntOS content authoring"); thread.setDaemon(true); return thread;
                    });
        }
        void close() { closed.set(true); worker.shutdownNow(); }
    }
    private record Summary(AuthoringKind kind, ResourceLocation id, String source) { }
    private record Document(JsonObject body, String hash, String source, boolean authored) { }
    private AuthoringService() { }

    public static void setSender(BiConsumer<ServerPlayer, AuthoringResultPayload> value) { sender = value; }

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("antos").then(Commands.literal("editing").requires(source -> source.hasPermission(2))
                .then(Commands.literal("on").executes(context -> configure(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> configure(context.getSource(), false)))));
    }

    private static int configure(CommandSourceStack source, boolean enabled) {
        if (!AntOSSettings.setContentEditingEnabled(enabled)) {
            source.sendFailure(Component.literal("Could not update antos.json; the existing setting was retained."));
            return 0;
        }
        if (!enabled) EDITORS.clear();
        source.sendSuccess(() -> Component.literal("AntOS content editing " + (enabled ? "allowed; operators turn it on in Settings > Apps" : "turned off for everyone")), true);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers())
            if (player.hasPermissions(2)) send(player, UUID.randomUUID().toString(), status(player));
        return 1;
    }

    public static void disconnect(UUID player) {
        TRANSFERS.remove(player);
        EDITORS.remove(player);
        LIMITER.forget(player);
    }

    public static void stopped() {
        for (UUID player : new HashSet<>(TRANSFERS.keySet())) disconnect(player);
        EDITORS.clear();
        LIMITER.clear();
        if (activeStore != null) activeStore.close();
        activeStore = null;
        activeServer = null;
    }

    public static void handle(ServerPlayer player, AuthoringRequestPayload payload) {
        if (activeServer != player.server) { stopped(); activeServer = player.server; activeStore = new Store(player.server); }
        if (!player.hasPermissions(2)) {
            TRANSFERS.remove(player.getUUID());
            EDITORS.remove(player.getUUID());
            if (LIMITER.tryAcquire(player.getUUID(), 1)) send(player, payload.id(), status(player));
            return;
        }
        try {
            long now = System.currentTimeMillis();
            TRANSFERS.values().removeIf(value -> value.expired(now));
            AuthoringWire.Chunk chunk = payload.chunk();
            AuthoringWire.Assembly assembly = TRANSFERS.get(player.getUUID());
            if (chunk.index() == 0) {
                if (!LIMITER.tryAcquire(player.getUUID(), 1)) throw new IllegalArgumentException("Editor is busy; try again shortly");
                assembly = new AuthoringWire.Assembly(chunk, now);
                TRANSFERS.put(player.getUUID(), assembly);
            }
            if (assembly == null) throw new IllegalArgumentException("Editor transfer has expired");
            String data = assembly.accept(chunk, now);
            if (data == null) return;
            TRANSFERS.remove(player.getUUID());
            JsonObject request = AuthoringValidation.envelope(data);
            String operation = AuthoringValidation.required(request, "operation", 24);
            JsonObject response;
            if (operation.equals("status")) response = status(player);
            else {
                if (!AntOSSettings.contentEditingEnabled()) throw new IllegalArgumentException("Authoring is disabled; use /antos editing on");
                if (operation.equals("enter")) { EDITORS.add(player.getUUID()); response = status(player); }
                else if (operation.equals("leave")) { EDITORS.remove(player.getUUID()); response = status(player); }
                else {
                    if (!EDITORS.contains(player.getUUID())) throw new IllegalArgumentException("Enter editing mode before changing content");
                    schedule(player, payload.id(), operation, request, activeStore);
                    return;
                }
            }
            response.addProperty("operation", operation);
            response.addProperty("reload_revision", ComputerGuideData.datapackRevision());
            response.addProperty("ok", true);
            send(player, payload.id(), response);
        } catch (Exception exception) {
            TRANSFERS.remove(player.getUUID());
            JsonObject error = new JsonObject();
            error.addProperty("ok", false);
            error.addProperty("error", exception.getMessage() == null ? "Invalid editor request" : exception.getMessage());
            send(player, payload.id(), error);
        }
    }

    private static void schedule(ServerPlayer player, String id, String operation, JsonObject request, Store store) {
        try {
            store.worker.execute(() -> {
                JsonObject response;
                try {
                    requirePermission(player, store);
                    if (operation.equals("list") && request.has("refresh") && request.get("refresh").getAsBoolean()) {
                        store.catalogRevision = -1;
                        store.pages.clear();
                    }
                    response = execute(player, operation, request, store);
                    response.addProperty("ok", true);
                } catch (Exception exception) {
                    response = new JsonObject();
                    response.addProperty("ok", false);
                    response.addProperty("error", exception.getMessage() == null ? "Editor operation failed" : exception.getMessage());
                }
                JsonObject result = response;
                store.server.execute(() -> {
                    if (store.closed.get() || activeStore != store) return;
                    if (store.server.getPlayerList().getPlayer(player.getUUID()) != player) return;
                    if (!player.hasPermissions(2) || !AntOSSettings.contentEditingEnabled() || !EDITORS.contains(player.getUUID())) {
                        JsonObject denied = status(player);
                        denied.addProperty("ok", false);
                        denied.addProperty("error", "Editing permission or editing mode is no longer active");
                        send(player, id, denied);
                        return;
                    }
                    if (operation.equals("save") && result.get("ok").getAsBoolean()) {
                        try { selectPack(store.server); }
                        catch (RuntimeException exception) { result.addProperty("message", "Saved. Enable the antos-authored datapack before /reload."); }
                    }
                    result.addProperty("operation", operation);
                    result.addProperty("reload_revision", ComputerGuideData.datapackRevision());
                    send(player, id, result);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            throw new IllegalArgumentException("Editor is busy; wait for the pending operation and try again");
        }
    }

    private static void requirePermission(ServerPlayer player, Store store) throws IOException {
        if (store.closed.get()) throw new IOException("Editor server has stopped");
        try {
            boolean allowed = store.server.submit(() -> !store.closed.get() && player.hasPermissions(2)
                    && AntOSSettings.contentEditingEnabled() && EDITORS.contains(player.getUUID())
                    && store.server.getPlayerList().getPlayer(player.getUUID()) == player).get(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!allowed) throw new IOException("Editing permission or editing mode is no longer active");
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IOException("Editor operation was cancelled", exception); }
        catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) { throw new IOException("Editor server did not respond; try again", exception); }
    }

    private static void selectPack(MinecraftServer server) {
        String packId = "file/" + AuthoringFiles.PACK_NAME;
        List<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        if (!selected.isEmpty() && selected.getLast().equals(packId)) return;
        if (!selected.contains(packId)) server.getPackRepository().reload();
        selected.remove(packId);
        selected.add(packId);
        server.getPackRepository().setSelected(selected);
    }

    private static JsonObject status(ServerPlayer player) {
        JsonObject result = new JsonObject();
        boolean allowed = player.hasPermissions(2) && AntOSSettings.contentEditingEnabled();
        result.addProperty("operation", "status");
        result.addProperty("ok", true);
        result.addProperty("available", allowed);
        result.addProperty("editing", allowed && EDITORS.contains(player.getUUID()));
        result.addProperty("reload_revision", ComputerGuideData.datapackRevision());
        return result;
    }

    private static JsonObject execute(ServerPlayer player, String operation, JsonObject request, Store store) throws IOException {
        AuthoringKind kind = AuthoringKind.parse(AuthoringValidation.required(request, "kind", 24));
        if (operation.equals("list")) return list(player.server, kind, request.has("offset") ? Math.max(0, request.get("offset").getAsInt()) : 0, store);
        ResourceLocation id = ResourceLocation.parse(AuthoringValidation.required(request, "id", 240));
        Path datapacks = player.server.getWorldPath(LevelResource.DATAPACK_DIR);
        Path file = AuthoringFiles.document(datapacks, kind, id.getNamespace(), id.getPath());
        Document current = read(player.server, kind, id);
        JsonObject result = new JsonObject();
        result.addProperty("kind", kind.key());
        result.addProperty("id", id.toString());
        if (operation.equals("get")) {
            if (current == null) throw new IllegalArgumentException("Content no longer exists; refresh the list");
            encode(result, current);
            return result;
        }
        if (!operation.equals("save") && !operation.equals("remove")) throw new IllegalArgumentException("Unsupported editor operation");
        String expected = AuthoringValidation.string(request, "hash", 64);
        if (!expected.equals(current == null ? "" : current.hash()))
            throw new IllegalArgumentException("Content changed since it was opened. Refresh before saving; your draft has been retained.");
        if (operation.equals("remove")) {
            if (current == null || !current.authored() || !Files.isRegularFile(file))
                throw new IllegalArgumentException("Only editor-authored files can be removed; inherited pack files remain untouched");
            requirePermission(player, store);
            AuthoringFiles.remove(file);
            result.addProperty("removed", true);
        } else {
            JsonObject body = AuthoringValidation.object(request, "body").deepCopy();
            AuthoringValidation.validate(kind, body);
            validateRuntime(player.server, kind, id, body);
            String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(body) + "\n";
            AuthoringValidation.parse(text);
            requirePermission(player, store);
            AuthoringFiles.save(datapacks, kind, id.getNamespace(), id.getPath(), text);
            encode(result, new Document(body, AuthoringFiles.hash(text), "file/" + AuthoringFiles.PACK_NAME, true));
        }
        store.writeRevision++;
        store.catalogRevision = -1;
        store.pages.clear();
        result.addProperty("message", "Saved to antos-authored. Run /reload to apply.");
        return result;
    }

    private static void encode(JsonObject target, Document document) {
        target.add("body", document.body().deepCopy());
        target.addProperty("hash", document.hash());
        target.addProperty("source", document.source());
        target.addProperty("authored", document.authored());
    }

    private static Document read(MinecraftServer server, AuthoringKind kind, ResourceLocation id) throws IOException {
        Path authored = AuthoringFiles.document(server.getWorldPath(LevelResource.DATAPACK_DIR), kind, id.getNamespace(), id.getPath());
        if (Files.isRegularFile(authored)) {
            if (Files.size(authored) > AuthoringValidation.MAX_DOCUMENT_CHARS * 4L) throw new IOException("Content file exceeds size limit");
            String text = Files.readString(authored);
            return new Document(AuthoringValidation.parse(text), AuthoringFiles.hash(text), "file/" + AuthoringFiles.PACK_NAME, true);
        }
        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), kind.directory() + "/" + id.getPath() + ".json");
        var resources = server.getResourceManager().getResourceStack(path);
        for (int index = resources.size() - 1; index >= 0; index--) {
            var resource = resources.get(index);
            if (resource.sourcePackId().equals("file/" + AuthoringFiles.PACK_NAME)) continue;
            try (var reader = resource.openAsReader()) {
                char[] buffer = new char[4096];
                StringBuilder text = new StringBuilder();
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    text.append(buffer, 0, count);
                    if (text.length() > AuthoringValidation.MAX_DOCUMENT_CHARS) throw new IOException("Content file exceeds size limit");
                }
                return new Document(AuthoringValidation.parse(text.toString()), AuthoringFiles.hash(text.toString()), resource.sourcePackId(), false);
            }
        }
        return null;
    }

    private static JsonObject list(MinecraftServer server, AuthoringKind requested, int offset, Store store) throws IOException {
        long revision = ComputerGuideData.datapackRevision() + store.writeRevision;
        if (store.catalogRevision != revision) {
            store.pages.clear();
            java.util.TreeMap<String, Summary> result = new java.util.TreeMap<>();
            Path data = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(AuthoringFiles.PACK_NAME).resolve("data");
            for (AuthoringKind kind : AuthoringKind.values()) {
                server.getResourceManager().listResources(kind.directory(), path -> kind.includes(path.getPath())).forEach((path, resource) -> {
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), path.getPath().substring(kind.directory().length() + 1, path.getPath().length() - 5));
                    addSummary(result, new Summary(kind, id, resource.sourcePackId()));
                });
                if (Files.isDirectory(data) && !Files.isSymbolicLink(data)) try (var namespaces = Files.list(data)) {
                    for (Path namespace : namespaces.filter(Files::isDirectory).limit(256).toList()) {
                        Path directory = namespace.resolve(kind.directory());
                        if (!Files.isDirectory(directory) || Files.isSymbolicLink(namespace)) continue;
                        try (var files = Files.walk(directory, 16)) {
                            files.filter(Files::isRegularFile).filter(path -> kind.includes(kind.directory() + "/" + directory.relativize(path).toString().replace('\\', '/'))).limit(4096).forEach(file -> {
                                String relative = directory.relativize(file).toString().replace('\\', '/');
                                ResourceLocation id = ResourceLocation.tryParse(namespace.getFileName() + ":" + relative.substring(0, relative.length() - 5));
                                if (id != null) addSummary(result, new Summary(kind, id, "file/" + AuthoringFiles.PACK_NAME));
                            });
                        }
                    }
                }
            }
            store.catalog = List.copyOf(result.values());
            store.catalogRevision = revision;
        }
        String pageKey = requested.key() + ":" + offset;
        JsonObject cached = store.pages.get(pageKey);
        if (cached != null) return cached.deepCopy();
        JsonArray entries = new JsonArray();
        int total = (int) store.catalog.stream().filter(value -> value.kind() == requested).count();
        int skipped = 0;
        for (Summary summary : store.catalog) if (summary.kind() == requested) {
            if (skipped++ < offset) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("id", summary.id().toString());
            entry.addProperty("source", summary.source());
            try {
                Document document = read(server, summary.kind(), summary.id());
                if (document == null) continue;
                entry.addProperty("source", document.source());
                for (String key : List.of("title", "name", "label", "subject", "command", "type", "icon", "icon_entity", "category", "position", "requires", "requires_any", "connects", "shape", "size", "icon_scale", "green_tint", "render_mob_from_spawn_egg"))
                    if (document.body().has(key)) {
                        JsonElement value = document.body().get(key);
                        if (value.isJsonArray()) {
                            JsonArray preview = new JsonArray();
                            for (int index = 0; index < Math.min(64, value.getAsJsonArray().size()); index++) preview.add(value.getAsJsonArray().get(index));
                            entry.add(key, preview);
                        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                            String label = value.getAsString();
                            entry.addProperty(key, label.substring(0, Math.min(512, label.length())));
                        } else entry.add(key, value.deepCopy());
                        if (entry.toString().length() > 2000) { entry.remove(key); entry.addProperty("preview_truncated", true); }
                    }
            } catch (RuntimeException | IOException exception) { entry.addProperty("error", "Invalid source definition"); }
            entries.add(entry);
            if (entries.size() >= 128) break;
        }
        JsonObject result = new JsonObject();
        result.addProperty("kind", requested.key());
        result.addProperty("offset", offset);
        result.addProperty("total", total);
        result.add("entries", entries);
        while (store.pages.size() >= 32) store.pages.remove(store.pages.keySet().iterator().next());
        store.pages.put(pageKey, result.deepCopy());
        return result;
    }

    private static void addSummary(java.util.TreeMap<String, Summary> summaries, Summary summary) {
        String key = summary.kind().key() + "\u0000" + summary.id();
        if (summaries.containsKey(key)) return;
        if (summaries.size() < 8192) {
            summaries.put(key, summary);
            return;
        }
        String largest = summaries.lastKey();
        if (key.compareTo(largest) < 0) {
            summaries.remove(largest);
            summaries.put(key, summary);
        }
    }

    private static void validateRuntime(MinecraftServer server, AuthoringKind kind, ResourceLocation id, JsonObject body) throws IOException {
        if (kind == AuthoringKind.TASK) ComputerGuideData.validateAuthoringTask(id, body);
        if (kind == AuthoringKind.CATEGORY || kind == AuthoringKind.GROUP || kind == AuthoringKind.REWARD_POOL)
            ComputerGuideData.validateAuthoringCatalog(kind.key(), id, body);
        if (kind == AuthoringKind.MESSAGE) com.craisinlord.antos.content.antmail.AntmailEventData.validateAuthoring(id, body);
        if (kind == AuthoringKind.TAKEOVER) com.craisinlord.antos.content.antmail.AntmailEventData.validateAuthoringTakeover(id, body);
        if (kind == AuthoringKind.PRODUCT) com.craisinlord.antos.content.antazon.AntazonData.validateAuthoring(id, body);
        if (kind == AuthoringKind.DEAL_CAMPAIGN) com.craisinlord.antos.content.antazon.AntazonDealData.validateAuthoring(id, body);
        if (kind == AuthoringKind.ARCHIVE_ENTRY) ComputerGuideData.validateAuthoringArchiveEntry(id, body);
        if (kind == AuthoringKind.ARCHIVE_FOLDER) ComputerGuideData.validateAuthoringArchiveFolder(id, body);
        if (kind == AuthoringKind.DISK) ComputerGuideData.validateAuthoringDisk(id, body);
        if (kind == AuthoringKind.DISK_CATEGORY) ComputerGuideData.validateAuthoringDiskCategory(id, body);
        if (kind == AuthoringKind.PROGRAM) com.craisinlord.antos.content.computer.ComputerProgramData.validateAuthoring(id, body);
        if (kind == AuthoringKind.TERMINAL_COMMAND)
            com.craisinlord.antos.content.computer.terminal.TerminalCommandData.validateAuthoring(id, body);
        if (kind == AuthoringKind.SELL_RULE) {
            int value = body.get("value").getAsInt();
            if (value < 1) throw new IllegalArgumentException("Sell rule value must be at least one");
            for (String key : List.of("item", "tag")) if (body.has(key)) {
                String raw = body.get(key).getAsString().replaceFirst("^#", "");
                ResourceLocation idValue = ResourceLocation.parse(raw);
                if (key.equals("item") && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(idValue))
                    throw new IllegalArgumentException("Unknown sell item " + idValue);
            }
            for (String key : List.of("items", "tags", "unlock_tasks", "unlock_disks", "unlock_commands")) if (body.has(key)) {
                for (JsonElement element : AuthoringValidation.array(body, key, 1024)) {
                    String raw = element.getAsString().replaceFirst("^#", "");
                    ResourceLocation.parse(raw);
                }
            }
        }
        if (kind == AuthoringKind.MESSAGE && body.has("delivery")) {
            JsonObject delivery = body.getAsJsonObject("delivery");
            for (JsonElement element : delivery.has("items") ? AuthoringValidation.array(delivery, "items", 108) : new JsonArray()) {
                JsonObject item = element.getAsJsonObject();
                if (!item.has("explorer_map")) net.minecraft.world.item.ItemStack.CODEC.parse(
                        net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess()), item)
                        .getOrThrow(error -> new IllegalArgumentException("Invalid delivery item: " + error));
                else ResourceLocation.parse(item.get("explorer_map").getAsString().replaceFirst("^#", ""));
            }
        }
        if (kind == AuthoringKind.TASK || kind == AuthoringKind.REWARD_POOL)
            validateGraph(server, kind, id, id, body, new HashSet<>(), new HashSet<>(), 0);
    }

    private static void validateGraph(MinecraftServer server, AuthoringKind kind, ResourceLocation root, ResourceLocation current,
                                      JsonObject replacement, Set<ResourceLocation> pending, Set<ResourceLocation> complete, int depth) throws IOException {
        if (complete.contains(current)) return;
        if (depth > (kind == AuthoringKind.TASK ? 256 : 7)) throw new IllegalArgumentException("Dependency nesting exceeds the supported limit at " + current);
        if (!pending.add(current)) throw new IllegalArgumentException("Dependency cycle at " + current);
        Document existing = current.equals(root) ? null : read(server, kind, current);
        JsonObject body = current.equals(root) ? replacement : existing == null ? null : existing.body();
        if (body == null) throw new IllegalArgumentException("Missing prerequisite or pool " + current + "; save it first");
        List<ResourceLocation> dependencies = new ArrayList<>();
        if (kind == AuthoringKind.TASK) {
            for (String key : List.of("requires", "requires_any")) if (body.has(key))
                for (JsonElement value : body.getAsJsonArray(key)) dependencies.add(ResourceLocation.parse(value.getAsString()));
        } else for (JsonElement value : body.getAsJsonArray("entries")) {
            JsonObject entry = value.getAsJsonObject();
            if (entry.has("pool")) dependencies.add(ResourceLocation.parse(entry.get("pool").getAsString()));
            else {
                ResourceLocation item = ResourceLocation.parse(AuthoringValidation.required(entry, "item", 240));
                if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item)) throw new IllegalArgumentException("Unknown reward item " + item);
            }
        }
        for (ResourceLocation dependency : dependencies) validateGraph(server, kind, root, dependency, replacement, pending, complete, depth + 1);
        pending.remove(current);
        complete.add(current);
    }

    private static void send(ServerPlayer player, String id, JsonObject response) {
        String text = response.toString();
        if (text.length() > AuthoringWire.MAX_CHARS) {
            response = new JsonObject();
            response.addProperty("ok", false);
            response.addProperty("error", "Editor response exceeds its limit; narrow the content catalog");
            text = response.toString();
        }
        for (AuthoringWire.Chunk chunk : AuthoringWire.split(id, text))
            sender.accept(player, new AuthoringResultPayload(chunk.id(), chunk.index(), chunk.total(), chunk.data()));
    }
}
