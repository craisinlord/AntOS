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
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
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
import java.util.Optional;
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
    private static final String[] ICONS = {"ARCHIVE", "FILES", "SETTINGS", "TASKS", "TERMINAL", "TEXT", "PAINT", "ANTMAIL", "ANTAZON", "GAMES", "DISKS"};
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
    private String accountMessage;
    String faceIdMessage = "";
    boolean faceIdWaiting;
    long faceIdRevisionAtRequest;
    boolean faceIdExpectedLinked;
    final GamesApp games = new GamesApp();
    final TasksApp tasks = new TasksApp();
    private ComputerAccessResultPayload accessResult;
    private com.craisinlord.antos.content.client.DiskRewardsClientState.Award diskRewardToast;
    private int diskRewardToastTicks;
    private String diskRewardToastKey = "";
    private int observedResult = -1;
    private Window dragging;
    private int dragX;
    private int dragY;
    Window activeWindow;
    private final Set<String> archiveRenderLog = new HashSet<>();
    boolean archiveGreenTint = true;
    private static final boolean LOG_ARCHIVE_PREVIEWS = Boolean.getBoolean("antos.logArchivePreviews");
    private static final Map<String, LivingEntity> archiveEntityPreviews = new HashMap<>();
    private static final Set<String> unavailableArchiveEntities = new HashSet<>();
    private static final Map<String, ItemStack> archiveItemStacks = new HashMap<>();
    private static Object archivePreviewLevel;
    private static final Map<String, ItemStack> archiveEnchantedBooks = new HashMap<>();
    private static final Map<String, ItemStack> archivePotionStacks = new HashMap<>();
    private static final Map<String, Optional<ResourceLocation>> WALLPAPER_TEXTURES = new HashMap<>();
    private static final int MAX_WRAP_CACHE = 512;
    private static final int[][] BADGE_RECTS = badgeRects();
    private final Map<WrapKey, List<net.minecraft.util.FormattedCharSequence>> wrapCache = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<WrapKey, List<net.minecraft.util.FormattedCharSequence>> eldest) {
            return size() > MAX_WRAP_CACHE;
        }
    };
    private Object wrapLanguage;
    private long clockMinute = -1L;
    private String clockLabel = "";
    private List<String> frameDesktopApps;
    private String parsedWallpaperId;
    private ResourceLocation parsedWallpaperLocation;

    public ComputerScreen() {
        super(Component.translatable("screen.antos.computer"));
        PixelIcons.clear();
        var accountResult = com.craisinlord.antos.content.client.AnternetAccountClientState.latest();
        this.sessionKey = com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of();
        this.session = SESSIONS.computeIfAbsent(sessionKey, ignored -> new Session());
        this.session.attach(this);
        for (ComputerApp app : List.of(tasks, games)) app.attach(this);
        this.loggedIn = accountResult != null && accountResult.result() == com.craisinlord.antos.content.network.AnternetAccountResultPayload.SUCCESS;
        if (!loggedIn || AntOSSettings.hasCompletedComputerTour(accountResult.accountId())) {
            this.session.onboardingCompleted = true;
            this.session.onboardingStep = -1;
        } else {
            this.session.onboardingCompleted = false;
            this.session.onboardingStep = 0;
        }
        this.session.windows.removeIf(window -> window.type.equals("GAMES") && (window.gameOpen || window.programOpen));
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
        if (loggedIn && !diskRewardToastKey.equals(com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of())) {
            diskRewardToastKey = com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of();
            diskRewardToast = null;
            diskRewardToastTicks = 0;
        }
        if (loggedIn && --diskRewardToastTicks <= 0) {
            diskRewardToast = com.craisinlord.antos.content.client.DiskRewardsClientState.poll();
            if (diskRewardToast != null) diskRewardToastTicks = 140;
        }
        if (activeWindow != null && activeWindow.type.equals("GAMES")) games.tick(activeWindow);
    }

    @Override
    public void render(GuiGraphics source, int mouseX, int mouseY, float partialTick) {
        if (archivePreviewLevel != null && archivePreviewLevel != Minecraft.getInstance().level) clearArchivePreviews();
        this.renderBackground(source, mouseX, mouseY, partialTick);
        GuiGraphics graphics = new com.craisinlord.antos.content.client.ThemedGraphics(source);
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
        graphics.flush();
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
        Component accountMessageComponent = accountMessage == null
                ? Component.translatable("computer.antos.account.initial_prompt")
                : Component.literal(accountMessage);
        g.drawCenteredString(font, accountMessageComponent, l + WIDTH / 2, t + 218,
                accountMessage != null && (accountMessage.contains("UNAVAILABLE") || accountMessage.contains("NOT RECOGNIZED")) ? 0xFFFF7777 : PALE_GREEN);
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
        frameDesktopApps = desktopApps();
        g.drawManaged(() -> renderDesktopSurface(g, l, t, mouseX, mouseY));
        if (activeWindow != null && !activeWindow.minimized && session.windows.remove(activeWindow)) {
            session.windows.add(activeWindow);
        }
        int layer = 1;
        for (Window window : session.windows) {
            if (!window.minimized) renderWindow(g, window, l, t, mouseX, mouseY, layer++);
        }
        if (!session.onboardingCompleted) {
            beginLayer(g);
            renderOnboarding(g, l, t, mouseX, mouseY);
        }
        if (diskRewardToast != null && diskRewardToastTicks > 0) renderDiskRewardToast(g, l, t, layer + 2);
    }

    void beginLayer(GuiGraphics g) {
        g.flush();
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private void renderDiskRewardToast(GuiGraphics g, int l, int t, int layer) {
        beginLayer(g);
        g.pose().pushPose();
        g.pose().translate(0, 0, layer * 10.0F);
        try {
            int rows = Math.min(4, diskRewardToast.rewards().size());
            int h = 40 + rows * 12 + (diskRewardToast.rewards().size() > rows ? 12 : 0);
            int x = l + WIDTH - 230;
            int y = t + HEIGHT - h - 14;
            g.fill(x, y, x + 216, y + h, BLACK);
            box(g, x, y, x + 216, y + h, GREEN);
            g.drawString(font, Component.translatable("computer.antos.disks.rewards_received"), x + 8, y + 7, GREEN, false);
            g.drawString(font, trimToWidth(Component.translatable(diskRewardToast.title()).getString(), 198), x + 8, y + 21, PALE_GREEN, false);
            for (int i = 0; i < rows; i++) g.drawString(font, trimToWidth("+ " + com.craisinlord.antos.content.client.DiskRewardsClientState.label(diskRewardToast.rewards().get(i)), 198), x + 8, y + 35 + i * 12, PALE_GREEN, false);
            if (diskRewardToast.rewards().size() > rows) g.drawString(font, Component.translatable("computer.antos.disks.more_rewards"), x + 8, y + 35 + rows * 12, GREEN, false);
        } finally {
            g.flush();
            g.pose().popPose();
        }
    }

    private void renderDesktopSurface(GuiGraphics g, int l, int t, int mouseX, int mouseY) {
        renderWallpaper(g, l, t);
        g.fill(l + 9, t + 9, l + WIDTH - 9, t + 30, BLACK);
        g.drawString(font, Component.literal("AntOS // v" + AntOS.MOD_VERSION), l + 18, t + 15, GREEN, false);
        long dayTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getDayTime();
        long dayTicks = Math.floorMod(dayTime, 24000L);
        int hours = (int) ((dayTicks / 1000L + 6L) % 24L);
        int minutes = (int) (dayTicks % 1000L * 60L / 1000L);
        long minute = hours * 60L + minutes;
        if (minute != clockMinute) {
            clockMinute = minute;
            clockLabel = String.format("%02d:%02d", hours, minutes);
        }
        g.drawString(font, Component.literal(clockLabel), l + WIDTH - 48, t + 15, PALE_GREEN, false);
        g.fill(l + 9, t + 31, l + WIDTH - 9, t + 33, GREEN);
        List<String> desktopApps = renderedDesktopApps();
        for (int i = 0; i < desktopApps.size(); i++) {
            String app = desktopApps.get(i);
            int x = desktopIconX(l, i);
            int y = desktopIconY(t, i);
            boolean hover = inside(x - 8, y - 8, 84, 61, mouseX, mouseY);
            if (hover) drawHover(g, x - 9, y - 9, 84, 62);
            drawDesktopIcon(g, app, x + 25, y + 2);
            if (app.equals("ANTMAIL") && session.antmail.hasUnreadMessages()) drawNotificationBadge(g, x + 35, y + 6);
            if (app.equals("ANTAZON") && session.antazon.hasUnseenDeals()) drawNotificationBadge(g, x + 35, y + 6);
            g.drawString(font, Component.translatable(appNameKey(app)), x, y + 35, GREEN, false);
        }
    }

    private void renderOnboarding(GuiGraphics g, int l, int t, int mouseX, int mouseY) {
        List<String> apps = renderedDesktopApps();
        int appStep = session.onboardingStep - 1;
        boolean welcome = session.onboardingStep == 0;
        g.fill(l + 9, t + 34, l + WIDTH - 9, t + HEIGHT - 9, 0xC4000000);
        if (!welcome && appStep >= 0 && appStep < apps.size()) {
            String app = apps.get(appStep);
            int desktopIndex = renderedDesktopApps().indexOf(app);
            if (desktopIndex >= 0) {
                int iconX = desktopIconX(l, desktopIndex);
                int iconY = desktopIconY(t, desktopIndex);
                g.fill(iconX - 10, iconY - 10, iconX + 66, iconY + 52, HOVER_FILL);
                box(g, iconX - 10, iconY - 10, iconX + 66, iconY + 52, GREEN);
                drawDesktopIcon(g, app, iconX + 25, iconY + 2);
                g.drawString(font, Component.translatable(appNameKey(app)), iconX, iconY + 35, GREEN, false);
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
            g.drawString(font, Component.translatable("computer.antos.onboarding.app_tour"), contentX, panelY + 8, GREEN, false);
            g.drawString(font, Component.translatable(appNameKey(app)), contentX, panelY + 35, GREEN, false);
            g.drawString(font, Component.translatable("computer.antos.onboarding.app_step", appStep + 1, apps.size()), panelX + panelW - 68, panelY + 35, PALE_GREEN, false);
            g.drawString(font, Component.literal(onboardingAppTitle(app)), contentX, panelY + 53, PALE_GREEN, false);
            wrap(g, onboardingAppDescription(app), contentX, panelY + 70, panelW - 20, PALE_GREEN);
            if (appStep > 0) onboardingButton(g, contentX, actionY, 82, Component.translatable("computer.antos.onboarding.back"), hovered(contentX, actionY, 82, 22));
            Component nextLabel = Component.translatable(appStep + 1 < apps.size()
                    ? "computer.antos.onboarding.next" : "computer.antos.onboarding.done");
            onboardingButton(g, panelX + panelW - 78, actionY, 68, nextLabel, hovered(panelX + panelW - 78, actionY, 68, 22));
            if (desktopIndex >= 0) {
                int iconX = desktopIconX(l, desktopIndex);
                int iconY = desktopIconY(t, desktopIndex);
                g.fill(iconX - 10, iconY - 10, iconX + 66, iconY + 52, HOVER_FILL);
                box(g, iconX - 10, iconY - 10, iconX + 66, iconY + 52, GREEN);
                drawDesktopIcon(g, app, iconX + 25, iconY + 2);
            g.drawString(font, Component.translatable(appNameKey(app)), iconX, iconY + 35, GREEN, false);
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
        g.drawString(font, Component.translatable("computer.antos.onboarding.title"), contentX, panelY + 8, GREEN, false);
        if (welcome) {
            g.drawString(font, Component.translatable("computer.antos.onboarding.welcome_title"), contentX, panelY + 43, GREEN, false);
            g.drawString(font, Component.translatable("computer.antos.onboarding.welcome_subtitle"), contentX, panelY + 62, PALE_GREEN, false);
            wrap(g, Component.translatable("computer.antos.onboarding.welcome_description"), contentX, panelY + 91, panelW - 44, PALE_GREEN);
            g.drawString(font, Component.translatable("computer.antos.onboarding.duration"), contentX, panelY + 145, GREEN, false);
            g.drawString(font, Component.translatable("computer.antos.onboarding.welcome"), contentX, panelY + 166, PALE_GREEN, false);
            onboardingButton(g, contentX, actionY, 126, Component.translatable("computer.antos.onboarding.start_tour"), hovered(contentX, actionY, 126, 22));
            onboardingButton(g, panelX + panelW - 110, actionY, 88, Component.translatable("computer.antos.onboarding.skip"), hovered(panelX + panelW - 110, actionY, 88, 22));
            return;
        }
        finishOnboarding();
    }

    private void onboardingButton(GuiGraphics g, int x, int y, int width, Component label, boolean hover) {
        if (hover) drawHover(g, x, y, width, 22);
        g.drawCenteredString(font, label, x + width / 2, y + 7, GREEN);
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
        return Component.translatable(appTitleKey(app)).getString();
    }

    private String onboardingAppDescription(String app) {
        return Component.translatable(appDescriptionKey(app)).getString();
    }

    private static String appNameKey(String app) { return "computer.antos.app." + app.toLowerCase(Locale.ROOT) + ".name"; }
    private static String appTitleKey(String app) { return "computer.antos.app." + app.toLowerCase(Locale.ROOT) + ".title"; }
    private static String appDescriptionKey(String app) { return "computer.antos.app." + app.toLowerCase(Locale.ROOT) + ".description"; }

    private static List<String> desktopApps() {
        return java.util.Arrays.stream(ICONS).filter(AntOSSettings::appEnabled).toList();
    }

    private List<String> renderedDesktopApps() {
        if (frameDesktopApps == null) frameDesktopApps = desktopApps();
        return frameDesktopApps;
    }

    private int desktopIconX(int left, int index) {
        return left + 28 + index % 4 * 104;
    }

    private int desktopIconY(int top, int index) {
        return top + 48 + index / 4 * 78;
    }

    private void drawDesktopIcon(GuiGraphics g, String type, int x, int y) {
        PixelIcons.desktop(g, type, x - PixelIcons.DESKTOP_SIZE / 2.0F, y - 3.5F);
    }

    private void drawNotificationBadge(GuiGraphics g, int x, int y) {
        for (int[] rect : BADGE_RECTS) g.fill(x - 6 + rect[0], y - 6 + rect[1], x - 6 + rect[2], y - 6 + rect[3], rect[4] == 0 ? BLACK : GREEN);
        g.fill(x - 1, y - 4, x + 1, y + 2, BLACK);
        g.fill(x - 1, y + 3, x + 1, y + 5, BLACK);
    }

    private static int[][] badgeRects() {
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
        List<int[]> rects = new ArrayList<>();
        List<int[]> previous = new ArrayList<>();
        for (int row = 0; row < circle.length; row++) {
            List<int[]> current = new ArrayList<>();
            int column = 0;
            while (column < circle[row].length()) {
                char pixel = circle[row].charAt(column);
                if (pixel != '#' && pixel != '+') {
                    column++;
                    continue;
                }
                int start = column;
                while (column < circle[row].length() && circle[row].charAt(column) == pixel) column++;
                int color = pixel == '#' ? 0 : 1;
                int[] rect = null;
                for (int[] candidate : previous) {
                    if (candidate[0] == start && candidate[2] == column && candidate[4] == color && candidate[3] == row) {
                        rect = candidate;
                        break;
                    }
                }
                if (rect == null) {
                    rect = new int[]{start, row, column, row + 1, color};
                    rects.add(rect);
                } else rect[3] = row + 1;
                current.add(rect);
            }
            previous = current;
        }
        return rects.toArray(new int[0][]);
    }

    private void renderWallpaper(GuiGraphics g, int l, int t) {
        int left = l + 9;
        int top = t + 34;
        int right = l + WIDTH - 9;
        int bottom = t + HEIGHT - 8;
        String wallpaperId = session.wallpaperId;
        ComputerGuideData.Wallpaper wallpaper;
        try { wallpaper = ComputerGuideData.wallpaper(parsedWallpaper(wallpaperId)); }
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

    private ResourceLocation parsedWallpaper(String wallpaperId) {
        if (!wallpaperId.equals(parsedWallpaperId)) {
            parsedWallpaperId = null;
            parsedWallpaperLocation = ResourceLocation.parse(wallpaperId);
            parsedWallpaperId = wallpaperId;
        }
        return parsedWallpaperLocation;
    }

    private static ResourceLocation wallpaperTexture(String textureIdValue) {
        Optional<ResourceLocation> cached = WALLPAPER_TEXTURES.get(textureIdValue);
        if (cached == null) {
            ResourceLocation texture;
            try {
                ResourceLocation textureId = ResourceLocation.parse(textureIdValue);
                texture = ResourceLocation.fromNamespaceAndPath(textureId.getNamespace(), "textures/" + textureId.getPath() + ".png");
            } catch (RuntimeException exception) {
                texture = null;
            }
            cached = Optional.ofNullable(texture);
            WALLPAPER_TEXTURES.put(textureIdValue, cached);
        }
        return cached.orElse(null);
    }

    boolean drawWallpaperTexture(GuiGraphics g, ComputerGuideData.Wallpaper wallpaper, int x, int y, int width, int height) {
        try {
            ResourceLocation texture = wallpaperTexture(wallpaper.textureId());
            if (texture == null) return false;
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

    private void renderWindow(GuiGraphics g, Window window, int l, int t, int mouseX, int mouseY, int layer) {
        beginLayer(g);
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
            else if (window.type.equals("DISKS")) session.disks.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("FILES")) session.files.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("SETTINGS")) session.settings.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("TERMINAL")) session.terminal.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("TEXT")) session.text.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("PAINT")) session.paint.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("ANTMAIL")) session.antmail.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("ANTAZON")) session.antazon.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("GAMES")) games.render(g, window, cx, cy, w - 16, h - 34, mouseX, mouseY);
            else if (window.type.equals("WALLPAPERS")) session.wallpaperPicker.render(g, cx, cy, h - 34);
        } finally {
            g.disableScissor();
        }
    }

    int renderTaskItemRecipe(GuiGraphics g, String itemId, int x, int y, int w) {
        if (Minecraft.getInstance().level != null) {
            try {
                if (resolveArchiveRecipe(itemId) != null) return renderArchiveRecipe(g, itemId, x, y, w);
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
        int recipeWidth = slot * 5 + 8;
        if (!renderArchiveRecipe(g, recipeId, x, y, Math.min(w, recipeWidth), slot)) {
            return wrap(g, "RECIPE // " + recipeId, x, y, w, PALE_GREEN);
        }
        return y + slot * 3;
    }

    boolean renderArchiveRecipe(GuiGraphics g, String recipeId, int x, int y, int width, int slot) {
        if (Minecraft.getInstance().level == null) return false;
        try {
            RecipeHolder<?> holder = resolveArchiveRecipe(recipeId);
            if (holder == null) return false;
            var recipe = holder.value();
            var access = Minecraft.getInstance().level.registryAccess();
            List<Ingredient> ingredients = recipe.getIngredients();
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            boolean gunBench = typeId != null && typeId.getNamespace().equals("scguns") && typeId.getPath().equals("gun_bench");
            int columns = gunBench ? 4 : 3;
            int actualSlot = Math.min(slot, Math.max(4, (width - 8) / (columns + 1)));
            int gridWidth = actualSlot * columns;
            int outputX = x + gridWidth + 8;
            int outputY = y + actualSlot;
            int shapedWidth = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            int shapedHeight = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 3;
            for (int index = 0; index < columns * 3; index++) {
                int column = index % columns;
                int row = index / columns;
                int ingredientIndex;
                if (gunBench) ingredientIndex = row < 2 ? row * 4 + column : column == 0 ? 8 : column == 2 ? 9 : -2;
                else if (recipe instanceof ShapedRecipe) ingredientIndex = column < shapedWidth && row < shapedHeight ? row * shapedWidth + column : -1;
                else ingredientIndex = index;
                if (ingredientIndex == -2) continue;
                int cellX = x + column * actualSlot;
                int cellY = y + row * actualSlot;
                g.fill(cellX, cellY, cellX + actualSlot - 1, cellY + actualSlot - 1, 0xFF173817);
                if (ingredientIndex >= 0 && ingredientIndex < ingredients.size()) {
                    ItemStack[] choices = ingredients.get(ingredientIndex).getItems();
                    if (choices.length > 0) {
                        ItemStack shown = choices[(int) ((System.currentTimeMillis() / 1000L) % choices.length)];
                        renderArchiveItem(g, shown, cellX, cellY, actualSlot);
                    }
                }
            }
            g.fill(x + gridWidth + 2, y + actualSlot + actualSlot / 2 - 1, x + gridWidth + 6, y + actualSlot + actualSlot / 2 + 1, PALE_GREEN);
            g.fill(x + gridWidth + 5, y + actualSlot + actualSlot / 2 - 3, x + gridWidth + 7, y + actualSlot + actualSlot / 2 + 3, PALE_GREEN);
            List<ItemStack> results = archiveRecipeResults(recipe, access);
            for (int index = 0; index < Math.min(2, results.size()); index++) {
                int resultY = outputY + index * actualSlot;
                g.fill(outputX, resultY, outputX + actualSlot - 1, resultY + actualSlot - 1, 0xFF173817);
                renderArchiveItem(g, results.get(index), outputX, resultY, actualSlot);
                renderArchiveStackCount(g, results.get(index), outputX, resultY, actualSlot);
            }
            ItemStack station = archiveRecipeStation(recipe);
            if (!station.isEmpty()) renderArchiveItem(g, station, outputX, y, actualSlot);
            return true;
        } catch (RuntimeException exception) {
            logArchivePreviewOnce("recipe.error." + recipeId, "Archive recipe preview failed id={} error={}", recipeId, exception.toString());
            return false;
        }
    }

    private static final Map<String, Optional<RecipeHolder<?>>> archiveResolvedRecipes = new HashMap<>();
    private static final Map<Class<?>, Optional<java.lang.reflect.Method>> archiveResultMethods = new HashMap<>();
    private static Object archiveRecipeLevel;
    private static Object archiveRecipeManager;
    private static Object archiveRecipeCollection;

    RecipeHolder<?> resolveArchiveRecipe(String recipeId) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        var recipeManager = level.getRecipeManager();
        var recipeCollection = recipeManager.getRecipes();
        if (archiveRecipeLevel != level || archiveRecipeManager != recipeManager || archiveRecipeCollection != recipeCollection) {
            archiveRecipeLevel = level;
            archiveRecipeManager = recipeManager;
            archiveRecipeCollection = recipeCollection;
            archiveResolvedRecipes.clear();
        }
        Optional<RecipeHolder<?>> cached = archiveResolvedRecipes.get(recipeId);
        if (cached != null) return cached.orElse(null);
        RecipeHolder<?> holder = null;
        ResourceLocation requestedId = ResourceLocation.tryParse(recipeId);
        if (requestedId != null) {
            holder = recipeManager.byKey(requestedId).orElse(null);
            if (holder == null) {
                var requestedItem = BuiltInRegistries.ITEM.getOptional(requestedId).orElse(null);
                if (requestedItem != null && requestedItem != Items.AIR) {
                    var access = level.registryAccess();
                    holder = recipeCollection.stream()
                            .filter(candidate -> archiveRecipeResults(candidate.value(), access).stream().anyMatch(stack -> stack.is(requestedItem)))
                            .min(java.util.Comparator.comparingInt(candidate -> archiveRecipePriority(candidate.value())))
                            .orElse(null);
                }
            }
        }
        archiveResolvedRecipes.put(recipeId, Optional.ofNullable(holder));
        return holder;
    }

    private static int archiveRecipePriority(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if (recipe.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING) return 0;
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (typeId == null) return 3;
        if (typeId.getNamespace().equals("scguns")) return 1;
        if (typeId.getNamespace().equals("oritech")) return 2;
        return 3;
    }

    static List<ItemStack> archiveRecipeResults(net.minecraft.world.item.crafting.Recipe<?> recipe, net.minecraft.core.RegistryAccess access) {
        ItemStack direct;
        try {
            direct = recipe.getResultItem(access);
        } catch (RuntimeException ignored) {
            direct = ItemStack.EMPTY;
        }
        if (direct != null && !direct.isEmpty()) return List.of(direct);
        java.lang.reflect.Method method = archiveResultMethods.computeIfAbsent(recipe.getClass(), type -> {
            try {
                return Optional.of(type.getMethod("getResults"));
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }).orElse(null);
        if (method == null) return List.of();
        try {
            if (method.invoke(recipe) instanceof List<?> list) {
                List<ItemStack> stacks = new java.util.ArrayList<>();
                for (Object value : list) if (value instanceof ItemStack stack && !stack.isEmpty()) stacks.add(stack);
                return stacks;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return List.of();
    }

    private static ItemStack archiveRecipeStation(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if (recipe.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING) return ItemStack.EMPTY;
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (typeId == null) return ItemStack.EMPTY;
        List<String> candidates = switch (typeId.toString()) {
            case "minecraft:smelting" -> List.of("minecraft:furnace");
            case "minecraft:blasting" -> List.of("minecraft:blast_furnace");
            case "minecraft:smoking" -> List.of("minecraft:smoker");
            case "minecraft:campfire_cooking" -> List.of("minecraft:campfire");
            case "minecraft:stonecutting" -> List.of("minecraft:stonecutter");
            case "minecraft:smithing" -> List.of("minecraft:smithing_table");
            default -> List.of(typeId + "_block", typeId.toString(), typeId.getNamespace() + ":" + typeId.getPath() + "_controller");
        };
        for (String candidate : candidates) {
            ResourceLocation itemId = ResourceLocation.tryParse(candidate);
            if (itemId == null) continue;
            var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item != null && item != Items.AIR) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    private void renderArchiveStackCount(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        if (stack.getCount() <= 1) return;
        String count = Integer.toString(stack.getCount());
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        g.drawString(font, Component.literal(count), x + size - font.width(count), y + size - 8, 0xFFFFFFFF, true);
        g.pose().popPose();
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
        return renderArchiveAsset(g, itemId, entityId, enchantmentId, potionId, centerX, centerY, size, rotation, renderScale, renderMobFromSpawnEgg, 1.0F);
    }

    boolean renderArchiveAsset(GuiGraphics g, String itemId, String entityId, String enchantmentId, String potionId, int centerX, int centerY, int size, float rotation, float renderScale, boolean renderMobFromSpawnEgg, float previewScale) {
        if (archivePreviewLevel != Minecraft.getInstance().level) {
            clearArchivePreviews();
            archivePreviewLevel = Minecraft.getInstance().level;
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
                        if (renderArchiveAsset(g, "", spawnedEntity.toString(), "", potionId, centerX, centerY, size, rotation, renderScale, true, previewScale)) return true;
                    }
                    renderArchiveItemCentered(g, stack, centerX, centerY, size, previewScale);
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
            ItemStack stack = archiveEnchantedBooks.get(enchantmentId);
            if (stack == null) {
                stack = createArchiveEnchantedBook(enchantmentId);
                if (!stack.isEmpty()) archiveEnchantedBooks.put(enchantmentId, stack);
            }
            if (!stack.isEmpty()) {
                renderArchiveItemCentered(g, stack, centerX, centerY, size, previewScale);
                logArchivePreviewOnce("enchantment.render." + enchantmentId, "Archive enchantment preview rendered id={}", enchantmentId);
                return true;
            }
            logArchivePreviewOnce("enchantment.unrenderable." + enchantmentId, "Archive enchantment preview unavailable id={}", enchantmentId);
        }
        if (!potionId.isBlank() && Minecraft.getInstance().level != null) {
            try {
                ItemStack potionStack = archivePotionStacks.get(potionId);
                if (potionStack == null) {
                    ResourceLocation potionLocation = ResourceLocation.parse(potionId);
                    ResourceKey<Potion> potionKey = ResourceKey.create(Registries.POTION, potionLocation);
                    var potion = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.POTION).get(potionKey).orElse(null);
                    potionStack = potion == null ? ItemStack.EMPTY : PotionContents.createItemStack(Items.POTION, potion);
                    archivePotionStacks.put(potionId, potionStack);
                }
                if (!potionStack.isEmpty()) {
                    renderArchiveItemCentered(g, potionStack, centerX, centerY, size, previewScale);
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
                    float entityRenderScale = Math.max(0.5F, Math.min(size, size * 0.52F * renderScale / largestDimension)) * previewScale;
                    float entityScale = Math.max(0.01F, living.getScale());
                    Vector3f translation = new Vector3f(0.0F, living.getBbHeight() / 2.0F + 0.0625F * entityScale, 0.0F);
                    Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
                    Quaternionf camera = new Quaternionf();
                    g.flush();
                    int previewSize = Math.max(1, Math.round(size * previewScale));
                    enableComputerScissor(g, centerX - previewSize / 2, centerY - previewSize / 2, centerX + (previewSize + 1) / 2, centerY + (previewSize + 1) / 2);
                    if (archiveGreenTint) {
                        float[] tint = com.craisinlord.antos.content.client.ComputerTheme.active().tint();
                        g.setColor(tint[0], tint[1], tint[2], 1.0F);
                    }
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

    private void renderArchiveItemCentered(GuiGraphics g, ItemStack stack, int centerX, int centerY, int size, float previewScale) {
        int baseSize = Math.min(32, Math.max(16, size - 4));
        if (previewScale == 1.0F) {
            renderArchiveItem(g, stack, centerX - baseSize / 2, centerY - baseSize / 2, baseSize);
            return;
        }
        float iconSize = Math.max(0.01F, baseSize * previewScale);
        renderArchiveItem(g, stack, centerX - iconSize / 2.0F, centerY - iconSize / 2.0F, iconSize);
    }

    private void renderArchiveItem(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        renderArchiveItem(g, stack, (float) x, (float) y, (float) size);
    }

    private void renderArchiveItem(GuiGraphics g, ItemStack stack, float x, float y, float size) {
        if (archiveGreenTint) {
            g.flush();
            float[] tint = com.craisinlord.antos.content.client.ComputerTheme.active().tint();
            g.setColor(tint[0], tint[1], tint[2], 1.0F);
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

    private static void clearArchivePreviews() {
        archiveEntityPreviews.clear();
        unavailableArchiveEntities.clear();
        archiveItemStacks.clear();
        archiveEnchantedBooks.clear();
        archivePotionStacks.clear();
        archivePreviewLevel = null;
        archiveResolvedRecipes.clear();
        archiveRecipeLevel = null;
        archiveRecipeManager = null;
        archiveRecipeCollection = null;
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

    List<net.minecraft.util.FormattedCharSequence> wrapLines(String text, int width) {
        return wrapLines(text, null, width);
    }

    List<net.minecraft.util.FormattedCharSequence> wrapLines(Component text, int width) {
        return wrapLines(text, text, width);
    }

    private List<net.minecraft.util.FormattedCharSequence> wrapLines(Object key, Component component, int width) {
        Object language = net.minecraft.locale.Language.getInstance();
        if (wrapLanguage != language) {
            wrapLanguage = language;
            wrapCache.clear();
        }
        WrapKey wrapKey = new WrapKey(key, width);
        List<net.minecraft.util.FormattedCharSequence> lines = wrapCache.get(wrapKey);
        if (lines == null) {
            lines = List.copyOf(font.split(component == null ? Component.literal((String) key) : component, width));
            wrapCache.put(wrapKey, lines);
        }
        return lines;
    }

    private record WrapKey(Object text, int width) { }

    int wrap(GuiGraphics g, String text, int x, int y, int width, int color) {
        for (var line : wrapLines(text, width)) { g.drawString(font, line, x, y, color, false); y += 11; }
        return y;
    }

    int wrap(GuiGraphics g, Component text, int x, int y, int width, int color) {
        for (var line : wrapLines(text, width)) { g.drawString(font, line, x, y, color, false); y += 11; }
        return y;
    }

    int wrapLimited(GuiGraphics g, String text, int x, int y, int width, int bottom, int color) {
        for (var line : wrapLines(text, width)) {
            if (y + 11 > bottom) break;
            g.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }

    void drawBottomWrapped(GuiGraphics g, String text, int x, int y, int h, int width, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = wrapLines(text, width);
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
                    case "DISKS" -> session.disks.click(mouseX, mouseY, button);
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
        session.disks.drag(mouseY);
        if (dragging != null) {
            int l = -WIDTH / 2;
            int t = -HEIGHT / 2;
            dragging.x = Math.max(12, Math.min(WIDTH - dragging.renderWidth - 12, (int) mouseX - l - this.dragX));
            dragging.y = Math.max(34, Math.min(HEIGHT - dragging.renderHeight - 12, (int) mouseY - t - this.dragY));
        }
        if (activeWindow != null && activeWindow.type.equals("PAINT")) session.paint.drag(mouseX, mouseY, button);
        if (activeWindow != null && activeWindow.type.equals("TEXT")) session.text.drag(mouseX, mouseY);
        if (activeWindow != null && activeWindow.type.equals("FILES")) session.files.drag(mouseX, mouseY);
        if (activeWindow != null && activeWindow.type.equals("TERMINAL")) session.terminal.drag(mouseX, mouseY);
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
        session.disks.release();
        session.paint.release(button);
        session.text.release();
        session.files.release(mouseX, mouseY);
        session.terminal.release();
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
            case "DISKS" -> session.disks.scroll(activeWindow, mouseX, mouseY, scrollY);
            case "GAMES" -> games.scroll(activeWindow, mouseX, mouseY, scrollY);
            case "TEXT" -> session.text.scroll(activeWindow, mouseX, mouseY, scrollY);
            case "PAINT" -> session.paint.scroll(activeWindow, mouseX, mouseY, scrollY);
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
            case "DISKS" -> session.disks.type(codePoint);
            case "FILES" -> session.files.type(activeWindow, codePoint, modifiers);
            case "ARCHIVE" -> session.archive.type(activeWindow, codePoint, modifiers);
            case "TEXT" -> session.text.type(activeWindow, codePoint, modifiers);
            case "PAINT" -> session.paint.type(activeWindow, codePoint, modifiers);
            case "SETTINGS" -> session.settings.type(activeWindow, codePoint, modifiers);
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
            case "DISKS" -> session.disks.key(keyCode, modifiers);
            case "GAMES" -> games.key(activeWindow, keyCode, scanCode, modifiers);
            case "FILES" -> session.files.key(activeWindow, keyCode, scanCode, modifiers);
            case "TERMINAL" -> session.terminal.key(activeWindow, keyCode, scanCode, modifiers);
            case "ANTMAIL" -> session.antmail.key(activeWindow, keyCode, scanCode, modifiers);
            case "TEXT" -> session.text.key(activeWindow, keyCode, scanCode, modifiers);
            case "PAINT" -> session.paint.key(activeWindow, keyCode, scanCode, modifiers);
            case "SETTINGS" -> session.settings.key(activeWindow, keyCode, scanCode, modifiers);
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
        disks.addAll(com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.activatedDisks());
        return List.copyOf(disks);
    }

    List<ResourceLocation> physicalDisks() {
        return com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.installedDisks();
    }
    void open(String type) {
        if (!AntOSSettings.appEnabled(type)) return;
        if (type.equals("DISKS") && AntOSSettings.appEnabled("ANTAZON")) session.antazon.refreshListings();
        for (int index = 0; index < session.windows.size(); index++) {
            Window window = session.windows.get(index);
            if (!window.type.equals(type)) continue;
            window.minimized = false;
            session.windows.remove(index);
            session.windows.add(window);
            activeWindow = window;
            if (type.equals("FILES")) ComputerNetworking.listFiles();
              else if (type.equals("ANTAZON")) session.antazon.refocus();
            return;
        }
        Window window = new Window(type, Component.translatable("computer.antos.app." + type.toLowerCase(Locale.ROOT) + ".window_title").getString(), 112 + session.windows.size() * 12, 52 + session.windows.size() * 10);
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

    @Override
    public void removed() {
        clearArchivePreviews();
        wrapCache.clear();
        super.removed();
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
        final DisksApp disks = new DisksApp();
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
            for (ComputerApp app : List.of(antazon, terminal, wallpaperPicker, settings, disks, text, paint, files, archive, antmail)) app.attach(screen);
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
        boolean programOpen;
        String gameId = "";

        Window(String type, String title, int x, int y) {
            this.type = type;
            this.title = title;
            this.height = type.equals("ANTAZON") || type.equals("SETTINGS") || type.equals("DISKS") || type.equals("GAMES") ? 250 : 210;
            this.renderHeight = this.height;
            this.width = type.equals("TASKS") || type.equals("ANTAZON") || type.equals("DISKS") || type.equals("GAMES") || type.equals("SETTINGS") ? 390 : type.equals("FILES") ? 300 : 230;
            this.renderWidth = this.width;
            this.x = x;
            this.y = y;
            this.restoreX = x;
            this.restoreY = y;
        }
    }
}
