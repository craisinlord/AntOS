package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class AnternetAccountHandler {
    private static final long AUTH_ATTEMPT_COOLDOWN_NANOS = 750_000_000L;
    private static final Map<UUID, ComputerWorkspaceData.AccountInfo> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, ComputerBlockEntity> PENDING_DEVICES = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingInsertion> PENDING_INSERTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, java.util.Set<ComputerBlockEntity>> BOUND_DEVICES = new ConcurrentHashMap<>();
    private static final Map<UUID, LoginThrottle> LOGIN_THROTTLES = new ConcurrentHashMap<>();
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
        if (session(player) != null || !computer.requiresAccountSession() || !computer.playerCanReach(player)
                || player.getItemInHand(hand) != stack || !stack.is(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK.get())
                || stack.get(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK_COMPONENT.get()) == null) return;
        ResourceLocation diskId = stack.get(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        PENDING_DEVICES.put(player.getUUID(), computer);
        PENDING_INSERTIONS.put(player.getUUID(), new PendingInsertion(computer, hand, diskId));
        resultSender.accept(player, new AnternetAccountResultPayload(AnternetAccountResultPayload.LOGIN_REQUIRED,
                AnternetAccountResultPayload.EMPTY_ID, AnternetAccountResultPayload.EMPTY_ID, ""));
    }

    public static void handle(ServerPlayer player, AnternetAccountPayload payload) {
        if (player.level().isClientSide || payload.username().length() > 32 || payload.password().length() > 128) {
            send(player, AnternetAccountResultPayload.INVALID_REQUEST);
            return;
        }
        if (payload.action() == AnternetAccountPayload.LOGOUT) {
            clearSession(player);
            PENDING_DEVICES.remove(player.getUUID());
            PENDING_INSERTIONS.remove(player.getUUID());
            resultSender.accept(player, new AnternetAccountResultPayload(AnternetAccountResultPayload.LOGGED_OUT,
                    AnternetAccountResultPayload.EMPTY_ID, AnternetAccountResultPayload.EMPTY_ID, ""));
            return;
        }
        MinecraftServer server = player.server;
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
        if (throttled(player.getUUID())) { send(player, AnternetAccountResultPayload.RATE_LIMITED); return; }
        if (payload.action() == AnternetAccountPayload.CHANGE_PASSWORD) {
            ComputerWorkspaceData.AccountInfo current = session(player);
            if (current == null) { send(player, AnternetAccountResultPayload.INVALID_CREDENTIALS); return; }
            ComputerWorkspaceData.AccountResult changed = data.changePassword(current.accountId(), payload.password());
            if (!changed.successful()) {
                recordFailure(player.getUUID());
                send(player, AnternetAccountResultPayload.INVALID_REQUEST);
                return;
            }
            recordSuccess(player.getUUID());
            revokeOtherSessions(server, current.accountId(), player.getUUID());
            resultSender.accept(player, new AnternetAccountResultPayload(AnternetAccountResultPayload.SUCCESS,
                    current.accountId(), current.workspaceId(), current.displayUsername()));
            return;
        }
        ComputerWorkspaceData.AccountResult result = payload.action() == AnternetAccountPayload.FACE_ID_LOGIN
                ? faceIdLogin(data, player)
                : payload.action() == AnternetAccountPayload.CREATE
                    ? data.createAccount(payload.username(), payload.password())
                    : data.authenticate(payload.username(), payload.password());
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
        if (pendingDevice != null && pendingDevice.authenticateAccount(player, account)) {
            BOUND_DEVICES.computeIfAbsent(player.getUUID(), ignored -> ConcurrentHashMap.newKeySet()).add(pendingDevice);
            PendingInsertion insertion = PENDING_INSERTIONS.remove(player.getUUID());
            if (insertion != null && insertion.computer() == pendingDevice) {
                ItemStack held = player.getItemInHand(insertion.hand());
                ResourceLocation heldId = held.get(com.craisinlord.antos.content.AntOSObjects.FLOPPY_DISK_COMPONENT.get());
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
        resultSender.accept(player, new AnternetAccountResultPayload(AnternetAccountResultPayload.SUCCESS,
                account.accountId(), account.workspaceId(), account.displayUsername()));
    }

    public static void disconnect(ServerPlayer player) {
        clearSession(player);
        PENDING_DEVICES.remove(player.getUUID());
        PENDING_INSERTIONS.remove(player.getUUID());
        LOGIN_THROTTLES.remove(player.getUUID());
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
    private record PendingInsertion(ComputerBlockEntity computer, InteractionHand hand, ResourceLocation diskId) { }
}
