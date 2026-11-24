package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.antmail.AntmailSmoke;
import com.craisinlord.antos.content.client.AuthoringClientState;
import com.craisinlord.antos.content.client.AnternetAccountClientState;
import com.craisinlord.antos.content.network.AnternetAccountNetworking;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class AuthoringSmokeDriver {
    private static final List<Step> steps = new ArrayList<>();
    private static final List<String> report = new ArrayList<>();
    private static int index = -1;
    private static int ticks;
    private static int worldTicks;
    private static int shots;
    private static boolean finished;
    private static CompletableFuture<?> pending;
    private static AntmailSmoke.Seed seed;
    private static final String TASK = "editor_smoke:field_samples";
    private static final String MESSAGE = "editor_smoke:field_report";
    private static final String PRODUCT = "editor_smoke:field_listing";
    private static final String SELL_RULE = "editor_smoke:field_sell_rule";
    private static final String DEAL = "editor_smoke:field_deal";
    private static final String ARCHIVE_FOLDER = "editor_smoke:field_folder";
    private static final String ARCHIVE_ENTRY = "editor_smoke:field_archive";
    private static String staleHash;
    private static JsonObject reply;
    private static String graphHash;
    private static int permission;
    private record Step(String name, int timeout, Runnable begin, BooleanSupplier complete) { }
    private AuthoringSmokeDriver() { }

    public static void tick() {
        if (finished || System.getenv("ANTOS_EDITOR_SMOKE") == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) { worldTicks = 0; return; }
        if (index < 0) { if (++worldTicks < 80) return; build(); index = 0; }
        if (index >= steps.size()) { finish(); return; }
        Step step = steps.get(index);
        try {
            if (ticks == 0) step.begin().run();
            if (ticks > 4 && step.complete().getAsBoolean()) { record("PASS " + step.name()); advance(); }
            else if (++ticks > step.timeout()) { record("FAIL " + step.name() + " timed out"); advance(); }
        } catch (Exception exception) { record("FAIL " + step.name() + ": " + exception); AntOS.LOGGER.error("Authoring smoke failure", exception); advance(); }
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }
    private static ComputerScreen screen() { return mc().screen instanceof ComputerScreen value ? value : null; }
    private static ContentAuthoring editor() { return screen().authoring(screen().activeWindow); }
    private static void record(String line) { report.add(line); AntOS.LOGGER.info("[AntOS authoring smoke] {}", line); }
    private static void advance() { index++; ticks = 0; pending = null; reply = null; }
    private static void step(String name, int timeout, Runnable begin, BooleanSupplier complete) { steps.add(new Step(name, timeout, begin, complete)); }
    private static Object field(Object object, String name) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static void set(Object object, String name, Object value) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static Object call(Object object, String name, Object... args) {
        for (var method : object.getClass().getDeclaredMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            try { method.setAccessible(true); return method.invoke(object, args); }
            catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        }
        throw new IllegalStateException("Missing method " + name);
    }
    private static void click(int x, int y, int button) {
        ComputerScreen screen = screen();
        double rawX = screen.centerX + x * screen.uiScale();
        double rawY = screen.centerY + y * screen.uiScale();
        screen.mouseClicked(rawX, rawY, button);
        screen.mouseReleased(rawX, rawY, button);
    }
    private static void headerClick(int button) { click((int) field(editor(), "titleX") + 5, (int) field(editor(), "titleY") + 5, button); }
    private static void nextAntazonKind() { click((int) field(editor(), "left") + 40, (int) field(editor(), "top") + 31, 0); }
    private static void button(int position) {
        int width = (int) field(editor(), "width");
        int buttonWidth = Math.max(30, Math.min(49, (width - 20) / 7));
        click((int) field(editor(), "left") + position * (buttonWidth + 3) + buttonWidth / 2, (int) field(editor(), "top") + 8, 0);
    }
    private static void request(String operation, JsonObject values) { reply = null; AuthoringClientState.request(operation, values, value -> reply = value); }
    private static void server(Supplier<Object> action) { pending = mc().getSingleplayerServer().submit(action::get); }
    private static boolean serverDone() { return pending != null && pending.isDone() && Boolean.TRUE.equals(pending.join()); }
    private static JsonObject body() { return (JsonObject) field(field(editor(), "draft"), "body"); }
    private static String hash() { return (String) field(field(editor(), "draft"), "hash"); }
    private static void shot(String name) {
        step("screenshot " + name, 80, () -> { }, () -> {
            if (ticks < 12) return false;
            var directory = mc().gameDirectory.toPath().resolve("screenshots/antos-authoring");
            try { Files.createDirectories(directory); }
            catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            Screenshot.grab(mc().gameDirectory, "antos-authoring/" + String.format("%02d-%s.png", ++shots, name), mc().getMainRenderTarget(), message -> { });
            return true;
        });
    }

    private static void build() {
        step("seed isolated world", 400, () -> server(() -> {
            var server = mc().getSingleplayerServer();
            var player = server.getPlayerList().getPlayer(mc().player.getUUID());
            if (!player.hasPermissions(2)) server.getPlayerList().op(player.getGameProfile());
            seed = AntmailSmoke.seed(server, player.getUUID());
            return true;
        }), AuthoringSmokeDriver::serverDone);
        step("enable apps in memory", 40, () -> {
            try {
                var method = com.craisinlord.antos.config.AntOSSettings.class.getDeclaredMethod("shared");
                method.setAccessible(true);
                Object apps = field(method.invoke(null), "desktopApps");
                set(apps, "tasks", true); set(apps, "antmail", true); set(apps, "antazon", true); set(apps, "archive", true);
                com.craisinlord.antos.config.AntOSSettings.preferences().pauseWhileOpen = false;
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        }, () -> true);
        step("open computer", 200, () -> { }, () -> {
            if (screen() != null) return true;
            if (ticks % 20 == 0) mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(seed.computer()), Direction.WEST, seed.computer(), false));
            if (ticks == 80) { record("NOTE opened the existing computer screen directly after block interaction did not open it"); ComputerScreen.openComputer(); }
            return false;
        });
        step("log into fixture account", 400, () -> {
            set(screen(), "accountWaiting", true);
            AnternetAccountClientState.clearResult();
            AnternetAccountNetworking.login(AntmailSmoke.LEAD, AntmailSmoke.PASSWORD);
        }, () -> screen() != null && screen().loggedIn);
        step("dismiss onboarding", 240, () -> { }, () -> {
            if (screen().session.bootTicks > 0) return false;
            if ((boolean) call(screen(), "lookPickerOpen")) call(screen(), "confirmLook");
            if (!screen().session.onboardingCompleted) call(screen(), "finishOnboarding");
            screen().open("TASKS"); ComputerScreen.setMaximized(screen().activeWindow, true);
            return screen().session.onboardingCompleted;
        });
        step("editing controls are hidden when turned off", 100, () -> AuthoringClientState.setEnabled(false), () -> !AuthoringClientState.available());
        step("turn on editing controls", 100, () -> AuthoringClientState.setEnabled(true), AuthoringClientState::enabled);
        step("operator has Edit with editing initially off", 200, () -> { }, () -> AuthoringClientState.available() && !AuthoringClientState.editing());
        shot("operator-default");
        step("enter editor through title button", 200, () -> headerClick(0), () -> AuthoringClientState.editing());
        shot("task-map");
        step("create task through toolbar", 100, () -> button(0), () -> field(editor(), "draft") != null && field(editor(), "idField") != null);
        step("edit task ID and title through widgets", 100, () -> {
            ((EditBox) field(editor(), "idField")).setValue(TASK);
            AuthoringForm form = (AuthoringForm) field(editor(), "form");
            @SuppressWarnings("unchecked") var widgets = (java.util.Map<String, Object>) field(form, "widgets");
            EditBox title = (EditBox) widgets.get("/title");
            if (title == null) throw new IllegalStateException("Task title widget missing");
            click(title.getX() + 3, title.getY() + 3, 0);
            title.setValue("");
            for (char character : "Field samples".toCharArray()) screen().charTyped(character, 0);
            JsonObject extension = new JsonObject(); extension.addProperty("preserved", true); body().add("fixture_extension", extension);
        }, () -> body().get("title").getAsString().equals("Field samples"));
        step("undo and redo text changes", 100, () -> {
            call(editor(), "undo");
            if (!body().get("title").getAsString().equals("New task")) throw new IllegalStateException("Undo did not restore title");
            call(editor(), "redo");
        }, () -> body().get("title").getAsString().equals("Field samples"));
        step("prepare visual item field", 60, () -> {
            editor().structuralChange(); body().addProperty("icon", "minecraft:paper"); set(editor(), "tab", "LOOK");
            call(field(editor(), "form"), "invalidate");
        }, () -> formRow("/icon") != null);
        step("open item picker", 60, () -> call(field(editor(), "form"), "openPicker", formRow("/icon")), () -> ((AuthoringForm) field(editor(), "form")).pickerOpen());
        step("search visual item picker", 60, () -> ((EditBox) field(field(editor(), "form"), "pickerSearch")).setValue("minecraft:diamond"), () -> true);
        shot("item-picker");
        step("choose an item through the grid", 60, () -> {
            Object form = field(editor(), "form"); click((int) field(form, "pickerX") + 17, (int) field(form, "pickerY") + 59, 0);
        }, () -> body().get("icon").getAsString().equals("minecraft:diamond"));
        step("return to task properties", 40, () -> set(editor(), "tab", "TASK"), () -> true);
        shot("task-properties");
        step("save task", 240, () -> button(1), () -> !hash().isEmpty() && !(boolean) field(editor(), "loading"));
        step("reload saved task", 400, () -> button(4), () -> com.craisinlord.antos.content.guide.ComputerGuideData.task(ResourceLocation.parse(TASK)) != null);
        shot("saved-task-map");
        step("drag a task on the map", 120, () -> {
            @SuppressWarnings("unchecked") List<Object> nodes = (List<Object>) field(editor(), "nodes");
            Object node = nodes.stream().filter(value -> call(value, "id").equals(TASK)).findFirst().orElseThrow();
            int x = (int) call(node, "x") + 10, y = (int) call(node, "y") + 10;
            editor().click(screen().activeWindow, x, y, 0);
            editor().drag(screen().activeWindow, x + 84, y + 42);
            editor().release(screen().activeWindow);
        }, () -> field(editor(), "draft") != null && body().getAsJsonObject("position").get("x").getAsInt() == 2);
        step("undo and redo map movement", 80, () -> {
            call(editor(), "undo");
            if (body().getAsJsonObject("position").get("x").getAsInt() != 0) throw new IllegalStateException("Map undo failed");
            call(editor(), "redo");
        }, () -> body().getAsJsonObject("position").get("x").getAsInt() == 2);
        step("save moved task", 240, () -> button(1), () -> !(boolean) field(editor(), "loading") && !(boolean) field(field(editor(), "draft"), "dirty"));
        step("create a connected task", 100, () -> button(0), () -> field(editor(), "draft") != null && field(editor(), "idField") != null && hash().isEmpty());
        step("connect a task through the map", 80, () -> {
            ((EditBox) field(editor(), "idField")).setValue("editor_smoke:connected");
            button(5);
            @SuppressWarnings("unchecked") List<Object> nodes = (List<Object>) field(editor(), "nodes");
            Object node = nodes.stream().filter(value -> call(value, "id").equals(TASK)).findFirst().orElseThrow();
            click((int) call(node, "x") + 10, (int) call(node, "y") + 10, 0);
        }, () -> body().has("requires") && body().getAsJsonArray("requires").get(0).getAsString().equals(TASK));
        step("save the connected task", 240, () -> button(1), () -> !(boolean) field(editor(), "loading") && !hash().isEmpty());
        step("open Antmail editor", 180, () -> { screen().open("ANTMAIL"); ComputerScreen.setMaximized(screen().activeWindow, true); }, () -> field(editor(), "app").equals("ANTMAIL") && !(boolean) field(editor(), "loading"));
        step("create authored message", 100, () -> button(0), () -> field(editor(), "draft") != null && field(editor(), "idField") != null);
        step("set message and trigger", 80, () -> {
            ((EditBox) field(editor(), "idField")).setValue(MESSAGE);
            editor().changed();
            body().addProperty("subject", "Editor field report");
            body().addProperty("body", "Your task was completed, {player}. This message was authored in AntOS.");
            body().getAsJsonObject("trigger").addProperty("value", TASK);
            call(field(editor(), "form"), "invalidate");
        }, () -> true);
        step("type in the multiline mail widget", 80, () -> {
            @SuppressWarnings("unchecked") var widgets = (java.util.Map<String, Object>) field(field(editor(), "form"), "widgets");
            AuthoringMultiline widget = (AuthoringMultiline) widgets.get("/body");
            if (widget == null) throw new IllegalStateException("Mail body widget missing");
            click(widget.getX() + 3, widget.getY() + 3, 0);
            ((net.minecraft.client.gui.components.MultilineTextField) field(widget, "model")).seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, body().get("body").getAsString().length());
            screen().keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            for (char character : "Multiline verified.".toCharArray()) screen().charTyped(character, 0);
        }, () -> body().get("body").getAsString().contains("\nMultiline verified."));
        shot("mail-message");
        step("prepare a mail delivery", 60, () -> {
            editor().structuralChange(); JsonObject delivery = new JsonObject(); delivery.add("items", new com.google.gson.JsonArray()); delivery.addProperty("on", "read");
            body().add("delivery", delivery); set(editor(), "tab", "DELIVERY");
        }, () -> formRow("/delivery/items") != null);
        step("add a delivery item through the form", 80, () -> {
            Object form = field(editor(), "form");
            @SuppressWarnings("unchecked") List<Object> placements = (List<Object>) field(form, "placements");
            Object placement = placements.stream().filter(value -> call(call(value, "row"), "path").equals("/delivery/items")).findFirst().orElseThrow();
            int x = (int) field(form, "clipX") + (int) call(placement, "left") + (int) call(placement, "width") - 30;
            int y = (int) field(form, "clipY") - (int) field(form, "scroll") + (int) call(placement, "top") + 7;
            click(x, y, 0);
        }, () -> body().getAsJsonObject("delivery").getAsJsonArray("items").size() == 1
                && body().getAsJsonObject("delivery").getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsString().equals("minecraft:bread"));
        step("show schedule form", 50, () -> set(editor(), "tab", "SCHEDULE"), () -> true);
        shot("mail-schedule");
        step("save authored message", 240, () -> button(1), () -> !hash().isEmpty() && !(boolean) field(editor(), "loading"));
        step("remember revision for conflict test", 20, () -> staleHash = hash(), () -> true);
        step("reload message", 400, () -> button(4), () -> com.craisinlord.antos.content.antmail.AntmailEventData.definition(MESSAGE) != null);
        step("trigger mail from task completion", 200, () -> server(() -> {
            var player = mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());
            com.craisinlord.antos.content.antmail.AntmailEventData.onTaskComplete(player, ResourceLocation.parse(TASK));
            return true;
        }), AuthoringSmokeDriver::serverDone);
        step("verify scheduled mail delivery", 240, () -> { }, () -> {
            if (pending != null && pending.isDone() && serverDone()) return true;
            if (ticks % 20 != 0) return false;
            server(() -> com.craisinlord.antos.content.antmail.AntmailServerData.access(mc().getSingleplayerServer())
                    .mailbox(com.craisinlord.antos.content.antmail.AntmailAddress.ofUsername(AntmailSmoke.LEAD)).inbox().stream()
                    .anyMatch(message -> message.subject().equals("Editor field report")));
            return false;
        });
        step("change saved message outside editor", 120, () -> server(() -> {
            var root = mc().getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR);
            try {
                var file = com.craisinlord.antos.content.editor.AuthoringFiles.document(root, com.craisinlord.antos.content.editor.AuthoringKind.MESSAGE, "editor_smoke", "field_report");
                Files.writeString(file, Files.readString(file) + "\n");
            } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            return true;
        }), AuthoringSmokeDriver::serverDone);
        step("reject stale save", 200, () -> {
            JsonObject values = new JsonObject(); values.addProperty("kind", "message"); values.addProperty("id", MESSAGE); values.addProperty("hash", staleHash);
            values.add("body", com.craisinlord.antos.content.editor.AuthoringTemplates.create(com.craisinlord.antos.content.editor.AuthoringKind.MESSAGE));
            request("save", values);
        }, () -> reply != null && !reply.get("ok").getAsBoolean() && reply.get("error").getAsString().contains("changed"));
        step("open Antazon authoring in its app", 160, () -> {
            screen().open("ANTAZON"); ComputerScreen.setMaximized(screen().activeWindow, true);
        }, () -> field(editor(), "app").equals("ANTAZON") && !(boolean) field(editor(), "loading"));
        step("create Antazon product from the shop editor", 100, () -> button(0), () -> field(editor(), "draft") != null && field(editor(), "idField") != null);
        step("set Antazon product identity and name", 100, () -> {
            ((EditBox) field(editor(), "idField")).setValue(PRODUCT);
            body().addProperty("name", "Field listing");
            editor().structuralChange(); call(field(editor(), "form"), "invalidate");
        }, () -> body().get("name").getAsString().equals("Field listing"));
        shot("antazon-editor");
        step("show Antazon pricing controls", 40, () -> set(editor(), "tab", "PRICE"), () -> true);
        shot("antazon-pricing");
        step("show Antazon contents controls", 40, () -> set(editor(), "tab", "CONTENTS"), () -> true);
        shot("antazon-contents");
        step("return to Antazon listing controls", 40, () -> set(editor(), "tab", "LISTING"), () -> true);
        step("save Antazon product", 240, () -> button(1), () -> !hash().isEmpty() && !(boolean) field(editor(), "loading"));
        step("reload Antazon product into the live catalog", 400, () -> button(4), () -> com.craisinlord.antos.content.antazon.AntazonData.product(ResourceLocation.parse(PRODUCT)) != null);
        step("open Antazon sell-rule authoring", 100, AuthoringSmokeDriver::nextAntazonKind, () -> field(editor(), "kind") == com.craisinlord.antos.content.editor.AuthoringKind.SELL_RULE && !(boolean) field(editor(), "loading"));
        step("create an Antazon sell rule", 100, () -> button(0), () -> field(editor(), "draft") != null && field(editor(), "idField") != null);
        step("set Antazon sell-rule identity", 80, () -> ((EditBox) field(editor(), "idField")).setValue(SELL_RULE), () -> true);
        step("save Antazon sell rule", 240, () -> button(1), () -> !hash().isEmpty() && !(boolean) field(editor(), "loading"));
        step("reload Antazon sell rule into the live catalog", 400, () -> button(4), () -> com.craisinlord.antos.content.antazon.AntazonSellData.rule(ResourceLocation.parse("minecraft:rotten_flesh")) != null);
        step("open Antazon deal-campaign authoring", 100, AuthoringSmokeDriver::nextAntazonKind, () -> field(editor(), "kind") == com.craisinlord.antos.content.editor.AuthoringKind.DEAL_CAMPAIGN && !(boolean) field(editor(), "loading"));
        step("create an Antazon deal campaign", 100, () -> button(0), () -> field(editor(), "draft") != null && field(editor(), "idField") != null);
        step("set Antazon deal identity", 80, () -> ((EditBox) field(editor(), "idField")).setValue(DEAL), () -> true);
        step("save Antazon deal campaign", 240, () -> button(1), () -> !hash().isEmpty() && !(boolean) field(editor(), "loading"));
        step("reload Antazon deal campaign into the live catalog", 400, () -> button(4), () -> com.craisinlord.antos.content.antazon.AntazonDealData.campaign(ResourceLocation.parse(DEAL)) != null);
        step("open Archive authoring in its app", 160, () -> {
            screen().open("ARCHIVE"); ComputerScreen.setMaximized(screen().activeWindow, true);
        }, () -> field(editor(), "app").equals("ARCHIVE") && !(boolean) field(editor(), "loading"));
        step("select Archive folder definitions", 100, AuthoringSmokeDriver::nextAntazonKind, () -> field(editor(), "kind") == com.craisinlord.antos.content.editor.AuthoringKind.ARCHIVE_FOLDER && !(boolean) field(editor(), "loading"));
        step("create an Archive folder", 100, () -> button(0), () -> field(editor(), "draft") != null && field(editor(), "idField") != null);
        step("set Archive folder identity", 80, () -> ((EditBox) field(editor(), "idField")).setValue(ARCHIVE_FOLDER), () -> true);
        step("save Archive folder", 240, () -> button(1), () -> !hash().isEmpty() && !(boolean) field(editor(), "loading"));
        step("reload Archive folder into the live Archive", 400, () -> button(4), () -> com.craisinlord.antos.content.guide.ComputerGuideData.archiveFolder(ResourceLocation.parse(ARCHIVE_FOLDER)) != null);
        step("select Archive entry definitions", 100, AuthoringSmokeDriver::nextAntazonKind, () -> field(editor(), "kind") == com.craisinlord.antos.content.editor.AuthoringKind.ARCHIVE_ENTRY && !(boolean) field(editor(), "loading"));
        step("create an Archive article", 100, () -> button(0), () -> field(editor(), "draft") != null && field(editor(), "idField") != null);
        step("set Archive entry identity and folder", 80, () -> {
            ((EditBox) field(editor(), "idField")).setValue(ARCHIVE_ENTRY);
            body().addProperty("folder", ARCHIVE_FOLDER);
            editor().structuralChange(); call(field(editor(), "form"), "invalidate");
            set(editor(), "preview", true);
        }, () -> body().get("folder").getAsString().equals(ARCHIVE_FOLDER));
        shot("archive-entry-preview");
        step("save Archive article", 240, () -> button(1), () -> !hash().isEmpty() && !(boolean) field(editor(), "loading"));
        step("reload Archive article into the live index", 400, () -> button(4), () -> com.craisinlord.antos.content.guide.ComputerGuideData.entry(ResourceLocation.parse(ARCHIVE_ENTRY)) != null);
        step("leave editing", 160, () -> headerClick(0), () -> !AuthoringClientState.editing() && AuthoringClientState.available());
        shot("normal-antmail");
        step("right-click exposes hide action", 40, () -> headerClick(1), () -> (boolean) field(editor(), "hidePrompt"));
        shot("hide-controls-action");
        step("hide authoring controls", 200, () -> headerClick(0), () -> !AuthoringClientState.available());
        shot("hidden-controls");
        step("restore controls from settings", 200, () -> AuthoringClientState.setEnabled(true), AuthoringClientState::available);
        extraChecks();
        step("settle screenshots", 80, () -> { }, () -> ticks > 30);
    }

    private static Object formRow(String path) {
        @SuppressWarnings("unchecked") List<Object> rows = (List<Object>) field(field(editor(), "form"), "rows");
        return rows.stream().filter(row -> call(row, "path").equals(path)).findFirst().orElse(null);
    }

    private static void extraChecks() {
        step("enter for validation checks", 160, () -> request("enter", new JsonObject()), AuthoringClientState::editing);
        step("save first graph node", 240, () -> request("save", taskRequest("editor_smoke:graph_a", "")), () -> {
            if (reply == null || !reply.get("ok").getAsBoolean()) return false;
            graphHash = reply.get("hash").getAsString(); return true;
        });
        step("save dependent node before reload", 240, () -> {
            JsonObject values = taskRequest("editor_smoke:graph_b", "");
            com.google.gson.JsonArray requires = new com.google.gson.JsonArray(); requires.add("editor_smoke:graph_a");
            values.getAsJsonObject("body").add("requires", requires);
            request("save", values);
        }, () -> reply != null && reply.get("ok").getAsBoolean());
        step("reject cycle across unreloaded files", 240, () -> {
            JsonObject values = taskRequest("editor_smoke:graph_a", graphHash);
            com.google.gson.JsonArray requires = new com.google.gson.JsonArray(); requires.add("editor_smoke:graph_b");
            values.getAsJsonObject("body").add("requires", requires);
            request("save", values);
        }, () -> reply != null && !reply.get("ok").getAsBoolean() && reply.get("error").getAsString().contains("cycle"));
        step("transfer and save a large Unicode definition", 400, () -> {
            JsonObject values = taskRequest("editor_smoke:unicode", "");
            values.getAsJsonObject("body").addProperty("fixture_unicode", "\ud83d\udc1c".repeat(30000));
            request("save", values);
        }, () -> reply != null && reply.get("ok").getAsBoolean()
                && reply.getAsJsonObject("body").get("fixture_unicode").getAsString().equals("\ud83d\udc1c".repeat(30000)));
        step("verify unknown source fields survived", 100, () -> server(() -> {
            try {
                var root = mc().getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR);
                var file = com.craisinlord.antos.content.editor.AuthoringFiles.document(root, com.craisinlord.antos.content.editor.AuthoringKind.TASK, "editor_smoke", "field_samples");
                return com.craisinlord.antos.content.editor.AuthoringValidation.parse(Files.readString(file)).getAsJsonObject("fixture_extension").get("preserved").getAsBoolean();
            } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
        }), AuthoringSmokeDriver::serverDone);
        step("reject a non-operator save on the server", 100, () -> server(AuthoringSmokeDriver::unauthorizedSave), AuthoringSmokeDriver::serverDone);
        step("remove an authored override with backup", 200, () -> {
            JsonObject request = new JsonObject(); request.addProperty("kind", "task"); request.addProperty("id", "editor_smoke:graph_b");
            AuthoringClientState.request("get", request, response -> {
                request.addProperty("hash", response.get("hash").getAsString());
                request("remove", request);
            });
        }, () -> reply != null && reply.has("removed") && reply.get("removed").getAsBoolean());
        step("verify removal backup exists", 100, () -> server(() -> {
            try {
                var root = mc().getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR);
                var file = com.craisinlord.antos.content.editor.AuthoringFiles.document(root, com.craisinlord.antos.content.editor.AuthoringKind.TASK, "editor_smoke", "graph_b");
                return !Files.exists(file) && Files.isRegularFile(file.resolveSibling("graph_b.json.previous"));
            } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
        }), AuthoringSmokeDriver::serverDone);
        step("checkpoint and restore a visual draft", 100, () -> {
            screen().open("TASKS"); ComputerScreen.setMaximized(screen().activeWindow, true);
            call(editor(), "create"); editor().structuralChange(); body().addProperty("title", "Recovery fixture"); editor().checkpoint();
            ContentAuthoring restored = new ContentAuthoring("TASKS"); restored.attach(screen());
            LocalDraftRecovery recovery = (LocalDraftRecovery) field(restored, "recovery");
            if (!recovery.active()) throw new IllegalStateException("Draft recovery missing");
            call(restored, "restore", field(recovery, "pending"));
            JsonObject recovered = (JsonObject) field(field(restored, "draft"), "body");
            if (!recovered.get("title").getAsString().equals("Recovery fixture")) throw new IllegalStateException("Recovered draft differs");
            @SuppressWarnings("unchecked") var drafts = (java.util.Map<String, Object>) field(editor(), "drafts"); drafts.clear(); set(editor(), "draft", null); editor().checkpoint();
        }, () -> true);
        step("leave before regular-player UI check", 100, () -> request("leave", new JsonObject()), () -> !AuthoringClientState.editing());
        step("regular player has no authoring controls", 80, () -> {
            permission = 0;
            for (int level = 0; level <= 4; level++) if (mc().player.hasPermissions(level)) permission = level;
            mc().player.setPermissionLevel(0);
        }, () -> !AuthoringClientState.available() && !AuthoringClientState.editing());
        shot("regular-player");
        step("restore operator permissions in the client fixture", 160, () -> {
            mc().player.setPermissionLevel(permission);
            request("status", new JsonObject());
        }, AuthoringClientState::available);
        step("catalog performance fixture", 500, AuthoringSmokeDriver::startCatalogPerformance, () -> {
            if (pending == null || !pending.isDone()) return false;
            JsonObject result = (JsonObject) pending.join();
            record("PERF catalog of " + result.get("total") + " tasks, 128-row page: cold=" + result.get("cold_ms") + " ms on authoring worker, warm=" + result.get("warm_ms") + " ms");
            return result.get("ok").getAsBoolean();
        });
    }

    private static JsonObject taskRequest(String id, String hash) {
        JsonObject values = new JsonObject(); values.addProperty("kind", "task"); values.addProperty("id", id); values.addProperty("hash", hash);
        values.add("body", com.craisinlord.antos.content.editor.AuthoringTemplates.create(com.craisinlord.antos.content.editor.AuthoringKind.TASK));
        return values;
    }

    private static Object unauthorizedSave() {
        var server = mc().getSingleplayerServer();
        var fake = new net.minecraft.server.level.ServerPlayer(server, server.overworld(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "editor_viewer"), net.minecraft.server.level.ClientInformation.createDefault());
        try {
            var field = com.craisinlord.antos.content.editor.AuthoringService.class.getDeclaredField("sender"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var original = (java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, com.craisinlord.antos.content.network.AuthoringResultPayload>) field.get(null);
            List<com.craisinlord.antos.content.network.AuthoringResultPayload> received = new ArrayList<>();
            com.craisinlord.antos.content.editor.AuthoringService.setSender((player, payload) -> { if (player == fake) received.add(payload); else original.accept(player, payload); });
            try {
                JsonObject values = taskRequest("editor_smoke:forbidden", ""); values.addProperty("operation", "save");
                for (var chunk : com.craisinlord.antos.content.editor.AuthoringWire.split("forbidden", values.toString()))
                    com.craisinlord.antos.content.editor.AuthoringService.handle(fake, new com.craisinlord.antos.content.network.AuthoringRequestPayload(chunk.id(), chunk.index(), chunk.total(), chunk.data()));
                var root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR);
                var file = com.craisinlord.antos.content.editor.AuthoringFiles.document(root, com.craisinlord.antos.content.editor.AuthoringKind.TASK, "editor_smoke", "forbidden");
                return !fake.hasPermissions(2) && !Files.exists(file) && !received.isEmpty()
                        && !com.craisinlord.antos.content.editor.AuthoringValidation.envelope(received.getFirst().data()).get("available").getAsBoolean();
            } finally {
                com.craisinlord.antos.content.editor.AuthoringService.setSender(original);
                com.craisinlord.antos.content.editor.AuthoringService.disconnect(fake.getUUID());
            }
        } catch (ReflectiveOperationException | java.io.IOException exception) { throw new IllegalStateException(exception); }
    }

    private static void startCatalogPerformance() {
        try {
            var active = com.craisinlord.antos.content.editor.AuthoringService.class.getDeclaredField("activeStore"); active.setAccessible(true);
            Object store = active.get(null);
            var worker = store.getClass().getDeclaredField("worker"); worker.setAccessible(true);
            pending = CompletableFuture.supplyAsync(() -> catalogPerformance(store), (java.util.concurrent.Executor) worker.get(store));
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static Object catalogPerformance(Object store) {
        var server = mc().getSingleplayerServer();
        var root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR);
        try {
            JsonObject definition = com.craisinlord.antos.content.editor.AuthoringTemplates.create(com.craisinlord.antos.content.editor.AuthoringKind.TASK);
            com.google.gson.JsonArray references = new com.google.gson.JsonArray();
            for (int index = 0; index < 64; index++) references.add("editor_smoke:field_samples");
            definition.add("requires", references); definition.add("requires_any", references.deepCopy()); definition.add("connects", references.deepCopy());
            String text = definition.toString();
            for (int index = 0; index < 900; index++) com.craisinlord.antos.content.editor.AuthoringFiles.save(root,
                    com.craisinlord.antos.content.editor.AuthoringKind.TASK, "editor_perf", String.format("task_%04d", index), text);
            var type = com.craisinlord.antos.content.editor.AuthoringService.class;
            var revision = store.getClass().getDeclaredField("catalogRevision"); revision.setAccessible(true); revision.setLong(store, -1);
            var method = type.getDeclaredMethod("list", net.minecraft.server.MinecraftServer.class, com.craisinlord.antos.content.editor.AuthoringKind.class, int.class, store.getClass());
            method.setAccessible(true);
            var heartbeat = server.submit(() -> true);
            long start = System.nanoTime();
            JsonObject cold = (JsonObject) method.invoke(null, server, com.craisinlord.antos.content.editor.AuthoringKind.TASK, 0, store);
            long middle = System.nanoTime();
            JsonObject warm = (JsonObject) method.invoke(null, server, com.craisinlord.antos.content.editor.AuthoringKind.TASK, 0, store);
            long end = System.nanoTime();
            JsonObject result = new JsonObject();
            result.addProperty("total", cold.get("total").getAsInt());
            result.addProperty("cold_ms", (middle - start) / 1_000_000.0);
            result.addProperty("warm_ms", (end - middle) / 1_000_000.0);
            result.addProperty("server_heartbeat", heartbeat.isDone());
            result.addProperty("ok", heartbeat.isDone() && !server.isSameThread() && cold.toString().length() <= com.craisinlord.antos.content.editor.AuthoringWire.MAX_CHARS
                    && cold.getAsJsonArray("entries").size() == 128 && warm.getAsJsonArray("entries").size() == 128);
            return result;
        } catch (ReflectiveOperationException | java.io.IOException exception) { throw new IllegalStateException(exception); }
    }

    private static void finish() {
        finished = true;
        long failed = report.stream().filter(value -> value.startsWith("FAIL")).count();
        report.addFirst("Authoring smoke: " + (failed == 0 ? "ALL PASSED" : failed + " failures"));
        try {
            var directory = mc().gameDirectory.toPath().resolve("screenshots/antos-authoring");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("summary.txt"), String.join("\n", report));
        } catch (java.io.IOException exception) { AntOS.LOGGER.error("Could not save authoring smoke report", exception); }
        record(report.getFirst());
        mc().stop();
    }
}
