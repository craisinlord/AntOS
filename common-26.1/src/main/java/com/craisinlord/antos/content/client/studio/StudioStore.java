package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.AuthoringClientState;
import com.craisinlord.antos.content.client.ComputerWorkspaceClientKey;
import com.craisinlord.antos.content.editor.AuthoringIssue;
import com.craisinlord.antos.content.editor.AuthoringKind;
import com.craisinlord.antos.content.editor.AuthoringLinks;
import com.craisinlord.antos.content.editor.AuthoringValidation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;

final class StudioStore {
    private static final Map<String, StudioStore> STORES = new HashMap<>();

    static final class Row {
        final StudioKey key;
        final JsonObject body;
        final String source;
        final boolean authored;
        final String error;

        Row(StudioKey key, JsonObject body, String source, boolean authored, String error) {
            this.key = key;
            this.body = body;
            this.source = source;
            this.authored = authored;
            this.error = error;
        }
    }

    static final class Doc {
        final StudioKey key;
        JsonObject base;
        JsonObject body;
        String hash = "";
        String source = "";
        boolean authored;
        boolean removed;
        boolean idEdited;
        boolean conflict;
        long revision;
        List<AuthoringIssue> serverIssues = List.of();
        private long checkedRevision = -1;
        private long checkedStore = -1;
        private List<StudioProblems.Problem> problems = List.of();

        Doc(StudioKey key, JsonObject body) {
            this.key = key;
            this.body = body;
        }

        boolean fresh() { return base == null && hash.isEmpty(); }
        boolean unresolved() { return base == null && !hash.isEmpty(); }
        boolean dirty() { return removed || base == null || !body.equals(base); }
    }

    private record State(JsonObject body, boolean removed, boolean present) { }
    private record Edit(State before, State after) { }
    private static final class Change {
        final Map<StudioKey, Edit> edits = new LinkedHashMap<>();
        String tag = "";
        long at;
    }
    private record Request(String operation, JsonObject values, Consumer<JsonObject> callback) { }

    private final String place;
    String identity;
    final StudioRefs refs = new StudioRefs(this);
    private final Map<StudioKey, Row> rows = new LinkedHashMap<>();
    private final Map<StudioKey, Doc> docs = new LinkedHashMap<>();
    private final Set<StudioKey> requested = new HashSet<>();
    private final Set<StudioKey> missing = new HashSet<>();
    private final List<Runnable> waiting = new ArrayList<>();
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private final ArrayDeque<Change> undo = new ArrayDeque<>();
    private final ArrayDeque<Change> redo = new ArrayDeque<>();
    private final Map<AuthoringKind, List<StudioKey>> sorted = new HashMap<>();
    private Change transaction;
    private int transactionDepth;
    private boolean inFlight;
    private final Map<StudioKey, StudioKey> aliases = new HashMap<>();
    private StudioKey pinned;
    private boolean entered;
    private long generation;
    private boolean loading;
    private boolean loaded;
    private boolean saving;
    private boolean committing;
    private boolean leaving;
    private long commitAt;
    private boolean unreadableDrafts;
    private boolean restored;
    private boolean savePending;
    private long changedAt;
    private long savedAt;
    private long revision;
    private long sortedRevision = -1;
    private long reloadRevision = -1;
    private List<Row> incoming = new ArrayList<>();
    private String error = "";
    private String status = "";
    private long statusAt;
    private boolean statusGood;
    private Map<StudioKey, Set<StudioKey>> disksOf = Map.of();
    private Map<StudioKey, Map<AuthoringKind, List<StudioKey>>> diskContents = Map.of();
    private long membershipRevision = -1;
    private long problemRevision = -1;
    private List<StudioProblems.Problem> problems = List.of();

    private StudioStore(String place) {
        this.place = place;
        identity = place;
    }

    static StudioStore current() {
        Minecraft minecraft = Minecraft.getInstance();
        String world = "server";
        var server = minecraft.getSingleplayerServer();
        if (server != null) world = "local/" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        else if (minecraft.getCurrentServer() != null) world = "server/" + minecraft.getCurrentServer().ip;
        String player = minecraft.player == null ? minecraft.getUser().getName() : minecraft.player.getUUID().toString();
        return STORES.computeIfAbsent(world + "|" + player, StudioStore::new);
    }

    static void leaveWorld() {
        STORES.values().forEach(StudioStore::checkpoint);
        STORES.clear();
    }

    static void background() {
        for (StudioStore store : List.copyOf(STORES.values())) if (store.leaving) store.pump();
    }

    long revision() { return revision; }
    boolean ready() { return loaded; }
    boolean loading() { return loading || !loaded; }
    boolean saving() { return saving; }
    boolean busy() { return inFlight || !queue.isEmpty(); }
    String error() { return error; }
    String status() { return System.currentTimeMillis() - statusAt < 6000 ? status : ""; }
    boolean statusGood() { return statusGood; }
    long savedAgo() { return savedAt == 0 ? -1 : (System.currentTimeMillis() - savedAt) / 1000; }

    void say(String text, boolean good) {
        status = text == null ? "" : text;
        statusGood = good;
        statusAt = System.currentTimeMillis();
    }

    void open() {
        entered = false;
        error = "";
        leaving = false;
        if (!inFlight && !committing) saving = false;
        send("enter", new JsonObject(), response -> {
            if (ok(response)) {
                entered = true;
                belongTo(StudioJson.string(response, "world"));
                refresh(false);
            } else error = StudioText.t("error.enter", message(response));
        });
    }

    private void belongTo(String world) {
        String next = world.isEmpty() ? place : place + "#" + world;
        if (next.equals(identity)) return;
        if (restored) {
            checkpoint();
            rows.clear();
            docs.clear();
            undo.clear();
            redo.clear();
            aliases.clear();
            requested.clear();
            missing.clear();
            waiting.clear();
            refs.forget();
            pinned = null;
            restored = false;
            loaded = false;
            loading = false;
            unreadableDrafts = false;
            changed();
        }
        identity = next;
        String old = AntOSSettings.editorDraft("studio:" + place, "drafts");
        if (!identity.equals(place) && old != null && AntOSSettings.editorDraft("studio:" + identity, "drafts") == null) {
            AntOSSettings.setEditorDraft("studio:" + identity, "drafts", old);
            AntOSSettings.setEditorDraft("studio:" + place, "drafts", null);
        }
    }

    void close() {
        checkpoint();
        if (!queue.isEmpty()) {
            if (committing) queue.removeIf(request -> !request.operation().equals("stage") && !request.operation().equals("commit"));
            else {
                queue.clear();
                saving = false;
            }
            requested.removeIf(key -> !docs.containsKey(key));
            refs.forget();
        }
        generation++;
        entered = false;
        loading = false;
        leaving = committing;
        if (leaving) AntStudioScreen.tell(StudioText.t("saving_background"));
        else leave();
    }

    private static void leave() { AuthoringClientState.request("leave", new JsonObject(), response -> { }); }

    private void settled(boolean success, Consumer<Boolean> done) {
        saving = false;
        committing = false;
        if (leaving) {
            leaving = false;
            AntStudioScreen.tell(StudioText.t("finished_background", statusAt >= commitAt && !status.isEmpty() ? status : StudioText.t("error.not_saved")));
            leave();
        } else done.accept(success);
    }

    static boolean ok(JsonObject response) { return response.has("ok") && response.get("ok").getAsBoolean(); }
    static String message(JsonObject response) { return StudioJson.string(response, "error", StudioText.t("error.unknown")); }

    void send(String operation, JsonObject values, Consumer<JsonObject> callback) {
        queue.add(new Request(operation, values, callback));
        pump();
    }

    private void pump() {
        if (inFlight || queue.isEmpty() || AuthoringClientState.busy()) return;
        Request request = queue.removeFirst();
        inFlight = true;
        AuthoringClientState.request(request.operation(), request.values(), response -> {
            inFlight = false;
            if (ok(response)) error = "";
            try { request.callback().accept(response); }
            catch (RuntimeException exception) {
                com.craisinlord.antos.AntOS.LOGGER.warn("AntStudio could not use a {} response", request.operation(), exception);
                say(StudioText.t("error.unknown"), false);
            }
        });
    }

    void tick() {
        pump();
        long now = System.currentTimeMillis();
        if (savePending && now - changedAt > 1500) checkpoint();
        if (entered && loaded && !loading && !saving && reloadRevision >= 0 && AuthoringClientState.reloadRevision() != reloadRevision) refresh(false);
    }

    void refresh(boolean force) {
        if (loading) return;
        loading = true;
        incoming = new ArrayList<>();
        reloadRevision = AuthoringClientState.reloadRevision();
        generation++;
        page(0, force, generation);
    }

    private void page(int offset, boolean force, long wanted) {
        JsonObject request = new JsonObject();
        request.addProperty("offset", offset);
        request.addProperty("refresh", force);
        send("tree", request, response -> {
            if (wanted != generation) return;
            if (!ok(response)) {
                loading = false;
                error = StudioText.t("error.list", message(response));
                return;
            }
            for (JsonElement element : response.getAsJsonArray("entries")) {
                JsonObject entry = element.getAsJsonObject();
                incoming.add(new Row(StudioKey.decode(entry), entry.has("body") ? entry.getAsJsonObject("body") : new JsonObject(),
                        StudioJson.string(entry, "source"), StudioJson.bool(entry, "authored", false), StudioJson.string(entry, "error")));
            }
            int next = response.get("next").getAsInt();
            if (next > 0) { page(next, false, wanted); return; }
            rows.clear();
            for (Row row : incoming) rows.put(row.key, row);
            incoming = new ArrayList<>();
            loading = false;
            loaded = true;
            missing.clear();
            refs.forget();
            changed();
            if (!restored) {
                restored = true;
                restore();
            } else recheck();
        });
    }

    private void recheck() {
        Set<StudioKey> kept = new HashSet<>();
        if (pinned != null) kept.add(pinned);
        undo.forEach(change -> kept.addAll(change.edits.keySet()));
        redo.forEach(change -> kept.addAll(change.edits.keySet()));
        List<StudioKey> compared = new ArrayList<>();
        List<StudioKey> dropped = new ArrayList<>();
        for (Doc doc : docs.values()) {
            if (doc.fresh() && doc.hash.isEmpty()) continue;
            (doc.dirty() || kept.contains(doc.key) ? compared : dropped).add(doc.key);
        }
        dropped.forEach(key -> {
            docs.remove(key);
            requested.remove(key);
        });
        changed();
        compare(compared);
    }

    void pin(StudioKey key) { pinned = key; }

    StudioKey canonical(StudioKey key) {
        StudioKey result = key;
        for (int hops = 0; result != null && aliases.containsKey(result) && hops < 8; hops++) result = aliases.get(result);
        return result;
    }

    private void compare(List<StudioKey> drafts) {
        if (drafts.isEmpty()) return;
        fetch(drafts, values -> values.forEach((key, value) -> {
            Doc doc = docs.get(key);
            if (doc == null) return;
            boolean clean = doc.base != null && !doc.dirty();
            if (value.has("missing")) {
                if (clean) {
                    docs.remove(key);
                    requested.remove(key);
                    missing.add(key);
                } else if (!doc.hash.isEmpty() || doc.base != null) doc.conflict = !doc.removed;
                changed();
                return;
            }
            if (!value.has("hash")) return;
            if (clean) {
                boolean same = doc.body.equals(value.getAsJsonObject("body"));
                doc.base = value.getAsJsonObject("body");
                doc.hash = value.get("hash").getAsString();
                doc.source = value.get("source").getAsString();
                doc.authored = value.get("authored").getAsBoolean();
                if (!same) {
                    doc.body = doc.base.deepCopy();
                    doc.revision++;
                    undo.removeIf(change -> change.edits.containsKey(key));
                    redo.removeIf(change -> change.edits.containsKey(key));
                }
                changed();
                return;
            }
            String hash = value.get("hash").getAsString();
            if (doc.hash.equals(hash)) {
                doc.base = value.getAsJsonObject("body");
                doc.authored = value.get("authored").getAsBoolean();
                doc.source = value.get("source").getAsString();
                doc.conflict = false;
            } else if (doc.body.equals(value.getAsJsonObject("body"))) {
                doc.base = value.getAsJsonObject("body");
                doc.hash = hash;
                doc.conflict = false;
                keepLinkedIds(List.of(doc.base));
            } else {
                if (doc.base == null) doc.base = value.getAsJsonObject("body");
                doc.conflict = true;
            }
            changed();
        }));
    }

    void keepMine(StudioKey key) { resolve(key, false); }
    void useTheirs(StudioKey key) { resolve(key, true); }

    private void resolve(StudioKey key, boolean theirs) {
        fetch(List.of(key), values -> {
            Doc doc = docs.get(key);
            JsonObject value = values.get(key);
            if (doc == null || value == null) return;
            if (value.has("missing")) {
                doc.base = null;
                doc.hash = "";
                doc.conflict = false;
                if (theirs) docs.remove(key);
            } else if (value.has("body")) {
                doc.base = value.getAsJsonObject("body");
                doc.hash = value.get("hash").getAsString();
                doc.source = value.get("source").getAsString();
                doc.authored = value.get("authored").getAsBoolean();
                doc.conflict = false;
                if (theirs) {
                    doc.body = doc.base.deepCopy();
                    doc.removed = false;
                }
            }
            doc.serverIssues = List.of();
            doc.revision++;
            edited();
        });
    }

    private void fetch(List<StudioKey> keys, Consumer<Map<StudioKey, JsonObject>> callback) {
        Map<StudioKey, JsonObject> results = new LinkedHashMap<>();
        fetch(new ArrayList<>(keys), results, callback);
    }

    private void fetch(List<StudioKey> remaining, Map<StudioKey, JsonObject> results, Consumer<Map<StudioKey, JsonObject>> callback) {
        if (remaining.isEmpty()) { callback.accept(results); return; }
        JsonArray refs = new JsonArray();
        for (int index = 0; index < Math.min(200, remaining.size()); index++) refs.add(remaining.get(index).encode());
        JsonObject request = new JsonObject();
        request.add("refs", refs);
        send("get_many", request, response -> {
            if (!ok(response)) {
                say(message(response), false);
                callback.accept(results);
                return;
            }
            int received = 0;
            for (JsonElement element : response.getAsJsonArray("docs")) {
                JsonObject value = element.getAsJsonObject();
                StudioKey key = StudioKey.decode(value);
                results.put(key, value);
                remaining.remove(key);
                received++;
            }
            if (received == 0) callback.accept(results);
            else fetch(remaining, results, callback);
        });
    }

    boolean exists(StudioKey key) {
        Doc doc = docs.get(key);
        return doc != null ? !doc.removed : rows.containsKey(key);
    }

    boolean known(AuthoringKind kind, String id) { return exists(new StudioKey(kind, id)); }
    Row row(StudioKey key) { return rows.get(key); }
    Doc loaded(StudioKey key) { return docs.get(key); }
    boolean missing(StudioKey key) { return missing.contains(key); }

    JsonObject view(StudioKey key) {
        Doc doc = docs.get(key);
        if (doc != null) return doc.body;
        Row row = rows.get(key);
        return row == null ? null : row.body;
    }

    String label(StudioKey key) { return StudioText.label(key, view(key)); }
    String label(AuthoringKind kind, String id) { return label(new StudioKey(kind, id)); }

    Doc doc(StudioKey key) {
        if (key == null) return null;
        Doc doc = docs.get(key);
        if (doc == null && rows.containsKey(key) && !missing.contains(key)) load(List.of(key));
        return doc;
    }

    void load(Collection<StudioKey> keys) {
        List<StudioKey> wanted = new ArrayList<>();
        for (StudioKey key : keys) if (!docs.containsKey(key) && rows.containsKey(key) && requested.add(key)) wanted.add(key);
        if (wanted.isEmpty()) return;
        fetch(wanted, values -> {
            for (StudioKey key : wanted) {
                JsonObject value = values.get(key);
                if (value == null || !value.has("body")) {
                    missing.add(key);
                    requested.remove(key);
                    continue;
                }
                if (docs.containsKey(key)) continue;
                Doc doc = new Doc(key, value.getAsJsonObject("body").deepCopy());
                doc.base = value.getAsJsonObject("body");
                doc.hash = value.get("hash").getAsString();
                doc.source = value.get("source").getAsString();
                doc.authored = value.get("authored").getAsBoolean();
                docs.put(key, doc);
            }
            changed();
            List<Runnable> ready = new ArrayList<>(waiting);
            waiting.clear();
            ready.forEach(Runnable::run);
        });
    }

    void with(Collection<StudioKey> keys, Runnable action) {
        List<StudioKey> needed = keys.stream().filter(Objects::nonNull).filter(key -> !docs.containsKey(key) && rows.containsKey(key) && !missing.contains(key)).toList();
        if (needed.isEmpty()) { action.run(); return; }
        waiting.add(() -> with(keys, action));
        load(needed);
    }

    List<StudioKey> keys(AuthoringKind kind) {
        if (sortedRevision != revision) {
            sorted.clear();
            sortedRevision = revision;
        }
        return sorted.computeIfAbsent(kind, ignored -> {
            Set<StudioKey> result = new LinkedHashSet<>();
            for (StudioKey key : rows.keySet()) if (key.kind() == kind && exists(key)) result.add(key);
            for (StudioKey key : docs.keySet()) if (key.kind() == kind && exists(key)) result.add(key);
            List<StudioKey> list = new ArrayList<>(result);
            Map<StudioKey, String> labels = new HashMap<>();
            for (StudioKey key : list) labels.put(key, label(key).toLowerCase(java.util.Locale.ROOT));
            list.sort(Comparator.<StudioKey>comparingInt(key -> kind == AuthoringKind.CATEGORY || kind == AuthoringKind.GROUP || kind == AuthoringKind.ARCHIVE_FOLDER
                    ? StudioJson.integer(view(key), "sort_order", 0) : 0).thenComparing(labels::get).thenComparing(StudioKey::id));
            return List.copyOf(list);
        });
    }

    List<Doc> drafts() { return docs.values().stream().filter(Doc::dirty).toList(); }
    int draftCount() { return (int) docs.values().stream().filter(Doc::dirty).count(); }
    boolean dirty(StudioKey key) {
        Doc doc = docs.get(key);
        return doc != null && doc.dirty();
    }

    List<StudioProblems.Problem> problems(StudioKey key) {
        Doc doc = docs.get(key);
        if (doc == null || !doc.dirty() || doc.removed && doc.serverIssues.isEmpty() && !doc.conflict) return List.of();
        if (doc.checkedRevision != doc.revision || doc.checkedStore != revision) {
            doc.problems = StudioProblems.check(this, doc);
            doc.checkedRevision = doc.revision;
            doc.checkedStore = revision;
        }
        return doc.problems;
    }

    List<StudioProblems.Problem> problems() {
        if (problemRevision != revision) {
            List<StudioProblems.Problem> result = new ArrayList<>();
            for (Doc doc : drafts()) result.addAll(problems(doc.key));
            problems = List.copyOf(result);
            problemRevision = revision;
        }
        return problems;
    }

    boolean broken(StudioKey key) { return problems(key).stream().anyMatch(problem -> !problem.issue().warning()); }
    long errorCount() { return problems().stream().filter(problem -> !problem.issue().warning()).count(); }

    private void changed() { revision++; }

    private void edited() {
        revision++;
        changedAt = System.currentTimeMillis();
        savePending = true;
    }

    private static State state(Doc doc) { return doc == null ? new State(null, false, false) : new State(doc.body, doc.removed, true); }

    void transaction(Runnable action) {
        boolean outer = transactionDepth++ == 0;
        if (outer) transaction = new Change();
        try { action.run(); }
        finally {
            if (--transactionDepth == 0) {
                Change finished = transaction;
                transaction = null;
                if (!finished.edits.isEmpty()) push(finished);
            }
        }
    }

    private void push(Change change) {
        undo.addLast(change);
        while (undo.size() > 200) undo.removeFirst();
        redo.clear();
    }

    private void record(StudioKey key, State before, State after, String tag) {
        long now = System.currentTimeMillis();
        if (transaction != null) {
            Edit existing = transaction.edits.get(key);
            transaction.edits.put(key, new Edit(existing == null ? before : existing.before(), after));
            return;
        }
        Change last = undo.peekLast();
        if (last != null && !tag.isEmpty() && last.tag.equals(tag) && now - last.at < 1200 && last.edits.size() == 1 && last.edits.containsKey(key)) {
            last.edits.put(key, new Edit(last.edits.get(key).before(), after));
            last.at = now;
            redo.clear();
            return;
        }
        Change change = new Change();
        change.tag = tag;
        change.at = now;
        change.edits.put(key, new Edit(before, after));
        push(change);
    }

    void edit(StudioKey key, String tag, Consumer<JsonObject> action) {
        Doc doc = docs.get(key);
        if (doc == null) return;
        JsonObject next = doc.body.deepCopy();
        action.accept(next);
        if (next.equals(doc.body)) return;
        State before = state(doc);
        doc.body = next;
        doc.removed = false;
        doc.revision++;
        doc.serverIssues = List.of();
        record(key, before, state(doc), tag.isEmpty() ? "" : key + tag);
        edited();
    }

    StudioKey create(AuthoringKind kind, String title, String namespace, JsonObject body) {
        StudioKey key = new StudioKey(kind, freeId(kind, namespace, title.isBlank() ? kind.key() : title));
        Doc doc = new Doc(key, body);
        doc.source = "new";
        docs.put(key, doc);
        aliases.remove(key);
        missing.remove(key);
        record(key, new State(null, false, false), state(doc), "");
        edited();
        return key;
    }

    String freeId(AuthoringKind kind, String namespace, String title) {
        String slug = StudioText.slug(title);
        if (slug.isEmpty()) slug = kind.key();
        if (kind == AuthoringKind.TASK && Set.of("category", "group", "reward_pool").contains(slug)) slug += "_task";
        String base = (namespace.isBlank() ? namespace() : namespace) + ":" + slug;
        String id = base;
        for (int number = 2; taken(new StudioKey(kind, id)); number++) id = base + "_" + number;
        return id;
    }

    private boolean taken(StudioKey key) { return rows.containsKey(key) || docs.containsKey(key); }

    String namespace() {
        Map<String, Integer> counts = new HashMap<>();
        for (Row row : rows.values()) if (row.authored) counts.merge(row.key.namespace(), 1, Integer::sum);
        for (Doc doc : docs.values()) if (doc.fresh()) counts.merge(doc.key.namespace(), 1, Integer::sum);
        return counts.entrySet().stream().max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey())).map(Map.Entry::getKey).orElse("pack");
    }

    StudioKey duplicate(StudioKey key) {
        Doc doc = docs.get(key);
        if (doc == null) return null;
        JsonObject body = doc.body.deepCopy();
        String field = StudioText.titleKey(key.kind());
        String title = StudioText.label(key, body);
        if (!field.isEmpty() && key.kind() != AuthoringKind.TERMINAL_COMMAND) {
            title = StudioText.t("copy_of", title);
            body.addProperty(field, StudioText.write(key.kind(), field, StudioJson.string(body, field), title));
        } else if (key.kind() == AuthoringKind.TERMINAL_COMMAND) body.addProperty(field, StudioJson.string(body, field) + "2");
        if (key.kind() == AuthoringKind.TASK) {
            JsonObject position = StudioJson.object(body, "position");
            if (position.has("x")) {
                position = position.deepCopy();
                position.addProperty("x", Math.min(128, position.get("x").getAsInt() + 1));
                position.addProperty("y", Math.min(128, StudioJson.integer(position, "y", 0) + 1));
                body.add("position", position);
            }
        }
        String copied = title;
        return create(key.kind(), copied, key.namespace().equals("antos") ? namespace() : key.namespace(), body);
    }

    boolean rename(StudioKey key, String id) {
        Doc doc = docs.get(key);
        StudioKey next = new StudioKey(key.kind(), id);
        if (doc == null || saving || !doc.fresh() || !doc.hash.isEmpty() || key.equals(next)) return false;
        if (Identifier.tryParse(id) == null || !id.contains(":") || taken(next)) return false;
        Doc moved = new Doc(next, doc.body);
        moved.source = doc.source;
        moved.idEdited = doc.idEdited;
        moved.revision = doc.revision + 1;
        LinkedHashMap<StudioKey, Doc> copy = new LinkedHashMap<>();
        docs.forEach((existing, value) -> copy.put(existing.equals(key) ? next : existing, existing.equals(key) ? moved : value));
        docs.clear();
        docs.putAll(copy);
        List<StudioKey> unloaded = new ArrayList<>();
        for (Row row : rows.values()) if (row.authored && !docs.containsKey(row.key) && linked(row.key, row.body, key)) unloaded.add(row.key);
        for (Doc other : docs.values()) if (other.dirty() || other.authored) relink(other, key, id);
        if (!unloaded.isEmpty()) with(unloaded, () -> {
            for (StudioKey holder : unloaded) if (docs.containsKey(holder)) relink(docs.get(holder), key, id);
            edited();
        });
        undo.clear();
        redo.clear();
        aliases.remove(next);
        aliases.put(key, next);
        edited();
        return true;
    }

    private static boolean linked(StudioKey owner, JsonObject body, StudioKey target) {
        return AuthoringLinks.links(owner.kind(), owner.namespace(), body, target.kind(), target.id());
    }

    private void relink(Doc doc, StudioKey key, String id) {
        if (!linked(doc.key, doc.body, key)) return;
        Set<AuthoringKind> others = new HashSet<>();
        for (AuthoringKind kind : AuthoringKind.values()) if (kind != key.kind() && known(kind, key.id())) others.add(kind);
        doc.body = AuthoringLinks.relink(doc.key.kind(), doc.key.namespace(), doc.body, key.kind(), key.id(), id, others);
        doc.revision++;
    }

    private boolean held(StudioKey key) {
        for (Doc other : docs.values()) {
            if (other.key.equals(key)) continue;
            if (AuthoringLinks.loose(other.key.kind(), other.key.namespace(), other.body, key.id())) return true;
            JsonObject saved = other.unresolved() ? other.body : other.base;
            if (saved != null && linked(other.key, saved, key)) return true;
        }
        for (Row row : rows.values()) if (!docs.containsKey(row.key)
                && (linked(row.key, row.body, key) || AuthoringLinks.loose(row.key.kind(), row.key.namespace(), row.body, key.id()))) return true;
        return false;
    }

    private void keepLinkedIds(Collection<JsonObject> saved) {
        Set<String> used = new HashSet<>();
        for (JsonObject body : saved) AuthoringLinks.strings(body, used);
        for (Doc doc : docs.values()) if (doc.fresh() && used.contains(doc.key.id())) doc.idEdited = true;
    }

    void discard(StudioKey key) {
        Doc doc = docs.get(key);
        if (doc == null || saving) return;
        if (doc.unresolved()) {
            useTheirs(key);
            return;
        }
        State before = state(doc);
        if (doc.fresh()) {
            docs.remove(key);
            record(key, before, new State(null, false, false), "");
        } else {
            doc.body = doc.base.deepCopy();
            doc.removed = false;
            doc.serverIssues = List.of();
            doc.revision++;
            record(key, before, state(doc), "");
        }
        edited();
    }

    void remove(StudioKey key) {
        Doc doc = docs.get(key);
        if (doc == null || saving) return;
        if (doc.fresh()) { discard(key); return; }
        if (!doc.authored) return;
        State before = state(doc);
        doc.removed = true;
        doc.revision++;
        record(key, before, state(doc), "");
        edited();
    }

    boolean removable(StudioKey key) {
        Doc doc = docs.get(key);
        if (doc != null) return doc.fresh() || doc.authored;
        Row row = rows.get(key);
        return row != null && row.authored;
    }

    boolean canUndo() { return !undo.isEmpty() && !saving; }
    boolean canRedo() { return !redo.isEmpty() && !saving; }

    StudioKey undo() { return canUndo() ? apply(undo.removeLast(), redo, false) : null; }
    StudioKey redo() { return canRedo() ? apply(redo.removeLast(), undo, true) : null; }

    private StudioKey apply(Change change, ArrayDeque<Change> other, boolean forward) {
        StudioKey last = null;
        for (Map.Entry<StudioKey, Edit> entry : change.edits.entrySet()) {
            StudioKey key = entry.getKey();
            State target = forward ? entry.getValue().after() : entry.getValue().before();
            Doc doc = docs.get(key);
            if (!target.present()) docs.remove(key);
            else {
                if (doc == null && rows.containsKey(key)) continue;
                if (doc == null) {
                    doc = new Doc(key, target.body());
                    doc.source = "new";
                    docs.put(key, doc);
                }
                doc.body = target.body();
                doc.removed = target.removed();
                doc.serverIssues = List.of();
                doc.revision++;
            }
            if (last == null && target.present()) last = key;
        }
        change.tag = "";
        other.addLast(change);
        edited();
        return last;
    }

    Set<StudioKey> disksOf(StudioKey key) {
        if (membershipRevision != revision) {
            Map<StudioKey, Set<StudioKey>> result = new HashMap<>();
            for (StudioKey disk : keys(AuthoringKind.DISK)) {
                JsonObject body = view(disk);
                for (String id : StudioJson.strings(body, "tasks")) put(result, new StudioKey(AuthoringKind.TASK, id), disk);
                for (String id : StudioJson.strings(body, "entries")) put(result, new StudioKey(AuthoringKind.ARCHIVE_ENTRY, id), disk);
                for (String id : StudioJson.strings(body, "programs")) put(result, new StudioKey(AuthoringKind.PROGRAM, id), disk);
                for (String id : StudioJson.strings(body, "subscribe")) {
                    put(result, new StudioKey(AuthoringKind.MESSAGE, id), disk);
                    put(result, new StudioKey(AuthoringKind.MAIL_POOL, id), disk);
                }
            }
            boolean more = true;
            for (int pass = 0; more && pass < 64; pass++) {
                more = false;
                for (StudioKey task : keys(AuthoringKind.TASK)) {
                    JsonObject body = view(task);
                    for (String field : List.of("requires", "requires_any")) for (String id : StudioJson.strings(body, field)) {
                        for (StudioKey disk : List.copyOf(result.getOrDefault(new StudioKey(AuthoringKind.TASK, id), Set.of())))
                            more |= put(result, task, disk);
                    }
                }
            }
            for (StudioKey message : keys(AuthoringKind.MESSAGE)) {
                JsonObject trigger = StudioJson.object(view(message), "trigger");
                String type = StudioJson.string(trigger, "type");
                String value = StudioJson.string(trigger, "value");
                if (type.equals("disk_installed")) put(result, message, new StudioKey(AuthoringKind.DISK, value));
                if (type.equals("task_complete"))
                    for (StudioKey disk : List.copyOf(result.getOrDefault(new StudioKey(AuthoringKind.TASK, value), Set.of()))) put(result, message, disk);
            }
            disksOf = result;
            membershipRevision = revision;
            Map<StudioKey, Map<AuthoringKind, List<StudioKey>>> contents = new HashMap<>();
            for (AuthoringKind kind : List.of(AuthoringKind.TASK, AuthoringKind.ARCHIVE_ENTRY, AuthoringKind.MESSAGE, AuthoringKind.MAIL_POOL, AuthoringKind.PROGRAM))
                for (StudioKey content : keys(kind)) for (StudioKey disk : result.getOrDefault(content, Set.of()))
                    contents.computeIfAbsent(disk, ignored -> new HashMap<>()).computeIfAbsent(kind, ignored -> new ArrayList<>()).add(content);
            diskContents = contents;
        }
        return disksOf.getOrDefault(key, Set.of());
    }

    private static boolean put(Map<StudioKey, Set<StudioKey>> map, StudioKey key, StudioKey disk) {
        return map.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(disk);
    }

    boolean direct(StudioKey key, StudioKey disk) {
        String field = diskField(key.kind());
        JsonObject body = view(disk);
        if (field.isEmpty() || body == null) return false;
        if (key.kind() == AuthoringKind.MESSAGE && diskTrigger(key, disk)) return true;
        return StudioJson.contains(body, field, key.id());
    }

    boolean diskTrigger(StudioKey message, StudioKey disk) {
        JsonObject trigger = StudioJson.object(view(message), "trigger");
        return StudioJson.string(trigger, "type").equals("disk_installed") && StudioJson.string(trigger, "value").equals(disk.id());
    }

    static String diskField(AuthoringKind kind) {
        return switch (kind) {
            case TASK -> "tasks";
            case ARCHIVE_ENTRY -> "entries";
            case PROGRAM -> "programs";
            case MESSAGE, MAIL_POOL -> "subscribe";
            default -> "";
        };
    }

    List<StudioKey> onDisk(StudioKey disk, AuthoringKind kind) {
        List<StudioKey> result = new ArrayList<>();
        Set<StudioKey> listed = new LinkedHashSet<>();
        String field = diskField(kind);
        if (!field.isEmpty()) for (String id : StudioJson.strings(view(disk), field)) {
            StudioKey key = new StudioKey(kind, id);
            if (exists(key)) listed.add(key);
            else if (kind != AuthoringKind.MESSAGE && kind != AuthoringKind.MAIL_POOL) listed.add(key);
        }
        disksOf(disk);
        listed.addAll(diskContents.getOrDefault(disk, Map.of()).getOrDefault(kind, List.of()));
        result.addAll(listed);
        return result;
    }

    boolean onDefault(StudioKey key) {
        JsonObject body = view(key);
        if (body == null) return false;
        if (key.kind() == AuthoringKind.TASK) return StudioJson.string(body, "availability", "disk").equals("automatic")
                && StudioJson.list(body, "requires").isEmpty() && StudioJson.list(body, "requires_any").isEmpty()
                && !StudioJson.string(body, "type").equals("link");
        if (key.kind() == AuthoringKind.ARCHIVE_ENTRY) return StudioJson.bool(body, "unlocked_by_default", false);
        return false;
    }

    void putOnDisk(StudioKey key, StudioKey disk) {
        with(List.of(key, disk), () -> transaction(() -> {
            if (key.kind() == AuthoringKind.MESSAGE) {
                JsonObject trigger = StudioJson.object(view(key), "trigger");
                if (StudioJson.string(trigger, "type").equals("random")) {
                    edit(disk, "", body -> StudioJson.add(body, "subscribe", key.id()));
                    edit(key, "", body -> {
                        JsonObject copy = StudioJson.object(body, "trigger").deepCopy();
                        copy.addProperty("subscription_only", true);
                        body.add("trigger", copy);
                    });
                } else edit(key, "", body -> {
                    JsonObject copy = new JsonObject();
                    copy.addProperty("type", "disk_installed");
                    copy.addProperty("value", disk.id());
                    for (String name : List.of("delay", "delay_seconds", "delay_minutes")) if (trigger.has(name)) copy.add(name, trigger.get(name));
                    body.add("trigger", copy);
                });
                return;
            }
            String field = diskField(key.kind());
            if (field.isEmpty()) return;
            edit(disk, "", body -> StudioJson.add(body, field, key.id()));
            if (key.kind() == AuthoringKind.TASK) edit(key, "", body -> body.addProperty("availability", "disk"));
            if (key.kind() == AuthoringKind.ARCHIVE_ENTRY) edit(key, "", body -> body.addProperty("unlocked_by_default", false));
        }));
    }

    void takeOffDisk(StudioKey key, StudioKey disk) {
        with(List.of(key, disk), () -> transaction(() -> {
            String field = diskField(key.kind());
            if (!field.isEmpty()) edit(disk, "", body -> {
                if (StudioJson.contains(body, field, key.id())) {
                    com.google.gson.JsonArray copy = StudioJson.list(body, field).deepCopy();
                    body.add(field, copy);
                    StudioJson.remove(body, field, key.id());
                }
            });
            if (key.kind() == AuthoringKind.MESSAGE && diskTrigger(key, disk)) edit(key, "", body -> {
                JsonObject copy = StudioJson.object(body, "trigger").deepCopy();
                copy.addProperty("value", "");
                body.add("trigger", copy);
            });
        }));
    }

    private void restore() {
        String stored = AntOSSettings.editorDraft("studio:" + identity, "drafts");
        int count = load(stored);
        String workspace = ComputerWorkspaceClientKey.of();
        if (!workspace.equals("workspace:unbound")) for (String app : List.of("TASKS", "ANTMAIL", "ANTAZON", "ARCHIVE", "DISKS", "TERMINAL")) {
            String old = AntOSSettings.editorDraft(workspace, "content-authoring-" + app);
            if (old == null || old.isBlank()) continue;
            int imported = load(old);
            if (imported > 0) {
                count += imported;
                savePending = true;
            }
            AntOSSettings.setEditorDraft(workspace, "content-authoring-" + app, null);
        }
        if (count > 0) {
            say(StudioText.t(count == 1 ? "drafts_restored.one" : "drafts_restored", count), true);
            if (savePending) checkpoint();
            compare(docs.values().stream().filter(doc -> !doc.hash.isEmpty()).map(doc -> doc.key).toList());
        }
        if (unreadableDrafts) say(StudioText.t("drafts_unreadable"), false);
        changed();
    }

    private int load(String stored) {
        if (stored == null || stored.isBlank()) return 0;
        int count = 0;
        JsonArray unreadable = new JsonArray();
        JsonArray entries;
        try { entries = StudioJson.list(JsonParser.parseString(stored).getAsJsonObject(), "drafts"); }
        catch (RuntimeException exception) {
            com.craisinlord.antos.AntOS.LOGGER.warn("AntStudio could not restore drafts for {}", identity, exception);
            AntOSSettings.setEditorDraft("studio:" + identity, "drafts_unreadable", stored);
            say(StudioText.t("drafts_unreadable"), false);
            return 0;
        }
        for (JsonElement element : entries) {
            try {
                JsonObject entry = element.getAsJsonObject();
                StudioKey key = StudioKey.decode(entry);
                if (docs.containsKey(key)) continue;
                JsonObject body = entry.getAsJsonObject("body").deepCopy();
                AuthoringValidation.parse(body.toString());
                Doc doc = new Doc(key, body);
                doc.hash = StudioJson.string(entry, "hash");
                doc.source = StudioJson.string(entry, "source");
                doc.authored = StudioJson.bool(entry, "authored", false);
                doc.removed = StudioJson.bool(entry, "removed", false);
                doc.idEdited = StudioJson.bool(entry, "id_edited", false);
                docs.put(key, doc);
                requested.add(key);
                count++;
            } catch (RuntimeException exception) {
                com.craisinlord.antos.AntOS.LOGGER.warn("AntStudio could not restore a draft for {}", identity, exception);
                unreadable.add(element);
            }
        }
        if (!unreadable.isEmpty()) {
            JsonObject kept = new JsonObject();
            kept.add("drafts", unreadable);
            AntOSSettings.setEditorDraft("studio:" + identity, "drafts_unreadable", kept.toString());
            unreadableDrafts = true;
        }
        return count;
    }

    void checkpoint() {
        if (!restored) return;
        savePending = false;
        JsonArray entries = new JsonArray();
        for (Doc doc : drafts()) {
            JsonObject entry = doc.key.encode();
            entry.addProperty("hash", doc.hash);
            entry.addProperty("source", doc.source);
            entry.addProperty("authored", doc.authored);
            if (doc.removed) entry.addProperty("removed", true);
            if (doc.idEdited) entry.addProperty("id_edited", true);
            entry.add("body", doc.body);
            entries.add(entry);
        }
        JsonObject snapshot = new JsonObject();
        snapshot.add("drafts", entries);
        AntOSSettings.setEditorDraft("studio:" + identity, "drafts", entries.isEmpty() ? null : snapshot.toString());
        savedAt = System.currentTimeMillis();
    }

    private Map<StudioKey, StudioKey> nameFreshDrafts() {
        Map<StudioKey, StudioKey> renamed = new HashMap<>();
        for (Doc doc : List.copyOf(docs.values())) {
            if (!doc.fresh() || doc.idEdited || !doc.hash.isEmpty()) continue;
            String title = StudioText.label(doc.key, doc.body);
            String wanted = StudioText.slug(title);
            if (wanted.isEmpty() || doc.key.path().equals(wanted) || doc.key.path().matches(java.util.regex.Pattern.quote(wanted) + "_\\d+")) continue;
            Doc live = docs.remove(doc.key);
            if (live == null) continue;
            String id = freeId(doc.key.kind(), doc.key.namespace(), title);
            docs.put(doc.key, live);
            if (!id.equals(doc.key.id()) && !held(doc.key) && rename(doc.key, id)) renamed.put(doc.key, new StudioKey(doc.key.kind(), id));
        }
        return renamed;
    }

    void check(Collection<StudioKey> keys, Consumer<Boolean> done) { stage(keys, false, true, done); }
    void save(Collection<StudioKey> keys, boolean reload, Consumer<Boolean> done) { stage(keys, true, reload, done); }

    private void stage(Collection<StudioKey> requestedKeys, boolean commit, boolean reload, Consumer<Boolean> done) {
        if (saving) return;
        Set<StudioKey> wanted = new LinkedHashSet<>();
        for (Doc doc : drafts()) if (requestedKeys == null || requestedKeys.contains(doc.key)) wanted.add(doc.key);
        if (wanted.isEmpty()) { done.accept(true); return; }
        if (wanted.size() > AuthoringValidation.MAX_DRAFTS) {
            say(StudioText.t("error.too_many", AuthoringValidation.MAX_DRAFTS), false);
            done.accept(false);
            return;
        }
        if (commit) {
            Map<StudioKey, StudioKey> renamed = nameFreshDrafts();
            Set<StudioKey> named = new LinkedHashSet<>();
            for (StudioKey key : wanted) named.add(renamed.getOrDefault(key, key));
            wanted = named;
        }
        List<Doc> chosen = new ArrayList<>();
        for (StudioKey key : wanted) if (docs.containsKey(key)) chosen.add(docs.get(key));
        saving = true;
        committing = commit;
        commitAt = System.currentTimeMillis();
        checkpoint();
        List<JsonArray> batches = new ArrayList<>();
        JsonArray batch = new JsonArray();
        int size = 0;
        for (Doc doc : chosen) {
            JsonObject entry = doc.key.encode();
            entry.addProperty("hash", doc.hash);
            if (doc.removed) entry.addProperty("remove", true);
            else entry.add("body", doc.body.deepCopy());
            int length = entry.toString().length();
            if (!batch.isEmpty() && (size + length > 240_000 || batch.size() >= 200)) {
                batches.add(batch);
                batch = new JsonArray();
                size = 0;
            }
            batch.add(entry);
            size += length;
        }
        batches.add(batch);
        Map<StudioKey, JsonObject> sent = new LinkedHashMap<>();
        for (Doc doc : chosen) sent.put(doc.key, doc.body);
        upload(batches, 0, success -> {
            if (!success) {
                settled(false, done);
                return;
            }
            JsonObject request = new JsonObject();
            request.addProperty("reload", reload);
            send(commit ? "commit" : "check", request, response -> {
                boolean applied = false;
                try { applied = finish(response, sent, commit, reload); }
                finally { settled(applied, done); }
            });
        });
    }

    private void upload(List<JsonArray> batches, int index, Consumer<Boolean> done) {
        if (index >= batches.size()) { done.accept(true); return; }
        JsonObject request = new JsonObject();
        request.addProperty("reset", index == 0);
        request.add("drafts", batches.get(index));
        send("stage", request, response -> {
            if (!ok(response)) {
                say(StudioText.t("error.save", message(response)), false);
                done.accept(false);
            } else upload(batches, index + 1, done);
        });
    }

    private boolean finish(JsonObject response, Map<StudioKey, JsonObject> sent, boolean commit, boolean reload) {
        if (!ok(response)) {
            say(StudioText.t("error.save", message(response)), false);
            return false;
        }
        for (StudioKey key : sent.keySet()) {
            Doc doc = docs.get(key);
            if (doc != null) {
                doc.serverIssues = List.of();
                doc.revision++;
            }
        }
        for (JsonElement element : StudioJson.list(response, "checked")) {
            JsonObject row = element.getAsJsonObject();
            Doc doc = docs.get(StudioKey.decode(row));
            if (doc == null) continue;
            List<AuthoringIssue> issues = new ArrayList<>();
            row.getAsJsonArray("issues").forEach(value -> issues.add(AuthoringIssue.decode(value.getAsJsonObject())));
            doc.serverIssues = List.copyOf(issues);
            if (issues.stream().anyMatch(issue -> issue.code().equals("changed_elsewhere"))) doc.conflict = true;
            doc.revision++;
        }
        changed();
        if (!commit) {
            return StudioJson.bool(response, "clean", false);
        }
        List<String> hashes = StudioJson.strings(response, "saved");
        List<StudioKey> order = List.copyOf(sent.keySet());
        List<JsonObject> written = new ArrayList<>();
        for (int index = 0; index < Math.min(hashes.size(), order.size()); index++) {
            StudioKey key = order.get(index);
            Doc doc = docs.get(key);
            if (doc == null) continue;
            if (hashes.get(index).isEmpty()) {
                docs.remove(key);
                requested.remove(key);
                rows.remove(key);
                continue;
            }
            doc.base = sent.get(key).deepCopy();
            written.add(doc.base);
            doc.hash = hashes.get(index);
            doc.source = "file/" + com.craisinlord.antos.content.editor.AuthoringFiles.PACK_NAME;
            doc.authored = true;
            doc.conflict = false;
            doc.revision++;
            rows.put(key, new Row(key, com.craisinlord.antos.content.editor.AuthoringSummary.of(key.kind(), doc.base), doc.source, true, ""));
        }
        keepLinkedIds(written);
        undo.clear();
        redo.clear();
        edited();
        checkpoint();
        boolean applied = StudioJson.bool(response, "applied", false);
        int saved = hashes.size();
        if (response.has("write_failed")) say(StudioText.t("error.partly", saved, StudioJson.string(response, "write_failed")), false);
        else if (response.has("reload_failed")) say(StudioText.t("error.reload", StudioJson.string(response, "reload_failed")), false);
        else if (applied) say(StudioText.t((reload ? "applied" : "saved_later") + (saved == 1 ? ".one" : ""), saved), true);
        if (saved > 0) {
            reloadRevision = AuthoringClientState.reloadRevision();
            if (!leaving) refresh(true);
        }
        return applied;
    }
}
