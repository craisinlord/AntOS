package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.craisinlord.antos.content.network.ComputerAccessHandler;
import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ComputerPrograms {
    private static final Map<MinecraftServer, Map<UUID, Session>> RUNS = new WeakHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile long checkedRevision = -1L;

    private ComputerPrograms() { }

    public static boolean available(ComputerWorkspaceData data, UUID profile, ResourceLocation program) {
        DiskProfileState state = data.diskState(profile);
        if (state.programGranted(program)) return true;
        for (ResourceLocation diskId : data.profileDisks(profile)) {
            var disk = ComputerDiskData.disk(diskId);
            if (disk != null && state.activated(diskId) && disk.features().programs().contains(program)) return true;
        }
        return false;
    }

    public static JsonObject handle(ServerPlayer player, ComputerWorkspace workspace, int action, String value) {
        var account = AnternetAccountHandler.session(player);
        if (account == null || workspace == null || !workspace.canUseFileSystem(player)
                || !account.accountId().equals(workspace.workspaceOwner()) || !account.workspaceId().equals(workspace.workspaceId())) return error("unauthorized");
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        long now = player.server.overworld().getGameTime();
        Map<UUID, Session> runs;
        synchronized (RUNS) { runs = RUNS.computeIfAbsent(player.server, ignored -> new HashMap<>()); }
        try {
            if (action == ComputerAccessPayload.PROGRAM_START) {
                ResourceLocation id = ResourceLocation.tryParse(value);
                var definition = id == null ? null : ComputerDiskData.programs().get(id);
                if (definition == null || !available(data, account.accountId(), id)) return error("disk_required");
                List<UUID> expired = new java.util.ArrayList<>();
                runs.entrySet().removeIf(entry -> {
                    if (now - entry.getValue().lastTick <= 36000) return false;
                    expired.add(entry.getKey());
                    return true;
                });
                if (runs.size() >= 512 && !runs.containsKey(account.accountId())) {
                    UUID oldest = runs.entrySet().stream().min(java.util.Comparator.comparingLong(entry -> entry.getValue().lastTick)).orElseThrow().getKey();
                    runs.remove(oldest);
                    expired.add(oldest);
                }
                var run = new ComputerProgramRun(definition, RANDOM);
                Session session = new Session(run, ComputerDiskData.revision(), now);
                runs.put(account.accountId(), session);
                complete(player, workspace, account, session);
                for (UUID expiredAccount : expired) if (!expiredAccount.equals(account.accountId())) push(player.server, expiredAccount, null, error("no_program"));
                push(player.server, account.accountId(), player, run.snapshot());
                return run.snapshot();
            }
            Session session = runs.get(account.accountId());
            if (session == null) return error("no_program");
            if (session.revision != ComputerDiskData.revision()) { runs.remove(account.accountId()); push(player.server, account.accountId(), player, error("program_reloaded")); return error("program_reloaded"); }
            if (!available(data, account.accountId(), session.run.program().id())) { runs.remove(account.accountId()); push(player.server, account.accountId(), player, error("disk_required")); return error("disk_required"); }
            session.lastTick = now;
            if (action == ComputerAccessPayload.PROGRAM_ACTION) {
                JsonObject request = JsonParser.parseString(value).getAsJsonObject();
                if (session.run.token().equals(request.get("run").getAsString())
                        && session.run.choose(request.get("choice").getAsInt(), request.get("sequence").getAsInt())) {
                    complete(player, workspace, account, session);
                    push(player.server, account.accountId(), player, session.run.snapshot());
                }
            }
            return session.run.snapshot();
        } catch (RuntimeException ignored) { return error("invalid_program"); }
    }

    public static void revalidate(MinecraftServer server, UUID account) {
        Map<UUID, Session> runs;
        synchronized (RUNS) { runs = RUNS.get(server); }
        Session session = runs == null ? null : runs.get(account);
        if (session == null) return;
        String status = session.revision != ComputerDiskData.revision() ? "program_reloaded"
                : available(ComputerWorkspaceData.access(server), account, session.run.program().id()) ? null : "disk_required";
        if (status == null) return;
        runs.remove(account);
        push(server, account, null, error(status));
    }

    public static void tick(MinecraftServer server) {
        long revision = ComputerDiskData.revision();
        if (revision == checkedRevision) return;
        checkedRevision = revision;
        Map<UUID, Session> runs;
        synchronized (RUNS) { runs = RUNS.get(server); }
        if (runs == null) return;
        for (UUID account : List.copyOf(runs.keySet())) revalidate(server, account);
    }

    private static void push(MinecraftServer server, UUID account, ServerPlayer except, JsonObject state) {
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (target == except) continue;
            var session = AnternetAccountHandler.session(target);
            if (session != null && session.accountId().equals(account)) ComputerAccessHandler.sendProgramState(target, session.workspaceId(), state.deepCopy());
        }
    }

    private static void complete(ServerPlayer player, ComputerWorkspace workspace, ComputerWorkspaceData.AccountInfo account, Session session) {
        if (!session.run.finished() || session.completed) return;
        session.completed = true;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        DiskProfileState state = data.diskState(account.accountId());
        var program = session.run.program();
        List<ComputerGuideData.TaskReward> rewards = List.of();
        if (program.type().equals("quiz")) {
            if (session.run.quizRewardEligible() && !program.rewards().isEmpty()
                    && state.claimQuiz(program.id(), program.rewardOnce() ? 0L : player.server.overworld().getDayTime() / 24000L)) rewards = program.rewards();
        } else {
            var node = session.run.currentNode();
            if (!node.ending().isBlank() && state.recordEnding(program.id(), node.ending())) rewards = node.rewards();
        }
        data.setDirty();
        if (!rewards.isEmpty()) {
            var receipt = ComputerGuideData.encodeRewards(ComputerTasks.deliverRewards(player, program.id(), rewards, Map.of()));
            for (var element : receipt) if (element.getAsJsonObject().has("body")) element.getAsJsonObject().addProperty("body", "");
            if (!receipt.isEmpty()) ComputerAccessHandler.sendRewardNotice(player, account, program.id(), program.title(), receipt);
        }
        ComputerAccessHandler.refreshAfterDisk(player, workspace);
    }

    private static JsonObject error(String status) { JsonObject object = new JsonObject(); object.addProperty("status", status); return object; }

    private static final class Session {
        final ComputerProgramRun run;
        final long revision;
        long lastTick;
        boolean completed;
        Session(ComputerProgramRun run, long revision, long lastTick) { this.run = run; this.revision = revision; this.lastTick = lastTick; }
    }
}
