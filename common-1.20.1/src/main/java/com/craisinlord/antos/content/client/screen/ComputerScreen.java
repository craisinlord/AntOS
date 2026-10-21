package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.content.client.AntOSUiSounds.Cue;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
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
    private Window pointerWindow;
    private int pointerButton = -1;

    static final int WIDTH = 440;
    static final int HEIGHT = 286;
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int WINDOW_CONTROL_WIDTH = 18;
    private static final int WINDOW_CONTROL_COUNT = 3;
    private static final String[] BOOT_MESSAGES = {"ANTS MARCHING...", "COMPUTER COMPUTING...", "HCFS BREWING...", "WAKING THE QUEEN..."};
    private static final Map<String, Session> SESSIONS = new LinkedHashMap<>();

    public static void clearCachedSessions() {
        SESSIONS.clear();
    }
    private static final String[] ICONS = {"ARCHIVE", "FILES", "SETTINGS", "TASKS", "TERMINAL", "TEXT", "PAINT", "ANTMAIL", "ANTAZON", "GAMES", "DISKS", "MERCHANT"};
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
    private boolean accountHintFocused;
    private String accountHint = "";
    private String accountHintMessage = "";
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
    final CustomDisplayViewer displays = new CustomDisplayViewer(this);
    private ComputerAccessResultPayload accessResult;
    private com.craisinlord.antos.content.client.DiskRewardsClientState.Award diskRewardToast;
    private int diskRewardToastTicks;
    private String diskRewardToastKey = "";
    private int observedResult = -1;
    private Window dragging;
    private int desktopFocusIndex = -1;
    private boolean desktopKeyboardFocus;
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
    private int lookupWait;
    private ItemStack hoveredItem = ItemStack.EMPTY;
    private ItemStack frameHoveredItem = ItemStack.EMPTY;
    private int renderMouseX;
    private int renderMouseY;
    private ResourceLocation parsedWallpaperLocation;

    public ComputerScreen() {
        this(false);
    }

    ComputerScreen(boolean preview) {
        super(Component.translatable("screen.antos.computer"));
        PixelIcons.clear();
        var accountResult = com.craisinlord.antos.content.client.AnternetAccountClientState.latest();
        this.sessionKey = com.craisinlord.antos.content.client.ComputerWorkspaceClientKey.of();
        boolean freshSession = !SESSIONS.containsKey(sessionKey);
        this.session = preview ? new Session() : SESSIONS.computeIfAbsent(sessionKey, ignored -> new Session());
        this.session.attach(this);
        for (ComputerApp app : List.of(tasks, games)) app.attach(this);
        if (preview) { session.onboardingCompleted = true; return; }
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
        if (loggedIn && freshSession && AntOSSettings.preferences().resumeSession) {
            ComputerResume.restore(this, session, AntOSSettings.resumeSnapshot(sessionKey));
        }
        if (activeWindow == null) {
            for (int index = session.windows.size() - 1; index >= 0 && activeWindow == null; index--) {
                if (!session.windows.get(index).minimized) activeWindow = session.windows.get(index);
            }
        }
    }

    public static void openComputer() {
        com.craisinlord.antos.content.client.ArchiveLookup.forgetReturn();
        com.craisinlord.antos.content.client.ArchiveLookup.finishPending();
        Minecraft.getInstance().setScreen(new ComputerScreen());
    }

    public static void openPhone() {
        com.craisinlord.antos.content.client.ArchiveLookup.forgetReturn();
        com.craisinlord.antos.content.client.ArchiveLookup.finishPending();
        Minecraft.getInstance().setScreen(new ComputerScreen());
    }

    private void openLookupEntry() {
        com.craisinlord.antos.content.client.ArchiveLookup.Target target = com.craisinlord.antos.content.client.ArchiveLookup.pending();
        if (target == null || !loggedIn || !session.onboardingCompleted || session.bootTicks > 0) return;
        boolean ready;
        if (target.kind().equals("listing") || target.kind().equals("sell")) {
            com.craisinlord.antos.content.client.ArchiveLookup.finishPending();
            lookupWait = 0;
            if (!AntOSSettings.appEnabled("ANTAZON")) return;
            open("ANTAZON");
            if (target.kind().equals("listing")) session.antazon.openProduct(target.id(), "catalog");
            else session.antazon.openSellPrice(target.id());
            if (activeWindow != null && activeWindow.type.equals("ANTAZON") && !activeWindow.maximized) toggleMaximized(activeWindow);
            return;
        }
        if (target.kind().equals("task")) {
            ready = com.craisinlord.antos.content.client.ComputerTasksClientState.get().stream()
                    .anyMatch(row -> row.id().equals(target.id()) && row.visible() && !row.isLink());
            if (!ready && ++lookupWait < 60) return;
            com.craisinlord.antos.content.client.ArchiveLookup.finishPending();
            lookupWait = 0;
            if (!ready) return;
            open("TASKS");
            tasks.openDisplayTask(target.id());
            if (activeWindow != null && activeWindow.type.equals("TASKS") && !activeWindow.maximized) toggleMaximized(activeWindow);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(target.id());
        ComputerGuideData.Entry entry = id == null ? null : ComputerGuideData.availableEntry(workspaceDiskIds(),
                com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.get(), id);
        if (entry == null && ++lookupWait < 60) return;
        com.craisinlord.antos.content.client.ArchiveLookup.finishPending();
        lookupWait = 0;
        if (entry == null) return;
        session.archive.select(entry);
        open("ARCHIVE");
        if (activeWindow != null && activeWindow.type.equals("ARCHIVE") && !activeWindow.maximized) toggleMaximized(activeWindow);
    }

    @Override
    public void tick() {
        AntOSUiSounds.tick();
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
                } else if (currentResult.result() == ComputerAccessResultPayload.ACCOUNT_MISMATCH && loggedIn) {
                    loggedIn = false;
                    session.authenticated = false;
                    creatingAccount = false;
                    faceIdAutoLoginStarted = true;
                    accountMessage = Component.translatable("computer.antos.account.sign_in_here").getString();
                } else if (currentResult.result() == ComputerAccessResultPayload.BUSY) {
                    loginMessage = Component.translatable("computer.antos.status.terminal_busy").getString();
                    loginMessageTicks = 100;
                }
            }
        }
        session.antmail.tick(loggedIn);
        session.merchant.tick(loggedIn, loggedIn && session.windows.stream().anyMatch(window -> window.type.equals("MERCHANT") && !window.minimized));
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
                AntOSUiSounds.play(Cue.LOGIN);
                AntOSSettings.setLastAnternetUsername(accountSession.username());
                Minecraft.getInstance().setScreen(new ComputerScreen());
                return;
            }
            AntOSUiSounds.play(Cue.ERROR);
            accountHintMessage = accountSession.result() == AnternetAccountResultPayload.INVALID_CREDENTIALS && accountSession.username() != null
                    ? accountSession.username() : "";
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
        if (loggedIn && session.onboardingCompleted && session.bootTicks <= 0 && !lookPickerOpen() && AnternetAccountClientState.mustChangePassword()
                && session.windows.stream().noneMatch(window -> window.type.equals("SETTINGS") && !window.minimized)) {
            open("SETTINGS");
        }
        if (loggedIn && !session.desktopRequested) {
            ComputerNetworking.requestDesktopState();
            session.desktopRequested = true;
        }
        String desktop = com.craisinlord.antos.content.client.ComputerDesktopClientState.take();
        if (desktop != null && !desktop.isBlank()) {
            try {
                ComputerDesktopState state = new ComputerDesktopState();
                state.load(AntmailWire.decodeTag(desktop));
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
            if (diskRewardToast != null) {
                diskRewardToastTicks = 140;
                AntOSUiSounds.play(Cue.DISK);
            }
        }
        if (activeWindow != null && activeWindow.type.equals("GAMES")) games.tick(activeWindow);
        openLookupEntry();
    }

    @Override
    public void render(GuiGraphics source, int mouseX, int mouseY, float partialTick) {
        hoveredItem = frameHoveredItem;
        frameHoveredItem = ItemStack.EMPTY;
        renderMouseX = mouseX;
        renderMouseY = mouseY;
        displays.beginFrame();
        if (archivePreviewLevel != null && archivePreviewLevel != Minecraft.getInstance().level) clearArchivePreviews();
        this.renderBackground(source);
        Ui.refresh();
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
        graphics.fill(left - 5, top - 5, left + WIDTH + 5, top + HEIGHT + 5, Ui.BORDER_STRONG);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, Ui.BACKGROUND);
        graphics.fill(left + 5, top + 5, left + WIDTH - 5, top + HEIGHT - 5, Ui.SURFACE);
        if (session.bootTicks > 0) renderBoot(graphics, left, top); else if (loggedIn) renderDesktop(graphics, left, top, localMouseX, localMouseY); else renderLogin(graphics, left, top);
        graphics.pose().popPose();
        graphics.flush();
    }

    float uiScale() {
        float availableScale = Math.max(0.1F, Math.min((width - 24.0F) / WIDTH, (height - 24.0F) / HEIGHT));
        return Math.min(AntOSSettings.antosUiScale(), availableScale);
    }

    private void renderBoot(GuiGraphics g, int l, int t) {
        int progress = Math.min(100, (80 - session.bootTicks) * 100 / 80);
        int message = Math.min(BOOT_MESSAGES.length - 1, progress * BOOT_MESSAGES.length / 101);
        g.drawString(font, Component.literal("AntOS // v" + AntOS.MOD_VERSION), l + 28, t + 78, Ui.TEXT_STRONG, false);
        g.drawString(font, Component.literal("INITIALIZING SYSTEM..."), l + 28, t + 105, Ui.TEXT, false);
        g.fill(l + 28, t + 132, l + WIDTH - 28, t + 148, Ui.BACKGROUND);
        box(g, l + 28, t + 132, l + WIDTH - 28, t + 148, Ui.BORDER_STRONG);
        g.fill(l + 32, t + 136, l + 32 + (WIDTH - 64) * progress / 100, t + 144, Ui.ACCENT);
        g.drawString(font, Component.literal(progress + "%"), l + 28, t + 164, Ui.TEXT_STRONG, false);
        g.drawString(font, Component.literal(BOOT_MESSAGES[message]), l + 28, t + 190, Ui.TEXT, false);
    }

    private void renderLogin(GuiGraphics g, int l, int t) {
        g.drawString(font, Component.literal("AntOS // v" + AntOS.MOD_VERSION), l + 28, t + 25, Ui.TEXT_STRONG, false);
        g.drawString(font, Component.literal("ANTERNET // ACCOUNT"), l + 28, t + 43, Ui.TEXT, false);
        g.fill(l + 28, t + 59, l + WIDTH - 28, t + 61, Ui.BORDER_STRONG);
        int buttonX = l + WIDTH / 2 - 70;
        loginButton(g, buttonX, t + 70, 66, 20, "LOG IN", !creatingAccount);
        loginButton(g, buttonX + 74, t + 70, 66, 20, "CREATE", creatingAccount);
        g.drawString(font, Component.literal("USERNAME"), l + 28, t + 99, Ui.TEXT, false);
        g.fill(l + 28, t + 109, l + WIDTH - 28, t + 132, Ui.BACKGROUND);
        box(g, l + 28, t + 109, l + WIDTH - 28, t + 132, accountPasswordFocused || accountHintFocused ? Ui.BORDER_INACTIVE : Ui.ACCENT);
        g.drawString(font, Component.literal(accountUsername + (accountPasswordFocused || accountHintFocused ? "" : "_")), l + 34, t + 117, Ui.TEXT, false);
        g.drawString(font, Component.literal("PASSWORD"), l + 28, t + 140, Ui.TEXT, false);
        g.fill(l + 28, t + 150, l + WIDTH - 28, t + 173, Ui.BACKGROUND);
        box(g, l + 28, t + 150, l + WIDTH - 28, t + 173, accountPasswordFocused ? Ui.ACCENT : Ui.BORDER_INACTIVE);
        g.drawString(font, Component.literal("*".repeat(accountPassword.length()) + (accountPasswordFocused ? "_" : "")), l + 34, t + 158, Ui.TEXT, false);
        if (creatingAccount) {
            g.drawString(font, Component.translatable("computer.antos.account.hint_label"), l + 28, t + 178, Ui.TEXT, false);
            g.fill(l + 28, t + 188, l + WIDTH - 28, t + 208, Ui.BACKGROUND);
            box(g, l + 28, t + 188, l + WIDTH - 28, t + 208, accountHintFocused ? Ui.ACCENT : Ui.BORDER_INACTIVE);
            g.drawString(font, Component.literal(trimToWidth(accountHint + (accountHintFocused ? "_" : ""), WIDTH - 68)), l + 34, t + 194, Ui.TEXT, false);
        }
        loginButton(g, buttonX, t + (creatingAccount ? 214 : 184), 140, 22, accountWaiting ? "CONNECTING..." : creatingAccount ? "CREATE ACCOUNT" : "SIGN IN", true);
        Component accountMessageComponent = accountMessage == null
                ? Component.translatable("computer.antos.account.initial_prompt")
                : Component.literal(accountMessage);
        g.drawCenteredString(font, accountMessageComponent, l + WIDTH / 2, t + (creatingAccount ? 242 : 218),
                accountMessage != null && (accountMessage.contains("UNAVAILABLE") || accountMessage.contains("NOT RECOGNIZED")) ? Ui.ERROR : Ui.TEXT);
        if (!creatingAccount && !accountHintMessage.isEmpty()) {
            g.drawCenteredString(font, Component.literal(trimToWidth(Component.translatable("computer.antos.account.hint_shown", accountHintMessage).getString(), WIDTH - 56)),
                    l + WIDTH / 2, t + 231, Ui.TEXT);
        }
        g.drawCenteredString(font, Component.literal("Turn on FACE ID in settings to skip login"), l + WIDTH / 2, t + HEIGHT - 14, Ui.TEXT);
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
        box(g, x, y, x + width, y + height, selected || hovered(x, y, width, height) ? Ui.ACCENT : Ui.BORDER_INACTIVE);
        g.drawCenteredString(font, Component.literal(label), x + width / 2, y + (height - 8) / 2, selected ? Ui.ACCENT : Ui.TEXT);
    }

    private void cycleAccountField() {
        if (accountHintFocused) {
            accountHintFocused = false;
            accountPasswordFocused = false;
        } else if (accountPasswordFocused) {
            accountPasswordFocused = false;
            accountHintFocused = creatingAccount;
        } else {
            accountPasswordFocused = true;
        }
    }

    private void submitAccount() {
        if (accountWaiting) return;
        String username = accountUsername.trim();
        if (username.length() < 3) { accountMessage = "USERNAME NEEDS AT LEAST 3 CHARACTERS"; return; }
        if (accountPassword.isEmpty()) { accountMessage = "PASSWORD CANNOT BE EMPTY"; return; }
        if (creatingAccount && !com.craisinlord.antos.content.computer.ComputerWorkspaceData.validHint(accountHint, accountPassword)) {
            accountMessage = Component.translatable("computer.antos.account.hint_contains_password").getString();
            return;
        }
        accountHintMessage = "";
        accountWaiting = true;
        accountMessage = creatingAccount ? "CREATING ACCOUNT..." : "SIGNING IN...";
        AnternetAccountClientState.clear();
        AnternetAccountNetworking.checkFaceId();
        if (creatingAccount) AnternetAccountNetworking.create(username, accountPassword, accountHint.strip());
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
        if (lookPickerOpen()) {
            beginLayer(g);
            renderLookPicker(g, l, t);
        } else if (!session.onboardingCompleted) {
            beginLayer(g);
            renderOnboarding(g, l, t, mouseX, mouseY);
        }
        if (diskRewardToast != null && diskRewardToastTicks > 0) renderDiskRewardToast(g, l, t, layer + 2);
        if (session.onboardingCompleted && !lookPickerOpen()) displays.renderOverlay(g);
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
            g.fill(x, y, x + 216, y + h, Ui.BACKGROUND);
            box(g, x, y, x + 216, y + h, Ui.BORDER_STRONG);
            g.drawString(font, Component.translatable("computer.antos.disks.rewards_received"), x + 8, y + 7, Ui.TEXT_STRONG, false);
            g.drawString(font, trimToWidth(Component.translatable(diskRewardToast.title()).getString(), 198), x + 8, y + 21, Ui.TEXT, false);
            for (int i = 0; i < rows; i++) g.drawString(font, trimToWidth("+ " + com.craisinlord.antos.content.client.DiskRewardsClientState.label(diskRewardToast.rewards().get(i)), 198), x + 8, y + 35 + i * 12, Ui.TEXT, false);
            if (diskRewardToast.rewards().size() > rows) g.drawString(font, Component.translatable("computer.antos.disks.more_rewards"), x + 8, y + 35 + rows * 12, Ui.TEXT_STRONG, false);
        } finally {
            g.flush();
            g.pose().popPose();
        }
    }

    private void renderDesktopSurface(GuiGraphics g, int l, int t, int mouseX, int mouseY) {
        renderWallpaper(g, l, t);
        g.fill(l + 9, t + 9, l + WIDTH - 9, t + 30, Ui.BACKGROUND);
        g.drawString(font, Component.literal("AntOS // v" + AntOS.MOD_VERSION), l + 18, t + 15, Ui.TEXT_STRONG, false);
        long dayTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getDayTime();
        long dayTicks = Math.floorMod(dayTime, 24000L);
        int hours = (int) ((dayTicks / 1000L + 6L) % 24L);
        int minutes = (int) (dayTicks % 1000L * 60L / 1000L);
        long minute = hours * 60L + minutes;
        if (minute != clockMinute) {
            clockMinute = minute;
            clockLabel = String.format("%02d:%02d", hours, minutes);
        }
        g.drawString(font, Component.literal(clockLabel), l + WIDTH - 48, t + 15, Ui.TEXT, false);
        g.fill(l + 9, t + 31, l + WIDTH - 9, t + 33, Ui.BORDER_STRONG);
        List<String> desktopApps = renderedDesktopApps();
        for (int i = 0; i < desktopApps.size(); i++) {
            String app = desktopApps.get(i);
            int x = desktopIconX(l, i);
            int y = desktopIconY(t, i);
            boolean hover = windowAtLocal(mouseX, mouseY) == null && inside(x - 8, y - 8, 84, 61, mouseX, mouseY);
            if (hover) drawHover(g, x - 9, y - 9, 84, 62);
            drawDesktopIcon(g, app, x + 25, y + 2);
            if (app.equals("ANTMAIL") && session.antmail.hasUnreadMessages()) drawNotificationBadge(g, x + 35, y + 6);
            if (app.equals("ANTAZON") && session.antazon.hasUnseenDeals()) drawNotificationBadge(g, x + 35, y + 6);
            if (app.equals("MERCHANT") && session.merchant.hasPending()) drawNotificationBadge(g, x + 35, y + 6);
            g.drawString(font, Component.translatable(appNameKey(app)), x, y + 35, Ui.TEXT_STRONG, false);
            if (desktopKeyboardFocus && desktopFocusIndex == i) {
                box(g, x - 10, y - 10, x + 76, y + 53, Ui.BACKGROUND);
                box(g, x - 9, y - 9, x + 75, y + 52, Ui.FOCUS);
            }
        }
    }

    private void renderOnboarding(GuiGraphics g, int l, int t, int mouseX, int mouseY) {
        List<String> apps = renderedDesktopApps();
        int appStep = session.onboardingStep - 1;
        boolean welcome = session.onboardingStep == 0;
        g.fill(l + 9, t + 34, l + WIDTH - 9, t + HEIGHT - 9, Ui.alpha(Ui.SHADOW, 0xC4));
        if (!welcome && appStep >= 0 && appStep < apps.size()) {
            String app = apps.get(appStep);
            int desktopIndex = renderedDesktopApps().indexOf(app);
            if (desktopIndex >= 0) {
                int iconX = desktopIconX(l, desktopIndex);
                int iconY = desktopIconY(t, desktopIndex);
                g.fill(iconX - 10, iconY - 10, iconX + 66, iconY + 52, Ui.HOVER);
                box(g, iconX - 10, iconY - 10, iconX + 66, iconY + 52, Ui.ACCENT);
                drawDesktopIcon(g, app, iconX + 25, iconY + 2);
                g.drawString(font, Component.translatable(appNameKey(app)), iconX, iconY + 35, Ui.TEXT_STRONG, false);
            }
            int panelX = onboardingAppPanelX(l, desktopIndex);
            int panelY = onboardingAppPanelY(t, desktopIndex);
            int panelW = 190;
            int panelH = 154;
            int contentX = panelX + 10;
            int actionY = panelY + 121;
            g.fill(panelX, panelY, panelX + panelW, panelY + panelH, Ui.BACKGROUND);
            box(g, panelX, panelY, panelX + panelW, panelY + panelH, Ui.BORDER_STRONG);
            g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + 25, Ui.SURFACE);
            g.drawString(font, Component.translatable("computer.antos.onboarding.app_tour"), contentX, panelY + 8, Ui.TEXT_STRONG, false);
            g.drawString(font, Component.translatable(appNameKey(app)), contentX, panelY + 35, Ui.TEXT_STRONG, false);
            g.drawString(font, Component.translatable("computer.antos.onboarding.app_step", appStep + 1, apps.size()), panelX + panelW - 68, panelY + 35, Ui.TEXT, false);
            g.drawString(font, Component.literal(onboardingAppTitle(app)), contentX, panelY + 53, Ui.TEXT, false);
            wrap(g, onboardingAppDescription(app), contentX, panelY + 70, panelW - 20, Ui.TEXT);
            if (appStep > 0) onboardingButton(g, contentX, actionY, 82, Component.translatable("computer.antos.onboarding.back"), hovered(contentX, actionY, 82, 22));
            Component nextLabel = Component.translatable(appStep + 1 < apps.size()
                    ? "computer.antos.onboarding.next" : "computer.antos.onboarding.done");
            onboardingButton(g, panelX + panelW - 78, actionY, 68, nextLabel, hovered(panelX + panelW - 78, actionY, 68, 22));
            if (desktopIndex >= 0) {
                int iconX = desktopIconX(l, desktopIndex);
                int iconY = desktopIconY(t, desktopIndex);
                g.fill(iconX - 10, iconY - 10, iconX + 66, iconY + 52, Ui.HOVER);
                box(g, iconX - 10, iconY - 10, iconX + 66, iconY + 52, Ui.ACCENT);
                drawDesktopIcon(g, app, iconX + 25, iconY + 2);
            g.drawString(font, Component.translatable(appNameKey(app)), iconX, iconY + 35, Ui.TEXT_STRONG, false);
            }
            return;
        }
        int panelX = l + 30;
        int panelY = t + 43;
        int panelW = WIDTH - 60;
        int panelH = 228;
        int contentX = panelX + 22;
        int actionY = panelY + 185;
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, Ui.BACKGROUND);
        box(g, panelX, panelY, panelX + panelW, panelY + panelH, Ui.BORDER_STRONG);
        g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + 25, Ui.SURFACE);
        g.drawString(font, Component.translatable("computer.antos.onboarding.title"), contentX, panelY + 8, Ui.TEXT_STRONG, false);
        if (welcome) {
            g.drawString(font, Component.translatable("computer.antos.onboarding.welcome_title"), contentX, panelY + 43, Ui.TEXT_STRONG, false);
            g.drawString(font, Component.translatable("computer.antos.onboarding.welcome_subtitle"), contentX, panelY + 62, Ui.TEXT, false);
            wrap(g, Component.translatable("computer.antos.onboarding.welcome_description"), contentX, panelY + 91, panelW - 44, Ui.TEXT);
            g.drawString(font, Component.translatable("computer.antos.onboarding.duration"), contentX, panelY + 145, Ui.TEXT_STRONG, false);
            g.drawString(font, Component.translatable("computer.antos.onboarding.welcome"), contentX, panelY + 166, Ui.TEXT, false);
            onboardingButton(g, contentX, actionY, 126, Component.translatable("computer.antos.onboarding.start_tour"), hovered(contentX, actionY, 126, 22));
            onboardingButton(g, panelX + panelW - 110, actionY, 88, Component.translatable("computer.antos.onboarding.skip"), hovered(panelX + panelW - 110, actionY, 88, 22));
            return;
        }
        finishOnboarding();
    }

    private void onboardingButton(GuiGraphics g, int x, int y, int width, Component label, boolean hover) {
        if (hover) drawHover(g, x, y, width, 22);
        g.drawCenteredString(font, label, x + width / 2, y + 7, Ui.TEXT_STRONG);
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
        for (int[] rect : BADGE_RECTS) g.fill(x - 6 + rect[0], y - 6 + rect[1], x - 6 + rect[2], y - 6 + rect[3], rect[4] == 0 ? Ui.BACKGROUND : Ui.ACCENT);
        g.fill(x - 1, y - 4, x + 1, y + 2, Ui.BACKGROUND);
        g.fill(x - 1, y + 3, x + 1, y + 5, Ui.BACKGROUND);
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
        int base = Ui.proceduralWallpaper(Ui.alpha(green / 2 << 16 | green << 8 | blue, 255));
        g.fill(left, top, right, bottom, base);
        if (wallpaperId.endsWith("grid_ant")) {
            for (int py = top + 2; py < bottom; py += 18) {
                for (int px = left + 2; px < right; px += 36) {
                    g.fill(px, py, px + 2, py + 2, Ui.HOVER);
                }
            }
            return;
        }
        int accent = Ui.proceduralWallpaper(Ui.alpha(Math.min(120, green + 34) << 8 | Math.min(80, blue + 34), 255));
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
            parsedWallpaperLocation = ResourceLocation.tryParse(wallpaperId);
            parsedWallpaperId = wallpaperId;
        }
        return parsedWallpaperLocation;
    }

    private static ResourceLocation wallpaperTexture(String textureIdValue) {
        Optional<ResourceLocation> cached = WALLPAPER_TEXTURES.get(textureIdValue);
        if (cached == null) {
            ResourceLocation texture;
            try {
                ResourceLocation textureId = ResourceLocation.tryParse(textureIdValue);
                texture = new ResourceLocation(textureId.getNamespace(), "textures/" + textureId.getPath() + ".png");
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
            ResourceLocation drawn = Ui.matchWallpapers() ? com.craisinlord.antos.content.client.palette.WallpaperRecolor.recolored(texture) : null;
            g.blit(drawn == null ? texture : drawn, x, y, width, height, u, v, sourceWidth, sourceHeight, size[0], size[1]);
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
        int savedMouseX = session.mouseX;
        int savedMouseY = session.mouseY;
        int savedArchiveX = session.archiveMouseX;
        int savedArchiveY = session.archiveMouseY;
        boolean hovered = windowAtLocal(mouseX, mouseY) == window;
        if (!hovered) {
            session.mouseX = session.archiveMouseX = -10000;
            session.mouseY = session.archiveMouseY = -10000;
        }
        try {
            renderWindowLayer(g, window, l, t, hovered ? mouseX : -10000, hovered ? mouseY : -10000);
        } finally {
            session.mouseX = savedMouseX;
            session.mouseY = savedMouseY;
            session.archiveMouseX = savedArchiveX;
            session.archiveMouseY = savedArchiveY;
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
        g.fill(x - WindowBounds.FRAME, y - WindowBounds.FRAME, x + w + WindowBounds.FRAME, y + h + WindowBounds.FRAME, Ui.BORDER_STRONG);
        g.fill(x, y, x + w, y + h, Ui.BACKGROUND);
        g.fill(x, y, x + w, y + TITLE_BAR_HEIGHT, Ui.TITLE_BAR);
        int controlsLeft = x + w - WINDOW_CONTROL_WIDTH * WINDOW_CONTROL_COUNT;
        String title = window.title;
        if (window.type.equals("ANTMAIL")) {
            AntmailMailbox mailbox = session.antmail.mailbox(AntmailClientState.getMailbox());
            if (mailbox != null) title = mailbox.address().fullAddress();
        }
        g.drawString(font, Component.literal(trimToWidth(title, Math.max(1, controlsLeft - x - 12))), x + 7, y + 6, Ui.TEXT_STRONG, false);
        g.fill(controlsLeft, y, x + w, y + TITLE_BAR_HEIGHT, Ui.TITLE_BAR);
        g.fill(controlsLeft, y, controlsLeft + 1, y + TITLE_BAR_HEIGHT, Ui.BORDER_STRONG);
        g.fill(controlsLeft + WINDOW_CONTROL_WIDTH, y, controlsLeft + WINDOW_CONTROL_WIDTH + 1, y + TITLE_BAR_HEIGHT, Ui.BORDER_STRONG);
        g.fill(controlsLeft + WINDOW_CONTROL_WIDTH * 2, y, controlsLeft + WINDOW_CONTROL_WIDTH * 2 + 1, y + TITLE_BAR_HEIGHT, Ui.BORDER_STRONG);
        for (int control = 0; control < WINDOW_CONTROL_COUNT; control++) {
            if (hovered(controlsLeft + control * WINDOW_CONTROL_WIDTH, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT)) {
                g.fill(controlsLeft + control * WINDOW_CONTROL_WIDTH, y, controlsLeft + (control + 1) * WINDOW_CONTROL_WIDTH, y + TITLE_BAR_HEIGHT, Ui.TITLE_BAR);
            }
        }
        g.drawCenteredString(font, Component.literal("_"), controlsLeft + WINDOW_CONTROL_WIDTH / 2, y + 5, Ui.TEXT);
        g.drawCenteredString(font, Component.literal(window.maximized ? "❐" : "□"), controlsLeft + WINDOW_CONTROL_WIDTH + WINDOW_CONTROL_WIDTH / 2, y + 5, Ui.TEXT);
        g.drawCenteredString(font, Component.literal("X"), controlsLeft + WINDOW_CONTROL_WIDTH * 2 + WINDOW_CONTROL_WIDTH / 2, y + 6, Ui.TEXT_STRONG);
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
            else if (window.type.equals("MERCHANT")) session.merchant.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("ANTAZON")) session.antazon.render(g, cx, cy, w - 16, h - 34);
            else if (window.type.equals("GAMES")) games.render(g, window, cx, cy, w - 16, h - 34, mouseX, mouseY);
            else if (window.type.equals("WALLPAPERS")) session.wallpaperPicker.render(g, cx, cy, h - 34);
            if (window == activeWindow) {
                AppKit kit = windowAppKit(window);
                if (kit != null) {
                    kit.focusTarget(controlsLeft, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT, () -> minimizeWindow(window));
                    kit.focusTarget(controlsLeft + WINDOW_CONTROL_WIDTH, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT, () -> toggleMaximized(window));
                    kit.focusTarget(controlsLeft + WINDOW_CONTROL_WIDTH * 2, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT, () -> closeWindow(window));
                    kit.drawFocus(g);
                }
            }
        } finally {
            g.disableScissor();
        }
    }

    private AppKit windowAppKit(Window window) {
        return switch (window.type) {
            case "MERCHANT" -> session.merchant.kit;
            case "ARCHIVE" -> session.archive.kit;
            case "FILES" -> session.files.kit;
            case "PAINT" -> session.paint.kit;
            case "SETTINGS" -> session.settings.kit;
            case "TERMINAL" -> session.terminal.kit;
            case "TEXT" -> session.text.kit;
            default -> null;
        };
    }
    int renderTaskItemRecipe(GuiGraphics g, String itemId, int x, int y, int w) {
        if (Minecraft.getInstance().level != null) {
            try {
                if (resolveArchiveRecipe(itemId) != null) return renderArchiveRecipe(g, itemId, x, y, w);
            } catch (RuntimeException ignored) { }
        }
        return wrap(g, "NO RECIPE FOUND", x, y, w, Ui.TEXT_SOFT);
    }

    int renderTaskSectionLabel(GuiGraphics g, String label, int x, int y, int width) {
        g.drawString(font, Component.literal(label), x, y, Ui.TEXT_STRONG, false);
        g.fill(x, y + 10, x + width, y + 11, Ui.SURFACE);
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
        AntOSUiSounds.play(window.maximized ? Cue.OPEN : Cue.MAXIMIZE);
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
            return wrap(g, "RECIPE // " + recipeId, x, y, w, Ui.TEXT);
        }
        return y + slot * 3;
    }

    boolean renderArchiveRecipe(GuiGraphics g, String recipeId, int x, int y, int width, int slot) {
        if (Minecraft.getInstance().level == null) return false;
        try {
            net.minecraft.world.item.crafting.Recipe<?> holder = resolveArchiveRecipe(recipeId);
            if (holder == null) return false;
            var recipe = holder;
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
                g.fill(cellX, cellY, cellX + actualSlot - 1, cellY + actualSlot - 1, Ui.HOVER);
                if (ingredientIndex >= 0 && ingredientIndex < ingredients.size()) {
                    ItemStack[] choices = ingredients.get(ingredientIndex).getItems();
                    if (choices.length > 0) {
                        ItemStack shown = choices[(int) ((System.currentTimeMillis() / 1000L) % choices.length)];
                        renderArchiveItem(g, shown, cellX, cellY, actualSlot);
                    }
                }
            }
            g.fill(x + gridWidth + 2, y + actualSlot + actualSlot / 2 - 1, x + gridWidth + 6, y + actualSlot + actualSlot / 2 + 1, Ui.TEXT);
            g.fill(x + gridWidth + 5, y + actualSlot + actualSlot / 2 - 3, x + gridWidth + 7, y + actualSlot + actualSlot / 2 + 3, Ui.TEXT);
            List<ItemStack> results = archiveRecipeResults(recipe, access);
            for (int index = 0; index < Math.min(2, results.size()); index++) {
                int resultY = outputY + index * actualSlot;
                g.fill(outputX, resultY, outputX + actualSlot - 1, resultY + actualSlot - 1, Ui.HOVER);
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

    private static final Map<String, Optional<net.minecraft.world.item.crafting.Recipe<?>>> archiveResolvedRecipes = new HashMap<>();
    private static final Map<Class<?>, Optional<java.lang.reflect.Method>> archiveResultMethods = new HashMap<>();
    private static Object archiveRecipeLevel;
    private static Object archiveRecipeManager;
    private static Object archiveRecipeCollection;

    net.minecraft.world.item.crafting.Recipe<?> resolveArchiveRecipe(String recipeId) {
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
        Optional<net.minecraft.world.item.crafting.Recipe<?>> cached = archiveResolvedRecipes.get(recipeId);
        if (cached != null) return cached.orElse(null);
        net.minecraft.world.item.crafting.Recipe<?> holder = null;
        ResourceLocation requestedId = ResourceLocation.tryParse(recipeId);
        if (requestedId != null) {
            holder = recipeManager.byKey(requestedId).orElse(null);
            if (holder == null) {
                var requestedItem = BuiltInRegistries.ITEM.getOptional(requestedId).orElse(null);
                if (requestedItem != null && requestedItem != Items.AIR) {
                    var access = level.registryAccess();
                    holder = recipeCollection.stream()
                            .filter(candidate -> archiveRecipeResults(candidate, access).stream().anyMatch(stack -> stack.is(requestedItem)))
                            .min(java.util.Comparator.comparingInt(candidate -> archiveRecipePriority(candidate)))
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
        g.drawString(font, Component.literal(count), x + size - font.width(count), y + size - 8, Ui.TEXT_BRIGHT, true);
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
        g.fill(x, y, x + boxWidth, y + previewSize, Ui.SURFACE);
        int centerX = x + boxWidth / 2;
        int centerY = y + previewSize / 2;
        if (!renderArchiveAsset(g, itemId, entityId, enchantmentId, "", centerX, centerY, boxWidth, rotation, renderScale)) {
            String fallback = !enchantmentId.isBlank() ? enchantmentId : entityId;
            if (!fallback.isBlank()) g.drawString(font, Component.literal(trimToWidth(fallback, Math.max(1, w - boxWidth - 6))), x + boxWidth + 6, y + previewSize / 2 - 4, Ui.TEXT, false);
        } else if (!entityId.isBlank() || !enchantmentId.isBlank()) {
            String label = !enchantmentId.isBlank() ? "ENCHANTMENT // " + enchantmentId : "MOB // " + archiveMobName(entityId);
            g.drawString(font, Component.literal(trimToWidth(label, Math.max(1, w - boxWidth - 6))), x + boxWidth + 6, y + previewSize / 2 - 4, Ui.TEXT, false);
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
                    var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(itemId)).orElse(null);
                    if (LOG_ARCHIVE_PREVIEWS) logArchivePreviewOnce("item.lookup." + itemId, "Archive item preview lookup id={} found={} registryName={}", itemId, item != null, item == null ? "<missing>" : BuiltInRegistries.ITEM.getKey(item));
                    stack = item == null || item.equals(net.minecraft.world.item.Items.AIR) ? ItemStack.EMPTY : new ItemStack(item);
                    archiveItemStacks.put(itemId, stack);
                }
                if (!stack.isEmpty()) {
                    if (renderMobFromSpawnEgg && stack.getItem() instanceof SpawnEggItem spawnEgg) {
                        ResourceLocation spawnedEntity = BuiltInRegistries.ENTITY_TYPE.getKey(spawnEgg.getType(stack.getTag()));
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
                    ResourceLocation potionLocation = ResourceLocation.tryParse(potionId);
                    ResourceKey<Potion> potionKey = ResourceKey.create(Registries.POTION, potionLocation);
                    var potion = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.POTION).get(potionKey).orElse(null);
                    potionStack = potion == null ? ItemStack.EMPTY : PotionUtils.setPotion(new ItemStack(Items.POTION), potion.value());
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
                ResourceLocation resourceId = ResourceLocation.tryParse(entityId);
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
                        float[] tint = Ui.previewTint();
                        g.setColor(tint[0], tint[1], tint[2], 1.0F);
                    }
                    try {
                        InventoryScreen.renderEntityInInventory(g, centerX, centerY, Math.round(entityRenderScale / entityScale),
                                pose, camera, living);
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

    void noteItem(GuiGraphics g, ItemStack stack, float x, float y, float size) {
        if (stack == null || stack.isEmpty()) return;
        org.joml.Matrix4f matrix = g.pose().last().pose();
        org.joml.Vector4f from = matrix.transform(new org.joml.Vector4f(x, y, 0.0F, 1.0F));
        org.joml.Vector4f to = matrix.transform(new org.joml.Vector4f(x + size, y + size, 0.0F, 1.0F));
        if (renderMouseX >= Math.min(from.x(), to.x()) && renderMouseX < Math.max(from.x(), to.x())
                && renderMouseY >= Math.min(from.y(), to.y()) && renderMouseY < Math.max(from.y(), to.y())) frameHoveredItem = stack;
    }

    boolean hasTaskItemRecipe(String itemId) {
        if (Minecraft.getInstance().level == null) return false;
        try { return resolveArchiveRecipe(itemId) != null; }
        catch (RuntimeException ignored) { return false; }
    }

    boolean openInJei(String itemId, boolean uses) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) return false;
        return openInJei(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)), uses);
    }

    boolean openInJei(ItemStack stack, boolean uses) {
        if (stack.isEmpty() || !com.craisinlord.antos.content.client.JeiBridge.has(stack, uses)) return false;
        ItemStack target = stack.copyWithCount(1);
        if (com.craisinlord.antos.content.client.JeiBridge.recipesScreen(com.craisinlord.antos.content.client.ArchiveLookup.peekReturn())) onClose();
        return com.craisinlord.antos.content.client.JeiBridge.show(target, uses);
    }

    private boolean typingInActiveWindow() {
        if (activeWindow == null) return false;
        return switch (activeWindow.type) {
            case "ARCHIVE" -> session.archive.state.searchFocused;
            case "TASKS" -> tasks.typing();
            case "MERCHANT" -> session.merchant.typing();
            case "ANTAZON" -> session.antazon.typing();
            case "TERMINAL", "TEXT", "ANTMAIL", "PAINT", "SETTINGS", "FILES" -> true;
            default -> false;
        };
    }

    private void renderArchiveItem(GuiGraphics g, ItemStack stack, float x, float y, float size) {
        noteItem(g, stack, x, y, size);
        if (archiveGreenTint) {
            g.flush();
            float[] tint = Ui.previewTint();
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
            ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
            ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
            var enchantment = Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).get(id);
            if (enchantment == null) return ItemStack.EMPTY;
            net.minecraft.world.item.EnchantedBookItem.addEnchantment(stack, new EnchantmentInstance(enchantment, 1));
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
        g.fill(x, top, x + 3, top + safeTrackHeight, Ui.HOVER);
        g.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, Ui.BORDER_STRONG);
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
        g.fill(x, y, x + width, y + 17, selected ? Ui.TEXT : hover ? Ui.HOVER : Ui.SURFACE);
        box(g, x, y, x + width, y + 17, selected ? Ui.ACCENT : Ui.LINE);
        float scale = Math.min(0.82F, Math.max(0.55F, (width - 8.0F) / Math.max(1, font.width(label))));
        g.pose().pushPose();
        try {
            g.pose().translate(x + width / 2.0F, y + 4.0F, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, Component.literal(label), -font.width(label) / 2, 0,
                    selected ? Ui.BACKGROUND : Ui.ACCENT, false);
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
        g.fill(x, y, x + width, y + height, Ui.HOVER);
        box(g, x, y, x + width, y + height, Ui.ACCENT);
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
            AntOSUiSounds.play(Cue.CLICK);
            int buttonX = l + WIDTH / 2 - 70;
            if (inside(buttonX, t + 70, 66, 20, mouseX, mouseY)) {
                creatingAccount = false;
                accountHintFocused = false;
                accountMessage = "SIGN IN TO YOUR ANTERNET ACCOUNT";
            } else if (inside(buttonX + 74, t + 70, 66, 20, mouseX, mouseY)) {
                creatingAccount = true;
                accountHintMessage = "";
                accountMessage = "CHOOSE A USERNAME AND PASSWORD";
            } else if (inside(l + 28, t + 109, WIDTH - 56, 23, mouseX, mouseY)) {
                accountPasswordFocused = false;
                accountHintFocused = false;
            } else if (inside(l + 28, t + 150, WIDTH - 56, 23, mouseX, mouseY)) {
                accountPasswordFocused = true;
                accountHintFocused = false;
            } else if (creatingAccount && inside(l + 28, t + 188, WIDTH - 56, 20, mouseX, mouseY)) {
                accountPasswordFocused = false;
                accountHintFocused = true;
            } else if (inside(buttonX, t + (creatingAccount ? 214 : 184), 140, 22, mouseX, mouseY)) submitAccount();
            return true;
        }
        session.lastUse = System.currentTimeMillis();
        if (lookPickerOpen()) return lookPickerClicked(mouseX, mouseY);
        if (!session.onboardingCompleted) return onboardingClicked(mouseX, mouseY);
        if (displays.click(mouseX, mouseY, button)) return true;
        if (pointerWindow != null || dragging != null) return true;
        Window window = windowAtLocal(mouseX, mouseY);
        if (window != null) {
            WindowBounds bounds = bounds(window);
            int x = bounds.x();
            int y = bounds.y();
            int w = bounds.width();
            int h = bounds.height();
            activeWindow = window;
            session.windows.remove(window);
            session.windows.add(window);
            if (bounds.content(mouseX, mouseY, TITLE_BAR_HEIGHT)) {
                pointerWindow = window;
                pointerButton = button;
                switch (window.type) {
                    case "TASKS" -> tasks.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "DISKS" -> session.disks.click(mouseX, mouseY, button);
                    case "ARCHIVE" -> session.archive.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "FILES" -> session.files.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "PAINT" -> session.paint.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "TEXT" -> session.text.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "ANTMAIL" -> session.antmail.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "MERCHANT" -> session.merchant.click(mouseX, mouseY);
            case "ANTAZON" -> session.antazon.click(mouseX, mouseY);
                    case "GAMES" -> games.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "WALLPAPERS" -> session.wallpaperPicker.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "TERMINAL" -> session.terminal.click(window, mouseX, mouseY, button, x, y, w, h);
                    case "SETTINGS" -> session.settings.click(window, mouseX, mouseY, button, x, y, w, h);
                }
            } else if (button == 0 && bounds.title(mouseX, mouseY, TITLE_BAR_HEIGHT)) {
                int controlsLeft = x + w - WINDOW_CONTROL_WIDTH * WINDOW_CONTROL_COUNT;
                if (inside(controlsLeft + WINDOW_CONTROL_WIDTH * 2, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT, mouseX, mouseY)) closeWindow(window);
                else if (inside(controlsLeft + WINDOW_CONTROL_WIDTH, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT, mouseX, mouseY)) toggleMaximized(window);
                else if (inside(controlsLeft, y, WINDOW_CONTROL_WIDTH, TITLE_BAR_HEIGHT, mouseX, mouseY)) minimizeWindow(window);
                else if (!window.maximized) { dragging = window; dragX = (int) mouseX - x; dragY = (int) mouseY - y; }
            }
            return true;
        }
        List<String> desktopApps = desktopApps();
        for (int i = 0; i < desktopApps.size(); i++) {
            int x = desktopIconX(l, i);
            int y = desktopIconY(t, i);
            if (inside(x - 8, y - 8, 84, 61, mouseX, mouseY)) { desktopFocusIndex = i; desktopKeyboardFocus = false; open(desktopApps.get(i)); return true; }
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

    private static final String[] LOOKS = {"terminal", "graphite"};

    private boolean lookPickerOpen() {
        return loggedIn && session.bootTicks <= 0 && !AntOSSettings.preferences().lookChosen;
    }

    private void renderLookPicker(GuiGraphics g, int l, int t) {
        int panelX = l + 30;
        int panelY = t + 43;
        int panelW = WIDTH - 60;
        int panelH = 228;
        int contentX = panelX + 22;
        g.fill(l + 9, t + 34, l + WIDTH - 9, t + HEIGHT - 9, Ui.alpha(Ui.SHADOW, 0xC4));
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, Ui.BACKGROUND);
        box(g, panelX, panelY, panelX + panelW, panelY + panelH, Ui.BORDER_STRONG);
        g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + 25, Ui.SURFACE);
        g.drawString(font, Component.translatable("computer.antos.onboarding.look_title"), contentX, panelY + 8, Ui.TEXT_STRONG, false);
        wrap(g, Component.translatable("computer.antos.onboarding.look_subtitle"), contentX, panelY + 34, panelW - 44, Ui.TEXT);
        String current = AntOSSettings.preferences().displayLook;
        int cardW = (panelW - 56) / 2;
        for (int index = 0; index < LOOKS.length; index++) {
            renderLookCard(g, LOOKS[index], contentX + index * (cardW + 12), panelY + 60, cardW, LOOKS[index].equals(current));
        }
        int actionY = panelY + 192;
        onboardingButton(g, panelX + panelW - 110, actionY, 88, Component.translatable("computer.antos.onboarding.look_continue"),
                hovered(panelX + panelW - 110, actionY, 88, 22));
    }

    private void renderLookCard(GuiGraphics g, String look, int x, int y, int w, boolean selected) {
        Ui.Preview colors = Ui.preview(look);
        g.fill(x, y, x + w, y + 120, hovered(x, y, w, 120) ? Ui.HOVER : Ui.PANEL);
        box(g, x, y, x + w, y + 120, selected ? Ui.ACCENT : Ui.LINE);
        if (selected) box(g, x + 1, y + 1, x + w - 1, y + 119, Ui.ACCENT);
        int mockX = x + 6;
        int mockY = y + 6;
        int mockW = w - 12;
        int mockH = 74;
        g.fill(mockX, mockY, mockX + mockW, mockY + mockH, colors.desktop());
        for (int icon = 0; icon < 3; icon++) {
            g.fill(mockX + 5, mockY + 6 + icon * 14, mockX + 13, mockY + 14 + icon * 14, colors.accent());
        }
        int windowX = mockX + 22;
        int windowY = mockY + 6;
        int windowW = mockW - 28;
        int windowH = mockH - 12;
        g.fill(windowX - 1, windowY - 1, windowX + windowW + 1, windowY + windowH + 1, colors.border());
        g.fill(windowX, windowY, windowX + windowW, windowY + windowH, colors.window());
        g.fill(windowX, windowY, windowX + windowW, windowY + 9, colors.titleBar());
        g.fill(windowX + 4, windowY + 3, windowX + 34, windowY + 6, colors.heading());
        g.fill(windowX + 4, windowY + 15, windowX + windowW - 8, windowY + 18, colors.text());
        g.fill(windowX + 4, windowY + 22, windowX + windowW - 20, windowY + 25, colors.text());
        g.fill(windowX + 4, windowY + 29, windowX + windowW / 2, windowY + 32, colors.muted());
        g.fill(windowX + 4, windowY + 38, windowX + windowW - 4, windowY + 39, colors.line());
        int buttonY = windowY + windowH - 13;
        g.fill(windowX + 4, buttonY, windowX + 38, buttonY + 9, colors.hover());
        box(g, windowX + 4, buttonY, windowX + 38, buttonY + 9, colors.accent());
        g.drawString(font, Component.translatable("computer.antos.onboarding.look_" + look), x + 6, y + 86, selected ? Ui.ACCENT : Ui.TEXT_STRONG, false);
        g.drawString(font, Component.literal(trimToWidth(Component.translatable("computer.antos.onboarding.look_" + look + "_detail").getString(), w - 12)),
                x + 6, y + 100, Ui.TEXT_MUTED, false);
    }

    private boolean lookPickerClicked(double mouseX, double mouseY) {
        int panelX = -WIDTH / 2 + 30;
        int panelY = -HEIGHT / 2 + 43;
        int panelW = WIDTH - 60;
        int cardW = (panelW - 56) / 2;
        for (int index = 0; index < LOOKS.length; index++) {
            if (inside(panelX + 22 + index * (cardW + 12), panelY + 60, cardW, 120, mouseX, mouseY)) {
                chooseLook(LOOKS[index]);
                return true;
            }
        }
        if (inside(panelX + panelW - 110, panelY + 192, 88, 22, mouseX, mouseY)) confirmLook();
        return true;
    }

    private void chooseLook(String look) {
        AntOSUiSounds.play(Cue.CLICK);
        AntOSSettings.updatePreferences(p -> p.displayLook = look);
    }

    private void confirmLook() {
        AntOSUiSounds.play(Cue.CLICK);
        AntOSSettings.updatePreferences(p -> p.lookChosen = true);
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
        if (displays.drag(mouseX, mouseY)) return true;
        if (dragging != null && button == 0) {
            int l = -WIDTH / 2;
            int t = -HEIGHT / 2;
            dragging.x = Math.max(12, Math.min(WIDTH - dragging.renderWidth - 12, (int) mouseX - l - this.dragX));
            dragging.y = Math.max(34, Math.min(HEIGHT - dragging.renderHeight - 12, (int) mouseY - t - this.dragY));
        }
        if (pointerWindow != null && pointerButton == button && session.windows.contains(pointerWindow) && !pointerWindow.minimized) {
            switch (pointerWindow.type) {
                case "TASKS" -> tasks.drag(mouseX, mouseY, button);
                case "MERCHANT" -> session.merchant.drag(mouseY);
                case "ARCHIVE" -> session.archive.drag(mouseY);
                case "GAMES" -> games.drag(mouseY);
                case "SETTINGS" -> session.settings.drag(mouseX, mouseY);
                case "DISKS" -> session.disks.drag(mouseY);
                case "PAINT" -> session.paint.drag(mouseX, mouseY, button);
                case "TEXT" -> session.text.drag(mouseX, mouseY);
                case "FILES" -> session.files.drag(mouseX, mouseY);
                case "TERMINAL" -> session.terminal.drag(mouseX, mouseY);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        displays.release();
        float scale = uiScale();
        mouseX = (mouseX - width / 2.0F) / scale;
        mouseY = (mouseY - height / 2.0F) / scale;
        if (button == 0) dragging = null;
        if (pointerWindow != null && pointerButton == button) {
            switch (pointerWindow.type) {
                case "TASKS" -> tasks.release();
                case "MERCHANT" -> session.merchant.release();
                case "ARCHIVE" -> session.archive.release();
                case "GAMES" -> games.release();
                case "SETTINGS" -> session.settings.release();
                case "DISKS" -> session.disks.release();
                case "PAINT" -> session.paint.release(button);
                case "TEXT" -> session.text.release();
                case "FILES" -> session.files.release(mouseX, mouseY);
                case "TERMINAL" -> session.terminal.release();
            }
            pointerWindow = null;
            pointerButton = -1;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        float scale = uiScale();
        if (loggedIn && session.onboardingCompleted && !lookPickerOpen() && displays.scroll((mouseX - width / 2.0F) / scale, (mouseY - height / 2.0F) / scale, scrollY)) return true;
        Window hoveredWindow = windowAt(mouseX, mouseY);
        if (!loggedIn || hoveredWindow == null) return super.mouseScrolled(mouseX, mouseY, scrollY);
        if (!session.onboardingCompleted || lookPickerOpen()) return true;
        if (!bounds(hoveredWindow).content((mouseX - width / 2.0F) / scale, (mouseY - height / 2.0F) / scale, TITLE_BAR_HEIGHT)) return true;
        switch (hoveredWindow.type) {
            case "FILES" -> session.files.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            case "TASKS" -> tasks.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            case "DISKS" -> session.disks.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            case "GAMES" -> games.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            case "TEXT" -> session.text.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            case "PAINT" -> session.paint.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            case "TERMINAL" -> session.terminal.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            case "ANTMAIL" -> session.antmail.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            case "MERCHANT" -> session.merchant.scroll((mouseX - width / 2.0F) / scale, (mouseY - height / 2.0F) / scale, scrollY);
            case "ANTAZON" -> session.antazon.scroll((mouseX - width / 2.0F) / scale, (mouseY - height / 2.0F) / scale, scrollY);
            case "SETTINGS" -> session.settings.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            case "ARCHIVE" -> session.archive.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            case "WALLPAPERS" -> session.wallpaperPicker.scroll(hoveredWindow, mouseX, mouseY, scrollY);
            default -> { }
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (loggedIn && displays.charTyped(codePoint)) return true;
        if (loggedIn && displays.modal()) return true;
        if (!loggedIn) {
            if (accountHintFocused) {
                if (codePoint >= 32 && accountHint.length() < com.craisinlord.antos.content.computer.ComputerWorkspaceData.MAX_HINT_LENGTH) accountHint += codePoint;
            } else if (accountPasswordFocused) {
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
            case "MERCHANT" -> session.merchant.type(codePoint);
            case "ANTAZON" -> session.antazon.type(codePoint);
            default -> { }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (loggedIn && session.onboardingCompleted && !lookPickerOpen() && displays.key(keyCode)) return true;
        if (!loggedIn) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
            if (keyCode == GLFW.GLFW_KEY_TAB) cycleAccountField();
            else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (accountHintFocused) {
                    if (!accountHint.isEmpty()) accountHint = accountHint.substring(0, accountHint.length() - 1);
                } else if (accountPasswordFocused && !accountPassword.isEmpty()) accountPassword = accountPassword.substring(0, accountPassword.length() - 1);
                else if (!accountPasswordFocused && !accountUsername.isEmpty()) accountUsername = accountUsername.substring(0, accountUsername.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) submitAccount();
            return true;
        }
        if (lookPickerOpen()) {
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
                chooseLook(AntOSSettings.preferences().displayLook.equals("graphite") ? "terminal" : "graphite");
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                confirmLook();
            }
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
        if (!hoveredItem.isEmpty() && !typingInActiveWindow()) {
            Boolean uses = com.craisinlord.antos.content.client.JeiBridge.usesFor(keyCode, scanCode);
            if (uses != null && openInJei(hoveredItem, uses)) return true;
        }
        if (activeWindow == null) {
            int count = renderedDesktopApps().size();
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                if (count == 0) return true;
                desktopFocusIndex = desktopKeyboardFocus ? Math.floorMod(desktopFocusIndex + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1), count) : 0;
                desktopKeyboardFocus = true;
                return true;
            }
            if (desktopKeyboardFocus && count > 0) {
                int next = desktopFocusIndex;
                if (keyCode == GLFW.GLFW_KEY_LEFT) next--;
                else if (keyCode == GLFW.GLFW_KEY_RIGHT) next++;
                else if (keyCode == GLFW.GLFW_KEY_UP) next -= 4;
                else if (keyCode == GLFW.GLFW_KEY_DOWN) next += 4;
                else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) { open(renderedDesktopApps().get(desktopFocusIndex)); return true; }
                else next = desktopFocusIndex;
                if (next != desktopFocusIndex) { desktopFocusIndex = Math.floorMod(next, count); return true; }
            }
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
            case "MERCHANT" -> session.merchant.key(keyCode);
            case "ANTAZON" -> session.antazon.key(keyCode);
            default -> false;
        };
        if (handled) return true;
        if (activeWindow != null) {
            AppKit kit = windowAppKit(activeWindow);
            if (kit != null && keyCode == GLFW.GLFW_KEY_TAB) return kit.focusNext((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            if (kit != null && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE)
                    && kit.activateFocused()) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    int mouseX() {
        return session.mouseX;
    }

    boolean hasControl(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    List<ResourceLocation> workspaceDiskIds() {
        List<ResourceLocation> disks = new ArrayList<>();
        disks.add(new ResourceLocation("antos", "introduction"));
        disks.addAll(com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.activatedDisks());
        return List.copyOf(disks);
    }

    List<ResourceLocation> physicalDisks() {
        return com.craisinlord.antos.content.client.ComputerArchiveUnlockClientState.installedDisks();
    }
    void open(String type) {
        if (!AntOSSettings.appEnabled(type)) return;
        AntOSUiSounds.play(Cue.OPEN);
        if (type.equals("DISKS") && AntOSSettings.appEnabled("ANTAZON")) session.antazon.refreshListings();
        for (int index = 0; index < session.windows.size(); index++) {
            Window window = session.windows.get(index);
            if (!window.type.equals(type)) continue;
            window.minimized = false;
            session.windows.remove(index);
            session.windows.add(window);
            activeWindow = window;
            if (type.equals("FILES")) ComputerNetworking.listFiles();
              else if (type.equals("MERCHANT")) session.merchant.refresh();
              else if (type.equals("ANTAZON")) session.antazon.refocus();
            return;
        }
        Window window = new Window(type, Component.translatable("computer.antos.app." + type.toLowerCase(Locale.ROOT) + ".window_title").getString(), 112 + session.windows.size() * 12, 52 + session.windows.size() * 10);
        session.windows.add(window);
        activeWindow = window;
        windowOpened(type);
    }

    void windowOpened(String type) {
        if (type.equals("ANTMAIL")) session.antmail.requestState();
        else if (type.equals("MERCHANT")) session.merchant.refresh();
        else if (type.equals("ANTAZON")) session.antazon.refresh();
        else if (type.equals("TASKS")) tasks.opened();
        else if (type.equals("FILES")) ComputerNetworking.listFiles();
    }

    private WindowBounds bounds(Window window) {
        return new WindowBounds(-WIDTH / 2 + windowLocalX(window), -HEIGHT / 2 + windowLocalY(window), windowWidth(window), windowHeight(window));
    }

    private Window windowAt(double mouseX, double mouseY) {
        float scale = uiScale();
        return windowAtLocal((mouseX - width / 2.0F) / scale, (mouseY - height / 2.0F) / scale);
    }

    private Window windowAtLocal(double mouseX, double mouseY) {
        return WindowBounds.topmost(session.windows, window -> window.minimized ? null : bounds(window), mouseX, mouseY);
    }

    private void minimizeWindow(Window window) {
        AntOSUiSounds.play(Cue.MINIMIZE);
        window.minimized = true;
        if (activeWindow == window) activeWindow = null;
    }

    private void closeWindow(Window window) {
        AntOSUiSounds.play(Cue.CLOSE);
        if (window.type.equals("ANTMAIL")) session.antmail.windowClosed();
        session.windows.remove(window);
        if (activeWindow == window) activeWindow = null;
    }

    static boolean inside(int x, int y, int w, int h, double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override
    public boolean isPauseScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        return AntOSSettings.preferences().pauseWhileOpen && server != null && !server.isPublished();
    }

    @Override
    public void onClose() {
        AntOSUiSounds.stopPreview();
        AntOSUiSounds.play(Cue.CLOSE);
        ComputerNetworking.close();
        var accountSession = com.craisinlord.antos.content.client.AnternetAccountClientState.latest();
        boolean signedIn = accountSession != null && accountSession.result() == AnternetAccountResultPayload.SUCCESS;
        if (!signedIn || !AntOSSettings.preferences().resumeSession) {
            com.craisinlord.antos.content.network.AnternetAccountNetworking.logout();
            com.craisinlord.antos.content.client.AnternetAccountClientState.clear();
            AntmailClientState.clear();
        }
        net.minecraft.client.gui.screens.Screen parent = com.craisinlord.antos.content.client.ArchiveLookup.takeReturn();
        if (parent != null) Minecraft.getInstance().setScreen(parent);
        else super.onClose();
    }

    @Override
    public void removed() {
        if (loggedIn && AntOSSettings.preferences().resumeSession) {
            AntOSSettings.setResumeSnapshot(sessionKey, ComputerResume.capture(session));
        }
        AntOSUiSounds.stopPreview();
        clearArchivePreviews();
        displays.close();
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
        final MerchantApp merchant = new MerchantApp();
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
        boolean gamesGallery = true;

        void attach(ComputerScreen screen) {
            for (ComputerApp app : List.of(merchant, antazon, terminal, wallpaperPicker, settings, disks, text, paint, files, archive, antmail)) app.attach(screen);
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
            this.height = type.equals("MERCHANT") || type.equals("ANTAZON") || type.equals("SETTINGS") || type.equals("DISKS") || type.equals("GAMES") ? 250 : type.equals("ARCHIVE") ? 230 : 210;
            this.renderHeight = this.height;
            this.width = type.equals("MERCHANT") || type.equals("TASKS") || type.equals("ANTAZON") || type.equals("DISKS") || type.equals("GAMES") || type.equals("SETTINGS") ? 390 : (type.equals("FILES") || type.equals("ARCHIVE")) ? 300 : 230;
            this.renderWidth = this.width;
            this.x = x;
            this.y = y;
            this.restoreX = x;
            this.restoreY = y;
        }
    }
}
