package com.craisinlord.antos.content.computer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.util.HashMap;
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
    private static final String PINNED_TASKS_TAG = "PinnedTasks";
    public static final int MAX_PINNED_TASKS = 5;
    private static final String PENDING_ANTOS_GRANTS_TAG = "PendingAntOSGrants";
    private static final int ACCOUNT_SCHEMA = 1;
    private static final int PASSWORD_ITERATIONS = 160_000;
    private static final int PASSWORD_SALT_BYTES = 16;
    private static final int PASSWORD_HASH_BYTES = 32;
    private static final int MAX_PASSWORD_LENGTH = 128;
    public static final int MAX_HINT_LENGTH = 64;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final List<String> LEGACY_ACCOUNT_KEYS = List.of(ComputerAccountShard.DISKS_TAG,
            ComputerAccountShard.COLLECTED_DISKS_TAG, ComputerAccountShard.DISK_STATE_TAG, ComputerAccountShard.DISK_REWARDS_TAG);
    private static final net.minecraft.world.level.saveddata.SavedDataType<ComputerWorkspaceData> FACTORY = com.craisinlord.antos.content.AntOSSavedDataType.create(DATA_ID, ComputerWorkspaceData::new, tag -> load(tag, null), (data, tag) -> data.save(tag, null));
    private final Map<UUID, CompoundTag> workspaces = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeWorkspaceByOwner = new LinkedHashMap<>();
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();
    private final Map<String, UUID> accountIdByUsername = new LinkedHashMap<>();
    private final Map<UUID, UUID> accountByWorkspace = new HashMap<>();
    private final Map<UUID, ComputerAccountShard> shards = new HashMap<>();
    private final Map<UUID, CompoundTag> legacyShards = new LinkedHashMap<>();
    private final Set<UUID> pendingMigrations = new LinkedHashSet<>();
    private SavedDataStorage storage;
    private MinecraftServer server;
    private volatile long mutationEpoch;
    private final Map<UUID, UUID> faceIdAccountByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, UUID> teamByAccount = new LinkedHashMap<>();
    private final Map<UUID, TeamInvite> teamInvites = new LinkedHashMap<>();
    private final Map<UUID, Set<Identifier>> claimedTaskRewards = new LinkedHashMap<>();
    private final Map<UUID, Set<Identifier>> pinnedTasks = new LinkedHashMap<>();
    private final Map<UUID, Set<PendingAntOSGrant>> pendingAntOSGrants = new LinkedHashMap<>();

    public static ComputerWorkspaceData access(MinecraftServer server) {
        com.craisinlord.antos.content.AntOSSavedDataType.migrateLegacyFile(server, DATA_ID);
        SavedDataStorage storage = server.overworld().getDataStorage();
        ComputerWorkspaceData data = storage.computeIfAbsent(FACTORY);
        if (data.storage != storage) data.attach(server, storage, server.registryAccess());
        return data;
    }

    private synchronized void attach(MinecraftServer server, SavedDataStorage storage, HolderLookup.Provider registries) {
        if (this.storage == storage) return;
        com.craisinlord.antos.content.AntOSSavedDataType.migrateLegacyPrefix(server, "antos_account_");
        this.storage = storage;
        this.server = server;
        shards.clear();
        if (legacyShards.isEmpty()) return;
        for (Map.Entry<UUID, CompoundTag> entry : legacyShards.entrySet()) {
            String key = ComputerAccountShard.dataId(entry.getKey());
            ComputerAccountShard shard = storage.get(ComputerAccountShard.type(key));
            if (shard == null) {
                shard = ComputerAccountShard.load(entry.getValue(), registries);
                storage.set(ComputerAccountShard.type(key), shard);
                shard.awaitPersist();
                shard.setDirty();
                pendingMigrations.add(entry.getKey());
            }
            shard.bind(this);
            shards.put(entry.getKey(), shard);
        }
        legacyShards.clear();
        if (pendingMigrations.isEmpty()) setDirty();
    }

    long mutationEpoch() {
        return mutationEpoch;
    }

    void touch() {
        mutationEpoch++;
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty) touch();
    }

    @Override
    public synchronized boolean isDirty() {
        if (super.isDirty()) return true;
        for (UUID accountId : pendingMigrations) {
            ComputerAccountShard shard = shards.get(accountId);
            if (shard == null || shard.confirmPersisted(server, ComputerAccountShard.dataId(accountId))) return true;
        }
        return false;
    }

    private CompoundTag legacyAccountData(UUID accountId) {
        CompoundTag legacy = legacyShards.get(accountId);
        if (legacy != null) return legacy;
        ComputerAccountShard shard = pendingMigrations.contains(accountId) ? shards.get(accountId) : null;
        return shard == null ? null : shard.write(new CompoundTag());
    }

    private ComputerAccountShard shard(UUID accountId) {
        ComputerAccountShard shard = shards.get(accountId);
        if (shard == null) {
            shard = storage == null ? new ComputerAccountShard()
                    : storage.computeIfAbsent(ComputerAccountShard.type(ComputerAccountShard.dataId(accountId)));
            shard.bind(this);
            shards.put(accountId, shard);
        }
        return shard;
    }

    private ComputerAccountShard workspaceShard(UUID workspaceId) {
        UUID accountId = workspaceId == null ? null : accountByWorkspace.get(workspaceId);
        return accountId == null ? null : shard(accountId);
    }

    private ComputerAccountShard accountShard(UUID accountId) {
        return accountId == null || !accounts.containsKey(accountId) ? null : shard(accountId);
    }

    private static ComputerWorkspaceData load(CompoundTag tag, HolderLookup.Provider registries) {
        ComputerWorkspaceData data = new ComputerWorkspaceData();
        ListTag workspaces = tag.getListOrEmpty(WORKSPACES_TAG);
        for (int index = 0; index < workspaces.size(); index++) {
            CompoundTag row = workspaces.getCompoundOrEmpty(index);
            try {
                UUID workspaceId = UUID.fromString(row.getStringOr("Id", ""));
                data.workspaces.put(workspaceId, row.getCompoundOrEmpty("Progress").copy());
            } catch (RuntimeException ignored) { }
        }
        ListTag owners = tag.getListOrEmpty(OWNERS_TAG);
        for (int index = 0; index < owners.size(); index++) {
            CompoundTag row = owners.getCompoundOrEmpty(index);
            try {
                data.activeWorkspaceByOwner.put(UUID.fromString(row.getStringOr("Owner", "")), UUID.fromString(row.getStringOr("Workspace", "")));
            } catch (RuntimeException ignored) { }
        }
        ListTag accounts = tag.getListOrEmpty(ACCOUNTS_TAG);
        for (int index = 0; index < accounts.size(); index++) {
            CompoundTag row = accounts.getCompoundOrEmpty(index);
            try {
                UUID accountId = UUID.fromString(row.getStringOr("Id", ""));
                UUID workspaceId = UUID.fromString(row.getStringOr("Workspace", ""));
                String username = normalizeUsername(row.getStringOr("Username", ""));
                String displayUsername = row.getStringOr("DisplayUsername", "");
                String salt = row.getStringOr("Salt", "");
                String verifier = row.getStringOr("Verifier", "");
                int iterations = row.getIntOr("Iterations", 0);
                String hint = cleanHint(row.getStringOr("Hint", ""));
                boolean mustChangePassword = row.getBooleanOr("MustChangePassword", false);
                if (row.getIntOr("Schema", 0) == ACCOUNT_SCHEMA && validUsername(username) && !data.accountIdByUsername.containsKey(username)
                        && !salt.isBlank() && !verifier.isBlank() && iterations >= 100_000 && iterations <= 1_000_000) {
                    Account account = new Account(accountId, username,
                            displayUsername.isBlank() ? username : displayUsername, workspaceId,
                            salt, verifier, iterations, hint, mustChangePassword);
                    data.accounts.put(accountId, account);
                    data.accountIdByUsername.put(username, accountId);
                    data.accountByWorkspace.put(workspaceId, accountId);
                    if (legacyAccountRow(row)) {
                        CompoundTag legacy = new CompoundTag();
                        for (String key : LEGACY_ACCOUNT_KEYS) {
                            Tag value = row.get(key);
                            if (value != null) legacy.put(key, value);
                        }
                        data.legacyShards.put(accountId, legacy);
                    }
                }
            } catch (RuntimeException ignored) { }
        }
        ListTag snapshots = tag.getListOrEmpty(SNAPSHOTS_TAG);
        for (int index = 0; index < snapshots.size(); index++) {
            CompoundTag row = snapshots.getCompoundOrEmpty(index);
            try {
                UUID accountId = data.accountByWorkspace.get(UUID.fromString(row.getStringOr("Workspace", "")));
                if (accountId != null) {
                    data.legacyShards.computeIfAbsent(accountId, ignored -> new CompoundTag())
                            .put(ComputerAccountShard.SNAPSHOT_TAG, row.getCompoundOrEmpty("Data").copy());
                }
            } catch (RuntimeException ignored) { }
        }
        ListTag faceIdLinks = tag.getListOrEmpty(FACE_ID_LINKS_TAG);
        for (int index = 0; index < faceIdLinks.size(); index++) {
            CompoundTag row = faceIdLinks.getCompoundOrEmpty(index);
            try {
                UUID playerId = UUID.fromString(row.getStringOr("Player", ""));
                UUID accountId = UUID.fromString(row.getStringOr("Account", ""));
                if (data.accounts.containsKey(accountId) && !data.faceIdAccountByPlayer.containsKey(playerId)
                        && data.faceIdAccountByPlayer.values().stream().noneMatch(accountId::equals)) {
                    data.faceIdAccountByPlayer.put(playerId, accountId);
                }
            } catch (RuntimeException ignored) { }
        }
        ListTag teamRows = tag.getListOrEmpty(TEAMS_TAG);
        for (int index = 0; index < teamRows.size(); index++) {
            CompoundTag row = teamRows.getCompoundOrEmpty(index);
            try {
                UUID teamId = UUID.fromString(row.getStringOr("Id", ""));
                UUID ownerId = UUID.fromString(row.getStringOr("Owner", ""));
                if (!data.accounts.containsKey(ownerId) || data.teamByAccount.containsKey(ownerId) || data.teams.containsKey(teamId)) continue;
                Team team = new Team(teamId, ownerId);
                ListTag members = row.getListOrEmpty("Members");
                for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                    try {
                        UUID memberId = UUID.fromString(members.getStringOr(memberIndex, ""));
                        if (data.accounts.containsKey(memberId) && !data.teamByAccount.containsKey(memberId)) {
                            team.members.add(memberId);
                            data.teamByAccount.put(memberId, teamId);
                        }
                    } catch (RuntimeException ignored) { }
                }
                if (!team.members.contains(ownerId)) team.members.add(ownerId);
                team.progress.load(row.getCompoundOrEmpty("Progress"));
                data.teams.put(teamId, team);
                data.teamByAccount.put(ownerId, teamId);
            } catch (RuntimeException ignored) { }
        }
        ListTag inviteRows = tag.getListOrEmpty(TEAM_INVITES_TAG);
        for (int index = 0; index < inviteRows.size(); index++) {
            CompoundTag row = inviteRows.getCompoundOrEmpty(index);
            try {
                TeamInvite invite = new TeamInvite(UUID.fromString(row.getStringOr("Id", "")),
                        UUID.fromString(row.getStringOr("Team", "")), UUID.fromString(row.getStringOr("Recipient", "")),
                        UUID.fromString(row.getStringOr("Inviter", "")), row.getLongOr("Created", 0L));
                if (data.teams.containsKey(invite.teamId()) && data.accounts.containsKey(invite.recipientId())
                        && data.accounts.containsKey(invite.inviterId()) && !data.teamByAccount.containsKey(invite.recipientId())) {
                    data.teamInvites.putIfAbsent(invite.id(), invite);
                }
            } catch (RuntimeException ignored) { }
        }
        ListTag claimedRows = tag.getListOrEmpty(CLAIMED_REWARDS_TAG);
        for (int index = 0; index < claimedRows.size(); index++) {
            CompoundTag row = claimedRows.getCompoundOrEmpty(index);
            try {
                UUID accountId = UUID.fromString(row.getStringOr("Account", ""));
                if (!data.accounts.containsKey(accountId)) continue;
                Set<Identifier> claimed = new LinkedHashSet<>();
                ListTag ids = row.getListOrEmpty("Tasks");
                for (int idIndex = 0; idIndex < ids.size(); idIndex++) {
                    try { claimed.add(Identifier.parse(ids.getStringOr(idIndex, ""))); }
                    catch (RuntimeException ignored) { }
                }
                data.claimedTaskRewards.put(accountId, claimed);
            } catch (RuntimeException ignored) { }
        }
        ListTag pinnedRows = tag.getListOrEmpty(PINNED_TASKS_TAG);
        for (int index = 0; index < pinnedRows.size(); index++) {
            CompoundTag row = pinnedRows.getCompoundOrEmpty(index);
            try {
                UUID accountId = UUID.fromString(row.getStringOr("Account", ""));
                if (!data.accounts.containsKey(accountId)) continue;
                Set<Identifier> pinned = new LinkedHashSet<>();
                ListTag ids = row.getListOrEmpty("Tasks");
                for (int idIndex = 0; idIndex < ids.size() && pinned.size() < MAX_PINNED_TASKS; idIndex++) {
                    try { pinned.add(Identifier.parse(ids.getStringOr(idIndex, ""))); }
                    catch (RuntimeException ignored) { }
                }
                if (!pinned.isEmpty()) data.pinnedTasks.put(accountId, pinned);
            } catch (RuntimeException ignored) { }
        }
        ListTag pendingRows = tag.getListOrEmpty(PENDING_ANTOS_GRANTS_TAG);
        for (int index = 0; index < pendingRows.size(); index++) {
            CompoundTag row = pendingRows.getCompoundOrEmpty(index);
            try {
                UUID playerId = UUID.fromString(row.getStringOr("Player", ""));
                String action = row.getStringOr("Action", "");
                Identifier target = Identifier.parse(row.getStringOr("Target", ""));
                if ((action.equals("unlock_archive") || action.equals("grant_task")) && target.toString().length() <= 256) {
                    data.pendingAntOSGrants.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>())
                            .add(new PendingAntOSGrant(action, target));
                }
            } catch (RuntimeException ignored) { }
        }
        return data;
    }

    private static boolean legacyAccountRow(CompoundTag row) {
        for (String key : LEGACY_ACCOUNT_KEYS) {
            if (row.contains(key)) return true;
        }
        return false;
    }

    public AccountResult createAccount(String requestedUsername, String password) {
        AccountFailure failure = validateNewAccount(requestedUsername, password);
        if (failure != null) return AccountResult.failure(failure);
        return completeAccountCreation(requestedUsername, hashPassword(password));
    }

    public synchronized AccountFailure validateNewAccount(String requestedUsername, String password) {
        String username = normalizeUsername(requestedUsername);
        if (!validUsername(username)) return AccountFailure.INVALID_USERNAME;
        if (accountIdByUsername.containsKey(username)) return AccountFailure.USERNAME_TAKEN;
        if (!validPassword(password)) return AccountFailure.INVALID_PASSWORD;
        return null;
    }

    public synchronized AccountResult completeAccountCreation(String requestedUsername, PasswordHash hash) {
        return completeAccountCreation(requestedUsername, hash, "");
    }

    public synchronized AccountResult completeAccountCreation(String requestedUsername, PasswordHash hash, String hint) {
        String username = normalizeUsername(requestedUsername);
        if (!validUsername(username)) return AccountResult.failure(AccountFailure.INVALID_USERNAME);
        if (accountIdByUsername.containsKey(username)) return AccountResult.failure(AccountFailure.USERNAME_TAKEN);
        if (hash == null) return AccountResult.failure(AccountFailure.STORAGE_ERROR);
        UUID accountId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String displayUsername = requestedUsername.trim();
        Account account = new Account(accountId, username, displayUsername, workspaceId,
                hash.salt(), hash.verifier(), hash.iterations(), cleanHint(hint), false);
        accounts.put(accountId, account);
        accountIdByUsername.put(username, accountId);
        accountByWorkspace.put(workspaceId, accountId);
        setDirty();
        return AccountResult.success(account.publicAccount());
    }

    public AccountResult authenticate(String requestedUsername, String password) {
        CredentialCheck check = prepareAuthentication(requestedUsername);
        return completeAuthentication(check, verifyCredentials(check, password));
    }

    public synchronized CredentialCheck prepareAuthentication(String requestedUsername) {
        String username = normalizeUsername(requestedUsername);
        Account account = accounts.get(accountIdByUsername.get(username));
        if (account == null) {
            return new CredentialCheck(null, new byte[PASSWORD_SALT_BYTES], new byte[PASSWORD_HASH_BYTES], PASSWORD_ITERATIONS, null);
        }
        return new CredentialCheck(account.accountId(), decodeBase64(account.salt()), decodeBase64(account.verifier()),
                account.iterations(), account.verifier());
    }

    public static boolean verifyCredentials(CredentialCheck check, String password) {
        byte[] actual = derivePassword(password == null ? "" : password, check.salt(), check.iterations());
        boolean valid = actual != null && MessageDigest.isEqual(check.expected(), actual) && check.accountId() != null;
        Arrays.fill(check.salt(), (byte) 0);
        Arrays.fill(check.expected(), (byte) 0);
        if (actual != null) Arrays.fill(actual, (byte) 0);
        return valid;
    }

    public synchronized AccountResult completeAuthentication(CredentialCheck check, boolean valid) {
        Account account = check.accountId() == null ? null : accounts.get(check.accountId());
        if (!valid || account == null || !account.verifier().equals(check.verifier())) {
            return AccountResult.failure(AccountFailure.INVALID_CREDENTIALS);
        }
        return AccountResult.success(account.publicAccount());
    }

    public static PasswordHash hashPassword(String password) {
        if (!validPassword(password)) return null;
        byte[] salt = new byte[PASSWORD_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] verifier = derivePassword(password, salt, PASSWORD_ITERATIONS);
        if (verifier == null) return null;
        PasswordHash hash = new PasswordHash(Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(verifier), PASSWORD_ITERATIONS);
        Arrays.fill(verifier, (byte) 0);
        return hash;
    }

    public static PasswordChange derivePasswordChange(CredentialCheck check, String currentPassword, String newPassword) {
        boolean verified = verifyCredentials(check, currentPassword);
        if (!verified) return new PasswordChange(false, false, null);
        if (!validPassword(newPassword)) return new PasswordChange(true, false, null);
        return new PasswordChange(true, true, hashPassword(newPassword));
    }

    public AccountResult changePassword(UUID accountId, String newPassword) {
        if (account(accountId) == null) return AccountResult.failure(AccountFailure.INVALID_CREDENTIALS);
        if (!validPassword(newPassword)) return AccountResult.failure(AccountFailure.INVALID_PASSWORD);
        return completePasswordChange(accountId, null, hashPassword(newPassword));
    }

    public synchronized AccountResult resetPassword(UUID accountId, PasswordHash hash, boolean mustChangePassword) {
        Account account = accounts.get(accountId);
        if (account == null) return AccountResult.failure(AccountFailure.INVALID_CREDENTIALS);
        if (hash == null) return AccountResult.failure(AccountFailure.STORAGE_ERROR);
        accounts.put(accountId, account.withPassword(hash, mustChangePassword));
        setDirty();
        return AccountResult.success(accounts.get(accountId).publicAccount());
    }

    public synchronized boolean setHint(UUID accountId, String hint) {
        Account account = accounts.get(accountId);
        if (account == null) return false;
        accounts.put(accountId, account.withHint(cleanHint(hint)));
        setDirty();
        return true;
    }

    public synchronized String hint(UUID accountId) {
        Account account = accounts.get(accountId);
        return account == null ? "" : account.hint();
    }

    public synchronized String hintForUsername(String requestedUsername) {
        Account account = accounts.get(accountIdByUsername.get(normalizeUsername(requestedUsername)));
        return account == null ? "" : account.hint();
    }

    public synchronized boolean mustChangePassword(UUID accountId) {
        Account account = accounts.get(accountId);
        return account != null && account.mustChangePassword();
    }

    public static boolean validHint(String hint, String password) {
        if (hint == null) return true;
        String cleaned = hint.strip();
        if (cleaned.length() > MAX_HINT_LENGTH) return false;
        if (cleaned.isEmpty() || password == null || password.isEmpty()) return true;
        return !cleaned.toLowerCase(java.util.Locale.ROOT).contains(password.toLowerCase(java.util.Locale.ROOT));
    }

    private static String cleanHint(String hint) {
        if (hint == null) return "";
        String cleaned = hint.strip();
        return cleaned.length() > MAX_HINT_LENGTH ? cleaned.substring(0, MAX_HINT_LENGTH) : cleaned;
    }

    public synchronized AccountResult completePasswordChange(UUID accountId, String expectedVerifier, PasswordHash hash) {
        Account account = accounts.get(accountId);
        if (account == null || expectedVerifier != null && !expectedVerifier.equals(account.verifier())) {
            return AccountResult.failure(AccountFailure.INVALID_CREDENTIALS);
        }
        if (hash == null) return AccountResult.failure(AccountFailure.STORAGE_ERROR);
        accounts.put(accountId, account.withPassword(hash, false));
        setDirty();
        return AccountResult.success(accounts.get(accountId).publicAccount());
    }

    public record CredentialCheck(UUID accountId, byte[] salt, byte[] expected, int iterations, String verifier) { }
    public record PasswordHash(String salt, String verifier, int iterations) { }
    public record PasswordChange(boolean verified, boolean validPassword, PasswordHash hash) { }

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

    public synchronized boolean queuePendingAntOSGrant(UUID playerId, String action, Identifier target) {
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

    public record PendingAntOSGrant(String action, Identifier target) { }

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
        team.progress.load(shard(ownerId).snapshot.getCompoundOrEmpty("TaskProgress"));
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
        ComputerAccountShard shard = accountShard(accountId);
        if (shard != null) {
            ComputerTaskProgress personal = new ComputerTaskProgress();
            personal.load(shard.snapshot.getCompoundOrEmpty("TaskProgress"));
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

    public synchronized boolean hasClaimedTaskReward(UUID accountId, Identifier taskId) {
        return claimedTaskRewards.getOrDefault(accountId, Set.of()).contains(taskId);
    }

    public synchronized boolean markTaskRewardClaimed(UUID accountId, Identifier taskId) {
        if (accountId == null || taskId == null || !accounts.containsKey(accountId)) return false;
        boolean changed = claimedTaskRewards.computeIfAbsent(accountId, ignored -> new LinkedHashSet<>()).add(taskId);
        if (changed) setDirty();
        return changed;
    }

    public synchronized List<Identifier> pinnedTasks(UUID accountId) {
        return List.copyOf(pinnedTasks.getOrDefault(accountId, Set.of()));
    }

    public synchronized boolean isTaskPinned(UUID accountId, Identifier taskId) {
        return pinnedTasks.getOrDefault(accountId, Set.of()).contains(taskId);
    }

    public synchronized PinResult setTaskPinned(UUID accountId, Identifier taskId, boolean pinned) {
        if (accountId == null || taskId == null || !accounts.containsKey(accountId)) return PinResult.INVALID;
        Set<Identifier> current = pinnedTasks.computeIfAbsent(accountId, ignored -> new LinkedHashSet<>());
        if (!pinned) {
            boolean removed = current.remove(taskId);
            if (current.isEmpty()) pinnedTasks.remove(accountId);
            if (removed) setDirty();
            return removed ? PinResult.CHANGED : PinResult.UNCHANGED;
        }
        if (current.contains(taskId)) return PinResult.UNCHANGED;
        if (current.size() >= MAX_PINNED_TASKS) return PinResult.FULL;
        current.add(taskId);
        setDirty();
        return PinResult.CHANGED;
    }

    public enum PinResult { CHANGED, UNCHANGED, FULL, INVALID }

    private void persistTeamProgressToAccount(Team team, UUID accountId) {
        ComputerAccountShard shard = accountShard(accountId);
        if (shard == null) return;
        ComputerTaskProgress personal = new ComputerTaskProgress();
        personal.load(shard.snapshot.getCompoundOrEmpty("TaskProgress"));
        personal.mergeFrom(team.progress);
        CompoundTag saved = personal.save();
        if (saved.equals(shard.snapshot.get("TaskProgress"))) return;
        CompoundTag next = shallowCopy(shard.snapshot);
        next.put("TaskProgress", saved);
        shard.snapshot = next;
        shard.setDirty();
    }

    public synchronized List<AccountInfo> accounts() {
        return accounts.values().stream().map(Account::publicAccount).toList();
    }

    public synchronized List<Identifier> profileDisks(UUID accountId) {
        ComputerAccountShard shard = accountShard(accountId);
        return shard == null ? List.of() : List.copyOf(shard.profileDisks);
    }

    public synchronized List<Identifier> collectedDisks(UUID accountId) {
        ComputerAccountShard shard = accountShard(accountId);
        return shard == null ? List.of() : List.copyOf(shard.collectedDisks);
    }

    public synchronized DiskProfileState diskState(UUID accountId) {
        ComputerAccountShard shard = accountShard(accountId);
        if (shard == null) throw new IllegalArgumentException("Unknown disk profile");
        return shard.diskState;
    }

    public synchronized List<Identifier> activatedDisks(UUID accountId) {
        return accountId == null || !accounts.containsKey(accountId) ? List.of() : diskState(accountId).activatedDisks();
    }

    public synchronized boolean claimDiskRewards(UUID accountId, Identifier diskId) {
        ComputerAccountShard shard = diskId == null ? null : accountShard(accountId);
        if (shard == null || !shard.collectedDisks.contains(diskId)) return false;
        if (shard.diskRewardReceipts.putIfAbsent(diskId, "[]") != null) return false;
        shard.setDirty();
        return true;
    }

    public synchronized void recordDiskRewardReceipt(UUID accountId, Identifier diskId, String receipt) {
        ComputerAccountShard shard = accountShard(accountId);
        if (shard == null) return;
        Map<Identifier, String> receipts = shard.diskRewardReceipts;
        if (!receipts.containsKey(diskId) || receipt == null || receipt.equals(receipts.get(diskId))) return;
        receipts.put(diskId, receipt);
        shard.setDirty();
    }

    public synchronized Map<Identifier, String> diskRewardReceipts(UUID accountId) {
        ComputerAccountShard shard = accountShard(accountId);
        return shard == null ? Map.of() : Map.copyOf(shard.diskRewardReceipts);
    }

    public synchronized boolean addProfileDisk(UUID accountId, Identifier diskId) {
        ComputerAccountShard shard = diskId == null ? null : accountShard(accountId);
        if (shard == null) return false;
        boolean changed = shard.profileDisks.add(diskId);
        boolean collected = shard.collectedDisks.add(diskId);
        if (changed || collected) shard.setDirty();
        return changed;
    }

    public synchronized boolean removeProfileDisk(UUID accountId, Identifier diskId) {
        ComputerAccountShard shard = accountShard(accountId);
        if (shard == null || !shard.profileDisks.remove(diskId)) return false;
        shard.setDirty();
        return true;
    }

    public synchronized CompoundTag workspaceSnapshot(UUID workspaceId) {
        ComputerAccountShard shard = workspaceShard(workspaceId);
        return shard == null ? null : shard.snapshot.copy();
    }

    public synchronized CompoundTag workspaceSection(UUID workspaceId, String key) {
        ComputerAccountShard shard = workspaceShard(workspaceId);
        return shard == null ? null : shard.snapshot.getCompoundOrEmpty(key).copy();
    }

    synchronized boolean readWorkspaceSnapshot(UUID workspaceId, java.util.function.Consumer<CompoundTag> reader) {
        ComputerAccountShard shard = workspaceShard(workspaceId);
        if (shard == null) return false;
        reader.accept(shard.snapshot);
        return true;
    }

    public synchronized boolean saveWorkspaceSection(UUID workspaceId, String key, CompoundTag section) {
        ComputerAccountShard shard = section == null ? null : workspaceShard(workspaceId);
        if (shard == null) return false;
        if (section.equals(shard.snapshot.get(key))) return true;
        CompoundTag next = shallowCopy(shard.snapshot);
        next.put(key, section.copy());
        shard.snapshot = next;
        shard.setDirty();
        return true;
    }

    synchronized boolean saveOwnedWorkspaceSections(UUID workspaceId, Map<String, Tag> sections) {
        ComputerAccountShard shard = sections == null ? null : workspaceShard(workspaceId);
        if (shard == null) return false;
        CompoundTag snapshot = shard.snapshot;
        CompoundTag next = null;
        for (Map.Entry<String, Tag> section : sections.entrySet()) {
            if (section.getValue() == null || section.getValue().equals(snapshot.get(section.getKey()))) continue;
            if (next == null) next = shallowCopy(snapshot);
            next.put(section.getKey(), section.getValue());
        }
        if (next == null) return true;
        shard.snapshot = next;
        shard.setDirty();
        return true;
    }

    private static CompoundTag shallowCopy(CompoundTag tag) {
        CompoundTag copy = new CompoundTag();
        if (tag != null) {
            for (String key : tag.keySet()) copy.put(key, tag.get(key));
        }
        return copy;
    }

    public synchronized boolean saveWorkspaceSnapshot(UUID workspaceId, CompoundTag snapshot) {
        ComputerAccountShard shard = snapshot == null ? null : workspaceShard(workspaceId);
        if (shard == null) return false;
        CompoundTag copy = snapshot.copy();
        CompoundTag previous = shard.snapshot;
        shard.snapshot = copy;
        if (!previous.equals(copy)) shard.setDirty();
        return true;
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean validUsername(String username) {
        return username != null && username.length() >= 3 && username.length() <= 16
                && username.matches("[a-z0-9_]+");
    }

    public static boolean validPassword(String password) {
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
        if (saved != null) result.load(saved);
        return result;
    }

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
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : workspaces.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Id", entry.getKey().toString());
            row.put("Progress", entry.getValue());
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
        pendingMigrations.removeIf(accountId -> {
            ComputerAccountShard shard = shards.get(accountId);
            return shard == null || shard.persisted();
        });
        ListTag accountRows = new ListTag();
        ListTag snapshotRows = new ListTag();
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
            if (!account.hint().isEmpty()) row.putString("Hint", account.hint());
            if (account.mustChangePassword()) row.putBoolean("MustChangePassword", true);
            CompoundTag legacy = legacyAccountData(account.accountId());
            if (legacy != null) {
                for (String key : LEGACY_ACCOUNT_KEYS) {
                    Tag value = legacy.get(key);
                    if (value != null) row.put(key, value);
                }
                if (legacy.contains(ComputerAccountShard.SNAPSHOT_TAG)) {
                    CompoundTag snapshotRow = new CompoundTag();
                    snapshotRow.putString("Workspace", account.workspaceId().toString());
                    snapshotRow.put("Data", legacy.getCompoundOrEmpty(ComputerAccountShard.SNAPSHOT_TAG));
                    snapshotRows.add(snapshotRow);
                }
            }
            accountRows.add(row);
        }
        tag.put(ACCOUNTS_TAG, accountRows);
        if (!snapshotRows.isEmpty()) tag.put(SNAPSHOTS_TAG, snapshotRows);
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
        for (Map.Entry<UUID, Set<Identifier>> entry : claimedTaskRewards.entrySet()) {
            CompoundTag row = new CompoundTag(); row.putString("Account", entry.getKey().toString());
            ListTag ids = new ListTag(); entry.getValue().forEach(id -> ids.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
            row.put("Tasks", ids); claims.add(row);
        }
        tag.put(CLAIMED_REWARDS_TAG, claims);
        ListTag pins = new ListTag();
        for (Map.Entry<UUID, Set<Identifier>> entry : pinnedTasks.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag row = new CompoundTag(); row.putString("Account", entry.getKey().toString());
            ListTag ids = new ListTag(); entry.getValue().forEach(id -> ids.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
            row.put("Tasks", ids); pins.add(row);
        }
        tag.put(PINNED_TASKS_TAG, pins);
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
                           String salt, String verifier, int iterations, String hint, boolean mustChangePassword) {
        private AccountInfo publicAccount() {
            return new AccountInfo(accountId, username, displayUsername, workspaceId);
        }

        private Account withPassword(PasswordHash hash, boolean mustChange) {
            return new Account(accountId, username, displayUsername, workspaceId, hash.salt(), hash.verifier(), hash.iterations(), hint, mustChange);
        }

        private Account withHint(String newHint) {
            return new Account(accountId, username, displayUsername, workspaceId, salt, verifier, iterations, newHint, mustChangePassword);
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
