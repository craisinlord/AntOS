package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.client.ComputerAccessClientState;
import com.craisinlord.antos.content.client.AntazonClientState;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.AnternetAccountClientState;
import com.craisinlord.antos.content.client.AntmailGlitchText;
import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.antmail.AntmailRenderMarkers;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailMailbox;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailProfile;
import com.craisinlord.antos.content.antmail.AntmailWire;
import com.craisinlord.antos.content.antmail.AntmailAttachment;
import com.craisinlord.antos.content.antmail.AntmailAttachmentFiles;
import com.craisinlord.antos.content.antmail.AntmailDraft;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.api.client.archive.ArchiveEntityPreviewRegistry;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.craisinlord.antos.content.network.AntmailAnternetResultPayload;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.craisinlord.antos.content.network.AntmailNetworking;
import com.craisinlord.antos.content.network.AnternetAccountNetworking;
import com.craisinlord.antos.content.network.AnternetAccountResultPayload;
import com.craisinlord.antos.content.computer.paint.AntPaintCanvas;
import com.craisinlord.antos.content.computer.paint.AntPaintHistory;
import com.craisinlord.antos.content.computer.paint.AntPaintTool;
import com.craisinlord.antos.content.computer.paint.AntPaintFile;
import com.craisinlord.antos.content.computer.paint.AntPaintFileCodec;
import com.craisinlord.antos.content.computer.ComputerFileSystem;
import com.craisinlord.antos.content.computer.ComputerDesktopState;
import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.api.client.game.ComputerGameRegistry;
import com.craisinlord.antos.content.computer.terminal.TerminalCommandParser;
import com.craisinlord.antos.content.computer.terminal.TerminalResult;
import com.craisinlord.antos.config.AntOSSettings;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Base64;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ComputerScreen extends Screen {
    static final int WIDTH = 440;
    static final int HEIGHT = 286;
    static final int GREEN = 0xFF65FF65;
    static final int PALE_GREEN = 0xFFB8FFB8;
    static final int DARK_GREEN = 0xFF102010;
    static final int BLACK = 0xFF030603;
    static final int HOVER_FILL = 0xFF173817;
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int WINDOW_CONTROL_WIDTH = 18;
    private static final int WINDOW_CONTROL_COUNT = 3;
    private static final String[] BOOT_MESSAGES = {"ANTS MARCHING...", "COMPUTER COMPUTING...", "HCFS BREWING...", "WAKING THE QUEEN..."};
    private static final Map<String, Session> SESSIONS = new LinkedHashMap<>();

    public static void clearCachedSessions() {
        SESSIONS.clear();
    }
    private static final String[] ICONS = {"ARCHIVE", "FILES", "SETTINGS", "TASKS", "TERMINAL", "TEXT", "PAINT", "ANTMAIL", "ANTAZON", "GAMES", "TRASH"};
    private static final Map<String, String> TITLES = Map.ofEntries(Map.entry("ARCHIVE", "ANTARCHIVE"), Map.entry("FILES", "FILE EXPLORER"),
            Map.entry("SETTINGS", "SYSTEM SETTINGS"), Map.entry("WALLPAPERS", "DESKTOP WALLPAPERS"), Map.entry("TASKS", "TASKS"),
            Map.entry("TERMINAL", "ANTOS TERMINAL"), Map.entry("TEXT", "ANTTEXT EDITOR"), Map.entry("PAINT", "ANTPAINT"),
            Map.entry("ANTMAIL", "ANTMAIL"), Map.entry("ANTAZON", "ANTAZON // SUPPLIES"), Map.entry("GAMES", "INSTALLED GAMES"), Map.entry("TRASH", "RECYCLE BIN"));
    private static final Map<ResourceLocation, int[]> WALLPAPER_TEXTURE_SIZES = new HashMap<>();
    private final String sessionKey;
    final Session session;
    boolean loggedIn;
    String loginMessage = "";
    int loginMessageTicks;
    private String accountUsername = AntOSSettings.lastAnternetUsername();
    private String accountPassword = "";
    private boolean creatingAccount;
    private boolean accountPasswordFocused;
    private boolean accountWaiting;
    private long faceIdStatusAtOpen;
    private boolean faceIdAutoLoginStarted;
    private String accountMessage = "SIGN IN OR CREATE AN ACCOUNT";
    String faceIdMessage = "";
    boolean faceIdWaiting;
    long faceIdRevisionAtRequest;
    boolean faceIdExpectedLinked;
    final GamesApp games = new GamesApp();
    final TrashApp trash = new TrashApp();
    final TasksApp tasks = new TasksApp();
    private ComputerAccessResultPayload accessResult;
    private int observedResult = -1;
    private Window dragging;
    private int dragX;
    private int dragY;
    Window activeWindow;
    private final Set<String> archiveRenderLog = new HashSet<>();
    boolean archiveGreenTint = true;
    /** Verbose per-icon preview logging; off by default because building its arguments runs on every frame. */
    private static final boolean LOG_ARCHIVE_PREVIEWS = Boolean.getBoolean("antos.logArchivePreviews");
    // Icon caches outlive a single screen so reopening the computer does not rebuild preview entities; they are tied to the client level.
    private static final Map<String, LivingEntity> archiveEntityPreviews = new HashMap<>();
    private static final Set<String> unavailableArchiveEntities = new HashSet<>();
    private static final Map<String, ItemStack> archiveItemStacks = new HashMap<>();
    private static Object archivePreviewLevel;

    public ComputerScreen() {
        super(Component.translatable("screen.antos.computer"));
        var accountResult = com.craisinlord.antos.content.client.AnternetAccountClientState.latest();
        this.sessionKey = com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of();
        this.session = SESSIONS.computeIfAbsent(sessionKey, ignored -> new Session());
        this.session.attach(this);
        for (ComputerApp app : List.of(tasks, games, trash)) app.attach(this);
        this.loggedIn = accountResult != null && accountResult.result() == com.craisinlord.antos.content.network.AnternetAccountResultPayload.SUCCESS;
        if (!loggedIn || AntOSSettings.hasCompletedComputerTour(accountResult.accountId())) {
            this.session.onboardingCompleted = true;
            this.session.onboardingStep = -1;
        } else {
            this.session.onboardingCompleted = false;
            this.session.onboardingStep = 0;
        }
        this.session.windows.removeIf(window -> window.type.equals("GAMES") && window.gameOpen);
        if (this.session.bootTicks < 0) this.session.bootTicks = AntOSSettings.consumeInitialBoot() ? 80 : 0;
        ComputerAccessClientState.clear();
        ComputerNetworking.open();
        this.accessResult = ComputerAccessClientState.get();
        faceIdStatusAtOpen = AnternetAccountClientState.faceIdStatusRevision();
        AnternetAccountNetworking.checkFaceId();
        session.antmail.screenOpened(loggedIn);
        if (!loggedIn) {
            session.authenticated = false;
            session.windows.clear();
        }
    }

    public static void openComputer() {
        Minecraft.getInstance().setScreen(new ComputerScreen());
    }

    public static void openPhone() {
        Minecraft.getInstance().setScreen(new ComputerScreen());
    }

    @Override
    public void tick() {
        if (session.bootTicks > 0) session.bootTicks--;
        if (loginMessageTicks > 0) loginMessageTicks--;
        ComputerAccessResultPayload currentResult = ComputerAccessClientState.get();
        if (currentResult != null) {
            accessResult = currentResult;
            if (currentResult.result() != observedResult) {
                observedResult = currentResult.result();
                if (currentResult.result() == ComputerAccessResultPayload.SUCCESS) {
                    loginMessage = Component.translatable("computer.antos.status.access_granted").getString();
                    loginMessageTicks = 40;
                } else if (currentResult.result() == ComputerAccessResultPayload.INVALID_PASSWORD) {
                    loginMessage = Component.translatable("computer.antos.status.access_denied").getString();
                    loginMessageTicks = 100;
                } else if (currentResult.result() == ComputerAccessResultPayload.BUSY) {
                    loginMessage = Component.translatable("computer.antos.status.terminal_busy").getString();
                    loginMessageTicks = 100;
                }
            }
        }
        session.antmail.tick(loggedIn);
        session.antazon.tick(loggedIn, session.windows.stream().anyMatch(window -> window.type.equals("ANTAZON") && !window.minimized));
        tasks.tick(loggedIn && session.windows.stream().anyMatch(window -> window.type.equals("TASKS") && !window.minimized));
        var accountSession = com.craisinlord.antos.content.client.AnternetAccountClientState.latest();
        if (!loggedIn && !creatingAccount && !accountWaiting && !faceIdAutoLoginStarted
                && AnternetAccountClientState.faceIdStatusRevision() > faceIdStatusAtOpen) {
            faceIdAutoLoginStarted = true;
            if (AnternetAccountClientState.faceIdLinked()) {
                accountWaiting = true;
                accountMessage = "SIGNING IN WITH FACE ID...";
                AnternetAccountClientState.clearResult();
                AnternetAccountNetworking.faceIdLogin();
            }
        }
        if (!loggedIn && accountWaiting && accountSession != null) {
            accountWaiting = false;
            if (accountSession.result() == AnternetAccountResultPayload.SUCCESS) {
                AntOSSettings.setLastAnternetUsername(accountSession.username());
                Minecraft.getInstance().setScreen(new ComputerScreen());
                return;
            }
            accountMessage = switch (accountSession.result()) {
                case AnternetAccountResultPayload.USERNAME_TAKEN -> "USERNAME UNAVAILABLE";
                case AnternetAccountResultPayload.INVALID_CREDENTIALS -> "USERNAME OR PASSWORD NOT RECOGNIZED";
                case AnternetAccountResultPayload.RATE_LIMITED -> "PLEASE WAIT BEFORE TRYING AGAIN";
                default -> "INVALID DETAILS // CHECK AND RETRY";
            };
        }
        if (loggedIn && faceIdWaiting
                && AnternetAccountClientState.faceIdStatusRevision() > faceIdRevisionAtRequest) {
            String error = AnternetAccountClientState.faceIdError();
            if (!error.isBlank()) {
                faceIdWaiting = false;
                faceIdMessage = error;
            } else if (faceIdLinkedToCurrentAccount() == faceIdExpectedLinked) {
                faceIdWaiting = false;
                faceIdMessage = "";
            }
        }
        boolean hasAccountSession = accountSession != null
                && accountSession.result() == com.craisinlord.antos.content.network.AnternetAccountResultPayload.SUCCESS;
        if (loggedIn && !hasAccountSession) {
            loggedIn = false;
            session.authenticated = false;
            session.windows.clear();
            session.antmail.state.pickingAttachment = false;
        }
        if (loggedIn && !session.desktopRequested) {
            ComputerNetworking.requestDesktopState();
            session.desktopRequested = true;
        }
        if (currentResult != null && currentResult.data().startsWith("11\u0000")) {
            String[] desktop = currentResult.data().split("\u0000", 3);
            if (desktop.length == 3 && !desktop[2].isBlank()) {
                try {
                    var state = new ComputerDesktopState();
                    state.load(AntmailWire.decodeTag(desktop[2]));
                    updateDesktopSession(state);
                } catch (RuntimeException ignored) {
                }
            }
        }
        if (currentResult != null && currentResult.data().startsWith("12\0\0")) {
            try {
                ComputerDesktopState state = new ComputerDesktopState();
                state.load(AntmailWire.decodeTag(currentResult.data().substring(4)));
                updateDesktopSession(state);
            } catch (RuntimeException ignored) {
            }
        }
        session.terminal.tick();
        if (activeWindow != null && activeWindow.type.equals("GAMES")) games.tick(activeWindow);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        float scale = uiScale();
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, height / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        int left = -WIDTH / 2;
        int top = -HEIGHT / 2;
        int localMouseX = (int) ((mouseX - width / 2.0F) / scale);
        int localMouseY = (int) ((mouseY - height / 2.0F) / scale);
        session.mouseX = localMouseX;
        session.mouseY = localMouseY;
        graphics.fill(left - 5, top - 5, left + WIDTH + 5, top + HEIGHT + 5, GREEN);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, BLACK);
        graphics.fill(left + 5, top + 5, left + WIDTH - 5, top + HEIGHT - 5, DARK_GREEN);
        if (session.bootTicks > 0) renderBoot(graphics, left, top); else if (loggedIn) renderDesktop(graphics, left, top, localMouseX, localMouseY); else renderLogin(graphics, left, top);
        graphics.pose().popPose();
    }

    float uiScale() {
        return Math.min(1.0F, Math.min((width - 24.0F) / WIDTH, (height - 24.0F) / HEIGHT));
    }

    private void renderBoot(GuiGraphics g, int l, int t) {
        int progress = Math.min(100, (80 - session.bootTicks) * 100 / 80);
        int message = Math.min(BOOT_MESSAGES.length - 1, progress * BOOT_MESSAGES.length / 101);
        g.drawString(font, Component.literal("AntOS // v" + AntOS.MOD_VERSION), l + 28, t + 78, GREEN, false);
        g.drawString(font, Component.literal("INITIALIZING SYSTEM..."), l + 28, t + 105, PALE_GREEN, false);
        g.fill(l + 28, t + 132, l + WIDTH - 28, t + 148, BLACK);
        box(g, l + 28, t + 132, l + WIDTH - 28, t + 148, GREEN);
        g.fill(l + 32, t + 136, l + 32 + (WIDTH - 64) * progress / 100, t + 144, GREEN);
        g.drawString(font, Component.literal(progress + "%"), l + 28, t + 164, GREEN, false);
        g.drawString(font, Component.literal(BOOT_MESSAGES[message]), l + 28, t + 190, PALE_GREEN, false);
    }

    private void renderLogin(GuiGraphics g, int l, int t) {
        g.drawString(font, Component.literal("AntOS // v" + AntOS.MOD_VERSION), l + 28, t + 25, GREEN, false);
        g.drawString(font, Component.literal("ANTERNET // ACCOUNT"), l + 28, t + 43, PALE_GREEN, false);
        g.fill(l + 28, t + 59, l + WIDTH - 28, t + 61, GREEN);
        int buttonX = l + WIDTH / 2 - 70;
        loginButton(g, buttonX, t + 70, 66, 20, "LOG IN", !creatingAccount);
        loginButton(g, buttonX + 74, t + 70, 66, 20, "CREATE", creatingAccount);
        g.drawString(font, Component.literal("USERNAME"), l + 28, t + 99, PALE_GREEN, false);
        g.fill(l + 28, t + 109, l + WIDTH - 28, t + 132, BLACK);
        box(g, l + 28, t + 109, l + WIDTH - 28, t + 132, accountPasswordFocused ? 0xFF527952 : GREEN);
        g.drawString(font, Component.literal(accountUsername + (accountPasswordFocused ? "" : "_")), l + 34, t + 117, PALE_GREEN, false);
        g.drawString(font, Component.literal("PASSWORD"), l + 28, t + 140, PALE_GREEN, false);
        g.fill(l + 28, t + 150, l + WIDTH - 28, t + 173, BLACK);
        box(g, l + 28, t + 150, l + WIDTH - 28, t + 173, accountPasswordFocused ? GREEN : 0xFF527952);
        g.drawString(font, Component.literal("*".repeat(accountPassword.length()) + (accountPasswordFocused ? "_" : "")), l + 34, t + 158, PALE_GREEN, false);
        loginButton(g, buttonX, t + 184, 140, 22, accountWaiting ? "CONNECTING..." : creatingAccount ? "CREATE ACCOUNT" : "SIGN IN", true);
        g.drawCenteredString(font, Component.literal(accountMessage), l + WIDTH / 2, t + 218,
                accountMessage.contains("UNAVAILABLE") || accountMessage.contains("NOT RECOGNIZED") ? 0xFFFF7777 : PALE_GREEN);
        g.drawCenteredString(font, Component.literal("Turn on FACE ID in settings to skip login"), l + WIDTH / 2, t + HEIGHT - 14, PALE_GREEN);
    }

    void submitFaceIdLink() {
        faceIdWaiting = true;
        faceIdExpectedLinked = true;
        faceIdRevisionAtRequest = AnternetAccountClientState.faceIdStatusRevision();
        faceIdMessage = "LINKING PLAYER...";
        AnternetAccountNetworking.linkFaceId("");
    }

    private void loginButton(GuiGraphics g, int x, int y, int width, int height, String label, boolean selected) {
        if (hovered(x, y, width, height)) drawHover(g, x, y, width, height);
        box(g, x, y, x + width, y + height, selected || hovered(x, y, width, height) ? GREEN : 0xFF527952);
        g.drawCenteredString(font, Component.literal(label), x + width / 2, y + (height - 8) / 2, selected ? GREEN : PALE_GREEN);
    }

    private void submitAccount() {
        if (accountWaiting) return;
        String username = accountUsername.trim();
        if (username.length() < 3) { accountMessage = "USERNAME NEEDS AT LEAST 3 CHARACTERS"; return; }
        if (accountPassword.isEmpty()) { accountMessage = "PASSWORD CANNOT BE EMPTY"; return; }
        accountWaiting = true;
        accountMessage = creatingAccount ? "CREATING ACCOUNT..." : "SIGNING IN...";
        AnternetAccountClientState.clear();
        AnternetAccountNetworking.checkFaceId();
        if (creatingAccount) AnternetAccountNetworking.create(username, accountPassword);
        else AnternetAccountNetworking.login(username, accountPassword);
        accountPassword = "";
    }

    private void renderDesktop(GuiGraphics g, int l, int t, int mouseX, int mouseY) {
        session.lastUse = System.currentTimeMillis();
        session.mouseX = mouseX;
        session.mouseY = mouseY;
        session.archiveMouseX = mouseX;
        session.archiveMouseY = mouseY;
        renderWallpaper(g, l, t);
        g.fill(l + 9, t + 9, l + WIDTH - 9, t + 30, BLACK);
        g.drawString(font, Component.literal("AntOS // v" + AntOS.MOD_VERSION), l + 18, t + 15, GREEN, false);
        long dayTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getDayTime();
        long dayTicks = Math.floorMod(dayTime, 24000L);
        int hours = (int) ((dayTicks / 1000L + 6L) % 24L);
        int minutes = (int) (dayTicks % 1000L * 60L / 1000L);
        g.drawString(font, Component.literal(String.format("%02d:%02d", hours, minutes)), l + WIDTH - 48, t + 15, PALE_GREEN, false);
        g.fill(l + 9, t + 31, l + WIDTH - 9, t + 33, GREEN);
        List<String> desktopApps = desktopApps();
        for (int i = 0; i < desktopApps.size(); i++) {
            String app = desktopApps.get(i);
            int x = desktopIconX(l, i);
            int y = desktopIconY(t, i);
            boolean hover = inside(x - 8, y - 8, 84, 61, mouseX, mouseY);
            if (hover) drawHover(g, x - 9, y - 9, 84, 62);
            drawDesktopIcon(g, app, x + 25, y + 2);
            if (app.equals("ANTMAIL") && session.antmail.hasUnreadMessages()) drawNotificationBadge(g, x + 35, y + 6);
            if (app.equals("ANTAZON") && session.antazon.hasUnseenDeals()) drawNotificationBadge(g, x + 35, y + 6);
            g.drawString(font, Component.literal(app), x, y + 35, GREEN, false);
        }
        g.flush();
        if (activeWindow != null && !activeWindow.minimized && session.windows.remove(activeWindow)) {
            session.windows.add(activeWindow);
        }
        int layer = 1;
        for (Window window : session.windows) {
            if (!window.minimized) renderWindow(g, window, l, t, mouseX, mouseY, layer++);
        }
        if (!session.onboardingCompleted) renderOnboarding(g, l, t, mouseX, mouseY);
    }

    private void renderOnboarding(GuiGraphics g, int l, int t, int mouseX, int mouseY) {
        List<String> apps = onboardingApps();
        int appStep = session.onboardingStep - 1;
        boolean welcome = session.onboardingStep == 0;
        g.fill(l + 9, t + 34, l + WIDTH - 9, t + HEIGHT - 9, 0xC4000000);
        if (!welcome && appStep >= 0 && appStep < apps.size()) {
            String app = apps.get(appStep);
            int desktopIndex = desktopApps().indexOf(app);
            if (desktopIndex >= 0) {
                int iconX = desktopIconX(l, desktopIndex);
                int iconY = desktopIconY(t, desktopIndex);
                g.fill(iconX - 10, iconY - 10, iconX + 66, iconY + 52, HOVER_FILL);
                box(g, iconX - 10, iconY - 10, iconX + 66, iconY + 52, GREEN);
                drawDesktopIcon(g, app, iconX + 25, iconY + 2);
                g.drawString(font, Component.literal(app), iconX, iconY + 35, GREEN, false);
            }
            int panelX = onboardingAppPanelX(l, desktopIndex);
            int panelY = onboardingAppPanelY(t, desktopIndex);
            int panelW = 190;
            int panelH = 154;
            int contentX = panelX + 10;
            int actionY = panelY + 121;
            g.fill(panelX, panelY, panelX + panelW, panelY + panelH, BLACK);
            box(g, panelX, panelY, panelX + panelW, panelY + panelH, GREEN);
            g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + 25, DARK_GREEN);
            g.drawString(font, Component.literal("ANTOS // APP TOUR"), contentX, panelY + 8, GREEN, false);
            g.drawString(font, Component.literal(app), contentX, panelY + 35, GREEN, false);
            g.drawString(font, Component.literal("APP " + (appStep + 1) + " / " + apps.size()), panelX + panelW - 68, panelY + 35, PALE_GREEN, false);
            g.drawString(font, Component.literal(onboardingAppTitle(app)), contentX, panelY + 53, PALE_GREEN, false);
            wrap(g, onboardingAppDescription(app), contentX, panelY + 70, panelW - 20, PALE_GREEN);
            if (appStep > 0) onboardingButton(g, contentX, actionY, 82, "[ BACK ]", hovered(contentX, actionY, 82, 22));
            onboardingButton(g, panelX + panelW - 78, actionY, 68, appStep + 1 < apps.size() ? "[ NEXT ]" : "[ DONE ]", hovered(panelX + panelW - 78, actionY, 68, 22));
            if (desktopIndex >= 0) {
                int iconX = desktopIconX(l, desktopIndex);
                int iconY = desktopIconY(t, desktopIndex);
                g.fill(iconX - 10, iconY - 10, iconX + 66, iconY + 52, HOVER_FILL);
                box(g, iconX - 10, iconY - 10, iconX + 66, iconY + 52, GREEN);
                drawDesktopIcon(g, app, iconX + 25, iconY + 2);
                g.drawString(font, Component.literal(app), iconX, iconY + 35, GREEN, false);
            }
            return;
        }
        int panelX = l + 30;
        int panelY = t + 43;
        int panelW = WIDTH - 60;
        int panelH = 228;
        int contentX = panelX + 22;
        int actionY = panelY + 185;
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, BLACK);
        box(g, panelX, panelY, panelX + panelW, panelY + panelH, GREEN);
        g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + 25, DARK_GREEN);
        g.drawString(font, Component.literal("ANTOS // ONBOARDING"), contentX, panelY + 8, GREEN, false);
        if (welcome) {
            g.drawString(font, Component.literal("WELCOME TO ANTOS"), contentX, panelY + 43, GREEN, false);
            g.drawString(font, Component.literal("YOUR PERSONAL COMPUTER"), contentX, panelY + 62, PALE_GREEN, false);
            wrap(g, "Explore the archive, complete tasks, communicate with Antmail, and keep useful tools close at hand.", contentX, panelY + 91, panelW - 44, PALE_GREEN);
            g.drawString(font, Component.literal("A QUICK TOUR TAKES ABOUT A MINUTE."), contentX, panelY + 145, GREEN, false);
            g.drawString(font, Component.literal("WELCOME"), contentX, panelY + 166, PALE_GREEN, false);
            onboardingButton(g, contentX, actionY, 126, "[ START TOUR ]", hovered(contentX, actionY, 126, 22));
            onboardingButton(g, panelX + panelW - 110, actionY, 88, "[ SKIP ]", hovered(panelX + panelW - 110, actionY, 88, 22));
            return;
        }
        finishOnboarding();
    }

    private void onboardingButton(GuiGraphics g, int x, int y, int width, String label, boolean hover) {
        if (hover) drawHover(g, x, y, width, 22);
        g.drawCenteredString(font, Component.literal(label), x + width / 2, y + 7, GREEN);
    }

    private List<String> onboardingApps() {
        return desktopApps();
    }

    private int onboardingAppPanelX(int left, int desktopIndex) {
        return desktopIndex % 4 < 2 ? left + 238 : left + 12;
    }

    private int onboardingAppPanelY(int top, int desktopIndex) {
        return desktopIndex / 4 < 2 ? top + 126 : top + 43;
    }

    private String onboardingAppTitle(String app) {
        return switch (app) {
            case "ARCHIVE" -> "LEARN ABOUT THE WORLD";
            case "TASKS" -> "TRACK YOUR TASKS";
            case "ANTMAIL" -> "STAY CONNECTED";
            case "ANTAZON" -> "BUY AND SELL THROUGH ANTAZON";
            case "SETTINGS" -> "CUSTOMIZE ANTOS";
            case "TERMINAL" -> "RUN COMMANDS";
            case "TEXT" -> "WRITE DOCUMENTS";
            case "PAINT" -> "CREATE PIXEL ART";
            case "FILES" -> "KEEP YOUR WORK ORGANIZED";
            case "GAMES" -> "TAKE A BREAK";
            case "TRASH" -> "REMOVE OLD FILES";
            default -> "EXPLORE ANTOS";
        };
    }

    private String onboardingAppDescription(String app) {
        return switch (app) {
            case "ARCHIVE" -> "Discover creatures, items, recipes, and locations. Insert floppy disks to unlock new entries and wallpapers.";
            case "TASKS" -> "Follow tasks, watch your progress, and earn useful rewards as you play.";
            case "ANTMAIL" -> "Read messages, receive task rewards, and send notes or attachments to other AntOS users.";
            case "ANTAZON" -> "Buy supplies with Antcoins, or sell eligible items from a nearby chest to earn Antcoins. Products can arrive in a delivery chest, at configured coordinates, or directly in your inventory.";
            case "SETTINGS" -> "Change wallpapers, manage installed disks, and adjust computer options.";
            case "TERMINAL" -> "Use commands to work with files and perform computer actions directly.";
            case "TEXT" -> "Write, edit, and save plain text documents.";
            case "PAINT" -> "Draw pixel art and save your creations as AntPaint files.";
            case "FILES" -> "Manage local documents and saved AntPaint files on this computer.";
            case "GAMES" -> "Play installed games whenever you want a little recreation.";
            case "TRASH" -> "Drop unwanted files here when you are ready to remove them.";
            default -> "Explore the tools available on your AntOS desktop.";
        };
    }

    private static List<String> desktopApps() {
        return java.util.Arrays.stream(ICONS).filter(AntOSSettings::appEnabled).toList();
    }

    private int desktopIconX(int left, int index) {
        return left + 28 + index % 4 * 104;
    }

    private int desktopIconY(int top, int index) {
        return top + 48 + index / 4 * 78;
    }

    private void drawDesktopIcon(GuiGraphics g, String type, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(1.12F, 1.12F, 1.0F);
        icon(g, type, 0, 0);
        g.pose().popPose();
    }


    private void drawNotificationBadge(GuiGraphics g, int x, int y) {
        String[] circle = {
                "....#####....",
                "..##+++++##..",
                ".#+++++++++#.",
                "#+++++++++++#",
                "#+++++++++++#",
                "#+++++++++++#",
                "#+++++++++++#",
                "#+++++++++++#",
                "#+++++++++++#",
                "#+++++++++++#",
                ".#+++++++++#.",
                "..##+++++##..",
                "....#####...."
        };
        int badgeFill = GREEN;
        for (int row = 0; row < circle.length; row++) {
            for (int column = 0; column < circle[row].length(); column++) {
                char pixel = circle[row].charAt(column);
                if (pixel == '#') g.fill(x - 6 + column, y - 6 + row, x - 5 + column, y - 5 + row, BLACK);
                else if (pixel == '+') g.fill(x - 6 + column, y - 6 + row, x - 5 + column, y - 5 + row, badgeFill);
            }
        }
        g.fill(x - 1, y - 4, x + 1, y + 2, BLACK);
        g.fill(x - 1, y + 3, x + 1, y + 5, BLACK);
    }

    private void renderWallpaper(GuiGraphics g, int l, int t) {
        int left = l + 9;
        int top = t + 34;
        int right = l + WIDTH - 9;
        int bottom = t + HEIGHT - 8;
        String wallpaperId = session.wallpaperId;
        ComputerGuideData.Wallpaper wallpaper;
        try { wallpaper = ComputerGuideData.wallpaper(ResourceLocation.parse(wallpaperId)); }
        catch (RuntimeException exception) { wallpaper = null; }
        if (wallpaper != null && !wallpaper.textureId().isBlank() && drawWallpaperTexture(g, wallpaper, left, top, right - left, bottom - top)) return;
        int hash = wallpaperId.hashCode();
        int green = 24 + Math.floorMod(hash, 40);
        int blue = 12 + Math.floorMod(hash >>> 8, 24);
        int base = 0xFF000000 | (green / 2 << 16) | (green << 8) | blue;
        g.fill(left, top, right, bottom, base);
        if (wallpaperId.endsWith("grid_ant")) {
            for (int py = top + 2; py < bottom; py += 18) {
                for (int px = left + 2; px < right; px += 36) {
                    g.fill(px, py, px + 2, py + 2, 0xFF173817);
                }
            }
            return;
        }
        int accent = 0xFF000000 | (Math.min(120, green + 34) << 8) | Math.min(80, blue + 34);
        int spacing = 18 + Math.floorMod(hash, 13);
        int offset = Math.floorMod(hash >>> 16, spacing);
        for (int y = top - spacing; y < bottom + spacing; y += spacing) {
            int x = left - spacing + offset;
            while (x < right) {
                int size = 3 + Math.floorMod(x + y + hash, 7);
                g.fill(x, y, Math.min(right, x + size), Math.min(bottom, y + 2), accent);
                x += spacing * 2;
            }
            offset = spacing - offset;
        }
    }

    boolean drawWallpaperTexture(GuiGraphics g, ComputerGuideData.Wallpaper wallpaper, int x, int y, int width, int height) {
        try {
            ResourceLocation textureId = ResourceLocation.parse(wallpaper.textureId());
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(textureId.getNamespace(), "textures/" + textureId.getPath() + ".png");
            int[] size = WALLPAPER_TEXTURE_SIZES.get(texture);
            if (size == null) {
                var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
                if (resource.isEmpty()) return false;
                try (var stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                    size = new int[]{image.getWidth(), image.getHeight()};
                }
                WALLPAPER_TEXTURE_SIZES.put(texture, size);
            }
            int sourceWidth = size[0];
            int sourceHeight = size[1];
            int u = 0;
            int v = 0;
            if (wallpaper.fit().equals("cover")) {
                double sourceRatio = (double) sourceWidth / sourceHeight;
                double targetRatio = (double) width / height;
                if (sourceRatio > targetRatio) {
                    int croppedWidth = Math.max(1, (int) Math.round(sourceHeight * targetRatio));
                    u = (sourceWidth - croppedWidth) / 2;
                    sourceWidth = croppedWidth;
                } else if (sourceRatio < targetRatio) {
                    int croppedHeight = Math.max(1, (int) Math.round(sourceWidth / targetRatio));
                    v = (sourceHeight - croppedHeight) / 2;
                    sourceHeight = croppedHeight;
                }
            }
            g.blit(texture, x, y, width, height, u, v, sourceWidth, sourceHeight, size[0], size[1]);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void updateDesktopSession(ComputerDesktopState state) {
        ResourceLocation selected = state.selectedWallpaper();
        if (selected == null || ComputerGuideData.wallpaper(selected) == null) selected = ComputerDesktopState.DEFAULT_WALLPAPER;
        session.wallpaperId = selected.toString();
        session.wallpapers = state.unlockedWallpaperIds().stream().map(ResourceLocation::toString).toList();
    }

    String wallpaperName() {
        int separator = session.wallpaperId.indexOf(':');
        String name = separator >= 0 ? session.wallpaperId.substring(separator + 1) : session.wallpaperId;
        return name.toUpperCase();
    }

    private void icon(GuiGraphics g, String type, int x, int y) {
        if (type.equals("ARCHIVE") || type.equals("DISK")) {
            g.fill(x - 11, y - 2, x + 11, y + 20, BLACK);
            g.fill(x - 8, y + 1, x + 8, y + 16, GREEN);
            g.fill(x - 5, y + 3, x + 5, y + 8, BLACK);
            g.fill(x - 4, y + 4, x + 4, y + 7, PALE_GREEN);
            g.fill(x - 5, y + 11, x + 5, y + 13, BLACK);
            g.fill(x + 5, y + 14, x + 7, y + 16, BLACK);
            return;
        }
        if (type.equals("TASKS")) {
            g.fill(x - 11, y - 3, x + 11, y + 19, BLACK);
            g.fill(x - 8, y, x + 8, y + 16, GREEN);
            g.fill(x - 5, y + 3, x + 5, y + 5, BLACK);
            g.fill(x - 3, y + 8, x + 5, y + 10, BLACK);
            g.fill(x - 3, y + 12, x + 5, y + 14, BLACK);
            g.fill(x - 6, y + 7, x - 4, y + 9, PALE_GREEN);
            g.fill(x - 6, y + 11, x - 4, y + 13, PALE_GREEN);
            return;
        }
        if (type.equals("FILES")) {
            g.fill(x - 8, y - 2, x + 9, y + 14, BLACK);
            g.fill(x - 5, y + 1, x + 6, y + 11, PALE_GREEN);
            g.fill(x - 10, y + 4, x + 11, y + 20, BLACK);
            g.fill(x - 7, y + 7, x + 8, y + 17, GREEN);
            g.fill(x - 8, y + 4, x - 2, y + 7, GREEN);
            g.fill(x - 4, y + 10, x + 5, y + 12, BLACK);
            g.fill(x - 4, y + 14, x + 3, y + 16, BLACK);
            return;
        }
        if (type.equals("SETTINGS")) {
            g.fill(x - 4, y - 2, x + 4, y + 22, BLACK);
            g.fill(x - 12, y + 6, x + 12, y + 14, BLACK);
            g.fill(x - 10, y + 1, x - 5, y + 5, BLACK);
            g.fill(x + 5, y + 1, x + 10, y + 5, BLACK);
            g.fill(x - 10, y + 15, x - 5, y + 19, BLACK);
            g.fill(x + 5, y + 15, x + 10, y + 19, BLACK);
            g.fill(x - 2, y, x + 2, y + 20, GREEN);
            g.fill(x - 10, y + 8, x + 10, y + 12, GREEN);
            g.fill(x - 7, y + 3, x + 7, y + 17, GREEN);
            g.fill(x - 3, y + 7, x + 3, y + 13, BLACK);
            g.fill(x - 1, y + 9, x + 1, y + 11, PALE_GREEN);
            return;
        }
        if (type.equals("TERMINAL")) {
            g.fill(x - 12, y - 1, x + 12, y + 19, BLACK);
            g.fill(x - 9, y + 2, x + 9, y + 14, GREEN);
            g.fill(x - 6, y + 5, x - 3, y + 8, BLACK);
            g.fill(x - 3, y + 8, x + 1, y + 11, BLACK);
            g.fill(x - 6, y + 11, x - 3, y + 14, BLACK);
            g.fill(x + 3, y + 11, x + 7, y + 13, BLACK);
            g.fill(x - 5, y + 16, x + 5, y + 18, BLACK);
            return;
        }
        if (type.equals("TEXT")) {
            g.fill(x - 9, y - 2, x + 10, y + 20, BLACK);
            g.fill(x - 6, y + 1, x + 7, y + 17, GREEN);
            g.fill(x + 4, y + 1, x + 7, y + 4, BLACK);
            g.fill(x - 3, y + 5, x + 4, y + 7, BLACK);
            g.fill(x - 3, y + 9, x + 4, y + 11, BLACK);
            g.fill(x - 3, y + 13, x + 2, y + 15, BLACK);
            return;
        }
        if (type.equals("PAINT")) {
            g.fill(x - 11, y + 2, x + 8, y + 20, BLACK);
            g.fill(x - 8, y + 5, x + 5, y + 17, GREEN);
            g.fill(x - 11, y + 5, x - 8, y + 7, GREEN);
            g.fill(x - 6, y + 8, x - 3, y + 11, BLACK);
            g.fill(x, y + 7, x + 3, y + 10, BLACK);
            g.fill(x - 1, y + 13, x + 2, y + 16, BLACK);
            g.fill(x + 6, y - 3, x + 11, y + 1, BLACK);
            g.fill(x + 4, y, x + 9, y + 6, BLACK);
            g.fill(x + 2, y + 5, x + 7, y + 11, BLACK);
            g.fill(x, y + 10, x + 5, y + 16, BLACK);
            g.fill(x + 7, y - 1, x + 9, y + 2, PALE_GREEN);
            g.fill(x + 5, y + 3, x + 7, y + 7, PALE_GREEN);
            g.fill(x + 3, y + 8, x + 5, y + 11, PALE_GREEN);
            return;
        }
        if (type.equals("ANTMAIL")) {
            g.fill(x - 12, y + 1, x + 12, y + 18, BLACK);
            g.fill(x - 9, y + 4, x + 9, y + 15, GREEN);
            g.fill(x - 9, y + 4, x + 9, y + 6, BLACK);
            g.fill(x - 7, y + 6, x - 4, y + 8, BLACK);
            g.fill(x + 4, y + 6, x + 7, y + 8, BLACK);
            g.fill(x - 4, y + 8, x + 4, y + 10, BLACK);
            g.fill(x - 9, y + 11, x - 6, y + 13, BLACK);
            g.fill(x + 6, y + 11, x + 9, y + 13, BLACK);
            g.fill(x - 6, y + 13, x - 3, y + 15, BLACK);
            g.fill(x + 3, y + 13, x + 6, y + 15, BLACK);
            return;
        }
        if (type.equals("GAMES")) {
            g.fill(x - 7, y + 2, x + 7, y + 5, BLACK);
            g.fill(x - 11, y + 5, x + 11, y + 15, BLACK);
            g.fill(x - 9, y + 15, x - 5, y + 19, BLACK);
            g.fill(x + 5, y + 15, x + 9, y + 19, BLACK);
            g.fill(x - 8, y + 5, x + 8, y + 13, GREEN);
            g.fill(x - 7, y + 8, x - 1, y + 10, BLACK);
            g.fill(x - 5, y + 6, x - 3, y + 12, BLACK);
            g.fill(x + 3, y + 7, x + 5, y + 9, BLACK);
            g.fill(x + 6, y + 10, x + 8, y + 12, BLACK);
            return;
        }
        if (type.equals("ANTAZON")) {
            g.fill(x - 12, y - 3, x - 8, y - 1, BLACK);
            g.fill(x - 9, y - 1, x + 10, y + 2, BLACK);
            g.fill(x - 9, y + 2, x + 11, y + 5, BLACK);
            g.fill(x - 7, y + 5, x + 8, y + 15, BLACK);
            g.fill(x - 4, y + 6, x + 6, y + 12, GREEN);
            g.fill(x - 3, y + 8, x + 5, y + 10, PALE_GREEN);
            g.fill(x - 8, y + 14, x + 8, y + 17, BLACK);
            g.fill(x - 6, y + 17, x - 2, y + 21, BLACK);
            g.fill(x + 4, y + 17, x + 8, y + 21, BLACK);
            g.fill(x - 5, y + 18, x - 3, y + 20, PALE_GREEN);
            g.fill(x + 5, y + 18, x + 7, y + 20, PALE_GREEN);
            return;
        }
        if (type.equals("TRASH")) {
            g.fill(x - 8, y + 3, x + 8, y + 20, BLACK);
            g.fill(x - 11, y + 1, x + 11, y + 4, BLACK);
            g.fill(x - 5, y - 1, x + 5, y + 1, BLACK);
            g.fill(x - 6, y + 4, x + 6, y + 18, GREEN);
            g.fill(x - 3, y + 7, x - 1, y + 16, BLACK);
            g.fill(x + 2, y + 7, x + 4, y + 16, BLACK);
            return;
        }
        g.fill(x - 10, y, x + 10, y + 18, BLACK);
        g.fill(x - 8, y + 2, x + 8, y + 16, GREEN);
    }

    private void renderWindow(GuiGraphics g, Window window, int l, int t, int mouseX, int mouseY, int layer) {
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, layer * 10.0F);
        try {
            renderWindowLayer(g, window, l, t, mouseX, mouseY);
        } finally {
            g.flush();
            g.pose().popPose();
        }
    }

    private void renderWindowLayer(GuiGraphics g, Window window, int l, int t, int mouseX, int mouseY) {
        if (!window.maximized) {
            window.x = Math.max(12, Math.min(WIDTH - window.width - 12, window.x));
            window.y = Math.max(34, Math.min(HEIGHT - window.height - 12, window.y));
        }
        int x = l + windowLocalX(window);
        int y = t + windowLocalY(window);
        int w = windowWidth(window);
        int h = windowHeight(window);
        window.renderWidth = w;
        window.renderHeight = h;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, GREEN);
        g.fill(x, y, x + w, y + h, BLACK);
        g.fill(x, y, x + w, y + TITLE_BAR_HEIGHT, 0xFF173817);
        int controlsLeft = x + w - WINDOW_CONTROL_WIDTH * WINDOW_CONTROL_COUNT;
        String title = window.title;
        if (window.type.equals("ANTMAIL")) {
            AntmailMailbox mailbox = session.antmail.mailbox(AntmailClientState.getMailbox());
            if (mailbox != null) title = mailbox.address().fullAddress();
        }
        g.drawString(font, Component.literal(trimToWidth(title, Math.max(1, controlsLeft - x - 12))), x + 7, y + 6, GREEN, false);
        g.fill(controlsLeft, y, x + w, y + TITLE_BAR_HEIGHT, 0xFF173817);
        g.fill(controlsLeft, y, controlsLeft + 1, y + TITLE_BAR_HEIGHT, GREEN);
        g.fill(controlsLeft + WINDOW_CONTROL_WIDTH, y, controlsLeft + WINDOW_CONTROL_WIDTH + 1, y + TITLE_BAR_HEIGHT, GREEN);
        g.fill(controlsLeft + WINDOW_CONTROL_WIDTH * 2, y, controlsLeft + WINDOW_CONTROL_WIDTH * 2 + 1, y + TITLE_BAR_HEIGHT, GREEN);
        for (int control = 0; control < WINDOW_CONTROL_COUNT; control++) {
            if (hovered(controlsLeft + control * WINDOW_CONTROL_WIDTH, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT)) {
                g.fill(controlsLeft + control * WINDOW_CONTROL_WIDTH, y, controlsLeft + (control + 1) * WINDOW_CONTROL_WIDTH, y + TITLE_BAR_HEIGHT, HOVER_FILL);
            }
        }
        g.drawCenteredString(font, Component.literal("_"), controlsLeft + WINDOW_CONTROL_WIDTH / 2, y + 5, PALE_GREEN);
        g.drawCenteredString(font, Component.literal(window.maximized ? "❐" : "□"), controlsLeft + WINDOW_CONTROL_WIDTH + WINDOW_CONTROL_WIDTH / 2, y + 5, PALE_GREEN);
        g.drawCenteredString(font, Component.literal("X"), controlsLeft + WINDOW_CONTROL_WIDTH * 2 + WINDOW_CONTROL_WIDTH / 2, y + 6, GREEN);
        int cx = x + 8;
        int cy = y + 28;
        enableComputerScissor(g, x + 1, y + TITLE_BAR_HEIGHT, x + w - 1, y + h - 1);
        try {
            if (window.type.equals("ARCHIVE")) session.archive.render(g, cx, cy, w - 16, h - 34, window.maximized);
            else if (window.type.equals("TASKS")) tasks.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("FILES")) session.files.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("SETTINGS")) session.settings.render(g, cx, cy, h - 34);
            else if (window.type.equals("TERMINAL")) session.terminal.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("TEXT")) session.text.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("PAINT")) session.paint.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("ANTMAIL")) session.antmail.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("ANTAZON")) session.antazon.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("GAMES")) games.render(g, window, cx, cy, mouseX, mouseY);
            else if (window.type.equals("WALLPAPERS")) session.wallpaperPicker.render(g, cx, cy, h - 34);
            else trash.render(g, cx, cy);
        } finally {
            g.disableScissor();
        }
    }
















    int renderTaskItemRecipe(GuiGraphics g, String itemId, int x, int y, int w) {
        if (Minecraft.getInstance().level != null) {
            try {
                var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
                if (item != null && Minecraft.getInstance().level.getRecipeManager().getRecipes().stream()
                        .anyMatch(recipe -> recipe.value().getResultItem(Minecraft.getInstance().level.registryAccess()).is(item))) {
                    return renderArchiveRecipe(g, itemId, x, y, w);
                }
            } catch (RuntimeException ignored) { }
        }
        return wrap(g, "NO RECIPE FOUND", x, y, w, 0xFF87B787);
    }

    int renderTaskSectionLabel(GuiGraphics g, String label, int x, int y, int width) {
        g.drawString(font, Component.literal(label), x, y, GREEN, false);
        g.fill(x, y + 10, x + width, y + 11, DARK_GREEN);
        return y + 13;
    }



























    void enableComputerScissor(GuiGraphics g, int left, int top, int right, int bottom) {
        float scale = uiScale();
        int screenLeft = (int) Math.floor(width / 2.0F + left * scale);
        int screenTop = (int) Math.floor(height / 2.0F + top * scale);
        int screenRight = (int) Math.ceil(width / 2.0F + right * scale);
        int screenBottom = (int) Math.ceil(height / 2.0F + bottom * scale);
        g.enableScissor(screenLeft, screenTop, screenRight, screenBottom);
    }

    static int windowLocalX(Window window) { return window.maximized ? 14 : window.x; }
    static int windowLocalY(Window window) { return window.maximized ? 34 : window.y; }
    static int windowWidth(Window window) { return window.maximized ? WIDTH - 28 : window.width; }
    static int windowHeight(Window window) { return window.maximized ? HEIGHT - 48 : window.height; }

    private static void toggleMaximized(Window window) {
        if (window.maximized) {
            window.maximized = false;
            window.x = window.restoreX;
            window.y = window.restoreY;
        } else {
            window.restoreX = window.x;
            window.restoreY = window.y;
            window.maximized = true;
        }
    }






    int renderArchiveRecipe(GuiGraphics g, String recipeId, int x, int y, int w) {
        int slot = 16;
        int recipeWidth = slot * 3 + 24;
        if (!renderArchiveRecipe(g, recipeId, x, y, Math.min(w, recipeWidth), slot)) {
            return wrap(g, "RECIPE // " + recipeId, x, y, w, PALE_GREEN);
        }
        return y + slot * 3;
    }

    boolean renderArchiveRecipe(GuiGraphics g, String recipeId, int x, int y, int width, int slot) {
        if (Minecraft.getInstance().level == null) return false;
        try {
            var recipeManager = Minecraft.getInstance().level.getRecipeManager();
            ResourceLocation requestedId = ResourceLocation.parse(recipeId);
            RecipeHolder<?> holder = recipeManager.byKey(requestedId).orElse(null);
            if (holder == null) {
                var requestedItem = BuiltInRegistries.ITEM.getOptional(requestedId).orElse(null);
                if (requestedItem != null && requestedItem != Items.AIR) {
                    holder = recipeManager.getRecipes().stream()
                            .filter(candidate -> candidate.value().getResultItem(Minecraft.getInstance().level.registryAccess()).is(requestedItem))
                            .findFirst().orElse(null);
                }
            }
            if (holder == null) return false;
            var recipe = holder.value();
            List<Ingredient> ingredients = recipe.getIngredients();
            int actualSlot = Math.min(slot, Math.max(4, (width - 8) / 4));
            int gridWidth = actualSlot * 3;
            int outputX = x + gridWidth + 8;
            int outputY = y + actualSlot;
            int shapedWidth = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            int shapedHeight = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : Math.min(3, (ingredients.size() + 2) / 3);
            for (int index = 0; index < 9; index++) {
                int column = index % 3;
                int row = index / 3;
                int cellX = x + column * actualSlot;
                int cellY = y + row * actualSlot;
                g.fill(cellX, cellY, cellX + actualSlot - 1, cellY + actualSlot - 1, 0xFF173817);
                int ingredientIndex = recipe instanceof ShapedRecipe
                        ? (column < shapedWidth && row < shapedHeight ? row * shapedWidth + column : -1)
                        : index;
                if (ingredientIndex >= 0 && ingredientIndex < ingredients.size()) {
                    ItemStack[] choices = ingredients.get(ingredientIndex).getItems();
                    if (choices.length > 0) renderArchiveItem(g, choices[0], cellX, cellY, actualSlot);
                }
            }
            g.fill(x + gridWidth + 2, y + actualSlot + actualSlot / 2 - 1, x + gridWidth + 6, y + actualSlot + actualSlot / 2 + 1, PALE_GREEN);
            g.fill(x + gridWidth + 5, y + actualSlot + actualSlot / 2 - 3, x + gridWidth + 7, y + actualSlot + actualSlot / 2 + 3, PALE_GREEN);
            ItemStack result = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
            if (!result.isEmpty()) {
                g.fill(outputX, outputY, outputX + actualSlot - 1, outputY + actualSlot - 1, 0xFF173817);
                renderArchiveItem(g, result, outputX, outputY, actualSlot);
            }
            return true;
        } catch (RuntimeException exception) {
            logArchivePreviewOnce("recipe.error." + recipeId, "Archive recipe preview failed id={} error={}", recipeId, exception.toString());
            return false;
        }
    }

    int renderArchivePreview(GuiGraphics g, String itemId, String entityId, String enchantmentId, int x, int y, int w) {
        return renderArchivePreview(g, itemId, entityId, enchantmentId, x, y, w, 0.0F, 1.0F);
    }

    int renderArchivePreview(GuiGraphics g, String itemId, String entityId, String enchantmentId, int x, int y, int w, float rotation) {
        return renderArchivePreview(g, itemId, entityId, enchantmentId, x, y, w, rotation, 1.0F);
    }

    int renderArchivePreview(GuiGraphics g, String itemId, String entityId, String enchantmentId, int x, int y, int w, float rotation, float renderScale) {
        int previewSize = entityId.isBlank() ? 48 : 96;
        int boxWidth = Math.min(previewSize, w);
        g.fill(x, y, x + boxWidth, y + previewSize, 0xFF102010);
        int centerX = x + boxWidth / 2;
        int centerY = y + previewSize / 2;
        if (!renderArchiveAsset(g, itemId, entityId, enchantmentId, "", centerX, centerY, boxWidth, rotation, renderScale)) {
            String fallback = !enchantmentId.isBlank() ? enchantmentId : entityId;
            if (!fallback.isBlank()) g.drawString(font, Component.literal(trimToWidth(fallback, Math.max(1, w - boxWidth - 6))), x + boxWidth + 6, y + previewSize / 2 - 4, PALE_GREEN, false);
        } else if (!entityId.isBlank() || !enchantmentId.isBlank()) {
            String label = !enchantmentId.isBlank() ? "ENCHANTMENT // " + enchantmentId : "MOB // " + archiveMobName(entityId);
            g.drawString(font, Component.literal(trimToWidth(label, Math.max(1, w - boxWidth - 6))), x + boxWidth + 6, y + previewSize / 2 - 4, PALE_GREEN, false);
        }
        return y + previewSize;
    }

    private String archiveMobName(String entityId) {
        int separator = entityId.indexOf(':');
        return (separator >= 0 ? entityId.substring(separator + 1) : entityId).replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    boolean renderArchiveAsset(GuiGraphics g, String itemId, String entityId, String enchantmentId, String potionId, int centerX, int centerY, int size, float rotation, float renderScale) {
        return renderArchiveAsset(g, itemId, entityId, enchantmentId, potionId, centerX, centerY, size, rotation, renderScale, true);
    }

    boolean renderArchiveAsset(GuiGraphics g, String itemId, String entityId, String enchantmentId, String potionId, int centerX, int centerY, int size, float rotation, float renderScale, boolean renderMobFromSpawnEgg) {
        if (archivePreviewLevel != Minecraft.getInstance().level) {
            archivePreviewLevel = Minecraft.getInstance().level;
            archiveEntityPreviews.clear();
            unavailableArchiveEntities.clear();
            archiveItemStacks.clear();
        }
        if (!itemId.isBlank()) {
            try {
                ItemStack stack = archiveItemStacks.get(itemId);
                if (stack == null) {
                    var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
                    if (LOG_ARCHIVE_PREVIEWS) logArchivePreviewOnce("item.lookup." + itemId, "Archive item preview lookup id={} found={} registryName={}", itemId, item != null, item == null ? "<missing>" : BuiltInRegistries.ITEM.getKey(item));
                    stack = item == null || item.equals(net.minecraft.world.item.Items.AIR) ? ItemStack.EMPTY : new ItemStack(item);
                    archiveItemStacks.put(itemId, stack);
                }
                if (!stack.isEmpty()) {
                    if (renderMobFromSpawnEgg && stack.getItem() instanceof SpawnEggItem spawnEgg) {
                        ResourceLocation spawnedEntity = BuiltInRegistries.ENTITY_TYPE.getKey(spawnEgg.getType(stack));
                        if (renderArchiveAsset(g, "", spawnedEntity.toString(), "", potionId, centerX, centerY, size, rotation, renderScale)) return true;
                    }
                    int iconSize = Math.min(32, Math.max(16, size - 4));
                    renderArchiveItem(g, stack, centerX - iconSize / 2, centerY - iconSize / 2, iconSize);
                    if (LOG_ARCHIVE_PREVIEWS) logArchivePreviewOnce("item.render." + itemId, "Archive item preview rendered id={} descriptionId={} center=({}, {})", itemId, stack.getDescriptionId(), centerX, centerY);
                    return true;
                }
                logArchivePreviewOnce("item.unrenderable." + itemId, "Archive item preview unavailable id={} (missing or AIR)", itemId);
            } catch (RuntimeException exception) {
                archiveItemStacks.put(itemId, ItemStack.EMPTY);
                logArchivePreviewOnce("item.error." + itemId, "Archive item preview failed id={} error={}", itemId, exception.toString());
            }
        }
        if (!enchantmentId.isBlank()) {
            ItemStack stack = createArchiveEnchantedBook(enchantmentId);
            if (!stack.isEmpty()) {
                int iconSize = Math.min(32, Math.max(16, size - 4));
                renderArchiveItem(g, stack, centerX - iconSize / 2, centerY - iconSize / 2, iconSize);
                logArchivePreviewOnce("enchantment.render." + enchantmentId, "Archive enchantment preview rendered id={}", enchantmentId);
                return true;
            }
            logArchivePreviewOnce("enchantment.unrenderable." + enchantmentId, "Archive enchantment preview unavailable id={}", enchantmentId);
        }
        if (!potionId.isBlank() && Minecraft.getInstance().level != null) {
            try {
                ResourceLocation potionLocation = ResourceLocation.parse(potionId);
                ResourceKey<Potion> potionKey = ResourceKey.create(Registries.POTION, potionLocation);
                var potion = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.POTION).get(potionKey).orElse(null);
                if (potion != null) {
                    int iconSize = Math.min(32, Math.max(16, size - 4));
                    renderArchiveItem(g, PotionContents.createItemStack(Items.POTION, potion), centerX - iconSize / 2, centerY - iconSize / 2, iconSize);
                    return true;
                }
            } catch (RuntimeException exception) {
                logArchivePreviewOnce("potion.error." + potionId, "Archive potion preview failed id={} error={}", potionId, exception.toString());
            }
        }
        if (!entityId.isBlank() && Minecraft.getInstance().level == null) {
            logArchivePreviewOnce("entity.no_level." + entityId, "Archive mob preview skipped id={} because the client level is null", entityId);
        }
        if (!entityId.isBlank() && Minecraft.getInstance().level != null) {
            try {
                ResourceLocation resourceId = ResourceLocation.parse(entityId);
                var entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(resourceId).orElse(null);
                if (LOG_ARCHIVE_PREVIEWS) logArchivePreviewOnce("entity.lookup." + entityId, "Archive mob preview lookup id={} found={} registryName={}", entityId, entityType != null, entityType == null ? "<missing>" : BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
                LivingEntity living = archiveEntityPreviews.get(entityId);
                if (living == null && entityType != null && !unavailableArchiveEntities.contains(entityId)) {
                    var entity = entityType.create(Minecraft.getInstance().level);
                    if (entity instanceof LivingEntity created && !created.isRemoved()) {
                        ArchiveEntityPreviewRegistry.configure(resourceId, created);
                        living = created;
                        archiveEntityPreviews.put(entityId, created);
                    } else {
                        unavailableArchiveEntities.add(entityId);
                        logArchivePreviewOnce("entity.unrenderable." + entityId, "Archive mob preview unavailable id={} createdClass={} isLiving={}", entityId, entity == null ? "<null>" : entity.getClass().getName(), entity instanceof LivingEntity);
                    }
                }
                if (living != null && !living.isRemoved()) {
                    living.setPos(0.0D, 0.0D, 0.0D);
                    float yaw = 180.0F + rotation;
                    living.setYRot(yaw);
                    living.yRotO = yaw;
                    living.yBodyRot = yaw;
                    living.yBodyRotO = yaw;
                    living.yHeadRot = yaw;
                    living.yHeadRotO = yaw;
                    living.tickCount = (int) (Minecraft.getInstance().level.getGameTime() & Integer.MAX_VALUE);
                    float largestDimension = Math.max(0.45F, Math.max(living.getBbWidth(), living.getBbHeight()));
                    int entityRenderScale = Math.max(4, Math.min(size, Math.round(size * 0.52F * renderScale / largestDimension)));
                    float entityScale = Math.max(0.01F, living.getScale());
                    Vector3f translation = new Vector3f(0.0F, living.getBbHeight() / 2.0F + 0.0625F * entityScale, 0.0F);
                    Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
                    Quaternionf camera = new Quaternionf();
                    g.flush();
                    enableComputerScissor(g, centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2);
                    if (archiveGreenTint) g.setColor(0.0F, 1.0F, 0.0F, 1.0F);
                    try {
                        InventoryScreen.renderEntityInInventory(g, centerX, centerY, entityRenderScale / entityScale,
                                translation, pose, camera, living);
                        g.flush();
                    } finally {
                        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                        g.disableScissor();
                    }
                    if (LOG_ARCHIVE_PREVIEWS) logArchivePreviewOnce("entity.render." + entityId, "Archive mob preview rendered id={} class={} size={} center=({}, {})", entityId, living.getClass().getName(), size, centerX, centerY);
                    return true;
                }
            } catch (RuntimeException exception) {
                logArchivePreviewOnce("entity.error." + entityId, "Archive mob preview failed id={} error={}", entityId, exception.toString());
            }
        }
        return false;
    }

    private void renderArchiveItem(GuiGraphics g, ItemStack stack, int x, int y) {
        renderArchiveItem(g, stack, x, y, 16);
    }

    private void renderArchiveItem(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        // Only the tint needs queued fills drawn first; renderItem flushes its own batch.
        if (archiveGreenTint) {
            g.flush();
            g.setColor(0.0F, 1.0F, 0.0F, 1.0F);
        }
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0.0F);
            float scale = size / 16.0F;
            g.pose().scale(scale, scale, 1.0F);
            g.renderItem(stack, 0, 0);
        } finally {
            g.pose().popPose();
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private ItemStack createArchiveEnchantedBook(String enchantmentId) {
        if (Minecraft.getInstance().level == null) return ItemStack.EMPTY;
        try {
            ResourceLocation id = ResourceLocation.parse(enchantmentId);
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
            var holder = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
            if (holder == null) return ItemStack.EMPTY;
            ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchantments.set(holder, 1);
            ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
            stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable().withTooltip(true));
            return stack;
        } catch (RuntimeException exception) {
            logArchivePreviewOnce("enchantment.error." + enchantmentId, "Archive enchantment preview failed id={} error={}", enchantmentId, exception.toString());
            return ItemStack.EMPTY;
        }
    }

    private void logArchivePreviewOnce(String key, String message, Object... arguments) {
        if (archiveRenderLog.add(key)) AntOS.LOGGER.info(message, arguments);
    }













    String parentDirectory(String path) {
        if (path.equals("/")) return "/";
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    String trimToWidth(String value, int width) {
        if (font.width(value) <= width) return value;
        String ellipsis = "...";
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width(ellipsis))) + ellipsis;
    }





    boolean faceIdLinkedToCurrentAccount() {
        var current = AnternetAccountClientState.latest();
        java.util.UUID linkedAccount = AnternetAccountClientState.faceIdAccountId();
        return current != null && current.result() == AnternetAccountResultPayload.SUCCESS
                && linkedAccount != null && linkedAccount.equals(current.accountId());
    }





    void drawScrollbar(GuiGraphics g, int x, int top, int trackHeight, int visible, int total, int scroll, int maximum) {
        if (maximum <= 0 || total <= 0) return;
        int safeTrackHeight = Math.max(1, trackHeight);
        int safeMaximum = Math.max(1, maximum);
        int safeScroll = Math.max(0, Math.min(scroll, safeMaximum));
        int thumbHeight = scrollbarThumbHeight(safeTrackHeight, visible, total);
        int thumbTop = top + (safeTrackHeight - thumbHeight) * safeScroll / safeMaximum;
        g.fill(x, top, x + 3, top + safeTrackHeight, 0xFF173817);
        g.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, GREEN);
    }

    int scrollbarThumbHeight(int trackHeight, int visible, int total) {
        int safeTrackHeight = Math.max(1, trackHeight);
        int safeTotal = Math.max(1, total);
        int minimumThumbHeight = Math.min(12, safeTrackHeight);
        return Math.min(safeTrackHeight, Math.max(minimumThumbHeight, safeTrackHeight * Math.max(1, visible) / safeTotal));
    }








    boolean caretVisible() {
        return (System.currentTimeMillis() / 500L) % 2L == 0L;
    }



    String insertCaret(String value, int cursor) {
        int index = Math.max(0, Math.min(cursor, value.length()));
        return value.substring(0, index) + "|" + value.substring(index);
    }






    void drawTabLabel(GuiGraphics g, String label, int x, int y, int width, int color) {
        drawTabLabel(g, label, x, y, width, color, tabLabelScale(label, width));
    }

    float tabLabelScale(String label, int width) {
        return Math.min(1.0F, (width - 10) / (float) Math.max(1, font.width(label)));
    }

    void drawTabLabel(GuiGraphics g, String label, int x, int y, int width, int color, float scale) {
        if (width <= 10) return;
        scale = Math.min(scale, tabLabelScale(label, width));
        g.pose().pushPose();
        try {
            g.pose().translate(x + width / 2.0F, y + 10.0F - 4.0F * scale, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, Component.literal(label), -font.width(label) / 2, 0, color, false);
        } finally {
            g.pose().popPose();
        }
    }



    void drawFilterButton(GuiGraphics g, int x, int y, int width, boolean selected, String label) {
        boolean hover = hovered(x, y, width, 17);
        g.fill(x, y, x + width, y + 17, selected ? PALE_GREEN : hover ? HOVER_FILL : DARK_GREEN);
        box(g, x, y, x + width, y + 17, selected ? GREEN : 0xFF315531);
        float scale = Math.min(0.82F, Math.max(0.55F, (width - 8.0F) / Math.max(1, font.width(label))));
        g.pose().pushPose();
        try {
            g.pose().translate(x + width / 2.0F, y + 4.0F, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, Component.literal(label), -font.width(label) / 2, 0,
                    selected ? BLACK : GREEN, false);
        } finally {
            g.pose().popPose();
        }
    }



















    int wrap(GuiGraphics g, String text, int x, int y, int width, int color) { return wrap(g, Component.literal(text), x, y, width, color); }

    int wrap(GuiGraphics g, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) { g.drawString(font, line, x, y, color, false); y += 11; }
        return y;
    }

    int wrapLimited(GuiGraphics g, String text, int x, int y, int width, int bottom, int color) {
        for (var line : font.split(Component.literal(text), width)) {
            if (y + 11 > bottom) break;
            g.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }

    void drawBottomWrapped(GuiGraphics g, String text, int x, int y, int h, int width, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), width);
        int lineY = y + h - 16 - Math.max(0, lines.size() - 1) * 11;
        for (var line : lines) {
            g.drawString(font, line, x, lineY, color, false);
            lineY += 11;
        }
    }

    void box(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 2, color);
        g.fill(x1, y2 - 2, x2, y2, color);
        g.fill(x1, y1, x1 + 2, y2, color);
        g.fill(x2 - 2, y1, x2, y2, color);
    }

    boolean hovered(int x, int y, int width, int height) {
        return inside(x, y, width, height, session.mouseX, session.mouseY);
    }

    void drawHover(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, HOVER_FILL);
        box(g, x, y, x + width, y + height, GREEN);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float scale = uiScale();
        mouseX = (mouseX - width / 2.0F) / scale;
        mouseY = (mouseY - height / 2.0F) / scale;
        int l = -WIDTH / 2;
        int t = -HEIGHT / 2;
        if (!loggedIn) {
            if (button != 0) return true;
            int buttonX = l + WIDTH / 2 - 70;
            if (inside(buttonX, t + 70, 66, 20, mouseX, mouseY)) {
                creatingAccount = false;
                accountMessage = "SIGN IN TO YOUR ANTERNET ACCOUNT";
            } else if (inside(buttonX + 74, t + 70, 66, 20, mouseX, mouseY)) {
                creatingAccount = true;
                accountMessage = "CHOOSE A USERNAME AND PASSWORD";
            } else if (inside(l + 28, t + 109, WIDTH - 56, 23, mouseX, mouseY)) accountPasswordFocused = false;
            else if (inside(l + 28, t + 150, WIDTH - 56, 23, mouseX, mouseY)) accountPasswordFocused = true;
            else if (inside(buttonX, t + 184, 140, 22, mouseX, mouseY)) submitAccount();
            return true;
        }
        session.lastUse = System.currentTimeMillis();
        if (!session.onboardingCompleted) return onboardingClicked(mouseX, mouseY);
        for (int i = session.windows.size() - 1; i >= 0; i--) {
            Window window = session.windows.get(i);
            if (window.minimized) continue;
            int x = l + windowLocalX(window);
            int y = t + windowLocalY(window);
            int w = windowWidth(window);
            int h = windowHeight(window);
            int contentX = x + 8;
            int contentY = y + 28;
            int contentW = w - 16;
            int contentH = h - 34;
            if (inside(x, y + TITLE_BAR_HEIGHT, w, h - TITLE_BAR_HEIGHT, mouseX, mouseY)) {
                activeWindow = window;
                session.windows.remove(i);
                session.windows.add(window);
                return switch (window.type) {
                    case "TASKS" -> tasks.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "ARCHIVE" -> session.archive.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "FILES" -> session.files.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "PAINT" -> session.paint.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "TEXT" -> session.text.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "ANTMAIL" -> session.antmail.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "ANTAZON" -> session.antazon.click(mouseX, mouseY);
                    case "GAMES" -> games.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "WALLPAPERS" -> session.wallpaperPicker.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "TERMINAL" -> session.terminal.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "SETTINGS" -> session.settings.click(window, mouseX, mouseY, button, x, y, w, h);
                    default -> true;
                };
            }
            if (inside(x, y + TITLE_BAR_HEIGHT, w, h - TITLE_BAR_HEIGHT, mouseX, mouseY)) return true;
            if (inside(x, y, w, TITLE_BAR_HEIGHT, mouseX, mouseY)) {
                activeWindow = window;
                session.windows.remove(i);
                session.windows.add(window);
                int controlsLeft = x + w - WINDOW_CONTROL_WIDTH * WINDOW_CONTROL_COUNT;
                if (inside(controlsLeft + WINDOW_CONTROL_WIDTH * 2, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT, mouseX, mouseY)) {
                    if (window.type.equals("ANTMAIL")) session.antmail.windowClosed();
                    session.windows.remove(i);
                    if (activeWindow == window) activeWindow = null;
                }
                else if (inside(controlsLeft + WINDOW_CONTROL_WIDTH, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT, mouseX, mouseY)) toggleMaximized(window);
                else if (inside(controlsLeft, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT, mouseX, mouseY)) {
                    window.minimized = true;
                    if (activeWindow == window) activeWindow = null;
                }
                else if (!window.maximized) { dragging = window; dragX = (int) mouseX - x; dragY = (int) mouseY - y; }
                return true;
            }
        }
        List<String> desktopApps = desktopApps();
        for (int i = 0; i < desktopApps.size(); i++) {
            int x = desktopIconX(l, i);
            int y = desktopIconY(t, i);
            if (inside(x - 8, y - 8, 84, 61, mouseX, mouseY)) { open(desktopApps.get(i)); return true; }
        }
        return true;
    }

    private boolean onboardingClicked(double mouseX, double mouseY) {
        List<String> apps = onboardingApps();
        int panelX = -WIDTH / 2 + 30;
        int panelY = -HEIGHT / 2 + 43;
        int appStep = session.onboardingStep - 1;
        boolean welcome = session.onboardingStep == 0;
        int contentX = panelX + 22;
        int actionY = panelY + 185;
        if (!welcome && appStep >= 0 && appStep < apps.size()) {
            int desktopIndex = desktopApps().indexOf(apps.get(appStep));
            panelX = onboardingAppPanelX(-WIDTH / 2, desktopIndex);
            panelY = onboardingAppPanelY(-HEIGHT / 2, desktopIndex);
            contentX = panelX + 10;
            actionY = panelY + 121;
        }
        if (welcome) {
            if (inside(contentX, actionY, 126, 22, mouseX, mouseY)) {
                if (apps.isEmpty()) finishOnboarding();
                else session.onboardingStep = 1;
            } else if (inside(panelX + 250, actionY, 88, 22, mouseX, mouseY)) {
                finishOnboarding();
            }
            return true;
        }
        if (appStep < 0 || appStep >= apps.size()) {
            finishOnboarding();
            return true;
        }
        if (appStep > 0 && inside(contentX, actionY, 82, 22, mouseX, mouseY)) {
            session.onboardingStep--;
        } else if (inside(panelX + 112, actionY, 68, 22, mouseX, mouseY)) {
            if (appStep + 1 < apps.size()) session.onboardingStep++;
            else finishOnboarding();
        }
        return true;
    }

    private void finishOnboarding() {
        session.onboardingCompleted = true;
        session.onboardingStep = -1;
        var account = com.craisinlord.antos.content.client.AnternetAccountClientState.latest();
        if (account != null && account.result() == com.craisinlord.antos.content.network.AnternetAccountResultPayload.SUCCESS) {
            AntOSSettings.setComputerTourCompleted(account.accountId(), true);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        float scale = uiScale();
        mouseX = (mouseX - width / 2.0F) / scale;
        mouseY = (mouseY - height / 2.0F) / scale;
        tasks.drag(mouseX, mouseY, button);
        session.archive.drag(mouseY);
        session.settings.drag(mouseY);
        if (dragging != null) {
            int l = -WIDTH / 2;
            int t = -HEIGHT / 2;
            dragging.x = Math.max(12, Math.min(WIDTH - dragging.renderWidth - 12, (int) mouseX - l - this.dragX));
            dragging.y = Math.max(34, Math.min(HEIGHT - dragging.renderHeight - 12, (int) mouseY - t - this.dragY));
        }
        if (activeWindow != null && activeWindow.type.equals("PAINT")) session.paint.drag(activeWindow, mouseX, mouseY, button);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        float scale = uiScale();
        mouseX = (mouseX - width / 2.0F) / scale;
        mouseY = (mouseY - height / 2.0F) / scale;
        dragging = null;
        tasks.release();
        session.archive.release();
        session.settings.release();
        session.paint.release(button);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Window hoveredWindow = windowAt(mouseX, mouseY);
        if (!loggedIn || hoveredWindow == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        activeWindow = hoveredWindow;
        boolean handled = switch (activeWindow.type) {
            case "FILES" -> session.files.scroll(activeWindow, mouseX, mouseY, scrollY);
            case "TASKS" -> tasks.scroll(activeWindow, mouseX, mouseY, scrollY);
            case "TEXT" -> session.text.scroll(activeWindow, mouseX, mouseY, scrollY);
            case "TERMINAL" -> session.terminal.scroll(activeWindow, mouseX, mouseY, scrollY);
            case "ANTMAIL" -> session.antmail.scroll(activeWindow, mouseX, mouseY, scrollY);
            case "ANTAZON" -> {
                float scale = uiScale();
                yield session.antazon.scroll((mouseX - width / 2.0F) / scale, (mouseY - height / 2.0F) / scale, scrollY);
            }
            case "SETTINGS" -> session.settings.scroll(activeWindow, mouseX, mouseY, scrollY);
            case "ARCHIVE" -> session.archive.scroll(activeWindow, mouseX, mouseY, scrollY);
            case "WALLPAPERS" -> session.wallpaperPicker.scroll(activeWindow, mouseX, mouseY, scrollY);
            default -> false;
        };
        return handled || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!loggedIn) {
            if (accountPasswordFocused) {
                if (codePoint >= 32 && accountPassword.length() < 128) accountPassword += codePoint;
            } else if ((Character.isLetterOrDigit(codePoint) || codePoint == '_') && accountUsername.length() < 16) accountUsername += codePoint;
            return true;
        }
        if (activeWindow == null) return true;
        switch (activeWindow.type) {
            case "TASKS" -> tasks.type(activeWindow, codePoint, modifiers);
            case "FILES" -> session.files.type(activeWindow, codePoint, modifiers);
            case "ARCHIVE" -> session.archive.type(activeWindow, codePoint, modifiers);
            case "TEXT" -> session.text.type(activeWindow, codePoint, modifiers);
            case "ANTMAIL" -> session.antmail.type(activeWindow, codePoint, modifiers);
            case "GAMES" -> games.type(activeWindow, codePoint, modifiers);
            case "TERMINAL" -> session.terminal.type(activeWindow, codePoint, modifiers);
            case "ANTAZON" -> session.antazon.type(codePoint);
            default -> { }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!loggedIn) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
            if (keyCode == GLFW.GLFW_KEY_TAB) accountPasswordFocused = !accountPasswordFocused;
            else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (accountPasswordFocused && !accountPassword.isEmpty()) accountPassword = accountPassword.substring(0, accountPassword.length() - 1);
                else if (!accountPasswordFocused && !accountUsername.isEmpty()) accountUsername = accountUsername.substring(0, accountUsername.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) submitAccount();
            return true;
        }
        if (!session.onboardingCompleted) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishOnboarding();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                List<String> apps = onboardingApps();
                if (session.onboardingStep == 0) {
                    if (apps.isEmpty()) finishOnboarding();
                    else session.onboardingStep = 1;
                } else if (session.onboardingStep <= apps.size()) {
                    if (session.onboardingStep < apps.size()) session.onboardingStep++;
                    else finishOnboarding();
                }
                else finishOnboarding();
                return true;
            }
            return true;
        }
        boolean handled = activeWindow != null && switch (activeWindow.type) {
            case "TASKS" -> tasks.key(activeWindow, keyCode, scanCode, modifiers);
            case "GAMES" -> games.key(activeWindow, keyCode, scanCode, modifiers);
            case "FILES" -> session.files.key(activeWindow, keyCode, scanCode, modifiers);
            case "TERMINAL" -> session.terminal.key(activeWindow, keyCode, scanCode, modifiers);
            case "ANTMAIL" -> session.antmail.key(activeWindow, keyCode, scanCode, modifiers);
            case "TEXT" -> session.text.key(activeWindow, keyCode, scanCode, modifiers);
            case "ARCHIVE" -> session.archive.key(activeWindow, keyCode, scanCode, modifiers);
            case "ANTAZON" -> session.antazon.key(keyCode);
            default -> false;
        };
        return handled || super.keyPressed(keyCode, scanCode, modifiers);
    }














































    int mouseX() {
        return session.mouseX;
    }























    boolean hasControl(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    List<ResourceLocation> workspaceDiskIds() {
        List<ResourceLocation> disks = new ArrayList<>();
        disks.add(ResourceLocation.fromNamespaceAndPath("antos", "introduction"));
        disks.addAll(com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.installedDisks());
        return List.copyOf(disks);
    }

    List<ResourceLocation> physicalDisks() {
        return com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.installedDisks();
    }








    void open(String type) {
        if (!AntOSSettings.appEnabled(type)) return;
        for (int index = 0; index < session.windows.size(); index++) {
            Window window = session.windows.get(index);
            if (!window.type.equals(type)) continue;
            window.minimized = false;
            session.windows.remove(index);
            session.windows.add(window);
            activeWindow = window;
            if (type.equals("FILES")) ComputerNetworking.listFiles();
              else if (type.equals("ANTAZON")) session.antazon.refresh();
            return;
        }
        Window window = new Window(type, TITLES.get(type), 112 + session.windows.size() * 12, 52 + session.windows.size() * 10);
        session.windows.add(window);
        activeWindow = window;
        if (type.equals("ANTMAIL")) session.antmail.requestState();
        else if (type.equals("ANTAZON")) session.antazon.refresh();
        else if (type.equals("TASKS")) tasks.opened();
        else if (type.equals("FILES")) ComputerNetworking.listFiles();
    }

    private Window windowAt(double mouseX, double mouseY) {
        float scale = uiScale();
        double localMouseX = (mouseX - width / 2.0F) / scale;
        double localMouseY = (mouseY - height / 2.0F) / scale;
        int left = -WIDTH / 2;
        int top = -HEIGHT / 2;
        for (int index = session.windows.size() - 1; index >= 0; index--) {
            Window window = session.windows.get(index);
            if (window.minimized) continue;
            int windowX = left + windowLocalX(window);
            int windowY = top + windowLocalY(window);
            int renderedWidth = windowWidth(window);
            int renderedHeight = windowHeight(window);
            if (inside(windowX, windowY + TITLE_BAR_HEIGHT, renderedWidth, renderedHeight - TITLE_BAR_HEIGHT, localMouseX, localMouseY)) return window;
        }
        return null;
    }



    static boolean inside(int x, int y, int w, int h, double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        ComputerNetworking.close();
        com.craisinlord.antos.content.network.AnternetAccountNetworking.logout();
        com.craisinlord.antos.content.client.AnternetAccountClientState.clear();
        AntmailClientState.clear();
        super.onClose();
    }

    static final class Session {
        boolean authenticated;
        long lastUse;
        final List<Window> windows = new ArrayList<>();
        int archiveMouseX;
        int archiveMouseY;
        int mouseX;
        int mouseY;
        String wallpaperId = ComputerDesktopState.DEFAULT_WALLPAPER.toString();
        List<String> wallpapers = List.of(ComputerDesktopState.DEFAULT_WALLPAPER.toString());
        final AntazonApp antazon = new AntazonApp();
        final TerminalApp terminal = new TerminalApp();
        final WallpapersApp wallpaperPicker = new WallpapersApp();
        final SettingsApp settings = new SettingsApp();
        final TextEditorApp text = new TextEditorApp();
        final PaintApp paint = new PaintApp();
        final FileExplorerApp files = new FileExplorerApp();
        final ArchiveApp archive = new ArchiveApp();
        final AntmailApp antmail = new AntmailApp();
        boolean desktopRequested;
        int bootTicks = -1;
        boolean onboardingCompleted;
        int onboardingStep;

        void attach(ComputerScreen screen) {
            for (ComputerApp app : List.of(antazon, terminal, wallpaperPicker, settings, text, paint, files, archive, antmail)) app.attach(screen);
        }
    }

    static final class Window {
        final String type;
        final String title;
        final int width;
        final int height;
        int x;
        int y;
        int restoreX;
        int restoreY;
        int renderWidth;
        int renderHeight;
        boolean minimized;
        boolean maximized;
        boolean gameOpen;
        String gameId = "";

        Window(String type, String title, int x, int y) {
            this.type = type;
            this.title = title;
            this.height = type.equals("ANTAZON") || type.equals("SETTINGS") ? 250 : 210;
            this.renderHeight = this.height;
            this.width = type.equals("TASKS") || type.equals("ANTAZON") ? 390 : 230;
            this.renderWidth = this.width;
            this.x = x;
            this.y = y;
            this.restoreX = x;
            this.restoreY = y;
        }
    }
}
