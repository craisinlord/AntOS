package com.craisinlord.antos.content.computer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class ComputerWorkspaceData extends SavedData {
    private static final String DATA_ID = "antos_computer_workspaces";
    private static final String WORKSPACES_TAG = "Workspaces";
    private static final String OWNERS_TAG = "Owners";
    private static final String ACCOUNTS_TAG = "Accounts";
    private static final String SNAPSHOTS_TAG = "Snapshots";
    private static final String FACE_ID_LINKS_TAG = "FaceIdLinks";
    private static final String TEAMS_TAG = "Teams";
    private static final String TEAM_INVITES_TAG = "TeamInvites";
    private static final String CLAIMED_REWARDS_TAG = "ClaimedTaskRewards";
    private static final String PENDING_ANTOS_GRANTS_TAG = "PendingAntOSGrants";
    private static final int ACCOUNT_SCHEMA = 1;
    private static final int PASSWORD_ITERATIONS = 160_000;
    private static final int PASSWORD_SALT_BYTES = 16;
    private static final int PASSWORD_HASH_BYTES = 32;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Map<UUID, CompoundTag> workspaces = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeWorkspaceByOwner = new LinkedHashMap<>();
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();
    private final Map<String, UUID> accountIdByUsername = new LinkedHashMap<>();
    private final Map<UUID, CompoundTag> workspaceSnapshots = new LinkedHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> profileDisks = new LinkedHashMap<>();
    private final Map<UUID, UUID> faceIdAccountByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, UUID> teamByAccount = new LinkedHashMap<>();
    private final Map<UUID, TeamInvite> teamInvites = new LinkedHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> claimedTaskRewards = new LinkedHashMap<>();
    private final Map<UUID, Set<PendingAntOSGrant>> pendingAntOSGrants = new LinkedHashMap<>();

    public static ComputerWorkspaceData access(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ComputerWorkspaceData::new, ComputerWorkspaceData::load, null), DATA_ID);
    }

    private static ComputerWorkspaceData load(CompoundTag tag, HolderLookup.Provider registries) {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        ListTag workspaces = tag.getList(WORKSPACES_TAG, 10);
        for (int index = 0; index < workspaces.size(); index++) {
            CompoundTag row = workspaces.getCompound(index);
            try {
                UUID workspaceId = UUID.fromString(row.getString("Id"));
                data.workspaces.put(workspaceId, row.getCompound("Progress").copy());
            } catch (RuntimeException ignored) { }
        }
        ListTag owners = tag.getList(OWNERS_TAG, 10);
        for (int index = 0; index < owners.size(); index++) {
            CompoundTag row = owners.getCompound(index);
            try {
                data.activeWorkspaceByOwner.put(UUID.fromString(row.getString("Owner")), UUID.fromString(row.getString("Workspace")));
            } catch (RuntimeException ignored) { }
        }
        ListTag accounts = tag.getList(ACCOUNTS_TAG, 10);
        for (int index = 0; index < accounts.size(); index++) {
            CompoundTag row = accounts.getCompound(index);
            try {
                UUID accountId = UUID.fromString(row.getString("Id"));
                UUID workspaceId = UUID.fromString(row.getString("Workspace"));
                String username = normalizeUsername(row.getString("Username"));
                String displayUsername = row.getString("DisplayUsername");
                String salt = row.getString("Salt");
                String verifier = row.getString("Verifier");
                int iterations = row.getInt("Iterations");
                if (row.getInt("Schema") == ACCOUNT_SCHEMA && validUsername(username) && !data.accountIdByUsername.containsKey(username)
                        && !salt.isBlank() && !verifier.isBlank() && iterations >= 100_000 && iterations <= 1_000_000) {
                    Account account = new Account(accountId, username,
                            displayUsername.isBlank() ? username : displayUsername, workspaceId,
                            salt, verifier, iterations);
                    data.accounts.put(accountId, account);
                    data.accountIdByUsername.put(username, accountId);
                    data.workspaceSnapshots.putIfAbsent(workspaceId, new CompoundTag());
                    Set<ResourceLocation> disks = new LinkedHashSet<>();
                    ListTag diskTags = row.getList("Disks", 8);
                    for (int diskIndex = 0; diskIndex < diskTags.size(); diskIndex++) {
                        try { disks.add(ResourceLocation.parse(diskTags.getString(diskIndex))); }
                        catch (RuntimeException ignored) { }
                    }
                    data.profileDisks.put(accountId, disks);
                }
            } catch (RuntimeException ignored) { }
        }
        ListTag snapshots = tag.getList(SNAPSHOTS_TAG, 10);
        for (int index = 0; index < snapshots.size(); index++) {
            CompoundTag row = snapshots.getCompound(index);
            try {
                UUID workspaceId = UUID.fromString(row.getString("Workspace"));
                if (data.isAccountWorkspace(workspaceId)) {
                    data.workspaceSnapshots.put(workspaceId, row.getCompound("Data").copy());
                }
            } catch (RuntimeException ignored) { }
        }
        ListTag faceIdLinks = tag.getList(FACE_ID_LINKS_TAG, 10);
        for (int index = 0; index < faceIdLinks.size(); index++) {
            CompoundTag row = faceIdLinks.getCompound(index);
            try {
                UUID playerId = UUID.fromString(row.getString("Player"));
                UUID accountId = UUID.fromString(row.getString("Account"));
                if (data.accounts.containsKey(accountId) && !data.faceIdAccountByPlayer.containsKey(playerId)
                        && data.faceIdAccountByPlayer.values().stream().noneMatch(accountId::equals)) {
                    data.faceIdAccountByPlayer.put(playerId, accountId);
                }
            } catch (RuntimeException ignored) { }
        }
        ListTag teamRows = tag.getList(TEAMS_TAG, 10);
        for (int index = 0; index < teamRows.size(); index++) {
            CompoundTag row = teamRows.getCompound(index);
            try {
                UUID teamId = UUID.fromString(row.getString("Id"));
                UUID ownerId = UUID.fromString(row.getString("Owner"));
                if (!data.accounts.containsKey(ownerId) || data.teamByAccount.containsKey(ownerId) || data.teams.containsKey(teamId)) continue;
                Team team = new Team(teamId, ownerId);
                ListTag members = row.getList("Members", 8);
                for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                    try {
                        UUID memberId = UUID.fromString(members.getString(memberIndex));
                        if (data.accounts.containsKey(memberId) && !data.teamByAccount.containsKey(memberId)) {
                            team.members.add(memberId);
                            data.teamByAccount.put(memberId, teamId);
                        }
                    } catch (RuntimeException ignored) { }
                }
                if (!team.members.contains(ownerId)) team.members.add(ownerId);
                team.progress.load(row.getCompound("Progress"));
                data.teams.put(teamId, team);
                data.teamByAccount.put(ownerId, teamId);
            } catch (RuntimeException ignored) { }
        }
        ListTag inviteRows = tag.getList(TEAM_INVITES_TAG, 10);
        for (int index = 0; index < inviteRows.size(); index++) {
            CompoundTag row = inviteRows.getCompound(index);
            try {
                TeamInvite invite = new TeamInvite(UUID.fromString(row.getString("Id")),
                        UUID.fromString(row.getString("Team")), UUID.fromString(row.getString("Recipient")),
                        UUID.fromString(row.getString("Inviter")), row.getLong("Created"));
                if (data.teams.containsKey(invite.teamId()) && data.accounts.containsKey(invite.recipientId())
                        && data.accounts.containsKey(invite.inviterId()) && !data.teamByAccount.containsKey(invite.recipientId())) {
                    data.teamInvites.putIfAbsent(invite.id(), invite);
                }
            } catch (RuntimeException ignored) { }
        }
        ListTag claimedRows = tag.getList(CLAIMED_REWARDS_TAG, 10);
        for (int index = 0; index < claimedRows.size(); index++) {
            CompoundTag row = claimedRows.getCompound(index);
            try {
                UUID accountId = UUID.fromString(row.getString("Account"));
                if (!data.accounts.containsKey(accountId)) continue;
                Set<ResourceLocation> claimed = new LinkedHashSet<>();
                ListTag ids = row.getList("Tasks", 8);
                for (int idIndex = 0; idIndex < ids.size(); idIndex++) {
                    try { claimed.add(ResourceLocation.parse(ids.getString(idIndex))); }
                    catch (RuntimeException ignored) { }
                }
                data.claimedTaskRewards.put(accountId, claimed);
            } catch (RuntimeException ignored) { }
        }
        ListTag pendingRows = tag.getList(PENDING_ANTOS_GRANTS_TAG, 10);
        for (int index = 0; index < pendingRows.size(); index++) {
            CompoundTag row = pendingRows.getCompound(index);
            try {
                UUID playerId = UUID.fromString(row.getString("Player"));
                String action = row.getString("Action");
                ResourceLocation target = ResourceLocation.parse(row.getString("Target"));
                if ((action.equals("unlock_archive") || action.equals("grant_task")) && target.toString().length() <= 256) {
                    data.pendingAntOSGrants.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>())
                            .add(new PendingAntOSGrant(action, target));
                }
            } catch (RuntimeException ignored) { }
        }
        return data;
    }

    public synchronized AccountResult createAccount(String requestedUsername, String password) {
        String username = normalizeUsername(requestedUsername);
        if (!validUsername(username)) return AccountResult.failure(AccountFailure.INVALID_USERNAME);
        if (accountIdByUsername.containsKey(username)) return AccountResult.failure(AccountFailure.USERNAME_TAKEN);
        if (!validPassword(password)) return AccountResult.failure(AccountFailure.INVALID_PASSWORD);
        byte[] salt = new byte[PASSWORD_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] verifier = derivePassword(password, salt, PASSWORD_ITERATIONS);
        if (verifier == null) return AccountResult.failure(AccountFailure.STORAGE_ERROR);
        UUID accountId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String displayUsername = requestedUsername.trim();
        Account account = new Account(accountId, username, displayUsername, workspaceId,
                Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(verifier),
                PASSWORD_ITERATIONS);
        accounts.put(accountId, account);
        accountIdByUsername.put(username, accountId);
        workspaceSnapshots.put(workspaceId, new CompoundTag());
        setDirty();
        Arrays.fill(verifier, (byte) 0);
        return AccountResult.success(account.publicAccount());
    }

    public synchronized AccountResult authenticate(String requestedUsername, String password) {
        String username = normalizeUsername(requestedUsername);
        Account account = accounts.get(accountIdByUsername.get(username));
        byte[] salt = account == null ? new byte[PASSWORD_SALT_BYTES] : decodeBase64(account.salt());
        byte[] expected = account == null ? new byte[PASSWORD_HASH_BYTES] : decodeBase64(account.verifier());
        byte[] actual = derivePassword(password == null ? "" : password, salt, account == null ? PASSWORD_ITERATIONS : account.iterations());
        boolean valid = actual != null && MessageDigest.isEqual(expected, actual) && account != null;
        Arrays.fill(salt, (byte) 0);
        Arrays.fill(expected, (byte) 0);
        if (actual != null) Arrays.fill(actual, (byte) 0);
        return valid ? AccountResult.success(account.publicAccount()) : AccountResult.failure(AccountFailure.INVALID_CREDENTIALS);
    }

    public synchronized AccountResult changePassword(UUID accountId, String newPassword) {
        Account account = accounts.get(accountId);
        if (account == null) return AccountResult.failure(AccountFailure.INVALID_CREDENTIALS);
        if (!validPassword(newPassword)) return AccountResult.failure(AccountFailure.INVALID_PASSWORD);
        byte[] salt = new byte[PASSWORD_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] verifier = derivePassword(newPassword, salt, PASSWORD_ITERATIONS);
        if (verifier == null) return AccountResult.failure(AccountFailure.STORAGE_ERROR);
        accounts.put(accountId, new Account(account.accountId(), account.username(), account.displayUsername(),
                account.workspaceId(), Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(verifier), PASSWORD_ITERATIONS));
        setDirty();
        Arrays.fill(verifier, (byte) 0);
        return AccountResult.success(accounts.get(accountId).publicAccount());
    }

    public synchronized AccountInfo account(UUID accountId) {
        Account account = accounts.get(accountId);
        return account == null ? null : account.publicAccount();
    }

    public synchronized AccountInfo faceIdAccount(UUID playerId) {
        UUID accountId = faceIdAccountByPlayer.get(playerId);
        return account(accountId);
    }

    public synchronized UUID faceIdPlayer(UUID accountId) {
        return faceIdAccountByPlayer.entrySet().stream()
                .filter(entry -> entry.getValue().equals(accountId))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    public synchronized boolean queuePendingAntOSGrant(UUID playerId, String action, ResourceLocation target) {
        if (playerId == null || target == null || action == null || !(action.equals("unlock_archive") || action.equals("grant_task"))
                || target.toString().length() > 256) return false;
        boolean added = pendingAntOSGrants.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>())
                .add(new PendingAntOSGrant(action, target));
        if (added) setDirty();
        return true;
    }

    public synchronized List<PendingAntOSGrant> pendingAntOSGrants(UUID playerId) {
        return List.copyOf(pendingAntOSGrants.getOrDefault(playerId, Set.of()));
    }

    public synchronized boolean removePendingAntOSGrant(UUID playerId, PendingAntOSGrant grant) {
        Set<PendingAntOSGrant> grants = pendingAntOSGrants.get(playerId);
        if (grants == null || !grants.remove(grant)) return false;
        if (grants.isEmpty()) pendingAntOSGrants.remove(playerId);
        setDirty();
        return true;
    }

    public record PendingAntOSGrant(String action, ResourceLocation target) { }

    public synchronized boolean linkFaceId(UUID playerId, UUID accountId) {
        if (playerId == null || accountId == null || !accounts.containsKey(accountId)) return false;
        UUID linkedAccount = faceIdAccountByPlayer.get(playerId);
        UUID linkedPlayer = faceIdPlayer(accountId);
        if (linkedAccount != null && !linkedAccount.equals(accountId)) return false;
        if (linkedPlayer != null && !linkedPlayer.equals(playerId)) faceIdAccountByPlayer.remove(linkedPlayer);
        if (!accountId.equals(linkedAccount)) {
            faceIdAccountByPlayer.put(playerId, accountId);
            setDirty();
        } else if (linkedPlayer != null && !linkedPlayer.equals(playerId)) {
            setDirty();
        }
        return true;
    }

    public synchronized boolean unlinkFaceId(UUID playerId, UUID accountId) {
        if (playerId == null || accountId == null || !accountId.equals(faceIdAccountByPlayer.get(playerId))) return false;
        faceIdAccountByPlayer.remove(playerId);
        setDirty();
        return true;
    }

    public synchronized AccountInfo accountByUsername(String requestedUsername) {
        UUID id = accountIdByUsername.get(normalizeUsername(requestedUsername));
        return account(id);
    }

    public synchronized TeamInfo team(UUID accountId) {
        UUID teamId = teamByAccount.get(accountId);
        Team team = teamId == null ? null : teams.get(teamId);
        if (team == null) return null;
        Account owner = accounts.get(team.ownerId);
        return new TeamInfo(team.id, team.ownerId, owner == null ? "" : owner.displayUsername(),
                team.members.stream().map(accounts::get).filter(java.util.Objects::nonNull)
                        .map(account -> new TeamMember(account.accountId(), account.displayUsername(), account.accountId().equals(team.ownerId))).toList());
    }

    public synchronized List<TeamInviteInfo> invitations(UUID accountId) {
        return teamInvites.values().stream().filter(invite -> invite.recipientId().equals(accountId))
                .map(invite -> {
                    Team team = teams.get(invite.teamId());
                    Account owner = team == null ? null : accounts.get(team.ownerId);
                    Account inviter = accounts.get(invite.inviterId());
                    return team == null ? null : new TeamInviteInfo(invite.id(), team.id,
                            owner == null ? "" : owner.displayUsername(), inviter == null ? "" : inviter.displayUsername());
                }).filter(java.util.Objects::nonNull).toList();
    }

    public synchronized boolean createTeam(UUID ownerId) {
        if (ownerId == null || !accounts.containsKey(ownerId) || teamByAccount.containsKey(ownerId)) return false;
        UUID teamId = UUID.randomUUID();
        Team team = new Team(teamId, ownerId);
        CompoundTag snapshot = workspaceSnapshots.get(accounts.get(ownerId).workspaceId());
        if (snapshot != null) team.progress.load(snapshot.getCompound("TaskProgress"));
        team.members.add(ownerId);
        teams.put(teamId, team);
        teamByAccount.put(ownerId, teamId);
        setDirty();
        return true;
    }

    public synchronized TeamInviteInfo inviteToTeam(UUID ownerId, String targetUsername, long createdAt) {
        UUID teamId = teamByAccount.get(ownerId);
        Team team = teamId == null ? null : teams.get(teamId);
        Account recipient = accounts.get(accountIdByUsername.get(normalizeUsername(targetUsername)));
        if (team == null || !team.ownerId.equals(ownerId) || recipient == null || recipient.accountId().equals(ownerId)
                || teamByAccount.containsKey(recipient.accountId())) return null;
        if (teamInvites.values().stream().anyMatch(invite -> invite.teamId().equals(teamId)
                && invite.recipientId().equals(recipient.accountId()))) return null;
        UUID inviteId = UUID.randomUUID();
        teamInvites.put(inviteId, new TeamInvite(inviteId, teamId, recipient.accountId(), ownerId, createdAt));
        setDirty();
        return new TeamInviteInfo(inviteId, teamId, accounts.get(team.ownerId).displayUsername(), accounts.get(ownerId).displayUsername());
    }

    public synchronized boolean acceptTeamInvite(UUID accountId, UUID inviteId) {
        TeamInvite invite = teamInvites.get(inviteId);
        if (accountId == null || invite == null || !invite.recipientId().equals(accountId) || teamByAccount.containsKey(accountId)) return false;
        Team team = teams.get(invite.teamId());
        if (team == null) { teamInvites.remove(inviteId); setDirty(); return false; }
        Account account = accounts.get(accountId);
        CompoundTag snapshot = workspaceSnapshots.get(account.workspaceId());
        if (snapshot != null) {
            ComputerTaskProgress personal = new ComputerTaskProgress();
            personal.load(snapshot.getCompound("TaskProgress"));
            team.progress.mergeFrom(personal);
        }
        team.members.add(accountId);
        teamByAccount.put(accountId, team.id);
        teamInvites.values().removeIf(other -> other.recipientId().equals(accountId));
        setDirty();
        return true;
    }

    public synchronized boolean declineTeamInvite(UUID accountId, UUID inviteId) {
        TeamInvite invite = teamInvites.get(inviteId);
        if (invite == null || accountId == null || !invite.recipientId().equals(accountId)) return false;
        teamInvites.remove(inviteId);
        setDirty();
        return true;
    }

    public synchronized boolean leaveTeam(UUID accountId) {
        UUID teamId = teamByAccount.get(accountId);
        Team team = teamId == null ? null : teams.get(teamId);
        if (team == null || team.ownerId.equals(accountId)) return false;
        persistTeamProgressToAccount(team, accountId);
        team.members.remove(accountId);
        teamByAccount.remove(accountId);
        setDirty();
        return true;
    }

    public synchronized boolean disbandTeam(UUID ownerId) {
        UUID teamId = teamByAccount.get(ownerId);
        Team team = teamId == null ? null : teams.get(teamId);
        if (team == null || !team.ownerId.equals(ownerId)) return false;
        for (UUID memberId : List.copyOf(team.members)) persistTeamProgressToAccount(team, memberId);
        team.members.forEach(teamByAccount::remove);
        teamInvites.values().removeIf(invite -> invite.teamId().equals(teamId));
        teams.remove(teamId);
        setDirty();
        return true;
    }

    public synchronized boolean mergeTeamProgress(UUID accountId, ComputerTaskProgress progress) {
        UUID teamId = teamByAccount.get(accountId);
        Team team = teamId == null ? null : teams.get(teamId);
        if (team == null || progress == null) return false;
        boolean changed = team.progress.mergeFrom(progress);
        if (changed) setDirty();
        return changed;
    }

    public synchronized ComputerTaskProgress effectiveProgress(UUID accountId, ComputerTaskProgress personal) {
        ComputerTaskProgress effective = new ComputerTaskProgress();
        if (personal != null) effective.mergeFrom(personal);
        UUID teamId = teamByAccount.get(accountId);
        Team team = teamId == null ? null : teams.get(teamId);
        if (team != null) effective.mergeFrom(team.progress);
        return effective;
    }

    public synchronized boolean hasClaimedTaskReward(UUID accountId, ResourceLocation taskId) {
        return claimedTaskRewards.getOrDefault(accountId, Set.of()).contains(taskId);
    }

    public synchronized boolean markTaskRewardClaimed(UUID accountId, ResourceLocation taskId) {
        if (accountId == null || taskId == null || !accounts.containsKey(accountId)) return false;
        boolean changed = claimedTaskRewards.computeIfAbsent(accountId, ignored -> new LinkedHashSet<>()).add(taskId);
        if (changed) setDirty();
        return changed;
    }

    private void persistTeamProgressToAccount(Team team, UUID accountId) {
        Account account = accounts.get(accountId);
        if (account == null) return;
        CompoundTag snapshot = workspaceSnapshots.computeIfAbsent(account.workspaceId(), ignored -> new CompoundTag());
        ComputerTaskProgress personal = new ComputerTaskProgress();
        personal.load(snapshot.getCompound("TaskProgress"));
        personal.mergeFrom(team.progress);
        snapshot.put("TaskProgress", personal.save());
    }

    public synchronized List<AccountInfo> accounts() {
        return accounts.values().stream().map(Account::publicAccount).toList();
    }

    public synchronized List<ResourceLocation> profileDisks(UUID accountId) {
        return List.copyOf(profileDisks.getOrDefault(accountId, Set.of()));
    }

    public synchronized boolean addProfileDisk(UUID accountId, ResourceLocation diskId) {
        if (accountId == null || diskId == null || !accounts.containsKey(accountId)) return false;
        boolean changed = profileDisks.computeIfAbsent(accountId, ignored -> new LinkedHashSet<>()).add(diskId);
        if (changed) setDirty();
        return changed;
    }

    public synchronized boolean removeProfileDisk(UUID accountId, ResourceLocation diskId) {
        Set<ResourceLocation> disks = profileDisks.get(accountId);
        if (disks == null || !disks.remove(diskId)) return false;
        setDirty();
        return true;
    }

    public synchronized CompoundTag workspaceSnapshot(UUID workspaceId) {
        CompoundTag snapshot = workspaceId == null ? null : workspaceSnapshots.get(workspaceId);
        return snapshot == null ? null : snapshot.copy();
    }

    /** Copies only the task progress section of an account workspace, avoiding a copy of its files and desktop. */
    public synchronized CompoundTag workspaceSection(UUID workspaceId, String key) {
        CompoundTag snapshot = workspaceId == null ? null : workspaceSnapshots.get(workspaceId);
        return snapshot == null ? null : snapshot.getCompound(key).copy();
    }

    /** Replaces one section of an account workspace in place, leaving the rest of the snapshot untouched. */
    public synchronized boolean saveWorkspaceSection(UUID workspaceId, String key, CompoundTag section) {
        if (workspaceId == null || section == null || !isAccountWorkspace(workspaceId)) return false;
        CompoundTag snapshot = workspaceSnapshots.computeIfAbsent(workspaceId, ignored -> new CompoundTag());
        CompoundTag copy = section.copy();
        if (copy.equals(snapshot.get(key))) return true;
        snapshot.put(key, copy);
        setDirty();
        return true;
    }

    public synchronized boolean saveWorkspaceSnapshot(UUID workspaceId, CompoundTag snapshot) {
        if (workspaceId == null || snapshot == null || !isAccountWorkspace(workspaceId)) return false;
        CompoundTag copy = snapshot.copy();
        CompoundTag previous = workspaceSnapshots.put(workspaceId, copy);
        if (previous == null || !previous.equals(copy)) setDirty();
        return true;
    }

    private boolean isAccountWorkspace(UUID workspaceId) {
        return accounts.values().stream().anyMatch(account -> account.workspaceId().equals(workspaceId));
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean validUsername(String username) {
        return username != null && username.length() >= 3 && username.length() <= 16
                && username.matches("[a-z0-9_]+");
    }

    private static boolean validPassword(String password) {
        return password != null && !password.isEmpty() && password.length() <= MAX_PASSWORD_LENGTH;
    }

    private static byte[] derivePassword(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, PASSWORD_HASH_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ignored) {
            return null;
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (RuntimeException ignored) {
            return new byte[0];
        }
    }

    public synchronized boolean hasWorkspace(UUID owner) {
        UUID workspace = activeWorkspace(owner);
        return progress(workspace).hasRecoverableProgress();
    }

    public synchronized UUID activeWorkspace(UUID owner) {
        return owner == null ? null : activeWorkspaceByOwner.get(owner);
    }

    public synchronized ComputerTaskProgress progress(UUID workspaceId) {
        ComputerTaskProgress result = new ComputerTaskProgress();
        CompoundTag saved = workspaceId == null ? null : workspaces.get(workspaceId);
        if (saved != null) result.load(saved.copy());
        return result;
    }

    /** Starts a new workspace and makes it the owner's active recovery point. */
    public synchronized UUID createWorkspace(UUID owner, ComputerTaskProgress progress) {
        if (owner == null || progress == null) return null;
        UUID workspaceId = UUID.randomUUID();
        activeWorkspaceByOwner.put(owner, workspaceId);
        workspaces.put(workspaceId, progress.save());
        setDirty();
        return workspaceId;
    }

    public synchronized boolean setActiveWorkspace(UUID owner, UUID workspaceId) {
        if (owner == null || workspaceId == null || !workspaces.containsKey(workspaceId)) return false;
        UUID previous = activeWorkspaceByOwner.put(owner, workspaceId);
        if (!workspaceId.equals(previous)) setDirty();
        return true;
    }

    public synchronized void save(UUID workspaceId, ComputerTaskProgress progress) {
        if (workspaceId == null || progress == null) return;
        CompoundTag snapshot = progress.save();
        CompoundTag previous = workspaces.put(workspaceId, snapshot);
        if (previous == null || !previous.equals(snapshot)) setDirty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : workspaces.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Id", entry.getKey().toString());
            row.put("Progress", entry.getValue().copy());
            rows.add(row);
        }
        tag.put(WORKSPACES_TAG, rows);
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, UUID> entry : activeWorkspaceByOwner.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Owner", entry.getKey().toString());
            row.putString("Workspace", entry.getValue().toString());
            owners.add(row);
        }
        tag.put(OWNERS_TAG, owners);
        ListTag accountRows = new ListTag();
        for (Account account : accounts.values()) {
            CompoundTag row = new CompoundTag();
            row.putInt("Schema", ACCOUNT_SCHEMA);
            row.putString("Id", account.accountId().toString());
            row.putString("Username", account.username());
            row.putString("DisplayUsername", account.displayUsername());
            row.putString("Workspace", account.workspaceId().toString());
            row.putString("Salt", account.salt());
            row.putString("Verifier", account.verifier());
            row.putInt("Iterations", account.iterations());
            ListTag disks = new ListTag();
            for (ResourceLocation diskId : profileDisks.getOrDefault(account.accountId(), Set.of())) {
                disks.add(net.minecraft.nbt.StringTag.valueOf(diskId.toString()));
            }
            row.put("Disks", disks);
            accountRows.add(row);
        }
        tag.put(ACCOUNTS_TAG, accountRows);
        ListTag snapshotRows = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : workspaceSnapshots.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Workspace", entry.getKey().toString());
            row.put("Data", entry.getValue().copy());
            snapshotRows.add(row);
        }
        tag.put(SNAPSHOTS_TAG, snapshotRows);
        ListTag faceIdLinks = new ListTag();
        for (Map.Entry<UUID, UUID> entry : faceIdAccountByPlayer.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Player", entry.getKey().toString());
            row.putString("Account", entry.getValue().toString());
            faceIdLinks.add(row);
        }
        tag.put(FACE_ID_LINKS_TAG, faceIdLinks);
        ListTag teamRows = new ListTag();
        for (Team team : teams.values()) {
            CompoundTag row = new CompoundTag();
            row.putString("Id", team.id.toString()); row.putString("Owner", team.ownerId.toString());
            ListTag members = new ListTag();
            team.members.forEach(member -> members.add(net.minecraft.nbt.StringTag.valueOf(member.toString())));
            row.put("Members", members); row.put("Progress", team.progress.save()); teamRows.add(row);
        }
        tag.put(TEAMS_TAG, teamRows);
        ListTag inviteRows = new ListTag();
        for (TeamInvite invite : teamInvites.values()) {
            CompoundTag row = new CompoundTag(); row.putString("Id", invite.id().toString());
            row.putString("Team", invite.teamId().toString()); row.putString("Recipient", invite.recipientId().toString());
            row.putString("Inviter", invite.inviterId().toString()); row.putLong("Created", invite.createdAt()); inviteRows.add(row);
        }
        tag.put(TEAM_INVITES_TAG, inviteRows);
        ListTag claims = new ListTag();
        for (Map.Entry<UUID, Set<ResourceLocation>> entry : claimedTaskRewards.entrySet()) {
            CompoundTag row = new CompoundTag(); row.putString("Account", entry.getKey().toString());
            ListTag ids = new ListTag(); entry.getValue().forEach(id -> ids.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
            row.put("Tasks", ids); claims.add(row);
        }
        tag.put(CLAIMED_REWARDS_TAG, claims);
        ListTag pendingRows = new ListTag();
        for (Map.Entry<UUID, Set<PendingAntOSGrant>> entry : pendingAntOSGrants.entrySet()) {
            for (PendingAntOSGrant grant : entry.getValue()) {
                CompoundTag row = new CompoundTag();
                row.putString("Player", entry.getKey().toString());
                row.putString("Action", grant.action());
                row.putString("Target", grant.target().toString());
                pendingRows.add(row);
            }
        }
        tag.put(PENDING_ANTOS_GRANTS_TAG, pendingRows);
        return tag;
    }

    private static final class Team {
        private final UUID id;
        private final UUID ownerId;
        private final Set<UUID> members = new LinkedHashSet<>();
        private final ComputerTaskProgress progress = new ComputerTaskProgress();
        private Team(UUID id, UUID ownerId) { this.id = id; this.ownerId = ownerId; }
    }

    private record TeamInvite(UUID id, UUID teamId, UUID recipientId, UUID inviterId, long createdAt) { }

    public record TeamMember(UUID accountId, String displayUsername, boolean owner) { }
    public record TeamInfo(UUID id, UUID ownerId, String ownerName, List<TeamMember> members) { }
    public record TeamInviteInfo(UUID id, UUID teamId, String ownerName, String inviterName) { }

    private record Account(UUID accountId, String username, String displayUsername, UUID workspaceId,
                           String salt, String verifier, int iterations) {
        private AccountInfo publicAccount() {
            return new AccountInfo(accountId, username, displayUsername, workspaceId);
        }
    }

    public record AccountInfo(UUID accountId, String username, String displayUsername, UUID workspaceId) { }

    public record AccountResult(AccountInfo account, AccountFailure failure) {
        public boolean successful() { return account != null; }
        public static AccountResult success(AccountInfo account) { return new AccountResult(account, null); }
        public static AccountResult failure(AccountFailure failure) { return new AccountResult(null, failure); }
    }

    public enum AccountFailure { INVALID_USERNAME, USERNAME_TAKEN, INVALID_PASSWORD, INVALID_CREDENTIALS, STORAGE_ERROR }
}
