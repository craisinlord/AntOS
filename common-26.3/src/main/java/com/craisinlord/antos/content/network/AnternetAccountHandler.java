package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class AnternetAccountHandler {
    private static final long AUTH_ATTEMPT_COOLDOWN_NANOS = 750_000_000L;
    private static final Map<UUID, ComputerWorkspaceData.AccountInfo> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, ComputerBlockEntity> PENDING_DEVICES = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingInsertion> PENDING_INSERTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, java.util.Set<ComputerBlockEntity>> BOUND_DEVICES = new ConcurrentHashMap<>();
    private static final Map<UUID, LoginThrottle> LOGIN_THROTTLES = new ConcurrentHashMap<>();
    private static final Map<UUID, Object> PENDING_AUTH = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrayDeque<Long>> ACCOUNT_CREATIONS = new ConcurrentHashMap<>();
    private static final int MAX_ACCOUNT_CREATIONS = 3;
    private static final long ACCOUNT_CREATION_WINDOW_NANOS = 600_000_000_000L;
    private static ThreadPoolExecutor authExecutor;
    private static BiConsumer<ServerPlayer, AnternetAccountResultPayload> resultSender = (player, result) -> { };

    private AnternetAccountHandler() { }

    public static void setResultSender(BiConsumer<ServerPlayer, AnternetAccountResultPayload> sender) {
        resultSender = sender;
    }

    public static ComputerWorkspaceData.AccountInfo session(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    public static void requestDeviceBinding(ServerPlayer player, ComputerBlockEntity computer) {
        ComputerWorkspaceData.AccountInfo account = session(player);
        if (account == null) PENDING_DEVICES.put(player.getUUID(), computer);
        else if (computer.authenticateAccount(player, account)) {
            BOUND_DEVICES.computeIfAbsent(player.getUUID(), ignored -> ConcurrentHashMap.newKeySet()).add(computer);
        }
    }

    public static void requestDiskInsertion(ServerPlayer player, ComputerBlockEntity computer, InteractionHand hand, ItemStack stack) {
        if (session(player) != null || !computer.playerCanReach(player)
                || player.getItemInHand(hand) != stack || !stack.is(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK.get())
                || stack.get(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK_COMPONENT.get()) == null) return;
        Identifier diskId = stack.get(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        PENDING_DEVICES.put(player.getUUID(), computer);
        PENDING_INSERTIONS.put(player.getUUID(), new PendingInsertion(computer, hand, diskId));
        resultSender.accept(player, new AnternetAccountResultPayload(AnternetAccountResultPayload.LOGIN_REQUIRED,
                AnternetAccountResultPayload.EMPTY_ID, AnternetAccountResultPayload.EMPTY_ID, ""));
    }

    public static void handle(ServerPlayer player, AnternetAccountPayload payload) {
        int usernameLimit = payload.action() == AnternetAccountPayload.CHANGE_PASSWORD ? 128 : 32;
        if (player.level().isClientSide() || payload.username().length() > usernameLimit || payload.password().length() > 128) {
            send(player, payload.action() == AnternetAccountPayload.CHANGE_PASSWORD
                    ? AnternetAccountResultPayload.PASSWORD_CHANGE_INVALID : AnternetAccountResultPayload.INVALID_REQUEST);
            return;
        }
        if (payload.action() == AnternetAccountPayload.LOGOUT) {
            PENDING_AUTH.remove(player.getUUID());
            clearSession(player);
            PENDING_DEVICES.remove(player.getUUID());
            PENDING_INSERTIONS.remove(player.getUUID());
            resultSender.accept(player, new AnternetAccountResultPayload(AnternetAccountResultPayload.LOGGED_OUT,
                    AnternetAccountResultPayload.EMPTY_ID, AnternetAccountResultPayload.EMPTY_ID, ""));
            return;
        }
        MinecraftServer server = player.level().getServer();
        ComputerWorkspaceData data = ComputerWorkspaceData.access(server);
        if (payload.action() == AnternetAccountPayload.FACE_ID_STATUS) {
            ComputerWorkspaceData.AccountInfo linked = data.faceIdAccount(player.getUUID());
            resultSender.accept(player, new AnternetAccountResultPayload(AnternetAccountResultPayload.FACE_ID_STATUS,
                    linked == null ? AnternetAccountResultPayload.EMPTY_ID : linked.accountId(),
                    linked == null ? AnternetAccountResultPayload.EMPTY_ID : linked.workspaceId(),
                    linked == null ? "" : linked.displayUsername()));
            return;
        }
        if (payload.action() == AnternetAccountPayload.FACE_ID_UNLINK) {
            if (throttled(player.getUUID())) { sendFaceIdResult(player, AnternetAccountResultPayload.FACE_ID_RATE_LIMITED, ""); return; }
            ComputerWorkspaceData.AccountInfo current = session(player);
            if (current == null) { send(player, AnternetAccountResultPayload.INVALID_CREDENTIALS); return; }
            data.unlinkFaceId(player.getUUID(), current.accountId());
            recordSuccess(player.getUUID());
            sendFaceIdStatus(player, data, null);
            return;
        }
        if (payload.action() == AnternetAccountPayload.FACE_ID_LINK) {
            if (throttled(player.getUUID())) { sendFaceIdResult(player, AnternetAccountResultPayload.FACE_ID_RATE_LIMITED, ""); return; }
            ComputerWorkspaceData.AccountInfo current = session(player);
            if (current == null) {
                send(player, AnternetAccountResultPayload.INVALID_CREDENTIALS);
                return;
            }
            if (!data.linkFaceId(player.getUUID(), current.accountId())) {
                recordSuccess(player.getUUID());
                sendFaceIdResult(player, AnternetAccountResultPayload.FACE_ID_CONFLICT, "");
                return;
            }
            recordSuccess(player.getUUID());
            sendFaceIdStatus(player, data, current);
            return;
        }
        if (payload.action() != AnternetAccountPayload.CREATE && payload.action() != AnternetAccountPayload.LOGIN
                && payload.action() != AnternetAccountPayload.CHANGE_PASSWORD
                && payload.action() != AnternetAccountPayload.FACE_ID_LOGIN) {
            send(player, AnternetAccountResultPayload.INVALID_REQUEST);
            return;
        }
        if (payload.action() == AnternetAccountPayload.CHANGE_PASSWORD) {
            if (throttled(player.getUUID()) || PENDING_AUTH.containsKey(player.getUUID())) {
                send(player, AnternetAccountResultPayload.PASSWORD_CHANGE_RATE_LIMITED);
                return;
            }
            ComputerWorkspaceData.AccountInfo current = session(player);
            if (current == null) { send(player, AnternetAccountResultPayload.PASSWORD_CHANGE_DENIED); return; }
            ComputerWorkspaceData.CredentialCheck check = data.prepareAuthentication(current.username());
            String currentPassword = payload.username();
            String newPassword = payload.password();
            boolean submitted = submitAuthJob(player, () -> ComputerWorkspaceData.derivePasswordChange(check, currentPassword, newPassword),
                    (target, change) -> finishPasswordChange(target, current, check, change));
            if (!submitted) send(player, AnternetAccountResultPayload.PASSWORD_CHANGE_RATE_LIMITED);
            return;
        }
        if (throttled(player.getUUID()) || PENDING_AUTH.containsKey(player.getUUID())) { send(player, AnternetAccountResultPayload.RATE_LIMITED); return; }
        if (payload.action() == AnternetAccountPayload.FACE_ID_LOGIN) {
            finishLogin(player, faceIdLogin(data, player));
            return;
        }
        String username = payload.username();
        String password = payload.password();
        boolean submitted;
        if (payload.action() == AnternetAccountPayload.CREATE) {
            if (creationLimited(player.getUUID())) { send(player, AnternetAccountResultPayload.RATE_LIMITED); return; }
            ComputerWorkspaceData.AccountFailure failure = data.validateNewAccount(username, password);
            if (failure != null) {
                finishLogin(player, ComputerWorkspaceData.AccountResult.failure(failure));
                return;
            }
            submitted = submitAuthJob(player, () -> ComputerWorkspaceData.hashPassword(password), (target, hash) -> {
                ComputerWorkspaceData.AccountResult result = ComputerWorkspaceData.access(target.level().getServer()).completeAccountCreation(username, hash);
                if (result.successful()) recordCreation(target.getUUID());
                finishLogin(target, result);
            });
        } else {
            ComputerWorkspaceData.CredentialCheck check = data.prepareAuthentication(username);
            submitted = submitAuthJob(player, () -> ComputerWorkspaceData.verifyCredentials(check, password),
                    (target, valid) -> finishLogin(target, ComputerWorkspaceData.access(target.level().getServer()).completeAuthentication(check, Boolean.TRUE.equals(valid))));
        }
        if (!submitted) send(player, AnternetAccountResultPayload.RATE_LIMITED);
    }

    private static void finishPasswordChange(ServerPlayer player, ComputerWorkspaceData.AccountInfo current,
                                             ComputerWorkspaceData.CredentialCheck check, ComputerWorkspaceData.PasswordChange change) {
        ComputerWorkspaceData.AccountInfo session = session(player);
        if (session == null || !session.accountId().equals(current.accountId())) {
            send(player, AnternetAccountResultPayload.PASSWORD_CHANGE_DENIED);
            return;
        }
        if (change == null || !change.verified() || !current.accountId().equals(check.accountId())) {
            recordFailure(player.getUUID());
            send(player, AnternetAccountResultPayload.PASSWORD_CHANGE_DENIED);
            return;
        }
        if (!change.validPassword()) {
            send(player, AnternetAccountResultPayload.PASSWORD_CHANGE_INVALID);
            return;
        }
        ComputerWorkspaceData.AccountResult changed = ComputerWorkspaceData.access(player.level().getServer())
                .completePasswordChange(current.accountId(), check.verifier(), change.hash());
        if (!changed.successful()) {
            send(player, AnternetAccountResultPayload.PASSWORD_CHANGE_INVALID);
            return;
        }
        recordSuccess(player.getUUID());
        revokeOtherSessions(player.level().getServer(), current.accountId(), player.getUUID());
        send(player, AnternetAccountResultPayload.PASSWORD_CHANGED);
    }

    private static void finishLogin(ServerPlayer player, ComputerWorkspaceData.AccountResult result) {
        MinecraftServer server = player.level().getServer();
        if (!result.successful()) {
            recordFailure(player.getUUID());
            int error = switch (result.failure()) {
                case USERNAME_TAKEN -> AnternetAccountResultPayload.USERNAME_TAKEN;
                case INVALID_CREDENTIALS -> AnternetAccountResultPayload.INVALID_CREDENTIALS;
                default -> AnternetAccountResultPayload.INVALID_REQUEST;
            };
            send(player, error);
            return;
        }
        ComputerWorkspaceData.AccountInfo account = result.account();
        recordSuccess(player.getUUID());
        clearSession(player);
        SESSIONS.put(player.getUUID(), account);
        ComputerBlockEntity pendingDevice = PENDING_DEVICES.remove(player.getUUID());
        if (pendingDevice != null && !pendingDevice.isRemoved() && pendingDevice.authenticateAccount(player, account)) {
            BOUND_DEVICES.computeIfAbsent(player.getUUID(), ignored -> ConcurrentHashMap.newKeySet()).add(pendingDevice);
            PendingInsertion insertion = PENDING_INSERTIONS.remove(player.getUUID());
            if (insertion != null && insertion.computer() == pendingDevice) {
                ItemStack held = player.getItemInHand(insertion.hand());
                Identifier heldId = held.get(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK_COMPONENT.get());
                var deviceLevel = pendingDevice.getLevel();
                if (deviceLevel != null && player.level() == deviceLevel && deviceLevel.hasChunkAt(pendingDevice.getBlockPos())
                        && deviceLevel.getBlockEntity(pendingDevice.getBlockPos()) == pendingDevice
                        && held.is(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK.get())
                        && insertion.diskId().equals(heldId)) {
                    pendingDevice.insert(held, player);
                }
            }
        } else {
            PENDING_INSERTIONS.remove(player.getUUID());
        }
        com.craisinlord.antos.content.antazon.AntazonServerData.access(server).migratePlayerToProfile(player.getUUID(), account.accountId());
        com.craisinlord.antos.content.item.AntroidPhoneItem.syncPendingDisks(player, account);
        com.craisinlord.antos.content.computer.ComputerDisks.reconcileRewards(player, account);
        com.craisinlord.antos.compat.ftbquests.AntOSFTBBridge.deliverPending(player, account);
        com.craisinlord.antos.content.antmail.AntmailEventData.onSession(player, account);
        com.craisinlord.antos.content.computer.ComputerAccountLoginEvents.fire(player, account);
        resultSender.accept(player, new AnternetAccountResultPayload(AnternetAccountResultPayload.SUCCESS,
                account.accountId(), account.workspaceId(), account.displayUsername()));
    }

    private static <T> boolean submitAuthJob(ServerPlayer player, Supplier<T> work, BiConsumer<ServerPlayer, T> apply) {
        UUID playerId = player.getUUID();
        MinecraftServer server = player.level().getServer();
        Object token = new Object();
        if (PENDING_AUTH.putIfAbsent(playerId, token) != null) return false;
        try {
            authExecutor().execute(() -> {
                T result;
                try {
                    result = work.get();
                } catch (RuntimeException exception) {
                    result = null;
                }
                T outcome = result;
                server.execute(() -> {
                    if (!server.isSameThread() || !PENDING_AUTH.remove(playerId, token)) return;
                    ServerPlayer target = server.getPlayerList().getPlayer(playerId);
                    if (target == null || target.hasDisconnected()) return;
                    apply.accept(target, outcome);
                });
            });
            return true;
        } catch (RejectedExecutionException exception) {
            PENDING_AUTH.remove(playerId, token);
            return false;
        }
    }

    private static synchronized ThreadPoolExecutor authExecutor() {
        if (authExecutor == null || authExecutor.isShutdown()) {
            AtomicInteger threads = new AtomicInteger();
            ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "AntOS-Auth-" + threads.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
            executor.allowCoreThreadTimeOut(true);
            authExecutor = executor;
        }
        return authExecutor;
    }

    private static boolean creationLimited(UUID playerId) {
        ArrayDeque<Long> creations = ACCOUNT_CREATIONS.get(playerId);
        if (creations == null) return false;
        long now = System.nanoTime();
        while (!creations.isEmpty() && now - creations.peekFirst() > ACCOUNT_CREATION_WINDOW_NANOS) creations.pollFirst();
        if (creations.isEmpty()) {
            ACCOUNT_CREATIONS.remove(playerId, creations);
            return false;
        }
        return creations.size() >= MAX_ACCOUNT_CREATIONS;
    }

    private static void recordCreation(UUID playerId) {
        ACCOUNT_CREATIONS.computeIfAbsent(playerId, ignored -> new ArrayDeque<>()).addLast(System.nanoTime());
    }

    public static void forgetDevice(ComputerBlockEntity computer) {
        if (computer == null) return;
        PENDING_DEVICES.values().removeIf(device -> device == computer);
        PENDING_INSERTIONS.values().removeIf(insertion -> insertion.computer() == computer);
        BOUND_DEVICES.values().forEach(devices -> devices.remove(computer));
        BOUND_DEVICES.values().removeIf(java.util.Set::isEmpty);
    }

    public static void serverStopped() {
        com.craisinlord.antos.content.computer.ComputerTasks.serverStopped();
        synchronized (AnternetAccountHandler.class) {
            if (authExecutor != null) authExecutor.shutdownNow();
            authExecutor = null;
        }
        PENDING_AUTH.clear();
        SESSIONS.clear();
        PENDING_DEVICES.clear();
        PENDING_INSERTIONS.clear();
        BOUND_DEVICES.clear();
        LOGIN_THROTTLES.clear();
        ACCOUNT_CREATIONS.clear();
    }

    public static void disconnect(ServerPlayer player) {
        PENDING_AUTH.remove(player.getUUID());
        clearSession(player);
        PENDING_DEVICES.remove(player.getUUID());
        PENDING_INSERTIONS.remove(player.getUUID());
        LOGIN_THROTTLES.remove(player.getUUID());
        ComputerAccessHandler.playerDisconnected(player);
        ArchiveRecipeNetworking.playerDisconnected(player);
    }

    private static void clearSession(ServerPlayer player) {
        UUID playerId = player.getUUID();
        SESSIONS.remove(playerId);
        java.util.Set<ComputerBlockEntity> devices = BOUND_DEVICES.remove(playerId);
        if (devices != null) devices.forEach(device -> device.releaseUser(player));
    }

    private static void revokeOtherSessions(MinecraftServer server, UUID accountId, UUID exceptPlayerId) {
        for (Map.Entry<UUID, ComputerWorkspaceData.AccountInfo> entry : SESSIONS.entrySet()) {
            if (!entry.getValue().accountId().equals(accountId) || entry.getKey().equals(exceptPlayerId)) continue;
            ServerPlayer other = server.getPlayerList().getPlayer(entry.getKey());
            if (other == null) SESSIONS.remove(entry.getKey(), entry.getValue());
            else {
                clearSession(other);
                resultSender.accept(other, new AnternetAccountResultPayload(AnternetAccountResultPayload.LOGGED_OUT,
                        AnternetAccountResultPayload.EMPTY_ID, AnternetAccountResultPayload.EMPTY_ID, ""));
            }
        }
    }

    private static void send(ServerPlayer player, int result) {
        resultSender.accept(player, new AnternetAccountResultPayload(result, AnternetAccountResultPayload.EMPTY_ID,
                AnternetAccountResultPayload.EMPTY_ID, ""));
    }

    private static ComputerWorkspaceData.AccountResult faceIdLogin(ComputerWorkspaceData data, ServerPlayer player) {
        ComputerWorkspaceData.AccountInfo account = data.faceIdAccount(player.getUUID());
        return account == null
                ? ComputerWorkspaceData.AccountResult.failure(ComputerWorkspaceData.AccountFailure.INVALID_CREDENTIALS)
                : ComputerWorkspaceData.AccountResult.success(account);
    }

    private static void sendFaceIdStatus(ServerPlayer player, ComputerWorkspaceData data,
                                         ComputerWorkspaceData.AccountInfo linked) {
        if (linked == null) linked = data.faceIdAccount(player.getUUID());
        resultSender.accept(player, new AnternetAccountResultPayload(AnternetAccountResultPayload.FACE_ID_STATUS,
                linked == null ? AnternetAccountResultPayload.EMPTY_ID : linked.accountId(),
                linked == null ? AnternetAccountResultPayload.EMPTY_ID : linked.workspaceId(),
                linked == null ? "" : linked.displayUsername()));
    }

    private static void sendFaceIdResult(ServerPlayer player, int result, String detail) {
        resultSender.accept(player, new AnternetAccountResultPayload(result, AnternetAccountResultPayload.EMPTY_ID,
                AnternetAccountResultPayload.EMPTY_ID, detail));
    }

    private static boolean throttled(UUID playerId) {
        LoginThrottle throttle = LOGIN_THROTTLES.get(playerId);
        if (throttle == null) return false;
        long now = System.nanoTime();
        return throttle.failures >= 5 && now < throttle.blockedUntil
                || now - throttle.lastAttempt < AUTH_ATTEMPT_COOLDOWN_NANOS;
    }

    private static void recordFailure(UUID playerId) {
        long now = System.nanoTime();
        LOGIN_THROTTLES.compute(playerId, (id, current) -> {
            LoginThrottle state = current == null || now - current.windowStart > 60_000_000_000L
                    ? new LoginThrottle(now, 0, 0L, now) : current;
            int failures = state.failures + 1;
            long blockedUntil = failures >= 5 ? now + 30_000_000_000L : 0L;
            return new LoginThrottle(state.windowStart, failures, blockedUntil, now);
        });
    }

    private static void recordSuccess(UUID playerId) {
        long now = System.nanoTime();
        LOGIN_THROTTLES.put(playerId, new LoginThrottle(now, 0, 0L, now));
    }

    private record LoginThrottle(long windowStart, int failures, long blockedUntil, long lastAttempt) { }
    private record PendingInsertion(ComputerBlockEntity computer, InteractionHand hand, Identifier diskId) { }
}
