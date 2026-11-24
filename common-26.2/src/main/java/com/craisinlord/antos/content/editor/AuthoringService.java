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
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class AuthoringService {
    private static final Map<UUID, AuthoringWire.Assembly> TRANSFERS = new HashMap<>();
    private static final Set<UUID> EDITORS = new HashSet<>();
    private static final ComputerRequestLimiter LIMITER = new ComputerRequestLimiter(40, 12);
    private static BiConsumer<ServerPlayer, AuthoringResultPayload> sender = (player, payload) -> { };
    private static MinecraftServer activeServer;
    private static Store activeStore;
    private static final class Store {
        final MinecraftServer server;
        final java.util.concurrent.ThreadPoolExecutor worker;
        final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        long catalogRevision = -1;
        long writeRevision;
        String world;
        List<Summary> catalog = List.of();
        final Map<String, JsonObject> pages = new java.util.LinkedHashMap<>();
        final Map<UUID, Map<String, Staged>> staged = new HashMap<>();
        Store(MinecraftServer server) {
            this.server = server;
            worker = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(8), runnable -> {
                        Thread thread = new Thread(runnable, "AntOS content authoring"); thread.setDaemon(true); return thread;
                    });
        }
        void close() { closed.set(true); worker.shutdownNow(); }
    }
    private record Summary(AuthoringKind kind, Identifier id, String source, Path file) { }
    private record Staged(AuthoringKind kind, Identifier id, String hash, JsonObject body) { }
    private record Document(JsonObject body, String hash, String source, boolean authored) { }
    private AuthoringService() { }

    private static boolean isOperator(ServerPlayer player) {
        return player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public static void setSender(BiConsumer<ServerPlayer, AuthoringResultPayload> value) { sender = value; }

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("antos").then(Commands.literal("editing").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("on").executes(context -> configure(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> configure(context.getSource(), false)))));
        dispatcher.register(Commands.literal("antos").then(Commands.literal("editor").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .executes(context -> open(context.getSource(), null, null))
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(AuthoringKind.values()).map(AuthoringKind::key), builder))
                        .executes(context -> open(context.getSource(), StringArgumentType.getString(context, "type"), null))
                        .then(Commands.argument("id", IdentifierArgument.id())
                                .suggests((context, builder) -> {
                                    try {
                                        AuthoringKind kind = AuthoringKind.parse(StringArgumentType.getString(context, "type"));
                                        return SharedSuggestionProvider.suggestResource(ids(context.getSource().getServer(), kind), builder);
                                    } catch (IllegalArgumentException exception) { return builder.buildFuture(); }
                                })
                                .executes(context -> open(context.getSource(), StringArgumentType.getString(context, "type"),
                                        IdentifierArgument.getId(context, "id")))))));
    }

    private static int open(CommandSourceStack source, String type, Identifier id) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.translatable("antos.studio.command.player")); return 0; }
        if (!AntOSSettings.contentEditingEnabled()) { source.sendFailure(Component.translatable("antos.studio.command.off")); return 0; }
        JsonObject message = status(player);
        message.addProperty("operation", "open");
        if (type != null) {
            AuthoringKind kind;
            try { kind = AuthoringKind.parse(type); }
            catch (IllegalArgumentException exception) { source.sendFailure(Component.translatable("antos.studio.command.type", type)); return 0; }
            message.addProperty("kind", kind.key());
            if (id != null) {
                try {
                    if (read(player.level().getServer(), kind, id, Map.of()) == null) { source.sendFailure(Component.translatable("antos.studio.command.missing", id.toString())); return 0; }
                } catch (IOException | RuntimeException exception) {
                    source.sendFailure(Component.translatable("antos.studio.command.unreadable", id.toString(), AuthoringChecks.clean(exception)));
                    return 0;
                }
                message.addProperty("id", id.toString());
            }
        }
        send(player, UUID.randomUUID().toString(), message);
        return 1;
    }

    private static List<Identifier> ids(MinecraftServer server, AuthoringKind kind) {
        java.util.TreeSet<Identifier> result = new java.util.TreeSet<>();
        for (Identifier path : server.getResourceManager().listResources(kind.directory(), path -> kind.includes(path.getPath())).keySet())
            result.add(Identifier.fromNamespaceAndPath(path.getNamespace(), path.getPath().substring(kind.directory().length() + 1, path.getPath().length() - 5)));
        return List.copyOf(result);
    }

    private static int configure(CommandSourceStack source, boolean enabled) {
        if (!AntOSSettings.setContentEditingEnabled(enabled)) {
            source.sendFailure(Component.literal("Could not update antos.json; the existing setting was retained."));
            return 0;
        }
        if (!enabled) EDITORS.clear();
        source.sendSuccess(() -> Component.literal("AntOS content editing " + (enabled ? "allowed; operators open AntStudio with /antos editor" : "turned off for everyone")), true);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers())
            if (isOperator(player)) send(player, UUID.randomUUID().toString(), status(player));
        return 1;
    }

    public static void disconnect(UUID player) {
        TRANSFERS.remove(player);
        EDITORS.remove(player);
        LIMITER.forget(player);
        Store store = activeStore;
        if (store != null && !store.closed.get()) {
            try { store.worker.execute(() -> store.staged.remove(player)); }
            catch (java.util.concurrent.RejectedExecutionException ignored) { }
        }
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
        if (activeServer != player.level().getServer()) { stopped(); activeServer = player.level().getServer(); activeStore = new Store(player.level().getServer()); }
        if (!isOperator(player)) {
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
                if (!AntOSSettings.contentEditingEnabled()) throw new IllegalArgumentException("Content editing is off on this server. Use /antos editing on to allow it.");
                if (operation.equals("enter")) { EDITORS.add(player.getUUID()); response = status(player); response.addProperty("world", world(activeStore)); }
                else if (operation.equals("leave")) { EDITORS.remove(player.getUUID()); response = status(player); }
                else {
                    if (!EDITORS.contains(player.getUUID())) throw new IllegalArgumentException("AntStudio lost its place on this server. Reopen it; your drafts are kept.");
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
                    response = execute(player, operation, request, store);
                    response.addProperty("ok", true);
                } catch (Exception exception) {
                    response = new JsonObject();
                    response.addProperty("ok", false);
                    response.addProperty("error", exception.getMessage() == null ? "Editor operation failed" : exception.getMessage());
                }
                JsonObject result = response;
                store.server.execute(() -> finish(player, id, operation, result, store));
            });
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            throw new IllegalArgumentException("Editor is busy; wait for the pending operation and try again");
        }
    }

    private static String world(Store store) {
        if (store.world != null) return store.world;
        Path file = store.server.getWorldPath(LevelResource.ROOT).resolve("antos_studio_id");
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= 64) {
                try { store.world = UUID.fromString(Files.readString(file).trim()).toString(); }
                catch (IllegalArgumentException ignored) { }
            }
            if (store.world == null) {
                String made = UUID.randomUUID().toString();
                Files.writeString(file, made);
                store.world = made;
            }
        } catch (IOException exception) {
            com.craisinlord.antos.AntOS.LOGGER.warn("AntStudio could not identify this world", exception);
            store.world = "";
        }
        return store.world;
    }

    private static boolean current(ServerPlayer player, Store store) {
        return !store.closed.get() && activeStore == store && store.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static void finish(ServerPlayer player, String id, String operation, JsonObject result, Store store) {
        if (store.closed.get() || activeStore != store) return;
        boolean written = result.has("written");
        if (!written && !current(player, store)) return;
        if (!written && (!isOperator(player) || !AntOSSettings.contentEditingEnabled() || !EDITORS.contains(player.getUUID()))) {
            JsonObject denied = status(player);
            denied.addProperty("ok", false);
            denied.addProperty("error", "Editing permission is no longer active");
            send(player, id, denied);
            return;
        }
        boolean ok = result.get("ok").getAsBoolean();
        result.addProperty("operation", operation);
        if (ok && (result.has("written") || operation.equals("save"))) {
            try { selectPack(store.server); }
            catch (RuntimeException exception) { result.addProperty("message", "Saved. Enable the antos-authored datapack, then reload."); }
        }
        if (ok && result.has("reload")) {
            result.remove("reload");
            long started = ComputerGuideData.datapackRevision();
            List<String> selected = new ArrayList<>(store.server.getPackRepository().getSelectedIds());
            store.server.reloadResources(selected).whenComplete((ignored, failure) -> store.server.execute(() -> {
                if (store.closed.get() || activeStore != store) return;
                store.writeRevision++;
                if (failure != null) {
                    com.craisinlord.antos.AntOS.LOGGER.warn("AntStudio reload failed", failure);
                    result.addProperty("reload_failed", AuthoringChecks.clean(failure));
                } else result.addProperty("reloaded", ComputerGuideData.datapackRevision() != started);
                result.addProperty("reload_revision", ComputerGuideData.datapackRevision());
                if (current(player, store)) send(player, id, result);
            }));
            return;
        }
        result.addProperty("reload_revision", ComputerGuideData.datapackRevision());
        if (current(player, store)) send(player, id, result);
    }

    private static void requirePermission(ServerPlayer player, Store store) throws IOException {
        if (store.closed.get()) throw new IOException("Editor server has stopped");
        try {
            boolean allowed = store.server.submit(() -> !store.closed.get() && isOperator(player)
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
        boolean allowed = isOperator(player) && AntOSSettings.contentEditingEnabled();
        result.addProperty("operation", "status");
        result.addProperty("ok", true);
        result.addProperty("available", allowed);
        result.addProperty("editing", allowed && EDITORS.contains(player.getUUID()));
        result.addProperty("reload_revision", ComputerGuideData.datapackRevision());
        return result;
    }

    private static JsonObject execute(ServerPlayer player, String operation, JsonObject request, Store store) throws IOException {
        switch (operation) {
            case "tree" -> { return tree(player.level().getServer(), request.has("offset") ? Math.max(0, request.get("offset").getAsInt()) : 0,
                    request.has("refresh") && request.get("refresh").getAsBoolean(), store); }
            case "get_many" -> { return getMany(player.level().getServer(), request); }
            case "stage" -> { return stage(player, request, store); }
            case "check" -> { return check(player.level().getServer(), store.staged.getOrDefault(player.getUUID(), Map.of())); }
            case "commit" -> { return commit(player, request, store); }
            case "reload" -> {
                JsonObject result = new JsonObject();
                result.addProperty("reload", true);
                return result;
            }
            case "references" -> { return AuthoringTools.references(player.level().getServer(), AuthoringValidation.required(request, "type", 32)); }
            case "give_disk", "reset_disk", "send_test" -> { return onServer(store, () -> AuthoringTools.run(player, operation, request)); }
            default -> { }
        }
        AuthoringKind kind = AuthoringKind.parse(AuthoringValidation.required(request, "kind", 24));
        Identifier id = Identifier.parse(AuthoringValidation.required(request, "id", 240));
        Path datapacks = player.level().getServer().getWorldPath(LevelResource.DATAPACK_DIR);
        Path file = AuthoringFiles.document(datapacks, kind, id.getNamespace(), id.getPath());
        Document current = read(player.level().getServer(), kind, id, Map.of());
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
            validateRuntime(player.level().getServer(), kind, id, body, Map.of());
            String text = text(body);
            requirePermission(player, store);
            AuthoringFiles.save(datapacks, kind, id.getNamespace(), id.getPath(), text);
            encode(result, new Document(body, AuthoringFiles.hash(text), "file/" + AuthoringFiles.PACK_NAME, true));
        }
        changed(store);
        result.addProperty("message", "Saved to antos-authored. Run /reload to apply.");
        return result;
    }

    private static void changed(Store store) {
        store.writeRevision++;
        store.catalogRevision = -1;
        store.pages.clear();
    }

    private static String text(JsonObject body) {
        String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(body) + "\n";
        AuthoringValidation.parse(text);
        return text;
    }

    private static <T> T onServer(Store store, java.util.function.Supplier<T> action) throws IOException {
        try { return store.server.submit(action).get(10, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IOException("Editor operation was cancelled", exception); }
        catch (java.util.concurrent.TimeoutException exception) { throw new IOException("Editor server did not respond; try again", exception); }
        catch (java.util.concurrent.ExecutionException exception) {
            if (exception.getCause() instanceof IllegalArgumentException cause) throw cause;
            throw new IOException(AuthoringChecks.clean(exception.getCause()), exception);
        }
    }

    private static String key(AuthoringKind kind, Identifier id) { return kind.key() + "/" + id; }

    private static JsonObject getMany(MinecraftServer server, JsonObject request) {
        JsonArray documents = new JsonArray();
        int size = 0;
        for (JsonElement element : AuthoringValidation.array(request, "refs", 256)) {
            JsonObject ref = element.getAsJsonObject();
            AuthoringKind kind = AuthoringKind.parse(AuthoringValidation.required(ref, "kind", 24));
            Identifier id = Identifier.parse(AuthoringValidation.required(ref, "id", 240));
            JsonObject row = new JsonObject();
            row.addProperty("kind", kind.key());
            row.addProperty("id", id.toString());
            try {
                Document document = read(server, kind, id, Map.of());
                if (document == null) row.addProperty("missing", true);
                else encode(row, document);
            } catch (IOException | RuntimeException exception) { row.addProperty("error", AuthoringChecks.clean(exception)); }
            int length = row.toString().length();
            if (documents.size() > 0 && size + length > 220_000) break;
            size += length;
            documents.add(row);
        }
        JsonObject result = new JsonObject();
        result.add("docs", documents);
        return result;
    }

    private static JsonObject stage(ServerPlayer player, JsonObject request, Store store) {
        Map<String, Staged> staged = store.staged.computeIfAbsent(player.getUUID(), ignored -> new java.util.LinkedHashMap<>());
        if (request.has("reset") && request.get("reset").getAsBoolean()) staged.clear();
        for (JsonElement element : AuthoringValidation.array(request, "drafts", 256)) {
            JsonObject draft = element.getAsJsonObject();
            AuthoringKind kind = AuthoringKind.parse(AuthoringValidation.required(draft, "kind", 24));
            String raw = AuthoringValidation.required(draft, "id", 240);
            Identifier id = Identifier.tryParse(raw);
            if (id == null) throw new IllegalArgumentException("\"" + raw + "\" is not a valid ID");
            boolean remove = draft.has("remove") && draft.get("remove").getAsBoolean();
            staged.put(key(kind, id), new Staged(kind, id, AuthoringValidation.string(draft, "hash", 64),
                    remove ? null : AuthoringValidation.object(draft, "body").deepCopy()));
            if (staged.size() > AuthoringValidation.MAX_DRAFTS) {
                staged.clear();
                throw new IllegalArgumentException("Save " + AuthoringValidation.MAX_DRAFTS + " changes or fewer at once");
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("staged", staged.size());
        return result;
    }

    private static List<AuthoringIssue> inspect(MinecraftServer server, Staged value, Map<String, Staged> staged) {
        List<AuthoringIssue> issues = new ArrayList<>();
        Document current;
        try {
            AuthoringFiles.document(server.getWorldPath(LevelResource.DATAPACK_DIR), value.kind(), value.id().getNamespace(), value.id().getPath());
            current = read(server, value.kind(), value.id(), Map.of());
        } catch (IOException | RuntimeException exception) {
            issues.add(AuthoringIssue.error("server", "", AuthoringChecks.clean(exception)));
            return issues;
        }
        if (!value.hash().equals(current == null ? "" : current.hash()))
            issues.add(AuthoringIssue.error(value.hash().isEmpty() ? "id_taken" : "changed_elsewhere", "", value.id().toString()));
        if (value.body() == null) {
            if (current == null || !current.authored()) issues.add(AuthoringIssue.error("remove_inherited", ""));
            return issues;
        }
        List<AuthoringIssue> basic = AuthoringChecks.check(value.kind(), value.body());
        issues.addAll(basic);
        if (basic.stream().allMatch(AuthoringIssue::warning)) {
            try { validateRuntime(server, value.kind(), value.id(), value.body(), staged); }
            catch (MissingReference exception) { issues.add(AuthoringIssue.error("missing_reference", exception.field, exception.id)); }
            catch (IOException | RuntimeException exception) { issues.add(AuthoringIssue.error("server", "", AuthoringChecks.clean(exception))); }
        }
        return issues;
    }

    private static JsonObject check(MinecraftServer server, Map<String, Staged> staged) {
        JsonArray rows = new JsonArray();
        boolean clean = true;
        int size = 0;
        for (Staged value : staged.values()) {
            List<AuthoringIssue> issues = inspect(server, value, staged);
            if (issues.isEmpty()) continue;
            clean &= issues.stream().allMatch(AuthoringIssue::warning);
            JsonObject row = new JsonObject();
            row.addProperty("kind", value.kind().key());
            row.addProperty("id", value.id().toString());
            JsonArray encoded = new JsonArray();
            issues.forEach(issue -> encoded.add(issue.encode()));
            row.add("issues", encoded);
            size += row.toString().length();
            if (size <= 220_000) rows.add(row);
            else if (!clean) break;
        }
        JsonObject result = new JsonObject();
        result.addProperty("clean", clean);
        result.add("checked", rows);
        return result;
    }

    private static JsonObject commit(ServerPlayer player, JsonObject request, Store store) throws IOException {
        Map<String, Staged> staged = store.staged.getOrDefault(player.getUUID(), Map.of());
        if (staged.isEmpty()) throw new IllegalArgumentException("There is nothing to save");
        JsonObject result = check(player.level().getServer(), staged);
        if (!result.get("clean").getAsBoolean()) {
            result.addProperty("applied", false);
            return result;
        }
        requirePermission(player, store);
        Path datapacks = player.level().getServer().getWorldPath(LevelResource.DATAPACK_DIR);
        JsonArray saved = new JsonArray();
        String failure = "";
        for (Staged value : staged.values()) {
            try {
                if (value.body() == null) {
                    AuthoringFiles.remove(AuthoringFiles.document(datapacks, value.kind(), value.id().getNamespace(), value.id().getPath()));
                    saved.add("");
                } else {
                    String text = text(value.body());
                    AuthoringFiles.save(datapacks, value.kind(), value.id().getNamespace(), value.id().getPath(), text);
                    saved.add(AuthoringFiles.hash(text));
                }
            } catch (IOException | RuntimeException exception) {
                failure = value.id() + ": " + AuthoringChecks.clean(exception);
                com.craisinlord.antos.AntOS.LOGGER.warn("AntStudio could not save {}", value.id(), exception);
                break;
            }
        }
        changed(store);
        boolean complete = saved.size() == staged.size();
        store.staged.remove(player.getUUID());
        result.add("saved", saved);
        if (result.get("checked").toString().length() + saved.toString().length() > AuthoringWire.MAX_CHARS - 8192) result.add("checked", new JsonArray());
        result.addProperty("written", true);
        result.addProperty("applied", complete);
        if (!complete) result.addProperty("write_failed", failure);
        else if (!request.has("reload") || request.get("reload").getAsBoolean()) result.addProperty("reload", true);
        return result;
    }

    private static void encode(JsonObject target, Document document) {
        target.add("body", document.body().deepCopy());
        target.addProperty("hash", document.hash());
        target.addProperty("source", document.source());
        target.addProperty("authored", document.authored());
    }

    private static Document read(MinecraftServer server, AuthoringKind kind, Identifier id, Map<String, Staged> staged) throws IOException {
        Staged draft = staged.get(key(kind, id));
        if (draft != null && draft.body() != null) return new Document(draft.body(), draft.hash(), "draft", true);
        Path authored = AuthoringFiles.document(server.getWorldPath(LevelResource.DATAPACK_DIR), kind, id.getNamespace(), id.getPath());
        return Files.isRegularFile(authored) ? readAuthored(authored) : readPack(server, kind, id);
    }

    private static Document readAuthored(Path authored) throws IOException {
        if (Files.size(authored) > AuthoringValidation.MAX_DOCUMENT_CHARS * 4L) throw new IOException("Content file exceeds size limit");
        String text = Files.readString(authored);
        return new Document(AuthoringValidation.parse(text), AuthoringFiles.hash(text), "file/" + AuthoringFiles.PACK_NAME, true);
    }

    private static Document readPack(MinecraftServer server, AuthoringKind kind, Identifier id) throws IOException {
        Identifier path = Identifier.fromNamespaceAndPath(id.getNamespace(), kind.directory() + "/" + id.getPath() + ".json");
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

    private static JsonObject tree(MinecraftServer server, int offset, boolean refresh, Store store) throws IOException {
        if (refresh) changed(store);
        long revision = ComputerGuideData.datapackRevision() + store.writeRevision;
        if (store.catalogRevision != revision) {
            store.pages.clear();
            java.util.TreeMap<String, Summary> result = new java.util.TreeMap<>();
            Path data = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(AuthoringFiles.PACK_NAME).resolve("data");
            for (AuthoringKind kind : AuthoringKind.values()) {
                server.getResourceManager().listResources(kind.directory(), path -> kind.includes(path.getPath())).forEach((path, resource) -> {
                    Identifier id = Identifier.fromNamespaceAndPath(path.getNamespace(), path.getPath().substring(kind.directory().length() + 1, path.getPath().length() - 5));
                    addSummary(result, new Summary(kind, id, resource.sourcePackId(), null));
                });
                if (Files.isDirectory(data) && !Files.isSymbolicLink(data)) try (var namespaces = Files.list(data)) {
                    for (Path namespace : namespaces.filter(Files::isDirectory).limit(256).toList()) {
                        Path directory = namespace.resolve(kind.directory());
                        if (!Files.isDirectory(directory) || Files.isSymbolicLink(namespace)) continue;
                        try (var files = Files.walk(directory, 16)) {
                            files.filter(Files::isRegularFile).filter(path -> kind.includes(kind.directory() + "/" + directory.relativize(path).toString().replace('\\', '/'))).limit(4096).forEach(file -> {
                                String relative = directory.relativize(file).toString().replace('\\', '/');
                                Identifier id = Identifier.tryParse(namespace.getFileName() + ":" + relative.substring(0, relative.length() - 5));
                                if (id != null) addSummary(result, new Summary(kind, id, "file/" + AuthoringFiles.PACK_NAME, file));
                            });
                        }
                    }
                }
            }
            store.catalog = List.copyOf(result.values());
            store.catalogRevision = revision;
        }
        String pageKey = Integer.toString(offset);
        JsonObject cached = store.pages.get(pageKey);
        if (cached != null) return cached.deepCopy();
        JsonArray entries = new JsonArray();
        int size = 0;
        int next = -1;
        for (int index = offset; index < store.catalog.size(); index++) {
            Summary summary = store.catalog.get(index);
            JsonObject entry = new JsonObject();
            entry.addProperty("kind", summary.kind().key());
            entry.addProperty("id", summary.id().toString());
            entry.addProperty("source", summary.source());
            try {
                Document document = summary.file() != null && Files.isRegularFile(summary.file()) ? readAuthored(summary.file()) : readPack(server, summary.kind(), summary.id());
                if (document == null) continue;
                entry.addProperty("source", document.source());
                entry.addProperty("authored", document.authored());
                entry.add("body", AuthoringSummary.of(summary.kind(), document.body()));
            } catch (RuntimeException | IOException exception) { entry.addProperty("error", AuthoringChecks.clean(exception)); }
            int length = entry.toString().length();
            if (entries.size() > 0 && size + length > 180_000) { next = index; break; }
            size += length;
            entries.add(entry);
        }
        JsonObject result = new JsonObject();
        result.addProperty("offset", offset);
        result.addProperty("next", next);
        result.addProperty("total", store.catalog.size());
        result.add("entries", entries);
        while (store.pages.size() >= 32) store.pages.remove(store.pages.keySet().iterator().next());
        store.pages.put(pageKey, result.deepCopy());
        return result;
    }

    private static void addSummary(java.util.TreeMap<String, Summary> summaries, Summary summary) {
        if (summary.id().getNamespace().equals("antos") && summary.id().getPath().startsWith("field_manual")) return;
        String key = summary.kind().key() + "\u0000" + summary.id();
        if (summaries.containsKey(key)) {
            if (summary.file() != null) summaries.put(key, summary);
            return;
        }
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

    private static final class MissingReference extends IllegalArgumentException {
        private final String field;
        private final String id;

        MissingReference(String field, String id) {
            super("Missing reference " + id);
            this.field = field;
            this.id = id;
        }
    }

    private static void validateRuntime(MinecraftServer server, AuthoringKind kind, Identifier id, JsonObject body, Map<String, Staged> staged) throws IOException {
        if (kind == AuthoringKind.TASK) ComputerGuideData.validateAuthoringTask(id, body);
        if (kind == AuthoringKind.CATEGORY || kind == AuthoringKind.GROUP || kind == AuthoringKind.REWARD_POOL)
            ComputerGuideData.validateAuthoringCatalog(kind.key(), id, body);
        if (kind == AuthoringKind.MESSAGE) com.craisinlord.antos.content.antmail.AntmailEventData.validateAuthoring(id, body);
        if (kind == AuthoringKind.PRODUCT) com.craisinlord.antos.content.antazon.AntazonData.validateAuthoring(id, body);
        if (kind == AuthoringKind.DEAL_CAMPAIGN) com.craisinlord.antos.content.antazon.AntazonDealData.validateAuthoring(id, body);
        if (kind == AuthoringKind.ARCHIVE_ENTRY) {
            String folder = AuthoringChecks.string(body, "folder").trim();
            if (!folder.isEmpty() && folder(server, folder, staged) == null) throw new MissingReference("folder", folder);
            ComputerGuideData.validateAuthoringArchiveEntry(id, body);
        }
        if (kind == AuthoringKind.ARCHIVE_FOLDER) {
            String parent = AuthoringChecks.string(body, "parent").trim();
            if (parent.equals(id.toString())) throw new IllegalArgumentException("An Archive folder cannot contain itself");
            if (!parent.isEmpty()) {
                Document owner = folder(server, parent, staged);
                if (owner == null) throw new MissingReference("parent", parent);
                if (!AuthoringChecks.string(owner.body(), "parent").isBlank()) throw new IllegalArgumentException("Folders can only be one level deep. Choose a top-level folder.");
            }
            ComputerGuideData.validateAuthoringArchiveFolder(id, body);
        }
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
                Identifier idValue = Identifier.parse(raw);
                if (key.equals("item") && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(idValue))
                    throw new IllegalArgumentException("Unknown sell item " + idValue);
            }
            for (String key : List.of("items", "tags", "unlock_tasks", "unlock_disks", "unlock_commands")) if (body.has(key)) {
                for (JsonElement element : AuthoringValidation.array(body, key, 1024)) {
                    String raw = element.getAsString().replaceFirst("^#", "");
                    Identifier.parse(raw);
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
                else Identifier.parse(item.get("explorer_map").getAsString().replaceFirst("^#", ""));
            }
        }
        if (kind == AuthoringKind.TASK || kind == AuthoringKind.REWARD_POOL)
            validateGraph(server, kind, id, id, body, staged, new HashSet<>(), new HashSet<>(), 0);
    }

    private static Document folder(MinecraftServer server, String id, Map<String, Staged> staged) throws IOException {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? null : read(server, AuthoringKind.ARCHIVE_FOLDER, parsed, staged);
    }

    private static void validateGraph(MinecraftServer server, AuthoringKind kind, Identifier root, Identifier current,
                                      JsonObject replacement, Map<String, Staged> staged, Set<Identifier> pending, Set<Identifier> complete, int depth) throws IOException {
        if (complete.contains(current)) return;
        if (depth > (kind == AuthoringKind.TASK ? 256 : 7)) throw new IllegalArgumentException("Dependency nesting exceeds the supported limit at " + current);
        if (!pending.add(current)) throw new IllegalArgumentException("These depend on each other in a loop: " + current);
        Document existing = current.equals(root) ? null : read(server, kind, current, staged);
        JsonObject body = current.equals(root) ? replacement : existing == null ? null : existing.body();
        if (body == null) throw new MissingReference(kind == AuthoringKind.TASK ? "requires" : "entries", current.toString());
        List<Identifier> dependencies = new ArrayList<>();
        if (kind == AuthoringKind.TASK) {
            for (String key : List.of("requires", "requires_any")) if (body.has(key))
                for (JsonElement value : body.getAsJsonArray(key)) dependencies.add(Identifier.parse(value.getAsString()));
        } else for (JsonElement value : body.getAsJsonArray("entries")) {
            JsonObject entry = value.getAsJsonObject();
            if (entry.has("pool")) dependencies.add(Identifier.parse(entry.get("pool").getAsString()));
            else {
                Identifier item = Identifier.parse(AuthoringValidation.required(entry, "item", 240));
                if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item)) throw new IllegalArgumentException("Unknown reward item " + item);
            }
        }
        for (Identifier dependency : dependencies) validateGraph(server, kind, root, dependency, replacement, staged, pending, complete, depth + 1);
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
