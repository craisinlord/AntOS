package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.content.client.AuthoringClientState;
import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.client.screen.StudioAssets;
import com.craisinlord.antos.content.editor.AuthoringKind;
import com.craisinlord.antos.content.editor.AuthoringTemplates;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class AntStudioScreen extends Screen {
    private static final int TOP = 24;
    private static final int FOOT = 15;
    private static final ResourceLocation UNDO = new ResourceLocation("antos", "textures/gui/antpaint/undo.png");
    private static final ResourceLocation REDO = new ResourceLocation("antos", "textures/gui/antpaint/redo.png");
    private static final ResourceLocation LOGO = new ResourceLocation("antos", "textures/gui/desktop/disks.png");

    private record Place(StudioCategory category, StudioKey key, StudioKey disk) { }

    private final Screen parent;
    final StudioPrefs prefs = StudioPrefs.load();
    final StudioKit kit = new StudioKit();
    final StudioStore store = StudioStore.current();
    final StudioTree tree = new StudioTree(this);
    final StudioEditors editors = new StudioEditors(this);
    final StudioPreview preview = new StudioPreview(this);
    final StudioMap map = new StudioMap(this);
    final StudioTable table = new StudioTable(this);
    private final List<StudioPopup> popups = new ArrayList<>();
    private final List<Place> history = new ArrayList<>();
    private final Map<StudioCategory, StudioKey> lastOpen = new HashMap<>();
    private int historyIndex = -1;
    private StudioCategory category;
    private StudioKey current;
    private StudioKey disk;
    private boolean showList;
    private boolean showPreview;
    private String focusTitle = "";
    private float pose = 1.0F;
    private int vw = 640;
    private int vh = 360;
    private boolean closing;
    private int failures;
    private String pendingItem = "";
    private Map<StudioKey, List<StudioKey>> users = Map.of();
    private long usersRevision = -1;

    private AntStudioScreen(Screen parent) {
        super(Component.translatable("antos.studio.title"));
        this.parent = parent;
        category = StudioCategory.parse(prefs.category);
        StudioAssets.clear();
        store.open();
    }

    public static void open(String kind, String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        AntStudioScreen screen;
        if (minecraft.screen instanceof AntStudioScreen existing) screen = existing;
        else {
            Screen parent = minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen ? null : minecraft.screen;
            screen = new AntStudioScreen(parent);
            minecraft.setScreen(screen);
        }
        if (kind.isEmpty()) return;
        try {
            AuthoringKind parsed = AuthoringKind.parse(kind);
            if (id.startsWith("@item/")) {
                screen.go(StudioCategory.ANTAZON);
                screen.pendingItem = id.substring(6);
            } else if (id.isEmpty()) screen.go(StudioCategory.of(parsed));
            else screen.open(new StudioKey(parsed, id));
        } catch (IllegalArgumentException ignored) { }
    }

    public static boolean offered() { return AuthoringClientState.available(); }
    public static boolean shortcuts() { return AuthoringClientState.available() && com.craisinlord.antos.config.AntOSSettings.contentEditingShortcuts(); }
    public static void leaveWorld() { StudioStore.leaveWorld(); }
    public static void background() { StudioStore.background(); }

    static void tell(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(text), false);
    }

    private String t(String key, Object... arguments) { return StudioText.t(key, arguments); }

    StudioCategory category() { return category; }
    StudioKey current() { return current; }

    StudioKey contextDisk() {
        if (current != null && current.kind() == AuthoringKind.DISK) return current;
        if (disk != null && store.exists(disk)) return disk;
        return null;
    }

    void go(StudioCategory next) {
        if (category == next && current == null) return;
        category = next;
        prefs.category = next.name();
        StudioKey remembered = store.canonical(lastOpen.get(next));
        current = remembered != null && (store.exists(remembered) || !store.ready()) ? remembered : null;
        disk = null;
        remember();
    }

    void open(StudioKey key) { open(key, null); }

    void open(StudioKey key, StudioKey fromDisk) {
        if (key == null) return;
        boolean inFloppies = category == StudioCategory.FLOPPIES && (key.kind() == AuthoringKind.DISK || key.kind() == AuthoringKind.DISK_CATEGORY || !store.disksOf(key).isEmpty() || store.onDefault(key));
        if (!inFloppies || fromDisk == null && category != StudioCategory.FLOPPIES) category = StudioCategory.of(key.kind());
        if (inFloppies) category = StudioCategory.FLOPPIES;
        prefs.category = category.name();
        if (!key.equals(current)) {
            kit.scrollTo("preview:" + key, 0);
            editors.hovered = "";
        }
        current = key;
        disk = key.kind() == AuthoringKind.DISK ? key : fromDisk != null ? fromDisk : disk != null && store.disksOf(key).contains(disk) ? disk : store.disksOf(key).stream().findFirst().orElse(null);
        lastOpen.put(category, key);
        store.pin(key);
        tree.reveal(key, fromDisk);
        if (category == StudioCategory.TASKS) map.show(key);
        remember();
    }

    private void remember() {
        Place place = new Place(category, current, disk);
        if (historyIndex >= 0 && history.get(historyIndex).equals(place)) return;
        while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
        history.add(place);
        while (history.size() > 80) history.remove(0);
        historyIndex = history.size() - 1;
    }

    private void travel(int step) {
        int next = historyIndex + step;
        if (next < 0 || next >= history.size()) return;
        historyIndex = next;
        Place place = history.get(next);
        category = place.category();
        current = store.canonical(place.key());
        disk = store.canonical(place.disk());
        if (current != null) {
            store.pin(current);
            tree.reveal(current, disk);
        }
    }

    void setAdvanced(boolean value) {
        prefs.advanced = value;
        prefs.save();
    }

    void popup(StudioPopup popup) {
        popups.add(popup);
        if (popup instanceof StudioPicker picker) kit.focus(picker.field());
        else if (popup instanceof StudioNew dialog) kit.focus(dialog.field());
        else if (popup instanceof StudioFind find) kit.focus(find.field());
        else if (popup instanceof StudioDialogs.Prompt prompt) kit.focusAll(prompt.field());
        else kit.blur();
    }

    void closePopup(StudioPopup popup) {
        int index = popups.indexOf(popup);
        if (index < 0) return;
        while (popups.size() > index) popups.remove(popups.size() - 1).closed();
        kit.blur();
        if (!popups.isEmpty() && popups.get(popups.size() - 1) instanceof StudioNew dialog) kit.focus(dialog.field());
    }

    StudioPopup topPopup() { return popups.isEmpty() ? null : popups.get(popups.size() - 1); }

    void help(String topic) { popup(new StudioHelp(this, topic)); }
    void confirm(String message, String yes, Runnable action) { popup(new StudioDialogs.Confirm(this, message, yes, action)); }
    void editJson() { if (current != null && store.loaded(current) != null) popup(new StudioDialogs.Raw(this, current)); }
    void review() { popup(new StudioApply(this)); }

    void newDialog(AuthoringKind preferred) {
        AuthoringKind kind = preferred == null ? category == StudioCategory.FLOPPIES ? AuthoringKind.TASK : category.kinds.get(0) : preferred;
        popup(new StudioNew(this, kind, category == StudioCategory.FLOPPIES || disk != null && current != null && store.disksOf(current).contains(disk) ? contextDisk() : null));
    }

    void newDialogFor(StudioKey floppy) { popup(new StudioNew(this, AuthoringKind.TASK, floppy)); }

    void newMenu(int x, int y) {
        StudioMenu menu = new StudioMenu(this, x, y, 0);
        for (AuthoringKind kind : category.kinds) menu.item(t("new_kind", StudioText.kind(kind)), () -> make(kind, t("untitled", StudioText.kind(kind)), true));
        popup(menu);
    }

    StudioKey create(AuthoringKind kind, String title) {
        JsonObject body = AuthoringTemplates.create(kind);
        String field = StudioText.titleKey(kind);
        if (!field.isEmpty()) body.addProperty(field, kind == AuthoringKind.TERMINAL_COMMAND ? commandName(title) : StudioText.write(kind, field, StudioJson.string(body, field), title));
        switch (kind) {
            case TASK -> {
                if (category == StudioCategory.TASKS && !map.category.isEmpty()) body.addProperty("category", map.category);
                int column = 0;
                int row = 0;
                Set<String> taken = new LinkedHashSet<>();
                String own = StudioJson.string(body, "category");
                for (StudioKey task : store.keys(AuthoringKind.TASK)) if (StudioJson.string(store.view(task), "category", "antos:general").equals(own)) {
                    JsonObject position = StudioJson.object(store.view(task), "position");
                    taken.add(StudioJson.integer(position, "x", 0) + "," + StudioJson.integer(position, "y", 0));
                }
                while (taken.contains(column + "," + row)) if (++column > 7) {
                    column = 0;
                    row++;
                }
                JsonObject position = new JsonObject();
                position.addProperty("x", column);
                position.addProperty("y", Math.min(128, row));
                body.add("position", position);
            }
            case ARCHIVE_ENTRY -> {
                body.remove("description");
                JsonArray content = new JsonArray();
                JsonObject text = new JsonObject();
                text.addProperty("text", "");
                content.add(text);
                body.add("content", content);
                if (category == StudioCategory.ARCHIVE && current != null) {
                    String folder = current.kind() == AuthoringKind.ARCHIVE_FOLDER ? current.id() : current.kind() == AuthoringKind.ARCHIVE_ENTRY ? StudioJson.string(store.view(current), "folder") : "";
                    if (!folder.isEmpty()) body.addProperty("folder", folder);
                }
            }
            case MESSAGE -> {
                String sender = StudioProblems.commonSender(store);
                body.addProperty("sender", sender);
                body.addProperty("sender_display", StudioProblems.senderName(store, sender));
                body.addProperty("body", "");
                JsonObject trigger = new JsonObject();
                trigger.addProperty("type", "disk_installed");
                trigger.addProperty("value", contextDisk() == null ? "" : contextDisk().id());
                body.add("trigger", trigger);
            }
            case PRODUCT -> {
                body.add("rewards", new JsonArray());
                if (current != null && current.kind() == AuthoringKind.PRODUCT && category == StudioCategory.ANTAZON) body.addProperty("category", StudioJson.string(store.view(current), "category", "general"));
            }
            case SELL_RULE -> body.add("items", new JsonArray());
            case REWARD_POOL -> body.add("entries", new JsonArray());
            case PROGRAM -> {
                JsonArray questions = new JsonArray();
                JsonObject question = new JsonObject();
                question.addProperty("question", "");
                JsonArray answers = new JsonArray();
                answers.add("");
                answers.add("");
                question.add("answers", answers);
                question.addProperty("correct", 0);
                questions.add(question);
                body.add("questions", questions);
            }
            case DISK -> {
                body.addProperty("description", "");
                if (!store.known(AuthoringKind.DISK_CATEGORY, "antos:general") && !store.keys(AuthoringKind.DISK_CATEGORY).isEmpty()) body.addProperty("category", store.keys(AuthoringKind.DISK_CATEGORY).get(0).id());
            }
            case TERMINAL_COMMAND -> {
                JsonArray output = new JsonArray();
                output.add("");
                body.add("output", output);
            }
            default -> { }
        }
        String namespace = contextDisk() != null && !contextDisk().namespace().equals("antos") ? contextDisk().namespace() : "";
        return store.create(kind, title, namespace, body);
    }

    private static String commandName(String title) {
        String slug = StudioText.slug(title);
        return slug.length() > 32 ? slug.substring(0, 32) : slug;
    }

    StudioKey make(AuthoringKind kind, String title, boolean onDisk) {
        StudioKey floppy = onDisk ? contextDisk() : null;
        StudioKey[] made = new StudioKey[1];
        store.transaction(() -> {
            made[0] = create(kind, title);
            if (floppy != null && !StudioStore.diskField(kind).isEmpty() && kind != AuthoringKind.MESSAGE) store.putOnDisk(made[0], floppy);
        });
        open(made[0], floppy);
        focusTitle(made[0]);
        return made[0];
    }

    StudioKey createMail(StudioKey floppy, String trigger, String title) {
        StudioKey made = create(AuthoringKind.MESSAGE, title);
        store.edit(made, "", body -> {
            JsonObject when = new JsonObject();
            when.addProperty("type", trigger);
            switch (trigger) {
                case "disk_installed" -> when.addProperty("value", floppy.id());
                case "task_complete" -> {
                    List<StudioKey> tasks = store.onDisk(floppy, AuthoringKind.TASK);
                    when.addProperty("value", tasks.isEmpty() ? "" : tasks.get(tasks.size() - 1).id());
                }
                default -> {
                    when.addProperty("value", "0.1");
                    when.addProperty("subscription_only", true);
                }
            }
            body.add("trigger", when);
            if (trigger.equals("random")) body.addProperty("once", true);
        });
        if (trigger.equals("random")) store.putOnDisk(made, floppy);
        return made;
    }

    void makeMail(StudioKey floppy, String trigger) {
        StudioKey[] made = new StudioKey[1];
        store.with(List.of(floppy), () -> {
            store.transaction(() -> made[0] = createMail(floppy, trigger, t("untitled", StudioText.kind(AuthoringKind.MESSAGE))));
            open(made[0], floppy);
            focusTitle(made[0]);
        });
    }

    void focusTitle(StudioKey key) {
        if (!StudioText.titleKey(key.kind()).isEmpty()) focusTitle = key.toString();
    }

    private String titleField() { return "title:" + current; }

    void tool(String operation, StudioKey key, JsonObject body, String done) {
        JsonObject request = key.encode();
        if (body != null) request.add("body", body);
        store.send(operation, request, response -> store.say(StudioStore.ok(response) ? done : StudioStore.message(response), StudioStore.ok(response)));
    }

    void saveApply() {
        if (store.saving()) return;
        if (store.draftCount() == 0) {
            store.say(t("apply.nothing"), true);
            return;
        }
        if (store.errorCount() > 0) {
            review();
            return;
        }
        store.save(null, true, success -> {
            current = store.canonical(current);
            disk = store.canonical(disk);
            if (!success && store.errorCount() > 0 && topPopup() == null) review();
        });
    }

    void duplicate(StudioKey key) {
        store.with(List.of(key), () -> {
            StudioKey made = store.duplicate(key);
            if (made == null) return;
            StudioKey floppy = contextDisk();
            if (floppy != null && store.direct(key, floppy) && key.kind() != AuthoringKind.MESSAGE) store.putOnDisk(made, floppy);
            open(made, disk);
            focusTitle(made);
        });
    }

    void remove(StudioKey key) {
        store.with(List.of(key), () -> {
            StudioStore.Doc doc = store.loaded(key);
            if (doc == null) return;
            if (doc.removed) {
                store.discard(key);
                return;
            }
            if (!store.removable(key)) {
                store.say(t("remove.inherited", doc.source), false);
                return;
            }
            if (doc.fresh()) {
                store.discard(key);
                if (key.equals(current)) current = null;
                return;
            }
            confirm(t("remove.ask", store.label(key)), t("remove"), () -> store.remove(key));
        });
    }

    void contextMenu(StudioKey key, StudioKey floppy, int x, int y) {
        StudioMenu menu = new StudioMenu(this, x, y, 0);
        boolean exists = store.exists(key);
        if (exists) menu.item(t("open"), () -> open(key, floppy));
        if (exists) menu.item(t("duplicate"), null, "Ctrl D", true, false, () -> duplicate(key));
        String field = StudioStore.diskField(key.kind());
        if (!field.isEmpty() && exists) {
            menu.separator();
            menu.item(t("put_on_floppy"), () -> editors.pickRef("kind:disk", x, y, false, value -> store.putOnDisk(key, new StudioKey(AuthoringKind.DISK, value))));
            for (StudioKey owner : store.disksOf(key)) if (store.direct(key, owner)) menu.item(t("take_off_named", store.label(owner)), () -> store.takeOffDisk(key, owner));
        }
        if (key.kind() == AuthoringKind.DISK && exists) menu.separator().item(t("add_to_floppy"), () -> newDialogFor(key));
        menu.separator();
        if (key.kind() == AuthoringKind.MESSAGE && exists) menu.item(t("send_test"), null, "", !store.broken(key) && !store.busy(), false,
                () -> store.with(List.of(key), () -> tool("send_test", key, store.view(key).deepCopy(), t("test_sent"))));
        if (exists) menu.item(t("edit_json"), () -> {
            open(key, floppy);
            store.with(List.of(key), this::editJson);
        });
        menu.item(t("copy_id"), () -> minecraft.keyboardHandler.setClipboard(key.id()));
        if (store.dirty(key)) menu.item(t("discard"), () -> confirm(t("discard.ask", store.label(key)), t("discard"), () -> {
            store.discard(key);
            if (!store.exists(key) && key.equals(current)) current = null;
        }));
        StudioStore.Doc doc = store.loaded(key);
        menu.danger(t(doc != null && doc.removed ? "keep" : "remove"), store.removable(key), () -> remove(key));
        popup(menu);
    }

    List<StudioKey> users(StudioKey key) {
        if (usersRevision != store.revision()) {
            Map<StudioKey, List<StudioKey>> result = new HashMap<>();
            for (AuthoringKind kind : AuthoringKind.values()) for (StudioKey other : store.keys(kind)) {
                JsonObject body = store.view(other);
                for (Map.Entry<String, com.google.gson.JsonElement> entry : body.entrySet()) collect(entry.getValue(), other, result);
            }
            users = result;
            usersRevision = store.revision();
        }
        List<StudioKey> found = new ArrayList<>();
        for (StudioKey other : users.getOrDefault(new StudioKey(AuthoringKind.TASK, key.id()), List.of())) if (!other.equals(key) && !found.contains(other)) found.add(other);
        return found.stream().filter(other -> mentions(other, key)).limit(40).toList();
    }

    private static void collect(com.google.gson.JsonElement element, StudioKey owner, Map<StudioKey, List<StudioKey>> result) {
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString() && element.getAsString().indexOf(':') > 0 && element.getAsString().length() < 200)
                result.computeIfAbsent(new StudioKey(AuthoringKind.TASK, element.getAsString()), ignored -> new ArrayList<>()).add(owner);
        } else if (element.isJsonArray()) element.getAsJsonArray().forEach(child -> collect(child, owner, result));
        else if (element.isJsonObject()) element.getAsJsonObject().entrySet().forEach(entry -> collect(entry.getValue(), owner, result));
    }

    private boolean mentions(StudioKey other, StudioKey key) {
        JsonObject body = store.view(other);
        return switch (key.kind()) {
            case TASK -> StudioJson.contains(body, "requires", key.id()) || StudioJson.contains(body, "requires_any", key.id()) || StudioJson.contains(body, "unlock_tasks", key.id())
                    || other.kind() == AuthoringKind.TERMINAL_COMMAND && StudioJson.contains(body, "tasks", key.id())
                    || other.kind() == AuthoringKind.MESSAGE && StudioJson.string(StudioJson.object(body, "trigger"), "value").equals(key.id());
            case ARCHIVE_ENTRY -> StudioJson.contains(body, "entries", key.id()) && other.kind() != AuthoringKind.DISK || StudioJson.mentions(StudioJson.list(body, "rewards"), key.id())
                    || StudioJson.contains(body, "archive_entries", key.id()) || StudioJson.mentions(StudioJson.list(body, "objectives"), key.id());
            case DISK -> StudioJson.contains(body, "unlock_disks", key.id()) || StudioJson.mentions(StudioJson.list(body, "objectives"), key.id());
            case DISK_CATEGORY, CATEGORY, GROUP, ARCHIVE_FOLDER -> false;
            default -> StudioJson.mentions(body, key.id());
        };
    }

    void bulkFloppy(List<StudioKey> keys, int x, int y) {
        editors.pickRef("kind:disk", x, y, false, value -> {
            List<StudioKey> needed = new ArrayList<>(keys);
            needed.add(new StudioKey(AuthoringKind.DISK, value));
            store.with(needed, () -> store.transaction(() -> keys.forEach(key -> store.putOnDisk(key, new StudioKey(AuthoringKind.DISK, value)))));
        });
    }

    void bulkCategory(List<StudioKey> keys, int x, int y) {
        editors.pickRef("kind:category", x, y, false, value -> store.with(keys, () -> store.transaction(() -> keys.forEach(key -> store.edit(key, "", body -> body.addProperty("category", value))))));
    }

    void bulkReward(List<StudioKey> keys, int x, int y) {
        popup(new StudioPicker(this, x, y, 0, picked -> store.with(keys, () -> store.transaction(() -> keys.forEach(key -> store.edit(key, "", body -> {
            JsonObject reward = StudioEditors.newReward("item");
            reward.addProperty("item", picked.id());
            JsonArray rewards = StudioJson.list(body, "rewards");
            rewards.add(reward);
            body.add("rewards", rewards);
        }))))).items(false, false));
    }

    void bulkLink(List<StudioKey> keys) {
        if (keys.size() < 2) {
            store.say(t("bulk.link_two"), false);
            return;
        }
        store.with(keys, () -> store.transaction(() -> {
            for (int index = 1; index < keys.size(); index++) {
                StudioKey previous = keys.get(index - 1);
                store.edit(keys.get(index), "", body -> StudioJson.add(body, "requires", previous.id()));
            }
        }));
        store.say(t("bulk.linked", keys.size()), true);
    }

    void bulkRemove(List<StudioKey> keys) {
        if (keys.size() == 1) {
            remove(keys.get(0));
            return;
        }
        store.with(keys, () -> {
            List<StudioKey> removable = keys.stream().filter(store::removable).toList();
            if (removable.isEmpty()) {
                store.say(t("remove.none"), false);
                return;
            }
            confirm(t("remove.many", removable.size()), t("remove"), () -> store.transaction(() -> removable.forEach(store::remove)));
        });
    }

    @Override
    protected void init() {
        super.init();
        double pixelsW = minecraft.getWindow().getWidth();
        double pixelsH = minecraft.getWindow().getHeight();
        int natural = (int) Math.max(1, Math.floor(Math.min(pixelsW / 640.0, pixelsH / 360.0) + 0.001));
        int scale = Math.max(1, natural + prefs.zoom);
        while (scale > 1 && (pixelsW / scale < 300 || pixelsH / scale < 190)) scale--;
        float gui = (float) minecraft.getWindow().getGuiScale();
        pose = scale / gui;
        vw = (int) Math.ceil(pixelsW / scale);
        vh = (int) Math.ceil(pixelsH / scale);
        showList = vw >= 420;
        showPreview = prefs.preview && vw >= 560;
    }

    @Override
    public void tick() {
        store.tick();
        if (closing) return;
        current = store.canonical(current);
        disk = store.canonical(disk);
        if (!pendingItem.isEmpty() && store.ready()) {
            String item = pendingItem;
            pendingItem = "";
            for (StudioKey key : store.keys(AuthoringKind.SELL_RULE)) {
                JsonObject body = store.view(key);
                if (!StudioJson.string(body, "item").equals(item) && !StudioJson.contains(body, "items", item)) continue;
                tree.expand("sell_rules");
                open(key);
                break;
            }
        }
        if (!AuthoringClientState.operator() || AuthoringClientState.refused()) {
            closing = true;
            store.say(t("error.permission"), false);
            if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("antos.studio.error.permission"), false);
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        StudioTheme.apply(prefs.matchAntOS);
        int mx = (int) Math.floor(mouseX / pose);
        int my = (int) Math.floor(mouseY / pose);
        graphics.fill(0, 0, width, height, StudioTheme.window);
        graphics.flush();
        GuiGraphics own = new GuiGraphics(minecraft, graphics.bufferSource());
        own.pose().mulPoseMatrix(graphics.pose().last().pose());
        own.pose().scale(pose, pose, pose);
        try {
            kit.begin(own, mx, my, vw, vh, pose);
            kit.assets.frame(width, height, pose, mx, my);
            renderBody();
            renderTop();
            renderFoot();
            renderPopups();
            kit.end();
            failures = 0;
        } catch (RuntimeException exception) {
            if (failures++ == 0) com.craisinlord.antos.AntOS.LOGGER.error("AntStudio could not draw a frame", exception);
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();
            if (failures > 20) {
                store.checkpoint();
                if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("antos.studio.error.drawing"), false);
                minecraft.execute(this::onClose);
            } else if (!popups.isEmpty() && failures > 5) popups.clear();
        } finally {
            own.flush();
            Ui.refresh();
        }
    }

    private int rail() { return vw < 480 ? 24 : 88; }
    private int listWidth() { return showList ? Math.min(prefs.listWidth, vw / 3) : 0; }
    private boolean taskView() { return category == StudioCategory.TASKS && !prefs.tasksView.equals("list"); }

    private void renderTop() {
        kit.fill(0, 0, vw, TOP, StudioTheme.panel);
        kit.fill(0, TOP - 1, vw, 1, StudioTheme.strongLine);
        int x = 5;
        StudioAssets.icon(kit.g(), LOGO, x, 6, 12);
        x += 16;
        if (vw >= 520) x += kit.text(t("title"), x, 8, 70, StudioTheme.strong) + 8;
        x += kit.button(x, 4, 16, 16, "<", StudioKit.NORMAL, historyIndex > 0, t("back"), () -> travel(-1)) + 3;
        x += kit.button(x, 4, 16, 16, ">", StudioKit.NORMAL, historyIndex + 1 < history.size(), t("forward"), () -> travel(1)) + 4;
        int right = vw - 5;
        right -= 16;
        kit.button(right, 4, 16, 16, "X", StudioKit.NORMAL, true, t("close"), this::onClose);
        right -= 19;
        int menuX = right;
        kit.button(right, 4, 16, 16, "=", StudioKit.NORMAL, true, t("settings"), () -> settings(menuX));
        int drafts = store.draftCount();
        String apply = store.saving() ? t("saving") : drafts == 0 ? t("save_apply") : t("save_apply_count", drafts);
        int applyWidth = kit.buttonWidth(apply);
        right -= applyWidth + 3;
        kit.button(right, 4, applyWidth, 16, apply, drafts > 0 ? StudioKit.PRIMARY : StudioKit.NORMAL, drafts > 0 && !store.saving(), t("save_apply.tip"), this::saveApply);
        boolean wide = vw >= 560;
        if (wide) {
            List<String> modes = List.of(t("simple"), t("advanced"));
            int size = 2 + modes.stream().mapToInt(value -> kit.width(value) + 10).sum();
            right -= size + 4;
            kit.segments(right, 4, modes, prefs.advanced ? 1 : 0, index -> setAdvanced(index == 1));
        }
        right -= 19;
        kit.iconButton(right, 4, 16, REDO, store.canRedo(), t("redo"), this::redo);
        right -= 18;
        kit.iconButton(right, 4, 16, UNDO, store.canUndo(), t("undo"), this::undo);
        String fresh = "+ " + t("new");
        int newWidth = kit.buttonWidth(fresh);
        right -= newWidth + 4;
        kit.button(right, 4, newWidth, 16, fresh, StudioKit.NORMAL, store.ready() && !store.saving(), t("new.tip"), () -> newDialog(null));
        int findWidth = right - 5 - x;
        if (findWidth > 30) {
            boolean hover = kit.hover(x, 4, findWidth, 16);
            kit.box(x, 4, findWidth, 16, StudioTheme.desk, hover ? StudioTheme.accent : StudioTheme.line);
            kit.text(t("find.hint"), x + 6, 8, findWidth - 46, StudioTheme.soft);
            if (findWidth > 110) kit.right("Ctrl K", x + findWidth - 6, 8, StudioTheme.strongLine);
            kit.press(x, 4, findWidth, 16, () -> popup(new StudioFind(this, false)));
        }
    }

    private void settings(int x) {
        StudioMenu menu = new StudioMenu(this, x, 4, 16);
        if (vw < 560) menu.check(t("advanced"), prefs.advanced, () -> setAdvanced(!prefs.advanced)).separator();
        menu.check(t("show_preview"), prefs.preview, () -> {
            prefs.preview = !prefs.preview;
            prefs.save();
            init();
        });
        menu.check(t("group_floppies"), prefs.groupDisks, () -> {
            prefs.groupDisks = !prefs.groupDisks;
            prefs.save();
        });
        menu.separator().heading(t("look"));
        menu.check(t("look.graphite"), !prefs.matchAntOS, () -> look(false));
        menu.check(t("look.match"), prefs.matchAntOS, () -> look(true));
        menu.separator();
        menu.item(t("bigger"), null, "Ctrl +", true, false, () -> zoom(1));
        menu.item(t("smaller"), null, "Ctrl -", true, false, () -> zoom(-1));
        menu.separator();
        menu.item(t("refresh"), null, "F5", true, false, () -> store.refresh(true));
        menu.item(t("reload"), null, "", !store.saving(), false, () -> store.send("reload", new JsonObject(), response -> store.say(StudioStore.ok(response) ? t("reloaded") : StudioStore.message(response), StudioStore.ok(response))));
        menu.item(t("help"), null, "F1", true, false, () -> help("start"));
        popup(menu);
    }

    private void look(boolean match) {
        prefs.matchAntOS = match;
        prefs.save();
        StudioAssets.clear();
    }

    private void zoom(int step) {
        prefs.zoom = Math.max(-3, Math.min(3, prefs.zoom + step));
        prefs.save();
        init();
    }

    void undo() { after(store.undo()); }
    private void redo() { after(store.redo()); }

    private void after(StudioKey key) {
        kit.blur();
        if (current != null && !store.exists(current) && store.row(current) == null) current = null;
        if (key != null && current == null) open(key);
    }

    private void renderFoot() {
        int y = vh - FOOT;
        kit.fill(0, y, vw, FOOT, StudioTheme.panel);
        kit.fill(0, y, vw, 1, StudioTheme.line);
        String status = store.status();
        int left;
        if (!store.error().isEmpty()) left = kit.text(store.error(), 6, y + 4, vw / 2, StudioTheme.error);
        else if (!status.isEmpty()) left = kit.text(status, 6, y + 4, vw * 2 / 3, store.statusGood() ? StudioTheme.accent : StudioTheme.error);
        else if (store.loading()) left = kit.text(t("loading"), 6, y + 4, vw / 2, StudioTheme.soft);
        else {
            long ago = store.savedAgo();
            String kept = store.draftCount() == 0 ? t("foot.saved") : ago < 0 ? t("foot.kept") : ago < 3 ? t("foot.kept_now") : t("foot.kept_ago", ago < 60 ? ago + "s" : ago / 60 + "m");
            left = kit.text(kept, 6, y + 4, vw / 2, StudioTheme.soft);
        }
        int x = vw - 6;
        List<String[]> hints = new ArrayList<>();
        StudioPopup top = topPopup();
        if (top instanceof StudioNew) hints.addAll(List.of(new String[]{"Up/Down", t("foot.choose")}, new String[]{"Enter", t("foot.create")}, new String[]{"Ctrl Enter", t("foot.create_more")}, new String[]{"Esc", t("cancel")}));
        else if (top != null) hints.add(new String[]{"Esc", t("close")});
        else if (category == StudioCategory.TASKS && prefs.tasksView.equals("map")) hints.addAll(List.of(new String[]{"Right-drag", t("foot.pan")}, new String[]{"Scroll", t("foot.zoom")}, new String[]{"Del", t("remove")}, new String[]{"Ctrl S", t("save_apply")}, new String[]{"F1", t("help")}));
        else if (category == StudioCategory.TASKS && prefs.tasksView.equals("table")) hints.addAll(List.of(new String[]{"Tab", t("foot.next_cell")}, new String[]{"Ctrl C/V", t("foot.rows")}, new String[]{"Ctrl S", t("save_apply")}, new String[]{"F1", t("help")}));
        else hints.addAll(List.of(new String[]{"Ctrl S", t("save_apply")}, new String[]{"Ctrl D", t("duplicate")}, new String[]{"Ctrl Enter", t("foot.like_this")}, new String[]{"F1", t("help")}));
        for (int index = hints.size() - 1; index >= 0; index--) {
            String[] hint = hints.get(index);
            int size = kit.width(hint[0] + " " + hint[1]);
            if (x - size < left + 20) break;
            kit.right(hint[1], x, y + 4, StudioTheme.soft);
            kit.right(hint[0], x - kit.width(hint[1]) - 4, y + 4, StudioTheme.text);
            x -= size + 10;
        }
    }

    private void renderBody() {
        int top = TOP;
        int height = vh - TOP - FOOT;
        renderRail(top, height);
        int x = rail();
        if (!store.ready()) {
            kit.fill(x, top, vw - x, height, StudioTheme.window);
            kit.wrap(store.error().isEmpty() ? t("loading") : store.error(), x + 12, top + 14, vw - x - 24, store.error().isEmpty() ? StudioTheme.soft : StudioTheme.error);
            if (!store.error().isEmpty()) kit.button(x + 12, top + 40, t("try_again"), StudioKit.NORMAL, store::open);
            return;
        }
        if (taskView()) {
            renderTasks(x, top, vw - x, height);
            return;
        }
        int list = listWidth();
        int previewWidth = showPreview ? Math.min(prefs.previewWidth, vw / 3) : 0;
        if (list > 0) {
            int treeTop = top;
            if (category == StudioCategory.TASKS) {
                kit.fill(x, top, list, 22, StudioTheme.window);
                taskTabs(x + 4, top + 3);
                treeTop += 20;
            }
            tree.render(x, treeTop, list, top + height - treeTop);
            kit.fill(x + list - 1, top, 1, height, StudioTheme.line);
            divider(x + list - 1, top, height, value -> prefs.listWidth = Math.max(80, Math.min(260, value - rail())));
        } else if (current == null || store.view(current) == null) {
            tree.render(x, top, vw - x, height);
            return;
        }
        x += list;
        int editorWidth = vw - x - previewWidth;
        renderEditor(x, top, editorWidth, height, list == 0);
        if (previewWidth > 0) {
            kit.fill(x + editorWidth, top, 1, height, StudioTheme.line);
            preview.render(current, x + editorWidth + 1, top, previewWidth - 1, height);
            divider(x + editorWidth, top, height, value -> prefs.previewWidth = Math.max(120, Math.min(320, vw - value)));
        }
    }

    private void divider(int x, int top, int height, java.util.function.IntConsumer action) {
        if (kit.hover(x - 2, top, 5, height)) kit.fill(x, top, 1, height, StudioTheme.accent);
        kit.hit(x - 2, top, 5, height, click -> kit.capture(new StudioKit.Capture() {
            @Override public void move(int px, int py) { action.accept(px); }
            @Override public void release(int px, int py) { prefs.save(); }
        }));
    }

    private void renderRail(int top, int height) {
        int w = rail();
        boolean labels = w > 40;
        kit.fill(0, top, w, height, StudioTheme.panel);
        kit.fill(w - 1, top, 1, height, StudioTheme.line);
        int y = top + 4;
        for (StudioCategory value : StudioCategory.values()) {
            boolean on = value == category;
            boolean hover = kit.hover(0, y, w - 1, 20);
            if (on) {
                kit.fill(0, y, w - 1, 20, StudioTheme.selected);
                kit.fill(0, y, 2, 20, StudioTheme.accent);
            }
            value.icon.draw(kit, 6, y + 4, 12);
            if (labels) kit.text(value.title(), 23, y + 6, w - 34, on ? StudioTheme.strong : hover ? StudioTheme.accent : StudioTheme.muted);
            else if (hover) kit.tooltip(value.title());
            boolean problem = false;
            boolean dirty = false;
            for (StudioStore.Doc doc : store.drafts()) if (value.kinds.contains(doc.key.kind())) {
                dirty = true;
                problem |= store.broken(doc.key);
            }
            if (dirty) kit.dot(w - 9, y + 8, problem, false);
            kit.press(0, y, w - 1, 20, () -> go(value));
            y += 20;
        }
        int bottom = top + height - 42;
        if (bottom < y) return;
        kit.fill(6, bottom, w - 13, 1, StudioTheme.line);
        shortcut(bottom + 4, w, labels, t("unsaved"), store.draftCount(), false);
        shortcut(bottom + 22, w, labels, t("problems"), (int) store.errorCount(), true);
    }

    private void shortcut(int y, int w, boolean labels, String label, int count, boolean problem) {
        boolean hover = kit.hover(0, y, w - 1, 18);
        if (count > 0) kit.dot(9, y + 7, problem, false);
        else kit.outline(9, y + 7, 4, 4, StudioTheme.strongLine);
        if (labels) {
            kit.text(label, 23, y + 5, w - 30 - kit.width(Integer.toString(count)), hover ? StudioTheme.accent : StudioTheme.muted);
            kit.right(Integer.toString(count), w - 6, y + 5, StudioTheme.soft);
        } else if (hover) kit.tooltip(label + " " + count);
        kit.press(0, y, w - 1, 18, this::review);
    }

    private void taskTabs(int x, int y) {
        List<String> views = List.of("map", "table", "list");
        kit.segments(x, y, views.stream().map(value -> t("view." + value)).toList(), views.indexOf(prefs.tasksView), index -> {
            prefs.tasksView = views.get(index);
            prefs.save();
        });
    }

    private void renderTasks(int x, int top, int w, int height) {
        boolean mapView = prefs.tasksView.equals("map");
        int dock = mapView && current != null && current.kind() != null && store.view(current) != null ? Math.max(190, Math.min(240, w * 2 / 5)) : 0;
        if (w < 360 && dock > 0) {
            renderEditor(x, top, w, height, true);
            return;
        }
        int main = w - dock;
        kit.fill(x, top, main, 22, StudioTheme.window);
        kit.fill(x, top + 21, main, 1, StudioTheme.line);
        taskTabs(x + 4, top + 3);
        int tabs = 2;
        for (String view : List.of("map", "table", "list")) tabs += kit.width(t("view." + view)) + 10;
        int filterX = x + tabs + 10;
        int reserved = mapView ? 52 : 130;
        int used = map.filters(filterX, top + 3, Math.max(60, main - tabs - 16 - reserved));
        if (mapView) {
            kit.button(x + main - 20, top + 3, 16, 16, "+", StudioKit.NORMAL, true, t("foot.zoom"), () -> map.zoomCenter(1.2F));
            kit.button(x + main - 38, top + 3, 16, 16, "-", StudioKit.NORMAL, true, t("foot.zoom"), () -> map.zoomCenter(1 / 1.2F));
            map.render(x, top + 22, main, height - 22);
        } else {
            int fieldX = Math.max(filterX + used + 8, x + main - 124);
            if (x + main - fieldX > 40) table.filterField(fieldX, top + 3, x + main - fieldX - 5);
            table.render(x, top + 22, main, height - 22);
        }
        if (dock > 0) {
            kit.fill(x + main, top, 1, height, StudioTheme.strongLine);
            renderEditor(x + main + 1, top, dock - 1, height, true);
        }
    }

    private void renderEditor(int x, int top, int w, int height, boolean closable) {
        kit.fill(x, top, w, height, StudioTheme.window);
        if (current == null || store.view(current) == null) {
            emptyEditor(x, top, w, height);
            return;
        }
        StudioStore.Doc doc = store.doc(current);
        JsonObject body = store.view(current);
        int y = top + 5;
        if (doc == null) {
            StudioStore.Row row = store.row(current);
            boolean unreadable = row != null && !row.error.isEmpty() || store.missing(current);
            kit.wrap(unreadable ? t("unreadable", current.id(), row == null ? "" : row.error) : t("loading"), x + 10, y + 8, w - 20, unreadable ? StudioTheme.error : StudioTheme.soft);
            return;
        }
        StudioKey key = current;
        StudioNode root = new StudioNode(store, key);
        String titleName = StudioText.titleKey(key.kind());
        kit.slot(x + 6, y, 22, StudioIcons.of(store, key), false, t("change_icon"), click -> icon(root, click));
        int right = x + w - 6;
        boolean narrow = w < 240;
        boolean mailTest = key.kind() == AuthoringKind.MESSAGE && !narrow;
        boolean roomy = w - 60 - kit.buttonWidth(t("duplicate")) - (mailTest ? kit.buttonWidth(t("send_test")) : 0) - (prefs.advanced ? kit.buttonWidth("JSON") : 0) > 96;
        kit.button(right - 18, y, 18, 16, "...", StudioKit.NORMAL, true, t("more"), click -> contextMenu(key, disk, click.x(), click.y()));
        int buttons = 21;
        if (!narrow) {
            if (roomy) {
                int size = kit.buttonWidth(t("duplicate"));
                kit.button(right - buttons - size, y, size, 16, t("duplicate"), StudioKit.NORMAL, !store.saving(), "Ctrl D", () -> duplicate(key));
                buttons += size + 3;
            }
            if (prefs.advanced) {
                int json = kit.buttonWidth("JSON");
                kit.button(right - buttons - json, y, json, 16, "JSON", StudioKit.NORMAL, true, t("edit_json"), this::editJson);
                buttons += json + 3;
            }
        }
        if (closable) {
            kit.button(right - buttons - 16, y, 16, 16, "X", StudioKit.GHOST, true, t("close_item"), () -> {
                current = null;
                store.pin(null);
                map.selected.clear();
            });
            buttons += 18;
        }
        if (mailTest) {
            int size = kit.buttonWidth(t("send_test"));
            kit.button(right - buttons - size, y, size, 16, t("send_test"), StudioKit.NORMAL, !store.broken(key) && !store.busy(), t("send_test.tip"), () -> tool("send_test", key, doc.body.deepCopy(), t("test_sent")));
            buttons += size + 3;
        }
        int titleX = x + 33;
        int titleWidth = right - buttons - titleX;
        if (titleName.isEmpty()) kit.text(StudioText.label(key, body), titleX, y + 4, titleWidth, StudioTheme.strong);
        else {
            String raw = StudioJson.string(body, titleName);
            kit.field(titleField(), titleX, y, titleWidth, StudioText.read(raw), new StudioKit.Field().hint(t("untitled", StudioText.kind(key.kind()))).max(key.kind() == AuthoringKind.MESSAGE ? 64 : 512)
                    .bad(editors.titleBad(store.problems(key), titleName)).color(StudioTheme.strong), value -> root.put(titleName, StudioText.write(key.kind(), titleName, raw, value)));
            if (StudioText.translated(raw) && kit.hover(titleX, y, titleWidth, 16)) kit.tooltip(t("from_language", raw));
            if (focusTitle.equals(key.toString())) {
                focusTitle = "";
                kit.focusAll(titleField());
            }
        }
        kit.text(subtitle(key, doc), titleX, y + 18, right - titleX - (prefs.advanced ? 40 : 0), doc.removed ? StudioTheme.error : StudioTheme.soft);
        if (prefs.advanced && doc.fresh()) {
            int size = kit.buttonWidth(t("set_id"));
            kit.button(right - size, y + 16, size, 13, t("set_id"), StudioKit.GHOST, true, t("set_id.tip"), () -> popup(new StudioDialogs.Prompt(this, t("set_id"), t("set_id.tip"), key.id(), value -> rename(key, value))));
        }
        y += 31;
        kit.fill(x, y, w, 1, StudioTheme.line);
        y++;
        Component sentence = StudioSentence.of(store, editors, key);
        int lines = Math.min(3, Math.max(1, kit.font().split(sentence, w - 14).size()));
        int sayHeight = lines * 11 + 7;
        kit.fill(x, y, w, sayHeight, StudioTheme.desk);
        int lineY = y + 4;
        for (var line : kit.font().split(sentence, w - 14).stream().limit(3).toList()) {
            kit.g().drawString(kit.font(), line, x + 7, lineY, StudioTheme.text, false);
            lineY += 11;
        }
        y += sayHeight;
        kit.fill(x, y, w, 1, StudioTheme.line);
        y++;
        if (doc.removed) {
            kit.wrap(t("removed_note"), x + 8, y + 8, w - 16, StudioTheme.error);
            kit.button(x + 8, y + 34, t("keep"), StudioKit.NORMAL, () -> store.discard(key));
            return;
        }
        if (doc.conflict) {
            int theirs = kit.buttonWidth(t("use_theirs"));
            int own = kit.buttonWidth(t("fix.keep_mine"));
            boolean stacked = kit.width(t("conflict")) > w - theirs - own - 22;
            kit.fill(x, y, w, stacked ? 34 : 20, StudioTheme.section);
            if (stacked) {
                kit.text(t("conflict"), x + 7, y + 4, w - 14, StudioTheme.error);
                y += 14;
            }
            kit.button(x + w - theirs - 6, y + 2, theirs, 16, t("use_theirs"), StudioKit.NORMAL, true, "", () -> confirm(t("use_theirs.ask"), t("use_theirs"), () -> store.useTheirs(key)));
            kit.button(x + w - theirs - own - 10, y + 2, own, 16, t("fix.keep_mine"), StudioKit.NORMAL, true, "", () -> store.keepMine(key));
            if (!stacked) kit.text(t("conflict"), x + 7, y + 6, w - theirs - own - 22, StudioTheme.error);
            y += 20;
        }
        editors.render(key, x, y, w, top + height - y);
    }

    private void rename(StudioKey key, String value) {
        if (store.rename(key, value)) {
            StudioStore.Doc doc = store.loaded(new StudioKey(key.kind(), value));
            if (doc != null) doc.idEdited = true;
            current = store.canonical(current);
        } else store.say(t("set_id.bad"), false);
    }

    private String subtitle(StudioKey key, StudioStore.Doc doc) {
        if (prefs.advanced) return key.id() + (doc.fresh() ? "" : "   " + doc.source);
        if (doc.removed) return t("removed_short");
        List<String> places = new ArrayList<>();
        for (StudioKey owner : store.disksOf(key)) if (places.size() < 2) places.add(store.label(owner));
        if (places.isEmpty() && key.kind() == AuthoringKind.ARCHIVE_ENTRY && store.known(AuthoringKind.ARCHIVE_FOLDER, StudioJson.string(doc.body, "folder")))
            return t("kind_in", StudioText.kind(key.kind()), store.label(AuthoringKind.ARCHIVE_FOLDER, StudioJson.string(doc.body, "folder")));
        if (store.disksOf(key).size() > 2) return t("kind_on_many", StudioText.kind(key.kind()), store.disksOf(key).size());
        return places.isEmpty() ? StudioText.kind(key.kind()) : t("kind_on", StudioText.kind(key.kind()), String.join(", ", places));
    }

    private void icon(StudioNode root, StudioKit.Click click) {
        boolean clear = click.button() == 1;
        switch (root.key.kind()) {
            case TASK, CATEGORY, GROUP -> asset(root, "icon", "icon_entity", clear, click);
            case ARCHIVE_ENTRY -> asset(root, "cover_item", "cover_entity", clear, click);
            case ARCHIVE_FOLDER -> asset(root, "icon_item", "", clear, click);
            case MESSAGE -> asset(root, "sender_avatar_item", "sender_avatar_entity", clear, click);
            case PRODUCT -> asset(root.at("thumbnail"), "item", "entity", clear, click);
            case DISK -> {
                StudioMenu menu = new StudioMenu(this, click.x(), click.y(), 10);
                for (StudioKey look : store.keys(AuthoringKind.DISK_CATEGORY)) menu.item(store.label(look), StudioIcons.of(store, look), () -> root.put("category", look.id()));
                menu.separator().item(t("new_look"), () -> make(AuthoringKind.DISK_CATEGORY, t("untitled", StudioText.kind(AuthoringKind.DISK_CATEGORY)), false));
                popup(menu);
            }
            case DISK_CATEGORY -> editors.pickRef("texture", click.x(), click.y(), false, value -> root.put("texture", value));
            default -> { }
        }
    }

    private void asset(StudioNode node, String item, String entity, boolean clear, StudioKit.Click click) {
        if (clear) {
            node.change("", target -> {
                target.getAsJsonObject().remove(item);
                if (!entity.isEmpty()) target.getAsJsonObject().remove(entity);
            });
            return;
        }
        editors.pickAsset(node, item, entity, click.x(), click.y());
    }

    private void emptyEditor(int x, int top, int w, int height) {
        int y = top + 20;
        int width = Math.min(260, w - 24);
        int left = x + (w - width) / 2;
        category.icon.draw(kit, left + width / 2 - 12, y, 24);
        y += 34;
        y = kit.wrap(t("empty." + category.name().toLowerCase(java.util.Locale.ROOT)), left, y, width, StudioTheme.muted) + 8;
        kit.button(left, y, width, 18, t("new_kind", StudioText.kind(category.kinds.get(0))), StudioKit.PRIMARY, !store.saving(), "", () -> {
            if (category == StudioCategory.FLOPPIES || category == StudioCategory.PROGRAMS || category == StudioCategory.TERMINAL) make(category.kinds.get(0), t("untitled", StudioText.kind(category.kinds.get(0))), false);
            else newDialog(category.kinds.get(0));
        });
        y += 24;
        kit.button(left, y, width, 16, t("find.hint") + "  (Ctrl K)", StudioKit.NORMAL, true, "", () -> popup(new StudioFind(this, false)));
        y += 22;
        kit.button(left, y, width, 16, t("empty.help"), StudioKit.GHOST, true, "", () -> help("start"));
    }

    private void renderPopups() {
        for (int index = 0; index < popups.size(); index++) {
            StudioPopup popup = popups.get(index);
            kit.layer(index + 1);
            kit.push(300 * (index + 1));
            popup.layout(kit);
            if (popup.shade()) kit.fill(0, 0, vw, vh, Ui.alpha(Ui.SHADOW, 0xA8));
            kit.block(popup.x, popup.y, popup.w, popup.h);
            try { popup.render(kit); }
            finally { kit.pop(); }
            if (index >= popups.size()) break;
        }
        if (popups.isEmpty()) kit.layer(0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) Math.floor(mouseX / pose);
        int y = (int) Math.floor(mouseY / pose);
        StudioPopup top = topPopup();
        if (top != null && !StudioKit.inside(top.x, top.y, top.w, top.h, x, y)) {
            closePopup(top);
            return true;
        }
        if (button == 3) { travel(-1); return true; }
        if (button == 4) { travel(1); return true; }
        kit.mouseDown(x, y, button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        kit.mouseMove((int) Math.floor(mouseX / pose), (int) Math.floor(mouseY / pose));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        kit.mouseUp((int) Math.floor(mouseX / pose), (int) Math.floor(mouseY / pose), button);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        return kit.mouseWheel((int) Math.floor(mouseX / pose), (int) Math.floor(mouseY / pose), vertical);
    }

    @Override
    public boolean charTyped(char character, int modifiers) { return kit.type(character, modifiers); }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_SPACE) map.space = false;
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean control = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        StudioPopup top = topPopup();
        if (top != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closePopup(top);
                return true;
            }
            boolean arrows = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN;
            boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
            if ((arrows || enter || !kit.typing()) && !kit.typingArea() && top.key(key, modifiers)) return true;
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) if (top instanceof StudioPicker && top.key(key, modifiers)) return true;
            kit.key(key, scan, modifiers);
            return true;
        }
        if (control) switch (key) {
            case GLFW.GLFW_KEY_S -> { saveApply(); return true; }
            case GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_P -> { popup(new StudioFind(this, false)); return true; }
            case GLFW.GLFW_KEY_N -> { if (store.ready() && !store.saving()) newDialog(null); return true; }
            case GLFW.GLFW_KEY_D -> { if (current != null) duplicate(current); return true; }
            case GLFW.GLFW_KEY_F -> { kit.focusAll(taskView() && prefs.tasksView.equals("table") ? "table:filter" : tree.field()); return true; }
            case GLFW.GLFW_KEY_Z -> {
                if (shift) redo();
                else undo();
                return true;
            }
            case GLFW.GLFW_KEY_Y -> { redo(); return true; }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> { zoom(1); return true; }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> { zoom(-1); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (current != null) make(current.kind(), t("untitled", StudioText.kind(current.kind())), true);
                return true;
            }
            default -> { }
        }
        if (key == GLFW.GLFW_KEY_F1) { help(topicFor()); return true; }
        if (key == GLFW.GLFW_KEY_F5) { store.refresh(true); return true; }
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0 && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            travel(key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        if (kit.key(key, scan, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (kit.typing()) return true;
        boolean mapView = category == StudioCategory.TASKS && prefs.tasksView.equals("map");
        boolean tableView = category == StudioCategory.TASKS && prefs.tasksView.equals("table");
        if (control && key == GLFW.GLFW_KEY_A) {
            if (mapView) map.selectAll();
            if (tableView) table.selected.addAll(map.tasks());
            return true;
        }
        if (control && tableView && key == GLFW.GLFW_KEY_C) return table.copy();
        if (control && tableView && key == GLFW.GLFW_KEY_V) {
            table.paste();
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE && mapView) {
            map.space = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (mapView && map.removeSelected()) return true;
            if (tableView && !table.selected.isEmpty()) {
                bulkRemove(List.copyOf(table.selected));
                return true;
            }
            if (current != null) {
                StudioKey floppy = category == StudioCategory.FLOPPIES ? contextDisk() : null;
                if (floppy != null && !floppy.equals(current) && store.direct(current, floppy)) store.takeOffDisk(current, floppy);
                else remove(current);
            }
            return true;
        }
        if (mapView && !map.selected.isEmpty() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            map.nudge(key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0, key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0);
            return true;
        }
        if (!taskView() && tree.key(key)) return true;
        return true;
    }

    private String topicFor() {
        return switch (category) {
            case FLOPPIES -> "floppies";
            case TASKS -> "tasks";
            case ARCHIVE -> "articles";
            case MAIL -> "mail";
            case ANTAZON -> "shop";
            case PROGRAMS -> "programs";
            case TERMINAL -> "terminal";
        };
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        closing = true;
        AntOSUiSounds.play(AntOSUiSounds.Cue.CLOSE);
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        kit.cancelDrag();
        kit.assets.close();
        prefs.save();
        store.close();
        super.removed();
    }
}
