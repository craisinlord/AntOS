package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.antmail.AntmailSmoke;
import com.craisinlord.antos.content.client.AnternetAccountClientState;
import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.ComputerTasksClientState;
import com.craisinlord.antos.content.network.AnternetAccountNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * Development smoke driver for Antmail teams. With {@code ANTOS_SMOKE} set, it waits for a singleplayer world, seeds
 * throwaway accounts and mail, then walks the real computer UI (log in, Groups, team, thread, Reply All, picker, Tasks
 * MAIL TEAM), saving screenshots to {@code screenshots/antos-smoke/} and a {@code summary.txt}, and closes the game.
 *
 * Input goes through ComputerScreen's own key and mouse handlers. Controls without a click target (Send, MAIL TEAM)
 * are invoked directly and noted in the summary.
 */
public final class AntmailSmokeDriver {
    private static final String PREFIX = "[AntOS smoke] ";
    private record Step(String name, int timeout, BooleanSupplier run) { }

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static int index = -1;
    private static int stepTicks;
    private static int worldTicks;
    private static boolean finished;
    private static int shotNumber;
    private static AntmailSmoke.Seed seed;
    private static CompletableFuture<?> pending;
    private static boolean pendingStarted;

    private AntmailSmokeDriver() { }

    /** Called every client tick; a no-op unless {@code ANTOS_SMOKE} is set. */
    public static void tick() {
        if (finished || !AntmailSmoke.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) { worldTicks = 0; return; }
        if (index < 0) { if (++worldTicks < 80) return; build(); index = 0; stepTicks = 0; log("world ready; starting " + STEPS.size() + " steps"); }
        if (index >= STEPS.size()) { finish(); return; }
        Step step = STEPS.get(index);
        boolean done;
        try { done = step.run.getAsBoolean(); }
        catch (RuntimeException exception) { record("FAIL " + step.name + ": " + exception); AntOS.LOGGER.error(PREFIX + step.name, exception); next(); return; }
        if (done) { record("PASS " + step.name); next(); }
        else if (++stepTicks > step.timeout) { record("FAIL " + step.name + ": timed out after " + step.timeout + " ticks"); next(); }
    }

    private static void next() { index++; stepTicks = 0; pending = null; pendingStarted = false; }
    private static void record(String line) { REPORT.add(line); log(line); }
    private static void log(String line) { AntOS.LOGGER.info(PREFIX + line); }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static Minecraft mc() { return Minecraft.getInstance(); }
    private static ComputerScreen screen() { return mc().gui.screen() instanceof ComputerScreen screen ? screen : null; }
    private static AntmailApp antmail() { var screen = screen(); return screen == null ? null : screen.session.antmail; }
    private static String teamToken() { return "team:" + seed.team(); }
    private static String tr(String key) { return Component.translatable("computer.antos.mail." + key).getString(); }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException("field " + name, exception); }
    }
    private static void setField(Object target, String name, Object value) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException("field " + name, exception); }
    }
    private static Object call(Object target, String name, Object... arguments) {
        for (var method : target.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            try { method.setAccessible(true); return method.invoke(target, arguments); }
            catch (ReflectiveOperationException exception) { throw new IllegalStateException("method " + name, exception); }
        }
        throw new IllegalStateException("no method " + name);
    }
    private static AntmailDirectoryPanel directory() { return field(antmail(), "directoryPanel"); }
    private static AntmailConversationPanel conversation() { return field(antmail(), "conversationPanel"); }

    private static boolean press(int key) { var screen = screen(); return screen != null && screen.keyPressed(new net.minecraft.client.input.KeyEvent(key, 0, 0)); }
    private static void type(String text) { var screen = screen(); for (char c : text.toCharArray()) screen.charTyped(new net.minecraft.client.input.CharacterEvent(c)); }
    /**
     * Clicks a labelled directory control through ComputerScreen's mouse handlers. Controls report desktop-local
     * coordinates; the screen maps window coordinates with {@code (mouse - center) / uiScale}, so invert that here.
     */
    private static boolean click(String label) { return clickLocal(directory().hitCenter(label)); }
    private static boolean clickLocal(int[] center) {
        if (center == null) return false;
        var screen = screen();
        float scale = screen.uiScale();
        double x = ((Number) field(screen, "centerX")).doubleValue() + center[0] * scale;
        double y = ((Number) field(screen, "centerY")).doubleValue() + center[1] * scale;
        screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
        screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0)));
        return true;
    }
    private static boolean visible(String label) { return antmail() != null && directory().hitCenter(label) != null; }

    /** Runs {@code work} on the integrated server once, then waits for it. */
    private static boolean onServer(java.util.function.Supplier<Object> work, java.util.function.Consumer<Object> done) {
        var server = mc().getSingleplayerServer();
        if (!pendingStarted) { pendingStarted = true; pending = server.submit(work::get); }
        if (!pending.isDone()) return false;
        done.accept(pending.join());
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void serverCheck(String stage, int timeout) {
        // Delivery is synchronous, but give the client's send packet time to arrive: retry until every check passes.
        STEPS.add(new Step("server check: " + stage, timeout, () -> {
            if (stepTicks % 20 != 0) return false;
            var server = mc().getSingleplayerServer();
            List<String> results = server.submit(() -> AntmailSmoke.check(server, stage)).join();
            boolean allPass = results.stream().allMatch(line -> line.startsWith("PASS"));
            if (allPass || stepTicks + 20 > timeout) { results.forEach(line -> record("  " + stage + ": " + line)); return allPass; }
            return false;
        }));
    }

    private static void action(String name, int timeout, BooleanSupplier run) { STEPS.add(new Step(name, timeout, run)); }
    private static void waitFor(String name, int timeout, BooleanSupplier condition) { STEPS.add(new Step(name, timeout, condition)); }

    /** Waits a few frames for the UI to settle, then saves a numbered screenshot. */
    private static void shot(String name) {
        STEPS.add(new Step("screenshot " + name, 40, () -> {
            if (stepTicks < 6) return false;
            File directory = new File(mc().gameDirectory, "screenshots/antos-smoke");
            directory.mkdirs();
            String file = String.format("%02d-%s.png", ++shotNumber, name);
            Screenshot.grab(mc().gameDirectory, "antos-smoke/" + file, mc().gameRenderer.mainRenderTarget(), 1, message -> { });
            REPORT.add("  shot " + file);
            return true;
        }));
    }

    // ---- the scenario --------------------------------------------------------------------------------------------

    private static void build() {
        STEPS.clear();
        // Packs can switch the Tasks app off; turn it on in memory for this run only (the config file is not saved).
        action("enable the Tasks app for this run", 5, () -> {
            try {
                var shared = com.craisinlord.antos.config.AntOSSettings.class.getDeclaredMethod("shared");
                shared.setAccessible(true);
                Object apps = field(shared.invoke(null), "desktopApps");
                setField(apps, "tasks", true);
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            return com.craisinlord.antos.config.AntOSSettings.appEnabled("TASKS");
        });
        action("seed accounts, team, groups and mail", 200, () -> onServer(
                () -> AntmailSmoke.seed(mc().getSingleplayerServer(), mc().player.getUUID()),
                result -> { seed = (AntmailSmoke.Seed) result; seed.lines().forEach(line -> REPORT.add("  seed: " + line)); }));
        waitFor("let the world update", 40, () -> stepTicks >= 30);
        serverCheck("seed", 60);

        action("right-click the computer", 100, () -> {
            if (stepTicks == 0) mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(seed.computer()), Direction.WEST, seed.computer(), false));
            return screen() != null;
        });
        shot("login");
        action("log in as " + AntmailSmoke.LEAD, 400, () -> {
            var screen = screen();
            if (screen == null) return false;
            if (stepTicks == 0) {
                setField(screen, "accountWaiting", true);
                AnternetAccountClientState.clearResult();
                AnternetAccountNetworking.login(AntmailSmoke.LEAD, AntmailSmoke.PASSWORD);
            }
            return screen.loggedIn;
        });
        waitFor("boot animation", 200, () -> screen() != null && screen().session.bootTicks <= 0);
        shot("first-run");
        action("dismiss first-run screens", 20, () -> {
            var screen = screen();
            if ((boolean) call(screen, "lookPickerOpen")) call(screen, "confirmLook");
            if (!screen.session.onboardingCompleted) call(screen, "finishOnboarding");
            return screen.session.onboardingCompleted && !(boolean) call(screen, "lookPickerOpen");
        });
        shot("desktop");

        action("open Antmail", 200, () -> {
            var screen = screen();
            if (stepTicks == 0) { screen.open("ANTMAIL"); ComputerScreen.setMaximized(screen.activeWindow, true); }
            return AntmailClientState.getMailbox() != null;
        });
        shot("inbox");

        String team = AntmailDirectoryPanel.teamLabel(AntmailSmoke.LEAD);
        action("F7 opens Groups with the team row", 200, () -> { if (stepTicks == 0) press(com.mojang.blaze3d.platform.InputConstants.KEY_F7); return visible(team); });
        shot("groups");
        action("click the team row", 60, () -> { if (stepTicks == 0) click(team); return visible(tr("message_team")); });
        shot("team-detail");
        action("click Open thread", 200, () -> {
            if (stepTicks == 0) click(tr("open_thread"));
            return seed.team().equals(conversation().id()) && conversation().latestSubject() != null;
        });
        shot("team-thread");
        action("R starts Reply All to the team", 60, () -> {
            if (stepTicks == 0) press(com.mojang.blaze3d.platform.InputConstants.KEY_R);
            var state = antmail().state;
            return state.mode.equals("compose") && state.recipient.equals(teamToken()) && state.subject.startsWith("Re: ");
        });
        shot("reply-compose");
        action("type and send the reply", 200, () -> {
            var app = antmail();
            if (stepTicks == 0) { app.state.focused = true; app.state.field = 3; app.state.cursor = app.state.body.length(); type("Smoke test reply sent from the Antmail UI."); }
            if (stepTicks == 2) call(app, "send");
            return stepTicks > 2 && !app.state.sendPending;
        });
        shot("reply-sent");
        serverCheck("reply", 200);

        action("F7 back to Groups shows history", 200, () -> { if (stepTicks == 0) press(com.mojang.blaze3d.platform.InputConstants.KEY_F7); return visible(AntmailSmoke.OLD_RAID); });
        shot("groups-history");
        action("open " + AntmailSmoke.CREW + " as a plain member", 200, () -> {
            if (stepTicks == 0) click(AntmailSmoke.CREW);
            return visible(tr("leave")) && visible(tr("open_thread")) && !visible(tr("rename"));
        });
        shot("group-member-view");
        action("Leave asks for confirmation", 40, () -> { if (stepTicks == 0) click(tr("leave")); return visible(tr("confirm")); });
        shot("leave-confirm");
        action("Back cancels without leaving", 200, () -> { if (stepTicks == 0) click(tr("back")); return visible(AntmailSmoke.CREW); });

        action("F4 opens the picker with the team", 200, () -> {
            var app = antmail();
            if (stepTicks == 0) { app.state.mode = "compose"; app.state.recipient = "smoke_out@antmail.com"; press(com.mojang.blaze3d.platform.InputConstants.KEY_F4); }
            return visible(team) && visible(AntmailSmoke.CREW);
        });
        shot("picker");
        action("picking the team replaces the recipient line", 60, () -> {
            if (stepTicks == 0) click(team);
            return antmail().state.mode.equals("compose") && antmail().state.recipient.equals(teamToken());
        });
        shot("compose-to-team");

        action("open the Tasks app", 300, () -> {
            var screen = screen();
            if (stepTicks == 0) screen.open("TASKS");
            return screen.activeWindow != null && screen.activeWindow.type.equals("TASKS")
                    && ComputerTasksClientState.team() != null && screen.tasks.teamChipCenter != null;
        });
        shot("tasks");
        action("click the TEAM chip", 60, () -> {
            if (stepTicks == 0) clickLocal(screen().tasks.teamChipCenter);
            return screen().tasks.mailTeamButtonCenter != null;
        });
        shot("tasks-team-menu");
        action("click MAIL TEAM", 60, () -> {
            if (stepTicks == 0) clickLocal(screen().tasks.mailTeamButtonCenter);
            var app = antmail();
            return screen().activeWindow != null && screen().activeWindow.type.equals("ANTMAIL") && app.state.mode.equals("compose") && app.state.recipient.equals(teamToken());
        });
        action("send the MAIL TEAM message", 200, () -> {
            var app = antmail();
            if (stepTicks == 0) { app.state.subject = AntmailSmoke.MAIL_TEAM_SUBJECT; app.state.body = "Sent from the Tasks team menu."; }
            if (stepTicks == 2) call(app, "send");
            return stepTicks > 2 && !app.state.sendPending;
        });
        shot("mail-team-sent");
        serverCheck("mailteam", 200);
    }

    private static void finish() {
        finished = true;
        long failures = REPORT.stream().filter(line -> line.trim().startsWith("FAIL") || line.contains(": FAIL")).count();
        REPORT.add(0, "AntOS Antmail smoke: " + (failures == 0 ? "ALL PASSED" : failures + " FAILURE(S)"));
        REPORT.add(1, "Not driven through a click target (invoked directly): log-in submit, first-run dismissal, Send.");
        try {
            File directory = new File(mc().gameDirectory, "screenshots/antos-smoke");
            directory.mkdirs();
            Files.writeString(new File(directory, "summary.txt").toPath(), String.join(System.lineSeparator(), REPORT) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) { AntOS.LOGGER.error(PREFIX + "could not write summary", exception); }
        log(REPORT.get(0));
        if (System.getenv("ANTOS_SMOKE_KEEP_OPEN") == null) mc().stop();
    }
}
