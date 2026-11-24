package com.craisinlord.antos.content.client;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.editor.AuthoringValidation;
import com.craisinlord.antos.content.editor.AuthoringWire;
import com.craisinlord.antos.content.network.AuthoringRequestPayload;
import com.craisinlord.antos.content.network.AuthoringResultPayload;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class AuthoringClientState {
    private static Consumer<AuthoringRequestPayload> sender = payload -> { };
    private static final Map<String, Pending> pending = new HashMap<>();
    private static final Map<String, AuthoringWire.Assembly> transfers = new HashMap<>();
    private static final ArrayDeque<AuthoringRequestPayload> outgoing = new ArrayDeque<>();
    private static boolean available;
    private static boolean statusKnown;
    private static boolean editing;
    private static long lastStatus;
    private static String error = "";
    private static int revision;
    private static long reloadRevision;
    private static final Runnable smokeDriver = loadSmokeDriver();
    private record Pending(long started, String operation, Consumer<JsonObject> callback) { }
    private AuthoringClientState() { }

    public static void setSender(Consumer<AuthoringRequestPayload> value) { sender = value; }
    public static boolean enabled() { return AntOSSettings.contentEditingControls() && operator(); }
    public static boolean available() { return enabled() && available; }
    public static boolean blockedByServer() { return enabled() && statusKnown && !available; }
    public static boolean editing() { return available() && editing; }
    public static int revision() { return revision; }
    public static long reloadRevision() { return reloadRevision; }
    public static String error() { return error; }
    public static boolean busy() { return !pending.isEmpty(); }
    public static boolean saving() { return pending.values().stream().anyMatch(value -> value.operation().equals("save") || value.operation().equals("remove")); }

    public static boolean setEnabled(boolean value) {
        if (!operator() || !AntOSSettings.setContentEditingControls(value)) return false;
        if (value) lastStatus = 0;
        else if (editing) request("leave", new JsonObject(), response -> { });
        revision++;
        return true;
    }

    public static boolean operator() {
        var player = Minecraft.getInstance().player;
        return player != null && player.hasPermissions(2);
    }

    public static void tick() {
        if (smokeDriver != null) smokeDriver.run();
        long now = System.currentTimeMillis();
        if (!operator()) {
            if (available || editing || !pending.isEmpty() || !outgoing.isEmpty()) clear();
            return;
        }
        if (Minecraft.getInstance().screen instanceof com.craisinlord.antos.content.client.screen.ComputerScreen && AntOSSettings.contentEditingControls() && now - lastStatus >= 5000 && pending.isEmpty()) {
            lastStatus = now;
            request("status", new JsonObject(), response -> { });
        }
        for (int index = 0; index < 4 && !outgoing.isEmpty(); index++) sender.accept(outgoing.removeFirst());
        if (pending.values().stream().anyMatch(value -> now - value.started() > 15_000)) {
            JsonObject timeout = new JsonObject();
            timeout.addProperty("ok", false);
            timeout.addProperty("error", "Editor request timed out. Your draft is retained; refresh and try again.");
            for (Pending request : pending.values()) request.callback().accept(timeout);
            pending.clear();
            transfers.clear();
            outgoing.clear();
            error = "Editor request timed out. Your draft is retained; refresh and try again.";
            revision++;
        }
    }

    private static Runnable loadSmokeDriver() {
        if (System.getenv("ANTOS_EDITOR_SMOKE") == null) return null;
        try {
            var method = Class.forName("com.craisinlord.antos.content.client.screen.AuthoringSmokeDriver").getMethod("tick");
            return () -> {
                try { method.invoke(null); }
                catch (ReflectiveOperationException exception) { throw new IllegalStateException("Authoring smoke driver failed", exception); }
            };
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Authoring smoke driver is unavailable", exception); }
    }

    public static void request(String operation, JsonObject values, Consumer<JsonObject> callback) {
        if (!operator()) return;
        if (pending.size() >= 4) { error = "Editor is busy; wait for the current request"; return; }
        JsonObject request = values.deepCopy();
        request.addProperty("operation", operation);
        String id = UUID.randomUUID().toString();
        try {
            for (AuthoringWire.Chunk chunk : AuthoringWire.split(id, request.toString()))
                outgoing.add(new AuthoringRequestPayload(chunk.id(), chunk.index(), chunk.total(), chunk.data()));
            pending.put(id, new Pending(System.currentTimeMillis(), operation, callback));
            if (!operation.equals("status")) error = "";
        } catch (IllegalArgumentException exception) { error = exception.getMessage(); }
    }

    public static void receive(AuthoringResultPayload payload) {
        try {
            AuthoringWire.Chunk chunk = payload.chunk();
            long now = System.currentTimeMillis();
            if (chunk.index() == 0) {
                transfers.values().removeIf(value -> value.expired(now));
                if (transfers.size() >= 8) return;
                transfers.put(chunk.id(), new AuthoringWire.Assembly(chunk, now));
            }
            AuthoringWire.Assembly assembly = transfers.get(chunk.id());
            if (assembly == null) return;
            String data = assembly.accept(chunk, now);
            if (data == null) return;
            transfers.remove(chunk.id());
            JsonObject response = AuthoringValidation.envelope(data);
            Pending request = pending.remove(chunk.id());
            if (response.has("reload_revision")) reloadRevision = response.get("reload_revision").getAsLong();
            if (response.has("available")) {
                statusKnown = true;
                available = response.get("available").getAsBoolean();
                editing = response.get("editing").getAsBoolean();
            }
            if (!response.has("ok") || !response.get("ok").getAsBoolean())
                error = response.has("error") ? response.get("error").getAsString() : "Editor request failed";
            if (request != null) request.callback().accept(response);
            revision++;
        } catch (RuntimeException exception) {
            transfers.remove(payload.id());
            pending.remove(payload.id());
            error = "Invalid editor response";
            revision++;
        }
    }

    public static void clear() {
        JsonObject cancelled = new JsonObject();
        cancelled.addProperty("ok", false);
        cancelled.addProperty("error", "Operator permission changed; editor request cancelled.");
        for (Pending request : pending.values()) request.callback().accept(cancelled);
        pending.clear();
        transfers.clear();
        outgoing.clear();
        available = false;
        statusKnown = false;
        editing = false;
        lastStatus = 0;
        reloadRevision = 0;
        error = "";
        revision++;
    }
}
