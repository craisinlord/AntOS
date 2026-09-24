package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.client.ComputerAccessClientState;
import com.craisinlord.antos.content.client.AntazonClientState;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.AnternetAccountClientState;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailMailbox;
import com.craisinlord.antos.content.antmail.AntmailMessage;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Base64;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public final class ComputerScreen extends Screen {
    private static final int TASK_NODE_SIZE = 32;
    private static final int TASK_NODE_GRID = 56;
    private static final int WIDTH = 440;
    private static final int HEIGHT = 286;
    private static final int GREEN = 0xFF65FF65;
    private static final int PALE_GREEN = 0xFFB8FFB8;
    private static final int DARK_GREEN = 0xFF102010;
    private static final int BLACK = 0xFF030603;
    private static final int HOVER_FILL = 0xFF173817;
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
    private final Session session;
    private boolean loggedIn;
    private String loginMessage = "";
    private int loginMessageTicks;
    private String accountUsername = AntOSSettings.lastAnternetUsername();
    private String accountPassword = "";
    private boolean creatingAccount;
    private boolean accountPasswordFocused;
    private boolean accountWaiting;
    private long faceIdStatusAtOpen;
    private boolean faceIdAutoLoginStarted;
    private String accountMessage = "SIGN IN OR CREATE AN ACCOUNT";
    private String faceIdMessage = "";
    private boolean faceIdWaiting;
    private long faceIdRevisionAtRequest;
    private boolean faceIdExpectedLinked;
    private ComputerAccessResultPayload accessResult;
    private int observedResult = -1;
    private Window dragging;
    private Window archiveScrollbarDragging;
    private boolean paintStrokeActive;
    private int paintStrokeButton = -1;
    private int lastPaintPixelX = -1;
    private int lastPaintPixelY = -1;
    private ResourceLocation selectedDisk;
    private int dragX;
    private int dragY;
    private Window activeWindow;
    private final Map<ResourceLocation, ComputerGame.Session> gameSessions = new HashMap<>();
    private final Set<String> archiveRenderLog = new HashSet<>();
    private int taskRefreshTicks;
    private int taskMapScrollX;
    private int taskMapScrollY;
    private int taskCategoryScroll;
    private int taskDetailScroll;
    private String selectedTaskCategory = "";
    private String selectedTaskId = "";
    private String taskArchiveCacheId = "";
    private Set<ResourceLocation> taskArchiveCacheDisks = Set.of();
    private Set<ResourceLocation> taskArchiveCacheUnlocks = Set.of();
    private Set<ResourceLocation> taskArchiveCacheVisible = Set.of();
    private boolean archiveGreenTint = true;
    private final List<TaskArchiveButton> taskArchiveButtons = new ArrayList<>();
    private final Map<String, LivingEntity> archiveEntityPreviews = new HashMap<>();
    private final Set<String> unavailableArchiveEntities = new HashSet<>();

    public ComputerScreen() {
        super(Component.translatable("screen.antos.computer"));
        var accountResult = com.craisinlord.antos.content.client.AnternetAccountClientState.latest();
        this.sessionKey = com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of();
        this.session = SESSIONS.computeIfAbsent(sessionKey, ignored -> new Session());
        this.loggedIn = accountResult != null && accountResult.result() == com.craisinlord.antos.content.network.AnternetAccountResultPayload.SUCCESS;
        if (!loggedIn || AntOSSettings.hasCompletedComputerTour(accountResult.accountId())) {
            this.session.onboardingCompleted = true;
            this.session.onboardingStep = -1;
        } else {
            this.session.onboardingCompleted = false;
            this.session.onboardingStep = 0;
        }
        this.session.antmailPickingAttachment = false;
        this.session.windows.removeIf(window -> window.type.equals("GAMES") && window.gameOpen);
        if (this.session.bootTicks < 0) this.session.bootTicks = AntOSSettings.consumeInitialBoot() ? 80 : 0;
        ComputerAccessClientState.clear();
        ComputerNetworking.open();
        this.accessResult = ComputerAccessClientState.get();
        faceIdStatusAtOpen = AnternetAccountClientState.faceIdStatusRevision();
        AnternetAccountNetworking.checkFaceId();
        if (loggedIn) {
            requestAntmailState();
            session.antmailRefreshTicks = 40;
        }
        if (!loggedIn) {
            session.authenticated = false;
            session.windows.clear();
            session.antmailPickingAttachment = false;
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
        if (com.craisinlord.antos.content.client.AntazonClientState.hasWishlistSnapshot()) {
            session.antazonWishlist.clear();
            session.antazonWishlist.addAll(com.craisinlord.antos.content.client.AntazonClientState.wishlist());
        }
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
        AntmailAnternetResultPayload mailResult = AntmailClientState.get();
        syncAntmailPaintAttachment();
        if (loggedIn && --session.antmailRefreshTicks <= 0) {
            requestAntmailState();
            session.antmailRefreshTicks = 40;
        }
        if (session.antazonWalletRefreshTicks > 0 && --session.antazonWalletRefreshTicks == 0)
            ComputerNetworking.requestAntazonWallet();
        if (loggedIn && session.windows.stream().anyMatch(window -> window.type.equals("TASKS")) && --taskRefreshTicks <= 0) {
            ComputerNetworking.requestTasks();
            taskRefreshTicks = 40;
        }
        if (loggedIn && AntmailClientState.getMailbox() == null) session.antmailStateWaitTicks++;
        else session.antmailStateWaitTicks = 0;
        if (!session.antmailRetryMessageId.isBlank() && mailResult != null
                && mailResult != session.antmailRetryBaseline
                && session.antmailRetryMessageId.equals(mailResult.messageId())
                && !"message_detail".equals(mailResult.detail())) {
            session.antmailStatus = antmailStatus(mailResult);
            session.antmailRetryMessageId = "";
            session.antmailRetryBaseline = null;
            session.antmailRetryTicks = 0;
            requestAntmailState(true);
        } else if (!session.antmailRetryMessageId.isBlank() && --session.antmailRetryTicks <= 0) {
            session.antmailStatus = Component.translatable("computer.antos.status.retry_timed_out").getString();
            session.antmailRetryMessageId = "";
            session.antmailRetryBaseline = null;
            requestAntmailState(true);
        }
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
            session.antmailPickingAttachment = false;
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
        ComputerAccessResultPayload terminalResult = com.craisinlord.antos.content.client.ComputerTerminalClientState.consume();
        if (terminalResult != null) {
            try {
                var terminal = AntmailWire.decodeTag(terminalResult.data().substring(3));
                session.terminalDirectory = terminal.getString("Directory");
                if (terminal.getBoolean("Clear")) session.terminalOutput.clear();
                var lines = terminal.getList("Lines", 8);
                for (int index = 0; index < lines.size(); index++) session.terminalOutput.add(lines.getString(index));
                String openPath = terminal.getString("Open");
                if (!openPath.isBlank()) {
                    ComputerNetworking.openFile(openPath);
                    open(openPath.endsWith(".antpaint") ? "PAINT" : "TEXT");
                }
                while (session.terminalOutput.size() > 40) session.terminalOutput.remove(0);
            } catch (RuntimeException ignored) {
                session.terminalOutput.add("ERROR: CORRUPTED SERVER RESPONSE");
            }
        }
        if (activeWindow != null && activeWindow.type.equals("GAMES") && activeWindow.gameOpen) {
            gameSession(activeWindow).ifPresent(ComputerGame.Session::tick);
        }
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

    private float uiScale() {
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

    private void submitFaceIdLink() {
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
            if (app.equals("ANTMAIL") && antmailHasUnreadMessages()) drawNotificationBadge(g, x + 35, y + 6);
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
            case "ANTAZON" -> "Buy supplies with Antcoins, or sell eligible items from a nearby chest to earn Antcoins. Deliveries include a chest for your next shipment.";
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

    private boolean antmailHasUnreadMessages() {
        AntmailAnternetResultPayload result = AntmailClientState.getMailbox();
        AntmailMailbox mailbox = mailbox(result);
        return mailbox != null && mailbox.unreadCount() > 0;
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

    private boolean drawWallpaperTexture(GuiGraphics g, ComputerGuideData.Wallpaper wallpaper, int x, int y, int width, int height) {
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

    private String wallpaperName() {
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
        g.drawString(font, Component.literal(trimToWidth(window.title, Math.max(1, controlsLeft - x - 12))), x + 7, y + 6, GREEN, false);
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
            if (window.type.equals("ARCHIVE")) renderArchive(g, cx, cy, w - 16, h - 34, window.maximized);
            else if (window.type.equals("TASKS")) renderTasks(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("FILES")) renderFileExplorer(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("SETTINGS")) renderSettings(g, cx, cy, h - 34);
            else if (window.type.equals("TERMINAL")) renderTerminal(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("TEXT")) renderTextEditor(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("PAINT")) renderPaint(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("ANTMAIL")) renderAntmail(g, cx, cy, h - 34);
            else if (window.type.equals("ANTAZON")) renderAntazon(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("GAMES")) renderGames(g, window, cx, cy, mouseX, mouseY);
            else if (window.type.equals("WALLPAPERS")) renderWallpapers(g, cx, cy, h - 34);
            else renderTrash(g, cx, cy);
        } finally {
            g.disableScissor();
        }
    }

    private void renderTasks(GuiGraphics g, int x, int y, int w, int h) {
        taskArchiveButtons.clear();
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> allRows =
                com.craisinlord.antos.content.client.ComputerTasksClientState.get();
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> visibleRows = allRows.stream()
                .filter(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::visible).toList();
        if (!com.craisinlord.antos.content.client.ComputerTasksClientState.hasSnapshot()) {
            g.drawString(font, Component.literal("TASKS"), x + 5, y + 3, PALE_GREEN, false);
            g.fill(x + 2, y + 17, x + 80, y + 18, GREEN);
            g.drawString(font, Component.literal("TASKS ARE LOADING..."), x + 96, y + 20, GREEN, false);
            g.drawString(font, Component.literal("WAITING FOR THE COMPUTER"), x + 96, y + 38, PALE_GREEN, false);
            return;
        }
        if (com.craisinlord.antos.content.client.ComputerTasksClientState.hasError()) {
            g.drawString(font, Component.literal("TASK LIST TOO LARGE TO LOAD"), x + 96, y + 20, GREEN, false);
            g.drawString(font, Component.literal("ASK THE PACK AUTHOR FOR HELP"), x + 96, y + 38, PALE_GREEN, false);
            return;
        }
        List<String> categories = visibleRows.stream().map(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::category)
                .distinct().sorted().toList();
        if (selectedTaskCategory.isBlank() || !categories.contains(selectedTaskCategory)) {
            selectedTaskCategory = categories.isEmpty() ? "" : categories.get(0);
        }
        int sidebarWidth = 102;
        int sidebarRight = x + sidebarWidth;
        g.fill(x, y, sidebarRight, y + h, 0xFF071007);
        g.fill(sidebarRight, y, sidebarRight + 1, y + h, GREEN);
        g.drawString(font, Component.literal("CATEGORIES"), x + 6, y + 3, GREEN, false);
        g.fill(x + 5, y + 14, sidebarRight - 5, y + 15, DARK_GREEN);
        int categoriesVisible = Math.max(1, (h - 32) / 19);
        taskCategoryScroll = Math.max(0, Math.min(taskCategoryScroll, Math.max(0, categories.size() - categoriesVisible)));
        int categoryY = y + 20;
        for (int categoryIndex = taskCategoryScroll; categoryIndex < categories.size() && categoryIndex < taskCategoryScroll + categoriesVisible; categoryIndex++) {
            String category = categories.get(categoryIndex);
            boolean selected = category.equals(selectedTaskCategory);
            if (selected || hovered(x + 2, categoryY - 2, sidebarWidth - 4, 16)) g.fill(x + 2, categoryY - 2, sidebarRight - 2, categoryY + 14, HOVER_FILL);
            String label = Component.translatable(categoryTitleKey(category)).getString();
            int categoryCount = (int) visibleRows.stream().filter(task -> task.category().equals(category)).count();
            int completeCount = (int) visibleRows.stream().filter(task -> task.category().equals(category) && task.complete()).count();
            String count = completeCount + "/" + categoryCount;
            int categoryLabelWidth = Math.max(1, sidebarWidth - font.width(count) - 18);
            g.drawString(font, Component.literal(trimToWidth(label.toUpperCase(Locale.ROOT), categoryLabelWidth)), x + 6, categoryY, selected ? GREEN : 0xFF87B787, false);
            g.drawString(font, Component.literal(count), sidebarRight - font.width(count) - 6, categoryY, selected ? PALE_GREEN : 0xFF638063, false);
            categoryY += 19;
            if (categoryY > y + h - 12) break;
        }
        if (visibleRows.isEmpty()) {
            g.drawString(font, Component.literal(allRows.isEmpty() ? "NO TASK RECORDS FOUND" : "NO RECORDS VISIBLE YET"), sidebarRight + 12, y + 12, GREEN, false);
            g.drawString(font, Component.literal(allRows.isEmpty() ? "INSERT A FIELD DISK TO LOAD ITS RECORDS" : "COMPLETE A PREREQUISITE TO REVEAL MORE"), sidebarRight + 12, y + 28, PALE_GREEN, false);
            return;
        }
        String selectedId = selectedTaskId;
        var selectedTask = visibleRows.stream().filter(task -> task.id().equals(selectedId)).findFirst().orElse(null);
        if (selectedTask != null) {
            renderTaskDetail(g, selectedTask, sidebarRight + 9, y, w - sidebarWidth - 9, h, unlockedTaskArchiveIds(selectedTask));
            return;
        }
        selectedTaskId = "";
        List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> categoryRows = visibleRows.stream()
                .filter(task -> task.category().equals(selectedTaskCategory)).toList();
        int mapX = sidebarRight + 9;
        int mapY = y + 30;
        int mapW = w - sidebarWidth - 9;
        int mapH = Math.max(1, h - 48);
        String progressLabel = categoryRows.stream().filter(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::complete).count()
                + "/" + categoryRows.size() + " COMPLETE";
        String categoryLabel = Component.translatable(categoryTitleKey(selectedTaskCategory)).getString().toUpperCase(Locale.ROOT);
        g.drawString(font, Component.literal(trimToWidth(categoryLabel, Math.max(1, mapW - font.width(progressLabel) - 8))), mapX, y + 3, GREEN, false);
        g.drawString(font, Component.literal(progressLabel), mapX + Math.max(0, mapW - font.width(progressLabel)), y + 3, PALE_GREEN, false);
        int legendX = mapX;
        String[] legendLabels = {"DONE", "AVAILABLE", "LOCKED"};
        int[] legendColors = {PALE_GREEN, GREEN, 0xFF638063};
        for (int i = 0; i < legendLabels.length; i++) {
            g.fill(legendX, y + 17, legendX + 5, y + 22, legendColors[i]);
            g.drawString(font, Component.literal(legendLabels[i]), legendX + 8, y + 16, legendColors[i], false);
            legendX += 8 + font.width(legendLabels[i]) + 11;
        }
        enableComputerScissor(g, mapX, mapY, mapX + mapW, mapY + mapH);
        int[] mapScrollLimits = taskMapScrollLimits(categoryRows, mapX, mapY, mapW, mapH);
        taskMapScrollX = Math.min(taskMapScrollX, mapScrollLimits[0]);
        taskMapScrollY = Math.min(taskMapScrollY, mapScrollLimits[1]);
        List<TaskNode> nodes = taskNodes(categoryRows, mapX, mapY, taskMapScrollX, taskMapScrollY);
        Map<String, TaskNode> nodesById = new HashMap<>();
        for (TaskNode node : nodes) nodesById.put(node.task().id(), node);
        for (TaskNode node : nodes) {
            for (String requiredId : node.task().requires()) {
                TaskNode parent = nodesById.get(requiredId);
                if (parent != null) renderTaskConnection(g, parent, node, parent.task().complete());
            }
        }
        for (TaskNode node : nodes) renderTaskNode(g, node, mapX, mapY, mapX + mapW, mapY + mapH);
        g.disableScissor();
        g.drawString(font, Component.literal(trimToWidth("SELECT TASK  //  SCROLL TO EXPLORE  //  SHIFT + SCROLL: SIDEWAYS", mapW)), mapX, y + h - 12, PALE_GREEN, false);
    }

    private Set<ResourceLocation> unlockedTaskArchiveIds(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task) {
        Set<ResourceLocation> diskIds = Set.copyOf(workspaceDiskIds());
        Set<ResourceLocation> unlocked = Set.copyOf(com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get());
        if (task.id().equals(taskArchiveCacheId) && diskIds.equals(taskArchiveCacheDisks)
                && unlocked.equals(taskArchiveCacheUnlocks)) return taskArchiveCacheVisible;
        Set<ResourceLocation> available = new HashSet<>();
        ComputerGuideData.entriesFor(List.copyOf(diskIds), unlocked).forEach(entry -> available.add(entry.id()));
        taskArchiveCacheId = task.id();
        taskArchiveCacheDisks = diskIds;
        taskArchiveCacheUnlocks = unlocked;
        taskArchiveCacheVisible = Set.copyOf(available);
        return taskArchiveCacheVisible;
    }

    private void renderTaskDetail(GuiGraphics g, com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task,
                                  int x, int y, int w, int h, Set<ResourceLocation> unlockedArchiveIds) {
        if (hovered(x - 2, y, 105, 16)) drawHover(g, x - 2, y, 105, 16);
        g.drawString(font, Component.literal("< BACK TO MAP"), x, y + 3, GREEN, false);
        String taskState = task.complete() ? "COMPLETE" : task.available() ? "AVAILABLE" : "LOCKED";
        g.drawString(font, Component.literal(taskState), x + w - font.width(taskState), y + 3,
                task.complete() ? PALE_GREEN : task.available() ? GREEN : 0xFF638063, false);
        g.fill(x, y + 17, x + w, y + 19, GREEN);
        enableComputerScissor(g, x, y + 19, x + w, y + h - 17);
        int line = y + 25 - taskDetailScroll;
        line = renderTaskSectionLabel(g, "OVERVIEW", x, line, w) + 3;
        line = wrap(g, Component.translatable(task.title()), x, line, w, task.complete() ? PALE_GREEN : GREEN) + 4;
        line = wrap(g, Component.translatable(task.description()), x, line, w, PALE_GREEN) + 5;
        line = renderTaskSectionLabel(g, "PROGRESS", x, line, w) + 3;
        String progress = task.total() == 0 ? "NO OBJECTIVES REQUIRED" : task.done() + " OF " + task.total() + " OBJECTIVES COMPLETE";
        line = wrap(g, progress, x, line, w, GREEN) + 3;
        if (task.total() > 0) {
            int barY = line;
            int barWidth = Math.max(1, w - 2);
            g.fill(x, barY, x + barWidth, barY + 4, DARK_GREEN);
            int filled = (int) ((long) barWidth * task.done() / task.total());
            if (filled > 0) g.fill(x, barY, x + filled, barY + 4, task.complete() ? PALE_GREEN : GREEN);
            line += 8;
        }
        if (!task.requires().isEmpty()) {
            line = renderTaskSectionLabel(g, "PREREQUISITES", x, line, w) + 3;
            List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> allRows =
                    com.craisinlord.antos.content.client.ComputerTasksClientState.get();
            for (String requiredId : task.requires()) {
                var required = allRows.stream().filter(candidate -> candidate.id().equals(requiredId)
                        && candidate.visible()).findFirst().orElse(null);
                String requiredTitle = required == null ? "SEALED FILE" : Component.translatable(required.title()).getString();
                line = wrap(g, (required != null && required.complete() ? "[X] " : "[ ] ") + requiredTitle,
                        x + 2, line, w - 4, required != null && required.complete() ? PALE_GREEN : GREEN) + 2;
            }
            line += 3;
        }
        if (!task.objectives().isEmpty()) line = renderTaskSectionLabel(g, "OBJECTIVES", x, line, w) + 3;
        for (var objective : task.objectives()) {
            boolean done = objective.progress() >= objective.count();
            String label = Component.translatable(objective.description()).getString();
            line = wrap(g, (done ? "[X] " : "[ ] ") + objective.progress() + "/" + objective.count() + "  " + label,
                    x + 2, line, w - 4, done ? PALE_GREEN : GREEN) + 3;
        }
        line += 4;
        if (task.hasRewards()) {
            line = renderTaskSectionLabel(g, "REWARD", x, line, w) + 3;
            line = wrap(g, "EXPRESS SHIPPED FROM ANTAZON", x, line, w, PALE_GREEN) + 4;
        }
        if (!task.archiveEntries().isEmpty()) line = renderTaskSectionLabel(g, "RELATED ARCHIVE FILES", x, line, w) + 3;
        for (String archiveValue : task.archiveEntries()) {
            ResourceLocation archiveId;
            try { archiveId = ResourceLocation.parse(archiveValue); } catch (RuntimeException ignored) { continue; }
            ComputerGuideData.Entry entry = ComputerGuideData.entry(archiveId);
            String label = entry == null ? archiveId.toString() : Component.translatable(entry.titleKey()).getString();
            int buttonY = line - 2;
            boolean unlocked = unlockedArchiveIds.contains(archiveId);
            if (unlocked) {
                if (buttonY >= y + 19 && buttonY + 13 <= y + h - 17) {
                    taskArchiveButtons.add(new TaskArchiveButton(archiveId, x + 1, buttonY, x + w - 1, buttonY + 13));
                }
                if (hovered(x + 1, buttonY, w - 2, 13)) drawHover(g, x + 1, buttonY, w - 2, 13);
            }
            line = wrap(g, (unlocked ? "ARCHIVE // " : "ARCHIVE LOCKED // ") + label,
                    x + 2, line, w - 4, unlocked ? PALE_GREEN : 0xFF638063) + 2;
        }
        g.disableScissor();
        int contentBottom = line + taskDetailScroll;
        int maximumScroll = Math.max(0, contentBottom - (y + h - 22));
        taskDetailScroll = Math.min(taskDetailScroll, maximumScroll);
        if (maximumScroll > 0) g.drawString(font, Component.literal("SCROLL FOR DETAILS"), x + w - 122, y + h - 12, PALE_GREEN, false);
    }

    private int renderTaskSectionLabel(GuiGraphics g, String label, int x, int y, int width) {
        g.drawString(font, Component.literal(label), x, y, GREEN, false);
        g.fill(x, y + 10, x + width, y + 11, DARK_GREEN);
        return y + 13;
    }

    private List<String> visibleTaskCategories() {
        return com.craisinlord.antos.content.client.ComputerTasksClientState.get().stream()
                .filter(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::visible)
                .map(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::category).distinct().sorted().toList();
    }

    private String categoryTitleKey(String category) {
        try {
            ResourceLocation id = ResourceLocation.parse(category);
            return "task.category." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        } catch (RuntimeException ignored) { return category; }
    }

    private List<TaskNode> taskNodes(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> tasks,
                                     int mapX, int mapY, int offsetX, int offsetY) {
        Map<String, com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> byId = new HashMap<>();
        tasks.forEach(task -> byId.put(task.id(), task));
        Map<String, Integer> depths = new HashMap<>();
        for (var task : tasks) taskDepth(task, byId, depths, new HashSet<>());
        Map<Integer, Integer> rowsByColumn = new HashMap<>();
        Set<String> occupiedCells = new HashSet<>();
        for (var task : tasks) if (task.x() >= 0 && task.y() >= 0) occupiedCells.add(task.x() + ":" + task.y());
        List<TaskNode> result = new ArrayList<>();
        for (var task : tasks.stream().sorted(java.util.Comparator.comparingInt((com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow value) -> depths.getOrDefault(value.id(), 0)).thenComparing(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::id)).toList()) {
            int column = depths.getOrDefault(task.id(), 0);
            int row;
            if (task.x() >= 0 && task.y() >= 0) {
                column = task.x();
                row = task.y();
            } else {
                row = rowsByColumn.getOrDefault(column, 0);
                while (occupiedCells.contains(column + ":" + row)) row++;
                rowsByColumn.put(column, row + 1);
                occupiedCells.add(column + ":" + row);
            }
            int left = mapX + 7 + column * TASK_NODE_GRID - offsetX;
            int top = mapY + 8 + row * TASK_NODE_GRID - offsetY;
            result.add(new TaskNode(task, left, top, left + TASK_NODE_SIZE, top + TASK_NODE_SIZE));
        }
        return result;
    }

    private int[] taskMapScrollLimits(List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> tasks,
                                      int mapX, int mapY, int mapW, int mapH) {
        List<TaskNode> nodes = taskNodes(tasks, mapX, mapY, 0, 0);
        int right = nodes.stream().mapToInt(TaskNode::right).max().orElse(mapX + mapW);
        int bottom = nodes.stream().mapToInt(node -> node.bottom() + 12).max().orElse(mapY + mapH);
        return new int[]{Math.max(0, right - (mapX + mapW - 8)), Math.max(0, bottom - (mapY + mapH - 8))};
    }

    private int taskDepth(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task,
                          Map<String, com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> byId,
                          Map<String, Integer> cache, Set<String> visiting) {
        if (cache.containsKey(task.id())) return cache.get(task.id());
        if (!visiting.add(task.id())) return 0;
        int depth = 0;
        for (String requiredId : task.requires()) {
            var required = byId.get(requiredId);
            if (required != null && required.category().equals(task.category())) depth = Math.max(depth, taskDepth(required, byId, cache, visiting) + 1);
        }
        visiting.remove(task.id()); cache.put(task.id(), depth); return depth;
    }

    private void renderTaskConnection(GuiGraphics g, TaskNode from, TaskNode to, boolean complete) {
        int color = complete ? GREEN : 0xFF356035;
        int x1 = from.right(), y1 = from.top() + TASK_NODE_SIZE / 2, x2 = to.left(), y2 = to.top() + TASK_NODE_SIZE / 2;
        int mid = (x1 + x2) / 2;
        g.fill(Math.min(x1, mid), y1 - 1, Math.max(x1, mid) + 1, y1 + 1, color);
        g.fill(mid - 1, Math.min(y1, y2), mid + 1, Math.max(y1, y2) + 1, color);
        g.fill(Math.min(mid, x2), y2 - 1, Math.max(mid, x2) + 1, y2 + 1, color);
    }

    private void renderTaskNode(GuiGraphics g, TaskNode node, int mapLeft, int mapTop, int mapRight, int mapBottom) {
        var task = node.task();
        int border = task.complete() ? PALE_GREEN : task.available() ? GREEN : 0xFF476047;
        boolean isHovered = hovered(node.left(), node.top(), node.right() - node.left(), node.bottom() - node.top());
        if (isHovered) {
            g.fill(node.left() - 2, node.top() - 2, node.right() + 2, node.bottom() + 2, task.available() ? GREEN : 0xFF638063);
        }
        g.fill(node.left(), node.top(), node.right(), node.bottom(), border);
        g.fill(node.left() + 2, node.top() + 2, node.right() - 2, node.bottom() - 2, task.available() ? 0xFF0B180B : 0xFF080D08);
        renderTaskIcon(g, task, node.left() + TASK_NODE_SIZE / 2, node.top() + TASK_NODE_SIZE / 2);
        String state = task.complete() ? "DONE" : task.available() ? task.done() + "/" + task.total() : "LOCKED";
        int stateColor = task.available() || task.complete() ? PALE_GREEN : 0xFF638063;
        if (isHovered) {
            String title = trimToWidth(Component.translatable(task.title()).getString(), 120);
            int titleWidth = font.width(title) + 8;
            int tooltipX = node.right() + 5;
            if (tooltipX + titleWidth > mapRight - 2) tooltipX = node.left() - titleWidth - 5;
            tooltipX = Math.max(mapLeft + 2, Math.min(tooltipX, mapRight - titleWidth - 2));
            int tooltipY = Math.max(mapTop + 2, Math.min(node.top() - 2, mapBottom - 13));
            g.fill(tooltipX - 2, tooltipY, tooltipX + titleWidth, tooltipY + 13, 0xFF071007);
            box(g, tooltipX - 2, tooltipY, tooltipX + titleWidth, tooltipY + 13, GREEN);
            g.drawString(font, Component.literal(title), tooltipX + 2, tooltipY + 2, border, false);
        }
        g.drawString(font, Component.literal(state), node.left() + (TASK_NODE_SIZE - font.width(state)) / 2, node.bottom() + 3, stateColor, false);
    }

    private void renderTaskIcon(GuiGraphics g, com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task, int centerX, int centerY) {
        boolean previousTint = archiveGreenTint;
        archiveGreenTint = true;
        try {
            renderArchiveAsset(g, task.iconItem(), task.iconEntity(), "", "", centerX, centerY, TASK_NODE_SIZE - 6, 0.0F, 0.9F);
        } finally {
            archiveGreenTint = previousTint;
        }
    }

    private record TaskNode(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow task, int left, int top, int right, int bottom) { }
    private record TaskArchiveButton(ResourceLocation entryId, int left, int top, int right, int bottom) { }

    private void enableComputerScissor(GuiGraphics g, int left, int top, int right, int bottom) {
        float scale = uiScale();
        int screenLeft = (int) Math.floor(width / 2.0F + left * scale);
        int screenTop = (int) Math.floor(height / 2.0F + top * scale);
        int screenRight = (int) Math.ceil(width / 2.0F + right * scale);
        int screenBottom = (int) Math.ceil(height / 2.0F + bottom * scale);
        g.enableScissor(screenLeft, screenTop, screenRight, screenBottom);
    }

    private static int windowLocalX(Window window) { return window.maximized ? 14 : window.x; }
    private static int windowLocalY(Window window) { return window.maximized ? 34 : window.y; }
    private static int windowWidth(Window window) { return window.maximized ? WIDTH - 28 : window.width; }
    private static int windowHeight(Window window) { return window.maximized ? HEIGHT - 48 : window.height; }

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

    private void renderArchive(GuiGraphics g, int x, int y, int w, int h, boolean maximized) {
        List<ComputerGuideData.Entry> entries = ComputerGuideData.entriesFor(workspaceDiskIds(),
                com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get());

        ComputerGuideData.Entry selected = null;
        if (session.archiveEntryId != null) {
            selected = entries.stream().filter(entry -> entry.id().equals(session.archiveEntryId)).findFirst().orElse(null);
            if (selected == null) session.archiveEntryId = null;
        }

        if (selected != null) {
            archiveGreenTint = selected.greenTint();
            g.drawString(font, Component.literal("< BACK // RECOVERED FILES"), x, y, GREEN, false);
            g.fill(x, y + 16, x + w, y + 18, GREEN);
            g.drawString(font, Component.literal(trimToWidth(Component.translatable(selected.titleKey()).getString(), w)), x, y + 27, GREEN, false);
            int contentBottom = y + h - 20;
            enableComputerScissor(g, x, y + 36, x + w, contentBottom);
            int line = y + 43 - session.archiveDetailScroll * 11;
            line = wrap(g, selected.type().toUpperCase() + " // " + selected.category().toUpperCase(), x, line, w, PALE_GREEN) + 5;
            if (!selected.entityId().isBlank()) line = renderArchivePreview(g, "", selected.entityId(), "", x, line, w, selected.rotation(), selected.renderScale()) + 2;
            for (String descriptionPart : selected.descriptionKeys()) {
                line = renderArchiveDescriptionPart(g, descriptionPart, x, line, w);
            }
            if (!selected.itemId().isBlank()) line = renderArchivePreview(g, selected.itemId(), "", "", x, line, w) + 2;
            if (!selected.enchantmentId().isBlank()) line = renderArchivePreview(g, "", "", selected.enchantmentId(), x, line, w) + 2;
            if (!selected.recipeId().isBlank()) line = renderArchiveRecipe(g, selected.recipeId(), x, line, w) + 2;
            if (!selected.structureId().isBlank()) {
                line = wrap(g, "STRUCTURE // " + selected.structureId(), x, line, w, PALE_GREEN) + 2;
                line = wrap(g, com.craisinlord.antos.content.client.ComputerStructureLocatorClientState.get(), x, line, w, GREEN) + 2;
            } else if (!selected.locatorId().isBlank()) {
                line = wrap(g, com.craisinlord.antos.content.client.ComputerStructureLocatorClientState.get(), x, line, w, GREEN) + 2;
            }
            if (!selected.dimensionId().isBlank()) line = wrap(g, "DIMENSION // " + selected.dimensionId(), x, line, w, PALE_GREEN);
            g.disableScissor();
            session.archiveDetailMaxScroll = Math.max(0, (line + session.archiveDetailScroll * 11 - contentBottom + 10) / 11);
            session.archiveDetailScroll = Math.min(session.archiveDetailScroll, session.archiveDetailMaxScroll);
            if (session.archiveDetailMaxScroll > 0) g.drawString(font, Component.literal("SCROLL " + session.archiveDetailScroll + "/" + session.archiveDetailMaxScroll), x + w - 82, y + h - 12, PALE_GREEN, false);
            g.drawString(font, Component.literal("[ ESC ] BACK"), x, y + h - 12, PALE_GREEN, false);
            return;
        }

        List<ComputerGuideData.Entry> filteredEntries = filterArchiveEntries(entries);
        g.drawString(font, Component.literal("RECOVERED FILES // " + filteredEntries.size() + "/" + entries.size()), x, y, GREEN, false);
        g.fill(x, y + 14, x + w - 8, y + 35, BLACK);
        box(g, x, y + 14, x + w - 8, y + 35, session.archiveSearchFocused ? GREEN : PALE_GREEN);
        String searchText = session.archiveSearch.isBlank() && !session.archiveSearchFocused ? "SEARCH TITLE" : session.archiveSearch;
        g.drawString(font, Component.literal(trimToWidth(searchText + (session.archiveSearchFocused && caretVisible() ? "|" : ""), w - 16)), x + 6, y + 20,
                session.archiveSearch.isBlank() ? PALE_GREEN : GREEN, false);
        if (entries.isEmpty()) {
            g.drawString(font, Component.literal("NO ARCHIVE DATA"), x, y + 28, PALE_GREEN, false);
            return;
        }
        if (filteredEntries.isEmpty()) {
            g.drawString(font, Component.literal("NO MATCHING ENTRIES"), x, y + 52, PALE_GREEN, false);
            return;
        }
        int columns = maximized ? 3 : 1;
        int cellHeight = maximized ? 72 : 38;
        int thumbnailSize = maximized ? 44 : 28;
        int listTop = y + 40;
        int visibleRows = Math.max(1, (h - 58) / cellHeight);
        int totalRows = (filteredEntries.size() + columns - 1) / columns;
        int startRow = Math.max(0, Math.min(session.archiveScroll, Math.max(0, totalRows - visibleRows)));
        int maximumScroll = Math.max(0, totalRows - visibleRows);
        if (maximumScroll > 0) {
            int trackTop = listTop;
            int trackHeight = Math.max(1, h - 58);
            int thumbHeight = Math.max(12, trackHeight * visibleRows / totalRows);
            int thumbTop = trackTop + (trackHeight - thumbHeight) * startRow / maximumScroll;
            int trackX = x + w - 5;
            g.fill(trackX, trackTop, trackX + 3, trackTop + trackHeight, 0xFF173817);
            g.fill(trackX, thumbTop, trackX + 3, thumbTop + thumbHeight, GREEN);
        }
        for (int rowIndex = startRow; rowIndex < totalRows && rowIndex < startRow + visibleRows; rowIndex++) {
            int rowTop = listTop + (rowIndex - startRow) * cellHeight;
            for (int column = 0; column < columns; column++) {
                int index = rowIndex * columns + column;
                if (index >= filteredEntries.size()) break;
                ComputerGuideData.Entry entry = filteredEntries.get(index);
                int cellWidth = (w - 8) / columns;
                int cellX = x + column * cellWidth;
                int thumbX = maximized ? cellX + (cellWidth - thumbnailSize) / 2 : cellX + 3;
                int thumbY = maximized ? rowTop + 2 : rowTop + 4;
                int textX = maximized ? cellX + 3 : cellX + thumbnailSize + 10;
                int textY = maximized ? rowTop + thumbnailSize + 5 : rowTop + 7;
                boolean hover = inside(cellX, rowTop, cellWidth - 2, cellHeight - 2, session.archiveMouseX, session.archiveMouseY);
            if (hover) drawHover(g, cellX, rowTop, cellWidth - 2, cellHeight - 2);
                g.fill(thumbX, thumbY, thumbX + thumbnailSize, thumbY + thumbnailSize, BLACK);
                renderThumbnail(g, entry, thumbX + thumbnailSize / 2, thumbY + thumbnailSize / 2, hover, thumbnailSize);
                int textWidth = maximized ? cellWidth - 8 : cellWidth - thumbnailSize - 12;
                g.drawString(font, Component.literal(trimToWidth(Component.translatable(entry.titleKey()).getString(), textWidth)), textX, textY, GREEN, false);
                g.drawString(font, Component.literal(trimToWidth(entry.category().toUpperCase(), textWidth)), textX, textY + 12, PALE_GREEN, false);
            }
        }
        int shownStart = startRow * columns;
        int shownEnd = Math.min(filteredEntries.size(), (startRow + visibleRows) * columns);
        if (totalRows > visibleRows) g.drawString(font, Component.literal("SCROLL " + (shownStart + 1) + "-" + shownEnd + " / " + filteredEntries.size()), x, y + h - 12, PALE_GREEN, false);
        else g.drawString(font, Component.literal("[ CLICK ENTRY TO OPEN ]"), x, y + h - 12, PALE_GREEN, false);
    }

    private List<ComputerGuideData.Entry> filterArchiveEntries(List<ComputerGuideData.Entry> entries) {
        String query = session.archiveSearch.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return entries;
        return entries.stream().filter(entry -> Component.translatable(entry.titleKey()).getString().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private void renderThumbnail(GuiGraphics g, ComputerGuideData.Entry entry, int centerX, int centerY, boolean hover, int size) {
        archiveGreenTint = entry.greenTint();
        int background = hover ? 0xFF173817 : 0xFF102010;
        g.fill(centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2, background);
        if (!entry.recipeId().isBlank() && renderArchiveRecipe(g, entry.recipeId(), centerX - size / 2, centerY - size / 2 + size / 5, size, Math.max(6, size / 4))) return;
        String itemId = entry.coverItemId().isBlank() ? entry.itemId() : entry.coverItemId();
        String entityId = entry.coverEntityId().isBlank() ? entry.entityId() : entry.coverEntityId();
        if (!renderArchiveAsset(g, itemId, entityId, entry.enchantmentId(), entry.coverPotionId(), centerX, centerY, size, entry.rotation(), entry.renderScale())) {
            String fallback = !itemId.isBlank() ? itemId : !entry.enchantmentId().isBlank() ? entry.enchantmentId() : entityId;
            g.drawString(font, Component.literal(trimToWidth(fallback.isBlank() ? "NO RENDER" : fallback, size - 4)), centerX - size / 2 + 2, centerY - 4, hover ? GREEN : PALE_GREEN, false);
        }
    }

    private int renderArchiveDescriptionPart(GuiGraphics g, String part, int x, int y, int w) {
        if (part.startsWith("@item:")) return renderArchivePreview(g, part.substring(6), "", "", x, y, w) + 2;
        if (part.startsWith("@entity:")) return renderArchivePreview(g, "", part.substring(8), "", x, y, w) + 2;
        if (part.startsWith("@enchantment:")) return renderArchivePreview(g, "", "", part.substring(13), x, y, w) + 2;
        if (part.startsWith("@recipe:")) return renderArchiveRecipe(g, part.substring(8), x, y, w) + 2;
        return wrap(g, Component.translatable(part), x, y, w, GREEN) + 3;
    }

    private int renderArchiveRecipe(GuiGraphics g, String recipeId, int x, int y, int w) {
        int slot = 16;
        int recipeWidth = slot * 3 + 24;
        if (!renderArchiveRecipe(g, recipeId, x, y, Math.min(w, recipeWidth), slot)) {
            return wrap(g, "RECIPE // " + recipeId, x, y, w, PALE_GREEN);
        }
        return y + slot * 3;
    }

    private boolean renderArchiveRecipe(GuiGraphics g, String recipeId, int x, int y, int width, int slot) {
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

    private int renderArchivePreview(GuiGraphics g, String itemId, String entityId, String enchantmentId, int x, int y, int w) {
        return renderArchivePreview(g, itemId, entityId, enchantmentId, x, y, w, 0.0F, 1.0F);
    }

    private int renderArchivePreview(GuiGraphics g, String itemId, String entityId, String enchantmentId, int x, int y, int w, float rotation) {
        return renderArchivePreview(g, itemId, entityId, enchantmentId, x, y, w, rotation, 1.0F);
    }

    private int renderArchivePreview(GuiGraphics g, String itemId, String entityId, String enchantmentId, int x, int y, int w, float rotation, float renderScale) {
        int previewSize = entityId.isBlank() ? 48 : 96;
        int boxWidth = Math.min(previewSize, w);
        g.fill(x, y, x + boxWidth, y + previewSize, 0xFF102010);
        int centerX = x + boxWidth / 2;
        int centerY = y + previewSize / 2;
        if (!renderArchiveAsset(g, itemId, entityId, enchantmentId, "", centerX, centerY, boxWidth, rotation, renderScale)) {
            String fallback = !itemId.isBlank() ? itemId : !enchantmentId.isBlank() ? enchantmentId : entityId;
            g.drawString(font, Component.literal(trimToWidth(fallback, Math.max(1, w - boxWidth - 6))), x + boxWidth + 6, y + previewSize / 2 - 4, PALE_GREEN, false);
        } else if (!itemId.isBlank() || !entityId.isBlank() || !enchantmentId.isBlank()) {
            String label = !itemId.isBlank() ? "ITEM // " + itemId
                    : !enchantmentId.isBlank() ? "ENCHANTMENT // " + enchantmentId : "MOB // " + archiveMobName(entityId);
            g.drawString(font, Component.literal(trimToWidth(label, Math.max(1, w - boxWidth - 6))), x + boxWidth + 6, y + previewSize / 2 - 4, PALE_GREEN, false);
        }
        return y + previewSize;
    }

    private String archiveMobName(String entityId) {
        int separator = entityId.indexOf(':');
        return (separator >= 0 ? entityId.substring(separator + 1) : entityId).replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private boolean renderArchiveAsset(GuiGraphics g, String itemId, String entityId, String enchantmentId, String potionId, int centerX, int centerY, int size, float rotation, float renderScale) {
        if (!itemId.isBlank()) {
            try {
                ResourceLocation resourceId = ResourceLocation.parse(itemId);
                var item = BuiltInRegistries.ITEM.getOptional(resourceId).orElse(null);
                logArchivePreviewOnce("item.lookup." + itemId, "Archive item preview lookup id={} found={} registryName={}", itemId, item != null, item == null ? "<missing>" : BuiltInRegistries.ITEM.getKey(item));
                if (item != null && !item.equals(net.minecraft.world.item.Items.AIR)) {
                    ItemStack stack = new ItemStack(item);
                    if (item instanceof SpawnEggItem spawnEgg) {
                        ResourceLocation spawnedEntity = BuiltInRegistries.ENTITY_TYPE.getKey(spawnEgg.getType(stack));
                        if (renderArchiveAsset(g, "", spawnedEntity.toString(), "", potionId, centerX, centerY, size, rotation, renderScale)) return true;
                    }
                    int iconSize = Math.min(32, Math.max(16, size - 4));
                    renderArchiveItem(g, stack, centerX - iconSize / 2, centerY - iconSize / 2, iconSize);
                    logArchivePreviewOnce("item.render." + itemId, "Archive item preview rendered id={} descriptionId={} center=({}, {})", itemId, stack.getDescriptionId(), centerX, centerY);
                    return true;
                }
                logArchivePreviewOnce("item.unrenderable." + itemId, "Archive item preview unavailable id={} (missing or AIR)", itemId);
            } catch (RuntimeException exception) {
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
                logArchivePreviewOnce("entity.lookup." + entityId, "Archive mob preview lookup id={} found={} registryName={}", entityId, entityType != null, entityType == null ? "<missing>" : BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
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
                    logArchivePreviewOnce("entity.render." + entityId, "Archive mob preview rendered id={} class={} size={} center=({}, {})", entityId, living.getClass().getName(), size, centerX, centerY);
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
        g.flush();
        if (archiveGreenTint) g.setColor(0.0F, 1.0F, 0.0F, 1.0F);
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0.0F);
            float scale = size / 16.0F;
            g.pose().scale(scale, scale, 1.0F);
            g.renderItem(stack, 0, 0);
            g.flush();
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

    private void renderFileExplorer(GuiGraphics g, int x, int y, int w, int h) {
        var state = ComputerFileSystemClientState.get();
        List<String[]> rows = fileExplorerRows();
        int rowTop = y + 57;
        int rowBottom = y + h - 39;
        int visible = Math.max(1, (rowBottom - rowTop) / 14);
        int maximum = Math.max(0, rows.size() - visible);
        session.fileExplorerMaximumScroll = maximum;
        session.fileExplorerScroll = Math.max(0, Math.min(maximum, session.fileExplorerScroll));
        g.drawString(font, Component.literal("FILE EXPLORER"), x, y, GREEN, false);
        drawFileExplorerBreadcrumbs(g, x, y + 12, w);
        String[] labels = {"NEW", "MOVE", "RENAME", "DELETE", "REFRESH"};
        int[] widths = {34, 36, 42, 40, 48};
        int actionX = x;
        boolean hasSelection = fileExplorerSelectionMutable();
        for (int index = 0; index < labels.length; index++) {
            boolean enabled = index == 0 || index == 4 || hasSelection;
            drawFileExplorerButton(g, actionX, y + 25, widths[index], labels[index], enabled);
            actionX += widths[index] + 2;
        }
        g.fill(x, y + 43, x + w, y + 44, DARK_GREEN);
        g.drawString(font, Component.literal("TYPE  NAME"), x + 2, y + 47, GREEN, false);
        String sizeHeader = "SIZE";
        g.drawString(font, Component.literal(sizeHeader), x + w - font.width(sizeHeader) - 8, y + 47, GREEN, false);
        drawScrollbar(g, x + w - 4, rowTop, Math.max(1, rowBottom - rowTop), visible, rows.size(), session.fileExplorerScroll, maximum);
        for (int index = session.fileExplorerScroll; index < rows.size() && index < session.fileExplorerScroll + visible; index++) {
            String[] fields = rows.get(index);
            int line = rowTop + (index - session.fileExplorerScroll) * 14;
            boolean selected = fields[1].equals(session.fileExplorerSelected);
            boolean hover = hovered(x, line - 2, w - 6, 13);
            if (selected || hover) g.fill(x, line - 2, x + w - 6, line + 11, selected ? 0xFF204820 : HOVER_FILL);
            String icon = fields[0].equals("DIRECTORY") ? "[D]" : fields[0].equals("TEXT") ? "[T]" : "[I]";
            String name = fields[1].substring(fields[1].lastIndexOf('/') + 1);
            String size = trimToWidth(fields[2], 32);
            int sizeWidth = font.width(size);
            g.drawString(font, Component.literal(icon), x + 2, line, fields[0].equals("DIRECTORY") ? GREEN : PALE_GREEN, false);
            g.drawString(font, Component.literal(trimToWidth(name, Math.max(12, w - sizeWidth - 48))), x + 26, line,
                    selected ? PALE_GREEN : GREEN, false);
            g.drawString(font, Component.literal(size), x + w - sizeWidth - 8, line, PALE_GREEN, false);
        }
        if (rows.isEmpty()) {
            String message = !state.success() && state.error().isBlank() ? "SYNCING FILES..."
                    : !state.error().isBlank() ? "COULD NOT LOAD // " + state.error() : "THIS FOLDER IS EMPTY";
            g.drawString(font, Component.literal(trimToWidth(message, w - 12)), x + 3, rowTop + 4, PALE_GREEN, false);
            if (state.success() && state.error().isBlank()) g.drawString(font, Component.literal("USE NEW TO ADD A FOLDER"), x + 3, rowTop + 17, GREEN, false);
        }
        g.fill(x, y + h - 34, x + w, y + h - 33, DARK_GREEN);
        String selectedDetails = fileExplorerSelectedDetails(rows);
        g.drawString(font, Component.literal(trimToWidth(selectedDetails, w - 4)), x, y + h - 28, PALE_GREEN, false);
        String footer = fileExplorerFooter(state);
        g.drawString(font, Component.literal(trimToWidth(footer, w - 4)), x, y + h - 15, GREEN, false);
    }

    private List<String[]> fileExplorerRows() {
        return ComputerFileSystemClientState.get().files().stream().map(file -> file.split("\\t", 3))
                .filter(fields -> fields.length >= 3 && isDirectChild(fields[1], session.fileExplorerDirectory))
                .sorted((left, right) -> {
                    int directoryOrder = Boolean.compare(!left[0].equals("DIRECTORY"), !right[0].equals("DIRECTORY"));
                    if (directoryOrder != 0) return directoryOrder;
                    String leftName = left[1].substring(left[1].lastIndexOf('/') + 1);
                    String rightName = right[1].substring(right[1].lastIndexOf('/') + 1);
                    return String.CASE_INSENSITIVE_ORDER.compare(leftName, rightName);
                }).toList();
    }

    private void drawFileExplorerBreadcrumbs(GuiGraphics g, int x, int y, int w) {
        List<String> paths = fileExplorerVisibleBreadcrumbPaths(w);
        int cursor = x;
        for (int index = 0; index < paths.size(); index++) {
            String path = paths.get(index);
            String label = path.isEmpty() ? "..." : index == 0 ? "/" : path.substring(path.lastIndexOf('/') + 1);
            String segment = path.isEmpty() ? "..." : index == 0 ? "[/]" : trimToWidth(label, Math.max(10, w / 3));
            if (hovered(cursor - 1, y - 2, font.width(segment) + 3, 12)) drawHover(g, cursor - 1, y - 2, font.width(segment) + 3, 12);
            g.drawString(font, Component.literal(segment), cursor, y, index == paths.size() - 1 ? PALE_GREEN : GREEN, false);
            cursor += font.width(segment);
            if (index + 1 < paths.size()) {
                g.drawString(font, Component.literal(" > "), cursor, y, DARK_GREEN, false);
                cursor += font.width(" > ");
            }
        }
    }

    private List<String> fileExplorerVisibleBreadcrumbPaths(int width) {
        List<String> paths = fileExplorerBreadcrumbPaths();
        int totalWidth = 0;
        for (String path : paths) {
            String label = path.equals("/") ? "[/]" : path.substring(path.lastIndexOf('/') + 1);
            totalWidth += font.width(label) + font.width(" > ");
        }
        if (totalWidth <= width) return paths;
        if (paths.size() > 2) return List.of("/", "", paths.get(paths.size() - 2), paths.get(paths.size() - 1));
        return List.of("/", "", paths.get(paths.size() - 1));
    }

    private List<String> fileExplorerBreadcrumbPaths() {
        List<String> paths = new ArrayList<>(List.of("/"));
        String current = "";
        for (String part : session.fileExplorerDirectory.split("/")) {
            if (part.isBlank()) continue;
            current += "/" + part;
            paths.add(current);
        }
        return paths;
    }

    private String fileExplorerBreadcrumbAt(double mouseX, double mouseY, int x, int y) {
        List<String> paths = fileExplorerVisibleBreadcrumbPaths(windowWidth(activeWindow) - 16);
        int cursor = x;
        for (int index = 0; index < paths.size(); index++) {
            String path = paths.get(index);
            if (path.isEmpty()) {
                cursor += font.width("...") + font.width(" > ");
                continue;
            }
            String label = index == 0 ? "[/]" : trimToWidth(path.substring(path.lastIndexOf('/') + 1), Math.max(10, (windowWidth(activeWindow) - 16) / 3));
            String segment = label;
            int segmentWidth = font.width(segment);
            if (inside(cursor - 1, y - 2, segmentWidth + 3, 12, mouseX, mouseY)) return paths.get(index);
            cursor += segmentWidth;
            if (index + 1 < paths.size()) cursor += font.width(" > ");
        }
        return null;
    }

    private int fileExplorerButtonAt(int x) {
        int[] widths = {34, 36, 42, 40, 48};
        int cursor = 0;
        for (int index = 0; index < widths.length; index++) {
            if (x >= cursor && x < cursor + widths[index]) return index;
            cursor += widths[index] + 2;
        }
        return -1;
    }

    private void drawFileExplorerButton(GuiGraphics g, int x, int y, int width, String label, boolean enabled) {
        boolean hover = enabled && hovered(x, y, width, 14);
        g.fill(x, y, x + width, y + 14, hover ? HOVER_FILL : DARK_GREEN);
        box(g, x, y, x + width, y + 14, enabled ? hover ? PALE_GREEN : GREEN : 0xFF315531);
        g.drawCenteredString(font, Component.literal(label), x + width / 2, y + 3, enabled ? PALE_GREEN : 0xFF4A754A);
    }

    private String fileExplorerSelectedDetails(List<String[]> rows) {
        for (String[] fields : rows) {
            if (fields[1].equals(session.fileExplorerSelected)) {
                String name = fields[1].substring(fields[1].lastIndexOf('/') + 1);
                return fields[0] + " // " + name + " // " + fields[2];
            }
        }
        return session.fileExplorerDirectory.equals("/") ? "ROOT DIRECTORY" : session.fileExplorerDirectory;
    }

    private boolean fileExplorerSelectionMutable() {
        return !session.fileExplorerSelected.isBlank() && !session.fileExplorerSelected.equals("/")
                && !ComputerFileSystem.isProtected(session.fileExplorerSelected)
                && ComputerFileSystemClientState.get().files().stream().anyMatch(file -> {
                    String[] fields = file.split("\\t", 3);
                    return fields.length >= 2 && fields[1].equals(session.fileExplorerSelected);
                });
    }

    private String fileExplorerFooter(ComputerFileSystemClientState.State state) {
        if (session.fileExplorerDeleteConfirm) return "DELETE " + session.fileExplorerSelected + "? ENTER / ESC";
        if (session.fileExplorerCreatingDirectory) return "FOLDER: " + session.fileExplorerRename + "_  ENTER / ESC";
        if (session.fileExplorerMoving) return "MOVE TO: " + session.fileExplorerRename + "_  ENTER / ESC";
        if (session.fileExplorerRenaming) return "RENAME: " + session.fileExplorerRename + "_  ENTER / ESC";
        if (state.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_DELETE
                || state.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_MOVE
                || state.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_CREATE) {
            String action = switch (state.lastMutationAction()) {
                case com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_DELETE -> "DELETED";
                case com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_MOVE -> "MOVED";
                default -> "CREATED";
            };
            return state.lastMutationSuccess() ? action + " // " + state.lastMutationPath()
                    : "ERROR // " + state.lastMutationError();
        }
        return "CTRL+N NEW  CTRL+M MOVE  CTRL+R RENAME  DEL DELETE";
    }

    private boolean isDirectChild(String path, String directory) {
        if (path.equals(directory)) return false;
        String prefix = directory.equals("/") ? "/" : directory + "/";
        if (!path.startsWith(prefix)) return false;
        return path.indexOf('/', prefix.length()) < 0;
    }

    private String parentDirectory(String path) {
        if (path.equals("/")) return "/";
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    private String trimToWidth(String value, int width) {
        if (font.width(value) <= width) return value;
        String ellipsis = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + ellipsis) > width) end--;
        return value.substring(0, end) + ellipsis;
    }

    private void renderSettings(GuiGraphics g, int x, int y, int h) {
        g.drawString(font, Component.literal("SETTINGS"), x, y, GREEN, false);
        g.drawString(font, Component.literal("SYSTEM STATUS"), x, y + 15, GREEN, false);
        drawSettingsValue(g, x, y + 27, "CPU", (System.currentTimeMillis() / 100 % 87 + 12) + "%");
        drawSettingsValue(g, x, y + 39, "MEMORY", (System.currentTimeMillis() / 250 % 42 + 31) + "%");
        g.fill(x, y + 52, x + 214, y + 53, DARK_GREEN);
        g.drawString(font, Component.literal("APPEARANCE"), x, y + 57, GREEN, false);
        drawSettingsButton(g, x, y + 69, 214, "WALLPAPER", trimToWidth(wallpaperName(), 110), true);
        boolean faceIdLinked = AnternetAccountClientState.faceIdLinked();
        boolean linkedToCurrentAccount = faceIdLinkedToCurrentAccount();
        if (faceIdLinked && !linkedToCurrentAccount) {
            drawSettingsButton(g, x, y + 85, 214, "FACE ID", "IN USE", false);
        } else if (faceIdLinked) {
            drawSettingsButton(g, x, y + 85, 214, "FACE ID", faceIdMessage.isBlank() ? "ON  [ UNLINK ]" : trimToWidth(faceIdMessage, 112), true);
        } else {
            drawSettingsButton(g, x, y + 85, 214, "FACE ID", faceIdMessage.isBlank() ? "OFF  [ ENABLE ]" : trimToWidth(faceIdMessage, 112), true);
        }
        g.fill(x, y + 103, x + 214, y + 104, DARK_GREEN);
        g.drawString(font, Component.literal("HELP"), x, y + 108, GREEN, false);
        drawSettingsButton(g, x, y + 120, 104, "ANTOS", "REPLAY", true);
        drawSettingsButton(g, x + 110, y + 120, 104, "ANTAZON", "REPLAY", true);
        g.fill(x, y + 139, x + 214, y + 140, DARK_GREEN);
        g.drawString(font, Component.literal("INSTALLED DISKS"), x, y + 144, GREEN, false);
        List<ResourceLocation> disks = physicalDisks();
        if (disks.isEmpty()) {
            g.drawString(font, Component.literal("NONE INSTALLED"), x, y + 159, PALE_GREEN, false);
        } else {
            for (int i = 0; i < disks.size() && i < 3; i++) {
                ResourceLocation disk = disks.get(i);
                boolean active = disk.equals(selectedDisk);
                String diskLabel = trimToWidth(disk.toString(), 202);
                int rowY = y + 158 + i * 10;
                boolean diskHover = hovered(x, rowY - 1, 214, 9);
                if (active || diskHover) g.fill(x, rowY - 1, x + 214, rowY + 8, HOVER_FILL);
                g.drawString(font, Component.literal((active ? "> " : "  ") + trimToWidth(diskLabel, 194)), x, rowY, active || diskHover ? GREEN : PALE_GREEN, false);
            }
        }
        drawSettingsButton(g, x + 112, y + 189, 102, "DISK", "EJECT", selectedDisk != null);
        drawSettingsButton(g, x, y + 189, 102, "ACCOUNT", "LOG OUT", true);
    }

    private void drawSettingsValue(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(font, Component.literal(label), x + 4, y, PALE_GREEN, false);
        g.drawString(font, Component.literal(value), x + 164, y, PALE_GREEN, false);
    }

    private void drawSettingsButton(GuiGraphics g, int x, int y, int width, String label, String value, boolean enabled) {
        boolean hover = enabled && hovered(x, y, width, 15);
        g.fill(x, y, x + width, y + 15, hover ? HOVER_FILL : DARK_GREEN);
        box(g, x, y, x + width, y + 15, enabled ? (hover ? PALE_GREEN : GREEN) : 0xFF4A754A);
        int valueWidth = font.width(value);
        g.drawString(font, Component.literal(trimToWidth(label, Math.max(0, width - valueWidth - 12))), x + 4, y + 4, enabled ? PALE_GREEN : 0xFF4A754A, false);
        g.drawString(font, Component.literal(value), x + width - valueWidth - 5, y + 4, enabled ? GREEN : 0xFF4A754A, false);
    }

    private void drawSettingsHover(GuiGraphics g, String label, int x, int textY) {
        int textWidth = Math.min(font.width(label), 214);
        if (textWidth > 0 && hovered(x - 2, textY - 2, textWidth + 4, 13)) {
            drawHover(g, x - 2, textY - 2, textWidth + 4, 13);
        }
    }

    private boolean faceIdLinkedToCurrentAccount() {
        var current = AnternetAccountClientState.latest();
        java.util.UUID linkedAccount = AnternetAccountClientState.faceIdAccountId();
        return current != null && current.result() == AnternetAccountResultPayload.SUCCESS
                && linkedAccount != null && linkedAccount.equals(current.accountId());
    }

    private void renderWallpapers(GuiGraphics g, int x, int y, int h) {
        List<ComputerGuideData.Wallpaper> available = unlockedWallpapers();
        g.drawString(font, Component.literal("INSERT A FLOPPY TO UNLOCK MORE"), x, y, GREEN, false);
        int visible = Math.max(1, (h - 34) / 38);
        int maxScroll = Math.max(0, available.size() - visible);
        session.wallpaperScroll = Math.max(0, Math.min(session.wallpaperScroll, maxScroll));
        for (int row = 0; row < visible && session.wallpaperScroll + row < available.size(); row++) {
            ComputerGuideData.Wallpaper wallpaper = available.get(session.wallpaperScroll + row);
            int rowY = y + 18 + row * 38;
            boolean selected = wallpaper.id().toString().equals(session.wallpaperId);
            if (selected || hovered(x, rowY - 2, 214, 36)) g.fill(x, rowY - 2, x + 214, rowY + 34, HOVER_FILL);
            box(g, x, rowY - 2, x + 214, rowY + 34, selected ? PALE_GREEN : GREEN);
            drawWallpaperPreview(g, x + 5, rowY + 3, 48, 26, wallpaper.id().toString());
            String name = Component.translatable(wallpaper.titleKey()).getString();
            g.drawString(font, Component.literal(trimToWidth(name, 150)), x + 60, rowY + 4, selected ? PALE_GREEN : GREEN, false);
            g.drawString(font, Component.literal(selected ? "SELECTED" : "CLICK TO APPLY"), x + 60, rowY + 18, PALE_GREEN, false);
        }
        if (available.isEmpty()) g.drawString(font, Component.literal("NO WALLPAPERS AVAILABLE"), x, y + 24, PALE_GREEN, false);
    }

    private List<ComputerGuideData.Wallpaper> unlockedWallpapers() {
        Set<String> unlocked = new HashSet<>(session.wallpapers);
        unlocked.add(ComputerDesktopState.DEFAULT_WALLPAPER.toString());
        return ComputerGuideData.wallpapers().stream().filter(wallpaper -> unlocked.contains(wallpaper.id().toString())).toList();
    }

    private void drawWallpaperPreview(GuiGraphics g, int x, int y, int w, int h, String id) {
        try {
            ComputerGuideData.Wallpaper wallpaper = ComputerGuideData.wallpaper(ResourceLocation.parse(id));
            if (wallpaper != null && !wallpaper.textureId().isBlank() && drawWallpaperTexture(g, wallpaper, x, y, w, h)) return;
        } catch (RuntimeException ignored) {
        }
        int hash = id.hashCode();
        int green = 24 + Math.floorMod(hash, 40);
        int blue = 12 + Math.floorMod(hash >>> 8, 24);
        int base = 0xFF000000 | (green / 2 << 16) | (green << 8) | blue;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, GREEN);
        g.fill(x, y, x + w, y + h, base);
        int accent = 0xFF000000 | (Math.min(120, green + 34) << 8) | Math.min(80, blue + 34);
        for (int py = y + 4; py < y + h; py += 9) {
            for (int px = x + 4; px < x + w; px += 12) {
                if (Math.floorMod(px + py + hash, 3) == 0) g.fill(px, py, Math.min(x + w, px + 3), Math.min(y + h, py + 2), accent);
            }
        }
    }

    private void renderTerminal(GuiGraphics g, int x, int y, int w, int h) {
        g.drawString(font, Component.literal("ANTOS TERMINAL [READY]"), x, y, GREEN, false);
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        for (String output : session.terminalOutput) lines.addAll(font.split(Component.literal(output), Math.max(1, w - 10)));
        int visible = Math.max(1, (h - 68) / 11);
        int maximum = Math.max(0, lines.size() - visible);
        session.terminalScroll = Math.max(0, Math.min(session.terminalScroll, maximum));
        session.terminalMaximumScroll = maximum;
        drawScrollbar(g, x + w - 4, y + 17, Math.max(1, h - 68), visible, lines.size(), session.terminalScroll, maximum);
        int end = Math.min(lines.size(), session.terminalScroll + visible);
        for (int i = session.terminalScroll; i < end; i++) {
            g.drawString(font, lines.get(i), x, y + 17 + (i - session.terminalScroll) * 11, PALE_GREEN, false);
        }
        g.drawString(font, Component.literal(trimToWidth(session.terminalDirectory + "> " + session.terminalInput + (caretVisible() ? "|" : ""), 208)), x, y + h - 26, GREEN, false);
    }

    private void drawScrollbar(GuiGraphics g, int x, int top, int trackHeight, int visible, int total, int scroll, int maximum) {
        if (maximum <= 0 || total <= 0) return;
        int thumbHeight = Math.max(12, trackHeight * visible / total);
        int thumbTop = top + (trackHeight - thumbHeight) * scroll / maximum;
        g.fill(x, top, x + 3, top + trackHeight, 0xFF173817);
        g.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, GREEN);
    }

    private void renderPaint(GuiGraphics g, int x, int y, int w, int h) {
        var fileState = ComputerFileSystemClientState.get();
        if (!session.paintDirty && fileState.openedPath().endsWith(".antpaint")
                && (!fileState.openedPath().equals(session.paintLoadedPath)
                || !fileState.openedContents().equals(session.paintLoadedContents)) && !fileState.openedContents().isEmpty()) {
            try {
                AntPaintFile file = AntPaintFileCodec.decode(Base64.getDecoder().decode(fileState.openedContents()));
                session.paintName = file.filename();
                session.antmailPaintPath = fileState.openedPath();
                session.paintCanvas = file.canvas().copy();
                session.paintLoadedPath = fileState.openedPath();
                session.paintLoadedContents = fileState.openedContents();
            } catch (IllegalArgumentException ignored) {
            }
        }
        g.drawString(font, Component.literal(trimToWidth("ANTPAINT // " + session.paintName + (session.paintDirty ? " *" : ""), w)), x, y, GREEN, false);
        renderPaintToolbar(g, x, y);
        // Use the same content dimensions as paintAt().
        int canvasHeight = Math.max(1, h - 32);
        int size = Math.max(1, Math.min(w / AntPaintCanvas.WIDTH, canvasHeight / AntPaintCanvas.HEIGHT));
        int cx = x + (w - size * AntPaintCanvas.WIDTH) / 2;
        int cy = y + 30 + Math.max(0, (canvasHeight - size * AntPaintCanvas.HEIGHT) / 2);
        g.fill(cx - 1, cy - 1, cx + size * AntPaintCanvas.WIDTH + 1, cy + size * AntPaintCanvas.HEIGHT + 1, BLACK);
        g.fill(cx, cy, cx + size * AntPaintCanvas.WIDTH, cy + size * AntPaintCanvas.HEIGHT, BLACK);
        for (int py = 0; py < AntPaintCanvas.HEIGHT; py++) for (int px = 0; px < AntPaintCanvas.WIDTH; px++) if (session.paintCanvas.get(px, py)) g.fill(cx + px * size, cy + py * size, cx + (px + 1) * size, cy + (py + 1) * size, GREEN);
        int hoverX = (session.mouseX - cx) / size;
        int hoverY = (session.mouseY - cy) / size;
        if (caretVisible() && session.mouseX >= cx && session.mouseY >= cy && hoverX >= 0 && hoverX < AntPaintCanvas.WIDTH && hoverY >= 0 && hoverY < AntPaintCanvas.HEIGHT) {
            int pixelX = cx + hoverX * size;
            int pixelY = cy + hoverY * size;
            g.fill(pixelX, pixelY, pixelX + size, pixelY + 1, PALE_GREEN);
            g.fill(pixelX, pixelY + size - 1, pixelX + size, pixelY + size, PALE_GREEN);
            g.fill(pixelX, pixelY, pixelX + 1, pixelY + size, PALE_GREEN);
            g.fill(pixelX + size - 1, pixelY, pixelX + size, pixelY + size, PALE_GREEN);
        }
    }

    private void renderPaintToolbar(GuiGraphics g, int x, int y) {
        for (int index = 0; index < 7; index++) {
            int buttonX = x + index * 28;
            boolean hover = hovered(buttonX, y + 11, 22, 17);
            if (hover) drawHover(g, buttonX, y + 11, 22, 17);
            if (index < 3 && ((index == 0 && session.paintTool == AntPaintTool.PENCIL)
                    || (index == 1 && session.paintTool == AntPaintTool.ERASER)
                    || (index == 2 && session.paintTool == AntPaintTool.FILL))) {
                box(g, buttonX, y + 11, buttonX + 22, y + 28, PALE_GREEN);
            }
            paintToolbarIcon(g, index, buttonX + 11, y + 19);
        }
    }

    private void paintToolbarIcon(GuiGraphics g, int index, int x, int y) {
        String externalIcon = switch (index) {
            case 0 -> "pencil";
            case 1 -> "eraser";
            case 2 -> "bucket";
            case 3 -> "trash";
            case 4 -> "undo";
            case 5 -> "redo";
            case 6 -> "save";
            default -> null;
        };
        if (externalIcon != null) {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                    "antos", "textures/gui/antpaint/" + externalIcon + ".png");
            g.blit(texture, x - 8, y - 8, 16, 16, 0, 0, 24, 24, 24, 24);
            return;
        }
        int color = PALE_GREEN;
        String[][] sprites = {
                {
                        ".............##.",
                        "............####",
                        "...........##++#",
                        "..........##+++##",
                        ".........##+++##.",
                        "........##+++##..",
                        ".......##+++##...",
                        "......##+++##....",
                        ".....##+++##.....",
                        "....##+++##......",
                        "...##+++##.......",
                        "..##+++##........",
                        ".##++##..........",
                        ".####............",
                        "..##.............",
                        "...t............."
                },
                {
                        "................",
                        "...........####.",
                        ".........###++##",
                        ".......###+++##.",
                        ".....###+++##...",
                        "...###+++##.....",
                        ".###+++##.......",
                        "##+++###........",
                        "#++###...........",
                        "####.............",
                        "................",
                        "................",
                        "................",
                        "................",
                        "................",
                        "................"
                },
                {
                        "....########....",
                        "...##......##...",
                        "..##........##..",
                        "..##........##..",
                        ".##############.",
                        "..############..",
                        "..##++++++++##..",
                        "...##++++++##...",
                        "...##++gg++##...",
                        "....##+gg+##....",
                        "....##+gg+##....",
                        ".....######.....",
                        "......####......",
                        "................",
                        "................",
                        "................"
                },
                {
                        ".....######.....",
                        "...##########...",
                        "....##.##.##....",
                        "..############..",
                        "..##++++++++##..",
                        "..##++##++##++##.",
                        "..##++##++##++##.",
                        "..##++##++##++##.",
                        "...##++++++++##..",
                        "...############..",
                        "....##......##...",
                        "................",
                        "................",
                        "................",
                        "................",
                        "................"
                },
                {
                        "......####......",
                        "................",
                        "................",
                        "....##..........",
                        "...####.........",
                        "..##++##........",
                        ".##++++##.......",
                        "##++++++++++##..",
                        ".##++++##.......",
                        "..##++##........",
                        "...####.........",
                        "....##..........",
                        "................",
                        "................",
                        "................",
                        "................"
                },
                {
                        "......####......",
                        "................",
                        "................",
                        "....##..........",
                        "...####.........",
                        "..##++##........",
                        ".##++++##.......",
                        "##++++++++++##..",
                        ".##++++##.......",
                        "..##++##........",
                        "...####.........",
                        "....##..........",
                        "................",
                        "................",
                        "................",
                        "................"
                },
                {
                        ".##############.",
                        "#++++++++++++++#",
                        "#++##########++#",
                        "#++#........#++#",
                        "#++#........#++#",
                        "#++##########++#",
                        "#++++++++++++++#",
                        "#++##########++#",
                        "#++#++++++++#++#",
                        "#++#++gggg++#++#",
                        "#++#++gggg++#++#",
                        "#++#++++++++#++#",
                        "#++##########++#",
                        "#++++++++++++++#",
                        "################",
                        "................"
                }
        };
        String[] sprite = sprites[Math.max(0, Math.min(index, sprites.length - 1))];
        if (index == 5) {
            sprite = sprite.clone();
            for (int row = 0; row < sprite.length; row++) {
                sprite[row] = new StringBuilder(sprite[row].substring(0, Math.min(16, sprite[row].length()))).reverse().toString();
            }
        }
        for (int row = 0; row < 16; row++) {
            String line = row < sprite.length ? sprite[row] : "";
            if (line.length() > 16) line = line.substring(0, 16);
            for (int column = 0; column < 16; column++) {
                char pixel = column < line.length() ? line.charAt(column) : '.';
                if (pixel != '#' && pixel != '+' && pixel != 'g' && pixel != 't') continue;
                for (int[] offset : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
                    int dx = offset[0];
                    int dy = offset[1];
                    int nx = column + dx;
                    int ny = row + dy;
                    if (nx < 0 || nx >= 16 || ny < 0 || ny >= 16) continue;
                    String neighborLine = ny < sprite.length ? sprite[ny] : "";
                    char neighbor = nx < neighborLine.length() ? neighborLine.charAt(nx) : '.';
                    if (neighbor == '.' || neighbor == ' ') g.fill(x - 8 + nx, y - 8 + ny, x - 7 + nx, y - 7 + ny, 0xFF78A878);
                }
            }
        }
        for (int row = 0; row < 16; row++) {
            String line = row < sprite.length ? sprite[row] : "";
            for (int column = 0; column < 16; column++) {
                char pixel = column < line.length() ? line.charAt(column) : '.';
                if (pixel == '#') g.fill(x - 8 + column, y - 8 + row, x - 7 + column, y - 7 + row, BLACK);
                else if (pixel == '+') g.fill(x - 8 + column, y - 8 + row, x - 7 + column, y - 7 + row, color);
                else if (pixel == 'g') g.fill(x - 8 + column, y - 8 + row, x - 7 + column, y - 7 + row, GREEN);
                else if (pixel == 't') g.fill(x - 8 + column, y - 8 + row, x - 7 + column, y - 7 + row, 0xFF657465);
            }
        }
    }

    private void paintToolbarIconLegacy(GuiGraphics g, int index, int x, int y) {
        int color = PALE_GREEN;
        if (index == 0) {
            g.fill(x - 9, y + 4, x - 5, y + 8, BLACK);
            g.fill(x - 7, y + 2, x - 3, y + 7, BLACK);
            g.fill(x - 5, y, x + 3, y + 6, BLACK);
            g.fill(x - 2, y - 3, x + 6, y + 3, BLACK);
            g.fill(x + 4, y - 6, x + 8, y - 1, BLACK);
            g.fill(x - 8, y + 5, x - 6, y + 7, color);
            g.fill(x - 6, y + 3, x - 4, y + 5, color);
            g.fill(x - 4, y + 1, x - 2, y + 3, color);
            g.fill(x - 2, y - 1, x + 1, y + 2, color);
            g.fill(x + 1, y - 3, x + 4, y, color);
            g.fill(x + 4, y - 5, x + 6, y - 2, color);
            g.fill(x - 4, y + 3, x - 2, y + 5, GREEN);
            g.fill(x - 2, y + 1, x + 1, y + 3, GREEN);
            g.fill(x + 1, y - 1, x + 4, y + 1, GREEN);
            g.fill(x + 3, y - 4, x + 6, y - 2, BLACK);
            g.fill(x + 5, y - 6, x + 8, y - 4, BLACK);
            g.fill(x + 5, y - 4, x + 7, y - 2, color);
        } else if (index == 1) {
            g.fill(x - 9, y - 3, x + 5, y + 7, BLACK);
            g.fill(x - 6, y - 6, x + 8, y + 4, BLACK);
            g.fill(x - 7, y - 2, x - 2, y + 3, color);
            g.fill(x - 4, y - 4, x + 2, y + 1, color);
            g.fill(x - 1, y - 5, x + 5, y, color);
            g.fill(x - 9, y + 4, x + 3, y + 8, BLACK);
            g.fill(x - 7, y + 4, x - 1, y + 6, color);
            g.fill(x - 6, y - 1, x - 3, y + 2, PALE_GREEN);
            g.fill(x - 3, y - 3, x, y, PALE_GREEN);
            g.fill(x + 1, y - 4, x + 4, y - 1, PALE_GREEN);
            g.fill(x + 3, y, x + 6, y + 3, BLACK);
            g.fill(x + 5, y - 3, x + 8, y + 1, BLACK);
            g.fill(x + 5, y - 2, x + 6, y, color);
        } else if (index == 2) {
            g.fill(x - 6, y - 7, x + 6, y - 4, BLACK);
            g.fill(x - 8, y - 5, x - 5, y + 1, BLACK);
            g.fill(x + 5, y - 5, x + 8, y + 1, BLACK);
            g.fill(x - 6, y - 5, x + 6, y - 3, color);
            g.fill(x - 7, y - 2, x + 7, y + 2, BLACK);
            g.fill(x - 5, y - 1, x + 5, y + 1, color);
            g.fill(x - 5, y + 1, x + 5, y + 7, BLACK);
            g.fill(x - 4, y + 3, x + 4, y + 6, color);
            g.fill(x - 3, y + 2, x + 4, y + 4, PALE_GREEN);
            g.fill(x - 4, y + 6, x + 4, y + 8, BLACK);
            g.fill(x - 2, y + 5, x + 3, y + 6, GREEN);
            g.fill(x - 5, y - 6, x - 3, y - 4, GREEN);
            g.fill(x + 3, y - 6, x + 5, y - 4, GREEN);
        } else if (index == 3) {
            g.fill(x - 6, y - 7, x + 6, y - 4, BLACK);
            g.fill(x - 3, y - 9, x + 3, y - 6, BLACK);
            g.fill(x - 5, y - 3, x + 5, y + 8, BLACK);
            g.fill(x - 3, y - 1, x + 3, y + 6, color);
            g.fill(x - 4, y - 2, x + 4, y, PALE_GREEN);
            g.fill(x - 3, y + 1, x - 1, y + 6, GREEN);
            g.fill(x + 1, y + 1, x + 3, y + 6, GREEN);
            g.fill(x - 8, y - 8, x + 8, y - 5, BLACK);
            g.fill(x - 7, y - 4, x + 7, y - 2, BLACK);
            g.fill(x - 8, y - 8, x - 5, y + 8, BLACK);
            g.fill(x + 5, y - 8, x + 8, y + 8, BLACK);
            g.fill(x - 9, y - 7, x - 7, y + 8, color);
            g.fill(x + 7, y - 7, x + 9, y + 8, color);
            g.fill(x - 8, y - 1, x + 8, y + 1, BLACK);
            g.fill(x - 8, y + 5, x + 8, y + 7, BLACK);
            g.fill(x - 8, y - 8, x + 8, y - 6, color);
            g.fill(x - 7, y - 7, x + 7, y - 5, BLACK);
        } else if (index == 4) {
            g.fill(x - 9, y - 4, x - 5, y + 2, color);
            g.fill(x - 7, y - 6, x - 3, y - 2, color);
            g.fill(x - 7, y, x - 3, y + 4, color);
            g.fill(x - 5, y + 3, x + 3, y + 7, color);
            g.fill(x + 1, y + 1, x + 6, y + 5, color);
            g.fill(x + 4, y - 3, x + 8, y + 3, color);
            g.fill(x + 2, y - 6, x + 6, y - 2, color);
            g.fill(x - 5, y - 1, x + 3, y + 2, BLACK);
            g.fill(x - 3, y + 2, x + 3, y + 4, BLACK);
            g.fill(x - 8, y - 5, x - 5, y - 3, PALE_GREEN);
            g.fill(x + 5, y - 2, x + 7, y, PALE_GREEN);
        } else if (index == 5) {
            g.fill(x + 5, y - 4, x + 9, y + 2, color);
            g.fill(x + 3, y - 6, x + 7, y - 2, color);
            g.fill(x + 3, y, x + 7, y + 4, color);
            g.fill(x - 3, y + 3, x + 5, y + 7, color);
            g.fill(x - 6, y + 1, x - 1, y + 5, color);
            g.fill(x - 8, y - 3, x - 4, y + 3, color);
            g.fill(x - 6, y - 6, x - 2, y - 2, color);
            g.fill(x - 3, y - 1, x + 5, y + 2, BLACK);
            g.fill(x - 3, y + 2, x + 3, y + 4, BLACK);
            g.fill(x + 5, y - 5, x + 8, y - 3, PALE_GREEN);
            g.fill(x - 7, y - 2, x - 5, y, PALE_GREEN);
        } else {
            g.fill(x - 8, y - 8, x + 8, y + 8, BLACK);
            g.fill(x - 5, y - 6, x + 5, y + 6, color);
            g.fill(x - 5, y - 6, x + 2, y - 2, GREEN);
            g.fill(x + 2, y - 6, x + 5, y - 1, BLACK);
            g.fill(x + 4, y - 6, x + 5, y - 3, PALE_GREEN);
            g.fill(x - 5, y, x + 5, y + 6, BLACK);
            g.fill(x - 3, y + 1, x + 3, y + 5, color);
            g.fill(x - 2, y + 2, x + 2, y + 4, PALE_GREEN);
            g.fill(x - 7, y - 7, x - 5, y + 5, PALE_GREEN);
            g.fill(x - 7, y + 6, x + 7, y + 8, BLACK);
            g.fill(x - 4, y + 6, x + 4, y + 7, GREEN);
        }
    }

    private void renderTextEditor(GuiGraphics g, int x, int y, int w, int h) {
        var fileState = ComputerFileSystemClientState.get();
        if (session.textDirty
                && fileState.lastMutationSuccess()
                && (fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_CREATE
                || fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_SAVE)
                && fileState.lastMutationPath().equals(session.textPath)) {
            session.textDirty = false;
        }
        if (!session.textSaveAsPendingPath.isBlank()
                && fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_CREATE
                && fileState.lastMutationPath().equals(session.textSaveAsPendingPath)) {
            if (fileState.lastMutationSuccess()) {
                session.textPath = session.textSaveAsPendingPath;
                session.textDirty = false;
                session.textJustSavedAs = true;
            }
            session.textSaveAsPendingPath = "";
        }
        if (!session.textMovePendingPath.isBlank()
                && fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_MOVE
                && fileState.lastMutationPath().equals(session.textMovePendingPath)) {
            if (fileState.lastMutationSuccess()) {
                session.textPath = session.textMovePendingPath;
                session.textJustSavedAs = true;
            }
            session.textMovePendingPath = "";
        }
        if (!fileState.openedPath().isEmpty() && !session.textDirty && !session.textJustSavedAs) {
            session.textPath = fileState.openedPath();
            session.textContent = fileState.openedContents();
            session.textCursor = session.textContent.length();
        }
        session.textJustSavedAs = false;
        String editorTitle = session.textRenaming ? "RENAME // " + session.textRename + "_"
                : session.textPath.substring(session.textPath.lastIndexOf('/') + 1) + (session.textDirty ? " *" : "");
        g.drawString(font, Component.literal(trimToWidth(editorTitle, Math.max(1, w - 92))), x, y, GREEN, false);
        if (fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_CREATE
                || fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_SAVE
                || fileState.lastMutationAction() == com.craisinlord.antos.content.network.ComputerAccessPayload.FILE_MOVE) {
            String status = session.textDirty ? "UNSAVED" : fileState.lastMutationSuccess() ? "SAVED" : "ERROR";
            g.drawString(font, Component.literal(status), x + w - font.width(status), y, fileState.lastMutationSuccess() ? PALE_GREEN : GREEN, false);
        }
        String[] toolbarLabels = {"NEW", "OPEN", "WRITE", "SAVE", "SAVE AS"};
        int[] toolbarX = {0, 36, 76, 116, 152};
        int[] toolbarWidths = {34, 38, 38, 34, 62};
        for (int i = 0; i < toolbarLabels.length; i++) {
            drawTextEditorButton(g, x + toolbarX[i], y + 14, Math.min(toolbarWidths[i], Math.max(0, w - toolbarX[i])), 20, toolbarLabels[i]);
        }
        g.fill(x + 4, y + 38, x + w - 4, y + h - 4, BLACK);
        box(g, x + 4, y + 38, x + w - 4, y + h - 4, session.textFocused ? PALE_GREEN : DARK_GREEN);
        if (session.textListing) {
            g.drawString(font, Component.literal("OPEN A TEXT FILE"), x + 10, y + 45, GREEN, false);
            List<String> textFiles = fileState.files().stream().filter(file -> {
                String[] fields = file.split("\\t", 3);
                return fields.length >= 3 && fields[0].equals("TEXT");
            }).toList();
            int visibleFiles = Math.max(1, (h - 112) / 14);
            int maximum = Math.max(0, textFiles.size() - visibleFiles);
            session.textPickerMaximumScroll = maximum;
            session.textPickerScroll = Math.max(0, Math.min(session.textPickerScroll, maximum));
            drawScrollbar(g, x + w - 8, y + 61, Math.max(1, h - 111), visibleFiles, textFiles.size(), session.textPickerScroll, maximum);
            int shown = 0;
            for (int index = session.textPickerScroll; index < textFiles.size() && shown < visibleFiles; index++, shown++) {
                String file = textFiles.get(index);
                String[] fields = file.split("\\t", 3);
                int rowY = y + 61 + shown * 14;
                if (hovered(x + 8, rowY - 2, w - 20, 12)) g.fill(x + 8, rowY - 2, x + w - 12, rowY + 10, HOVER_FILL);
                String name = fields[1].substring(fields[1].lastIndexOf('/') + 1);
                g.drawString(font, Component.literal(trimToWidth(name, w - 34)), x + 10, rowY, PALE_GREEN, false);
            }
            if (textFiles.isEmpty()) g.drawString(font, Component.literal("NO TEXT FILES FOUND"), x + 10, y + 61, PALE_GREEN, false);
        } else {
            String content = session.textContent.isEmpty() ? "TYPE HERE..." : session.textContent;
            if (session.textFocused && caretVisible()) content = content.substring(0, Math.min(session.textCursor, content.length())) + "|"
                    + content.substring(Math.min(session.textCursor, content.length()));
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(content), w - 32);
            int maxLines = Math.max(1, (h - 92) / 11);
            int maximum = Math.max(0, lines.size() - maxLines);
            session.textMaximumScroll = maximum;
            session.textScroll = Math.max(0, Math.min(session.textScroll, maximum));
            drawScrollbar(g, x + w - 8, y + 44, Math.max(1, h - 88), maxLines, lines.size(), session.textScroll, maximum);
            for (int i = session.textScroll; i < lines.size() && i < session.textScroll + maxLines; i++) {
                g.drawString(font, lines.get(i), x + 10, y + 48 + (i - session.textScroll) * 11, session.textFocused ? PALE_GREEN : GREEN, false);
            }
        }
        if (session.textDeleteConfirm) {
            g.drawString(font, Component.literal("DELETE CURRENT FILE? [ ENTER / ESC ]"), x, y + h - 28, PALE_GREEN, false);
        }
    }

    private void drawTextEditorButton(GuiGraphics g, int x, int y, int width, int height, String label) {
        if (width <= 0) return;
        boolean hover = hovered(x, y, width, height);
        g.fill(x, y, x + width, y + height, hover ? HOVER_FILL : DARK_GREEN);
        box(g, x, y, x + width, y + height, hover ? PALE_GREEN : GREEN);
        g.drawCenteredString(font, Component.literal(label), x + width / 2, y + 6, hover ? GREEN : PALE_GREEN);
    }

    private void renderAntmail(GuiGraphics g, int x, int y, int h) {
        var result = AntmailClientState.getMailbox();
        AntmailAnternetResultPayload lastResponse = AntmailClientState.get();
        g.drawString(font, Component.literal("ANTMAIL"), x, y, GREEN, false);
        if (result != null) {
            g.drawString(font, Component.literal(trimToWidth(result.address(), 132)), x, y + 12, PALE_GREEN, false);
            if (hovered(x + 154, y - 2, 60, 20)) drawHover(g, x + 154, y - 2, 60, 20);
            g.drawString(font, Component.literal(session.antmailMode.equals("compose") ? "SAVE DRAFT" : "+ COMPOSE"), x + 158, y + 3, GREEN, false);
        }
        session.antmailAttachmentStartLine = -1;
        session.antmailVisibleAttachmentCount = 0;
        if (result == null) {
            g.drawString(font, Component.literal("CONTACTING ANTMAIL NETWORK"), x, y + 20, GREEN, false);
            String waitStatus = lastResponse != null && !lastResponse.detail().isBlank()
                    ? "SERVER // " + lastResponse.detail().toUpperCase()
                    : session.antmailStateWaitTicks >= 100 ? "NO RESPONSE // CHECK CLIENT + SERVER LOGS" : "PLEASE WAIT // RETRYING STATE SYNC";
            g.drawString(font, Component.literal(trimToWidth(waitStatus, 208)), x, y + 40, PALE_GREEN, false);
            box(g, x, y + 56, x + 150, y + 79, GREEN);
            g.drawString(font, Component.literal("[ RETRY STATE LINK ]"), x + 6, y + 63, GREEN, false);
        } else {
            if (hovered(x, y + 26, 54, 22)) drawHover(g, x, y + 26, 54, 22);
            if (hovered(x + 54, y + 26, 46, 22)) drawHover(g, x + 54, y + 26, 46, 22);
            if (hovered(x + 100, y + 26, 54, 22)) drawHover(g, x + 100, y + 26, 54, 22);
            if (session.antmailMode.equals("inbox")) box(g, x, y + 26, x + 54, y + 48, PALE_GREEN);
            if (session.antmailMode.equals("sent")) box(g, x + 54, y + 26, x + 100, y + 48, PALE_GREEN);
            if (session.antmailMode.equals("drafts")) box(g, x + 100, y + 26, x + 154, y + 48, PALE_GREEN);
            g.drawString(font, Component.literal("INBOX"), x + 5, y + 33, session.antmailMode.equals("inbox") ? PALE_GREEN : GREEN, false);
            int unread = mailbox(result) == null ? 0 : mailbox(result).unreadCount();
            if (unread > 0) g.drawString(font, Component.literal(String.valueOf(unread)), x + 39, y + 33, GREEN, false);
            g.drawString(font, Component.literal("SENT"), x + 60, y + 33, session.antmailMode.equals("sent") ? PALE_GREEN : GREEN, false);
            g.drawString(font, Component.literal("DRAFTS"), x + 105, y + 33, session.antmailMode.equals("drafts") ? PALE_GREEN : GREEN, false);
            g.fill(x, y + 51, x + 214, y + 52, GREEN);
            if (session.antmailMode.equals("compose")) {
                g.drawString(font, Component.literal("TO"), x, y + 60, GREEN, false);
                g.drawString(font, Component.literal(trimToWidth(session.antmailFocused && session.antmailField == 1 && caretVisible()
                        ? insertCaret(session.antmailRecipient, session.antmailCursor) : session.antmailRecipient, 184)), x + 27, y + 60, PALE_GREEN, false);
                g.fill(x, y + 73, x + 214, y + 74, 0xFF315531);
                g.drawString(font, Component.literal("SUBJECT"), x, y + 80, GREEN, false);
                g.drawString(font, Component.literal(trimToWidth(session.antmailFocused && session.antmailField == 2 && caretVisible()
                        ? insertCaret(session.antmailSubject, session.antmailCursor) : session.antmailSubject, 170)), x + 44, y + 80, PALE_GREEN, false);
                g.fill(x, y + 93, x + 214, y + 94, 0xFF315531);
                g.drawString(font, Component.literal("MESSAGE"), x, y + 99, GREEN, false);
                String body = session.antmailBody.isEmpty() ? "Write your message..." : session.antmailBody;
                wrapLimited(g, session.antmailFocused && session.antmailField == 3 && caretVisible()
                        ? insertCaret(body, session.antmailCursor) : body, x, y + 111, 208, y + 135, PALE_GREEN);
                List<String> attachmentLabels = new ArrayList<>();
                if (session.antmailAttachText) attachmentLabels.add("TEXT FILE");
                if (session.antmailAttachPaint) attachmentLabels.add("PAINTING");
                if (session.antmailAttachCoins) attachmentLabels.add("COINS " + session.antmailCoinAmount);
                String attachmentSummary = attachmentLabels.isEmpty() ? "NO ATTACHMENTS" : String.join(" + ", attachmentLabels);
                box(g, x, y + 138, x + 112, y + 158, session.antmailAttachmentMenu ? GREEN : PALE_GREEN);
                g.drawString(font, Component.literal("+ ATTACH"), x + 5, y + 144, GREEN, false);
                g.drawString(font, Component.literal(trimToWidth(attachmentSummary, 95)), x + 117, y + 144, PALE_GREEN, false);
                if (session.antmailAttachmentMenu) {
                    g.fill(x, y + 160, x + 112, y + 212, BLACK);
                    box(g, x, y + 160, x + 112, y + 212, GREEN);
                    g.drawString(font, Component.literal("TEXT FILE..."), x + 6, y + 165, PALE_GREEN, false);
                    g.drawString(font, Component.literal("PAINTING..."), x + 6, y + 180, PALE_GREEN, false);
                    g.drawString(font, Component.literal(session.antmailAttachCoins ? "REMOVE ANTCOINS" : "ANTCOINS..."), x + 6, y + 195, PALE_GREEN, false);
                }
                if (session.antmailPickingAttachment) {
                    g.fill(x + 4, y + 94, x + 210, y + h - 40, BLACK);
                    box(g, x + 4, y + 94, x + 210, y + h - 40, GREEN);
                    g.drawString(font, Component.literal("SELECT A FILE"), x + 10, y + 100, GREEN, false);
                    box(g, x + 190, y + 95, x + 205, y + 110, GREEN);
                    g.drawCenteredString(font, Component.literal("X"), x + 197, y + 98, GREEN);
                    int line = y + 116;
                    for (String file : ComputerFileSystemClientState.get().files()) {
                        String[] fields = file.split("\\t", 3);
                        if (fields.length < 2 || (session.antmailPickingPaint ? !fields[0].equals("IMAGE") : !fields[0].equals("TEXT")) || line > y + h - 52) continue;
                        g.drawString(font, Component.literal(trimToWidth(fields[1], 190)), x + 10, line, PALE_GREEN, false);
                        line += 14;
                    }
                }
                g.fill(x, y + h - 26, x + 214, y + h - 25, GREEN);
                box(g, x + 154, y + h - 25, x + 214, y + h - 5, GREEN);
                g.drawCenteredString(font, Component.literal("SEND"), x + 184, y + h - 19, GREEN);
            } else if (session.antmailMode.equals("message")) {
                AntmailMessage message = selectedAntmailMessage(result);
                if (message != null) {
                    boolean showRetry = session.antmailSent && !message.deliveryStatus().equals("DELIVERED");
                    int attachmentBottom = y + h - (showRetry ? 54 : 36);
                    g.drawString(font, Component.literal("< BACK TO " + (session.antmailSent ? "SENT" : "INBOX")), x, y + 40, GREEN, false);
                    g.drawString(font, Component.literal("DELETE"), x + 178, y + 40, GREEN, false);
                    g.drawString(font, Component.literal("FROM  " + trimToWidth(message.sender().fullAddress(), 178)), x, y + 60, PALE_GREEN, false);
                    g.drawString(font, Component.literal("TO    " + trimToWidth(message.recipient().fullAddress(), 178)), x, y + 73, PALE_GREEN, false);
                    String localizedSubject = localizedMailText(message.subject());
                    String localizedBody = localizedMailText(message.body());
                    g.drawString(font, Component.literal(trimToWidth(localizedSubject, 208)), x, y + 89, GREEN, false);
                    String sharedProduct = antazonProductLink(localizedBody);
                    if (!sharedProduct.isBlank()) g.drawString(font, Component.literal("[ OPEN ANTAZON PRODUCT ]"), x, y + 101, GREEN, false);
                    List<net.minecraft.util.FormattedCharSequence> bodyLines = font.split(Component.literal(localizedBody), 198);
                    int bodyVisible = Math.max(1, (attachmentBottom - (y + 112) - 8) / 11);
                    int bodyMaximum = Math.max(0, bodyLines.size() - bodyVisible);
                    session.antmailDetailScroll = Math.max(0, Math.min(session.antmailDetailScroll, bodyMaximum));
                    session.antmailDetailMaximumScroll = bodyMaximum;
                    drawScrollbar(g, x + 210, y + 112, Math.max(1, attachmentBottom - (y + 112) - 8), bodyVisible,
                            bodyLines.size(), session.antmailDetailScroll, bodyMaximum);
                    int bodyEnd = Math.min(bodyLines.size(), session.antmailDetailScroll + bodyVisible);
                    for (int bodyIndex = session.antmailDetailScroll; bodyIndex < bodyEnd; bodyIndex++) {
                        g.drawString(font, bodyLines.get(bodyIndex), x, y + 112 + (bodyIndex - session.antmailDetailScroll) * 11, PALE_GREEN, false);
                    }
                    int messageLine = y + 112 + (bodyEnd - session.antmailDetailScroll) * 11 + 4;
                    if (!message.attachments().isEmpty() && messageLine + 9 <= attachmentBottom) {
                        session.antmailAttachmentStartLine = messageLine;
                        for (AntmailAttachment attachment : message.attachments()) {
                            if (messageLine + 9 > attachmentBottom) break;
                            if (attachment instanceof AntmailAttachment.Render render) {
                                int previewSize = render.kind().equals("entity") ? 56 : 42;
                                int boxWidth = Math.min(previewSize, 72);
                                g.fill(x, messageLine, x + boxWidth, messageLine + previewSize, DARK_GREEN);
                                archiveGreenTint = false;
                                if (render.kind().equals("recipe")) {
                                    renderArchiveRecipe(g, render.resourceId(), x, messageLine, boxWidth, 12);
                                } else {
                                    renderArchiveAsset(g, render.kind().equals("item") ? render.resourceId() : "",
                                            render.kind().equals("entity") ? render.resourceId() : "",
                                            render.kind().equals("enchantment") ? render.resourceId() : "",
                                            render.kind().equals("potion") ? render.resourceId() : "",
                                            x + boxWidth / 2, messageLine + previewSize / 2, boxWidth, 0.0F, 0.85F);
                                }
                                String label = render.kind().toUpperCase(Locale.ROOT) + " // " + render.resourceId();
                                g.drawString(font, Component.literal(trimToWidth(label, 208 - boxWidth - 6)), x + boxWidth + 6, messageLine + previewSize / 2 - 4, PALE_GREEN, false);
                                messageLine += previewSize + 8;
                            } else {
                                String attachmentLabel = attachment instanceof AntmailAttachment.Antcoins coins ? "[ ANTCOIN TRANSFER ] " + coins.amount() + " ANTCOINS" : "[ SAVE ATTACHMENT ] " + attachment.fileName();
                                g.drawString(font, Component.literal(trimToWidth(attachmentLabel, 208)), x, messageLine, GREEN, false);
                                messageLine += 14;
                            }
                            session.antmailVisibleAttachmentCount++;
                        }
                    }
                    if (showRetry) {
                        String retryLabel = session.antmailRetryMessageId.equals(message.id().toString()) ? "[ RETRYING... ]" : "[ RETRY DELIVERY ]";
                        g.drawString(font, Component.literal(retryLabel), x, y + h - 46, GREEN, false);
                    }
                }
            } else if (session.antmailMode.equals("drafts")) {
                List<AntmailDraft> drafts = mailbox(result) == null ? List.of() : mailbox(result).drafts();
                    g.drawString(font, Component.literal("SAVED DRAFTS // " + drafts.size()), x, y + 58, GREEN, false);
                    int visible = Math.max(1, (h - 122) / 25);
                int maximum = Math.max(0, drafts.size() - visible);
                session.antmailListScroll = Math.max(0, Math.min(session.antmailListScroll, maximum));
                session.antmailListMaximumScroll = maximum;
                drawScrollbar(g, x + 210, y + 75, Math.max(1, h - 122), visible, drafts.size(), session.antmailListScroll, maximum);
                for (int row = 0; row < visible && session.antmailListScroll + row < drafts.size(); row++) {
                    AntmailDraft draft = drafts.get(session.antmailListScroll + row);
                    int rowY = y + 76 + row * 25;
                    if (inside(x, rowY - 2, 207, 23, session.mouseX, session.mouseY)) drawHover(g, x, rowY - 2, 207, 23);
                    g.drawString(font, Component.literal(trimToWidth(draft.subject().isBlank() ? "Untitled draft" : draft.subject(), 200)), x + 2, rowY, GREEN, false);
                    g.drawString(font, Component.literal(trimToWidth(draft.recipient() == null ? "No recipient" : draft.recipient().fullAddress(), 200)), x + 2, rowY + 11, PALE_GREEN, false);
                    g.fill(x, rowY + 22, x + 205, rowY + 23, 0xFF315531);
                }
                if (drafts.isEmpty()) g.drawString(font, Component.literal("NO SAVED DRAFTS"), x, y + 80, PALE_GREEN, false);
            } else {
                List<AntmailMessage> messages = mailboxMessages(result, session.antmailSent);
                int total = antmailTotal(result, session.antmailSent);
                g.drawString(font, Component.literal((session.antmailSent ? "SENT MAIL" : "INBOX") + " // " + total), x, y + 58, GREEN, false);
                int visible = Math.max(1, (h - 122) / 25);
                int maximum = Math.max(0, messages.size() - visible);
                session.antmailListScroll = Math.max(0, Math.min(session.antmailListScroll, maximum));
                session.antmailListMaximumScroll = maximum;
                drawScrollbar(g, x + 210, y + 75, Math.max(1, h - 122), visible, messages.size(), session.antmailListScroll, maximum);
                for (int row = 0; row < visible && session.antmailListScroll + row < messages.size(); row++) {
                    AntmailMessage message = messages.get(session.antmailListScroll + row);
                    int rowY = y + 76 + row * 25;
                    if (inside(x, rowY - 2, 207, 23, session.mouseX, session.mouseY)) drawHover(g, x, rowY - 2, 207, 23);
                    g.drawString(font, Component.literal((message.read() ? "" : "● ") + trimToWidth(session.antmailSent ? message.recipient().fullAddress() : message.sender().fullAddress(), 180)), x + 2, rowY, message.read() ? PALE_GREEN : GREEN, false);
                    String status = session.antmailSent ? "  // " + message.deliveryStatus() : "";
                    g.drawString(font, Component.literal(trimToWidth(message.subject() + status, 200)), x + 2, rowY + 11, message.read() ? PALE_GREEN : GREEN, false);
                    g.fill(x, rowY + 22, x + 205, rowY + 23, 0xFF315531);
                }
                g.drawString(font, Component.literal("[ < ] PAGE " + (session.antmailPage + 1) + " [ > ]"), x, y + h - 42, GREEN, false);
            }
        }
        AntmailAnternetResultPayload operation = AntmailClientState.get();
        if (session.antmailSendPending && operation != null && operation != session.antmailSendBaseline && !operation.messageId().isBlank()) {
            session.antmailSendPending = false;
            session.antmailSendBaseline = null;
            if (operation.status() == 0 || operation.status() == 1) {
                if (session.antmailDraftId != null) AntmailNetworking.deleteDraft(session.antmailDraftId);
                session.antmailDraftId = null;
                session.antmailDraftLoaded = false;
                session.antmailStatus = antmailStatus(operation);
                session.antmailMode = "inbox";
                session.antmailFocused = false;
                session.antmailRecipient = "";
                session.antmailSubject = "";
                session.antmailBody = "";
                session.antmailAttachText = false;
                session.antmailAttachPaint = false;
                session.antmailPaintAttachment = null;
                session.antmailPaintLoadPendingPath = "";
                session.antmailTextPath = "";
                session.antmailPaintPath = "";
                session.antmailPage = 0;
                requestAntmailState(true);
            } else {
                session.antmailFocused = true;
                session.antmailStatus = antmailStatus(operation);
            }
        }
        String displayStatus = session.antmailStatus;
        if (!displayStatus.isEmpty()) {
            int statusHeight = session.antmailMode.equals("compose") ? h - 24 : h;
            drawBottomWrapped(g, displayStatus, x, y, statusHeight, 208, PALE_GREEN);
        }
    }

    private boolean caretVisible() {
        return (System.currentTimeMillis() / 500L) % 2L == 0L;
    }

    private String insertCaret(String value, int cursor) {
        int index = Math.max(0, Math.min(cursor, value.length()));
        return value.substring(0, index) + "|" + value.substring(index);
    }

    private String localizedMailText(String value) {
        return value != null && value.startsWith("antazon.") ? Component.translatable(value).getString() : value;
    }

    private String antmailStatus(AntmailAnternetResultPayload result) {
        if (result.status() == 0) return Component.translatable("computer.antos.status.delivered").getString();
        if (result.status() == 1) return Component.translatable("computer.antos.status.queued").getString();
        return Component.translatable("computer.antos.status.delivery_failed", result.detail().isBlank() ? Component.translatable("computer.antos.status.server_rejected") : result.detail().toUpperCase()).getString();
    }

    private AntmailMailbox mailbox(AntmailAnternetResultPayload result) {
        if (result == null || result.data().isBlank()) return null;
        try {
            return AntmailMailbox.fromTag(AntmailWire.decodeTag(result.data()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<AntmailMessage> mailboxMessages(AntmailAnternetResultPayload result, boolean sent) {
        AntmailMailbox mailbox = mailbox(result);
        if (mailbox == null) return List.of();
        return sent ? mailbox.sent() : mailbox.inbox();
    }

    private int antmailTotal(AntmailAnternetResultPayload result, boolean sent) {
        if (result == null || result.data().isBlank()) return 0;
        try {
            var tag = AntmailWire.decodeTag(result.data());
            return tag.getInt(sent ? "TotalSent" : "TotalInbox");
        } catch (RuntimeException ignored) {
            return mailboxMessages(result, sent).size();
        }
    }

    private AntmailMessage selectedAntmailMessage(AntmailAnternetResultPayload result) {
        List<AntmailMessage> messages = mailboxMessages(result, session.antmailSent);
        if (session.antmailMessageIndex < 0 || session.antmailMessageIndex >= messages.size()) return null;
        AntmailMessage summary = messages.get(session.antmailMessageIndex);
        AntmailMessage detail = AntmailClientState.getMessage(summary.id());
        return detail == null ? summary : detail;
    }

    private void renderAntazonAsset(GuiGraphics g, com.craisinlord.antos.content.client.AntazonClientState.PreviewAsset asset, int centerX, int centerY, int size) {
        g.fill(centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2, 0xFF102010);
        archiveGreenTint = true;
        if (!renderArchiveAsset(g, asset.item(), asset.entity(), "", "", centerX, centerY, size, 0.0F, 1.0F)) {
            g.drawString(font, Component.literal("?"), centerX - 3, centerY - 4, PALE_GREEN, false);
        }
    }

    private List<com.craisinlord.antos.content.client.AntazonClientState.ProductRow> filterAntazonProducts(List<com.craisinlord.antos.content.client.AntazonClientState.ProductRow> products) {
        String query = session.antazonSearch.trim().toLowerCase(Locale.ROOT);
        return products.stream().filter(product -> session.antazonCategory.equals("ALL") || product.category().equalsIgnoreCase(session.antazonCategory))
                .filter(product -> query.isBlank()
                        || Component.translatable(product.name()).getString().toLowerCase(Locale.ROOT).contains(query)
                        || product.category().toLowerCase(Locale.ROOT).contains(query)
                        || product.id().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private List<String> antazonCategories(List<com.craisinlord.antos.content.client.AntazonClientState.ProductRow> products) {
        List<String> categories = new ArrayList<>();
        categories.add("ALL");
        products.stream().map(com.craisinlord.antos.content.client.AntazonClientState.ProductRow::category)
                .map(value -> value.toUpperCase(Locale.ROOT)).distinct().sorted().forEach(categories::add);
        return categories;
    }

    private void renderAntazon(GuiGraphics g, int x, int y, int w, int h) {
        if (!com.craisinlord.antos.content.client.AntazonClientState.onboardingReceived()) {
            g.drawString(font, Component.literal("CONNECTING TO ANTAZON..."), x, y + 38, PALE_GREEN, false);
            return;
        }
        if (!com.craisinlord.antos.content.client.AntazonClientState.onboardingComplete()) {
            renderAntazonOnboarding(g, x, y, w, h);
            return;
        }
        renderAntazonBalance(g, x, y, w);
        renderAntazonTaskbar(g, x, y, w);
        if (session.antazonMode.equals("sell")) {
            renderAntazonSell(g, x, y, w, h);
            return;
        }
        if (session.antazonMode.equals("prices")) {
            renderAntazonPrices(g, x, y, w, h);
            return;
        }
        if (!com.craisinlord.antos.content.client.AntazonClientState.hasSnapshot()) {
            g.drawString(font, Component.literal("LOADING BUY CATALOG..."), x, y + 36, PALE_GREEN, false);
            return;
        }
        List<com.craisinlord.antos.content.client.AntazonClientState.ProductRow> products = com.craisinlord.antos.content.client.AntazonClientState.products();
        if (session.antazonMode.equals("wishlist")) {
            g.drawString(font, Component.literal("WISHLIST"), x, y + 26, GREEN, false);
            box(g, x + 72, y + 21, x + 158, y + 42, PALE_GREEN);
            g.drawCenteredString(font, Component.literal("MAIL LIST"), x + 115, y + 27, GREEN);
            g.fill(x, y + 38, x + w, y + 39, GREEN);
            List<String> wishlist = session.antazonWishlist.stream().toList();
            int visible = Math.max(1, (h - 62) / 36);
            int maximum = Math.max(0, wishlist.size() - visible);
            session.antazonWishlistScroll = Math.max(0, Math.min(maximum, session.antazonWishlistScroll));
            drawScrollbar(g, x + w - 2, y + 46, Math.max(1, h - 62), visible, wishlist.size(), session.antazonWishlistScroll, maximum);
            int rowTop = y + 46;
            for (int index = session.antazonWishlistScroll; index < wishlist.size() && index < session.antazonWishlistScroll + visible; index++) {
                String wishlistId = wishlist.get(index);
                var product = products.stream().filter(value -> value.id().equals(wishlistId)).findFirst().orElse(null);
                if (product == null) continue;
                if (inside(x, rowTop - 3, w - 6, 34, session.mouseX, session.mouseY)) drawHover(g, x, rowTop - 3, w - 6, 34);
                g.drawString(font, Component.literal(trimToWidth(Component.translatable(product.name()).getString(), w - 12)), x + 4, rowTop, GREEN, false);
                String state = product.dealActive() ? "DEAL " + product.dealDiscount() + "% OFF" : antazonOnCooldown(product) ? "COOLDOWN " + antazonCooldown(product) : product.stock() > 0 ? "IN STOCK" : "UNLIMITED STOCK";
                g.drawString(font, Component.literal(trimToWidth(product.category().toUpperCase(Locale.ROOT) + " // " + state, w - 12)), x + 4, rowTop + 11, PALE_GREEN, false);
                String payment = product.payments().isEmpty() ? "PAYMENT UNKNOWN" : "PAY " + String.join(" OR ", product.payments().stream().map(this::prettyAntazonPayment).toList());
                g.drawString(font, Component.literal(trimToWidth(payment, w - 12)), x + 4, rowTop + 22, PALE_GREEN, false);
                rowTop += 36;
            }
            if (session.antazonWishlist.isEmpty()) g.drawString(font, Component.literal("WISHLIST EMPTY"), x, y + 46, PALE_GREEN, false);
            return;
        }
        if (session.antazonMode.equals("orders")) {
            List<com.craisinlord.antos.content.client.AntazonClientState.OrderRow> orders = com.craisinlord.antos.content.client.AntazonClientState.orders();
            if (session.antazonOrderSelection >= 0 && session.antazonOrderSelection < orders.size()) {
                var order = orders.get(session.antazonOrderSelection);
                var product = products.stream().filter(value -> value.id().equals(order.product())).findFirst().orElse(null);
                g.drawString(font, Component.literal("< BACK TO ORDERS"), x, y + 27, GREEN, false);
                g.fill(x, y + 42, x + w, y + 43, GREEN);
                g.drawString(font, Component.literal("PRODUCT"), x, y + 52, PALE_GREEN, false);
                g.drawString(font, Component.literal(trimToWidth(product == null ? order.product() : Component.translatable(product.name()).getString(), w - 8)), x, y + 65, GREEN, false);
                g.drawString(font, Component.literal("QUANTITY  " + order.units()), x, y + 87, PALE_GREEN, false);
                g.drawString(font, Component.literal("PAID WITH  " + trimToWidth(antazonOrderPayment(order.option()), w - 105)), x, y + 103, PALE_GREEN, false);
                g.drawString(font, Component.literal("ORDER STATUS"), x, y + 128, PALE_GREEN, false);
                g.drawString(font, Component.literal(trimToWidth(order.status(), w - 8)), x, y + 141, GREEN, false);
                if (product != null) {
                    wrapLimited(g, Component.translatable(product.description()).getString(), x, y + 160, w - 8, y + h - 10, PALE_GREEN);
                }
                return;
            }
            g.fill(x, y + 26, x + w, y + 27, GREEN);
            int rowTop = y + 34;
            int visible = Math.max(1, (h - 76) / 34);
            int maximum = Math.max(0, orders.size() - visible);
            session.antazonOrdersScroll = Math.max(0, Math.min(maximum, session.antazonOrdersScroll));
            drawScrollbar(g, x + w - 2, rowTop, Math.max(1, h - 76), visible, orders.size(), session.antazonOrdersScroll, maximum);
            for (int index = session.antazonOrdersScroll; index < orders.size() && index < session.antazonOrdersScroll + visible; index++) {
                var order = orders.get(index);
                var product = products.stream().filter(value -> value.id().equals(order.product())).findFirst().orElse(null);
                String name = product == null ? order.product() : Component.translatable(product.name()).getString();
                if (inside(x, rowTop - 3, w - 6, 31, session.mouseX, session.mouseY)) drawHover(g, x, rowTop - 3, w - 6, 31);
                g.drawString(font, Component.literal(trimToWidth(name + " // X" + order.units(), w - 12)), x + 4, rowTop, GREEN, false);
                g.drawString(font, Component.literal(trimToWidth("PAID " + antazonOrderPayment(order.option()) + " // " + order.status(), w - 12)), x + 4, rowTop + 12, PALE_GREEN, false);
                rowTop += 34;
            }
            if (orders.isEmpty()) g.drawString(font, Component.literal("NO ORDERS YET"), x, y + 34, PALE_GREEN, false);
            return;
        }
        if (session.antazonMode.equals("product")) {
            var product = products.stream().filter(value -> value.id().equals(session.antazonProductId)).findFirst().orElse(null);
            if (product == null) { session.antazonMode = "catalog"; return; }
            g.drawString(font, Component.literal("[ MAIL ]"), x + w - 145, y + 26, PALE_GREEN, false);
            g.drawString(font, Component.literal(session.antazonWishlist.contains(product.id()) ? "[ SAVED ]" : "[ + WISH ]"), x + w - 88, y + 26, PALE_GREEN, false);
            g.fill(x, y + 38, x + w, y + 39, GREEN);
            String deal = product.dealActive() ? "  // " + Component.translatable(product.dealLabel()).getString() + " -" + product.dealDiscount() + "%" : "";
            renderAntazonAsset(g, product.thumbnail(), x + 27, y + 76, 52);
            String productName = Component.translatable(product.name()).getString();
            g.drawString(font, Component.literal(trimToWidth(productName + deal, w - 66)), x + 62, y + 52, GREEN, false);
            wrapLimited(g, Component.translatable(product.description()).getString(), x + 62, y + 66, w - 70, y + 83, PALE_GREEN);
            String reviews = product.reviews().isEmpty() ? "NO REVIEWS" : product.reviews().size() + " REVIEWS // " + "★".repeat(product.reviews().stream().mapToInt(com.craisinlord.antos.content.client.AntazonClientState.ReviewRow::rating).sum() / product.reviews().size());
            g.drawString(font, Component.literal(reviews), x + 62, y + 86, GREEN, false);
            boolean cooldown = antazonOnCooldown(product);
            String availability = cooldown ? "COOLDOWN " + antazonCooldown(product) : product.stock() > 0 ? "STOCK " + product.stock() : "UNLIMITED STOCK";
            g.drawString(font, Component.literal(availability), x + w - font.width(availability), y + 86, PALE_GREEN, false);
            int controlsY = y + h - 36;
            String payment = product.payments().isEmpty() ? "PAYMENT UNKNOWN" : String.join(" OR ", product.payments().stream().map(this::prettyAntazonPayment).toList());
            g.drawString(font, Component.literal(trimToWidth(cooldown ? "PURCHASE LOCKED // COOLDOWN" : "EACH PURCHASE DELIVERS " + product.quantity() + " REWARD SETS", w - 8)), x + 4, y + 101, cooldown ? PALE_GREEN : GREEN, false);
            g.drawString(font, Component.literal(trimToWidth("PAY WITH: " + payment, w - 8)), x + 4, y + 117, PALE_GREEN, false);
            g.drawString(font, Component.literal("SERVER USES FIRST AFFORDABLE OPTION"), x + 4, y + 133, PALE_GREEN, false);
            int galleryX = x + 190;
            for (var asset : product.gallery()) {
                if (galleryX + 24 > x + w) break;
                renderAntazonAsset(g, asset, galleryX, y + 119, 20);
                galleryX += 26;
            }
            g.drawString(font, Component.literal("[-]"), x, controlsY, PALE_GREEN, false);
            g.drawCenteredString(font, Component.literal("QUANTITY " + session.antazonQuantity), x + 52, controlsY, PALE_GREEN);
            g.drawString(font, Component.literal("[+]"), x + 78, controlsY, PALE_GREEN, false);
            if (hovered(x, controlsY + 14, 105, 22)) drawHover(g, x, controlsY + 14, 105, 22);
            if (hovered(x + 112, controlsY + 14, Math.max(1, w - 116), 22)) drawHover(g, x + 112, controlsY + 14, w - 116, 22);
            box(g, x, controlsY + 14, x + 105, controlsY + 36, PALE_GREEN);
            box(g, x + 112, controlsY + 14, x + w - 4, controlsY + 36, GREEN);
            g.drawString(font, Component.literal(cooldown ? "[ COOLDOWN ]" : "[ ADD TO CART ]"), x, controlsY + 18, cooldown ? PALE_GREEN : GREEN, false);
            g.drawString(font, Component.literal(cooldown ? "[ AVAILABLE IN " + antazonCooldown(product) + " ]" : "[ BUY NOW ]"), x + 118, controlsY + 18, cooldown ? PALE_GREEN : GREEN, false);
            return;
        }
        if (session.antazonMode.equals("cart")) {
            g.fill(x, y + 26, x + w, y + 27, GREEN);
            int rowTop = y + 46;
            for (Map.Entry<String, Integer> entry : session.antazonCart.entrySet()) {
                if (rowTop + 37 > y + h - 30) break;
                String[] fields = entry.getKey().split("\\|", 2);
                var product = products.stream().filter(value -> value.id().equals(fields[0])).findFirst().orElse(null);
                String cooldown = product != null && antazonOnCooldown(product) ? " // COOLDOWN " + antazonCooldown(product) : "";
                String label = product == null ? fields[0] : Component.translatable(product.name()).getString();
                if (inside(x, rowTop - 3, w, 35, session.mouseX, session.mouseY)) drawHover(g, x, rowTop - 3, w, 35);
                g.drawString(font, Component.literal(trimToWidth(label, w - 104)), x + 4, rowTop, cooldown.isBlank() ? GREEN : PALE_GREEN, false);
                g.drawString(font, Component.literal(trimToWidth("QUANTITY " + entry.getValue() + cooldown, w - 104)), x + 4, rowTop + 12, PALE_GREEN, false);
                String payment = product == null || product.payments().isEmpty() ? "PAYMENT UNAVAILABLE" : "PAY " + String.join(" OR ", product.payments().stream().map(this::prettyAntazonPayment).toList());
                g.drawString(font, Component.literal(trimToWidth(payment, w - 104)), x + 4, rowTop + 24, PALE_GREEN, false);
                int controlsX = x + w - 96;
                g.drawString(font, Component.literal("[-]"), controlsX, rowTop + 4, PALE_GREEN, false);
                g.drawCenteredString(font, Component.literal(String.valueOf(entry.getValue())), controlsX + 29, rowTop + 4, GREEN);
                g.drawString(font, Component.literal("[+]"), controlsX + 43, rowTop + 4, PALE_GREEN, false);
                g.drawString(font, Component.literal("[X]"), controlsX + 68, rowTop + 4, PALE_GREEN, false);
                rowTop += 40;
            }
            if (session.antazonCart.isEmpty()) g.drawString(font, Component.literal("CART EMPTY"), x, rowTop, PALE_GREEN, false);
            else {
            g.drawString(font, Component.literal("FIRST AFFORDABLE PAYMENT OPTION PER PRODUCT"), x, y + 29, PALE_GREEN, false);
                box(g, x + w - 112, y + h - 24, x + w - 4, y + h - 4, GREEN);
                g.drawCenteredString(font, Component.literal("BUY AVAILABLE"), x + w - 58, y + h - 18, GREEN);
            }
            return;
        }
        box(g, x, y + 22, x + w - 8, y + 42, session.antazonSearchFocused ? GREEN : PALE_GREEN);
        String searchText = session.antazonSearch.isBlank() && !session.antazonSearchFocused ? "SEARCH PRODUCTS OR CATEGORIES" : session.antazonSearch;
        g.drawString(font, Component.literal(trimToWidth(searchText + (session.antazonSearchFocused && caretVisible() ? "|" : ""), w - 20)), x + 6, y + 28,
                session.antazonSearch.isBlank() ? PALE_GREEN : GREEN, false);
        List<String> categories = antazonCategories(products);
        String categoryLabel = "CATEGORY: " + session.antazonCategory;
        box(g, x, y + 45, x + Math.min(w - 8, 150), y + 64, session.antazonCategoryOpen ? GREEN : PALE_GREEN);
        g.drawString(font, Component.literal(trimToWidth(categoryLabel, 140)), x + 6, y + 50, GREEN, false);
        if (session.antazonCategoryOpen) {
            int menuBottom = Math.min(y + h - 16, y + 65 + categories.size() * 18);
            g.fill(x, y + 65, x + Math.min(w - 8, 150), menuBottom, BLACK);
            for (int index = 0; index < categories.size() && y + 65 + index * 18 + 18 <= menuBottom; index++) {
                String category = categories.get(index);
                if (category.equals(session.antazonCategory)) drawHover(g, x, y + 65 + index * 18, Math.min(w - 8, 150), 18);
                g.drawString(font, Component.literal(category), x + 6, y + 70 + index * 18, category.equals(session.antazonCategory) ? GREEN : PALE_GREEN, false);
            }
            return;
        }
        List<com.craisinlord.antos.content.client.AntazonClientState.ProductRow> filteredProducts = filterAntazonProducts(products);
        if (filteredProducts.isEmpty()) {
            g.drawString(font, Component.literal("NO MATCHING SUPPLIES"), x, y + 72, PALE_GREEN, false);
            return;
        }
        int rowTop = y + 70;
        int visibleProducts = Math.max(1, (h - 96) / 36);
        int maximumScroll = Math.max(0, filteredProducts.size() - visibleProducts);
        session.antazonScroll = Math.max(0, Math.min(session.antazonScroll, maximumScroll));
        drawScrollbar(g, x + w - 1, rowTop, Math.max(1, h - 96), visibleProducts, filteredProducts.size(), session.antazonScroll, maximumScroll);
        for (int index = session.antazonScroll; index < filteredProducts.size() && index < session.antazonScroll + visibleProducts; index++) {
            var product = filteredProducts.get(index);
            if (inside(x, rowTop - 2, w, 34, session.mouseX, session.mouseY)) drawHover(g, x, rowTop - 2, w, 34);
            renderAntazonAsset(g, product.thumbnail(), x + 12, rowTop + 12, 22);
            String deal = product.dealActive() ? "  [" + product.dealDiscount() + "% OFF]" : "";
            g.drawString(font, Component.literal(trimToWidth(Component.translatable(product.name()).getString() + deal, w - 132)), x + 30, rowTop + 1, GREEN, false);
            boolean cooldown = antazonOnCooldown(product);
            String stock = cooldown ? "COOLDOWN " + antazonCooldown(product) : product.stock() > 0 ? "STOCK " + product.stock() : "UNLIMITED";
            g.drawString(font, Component.literal(stock), x + w - font.width(stock) - 4, rowTop + 2, PALE_GREEN, false);
            g.drawString(font, Component.literal(trimToWidth(product.category().toUpperCase(Locale.ROOT) + " // " + product.quantity() + " REWARD SETS", w - 46)), x + 30, rowTop + 14, PALE_GREEN, false);
            String payment = product.payments().isEmpty() ? "PAYMENT UNKNOWN" : "PAY " + String.join(" OR ", product.payments().stream().map(this::prettyAntazonPayment).toList());
            g.drawString(font, Component.literal(trimToWidth(payment, w - 46)), x + 30, rowTop + 26, PALE_GREEN, false);
            rowTop += 36;
        }
    }

    private void renderAntazonBalance(GuiGraphics g, int x, int y, int w) {
        String label = "ANTCOINS " + com.craisinlord.antos.content.client.AntazonClientState.wallet();
        int labelWidth = font.width(label);
        int labelX = x + w - labelWidth - 2;
        g.fill(labelX - 5, y - 2, x + w, y + 10, 0xFF102010);
        g.drawString(font, Component.literal(label), labelX, y, GREEN, false);
    }

    private void renderAntazonTaskbar(GuiGraphics g, int x, int y, int w) {
        String[] labels = {"SHOP", "SELL", "PRICES", "WISH " + session.antazonWishlist.size(), "ORDERS", "CART " + session.antazonCart.values().stream().mapToInt(Integer::intValue).sum()};
        int[] widths = antazonTabWidths(w);
        int cursor = x;
        for (int index = 0; index < labels.length; index++) {
            boolean selected = switch (index) {
                case 0 -> session.antazonMode.equals("catalog") || session.antazonMode.equals("product");
                case 1 -> session.antazonMode.equals("sell");
                case 2 -> session.antazonMode.equals("prices");
                case 3 -> session.antazonMode.equals("wishlist");
                case 4 -> session.antazonMode.equals("orders");
                default -> session.antazonMode.equals("cart");
            };
            renderAntazonTab(g, labels[index], cursor, y, widths[index], selected);
            cursor += widths[index];
        }
        g.fill(x, y + 16, x + w, y + 17, GREEN);
    }

    private int[] antazonTabWidths(int width) {
        int[] base = {42, 40, 50, 68, 54, 58};
        int total = java.util.Arrays.stream(base).sum();
        int available = Math.max(0, Math.min(width, total));
        int[] result = new int[base.length];
        int used = 0;
        for (int index = 0; index < base.length - 1; index++) {
            result[index] = base[index] * available / total;
            used += result[index];
        }
        result[result.length - 1] = available - used;
        return result;
    }

    private int renderAntazonTab(GuiGraphics g, String label, int x, int y, int width, boolean selected) {
        label = trimToWidth(label, Math.max(0, width - 4));
        boolean hover = inside(x, y - 3, width, 18, session.mouseX, session.mouseY);
        if (hover || selected) g.fill(x - 2, y - 3, x + width - 2, y + 12, hover ? HOVER_FILL : 0xFF102010);
        g.drawString(font, Component.literal(label), x, y, selected || hover ? GREEN : PALE_GREEN, false);
        return x + width;
    }

    private void renderAntazonPrices(GuiGraphics g, int x, int y, int w, int h) {
        g.drawString(font, Component.literal("SELL PRICES // CLICK ITEM TO PREPARE CRATE"), x, y + 26, GREEN, false);
        g.drawString(font, Component.literal("CRATE QTY " + session.antazonPriceQuantity + " // SCROLL TO CHANGE"), x, y + 39, PALE_GREEN, false);
        int rowTop = y + 52;
        int visible = Math.max(1, (h - 70) / 22);
        List<com.craisinlord.antos.content.client.AntazonClientState.PriceRow> prices = com.craisinlord.antos.content.client.AntazonClientState.prices();
        int maximum = Math.max(0, prices.size() - visible);
        session.antazonPriceScroll = Math.max(0, Math.min(maximum, session.antazonPriceScroll));
        drawScrollbar(g, x + w - 2, rowTop, Math.max(1, h - 70), visible, prices.size(), session.antazonPriceScroll, maximum);
        for (int index = session.antazonPriceScroll; index < prices.size() && index < session.antazonPriceScroll + visible; index++) {
            var price = prices.get(index);
            if (inside(x, rowTop - 2, w, 20, session.mouseX, session.mouseY)) drawHover(g, x, rowTop - 2, w, 20);
            renderAntazonAsset(g, new com.craisinlord.antos.content.client.AntazonClientState.PreviewAsset(price.item(), ""), x + 10, rowTop + 9, 16);
            String label = prettyAntazonItem(price.item());
            if (session.antazonPriceItem.equals(price.item())) label += "  X" + session.antazonPriceQuantity + " = " + ((long) price.value() * session.antazonPriceQuantity);
            g.drawString(font, Component.literal(trimToWidth(label, w - 72)), x + 22, rowTop + 5, GREEN, false);
            g.drawString(font, Component.literal(price.value() + " / EA"), x + w - 68, rowTop + 5, PALE_GREEN, false);
            rowTop += 22;
        }
        if (prices.isEmpty()) g.drawString(font, Component.literal("NO SELL RULES LOADED"), x, y + 55, PALE_GREEN, false);
    }

    private void renderAntazonOnboarding(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xE0000000);
        box(g, x + 12, y + 28, x + w - 12, y + h - 16, GREEN);
        g.drawString(font, Component.literal("ANTAZON // GET STARTED"), x + 24, y + 40, GREEN, false);
        String[] lines = {"SHOP  Compare supplies, deals and payment options.", "CART  Review quantities before buying available items.", "SELL  Load a single chest, review the manifest, confirm shipment.", "PRICES  Click an item to prepare a sale crate.", "WISHLIST / ORDERS  Save products and track purchases."};
        for (int index = 0; index < lines.length; index++) g.drawString(font, Component.literal(trimToWidth(lines[index], w - 52)), x + 24, y + 64 + index * 18, PALE_GREEN, false);
        g.drawString(font, Component.literal("[ GOT IT ]"), x + w - 80, y + h - 31, GREEN, false);
        g.drawString(font, Component.literal("[ SKIP ]"), x + 24, y + h - 31, PALE_GREEN, false);
    }

    private void renderAntazonSell(GuiGraphics g, int x, int y, int w, int h) {
        List<com.craisinlord.antos.content.client.AntazonClientState.SellRow> rows = com.craisinlord.antos.content.client.AntazonClientState.sellRows();
        String manifestStatus = com.craisinlord.antos.content.client.AntazonClientState.status();
        String feedback = com.craisinlord.antos.content.client.AntazonClientState.sellFeedback();
        String status = feedback.isBlank() ? manifestStatus : feedback;
        g.drawString(font, Component.literal("SELL // CHEST SHIPMENT"), x, y + 26, GREEN, false);
        boolean ready = manifestStatus.isBlank() && !rows.isEmpty() && rows.stream().allMatch(com.craisinlord.antos.content.client.AntazonClientState.SellRow::sellable);
        if (status.equals("CHEST_REQUIRED")) {
            g.drawString(font, Component.literal("PLACE A SINGLE CHEST NEXT TO THIS COMPUTER"), x, y + 39, PALE_GREEN, false);
            g.drawString(font, Component.literal("PUT THE ITEMS YOU WANT TO SELL INSIDE IT"), x, y + 51, PALE_GREEN, false);
        } else if (status.equals("CRATE_EMPTY")) {
            g.drawString(font, Component.literal("THE CHEST IS EMPTY"), x, y + 39, PALE_GREEN, false);
        } else if (status.equals("UNSUPPORTED_ITEMS")) {
            g.drawString(font, Component.literal("UNSUPPORTED ITEMS // REMOVE THESE BEFORE SHIPPING"), x, y + 39, 0xFFFF7777, false);
        } else if (status.equals("SHIPMENT_LAUNCHED")) {
            g.drawString(font, Component.literal("FREIGHT LAUNCHED // PAYMENT PENDING"), x, y + 39, GREEN, false);
            g.drawString(font, Component.literal("PAYOUT: " + com.craisinlord.antos.content.client.AntazonClientState.sellAmount() + " ANTCOINS"), x, y + 51, PALE_GREEN, false);
            int receiptColor = (System.currentTimeMillis() / 250L) % 2L == 0L ? GREEN : PALE_GREEN;
            g.drawString(font, Component.literal("[ RECEIPT PRINTED ]"), x + w - font.width("[ RECEIPT PRINTED ]") - 8, y + 39, receiptColor, false);
        } else if (session.antazonSellConfirm) {
            int itemCount = rows.stream().mapToInt(com.craisinlord.antos.content.client.AntazonClientState.SellRow::count).sum();
            g.drawString(font, Component.literal(trimToWidth("CONFIRM " + itemCount + " ITEMS FOR " + com.craisinlord.antos.content.client.AntazonClientState.sellAmount() + " ANTCOINS", w - 8)), x, y + 39, PALE_GREEN, false);
            g.drawString(font, Component.literal("THE CHEST AND CONTENTS WILL BE CONSUMED"), x, y + 51, PALE_GREEN, false);
        } else {
            g.drawString(font, Component.literal("CRATE READY FOR COLLECTION"), x, y + 39, PALE_GREEN, false);
            g.drawString(font, Component.literal("THE CHEST AND CONTENTS WILL BE SHIPPED TO ANTAZON"), x, y + 51, PALE_GREEN, false);
        }
        if (!status.isBlank() && !status.equals("CHEST_REQUIRED") && !status.equals("CRATE_EMPTY") && !status.equals("UNSUPPORTED_ITEMS") && !status.equals("SHIPMENT_LAUNCHED"))
            g.drawString(font, Component.literal(trimToWidth(antazonSellMessage(status), w - 8)), x, y + 51, status.contains("FAILED") ? 0xFFFF7777 : PALE_GREEN, false);

        g.drawString(font, Component.literal("SHIPMENT MANIFEST"), x, y + 61, PALE_GREEN, false);
        int rowY = y + 68;
        int manifestBottom = y + h - 64;
        int visibleRows = Math.max(1, (manifestBottom - rowY) / 22);
        int maximumScroll = Math.max(0, rows.size() - visibleRows);
        session.antazonSellScroll = Math.max(0, Math.min(maximumScroll, session.antazonSellScroll));
        drawScrollbar(g, x + w - 2, rowY, Math.max(1, manifestBottom - rowY), visibleRows, rows.size(), session.antazonSellScroll, maximumScroll);
        for (int index = session.antazonSellScroll; index < rows.size() && index < session.antazonSellScroll + visibleRows; index++) {
            var row = rows.get(index);
            int rowTop = rowY + (index - session.antazonSellScroll) * 22;
            g.fill(x, rowTop, x + w - 8, rowTop + 20, row.sellable() ? 0xFF102010 : 0xFF203820);
            renderAntazonAsset(g, new com.craisinlord.antos.content.client.AntazonClientState.PreviewAsset(row.item(), ""), x + 12, rowTop + 10, 16);
            String label = prettyAntazonItem(row.item()) + " x" + row.count();
            String value = row.sellable() ? "+" + row.value() + " ANTCOINS" : "NOT ACCEPTED";
            int unsupportedColor = PALE_GREEN;
            g.drawString(font, Component.literal(trimToWidth(label, w - 105)), x + 25, rowTop + 6, row.sellable() ? PALE_GREEN : unsupportedColor, false);
            g.drawString(font, Component.literal(value), x + w - 98, rowTop + 6, row.sellable() ? GREEN : unsupportedColor, false);
        }
        long total = rows.stream().filter(com.craisinlord.antos.content.client.AntazonClientState.SellRow::sellable).mapToLong(com.craisinlord.antos.content.client.AntazonClientState.SellRow::value).sum();
        String overflow = rows.size() > visibleRows ? " // +" + (rows.size() - visibleRows) + " MORE TYPES" : "";
        String payoutLabel = status.equals("UNSUPPORTED_ITEMS") ? "SELLABLE ITEMS PAYOUT: " : "TOTAL PAYOUT: ";
        g.drawString(font, Component.literal(payoutLabel + total + " ANTCOINS" + overflow), x, y + h - 58, GREEN, false);
        int controlsY = y + h - 35;
        box(g, x, controlsY, x + 78, controlsY + 18, PALE_GREEN);
        g.drawCenteredString(font, Component.literal("REFRESH"), x + 39, controlsY + 5, GREEN);
        String ship = status.equals("SHIPMENT_LAUNCHED") ? "SHIPMENT IN TRANSIT" : status.equals("SHIPMENT_ACCEPTED") ? "SHIPMENT COMPLETE" : session.antazonSellConfirm ? "CONFIRM SHIPMENT" : "SEND CRATE TO ANTAZON";
        box(g, x + 84, controlsY, x + w - 8, controlsY + 18, ready ? GREEN : PALE_GREEN);
        g.drawCenteredString(font, Component.literal(trimToWidth(ship, w - 98)), x + 84 + (w - 92) / 2, controlsY + 5, ready ? GREEN : PALE_GREEN);
    }

    private void renderGames(GuiGraphics g, Window window, int x, int y, int mouseX, int mouseY) {
        g.drawString(font, Component.literal("INSTALLED GAMES"), x, y, GREEN, false);
        if (window.gameOpen) {
            ComputerGame.Session game = gameSession(window).orElse(null);
            if (game != null) game.render(g, font, x, y + 16, 214, 155);
            return;
        }
        List<ComputerGame> games = installedGames();
        for (int index = 0; index < games.size(); index++) {
            int rowTop = y + 18 + index * 16;
            if (inside(x - 4, rowTop, 218, 16, mouseX, mouseY)) drawHover(g, x - 4, rowTop, 218, 16);
            g.drawString(font, Component.literal(games.get(index).title() + "  [ ENTER ]"), x, y + 22 + index * 16, PALE_GREEN, false);
        }
        if (games.isEmpty()) {
            g.drawString(font, Component.literal("NO GAMES INSTALLED"), x, y + 22, PALE_GREEN, false);
            g.drawString(font, Component.literal(trimToWidth("FIND GAME DISKS TO INSTALL PROGRAMS", 214)), x, y + 40, PALE_GREEN, false);
        }
    }

    private List<ComputerGame> installedGames() {
        List<ResourceLocation> disks = physicalDisks();
        if (ComputerGuideData.unlockAllGameEntries()) return ComputerGameRegistry.all().stream().toList();
        return ComputerGameRegistry.all().stream().filter(game -> game.includedByDefault() || game.diskId() != null && disks.contains(game.diskId())).toList();
    }

    private java.util.Optional<ComputerGame.Session> gameSession(Window window) {
        if (window == null || window.gameId == null) return java.util.Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(window.gameId);
        ComputerGame game = id == null ? null : ComputerGameRegistry.get(id);
        if (game == null) return java.util.Optional.empty();
        return java.util.Optional.of(gameSessions.computeIfAbsent(id, ignored -> game.create()));
    }

    private void renderTrash(GuiGraphics g, int x, int y) {
        g.drawString(font, Component.literal("DROP A DISK HERE TO EJECT"), x, y, GREEN, false);
        g.drawString(font, Component.literal("EJECTED FILES RETURN TO PLAYER"), x, y + 22, PALE_GREEN, false);
    }

    private int wrap(GuiGraphics g, String text, int x, int y, int width, int color) { return wrap(g, Component.literal(text), x, y, width, color); }

    private int wrap(GuiGraphics g, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) { g.drawString(font, line, x, y, color, false); y += 11; }
        return y;
    }

    private int wrapLimited(GuiGraphics g, String text, int x, int y, int width, int bottom, int color) {
        for (var line : font.split(Component.literal(text), width)) {
            if (y + 11 > bottom) break;
            g.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }

    private void drawBottomWrapped(GuiGraphics g, String text, int x, int y, int h, int width, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), width);
        int lineY = y + h - 16 - Math.max(0, lines.size() - 1) * 11;
        for (var line : lines) {
            g.drawString(font, line, x, lineY, color, false);
            lineY += 11;
        }
    }

    private void box(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 2, color);
        g.fill(x1, y2 - 2, x2, y2, color);
        g.fill(x1, y1, x1 + 2, y2, color);
        g.fill(x2 - 2, y1, x2, y2, color);
    }

    private boolean hovered(int x, int y, int width, int height) {
        return inside(x, y, width, height, session.mouseX, session.mouseY);
    }

    private void drawHover(GuiGraphics g, int x, int y, int width, int height) {
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
                boolean textTrack = window.type.equals("TEXT") && inside(contentX + contentW - 9,
                        contentY + (session.textListing ? 61 : 44), 5,
                        Math.max(1, contentH - (session.textListing ? 111 : 88)), mouseX, mouseY);
                if (textTrack) {
                    int trackTop = contentY + (session.textListing ? 61 : 44);
                    int trackHeight = Math.max(1, contentH - (session.textListing ? 111 : 88));
                    int maximum = session.textListing ? session.textPickerMaximumScroll : session.textMaximumScroll;
                    int value = (int) Math.round(Math.max(0.0, Math.min(1.0, (mouseY - trackTop) / trackHeight)) * maximum);
                    if (session.textListing) session.textPickerScroll = value;
                    else session.textScroll = value;
                    return true;
                }
                boolean terminalTrack = window.type.equals("TERMINAL")
                        && inside(contentX + contentW - 5, contentY + 17, 5, Math.max(1, contentH - 68), mouseX, mouseY);
                boolean antmailDetailTrack = window.type.equals("ANTMAIL") && session.antmailMode.equals("message")
                        && inside(contentX + contentW - 6, contentY + 98, 6, Math.max(1, contentH - 142), mouseX, mouseY);
                boolean antmailListTrack = window.type.equals("ANTMAIL")
                        && (session.antmailMode.equals("inbox") || session.antmailMode.equals("sent") || session.antmailMode.equals("drafts"))
                        && inside(contentX + contentW - 6, contentY + 80, 6, Math.max(1, contentH - 126), mouseX, mouseY);
                boolean antmailTrack = antmailDetailTrack || antmailListTrack;
                if (terminalTrack || antmailTrack) {
                    int trackTop = terminalTrack ? contentY + 17 : antmailDetailTrack ? contentY + 98 : contentY + 80;
                    int trackHeight = terminalTrack ? Math.max(1, contentH - 68)
                            : Math.max(1, contentH - (antmailDetailTrack ? 142 : 126));
                    int maximum = terminalTrack ? session.terminalMaximumScroll
                            : session.antmailMode.equals("message") ? session.antmailDetailMaximumScroll : session.antmailListMaximumScroll;
                    int value = (int) Math.round(Math.max(0.0, Math.min(1.0, (mouseY - trackTop) / trackHeight)) * maximum);
                    if (terminalTrack) session.terminalScroll = value;
                    else if (session.antmailMode.equals("message")) session.antmailDetailScroll = value;
                    else session.antmailListScroll = value;
                    return true;
                }
                if (window.type.equals("SETTINGS") && inside(contentX, contentY + 157, Math.min(214, contentW), 31, mouseX, mouseY)) {
                    List<ResourceLocation> disks = physicalDisks();
                    int index = ((int) mouseY - (contentY + 157)) / 10;
                    if (index >= 0 && index < disks.size() && index < 3) selectedDisk = disks.get(index);
                    return true;
                }
                if (window.type.equals("TASKS")) {
                    int sidebarWidth = 102;
                    int sidebarRight = contentX + sidebarWidth;
                    if (inside(contentX, contentY, sidebarWidth, contentH, mouseX, mouseY)) {
                        int categoryOffset = (int) mouseY - (contentY + 18);
                        int visibleCategories = Math.max(1, (contentH - 32) / 19);
                        int visibleIndex = categoryOffset / 19;
                        int categoryIndex = taskCategoryScroll + visibleIndex;
                        List<String> categories = visibleTaskCategories();
                        if (categoryOffset >= 0 && categoryOffset % 19 < 16 && visibleIndex < visibleCategories
                                && categoryIndex >= 0 && categoryIndex < categories.size()) {
                            selectedTaskCategory = categories.get(categoryIndex);
                            selectedTaskId = "";
                            taskMapScrollX = 0; taskMapScrollY = 0;
                        }
                        return true;
                    }
                    if (!selectedTaskId.isBlank()) {
                        if (inside(sidebarRight + 7, contentY, 105, 16, mouseX, mouseY)) {
                            selectedTaskId = ""; taskDetailScroll = 0; taskArchiveButtons.clear();
                            return true;
                        }
                        for (TaskArchiveButton archiveButton : taskArchiveButtons) {
                            if (!inside(archiveButton.left(), archiveButton.top(), archiveButton.right() - archiveButton.left(),
                                    archiveButton.bottom() - archiveButton.top(), mouseX, mouseY)) continue;
                            boolean unlocked = ComputerGuideData.entriesFor(workspaceDiskIds(),
                                    com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get()).stream()
                                    .anyMatch(entry -> entry.id().equals(archiveButton.entryId()));
                            if (unlocked) {
                                session.archiveEntryId = archiveButton.entryId(); session.archiveDetailScroll = 0;
                                ComputerNetworking.recordArchiveViewed(archiveButton.entryId());
                                open("ARCHIVE");
                            }
                            return true;
                        }
                    } else {
                        int mapX = sidebarRight + 9;
                        int mapY = contentY + 30;
                        int mapW = contentW - sidebarWidth - 9;
                        int mapH = Math.max(1, contentH - 48);
                        if (inside(mapX, mapY, mapW, mapH, mouseX, mouseY)) {
                            List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> categoryRows =
                                    com.craisinlord.antos.content.client.ComputerTasksClientState.get().stream()
                                            .filter(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::visible)
                                            .filter(task -> task.category().equals(selectedTaskCategory)).toList();
                            List<TaskNode> nodes = taskNodes(categoryRows, mapX, mapY, taskMapScrollX, taskMapScrollY);
                            for (TaskNode node : nodes) if (inside(node.left(), node.top(), node.right() - node.left(), node.bottom() - node.top(), mouseX, mouseY)) {
                                selectedTaskId = node.task().id(); taskDetailScroll = 0; return true;
                            }
                        }
                    }
                    return true;
                }
                if (window.type.equals("ARCHIVE")) {
                    List<ComputerGuideData.Entry> entries = filterArchiveEntries(ComputerGuideData.entriesFor(workspaceDiskIds(),
                            com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get()));
                    int archiveX = x + 8;
                    int archiveY = y + 28;
                    if (session.archiveEntryId != null) {
                        if (inside(archiveX, archiveY, w - 16, 20, mouseX, mouseY)) {
                            session.archiveEntryId = null;
                            session.archiveDetailScroll = 0;
                        }
                    } else {
                        if (inside(archiveX, archiveY + 14, w - 16, 21, mouseX, mouseY)) {
                            session.archiveSearchFocused = true;
                            return true;
                        }
                        session.archiveSearchFocused = false;
                        int archiveContentWidth = w - 16;
                        int columns = window.maximized ? 3 : 1;
                        int cellHeight = window.maximized ? 72 : 38;
                        int visibleRows = Math.max(1, (h - 34 - 58) / cellHeight);
                        int totalRows = (entries.size() + columns - 1) / columns;
                        if (totalRows > visibleRows && inside(archiveX + archiveContentWidth - 5, archiveY + 40, 5,
                                Math.max(1, h - 34 - 58), mouseX, mouseY)) {
                            archiveScrollbarDragging = window;
                            updateArchiveScrollbar(window, mouseY);
                            return true;
                        }
                        int startRow = Math.max(0, Math.min(session.archiveScroll, Math.max(0, totalRows - visibleRows)));
                        int gridTop = archiveY + 40;
                        int relativeRow = ((int) mouseY - gridTop) / cellHeight;
                        int relativeColumn = Math.min(columns - 1, Math.max(0, ((int) mouseX - archiveX) / Math.max(1, (contentW - 8) / columns)));
                        int index = (startRow + relativeRow) * columns + relativeColumn;
                        int cellWidth = (contentW - 8) / columns;
                        int cellX = archiveX + relativeColumn * cellWidth;
                        int rowTop = gridTop + relativeRow * cellHeight;
                        if (relativeRow >= 0 && relativeRow < visibleRows && index < entries.size()
                                && inside(cellX, rowTop, cellWidth - 2, cellHeight - 2, mouseX, mouseY)) {
                            ComputerGuideData.Entry openedEntry = entries.get(index);
                            session.archiveEntryId = openedEntry.id();
                            session.archiveDetailScroll = 0;
                            ComputerNetworking.recordArchiveViewed(openedEntry.id());
                            if (!openedEntry.structureId().isBlank() || !openedEntry.locatorId().isBlank()) {
                                com.craisinlord.antos.content.client.ComputerStructureLocatorClientState.searching(openedEntry.dimensionId(),
                                        openedEntry.structureId().isBlank());
                                ComputerNetworking.locateStructure(openedEntry.id());
                            }
                        }
                    }
                    return true;
                }
                if (window.type.equals("FILES")) {
                    if (session.fileExplorerDeleteConfirm || session.fileExplorerCreatingDirectory
                            || session.fileExplorerMoving || session.fileExplorerRenaming) return true;
                    if (mouseY >= contentY + 12 && mouseY < contentY + 24) {
                        String breadcrumb = fileExplorerBreadcrumbAt(mouseX, mouseY, contentX, contentY + 12);
                        if (breadcrumb != null) {
                            session.fileExplorerDirectory = breadcrumb;
                            session.fileExplorerSelected = "";
                            session.fileExplorerScroll = 0;
                        }
                        return true;
                    }
                    if (mouseY >= contentY + 25 && mouseY < contentY + 39) {
                        int buttonIndex = fileExplorerButtonAt((int) (mouseX - contentX));
                        if (buttonIndex == 0) {
                            beginCreateFileExplorerDirectory();
                        } else if (buttonIndex == 1 && fileExplorerSelectionMutable()) {
                            beginMoveFileExplorerSelection();
                        } else if (buttonIndex == 2 && fileExplorerSelectionMutable()) {
                            beginRenameSelectedFile();
                        } else if (buttonIndex == 3 && fileExplorerSelectionMutable()) {
                            session.fileExplorerCreatingDirectory = false;
                            session.fileExplorerMoving = false;
                            session.fileExplorerRenaming = false;
                            session.fileExplorerDeleteConfirm = true;
                        } else if (buttonIndex == 4) {
                            session.fileExplorerScroll = 0;
                            ComputerNetworking.listFiles();
                        }
                        return true;
                    }
                    List<String[]> rows = fileExplorerRows();
                    int rowTop = contentY + 57;
                    int rowBottom = contentY + contentH - 39;
                    int visible = Math.max(1, (rowBottom - rowTop) / 14);
                    if (mouseX >= contentX + contentW - 6 && mouseY >= rowTop && mouseY < rowBottom) {
                        int maximum = Math.max(0, rows.size() - visible);
                        int offset = Math.max(0, Math.min(rowBottom - rowTop, (int) mouseY - rowTop));
                        session.fileExplorerScroll = maximum == 0 ? 0 : (int) Math.round((double) offset / Math.max(1, rowBottom - rowTop) * maximum);
                        return true;
                    }
                    for (int rowIndex = session.fileExplorerScroll; rowIndex < rows.size() && rowIndex < session.fileExplorerScroll + visible; rowIndex++) {
                        String[] fields = rows.get(rowIndex);
                        int rowY = rowTop + (rowIndex - session.fileExplorerScroll) * 14;
                        if (!inside(contentX, rowY - 2, contentW - 6, 14, mouseX, mouseY)) continue;
                        long now = System.currentTimeMillis();
                        boolean activate = fields[1].equals(session.fileExplorerLastClickedPath)
                                && now - session.fileExplorerLastClickTime <= 350L;
                        session.fileExplorerSelected = fields[1];
                        session.fileExplorerLastClickedPath = fields[1];
                        session.fileExplorerLastClickTime = now;
                        if (fields[0].equals("DIRECTORY")) {
                            if (activate) {
                                session.fileExplorerDirectory = fields[1];
                                session.fileExplorerSelected = "";
                                session.fileExplorerScroll = 0;
                                session.fileExplorerLastClickedPath = "";
                            }
                        }
                        else if (activate && (fields[0].equals("TEXT") || fields[0].equals("IMAGE"))) {
                            ComputerNetworking.openFile(fields[1]);
                            open(fields[1].endsWith(".antpaint") ? "PAINT" : "TEXT");
                            session.fileExplorerLastClickedPath = "";
                        }
                        break;
                    }
                    return true;
                }
                if (window.type.equals("PAINT")) {
                    int canvasX = contentX;
                    int canvasY = contentY + 30;
                    int canvasHeight = Math.max(1, contentH - 32);
                    if (inside(canvasX, canvasY, contentW, canvasHeight, mouseX, mouseY)) {
                        if (button == 0 || button == 1) {
                            paintStrokeActive = session.paintTool == AntPaintTool.PENCIL || session.paintTool == AntPaintTool.ERASER;
                            paintStrokeButton = button;
                            lastPaintPixelX = -1;
                            lastPaintPixelY = -1;
                            if (paintStrokeActive) session.paintHistory.record(session.paintCanvas);
                        }
                        if (button == 0 || button == 1) paintAt(mouseX, mouseY, canvasX, canvasY, contentW, canvasHeight);
                    }
                    else if (mouseY >= contentY + 11 && mouseY < contentY + 30 && mouseX >= contentX && mouseX < contentX + Math.min(196, contentW)) {
                        paintStrokeActive = false;
                        paintToolbarAction(((int) mouseX - contentX) / 28);
                    }
                    return true;
                }
                if (window.type.equals("TEXT")) {
                    if (inside(contentX, contentY + 14, contentW, 20, mouseX, mouseY)) {
                        int toolbarX = (int) mouseX - contentX;
                        if (toolbarX < 34) newText();
                        else if (toolbarX >= 36 && toolbarX < 74) { session.textListing = true; session.textPickerScroll = 0; ComputerNetworking.listFiles(); }
                        else if (toolbarX >= 76 && toolbarX < 114) writeText();
                        else if (toolbarX >= 116 && toolbarX < 150) saveText();
                        else if (toolbarX >= 152 && toolbarX < 214) beginSaveAs();
                    } else if (session.textListing) {
                        var files = ComputerFileSystemClientState.get().files();
                        int index = session.textPickerScroll + ((int) mouseY - (contentY + 61)) / 14;
                        if (mouseY < contentY + 61 || mouseY >= contentY + contentH - 50) return true;
                        int textIndex = 0;
                        for (String file : files) {
                            String[] fields = file.split("\\t", 3);
                            if (fields.length < 3 || !fields[0].equals("TEXT")) continue;
                            if (textIndex++ == index) {
                                session.textListing = false;
                                ComputerNetworking.openFile(fields[1]);
                                break;
                            }
                        }
                    } else {
                        session.textFocused = true;
                        setTextCursorFromMouse(mouseX - (contentX + 10), mouseY - (contentY + 48), contentW - 32);
                    }
                    return true;
                }
                if (window.type.equals("ANTMAIL")) {
                    var result = AntmailClientState.getMailbox();
                    if (result == null) {
                        if (inside(contentX, contentY + 56, Math.min(150, contentW), 23, mouseX, mouseY)) {
                            session.antmailStateWaitTicks = 0;
                            requestAntmailState(true);
                        }
                        return true;
                    } else if (inside(contentX, contentY + 26, 54, 22, mouseX, mouseY)) {
                        session.antmailPickingAttachment = false;
                        session.antmailAttachmentMenu = false;
                        session.antmailMode = "inbox";
                        session.antmailSent = false;
                        session.antmailPage = 0;
                        session.antmailMessageIndex = -1;
                        requestAntmailState(true);
                    } else if (inside(contentX + 54, contentY + 26, 46, 22, mouseX, mouseY)) {
                        session.antmailPickingAttachment = false;
                        session.antmailAttachmentMenu = false;
                        session.antmailMode = "sent";
                        session.antmailSent = true;
                        session.antmailPage = 0;
                        session.antmailMessageIndex = -1;
                        requestAntmailState(true);
                    } else if (inside(contentX + 100, contentY + 26, 54, 22, mouseX, mouseY)) {
                        session.antmailPickingAttachment = false;
                        session.antmailAttachmentMenu = false;
                        session.antmailMode = "drafts";
                        session.antmailSent = false;
                        session.antmailPage = 0;
                        session.antmailMessageIndex = -1;
                        requestAntmailState(true);
                    } else if (inside(contentX + 154, contentY - 2, Math.min(60, Math.max(0, contentW - 154)), 20, mouseX, mouseY)) {
                        if (session.antmailMode.equals("compose")) {
                            saveAntmailDraft();
                            session.antmailStatus = "DRAFT SAVED";
                        } else {
                            session.antmailPickingAttachment = false;
                            session.antmailMode = "compose";
                            session.antmailFocused = true;
                            session.antmailField = 1;
                            session.antmailCursor = session.antmailRecipient.length();
                            restoreLatestDraft(result);
                        }
                    } else if (session.antmailMode.equals("inbox") || session.antmailMode.equals("sent")) {
                        if (mouseY >= contentY + contentH - 48 && mouseY < contentY + contentH - 28) {
                            if (mouseX < contentX + 52 && session.antmailPage > 0) {
                                session.antmailPage--;
                                session.antmailListScroll = 0;
                                requestAntmailState(true);
                            } else if (mouseX >= contentX + 142 && (session.antmailPage + 1) * AntmailMailbox.PAGE_SIZE < antmailTotal(result, session.antmailSent)) {
                                session.antmailPage++;
                                session.antmailListScroll = 0;
                                requestAntmailState(true);
                            }
                            return true;
                        }
                        int index = ((int) mouseY - (contentY + 76)) / 25;
                        List<AntmailMessage> messages = mailboxMessages(result, session.antmailSent);
                        index += session.antmailListScroll;
                        if (mouseY >= contentY + 74 && mouseY < contentY + contentH - 48 && index >= 0 && index < messages.size()) {
                            session.antmailPickingAttachment = false;
                            session.antmailMessageIndex = index;
                            session.antmailMode = "message";
                            session.antmailDetailScroll = 0;
                            AntmailNetworking.requestMessage(messages.get(index).id());
                            if (!session.antmailSent) AntmailNetworking.markRead(messages.get(index).id());
                        }
                    } else if (session.antmailMode.equals("drafts")) {
                        int index = ((int) mouseY - (contentY + 76)) / 25;
                        index += session.antmailListScroll;
                        AntmailMailbox mailbox = mailbox(result);
                        if (mailbox != null && mouseY >= contentY + 74 && mouseY < contentY + contentH - 48 && index >= 0 && index < mailbox.drafts().size()) {
                            restoreDraft(mailbox.drafts().get(index));
                            session.antmailMode = "compose";
                            session.antmailFocused = true;
                            session.antmailField = 1;
                            session.antmailCursor = session.antmailRecipient.length();
                        }
                    } else if (session.antmailMode.equals("message")) {
                        AntmailMessage message = selectedAntmailMessage(result);
                        if (mouseY >= contentY + 38 && mouseY < contentY + 56 && mouseX >= contentX + 170) {
                            if (message != null) AntmailNetworking.delete(message.id(), session.antmailSent);
                            session.antmailMode = session.antmailSent ? "sent" : "inbox";
                            session.antmailMessageIndex = -1;
                            session.antmailDetailScroll = 0;
                            return true;
                        }
                        if (session.antmailSent && message != null && !message.deliveryStatus().equals("DELIVERED")
                                && session.antmailRetryMessageId.isBlank()
                                && mouseY >= contentY + contentH - 50 && mouseY < contentY + contentH - 32) {
                            session.antmailRetryBaseline = AntmailClientState.get();
                            session.antmailRetryMessageId = message.id().toString();
                            session.antmailRetryTicks = 100;
                            AntmailNetworking.retry(message.id());
                            session.antmailStatus = Component.translatable("computer.antos.status.retrying_delivery").getString();
                            return true;
                        }
                        if (message != null && session.antmailAttachmentStartLine >= 0
                                && mouseY >= session.antmailAttachmentStartLine
                                && mouseY < session.antmailAttachmentStartLine + session.antmailVisibleAttachmentCount * 14
                                && !message.attachments().isEmpty()) {
                            int attachmentIndex = ((int) mouseY - session.antmailAttachmentStartLine) / 14;
                            saveAntmailAttachment(message.attachments().get(attachmentIndex));
                            return true;
                        }
                        if (message != null && !antazonProductLink(localizedMailText(message.body())).isBlank()
                                && mouseY >= contentY + 99 && mouseY < contentY + 120) {
                            openAntazonProduct(antazonProductLink(localizedMailText(message.body())));
                            return true;
                        }
                        if (mouseY >= contentY + 38 && mouseY < contentY + 56) {
                            session.antmailMode = session.antmailSent ? "sent" : "inbox";
                            session.antmailMessageIndex = -1;
                        }
                    } else if (session.antmailMode.equals("compose")) {
                if (session.antmailPickingAttachment) {
                            if (inside(contentX + 190, contentY + 95, 15, 15, mouseX, mouseY)) {
                                session.antmailPickingAttachment = false;
                                return true;
                            }
                            int row = 0;
                            for (String file : ComputerFileSystemClientState.get().files()) {
                                String[] fields = file.split("\\t", 3);
                                if (fields.length < 2 || (session.antmailPickingPaint ? !fields[0].equals("IMAGE") : !fields[0].equals("TEXT"))) continue;
                                int rowTop = contentY + 116 + row++ * 14;
                                if (rowTop > contentY + contentH - 52) break;
                                if (mouseY >= rowTop - 2 && mouseY < rowTop + 12) {
                                    session.fileExplorerSelected = fields[1];
                                    session.antmailAttachText = fields[0].equals("TEXT");
                                    session.antmailAttachPaint = fields[0].equals("IMAGE");
                                    session.antmailPickingAttachment = false;
                                    if (session.antmailAttachText) {
                                        session.textPath = fields[1];
                                        session.antmailTextPath = fields[1];
                                    } else {
                                        session.antmailPaintPath = fields[1];
                                        session.paintName = fields[1].substring(fields[1].lastIndexOf('/') + 1);
                                        session.antmailPaintAttachment = null;
                                        session.antmailPaintLoadPendingPath = fields[1];
                                    }
                                    ComputerNetworking.openFile(fields[1]);
                                    return true;
                                }
                            }
                            return true;
                        }
                        if (inside(contentX, contentY + 138, 112, 20, mouseX, mouseY)) {
                            session.antmailAttachmentMenu = !session.antmailAttachmentMenu;
                            return true;
                        }
                        if (session.antmailAttachmentMenu && inside(contentX, contentY + 160, 112, 52, mouseX, mouseY)) {
                            int choice = ((int) mouseY - (contentY + 160)) / 15;
                            session.antmailAttachmentMenu = false;
                            if (choice == 0 || choice == 1) {
                                session.antmailPickingAttachment = true;
                                session.antmailPickingPaint = choice == 1;
                            } else {
                                session.antmailAttachCoins = !session.antmailAttachCoins;
                                if (session.antmailAttachCoins) {
                                    session.antmailFocused = true;
                                    session.antmailField = 4;
                                    session.antmailCursor = session.antmailCoinAmount.length();
                                }
                            }
                            return true;
                        }
                        if (mouseY >= contentY + 57 && mouseY < contentY + 74) {
                            session.antmailField = 1;
                            session.antmailFocused = true;
                            session.antmailCursor = session.antmailRecipient.length();
                        } else if (mouseY >= contentY + 74 && mouseY < contentY + 93) {
                            session.antmailField = 2;
                            session.antmailFocused = true;
                            session.antmailCursor = session.antmailSubject.length();
                        } else if (mouseY >= contentY + 94 && mouseY < contentY + 138) {
                            session.antmailField = 3;
                            session.antmailFocused = true;
                            session.antmailCursor = session.antmailBody.length();
                        } else if (mouseY >= contentY + contentH - 26 && mouseY < contentY + contentH - 4 && mouseX >= contentX + 148) {
                            sendAntmail();
                        }
                    }
                    return true;
                }
                if (window.type.equals("ANTAZON")) {
                    if (!com.craisinlord.antos.content.client.AntazonClientState.onboardingReceived()) return true;
                    if (!com.craisinlord.antos.content.client.AntazonClientState.onboardingComplete()) {
                        if (inside(contentX + contentW - 90, contentY + contentH - 38, 78, 20, mouseX, mouseY)
                                || inside(contentX + 12, contentY + contentH - 38, 60, 20, mouseX, mouseY)) {
                            ComputerNetworking.completeAntazonOnboarding();
                        }
                        return true;
                    }
                    List<com.craisinlord.antos.content.client.AntazonClientState.ProductRow> products =
                            com.craisinlord.antos.content.client.AntazonClientState.products();
                    int[] tabWidths = antazonTabWidths(contentW);
                    int tabLeft = contentX;
                    int selectedTab = -1;
                    for (int tabIndex = 0; tabIndex < tabWidths.length; tabIndex++) {
                        if (inside(tabLeft, contentY - 3, tabWidths[tabIndex], 18, mouseX, mouseY)) {
                            selectedTab = tabIndex;
                            break;
                        }
                        tabLeft += tabWidths[tabIndex];
                    }
                    if (selectedTab >= 0) {
                        session.antazonCategoryOpen = false;
                        switch (selectedTab) {
                            case 0 -> session.antazonMode = "catalog";
                            case 1 -> {
                                session.antazonMode = "sell";
                                session.antazonSellScroll = 0;
                                session.antazonSellConfirm = false;
                                com.craisinlord.antos.content.client.AntazonClientState.clearSellFeedback();
                                ComputerNetworking.requestAntazonSellState();
                            }
                            case 2 -> {
                                session.antazonMode = "prices";
                                ComputerNetworking.requestAntazonPrices();
                            }
                            case 3 -> session.antazonMode = "wishlist";
                            case 4 -> {
                                session.antazonMode = "orders";
                                session.antazonOrderSelection = -1;
                            }
                            case 5 -> session.antazonMode = "cart";
                            default -> { }
                        }
                        return true;
                    }
                    if (session.antazonMode.equals("prices")) {
                        List<com.craisinlord.antos.content.client.AntazonClientState.PriceRow> prices = com.craisinlord.antos.content.client.AntazonClientState.prices();
                        int visible = Math.max(1, (contentH - 70) / 22);
                        int maximum = Math.max(0, prices.size() - visible);
                        if (maximum > 0 && inside(contentX + contentW - 6, contentY + 52, 6, Math.max(1, contentH - 70), mouseX, mouseY)) {
                            session.antazonPriceScroll = (int) Math.round(Math.max(0.0, Math.min(1.0,
                                    (mouseY - (contentY + 52)) / Math.max(1, contentH - 70))) * maximum);
                            return true;
                        }
                        int row = ((int) mouseY - (contentY + 52)) / 22 + session.antazonPriceScroll;
                        if (row >= 0 && row < prices.size()) {
                            var price = prices.get(row);
                            session.antazonPriceItem = price.item();
                            ComputerNetworking.prepareAntazonShipment(ResourceLocation.parse(price.item()), session.antazonPriceQuantity);
                            ComputerNetworking.requestAntazonSellState();
                        }
                    } else if (session.antazonMode.equals("sell")) {
                        List<com.craisinlord.antos.content.client.AntazonClientState.SellRow> rows = com.craisinlord.antos.content.client.AntazonClientState.sellRows();
                        int manifestTop = contentY + 68;
                        int manifestBottom = contentY + contentH - 64;
                        int visibleRows = Math.max(1, (manifestBottom - manifestTop) / 22);
                        int maximumScroll = Math.max(0, rows.size() - visibleRows);
                        if (maximumScroll > 0 && inside(contentX + contentW - 6, manifestTop, 6, Math.max(1, manifestBottom - manifestTop), mouseX, mouseY)) {
                            session.antazonSellScroll = (int) Math.round(Math.max(0.0, Math.min(1.0,
                                    (mouseY - manifestTop) / Math.max(1, manifestBottom - manifestTop))) * maximumScroll);
                            return true;
                        }
                        int controlsY = contentY + contentH - 35;
                        if (inside(contentX, controlsY, 78, 18, mouseX, mouseY)) {
                            session.antazonSellConfirm = false;
                            com.craisinlord.antos.content.client.AntazonClientState.clearSellFeedback();
                            ComputerNetworking.requestAntazonSellState();
                        } else if (inside(contentX + 84, controlsY, contentW - 92, 18, mouseX, mouseY)) {
                            String status = com.craisinlord.antos.content.client.AntazonClientState.status();
                            boolean ready = status.isBlank() && !rows.isEmpty() && rows.stream().allMatch(com.craisinlord.antos.content.client.AntazonClientState.SellRow::sellable);
                            if (ready && !session.antazonSellConfirm) session.antazonSellConfirm = true;
                            else if (ready) {
                                ComputerNetworking.sellAntazon();
                                session.antazonSellConfirm = false;
                                session.antazonWalletRefreshTicks = 100;
                            }
                        }
                    } else if (session.antazonMode.equals("catalog")) {
                        if (inside(contentX, contentY + 22, contentW - 8, 20, mouseX, mouseY)) {
                            session.antazonSearchFocused = true;
                            return true;
                        }
                        List<String> categories = antazonCategories(products);
                        int categoryWidth = Math.min(contentW - 8, 150);
                        if (inside(contentX, contentY + 45, categoryWidth, 19, mouseX, mouseY)) {
                            session.antazonCategoryOpen = !session.antazonCategoryOpen;
                            return true;
                        }
                        if (session.antazonCategoryOpen) {
                            int menuBottom = Math.min(contentY + contentH - 16, contentY + 65 + categories.size() * 18);
                            if (inside(contentX, contentY + 65, categoryWidth, Math.max(1, menuBottom - (contentY + 65)), mouseX, mouseY)) {
                                int categoryIndex = ((int) mouseY - (contentY + 65)) / 18;
                                if (categoryIndex >= 0 && categoryIndex < categories.size()) {
                                    session.antazonCategory = categories.get(categoryIndex);
                                    session.antazonCategoryOpen = false;
                                    session.antazonScroll = 0;
                                }
                                return true;
                            }
                            session.antazonCategoryOpen = false;
                            return true;
                        }
                        List<com.craisinlord.antos.content.client.AntazonClientState.ProductRow> filteredProducts = filterAntazonProducts(products);
                        int visibleProducts = Math.max(1, (contentH - 96) / 36);
                        int maximumScroll = Math.max(0, filteredProducts.size() - visibleProducts);
                        if (maximumScroll > 0 && inside(contentX + contentW - 2, contentY + 70, 5, Math.max(1, contentH - 96), mouseX, mouseY)) {
                            session.antazonScroll = (int) Math.round(Math.max(0.0, Math.min(1.0, (mouseY - (contentY + 70)) / Math.max(1, contentH - 96))) * maximumScroll);
                            return true;
                        }
                        int row = ((int) mouseY - (contentY + 70)) / 36 + session.antazonScroll;
                        if (mouseY >= contentY + 68 && mouseY < contentY + contentH - 32 && row >= 0 && row < filteredProducts.size()) {
                            session.antazonProductId = filteredProducts.get(row).id();
                            session.antazonQuantity = 1;
                            session.antazonMode = "product";
                        }
                    } else if (session.antazonMode.equals("product")) {
                        var product = products.stream().filter(value -> value.id().equals(session.antazonProductId)).findFirst().orElse(null);
                        if (product == null) return true;
                        if (inside(contentX + contentW - 145, contentY + 24, 52, 20, mouseX, mouseY)) {
                            composeAntazonMail("Antazon product link", "ANTAZON PRODUCT LINK\nantazon://product/" + product.id());
                        } else if (inside(contentX + contentW - 92, contentY + 24, 92, 20, mouseX, mouseY)) {
                            try { ComputerNetworking.toggleAntazonWishlist(ResourceLocation.parse(product.id())); }
                            catch (RuntimeException ignored) { }
                        } else {
                            if (inside(contentX, contentY + contentH - 20, 105, 20, mouseX, mouseY)) {
                                if (antazonOnCooldown(product)) return true;
                                String key = product.id() + "|default";
                                session.antazonCart.merge(key, session.antazonQuantity, Integer::sum);
                                ComputerNetworking.requestAntazon();
                            } else if (inside(contentX + 112, contentY + contentH - 20, 105, 20, mouseX, mouseY)) {
                                if (antazonOnCooldown(product)) return true;
                                try {
                                    ComputerNetworking.purchaseAntazon(ResourceLocation.parse(product.id()), "default", session.antazonQuantity);
                                    ComputerNetworking.requestAntazon();
                                }
                                catch (RuntimeException ignored) { }
                            } else if (inside(contentX, contentY + contentH - 40, 34, 20, mouseX, mouseY)) {
                                session.antazonQuantity = Math.max(1, session.antazonQuantity - 1);
                            } else if (inside(contentX + 76, contentY + contentH - 40, 34, 20, mouseX, mouseY)) {
                                session.antazonQuantity = Math.min(64, session.antazonQuantity + 1);
                            }
                        }
                    } else if (session.antazonMode.equals("cart")) {
                        int rowTop = contentY + 46;
                        for (Map.Entry<String, Integer> entry : session.antazonCart.entrySet()) {
                            if (rowTop + 35 > contentY + contentH - 30) break;
                            int controlsX = contentX + contentW - 96;
                            if (inside(contentX, rowTop - 3, contentW, 35, mouseX, mouseY)) {
                                if (inside(controlsX + 68, rowTop - 3, 24, 35, mouseX, mouseY)) {
                                    session.antazonCart.remove(entry.getKey());
                                    return true;
                                }
                                if (inside(controlsX + 42, rowTop - 3, 24, 35, mouseX, mouseY)) {
                                    session.antazonCart.put(entry.getKey(), Math.min(64, entry.getValue() + 1));
                                    return true;
                                }
                                if (inside(controlsX, rowTop - 3, 22, 35, mouseX, mouseY)) {
                                    session.antazonCart.put(entry.getKey(), Math.max(1, entry.getValue() - 1));
                                    return true;
                                }
                            }
                            rowTop += 40;
                        }
                        if (!session.antazonCart.isEmpty() && inside(contentX + contentW - 112, contentY + contentH - 24, 108, 20, mouseX, mouseY)) {
                            for (Map.Entry<String, Integer> entry : session.antazonCart.entrySet()) {
                                String[] fields = entry.getKey().split("\\|", 2);
                                var product = products.stream().filter(value -> value.id().equals(fields[0])).findFirst().orElse(null);
                                if (product != null && antazonOnCooldown(product)) continue;
                                int remaining = entry.getValue();
                                while (remaining > 0) {
                                    int batch = Math.min(64, remaining);
                                    try { ComputerNetworking.purchaseAntazon(ResourceLocation.parse(fields[0]), "default", batch); }
                                    catch (RuntimeException ignored) { break; }
                                    remaining -= batch;
                                }
                            }
                            ComputerNetworking.requestAntazon();
                        }
                    } else if (session.antazonMode.equals("wishlist")) {
                        if (inside(contentX + 72, contentY + 24, 86, 20, mouseX, mouseY)) {
                            composeAntazonMail("Antazon wishlist", antazonWishlistBody());
                        } else {
                            List<String> wishlist = session.antazonWishlist.stream().toList();
                            int visible = Math.max(1, (contentH - 62) / 36);
                            int maximum = Math.max(0, wishlist.size() - visible);
                            if (maximum > 0 && inside(contentX + contentW - 6, contentY + 46, 6, Math.max(1, contentH - 62), mouseX, mouseY)) {
                                session.antazonWishlistScroll = (int) Math.round(Math.max(0.0, Math.min(1.0,
                                        (mouseY - (contentY + 46)) / Math.max(1, contentH - 62))) * maximum);
                                return true;
                            }
                            int row = ((int) mouseY - (contentY + 46)) / 36 + session.antazonWishlistScroll;
                            if (mouseY >= contentY + 43 && mouseY < contentY + 46 + visible * 36 && row >= 0 && row < wishlist.size()) {
                                session.antazonProductId = wishlist.get(row);
                                session.antazonQuantity = 1;
                                session.antazonMode = "product";
                            }
                        }
                    } else if (session.antazonMode.equals("orders")) {
                        List<com.craisinlord.antos.content.client.AntazonClientState.OrderRow> orders = com.craisinlord.antos.content.client.AntazonClientState.orders();
                        if (session.antazonOrderSelection >= 0) {
                            if (inside(contentX, contentY + 22, 120, 20, mouseX, mouseY)) session.antazonOrderSelection = -1;
                            return true;
                        }
                        int visible = Math.max(1, (contentH - 76) / 34);
                        int maximum = Math.max(0, orders.size() - visible);
                        if (maximum > 0 && inside(contentX + contentW - 6, contentY + 34, 6, Math.max(1, contentH - 76), mouseX, mouseY)) {
                            session.antazonOrdersScroll = (int) Math.round(Math.max(0.0, Math.min(1.0,
                                    (mouseY - (contentY + 34)) / Math.max(1, contentH - 76))) * maximum);
                            return true;
                        }
                        int row = ((int) mouseY - (contentY + 34)) / 34 + session.antazonOrdersScroll;
                        if (mouseY >= contentY + 31 && mouseY < contentY + 34 + visible * 34 && row >= 0 && row < orders.size()) {
                            session.antazonOrderSelection = row;
                        }
                    }
                    return true;
                }
                if (window.type.equals("GAMES") && !window.gameOpen) {
                    List<ComputerGame> games = installedGames();
                    int index = ((int) mouseY - (contentY + 18)) / 16;
                    if (index >= 0 && index < games.size() && mouseY < contentY + 18 + games.size() * 16) {
                        ComputerGame game = games.get(index);
                        window.gameId = game.id().toString();
                        window.gameOpen = true;
                        gameSessions.computeIfAbsent(game.id(), ignored -> game.create());
                        activeWindow = window;
                        return true;
                    }
                }
                if (window.type.equals("WALLPAPERS")) {
                    List<ComputerGuideData.Wallpaper> available = unlockedWallpapers();
                    int visible = Math.max(1, (contentH - 34) / 38);
                    int row = ((int) mouseY - (contentY + 18)) / 38;
                    int index = session.wallpaperScroll + row;
                    if (row >= 0 && row < visible && index < available.size()) {
                        ComputerGuideData.Wallpaper selected = available.get(index);
                        ComputerNetworking.selectWallpaper(selected.id());
                    }
                    return true;
                }
                if (window.type.equals("TERMINAL")) { session.terminalFocused = true; return true; }
            }
            if (window.type.equals("SETTINGS") && inside(contentX + 112, contentY + 189, Math.min(102, Math.max(0, contentW - 112)), 15, mouseX, mouseY)) {
                if (selectedDisk != null) ejectSelected();
                return true;
            }
            if (window.type.equals("SETTINGS") && inside(contentX, contentY + 69, Math.min(214, contentW), 15, mouseX, mouseY)) {
                open("WALLPAPERS");
                return true;
            }
            if (window.type.equals("SETTINGS") && inside(contentX, contentY + 85, Math.min(214, contentW), 15, mouseX, mouseY)) {
                if (faceIdWaiting) return true;
                faceIdMessage = "";
                if (AnternetAccountClientState.faceIdLinked()) {
                    if (!faceIdLinkedToCurrentAccount()) {
                        faceIdMessage = "FACE ID BELONGS TO ANOTHER ACCOUNT";
                    } else {
                        faceIdWaiting = true;
                        faceIdExpectedLinked = false;
                        faceIdRevisionAtRequest = AnternetAccountClientState.faceIdStatusRevision();
                        faceIdMessage = "UNLINKING PLAYER...";
                        AnternetAccountNetworking.unlinkFaceId();
                    }
                } else {
                    submitFaceIdLink();
                }
                return true;
            }
            if (window.type.equals("SETTINGS") && inside(contentX, contentY + 120, 104, 15, mouseX, mouseY)) {
                session.windows.clear();
                activeWindow = null;
                session.onboardingCompleted = false;
                session.onboardingStep = 0;
                return true;
            }
            if (window.type.equals("SETTINGS") && inside(contentX + 110, contentY + 120, Math.min(104, Math.max(0, contentW - 110)), 15, mouseX, mouseY)) {
                session.windows.clear();
                activeWindow = null;
                ComputerNetworking.resetAntazonOnboarding();
                com.craisinlord.antos.content.client.AntazonClientState.clear();
                open("ANTAZON");
                return true;
            }
            if (window.type.equals("SETTINGS") && inside(contentX, contentY + 189, Math.min(102, contentW), 15, mouseX, mouseY)) {
                ComputerNetworking.logout();
                com.craisinlord.antos.content.network.AnternetAccountNetworking.logout();
                com.craisinlord.antos.content.client.AnternetAccountClientState.clear();
                AnternetAccountNetworking.checkFaceId();
                loggedIn = false;
                session.authenticated = false;
                session.windows.clear();
                session.antmailPickingAttachment = false;
                activeWindow = null;
                return true;
            }
            if (inside(x, y + TITLE_BAR_HEIGHT, w, h - TITLE_BAR_HEIGHT, mouseX, mouseY)) return true;
            if (inside(x, y, w, TITLE_BAR_HEIGHT, mouseX, mouseY)) {
                activeWindow = window;
                session.windows.remove(i);
                session.windows.add(window);
                int controlsLeft = x + w - WINDOW_CONTROL_WIDTH * WINDOW_CONTROL_COUNT;
                if (inside(controlsLeft + WINDOW_CONTROL_WIDTH * 2, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT, mouseX, mouseY)) {
                    if (window.type.equals("ANTMAIL") && session.antmailMode.equals("compose")) saveAntmailDraft();
                    if (window.type.equals("ANTMAIL")) session.antmailPickingAttachment = false;
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
        if (archiveScrollbarDragging != null) updateArchiveScrollbar(archiveScrollbarDragging, mouseY);
        if (dragging != null) {
            int l = -WIDTH / 2;
            int t = -HEIGHT / 2;
            dragging.x = Math.max(12, Math.min(WIDTH - dragging.renderWidth - 12, (int) mouseX - l - this.dragX));
            dragging.y = Math.max(34, Math.min(HEIGHT - dragging.renderHeight - 12, (int) mouseY - t - this.dragY));
        }
        if (paintStrokeActive && button == paintStrokeButton && activeWindow != null && activeWindow.type.equals("PAINT")) {
            int l = -WIDTH / 2;
            int t = -HEIGHT / 2;
            int x = l + windowLocalX(activeWindow) + 8;
            int y = t + windowLocalY(activeWindow) + 58;
            paintAt(mouseX, mouseY, x, y, activeWindow.renderWidth - 16, Math.max(1, activeWindow.renderHeight - 66));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        float scale = uiScale();
        mouseX = (mouseX - width / 2.0F) / scale;
        mouseY = (mouseY - height / 2.0F) / scale;
        dragging = null;
        archiveScrollbarDragging = null;
        if (button == paintStrokeButton) {
            paintStrokeActive = false;
            paintStrokeButton = -1;
            lastPaintPixelX = -1;
            lastPaintPixelY = -1;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Window hoveredWindow = windowAt(mouseX, mouseY);
        if (!loggedIn || hoveredWindow == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        activeWindow = hoveredWindow;
        if (activeWindow.type.equals("FILES")) {
            session.fileExplorerScroll = Math.max(0, Math.min(session.fileExplorerMaximumScroll,
                    session.fileExplorerScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (activeWindow.type.equals("TASKS")) {
            float scale = uiScale();
            double localX = (mouseX - width / 2.0F) / scale, localY = (mouseY - height / 2.0F) / scale;
            int windowX = -WIDTH / 2 + windowLocalX(activeWindow), windowY = -HEIGHT / 2 + windowLocalY(activeWindow);
            int sidebarX = windowX + 8, sidebarY = windowY + 28, contentHeight = windowHeight(activeWindow) - 34;
            int contentWidth = windowWidth(activeWindow) - 16;
            if (inside(sidebarX, sidebarY + 18, 102, Math.max(1, contentHeight - 18), localX, localY)) {
                int visible = Math.max(1, (contentHeight - 32) / 19);
                taskCategoryScroll = Math.max(0, Math.min(Math.max(0, visibleTaskCategories().size() - visible),
                        taskCategoryScroll - (int) Math.signum(scrollY)));
            } else if (!selectedTaskId.isBlank()
                    && inside(sidebarX + 111, sidebarY + 19, Math.max(1, contentWidth - 111), Math.max(1, contentHeight - 36), localX, localY)) {
                taskDetailScroll = Math.max(0, taskDetailScroll - (int) Math.signum(scrollY) * 22);
            } else if (selectedTaskId.isBlank()
                    && inside(sidebarX + 111, sidebarY + 30, Math.max(1, contentWidth - 111), Math.max(1, contentHeight - 48), localX, localY)) {
                int mapX = sidebarX + 111, mapY = sidebarY + 30;
                int mapW = contentWidth - 111, mapH = Math.max(1, contentHeight - 48);
                List<com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow> categoryRows =
                        com.craisinlord.antos.content.client.ComputerTasksClientState.get().stream()
                                .filter(com.craisinlord.antos.content.client.ComputerTasksClientState.TaskRow::visible)
                                .filter(task -> task.category().equals(selectedTaskCategory)).toList();
                int[] limits = taskMapScrollLimits(categoryRows, mapX, mapY, mapW, mapH);
                if (hasShiftDown()) taskMapScrollX = Math.max(0, Math.min(limits[0], taskMapScrollX - (int) Math.signum(scrollY) * 36));
                else taskMapScrollY = Math.max(0, Math.min(limits[1], taskMapScrollY - (int) Math.signum(scrollY) * 36));
            }
            return true;
        }
        if (activeWindow.type.equals("TEXT") && session.textListing) {
            session.textPickerScroll = Math.max(0, Math.min(session.textPickerMaximumScroll,
                    session.textPickerScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (activeWindow.type.equals("TEXT")) {
            session.textScroll = Math.max(0, Math.min(session.textMaximumScroll,
                    session.textScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (activeWindow.type.equals("TERMINAL")) {
            session.terminalScroll = Math.max(0, Math.min(session.terminalMaximumScroll,
                    session.terminalScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (activeWindow.type.equals("ANTMAIL")) {
            if (session.antmailMode.equals("message")) {
                session.antmailDetailScroll = Math.max(0, Math.min(session.antmailDetailMaximumScroll,
                        session.antmailDetailScroll - (int) Math.signum(scrollY)));
            } else if (session.antmailMode.equals("inbox") || session.antmailMode.equals("sent") || session.antmailMode.equals("drafts")) {
                session.antmailListScroll = Math.max(0, Math.min(session.antmailListMaximumScroll,
                        session.antmailListScroll - (int) Math.signum(scrollY)));
            }
            return true;
        }
        if (activeWindow.type.equals("ANTAZON") && session.antazonMode.equals("prices")) {
            float scale = uiScale();
            double localX = (mouseX - width / 2.0F) / scale, localY = (mouseY - height / 2.0F) / scale;
            int windowX = -WIDTH / 2 + windowLocalX(activeWindow), windowY = -HEIGHT / 2 + windowLocalY(activeWindow);
            int contentX = windowX + 8, contentY = windowY + 28, contentW = windowWidth(activeWindow) - 16, contentH = windowHeight(activeWindow) - 34;
            int row = ((int) localY - (contentY + 52)) / 22 + session.antazonPriceScroll;
            List<com.craisinlord.antos.content.client.AntazonClientState.PriceRow> prices = com.craisinlord.antos.content.client.AntazonClientState.prices();
            if (row >= 0 && row < prices.size() && localX >= contentX && localX < contentX + contentW - 8) {
                session.antazonPriceItem = prices.get(row).item();
                int[] quantities = {1, 16, 32, 64};
                int current = 0;
                for (int index = 0; index < quantities.length; index++) if (quantities[index] == session.antazonPriceQuantity) current = index;
                session.antazonPriceQuantity = quantities[Math.max(0, Math.min(quantities.length - 1, current - (int) Math.signum(scrollY)))];
                return true;
            }
            int visible = Math.max(1, (contentH - 70) / 22);
            int maximum = Math.max(0, prices.size() - visible);
            session.antazonPriceScroll = Math.max(0, Math.min(maximum, session.antazonPriceScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (activeWindow.type.equals("ANTAZON") && (session.antazonMode.equals("wishlist") || session.antazonMode.equals("orders"))) {
            int contentH = windowHeight(activeWindow) - 34;
            if (session.antazonMode.equals("wishlist")) {
                int visible = Math.max(1, (contentH - 62) / 36);
                int maximum = Math.max(0, session.antazonWishlist.size() - visible);
                session.antazonWishlistScroll = Math.max(0, Math.min(maximum, session.antazonWishlistScroll - (int) Math.signum(scrollY)));
            } else {
                int visible = Math.max(1, (contentH - 76) / 34);
                int maximum = Math.max(0, com.craisinlord.antos.content.client.AntazonClientState.orders().size() - visible);
                session.antazonOrdersScroll = Math.max(0, Math.min(maximum, session.antazonOrdersScroll - (int) Math.signum(scrollY)));
            }
            return true;
        }
        if (activeWindow.type.equals("ANTAZON") && session.antazonMode.equals("sell")) {
            float scale = uiScale();
            double localX = (mouseX - width / 2.0F) / scale, localY = (mouseY - height / 2.0F) / scale;
            int windowX = -WIDTH / 2 + windowLocalX(activeWindow), windowY = -HEIGHT / 2 + windowLocalY(activeWindow);
            int contentX = windowX + 8, contentY = windowY + 28, contentW = windowWidth(activeWindow) - 16, contentH = windowHeight(activeWindow) - 34;
            int manifestTop = contentY + 68, manifestBottom = contentY + contentH - 64;
            if (localX >= contentX && localX < contentX + contentW - 8 && localY >= manifestTop && localY < manifestBottom) {
                List<com.craisinlord.antos.content.client.AntazonClientState.SellRow> rows = com.craisinlord.antos.content.client.AntazonClientState.sellRows();
                int visibleRows = Math.max(1, (manifestBottom - manifestTop) / 22);
                int maximum = Math.max(0, rows.size() - visibleRows);
                session.antazonSellScroll = Math.max(0, Math.min(maximum, session.antazonSellScroll - (int) Math.signum(scrollY)));
            }
            return true;
        }
        if (activeWindow.type.equals("ANTAZON") && session.antazonMode.equals("catalog")) {
            List<com.craisinlord.antos.content.client.AntazonClientState.ProductRow> products = filterAntazonProducts(com.craisinlord.antos.content.client.AntazonClientState.products());
            int visible = Math.max(1, (windowHeight(activeWindow) - 34 - 96) / 36);
            int maximum = Math.max(0, products.size() - visible);
            session.antazonScroll = Math.max(0, Math.min(maximum, session.antazonScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (activeWindow.type.equals("ARCHIVE")) {
            if (session.archiveEntryId == null) {
                List<ComputerGuideData.Entry> entries = ComputerGuideData.entriesFor(workspaceDiskIds(),
                        com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get());
                int columns = activeWindow.maximized ? 3 : 1;
                int cellHeight = activeWindow.maximized ? 72 : 38;
                int visibleRows = Math.max(1, (windowHeight(activeWindow) - 34 - 58) / cellHeight);
                int totalRows = (filterArchiveEntries(entries).size() + columns - 1) / columns;
                int maximum = Math.max(0, totalRows - visibleRows);
                session.archiveScroll = Math.max(0, Math.min(maximum,
                        session.archiveScroll - (int) Math.signum(scrollY)));
            } else {
                session.archiveDetailScroll = Math.max(0, session.archiveDetailScroll - (int) Math.signum(scrollY));
            }
            return true;
        }
        if (activeWindow.type.equals("WALLPAPERS")) {
            int visible = Math.max(1, (activeWindow.renderHeight - 68) / 38);
            int maximum = Math.max(0, unlockedWallpapers().size() - visible);
            session.wallpaperScroll = Math.max(0, Math.min(maximum, session.wallpaperScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!loggedIn) {
            if (accountPasswordFocused) {
                if (codePoint >= 32 && accountPassword.length() < 128) accountPassword += codePoint;
            } else if ((Character.isLetterOrDigit(codePoint) || codePoint == '_') && accountUsername.length() < 16) accountUsername += codePoint;
            return true;
        }
        if (loggedIn && activeWindow != null && activeWindow.type.equals("FILES") && session.fileExplorerRenaming && codePoint >= 32 && codePoint != '/' && codePoint != '\\') {
            if (session.fileExplorerRename.length() < 48) session.fileExplorerRename += codePoint;
            return true;
        }
        if (loggedIn && activeWindow != null && activeWindow.type.equals("ARCHIVE") && session.archiveSearchFocused && codePoint >= 32 && session.archiveSearch.length() < 48) {
            session.archiveSearch += codePoint;
            session.archiveScroll = 0;
            return true;
        }
        if (loggedIn && activeWindow != null && activeWindow.type.equals("ANTAZON") && session.antazonSearchFocused && codePoint >= 32 && session.antazonSearch.length() < 48) {
            session.antazonSearch += codePoint;
            session.antazonScroll = 0;
            return true;
        }
        if (loggedIn && activeWindow != null && activeWindow.type.equals("FILES") && session.fileExplorerCreatingDirectory && codePoint >= 32 && codePoint != '/' && codePoint != '\\') {
            if (session.fileExplorerRename.length() < 48) session.fileExplorerRename += codePoint;
            return true;
        }
        if (loggedIn && activeWindow != null && activeWindow.type.equals("FILES") && session.fileExplorerMoving && codePoint >= 32 && codePoint != '\\') {
            if (session.fileExplorerRename.length() < 64) session.fileExplorerRename += codePoint;
            return true;
        }
        if (loggedIn && activeWindow != null && activeWindow.type.equals("TEXT") && session.textRenaming && codePoint >= 32 && codePoint != '/' && codePoint != '\\') {
            if (session.textRename.length() < 48) session.textRename += codePoint;
            return true;
        }
        if (loggedIn && activeWindow != null) {
            if (activeWindow.type.equals("GAMES") && activeWindow.gameOpen && gameSession(activeWindow).map(game -> game.charTyped(codePoint)).orElse(false)) return true;
            if (activeWindow.type.equals("TEXT") && session.textFocused && codePoint >= 32) {
                replaceTextSelection(String.valueOf(codePoint));
                session.textDirty = true;
                return true;
            }
            if (activeWindow.type.equals("TERMINAL") && session.terminalFocused && codePoint >= 32) { session.terminalInput += codePoint; return true; }
            if (activeWindow.type.equals("ANTMAIL") && session.antmailFocused && codePoint >= 32) {
                if (session.antmailField >= 1 && session.antmailField <= 3) replaceAntmailSelection(String.valueOf(codePoint));
                else if (session.antmailField == 4 && Character.isDigit(codePoint)) replaceAntmailSelection(String.valueOf(codePoint));
                return true;
            }
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
        if (activeWindow != null && activeWindow.type.equals("GAMES") && activeWindow.gameOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                gameSession(activeWindow).ifPresent(ComputerGame.Session::close);
                gameSessions.remove(ResourceLocation.tryParse(activeWindow.gameId));
                activeWindow.gameOpen = false;
                return true;
            }
            if (gameSession(activeWindow).map(game -> game.keyPressed(keyCode)).orElse(false)) return true;
        }
        if (activeWindow != null && activeWindow.type.equals("FILES")) {
            if (session.fileExplorerDeleteConfirm) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { session.fileExplorerDeleteConfirm = false; return true; }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { session.fileExplorerDeleteConfirm = false; deleteSelectedFile(); return true; }
                return true;
            }
            if (session.fileExplorerCreatingDirectory) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (!session.fileExplorerRename.isEmpty()) session.fileExplorerRename = session.fileExplorerRename.substring(0, session.fileExplorerRename.length() - 1); return true; }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { session.fileExplorerCreatingDirectory = false; session.fileExplorerRename = ""; return true; }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    if (!session.fileExplorerRename.isBlank()) {
                        String folder = session.fileExplorerRename.replace("\\", "\\\\").replace("\"", "\\\"");
                        ComputerNetworking.terminalCommand(session.fileExplorerDirectory, "mkdir \"" + folder + "\"");
                    }
                    session.fileExplorerCreatingDirectory = false;
                    session.fileExplorerRename = "";
                    ComputerNetworking.listFiles();
                    return true;
                }
            }
            if (session.fileExplorerMoving) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (!session.fileExplorerRename.isEmpty()) session.fileExplorerRename = session.fileExplorerRename.substring(0, session.fileExplorerRename.length() - 1); return true; }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { session.fileExplorerMoving = false; session.fileExplorerRename = ""; return true; }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    if (!session.fileExplorerRename.isBlank() && !session.fileExplorerSelected.isBlank()) {
                        String destination = session.fileExplorerRename.startsWith("/")
                                ? ComputerFileSystem.normalize(session.fileExplorerRename)
                                : ComputerFileSystem.normalize(session.fileExplorerDirectory + "/" + session.fileExplorerRename);
                        ComputerNetworking.moveFile(session.fileExplorerSelected, destination);
                    }
                    session.fileExplorerMoving = false;
                    session.fileExplorerRename = "";
                    ComputerNetworking.listFiles();
                    return true;
                }
            }
            if (session.fileExplorerRenaming) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!session.fileExplorerRename.isEmpty()) session.fileExplorerRename = session.fileExplorerRename.substring(0, session.fileExplorerRename.length() - 1);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    session.fileExplorerRenaming = false;
                    session.fileExplorerRename = "";
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    renameSelectedFile();
                    return true;
                }
            }
            if (session.fileExplorerCreatingDirectory || session.fileExplorerMoving || session.fileExplorerRenaming) return true;
            if (keyCode == GLFW.GLFW_KEY_R && hasControl(modifiers)) {
                beginRenameSelectedFile();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_DELETE && fileExplorerSelectionMutable()) {
                session.fileExplorerCreatingDirectory = false;
                session.fileExplorerMoving = false;
                session.fileExplorerRenaming = false;
                session.fileExplorerDeleteConfirm = true;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_N && hasControl(modifiers)) {
                beginCreateFileExplorerDirectory();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_M && hasControl(modifiers) && fileExplorerSelectionMutable()) {
                beginMoveFileExplorerSelection();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_F5) {
                session.fileExplorerScroll = 0;
                ComputerNetworking.listFiles();
                return true;
            }
        }
        if (activeWindow != null && activeWindow.type.equals("TERMINAL") && session.terminalFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (!session.terminalInput.isEmpty()) session.terminalInput = session.terminalInput.substring(0, session.terminalInput.length() - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { executeTerminal(); return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { session.terminalFocused = false; return true; }
        }

        if (activeWindow != null && activeWindow.type.equals("ANTMAIL")
                && session.antmailPickingAttachment && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            session.antmailPickingAttachment = false;
            return true;
        }
        if (activeWindow != null && activeWindow.type.equals("ANTMAIL")
                && session.antmailAttachmentMenu && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            session.antmailAttachmentMenu = false;
            return true;
        }
        if (activeWindow != null && activeWindow.type.equals("ANTMAIL") && session.antmailFocused) {
            if (hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_A) { session.antmailSelectionStart = 0; session.antmailCursor = antmailText().length(); return true; }
            if (hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_C) { if (hasAntmailSelection()) minecraft.keyboardHandler.setClipboard(selectedAntmailText()); return true; }
            if (hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_X) { if (hasAntmailSelection()) { minecraft.keyboardHandler.setClipboard(selectedAntmailText()); replaceAntmailSelection(""); } return true; }
            if (hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_V) {
                String clip = minecraft.keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    clip = clip.replace("\r", "");
                    replaceAntmailSelection(clip);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (hasAntmailSelection()) replaceAntmailSelection("");
                else if (session.antmailCursor > 0) { session.antmailSelectionStart = session.antmailCursor - 1; replaceAntmailSelection(""); }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (hasAntmailSelection()) replaceAntmailSelection("");
                else if (session.antmailCursor < antmailText().length()) { session.antmailSelectionStart = session.antmailCursor; session.antmailCursor++; replaceAntmailSelection(""); }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) { session.antmailCursor = Math.max(0, session.antmailCursor - 1); session.antmailSelectionStart = -1; return true; }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { session.antmailCursor = Math.min(antmailText().length(), session.antmailCursor + 1); session.antmailSelectionStart = -1; return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (session.antmailMode.equals("compose")) {
                    if (session.antmailField == 1) {
                        session.antmailField = 2;
                        session.antmailCursor = session.antmailSubject.length();
                    } else if (session.antmailField == 2) {
                        session.antmailField = 3;
                        session.antmailCursor = session.antmailBody.length();
                    } else if (session.antmailField == 4) {
                        session.antmailFocused = false;
                    } else if (session.antmailField == 3) {
                        replaceAntmailSelection("\n");
                    }
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { session.antmailFocused = false; return true; }
        }
        if (activeWindow != null && activeWindow.type.equals("TEXT") && session.textFocused) {
            if (hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_A) { session.textSelectionStart = 0; session.textCursor = session.textContent.length(); return true; }
            if (hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_C) { if (hasTextSelection()) minecraft.keyboardHandler.setClipboard(selectedText()); return true; }
            if (hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_X) { if (hasTextSelection()) { minecraft.keyboardHandler.setClipboard(selectedText()); replaceTextSelection(""); session.textDirty = true; } return true; }
            if (hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_V) { String clip = minecraft.keyboardHandler.getClipboard(); if (clip != null && !clip.isEmpty()) { replaceTextSelection(clip.replace("\r", "")); session.textDirty = true; } return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (hasTextSelection()) { replaceTextSelection(""); session.textDirty = true; } else if (session.textCursor > 0) { session.textContent = session.textContent.substring(0, session.textCursor - 1) + session.textContent.substring(session.textCursor); session.textCursor--; session.textDirty = true; } return true; }
            if (keyCode == GLFW.GLFW_KEY_DELETE && !hasControl(modifiers)) { if (hasTextSelection()) { replaceTextSelection(""); session.textDirty = true; } else if (session.textCursor < session.textContent.length()) { session.textContent = session.textContent.substring(0, session.textCursor) + session.textContent.substring(session.textCursor + 1); session.textDirty = true; } return true; }
            if (keyCode == GLFW.GLFW_KEY_LEFT) { session.textCursor = Math.max(0, session.textCursor - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { session.textCursor = Math.min(session.textContent.length(), session.textCursor + 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) { moveTextCursorVertically(keyCode == GLFW.GLFW_KEY_UP); return true; }
            if (keyCode == GLFW.GLFW_KEY_HOME) { session.textCursor = session.textContent.lastIndexOf('\n', Math.max(0, session.textCursor - 1)) + 1; return true; }
            if (keyCode == GLFW.GLFW_KEY_END) { int next = session.textContent.indexOf('\n', session.textCursor); session.textCursor = next < 0 ? session.textContent.length() : next; return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER) { replaceTextSelection("\n"); session.textDirty = true; return true; }
            if (keyCode == GLFW.GLFW_KEY_S && hasControl(modifiers) && (modifiers & GLFW.GLFW_MOD_SHIFT) == 0) { saveText(); return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { session.textFocused = false; return true; }
        }
        if (activeWindow != null && activeWindow.type.equals("TEXT")) {
            if (session.textDeleteConfirm) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { session.textDeleteConfirm = false; return true; }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { session.textDeleteConfirm = false; deleteTextFile(); return true; }
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE && hasControl(modifiers)) { session.textDeleteConfirm = true; session.textFocused = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_S && hasControl(modifiers) && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                beginSaveAs();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_R && hasControl(modifiers) && !session.textPath.isBlank()) {
                int slash = session.textPath.lastIndexOf('/');
                session.textRename = session.textPath.substring(slash + 1);
                session.textRenaming = true;
                return true;
            }
            if (session.textRenaming) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!session.textRename.isEmpty()) session.textRename = session.textRename.substring(0, session.textRename.length() - 1);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    session.textRenaming = false;
                    session.textRename = "";
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    renameTextFile();
                    return true;
                }
            }
        }
        if (activeWindow != null && activeWindow.type.equals("ARCHIVE") && session.archiveEntryId != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            session.archiveEntryId = null;
            session.archiveDetailScroll = 0;
            return true;
        }
        if (activeWindow != null && activeWindow.type.equals("ARCHIVE") && session.archiveSearchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!session.archiveSearch.isEmpty()) session.archiveSearch = session.archiveSearch.substring(0, session.archiveSearch.length() - 1);
                session.archiveScroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                session.archiveSearchFocused = false;
                return true;
            }
        }
        if (activeWindow != null && activeWindow.type.equals("ANTAZON") && session.antazonSearchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!session.antazonSearch.isEmpty()) session.antazonSearch = session.antazonSearch.substring(0, session.antazonSearch.length() - 1);
                session.antazonScroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                session.antazonSearchFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean hasTextSelection() { return session.textSelectionStart >= 0 && session.textSelectionStart != session.textCursor; }
    private String selectedText() { int start = Math.min(session.textSelectionStart, session.textCursor); int end = Math.max(session.textSelectionStart, session.textCursor); return session.textContent.substring(start, end); }
    private void replaceTextSelection(String text) {
        int start = hasTextSelection() ? Math.min(session.textSelectionStart, session.textCursor) : session.textCursor;
        int end = hasTextSelection() ? Math.max(session.textSelectionStart, session.textCursor) : session.textCursor;
        session.textContent = session.textContent.substring(0, start) + text + session.textContent.substring(end);
        session.textCursor = start + text.length();
        session.textSelectionStart = -1;
    }

    private String antmailText() {
        return switch (session.antmailField) {
            case 1 -> session.antmailRecipient;
            case 2 -> session.antmailSubject;
            case 3 -> session.antmailBody;
            case 4 -> session.antmailCoinAmount;
            default -> "";
        };
    }

    private boolean hasAntmailSelection() { return session.antmailSelectionStart >= 0 && session.antmailSelectionStart != session.antmailCursor; }
    private String selectedAntmailText() {
        int start = Math.min(session.antmailSelectionStart, session.antmailCursor);
        int end = Math.max(session.antmailSelectionStart, session.antmailCursor);
        return antmailText().substring(start, end);
    }
    private void replaceAntmailSelection(String text) {
        String current = antmailText();
        int cursor = Math.min(session.antmailCursor, current.length());
        int start = hasAntmailSelection() ? Math.min(session.antmailSelectionStart, cursor) : cursor;
        int end = hasAntmailSelection() ? Math.max(session.antmailSelectionStart, cursor) : cursor;
        int maximum = session.antmailField == 3 ? 16384 : session.antmailField == 4 ? 12 : 64;
        String replacement = text.substring(0, Math.min(text.length(), Math.max(0, maximum - (current.length() - (end - start)))));
        String updated = current.substring(0, start) + replacement + current.substring(end);
        if (session.antmailField == 1) session.antmailRecipient = updated;
        else if (session.antmailField == 2) session.antmailSubject = updated;
        else if (session.antmailField == 3) session.antmailBody = updated;
        else if (session.antmailField == 4) session.antmailCoinAmount = updated.replaceAll("[^0-9]", "");
        session.antmailCursor = start + replacement.length();
        session.antmailSelectionStart = -1;
    }

    private void executeTerminal() {
        String input = session.terminalInput.trim();
        if (input.isEmpty()) return;
        session.terminalOutput.add(session.terminalDirectory + "> " + input);
        ComputerNetworking.terminalCommand(session.terminalDirectory, input);
        session.terminalInput = "";
    }

    private void requestAntmailState() {
        requestAntmailState(false);
    }

    private void requestAntmailState(boolean force) {
        int folder = session.antmailMode.equals("sent") ? com.craisinlord.antos.content.network.AntmailStateRequestPayload.SENT
                : session.antmailMode.equals("drafts") ? com.craisinlord.antos.content.network.AntmailStateRequestPayload.DRAFTS
                : com.craisinlord.antos.content.network.AntmailStateRequestPayload.INBOX;
        AntmailNetworking.requestState(folder, session.antmailPage, force ? 0L : AntmailClientState.getVersion());
        if (force) session.antmailStateWaitTicks = 0;
    }

    private String antazonWishlistBody() {
        StringBuilder body = new StringBuilder("ANTAZON WISHLIST\n");
        for (String product : session.antazonWishlist) body.append("antazon://product/").append(product).append('\n');
        return body.toString().trim();
    }

    private String prettyAntazonPayment(String payment) {
        int separator = payment.indexOf(' ');
        if (separator < 0) return payment;
        String amount = payment.substring(0, separator);
        String resource = payment.substring(separator + 1);
        int namespaceSeparator = resource.indexOf(':');
        if (namespaceSeparator >= 0) resource = resource.substring(namespaceSeparator + 1);
        StringBuilder result = new StringBuilder();
        for (String part : resource.split("_")) {
            if (part.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return amount + " " + result;
    }

    private String prettyAntazonItem(String item) {
        int separator = item.indexOf(':');
        String resource = separator >= 0 ? item.substring(separator + 1) : item;
        StringBuilder result = new StringBuilder();
        for (String part : resource.split("_")) {
            if (part.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private String antazonOrderPayment(String payment) {
        if (payment == null || payment.isBlank() || payment.equals("standard")) return "METHOD NOT RECORDED";
        return prettyAntazonPayment(payment);
    }

    private boolean antazonOnCooldown(com.craisinlord.antos.content.client.AntazonClientState.ProductRow product) {
        return product.cooldownEnds() > antazonGameTime();
    }

    private long antazonGameTime() {
        return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
    }

    private String antazonCooldown(com.craisinlord.antos.content.client.AntazonClientState.ProductRow product) {
        long remaining = Math.max(0L, product.cooldownEnds() - antazonGameTime());
        long days = remaining / 24000L;
        long hours = remaining % 24000L / 1000L;
        long minutes = remaining % 1000L * 60L / 1000L;
        if (days > 0L) return days + "D " + hours + "H";
        if (hours > 0L) return hours + "H " + minutes + "M";
        return Math.max(1L, minutes) + "M";
    }

    private String antazonSellMessage(String status) {
        return switch (status) {
            case "SHIPMENT_ACCEPTED" -> Component.translatable("computer.antos.status.shipment_accepted").getString();
            case "CRATE_CHANGED" -> Component.translatable("computer.antos.status.crate_changed").getString();
            case "UNAUTHORIZED" -> Component.translatable("computer.antos.status.shipping_unavailable").getString();
            case "INVALID_REQUEST", "SHIPMENT_FAILED" -> Component.translatable("computer.antos.status.shipment_failed").getString();
            default -> status.replace('_', ' ');
        };
    }

    private String antazonProductLink(String body) {
        int start = body.indexOf("antazon://product/");
        if (start < 0) return "";
        int end = start + "antazon://product/".length();
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) end++;
        return body.substring(start + "antazon://product/".length(), end);
    }

    private void openAntazonProduct(String productId) {
        try {
            ResourceLocation.parse(productId);
            open("ANTAZON");
            session.antazonProductId = productId;
            session.antazonQuantity = 1;
            session.antazonMode = "product";
        } catch (RuntimeException ignored) { }
    }

    private void composeAntazonMail(String subject, String body) {
        open("ANTMAIL");
        session.antmailMode = "compose";
        session.antmailSent = false;
        session.antmailRecipient = "";
        session.antmailSubject = subject;
        session.antmailBody = body;
        session.antmailFocused = true;
        session.antmailField = 1;
        session.antmailCursor = 0;
        session.antmailStatus = Component.translatable("computer.antos.status.antazon_link_ready").getString();
    }

    private void sendAntmail() {
        if (!AntmailAddress.isValidAddress(session.antmailRecipient)) {
            session.antmailStatus = Component.translatable("computer.antos.status.invalid_recipient").getString();
            return;
        }
        if (session.antmailSubject.isBlank() || session.antmailBody.isBlank()) {
            session.antmailStatus = Component.translatable("computer.antos.status.subject_body_required").getString();
            return;
        }
        if (session.antmailAttachCoins) {
            try {
                if (Long.parseLong(session.antmailCoinAmount) < 1) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                session.antmailStatus = Component.translatable("computer.antos.status.positive_antcoins_required").getString();
                return;
            }
        }
        if (session.antmailAttachText && (session.textDirty || session.textPath.isBlank() || session.textPath.contains("untitled") || !session.textPath.equals(session.antmailTextPath))) {
                session.antmailStatus = Component.translatable("computer.antos.status.save_text_file_first").getString();
            return;
        }
        if (session.antmailAttachPaint && !session.antmailPaintLoadPendingPath.isBlank()) {
                session.antmailStatus = Component.translatable("computer.antos.status.attachment_loading").getString();
            return;
        }
        if (session.antmailAttachPaint && session.antmailPaintAttachment == null
                && (session.paintDirty || session.paintName.isBlank() || session.paintName.startsWith("UNTITLED")
                || !session.antmailPaintPath.endsWith(session.paintName))) {
                session.antmailStatus = Component.translatable("computer.antos.status.save_painting_first").getString();
            return;
        }
        List<AntmailAttachment> attachments = composeAttachments();
        session.antmailSendBaseline = AntmailClientState.get();
        AntmailNetworking.send(session.antmailRecipient, session.antmailSubject, session.antmailBody, attachments);
        if (session.antmailAttachCoins) com.craisinlord.antos.content.network.ComputerNetworking.requestAntazonWallet();
        session.antmailStatus = Component.translatable("computer.antos.status.sending_message").getString();
        session.antmailSendPending = true;
        session.antmailFocused = false;
        session.antmailDraftId = null;
        session.antmailDraftLoaded = false;
        session.antmailPickingAttachment = false;
    }

    private void restoreLatestDraft(AntmailAnternetResultPayload result) {
        if (session.antmailDraftLoaded) return;
        AntmailMailbox mailbox = mailbox(result);
        if (mailbox == null || mailbox.drafts().isEmpty()) {
            session.antmailDraftLoaded = true;
            return;
        }
        restoreDraft(mailbox.drafts().getLast());
            session.antmailStatus = Component.translatable("computer.antos.status.draft_recovered").getString();
        session.antmailDraftLoaded = true;
    }

    private void restoreDraft(AntmailDraft draft) {
        session.antmailDraftId = draft.id();
        session.antmailRecipient = draft.recipient() == null ? "" : draft.recipient().fullAddress();
        session.antmailSubject = draft.subject();
        session.antmailBody = draft.body();
        session.antmailAttachText = false;
        session.antmailAttachPaint = false;
        session.antmailAttachCoins = false;
        session.antmailCoinAmount = "";
        session.antmailPickingAttachment = false;
        for (AntmailAttachment attachment : draft.attachments()) {
            if (attachment instanceof AntmailAttachment.TextFile text) {
                session.antmailAttachText = true;
                session.textPath = "/documents/" + text.fileName();
                session.antmailTextPath = session.textPath;
                session.textContent = text.contents();
            } else if (attachment instanceof AntmailAttachment.PaintImage paint) {
                session.antmailAttachPaint = true;
                session.paintName = paint.fileName();
                session.antmailPaintPath = "/pictures/" + paint.fileName();
                session.antmailPaintAttachment = paint;
                session.antmailPaintLoadPendingPath = "";
                AntPaintCanvas canvas = new AntPaintCanvas();
                byte[] pixels = paint.pixels();
                for (int index = 0; index < pixels.length; index++) if (pixels[index] == 1) canvas.set(index % AntPaintCanvas.WIDTH, index / AntPaintCanvas.WIDTH, true);
                session.paintCanvas = canvas;
            } else if (attachment instanceof AntmailAttachment.Antcoins coins) {
                session.antmailAttachCoins = true;
                session.antmailCoinAmount = Long.toString(coins.amount());
            }
        }
    }

    private void saveAntmailDraft() {
        try {
            if (session.antmailRecipient.isBlank() && session.antmailSubject.isBlank() && session.antmailBody.isBlank()) return;
            AntmailAnternetResultPayload result = AntmailClientState.getMailbox();
            AntmailMailbox mailbox = mailbox(result);
            if (mailbox == null) return;
            AntmailDraft draft = new AntmailDraft(session.antmailDraftId, AntmailAddress.parse(mailbox.address().fullAddress()),
                    session.antmailRecipient.isBlank() ? null : AntmailAddress.parse(session.antmailRecipient), session.antmailSubject, session.antmailBody, composeAttachments());
            AntmailNetworking.saveDraft(draft);
        } catch (RuntimeException ignored) {
            session.antmailStatus = Component.translatable("computer.antos.status.draft_save_error").getString();
        }
    }

    private List<AntmailAttachment> composeAttachments() {
        List<AntmailAttachment> attachments = new ArrayList<>();
        if (session.antmailAttachText) attachments.add(new AntmailAttachment.TextFile(session.textPath.substring(session.textPath.lastIndexOf('/') + 1), session.textContent));
        if (session.antmailAttachPaint && session.antmailPaintAttachment != null) {
            attachments.add(session.antmailPaintAttachment);
        } else if (session.antmailAttachPaint) {
            byte[] pixels = new byte[AntPaintCanvas.WIDTH * AntPaintCanvas.HEIGHT];
            for (int py = 0; py < AntPaintCanvas.HEIGHT; py++) for (int px = 0; px < AntPaintCanvas.WIDTH; px++) pixels[py * AntPaintCanvas.WIDTH + px] = (byte) (session.paintCanvas.get(px, py) ? 1 : 0);
            attachments.add(new AntmailAttachment.PaintImage(session.paintName, AntPaintCanvas.WIDTH, AntPaintCanvas.HEIGHT, pixels));
        }
        if (session.antmailAttachCoins) attachments.add(new AntmailAttachment.Antcoins(Long.parseLong(session.antmailCoinAmount)));
        return attachments;
    }

    private void syncAntmailPaintAttachment() {
        String requestedPath = session.antmailPaintLoadPendingPath;
        if (requestedPath.isBlank()) return;
        ComputerFileSystemClientState.State fileState = ComputerFileSystemClientState.get();
        if (!requestedPath.equals(fileState.openedPath()) || fileState.openedContents().isBlank()) return;
        try {
            AntPaintFile file = AntPaintFileCodec.decode(Base64.getDecoder().decode(fileState.openedContents()));
            session.paintName = file.filename();
            session.antmailPaintPath = requestedPath;
            session.antmailPaintAttachment = AntmailAttachmentFiles.fromPaintFile(file);
            session.antmailPaintLoadPendingPath = "";
            session.antmailStatus = Component.translatable("computer.antos.status.painting_attached", file.filename()).getString();
        } catch (RuntimeException exception) {
            session.antmailPaintLoadPendingPath = "";
            session.antmailStatus = Component.translatable("computer.antos.status.invalid_paint_file").getString();
        }
    }

    private void saveAntmailAttachment(AntmailAttachment attachment) {
        try {
            if (attachment instanceof AntmailAttachment.TextFile text) {
                String path = "/documents/" + text.fileName();
                if (computerFileExists(path)) ComputerNetworking.saveFile(path, text.contents());
                else ComputerNetworking.createFile(path, text.contents());
                session.antmailStatus = Component.translatable("computer.antos.status.attachment_saved", text.fileName()).getString();
            } else if (attachment instanceof AntmailAttachment.PaintImage paint) {
                String name = paint.fileName().endsWith(".antpaint") ? paint.fileName() : paint.fileName() + ".antpaint";
                AntPaintFile file = AntmailAttachmentFiles.toPaintFile(paint, UUID.randomUUID().toString(), 0L, 0L);
                String encoded = Base64.getEncoder().encodeToString(AntPaintFileCodec.encode(file));
                String path = "/pictures/" + name;
                if (computerFileExists(path)) ComputerNetworking.saveFile(path, encoded);
                else ComputerNetworking.createFile(path, encoded);
                session.antmailStatus = Component.translatable("computer.antos.status.attachment_saved", name).getString();
            } else if (attachment instanceof AntmailAttachment.Antcoins coins) {
                session.antmailStatus = Component.translatable("computer.antos.status.coins_already_credited", coins.amount()).getString();
            }
            ComputerNetworking.listFiles();
        } catch (RuntimeException exception) {
            session.antmailStatus = Component.translatable("computer.antos.status.invalid_file").getString();
        }
    }

    private boolean computerFileExists(String path) {
        return ComputerFileSystemClientState.get().files().stream().anyMatch(entry -> entry.contains("\t" + path + "\t"));
    }

    private void newText() {
        session.textPath = "/documents/untitled.txt";
        session.textContent = "";
        session.textCursor = 0;
        session.textScroll = 0;
        session.textDirty = true;
        session.textListing = false;
        session.textFocused = true;
    }

    private void beginSaveAs() {
        int slash = session.textPath.lastIndexOf('/');
        session.textRename = session.textPath.substring(slash + 1);
        session.textSaveAs = true;
        session.textRenaming = true;
        session.textFocused = false;
    }

    private void setTextCursorFromMouse(double mouseX, double mouseY, int width) {
        if (session.textContent.isEmpty()) { session.textCursor = 0; return; }
        StringBuilder line = new StringBuilder();
        int cursor = 0;
        int targetLine = session.textScroll + Math.max(0, (int) (mouseY / 11));
        int currentLine = 0;
        for (int index = 0; index < session.textContent.length(); index++) {
            char character = session.textContent.charAt(index);
            if (character == '\n' || font.width(line.toString() + character) > width) {
                if (currentLine == targetLine) {
                    session.textCursor = index;
                    return;
                }
                currentLine++;
                line.setLength(0);
                if (character == '\n') continue;
            }
            line.append(character);
            cursor = index + 1;
        }
        session.textCursor = cursor;
    }

    private void moveTextCursorVertically(boolean up) {
        int lineStart = session.textContent.lastIndexOf('\n', Math.max(0, session.textCursor - 1)) + 1;
        int column = session.textCursor - lineStart;
        int target = up ? lineStart - 1 : session.textContent.indexOf('\n', session.textCursor);
        if (target < 0) return;
        int targetStart = up ? session.textContent.lastIndexOf('\n', target - 1) + 1 : target + 1;
        int targetEnd = session.textContent.indexOf('\n', targetStart);
        if (targetEnd < 0) targetEnd = session.textContent.length();
        session.textCursor = Math.min(targetStart + column, targetEnd);
    }

    private void writeText() {
        boolean exists = ComputerFileSystemClientState.get().files().stream().anyMatch(entry -> entry.contains("\t" + session.textPath + "\t"));
        if (exists) ComputerNetworking.saveFile(session.textPath, session.textContent);
        else ComputerNetworking.createFile(session.textPath, session.textContent);
        ComputerNetworking.listFiles();
    }

    private void saveText() {
        boolean exists = ComputerFileSystemClientState.get().files().stream().anyMatch(entry -> entry.contains("\t" + session.textPath + "\t"));
        if (exists) ComputerNetworking.saveFile(session.textPath, session.textContent);
        else ComputerNetworking.createFile(session.textPath, session.textContent);
        ComputerNetworking.listFiles();
    }

    private void renameTextFile() {
        if (!session.textRename.isBlank() && !session.textRename.contains("/") && !ComputerFileSystem.isProtected(session.textPath)) {
            String parent = parentDirectory(session.textPath);
            String destination = parent.equals("/") ? "/" + session.textRename : parent + "/" + session.textRename;
            if (session.textSaveAs) {
                session.textSaveAsPendingPath = destination;
                ComputerNetworking.createFile(destination, session.textContent);
            } else {
                ComputerNetworking.moveFile(session.textPath, destination);
                session.textMovePendingPath = destination;
            }
            ComputerNetworking.listFiles();
        }
        session.textSaveAs = false;
        session.textRenaming = false;
        session.textRename = "";
    }

    private void savePaint() {
        String path = "/pictures/" + session.paintName;
        if (!path.endsWith(".antpaint")) path += ".antpaint";
        session.paintName = path.substring(path.lastIndexOf('/') + 1);
        AntPaintFile file = new AntPaintFile(UUID.randomUUID().toString(), session.paintName, session.paintCanvas.copy(), 0L, 0L);
        String encoded = Base64.getEncoder().encodeToString(AntPaintFileCodec.encode(file));
        session.paintLoadedContents = encoded;
        String savedPath = path;
        boolean exists = ComputerFileSystemClientState.get().files().stream().anyMatch(entry -> entry.contains("\t" + savedPath + "\t"));
        if (exists) ComputerNetworking.saveFile(savedPath, encoded); else ComputerNetworking.createFile(savedPath, encoded);
        session.antmailPaintPath = savedPath;
        session.paintLoadedPath = savedPath;
        session.antmailPaintAttachment = null;
        session.antmailPaintLoadPendingPath = "";
        session.paintDirty = false;
        ComputerNetworking.listFiles();
    }

    private void paintTool(int offset) {
        session.paintTool = offset < 52 ? AntPaintTool.PENCIL : offset < 108 ? AntPaintTool.ERASER : AntPaintTool.FILL;
    }

    private void paintToolbarAction(int index) {
        if (index == 0) session.paintTool = AntPaintTool.PENCIL;
        else if (index == 1) session.paintTool = AntPaintTool.ERASER;
        else if (index == 2) session.paintTool = AntPaintTool.FILL;
        else if (index == 3) {
            session.paintHistory.record(session.paintCanvas);
            session.paintCanvas.clear();
            session.paintDirty = true;
        } else if (index == 4 && session.paintHistory.canUndo()) {
            session.paintCanvas = session.paintHistory.undo(session.paintCanvas);
            session.paintDirty = true;
        } else if (index == 5 && session.paintHistory.canRedo()) {
            session.paintCanvas = session.paintHistory.redo(session.paintCanvas);
            session.paintDirty = true;
        } else if (index == 6) {
            savePaint();
        }
    }

    private void paintAt(double mouseX, double mouseY, int x, int y, int w, int h) {
        int size = Math.min(w / AntPaintCanvas.WIDTH, h / AntPaintCanvas.HEIGHT);
        if (size <= 0) return;
        int canvasX = x + (w - size * AntPaintCanvas.WIDTH) / 2;
        int px = (int) ((mouseX - canvasX) / size);
        int canvasY = y + Math.max(0, (h - size * AntPaintCanvas.HEIGHT) / 2);
        int py = (int) ((mouseY - canvasY) / size);
        if (px < 0 || py < 0 || px >= AntPaintCanvas.WIDTH || py >= AntPaintCanvas.HEIGHT) {
            lastPaintPixelX = -1;
            lastPaintPixelY = -1;
            return;
        }
        if (session.paintTool == AntPaintTool.FILL) {
            session.paintHistory.record(session.paintCanvas);
            if (session.paintCanvas.floodFill(px, py, true) > 0) session.paintDirty = true;
            return;
        }
        if (lastPaintPixelX < 0 || lastPaintPixelY < 0) applyPaintPixel(px, py);
        else {
            int dx = Math.abs(px - lastPaintPixelX);
            int dy = Math.abs(py - lastPaintPixelY);
            int stepX = lastPaintPixelX < px ? 1 : -1;
            int stepY = lastPaintPixelY < py ? 1 : -1;
            int error = dx - dy;
            int currentX = lastPaintPixelX;
            int currentY = lastPaintPixelY;
            while (currentX != px || currentY != py) {
                int twiceError = error * 2;
                if (twiceError > -dy) { error -= dy; currentX += stepX; }
                if (twiceError < dx) { error += dx; currentY += stepY; }
                applyPaintPixel(currentX, currentY);
            }
        }
        lastPaintPixelX = px;
        lastPaintPixelY = py;
    }

    private void applyPaintPixel(int x, int y) {
        boolean changed = session.paintTool == AntPaintTool.PENCIL
                ? session.paintCanvas.draw(x, y)
                : session.paintCanvas.erase(x, y);
        if (changed) session.paintDirty = true;
    }

    private boolean hasControl(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    private List<ResourceLocation> workspaceDiskIds() {
        List<ResourceLocation> disks = new ArrayList<>();
        disks.add(ResourceLocation.fromNamespaceAndPath("antos", "introduction"));
        disks.addAll(com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.installedDisks());
        return List.copyOf(disks);
    }

    private List<ResourceLocation> physicalDisks() {
        return com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.installedDisks();
    }

    private void ejectSelected() {
        if (selectedDisk == null) return;
        ComputerNetworking.eject(selectedDisk);
        loginMessage = Component.translatable("computer.antos.status.ejecting_disk").getString();
        loginMessageTicks = 100;
        selectedDisk = null;
    }

    private void beginRenameSelectedFile() {
        if (session.fileExplorerSelected.isEmpty() || session.fileExplorerSelected.equals("/") || ComputerFileSystem.isProtected(session.fileExplorerSelected)) return;
        session.fileExplorerDeleteConfirm = false;
        session.fileExplorerCreatingDirectory = false;
        session.fileExplorerMoving = false;
        int slash = session.fileExplorerSelected.lastIndexOf('/');
        session.fileExplorerRename = session.fileExplorerSelected.substring(slash + 1);
        session.fileExplorerRenaming = true;
    }

    private void beginCreateFileExplorerDirectory() {
        session.fileExplorerDeleteConfirm = false;
        session.fileExplorerCreatingDirectory = true;
        session.fileExplorerMoving = false;
        session.fileExplorerRenaming = false;
        session.fileExplorerRename = "";
    }

    private void beginMoveFileExplorerSelection() {
        if (!fileExplorerSelectionMutable()) return;
        session.fileExplorerDeleteConfirm = false;
        session.fileExplorerMoving = true;
        session.fileExplorerCreatingDirectory = false;
        session.fileExplorerRenaming = false;
        session.fileExplorerRename = "";
    }

    private void renameSelectedFile() {
        if (!session.fileExplorerRename.isBlank() && !session.fileExplorerRename.contains("/")) {
            String parent = parentDirectory(session.fileExplorerSelected);
            String destination = parent.equals("/") ? "/" + session.fileExplorerRename : parent + "/" + session.fileExplorerRename;
            ComputerNetworking.moveFile(session.fileExplorerSelected, destination);
            session.fileExplorerSelected = destination;
            ComputerNetworking.listFiles();
        }
        session.fileExplorerRenaming = false;
        session.fileExplorerRename = "";
    }

    private void deleteSelectedFile() {
        if (session.fileExplorerSelected.isEmpty() || session.fileExplorerSelected.equals("/") || ComputerFileSystem.isProtected(session.fileExplorerSelected)) return;
        ComputerNetworking.deleteFile(session.fileExplorerSelected);
        session.fileExplorerSelected = "";
        ComputerNetworking.listFiles();
    }

    private void deleteTextFile() {
        if (session.textPath.isBlank() || ComputerFileSystem.isProtected(session.textPath)) return;
        ComputerNetworking.deleteFile(session.textPath);
        session.textPath = "/documents/untitled.txt";
        session.textContent = "";
        session.textCursor = 0;
        session.textDirty = false;
        ComputerNetworking.listFiles();
    }

    private void open(String type) {
        if (!AntOSSettings.appEnabled(type)) return;
        for (int index = 0; index < session.windows.size(); index++) {
            Window window = session.windows.get(index);
            if (!window.type.equals(type)) continue;
            window.minimized = false;
            session.windows.remove(index);
            session.windows.add(window);
            activeWindow = window;
            if (type.equals("FILES")) ComputerNetworking.listFiles();
              else if (type.equals("ANTAZON")) refreshAntazon();
            return;
        }
        Window window = new Window(type, TITLES.get(type), 112 + session.windows.size() * 12, 52 + session.windows.size() * 10);
        session.windows.add(window);
        activeWindow = window;
        if (type.equals("ANTMAIL")) requestAntmailState();
        else if (type.equals("ANTAZON")) refreshAntazon();
        else if (type.equals("TASKS")) { taskRefreshTicks = 0; selectedTaskId = ""; selectedTaskCategory = ""; taskMapScrollX = 0; taskMapScrollY = 0; taskCategoryScroll = 0; ComputerNetworking.requestTasks(); }
        else if (type.equals("FILES")) ComputerNetworking.listFiles();
    }

    private void refreshAntazon() {
        ComputerNetworking.linkAntazonCrate();
        ComputerNetworking.requestAntazon();
        ComputerNetworking.requestAntazonWallet();
        ComputerNetworking.requestAntazonSellState();
        ComputerNetworking.requestAntazonWishlist();
        ComputerNetworking.requestAntazonOrders();
        ComputerNetworking.requestAntazonPrices();
        ComputerNetworking.requestAntazonOnboarding();
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

    private void updateArchiveScrollbar(Window window, double mouseY) {
        List<ComputerGuideData.Entry> entries = ComputerGuideData.entriesFor(workspaceDiskIds(),
                com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get());
        int columns = window.maximized ? 3 : 1;
        int cellHeight = window.maximized ? 72 : 38;
        int visibleRows = Math.max(1, (windowHeight(window) - 34 - 58) / cellHeight);
        int totalRows = (filterArchiveEntries(entries).size() + columns - 1) / columns;
        int maximum = Math.max(0, totalRows - visibleRows);
        if (maximum == 0) {
            session.archiveScroll = 0;
            return;
        }
        int trackHeight = Math.max(1, windowHeight(window) - 34 - 58);
        int thumbHeight = Math.max(12, trackHeight * visibleRows / totalRows);
        int trackTop = -HEIGHT / 2 + windowLocalY(window) + 68;
        int travel = Math.max(1, trackHeight - thumbHeight);
        double progress = Math.max(0.0, Math.min(1.0, (mouseY - trackTop - thumbHeight / 2.0) / travel));
        session.archiveScroll = (int) Math.round(progress * maximum);
    }

    private static boolean inside(int x, int y, int w, int h, double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

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

    private static final class Session {
        private boolean authenticated;
        private long lastUse;
        private final List<Window> windows = new ArrayList<>();
        private final List<String> terminalOutput = new ArrayList<>(List.of("ANTOS TERMINAL [READY]", "TYPE HELP FOR COMMANDS"));
        private int terminalScroll;
        private int terminalMaximumScroll;
        private String terminalInput = "";
        private String terminalDirectory = "/";
        private boolean terminalFocused;
        private String textPath = "/documents/untitled.txt";
        private String textContent = "";
        private int textCursor;
        private int textSelectionStart = -1;
        private int textScroll;
        private int textMaximumScroll;
        private boolean textDirty;
        private boolean textFocused;
        private boolean textListing;
        private int textPickerScroll;
        private int textPickerMaximumScroll;
        private boolean textRenaming;
        private boolean textSaveAs;
        private String textSaveAsPendingPath = "";
        private String textMovePendingPath = "";
        private boolean textJustSavedAs;
        private boolean textDeleteConfirm;
        private String textRename = "";
        private ResourceLocation archiveEntryId;
        private int archiveScroll;
        private String archiveSearch = "";
        private boolean archiveSearchFocused;
        private int fileExplorerScroll;
        private int fileExplorerMaximumScroll;
        private String fileExplorerLastClickedPath = "";
        private long fileExplorerLastClickTime;
        private boolean fileExplorerDeleteConfirm;
        private boolean fileExplorerCreatingDirectory;
        private boolean fileExplorerMoving;
        private int archiveDetailScroll;
        private int archiveDetailMaxScroll;
        private int archiveMouseX;
        private int archiveMouseY;
        private int mouseX;
        private int mouseY;
        private AntPaintCanvas paintCanvas = new AntPaintCanvas();
        private final AntPaintHistory paintHistory = new AntPaintHistory();
        private AntPaintTool paintTool = AntPaintTool.PENCIL;
        private String paintName = "UNTITLED.ANTPAINT";
        private String paintLoadedPath = "";
        private String paintLoadedContents = "";
        private boolean paintDirty;
        private String antmailStatus = "";
        private boolean antmailSendPending;
        private AntmailAnternetResultPayload antmailSendBaseline;
        private String antmailRetryMessageId = "";
        private AntmailAnternetResultPayload antmailRetryBaseline;
        private int antmailRetryTicks;
        private int antmailRefreshTicks;
        private int antmailStateWaitTicks;
        private boolean antmailFocused;
        private String antmailMode = "inbox";
        private String antmailRecipient = "";
        private String antmailSubject = "";
        private String antmailBody = "";
        private int antmailField;
        private int antmailCursor;
        private int antmailSelectionStart = -1;
        private boolean antmailSent;
        private int antmailMessageIndex = -1;
        private int antmailPage;
        private int antmailListScroll;
        private int antmailListMaximumScroll;
        private int antmailDetailScroll;
        private int antmailDetailMaximumScroll;
        private int antmailAttachmentStartLine = -1;
        private int antmailVisibleAttachmentCount;
        private boolean antmailAttachText;
        private boolean antmailAttachPaint;
        private boolean antmailAttachCoins;
        private boolean antmailAttachmentMenu;
        private String antmailCoinAmount = "";
        private String antmailTextPath = "";
        private String antmailPaintPath = "";
        private AntmailAttachment.PaintImage antmailPaintAttachment;
        private String antmailPaintLoadPendingPath = "";
        private UUID antmailDraftId;
        private boolean antmailDraftLoaded;
        private boolean antmailPickingAttachment;
        private boolean antmailPickingPaint;
        private String fileExplorerDirectory = "/";
        private String fileExplorerSelected = "";
        private String fileExplorerRename = "";
        private boolean fileExplorerRenaming;
        private String wallpaperId = ComputerDesktopState.DEFAULT_WALLPAPER.toString();
        private List<String> wallpapers = List.of(ComputerDesktopState.DEFAULT_WALLPAPER.toString());
        private int wallpaperScroll;
        private String antazonMode = "catalog";
        private int antazonPriceScroll;
        private int antazonSellScroll;
        private int antazonWishlistScroll;
        private int antazonOrdersScroll;
        private int antazonOrderSelection = -1;
        private int antazonPriceQuantity = 1;
        private String antazonPriceItem = "";
        private String antazonProductId = "";
        private int antazonQuantity = 1;
        private String antazonSearch = "";
        private boolean antazonSearchFocused;
        private String antazonCategory = "ALL";
        private boolean antazonCategoryOpen;
        private int antazonScroll;
        private final Map<String, Integer> antazonCart = new LinkedHashMap<>();
        private final Set<String> antazonWishlist = new HashSet<>();
        private boolean antazonSellConfirm;
        private int antazonWalletRefreshTicks;
        private boolean desktopRequested;
        private int bootTicks = -1;
        private boolean onboardingCompleted;
        private int onboardingStep;
    }

    private static final class Window {
        private final String type;
        private final String title;
        private final int width;
        private final int height;
        private int x;
        private int y;
        private int restoreX;
        private int restoreY;
        private int renderWidth;
        private int renderHeight;
        private boolean minimized;
        private boolean maximized;
        private boolean gameOpen;
        private String gameId = "";

        private Window(String type, String title, int x, int y) {
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
