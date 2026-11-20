package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailDebug;
import com.craisinlord.antos.content.antmail.AntmailAttachment;
import com.craisinlord.antos.content.antmail.AntmailDeliveryResult;
import com.craisinlord.antos.content.antmail.AntmailDraft;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailMailbox;
import com.craisinlord.antos.content.antmail.AntmailServerData;
import com.craisinlord.antos.content.antmail.AntmailValidation;
import com.craisinlord.antos.content.antmail.AntmailWire;
import com.craisinlord.antos.content.antazon.AntazonServerData;
import com.craisinlord.antos.content.computer.ComputerWorkspace;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class AntmailServerHandler {
    private static final int MAX_RESULT_DATA_CHARS = AntmailAnternetResultPayload.MAX_DATA_CHARS;
    private static BiConsumer<ServerPlayer, AntmailAnternetResultPayload> resultSender = (player, payload) -> { };
    private static volatile boolean resultSenderConfigured;
    private static final double BUCKET_CAPACITY = 80.0D;
    private static final double BUCKET_REFILL_PER_NANO = 10.0D / 1_000_000_000L;
    private static final java.util.Map<UUID, Limiter> LIMITERS = new java.util.concurrent.ConcurrentHashMap<>();

    private AntmailServerHandler() {
    }

    public static void setResultSender(BiConsumer<ServerPlayer, AntmailAnternetResultPayload> sender) {
        resultSender = sender;
        resultSenderConfigured = sender != null;
    }

    public static void handle(ServerPlayer player, AntmailAnternetPayload payload) {
        if (payload.first().length() > 65536 || payload.second().length() > 65536 || payload.third().length() > 65536 || payload.fourth().length() > 65536 || payload.fifth().length() > 65536) return;
        if (!limiter(player).tryAcquire(cost(payload.action()))) {
            if (payload.action() == AntmailAnternetPayload.UPDATE_PROFILE) {
                result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "profile_rate_limited", "");
            } else if (payload.action() != AntmailAnternetPayload.STATE) {
                String messageId = payload.action() == AntmailAnternetPayload.SEND ? UUID.randomUUID().toString()
                        : payload.first().length() <= 64 ? payload.first() : "";
                result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", messageId,
                        payload.action() == AntmailAnternetPayload.SEND ? "send:rate_limited" : "rate_limited", "");
            }
            return;
        }
        var account = AnternetAccountHandler.session(player);
        if (account == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", payload.action() == AntmailAnternetPayload.SEND ? "send:unauthorized" : "unauthorized", "");
            return;
        }
        switch (payload.action()) {
            case AntmailAnternetPayload.STATE -> {
                int folder = (int) (payload.number() >> 32);
                int page = (int) payload.number();
                handleState(player, new AntmailStateRequestPayload(folder, page, payload.version()), payload.first(),
                        Boolean.parseBoolean(payload.second()), Boolean.parseBoolean(payload.third()));
            }
            case AntmailAnternetPayload.DIRECTORY -> handleDirectory(player, payload);
            case AntmailAnternetPayload.CONVERSATION -> handleConversation(player, payload);
            case AntmailAnternetPayload.MESSAGE -> handle(player, new AntmailMessageRequestPayload(payload.first()));
            case AntmailAnternetPayload.SEND -> handleSend(player, new AntmailSendPayload(payload.first(), payload.second(), payload.third(), payload.fourth()), payload.fifth());
            case AntmailAnternetPayload.READ -> handle(player, new AntmailReadPayload(payload.first(), Boolean.parseBoolean(payload.second())));
            case AntmailAnternetPayload.DELETE -> handle(player, new AntmailDeletePayload(payload.first(), Boolean.parseBoolean(payload.second())));
            case AntmailAnternetPayload.DRAFT -> {
                try { handle(player, new AntmailDraftPayload(Integer.parseInt(payload.first()), payload.second())); }
                catch (NumberFormatException exception) { result(player, AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal(), "", "", "invalid_action", ""); }
            }
            case AntmailAnternetPayload.RETRY -> handle(player, new AntmailRetryPayload(payload.first()));
            case AntmailAnternetPayload.ARCHIVE -> handleMove(player, payload.first(), Boolean.parseBoolean(payload.second()) ? 1 : 0);
            case AntmailAnternetPayload.TRASH -> handleMove(player, payload.first(), parseFolder(payload.second(), 0), true);
            case AntmailAnternetPayload.RESTORE -> handleRestore(player, payload.first(), parseFolder(payload.second(), 0));
            case AntmailAnternetPayload.PERMANENT_DELETE -> handlePermanentDelete(player, payload.first());
            case AntmailAnternetPayload.UNARCHIVE -> handleUnarchive(player, payload.first(), parseFolder(payload.second(), 0));
            case AntmailAnternetPayload.UPDATE_PROFILE -> handleProfileUpdate(player, payload.first(), payload.second());
            case AntmailAnternetPayload.JUNK_BLOCK -> handleJunkBlock(player, payload.first(), Boolean.parseBoolean(payload.second()));
            case AntmailAnternetPayload.JUNK_BLOCK_ALL -> handleJunkBlockAll(player, Boolean.parseBoolean(payload.first()));
            default -> result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "invalid_action", "");
        }
    }

    private static final com.google.gson.Gson DIRECTORY_JSON = new com.google.gson.Gson();
    /** Received-history heads per mailbox, reused until that mailbox's version changes. Bounded LRU. */
    private static final java.util.Map<AntmailAddress, HistoryHeads> HISTORY_HEADS = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<AntmailAddress, HistoryHeads> eldest) { return size() > 128; }
    };
    private record HistoryHeads(AntmailServerData owner, long version, java.util.Map<UUID, AntmailMessage> heads) { }

    private static java.util.Map<UUID, AntmailMessage> historyHeads(AntmailServerData data, AntmailAddress address) {
        long version = data.mailboxVersion(address);
        synchronized (HISTORY_HEADS) {
            HistoryHeads cached = HISTORY_HEADS.get(address);
            if (cached != null && cached.owner() == data && cached.version() == version) return cached.heads();
            var heads = data.mailboxOrCreate(address).conversationHeads();
            HISTORY_HEADS.put(address, new HistoryHeads(data, version, heads));
            return heads;
        }
    }

    private static void handleDirectory(ServerPlayer player, AntmailAnternetPayload payload) {
        if (authorizedComputer(player) == null) return;
        var account = AnternetAccountHandler.session(player);
        var data = AntmailServerData.access(player.level().getServer());
        var directory = data.directory();
        var accounts = com.craisinlord.antos.content.antmail.AntmailDispatch.accounts(player.level().getServer());
        var teams = com.craisinlord.antos.content.antmail.AntmailDispatch.teams(player.level().getServer());
        var address = data.addressFor(player);
        try {
            if (payload.first().length() > 32 || payload.second().length() > 80 || payload.third().length() > 128 || payload.fourth().length() > 32 || payload.fifth().length() > 64) throw new IllegalArgumentException("request_limit");
            var json = new com.google.gson.JsonObject();
            com.craisinlord.antos.content.antmail.AntmailDirectory.View selectedView = null;
            if (payload.first().equals("contact") || payload.first().equals("uncontact")) {
                directory.contact(account.accountId(), accounts.resolve(payload.second()), payload.third(), payload.first().equals("uncontact"), accounts);
            } else if (payload.first().equals("create")) {
                selectedView = directory.create(account.accountId(), UUID.fromString(payload.second()), payload.fourth(), payload.third());
            } else if (payload.first().equals("view")) {
                selectedView = directory.view(account.accountId(), UUID.fromString(payload.second()));
            } else if (!payload.first().equals("page")) {
                var view = directory.change(account.accountId(), UUID.fromString(payload.second()), payload.version(), payload.first(), payload.third(), accounts);
                if (!payload.first().equals("delete") && !payload.first().equals("leave")) selectedView = view;
            }
            if (selectedView != null) json.add("selected", DIRECTORY_JSON.toJsonTree(selectedView));
            if (!payload.first().equals("page") && !payload.first().equals("view")) data.setDirty();
            int requestedPage = (int) Math.max(0, Math.min(1024, payload.number()));
            json.add("page", DIRECTORY_JSON.toJsonTree(directory.page(account.accountId(), payload.fourth(), payload.fifth(), requestedPage, accounts)));
            var history = new com.google.gson.JsonArray();
            if (payload.fourth().equals("conversation")) {
                // One row per conversation this mailbox has copies of but the account no longer belongs to.
                var heads = historyHeads(data, address);
                var historyIds = heads.keySet().stream().filter(id -> !directory.currentConversation(account.accountId(), id, teams)).toList();
                long start = (long) requestedPage * com.craisinlord.antos.content.antmail.AntmailDirectory.PAGE_SIZE;
                historyIds.stream().skip(start).limit(com.craisinlord.antos.content.antmail.AntmailDirectory.PAGE_SIZE).forEach(id -> {
                    var row = new com.google.gson.JsonObject();
                    var message = heads.get(id);
                    row.addProperty("id", id.toString());
                    row.addProperty("kind", message.conversationKind());
                    row.addProperty("name", message.conversationName() == null ? "" : message.conversationName());
                    history.add(row);
                });
                json.addProperty("historyMore", start + com.craisinlord.antos.content.antmail.AntmailDirectory.PAGE_SIZE < historyIds.size());
            }
            json.add("history", history);
            if (payload.fourth().equals("conversation") || payload.fourth().equals("picker")) {
                UUID teamId = teams.teamOf(account.accountId());
                var team = teamId == null ? null : teams.team(teamId);
                if (team != null && team.member(account.accountId())) {
                    var row = new com.google.gson.JsonObject();
                    row.addProperty("id", team.id().toString());
                    row.addProperty("ownerName", team.ownerName());
                    row.addProperty("owner", team.owner() != null ? team.owner().toString() : "");
                    var teamMembers = new com.google.gson.JsonArray();
                    for (UUID member : team.epochs().keySet()) {
                        String memberAddress = accounts.address(member);
                        if (memberAddress == null) continue;
                        var person = new com.google.gson.JsonObject();
                        person.addProperty("id", member.toString());
                        person.addProperty("address", memberAddress);
                        teamMembers.add(person);
                    }
                    row.add("members", teamMembers);
                    json.add("team", row);
                }
            }
            json.addProperty("account", account.accountId().toString());
            var members = new com.google.gson.JsonArray();
            if (selectedView != null) {
                for (UUID id : selectedView.members()) {
                    var row = new com.google.gson.JsonObject();
                    row.addProperty("id", id.toString());
                    row.addProperty("address", accounts.address(id));
                    members.add(row);
                }
            }
            var profileRows = new com.google.gson.JsonObject();
            var pageRows = json.getAsJsonObject("page").getAsJsonArray("people");
            for (var element : pageRows) {
                String recipient = element.getAsJsonObject().get("address").getAsString();
                profileRows.add(recipient, DIRECTORY_JSON.toJsonTree(data.profile(AntmailAddress.parse(recipient))));
            }
            json.add("profiles", profileRows);
            json.add("members", members);
            json.addProperty("operation", payload.first());
            json.addProperty("target", payload.second());
            result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "", "directory", json.toString());
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal(), address.fullAddress(), "", "directory_error:" + exception.getMessage(), "");
        }
    }

    private static void handleConversation(ServerPlayer player, AntmailAnternetPayload payload) {
        if (authorizedComputer(player) == null) return;
        var data = AntmailServerData.access(player.level().getServer());
        var address = data.addressFor(player);
        try {
            UUID id = UUID.fromString(payload.first());
            java.util.Map<UUID, Integer> folders = new java.util.HashMap<>();
            var messages = data.mailboxOrCreate(address).conversation(id, folders);
            int page = (int) Math.max(0, Math.min(1024, payload.number()));
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("Conversation", id.toString());
            tag.putInt("Page", page);
            var rows = new net.minecraft.nbt.ListTag();
            messages.stream().skip((long) page * 12).limit(12).forEach(message -> {
                var row = message.toListTag();
                row.putInt("SourceFolder", folders.getOrDefault(message.id(), -1));
                rows.add(row);
            });
            tag.put("Messages", rows);
            tag.putBoolean("More", ((long) page + 1) * 12 < messages.size());
            data.appendProfiles(tag, address);
            result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "", "conversation", AntmailWire.encodeTag(tag));
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal(), address.fullAddress(), "", "conversation_error", "");
        }
    }

    private static int cost(int action) {
        return switch (action) {
            case AntmailAnternetPayload.STATE, AntmailAnternetPayload.MESSAGE, AntmailAnternetPayload.READ -> 1;
            case AntmailAnternetPayload.SEND, AntmailAnternetPayload.RETRY -> 5;
            case AntmailAnternetPayload.UPDATE_PROFILE -> 4;
            default -> 2;
        };
    }

    private static Limiter limiter(ServerPlayer player) {
        long now = System.nanoTime();
        if (LIMITERS.size() > 256) LIMITERS.values().removeIf(limiter -> limiter.idleSince(now) > 300_000_000_000L);
        return LIMITERS.computeIfAbsent(player.getUUID(), ignored -> new Limiter(now));
    }

    private static final class Limiter {
        private double tokens = BUCKET_CAPACITY;
        private long refilledAt;

        private Limiter(long now) {
            refilledAt = now;
        }

        synchronized long idleSince(long now) {
            return now - refilledAt;
        }

        synchronized boolean tryAcquire(int cost) {
            long now = System.nanoTime();
            tokens = Math.min(BUCKET_CAPACITY, tokens + (now - refilledAt) * BUCKET_REFILL_PER_NANO);
            refilledAt = now;
            if (tokens < cost) return false;
            tokens -= cost;
            return true;
        }
    }

    public static void handle(ServerPlayer player, AntmailStateRequestPayload payload) {
        handleState(player, payload, "", false, false);
    }

    private static void handleState(ServerPlayer player, AntmailStateRequestPayload payload, String query,
                                    boolean unreadOnly, boolean attachmentsOnly) {
        AntmailDebug.log("state request received player=" + player.getGameProfile().name()
                + " folder=" + payload.folder() + " page=" + payload.page() + " knownVersion=" + payload.knownVersion());
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            AntmailDebug.log("state request rejected: computer missing, out of reach, unauthenticated, or owned by another user");
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", "", "unconfigured", "");
            return;
        }
        AntmailMailbox mailbox = data.mailboxOrCreate(address);
        if (payload.knownVersion() == data.mailboxVersion(address)) {
            result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "", "unchanged", "");
            return;
        }
        int folder = payload.folder() < AntmailStateRequestPayload.INBOX || payload.folder() > AntmailStateRequestPayload.TRASH
                ? AntmailStateRequestPayload.INBOX : payload.folder();
        int page = Math.max(0, Math.min(AntmailValidation.MAX_MAILBOX_MESSAGES, payload.page()));
        try {
            String encoded = encodeSnapshot(player.level().getServer(), data, mailbox, address, folder, page, query, unreadOnly, attachmentsOnly);
            result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "", "", encoded);
        } catch (RuntimeException exception) {
            AntmailDebug.error("Failed to encode state for " + address.fullAddress(), exception);
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), "", "state_encode_failed", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailMessageRequestPayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", payload.messageId(), "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", payload.messageId(), "unconfigured", "");
            return;
        }
        try {
            UUID id = UUID.fromString(payload.messageId());
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            AntmailMessage message = mailbox.findInbox(id);
            if (message == null) message = mailbox.findSent(id);
            if (message == null) message = mailbox.findArchived(id);
            if (message == null) message = mailbox.findTrash(id);
            if (message == null) {
                result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), "not_found", "");
                return;
            }
            var detail = message.toTag();
            detail.putInt("SourceFolder", mailbox.folderOf(message.id()));
            data.appendProfiles(detail, address);
            result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), payload.messageId(), "message_detail", AntmailWire.encodeTag(detail));
        } catch (IllegalArgumentException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), "invalid_id", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailSendPayload payload) {
        handleSend(player, payload, "");
    }

    private static void handleSend(ServerPlayer player, AntmailSendPayload payload, String draftIdInput) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            sendResult(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", UUID.randomUUID().toString(), "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress sender = addressFor(player, data);
        if (sender == null) {
            sendResult(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", UUID.randomUUID().toString(), "unconfigured", "");
            return;
        }
        try {
            UUID requestId = draftIdInput.isBlank() ? UUID.randomUUID() : UUID.fromString(draftIdInput);
            var actor = AnternetAccountHandler.session(player).accountId();
            var message = com.craisinlord.antos.content.antmail.AntmailDispatch.send(player.level().getServer(), data, actor, sender, requestId,
                    payload.recipient(), payload.subject(), payload.body(), AntmailWire.decodeAttachments(payload.attachments()),
                    count -> limiter(player).tryAcquire(Math.max(0, count - 1)));
            sendResult(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), sender.fullAddress(), message.id().toString(),
                    message.batch().failedCount() == 0 ? "" : "partial_delivery", "");
        } catch (RuntimeException exception) {
            sendResult(player, AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal(), sender.fullAddress(), draftIdInput,
                    exception.getMessage() == null ? "invalid_message" : exception.getMessage(), "");
        }
    }

    private static void handleProfileUpdate(ServerPlayer player, String displayNameInput, String avatarItemInput) {
        if (authorizedComputer(player) == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", "", "unconfigured", "");
            return;
        }
        String displayName = net.minecraft.ChatFormatting.stripFormatting(displayNameInput == null ? "" : displayNameInput)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        String avatarItem = avatarItemInput == null ? "" : avatarItemInput.trim();
        if (displayName.length() > 32) {
            sendProfileUpdateResult(player, data, address, "invalid_display_name", AntmailDeliveryResult.Status.MESSAGE_INVALID);
            return;
        }
        if (!avatarItem.isBlank()) {
            if (avatarItem.length() > 128) {
                sendProfileUpdateResult(player, data, address, "invalid_item_id", AntmailDeliveryResult.Status.MESSAGE_INVALID);
                return;
            }
            if (!avatarItem.contains(":")) avatarItem = "minecraft:" + avatarItem;
            net.minecraft.resources.Identifier itemId = net.minecraft.resources.Identifier.tryParse(avatarItem);
            if (itemId == null || net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).isEmpty()) {
                sendProfileUpdateResult(player, data, address, "invalid_item_id", AntmailDeliveryResult.Status.MESSAGE_INVALID);
                return;
            }
            avatarItem = itemId.toString();
        }
        data.setProfile(address, displayName, avatarItem, player.level().getServer());
        sendProfileUpdateResult(player, data, address, "profile_saved", AntmailDeliveryResult.Status.DELIVERED);
    }

    private static void handleJunkBlock(ServerPlayer player, String senderInput, boolean blocked) {
        if (authorizedComputer(player) == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", "", "unconfigured", "");
            return;
        }
        String sender = senderInput == null || senderInput.length() > 64 ? "" : senderInput.trim();
        if (sender.isEmpty() || blocked && !com.craisinlord.antos.content.antmail.AntmailEventData.isJunkSender(sender)
                || !data.setJunkSenderBlocked(address, sender, blocked)) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), "", "junk_failed", "");
            return;
        }
        result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "", blocked ? "junk_blocked" : "junk_unblocked", "");
    }

    private static void handleJunkBlockAll(ServerPlayer player, boolean blocked) {
        if (authorizedComputer(player) == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", "", "unconfigured", "");
            return;
        }
        data.setBlockAllJunk(address, blocked);
        result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "", "junk_saved", "");
    }

    private static void appendJunkSettings(AntmailServerData data, net.minecraft.nbt.CompoundTag snapshot, AntmailAddress address) {
        net.minecraft.nbt.ListTag blocked = new net.minecraft.nbt.ListTag();
        for (String sender : data.blockedJunkSenders(address)) {
            net.minecraft.nbt.CompoundTag row = new net.minecraft.nbt.CompoundTag();
            row.putString("Address", sender);
            row.putString("Name", com.craisinlord.antos.content.antmail.AntmailEventData.junkSenderName(sender));
            blocked.add(row);
        }
        snapshot.put("JunkBlocked", blocked);
        snapshot.putBoolean("JunkBlockAll", data.blocksAllJunk(address));
    }

    private static void sendProfileUpdateResult(ServerPlayer player, AntmailServerData data, AntmailAddress address,
                                                String detail, AntmailDeliveryResult.Status status) {
        result(player, status.ordinal(), address.fullAddress(), "", detail, "");
    }

    private static UUID antcoinAccount(net.minecraft.server.MinecraftServer server, AntmailAddress address) {
        return AntmailServerData.access(server).accountIdForAddress(server, address);
    }

    private static void markAllRead(ServerPlayer player, ComputerWorkspace computer, AntmailServerData data, AntmailAddress address) {
        AntmailMailbox mailbox = data.mailboxOrCreate(address);
        List<AntmailMessage> unread = mailbox.inbox().stream().filter(message -> !message.read()).toList();
        boolean archiveChanged = false;
        for (AntmailMessage message : unread) {
            mailbox.markRead(message.id());
            if (message.definitionId().isBlank()) continue;
            try {
                unlockArchivePages(player, computer, message);
                com.craisinlord.antos.content.computer.ComputerTasks.recordEvent(player, computer, "mail_read", net.minecraft.resources.Identifier.parse(message.definitionId()));
                archiveChanged = true;
            } catch (RuntimeException ignored) { }
            com.craisinlord.antos.content.antmail.AntmailEventData.onMailRead(player, address, message);
        }
        if (!unread.isEmpty()) data.touchMailbox(address);
        if (archiveChanged) ComputerAccessHandler.sendArchive(player, computer);
        result(player, unread.isEmpty() ? AntmailDeliveryResult.Status.FAILED.ordinal() : AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "*", unread.isEmpty() ? "not_found" : "", "");
    }

    public static void handle(ServerPlayer player, AntmailReadPayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", payload.messageId(), "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", payload.messageId(), "unconfigured", "");
            return;
        }
        if (payload.read() && payload.messageId().equals("*")) {
            markAllRead(player, computer, data, address);
            return;
        }
        try {
            UUID id = UUID.fromString(payload.messageId());
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            AntmailMessage message = mailbox.findInbox(id);
            boolean newlyRead = payload.read() && message != null && !message.read();
            boolean changed = payload.read() ? mailbox.markRead(id) : markUnread(mailbox, id);
            if (newlyRead && message != null && !message.definitionId().isBlank()) {
                try {
                    unlockArchivePages(player, computer, message);
                    com.craisinlord.antos.content.computer.ComputerTasks.recordEvent(player, computer, "mail_read", net.minecraft.resources.Identifier.parse(message.definitionId()));
                    ComputerAccessHandler.sendArchive(player, computer);
                } catch (RuntimeException ignored) { }
                com.craisinlord.antos.content.antmail.AntmailEventData.onMailRead(player, address, message);
            }
            if (changed) data.touchMailbox(address);
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), changed ? "" : "not_found", "");
        } catch (IllegalArgumentException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), "invalid_id", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailDeletePayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", payload.messageId(), "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", payload.messageId(), "unconfigured", "");
            return;
        }
        try {
            UUID id = UUID.fromString(payload.messageId());
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            boolean changed = payload.sent() ? mailbox.removeSent(id) : mailbox.removeInbox(id);
            if (changed) data.touchMailbox(address);
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), changed ? "" : "not_found", "");
        } catch (IllegalArgumentException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), payload.messageId(), "invalid_id", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailDraftPayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", "", "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (address == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", "", "unconfigured", "");
            return;
        }
        AntmailMailbox mailbox = data.mailboxOrCreate(address);
        try {
            if (payload.action() == AntmailDraftPayload.SAVE) {
                AntmailDraft draft = AntmailDraft.fromTag(AntmailWire.decodeTag(payload.data()));
                AntmailDraft stored = new AntmailDraft(draft.id(), address, draft.recipient(), draft.subject(), draft.body(), draft.attachments(), draft.recipients());
                if (mailbox.batch(draft.id()) != null) throw new IllegalArgumentException("already_sent");
                if (!mailbox.saveDraft(stored)) throw new IllegalArgumentException("draft_limit");
            } else if (payload.action() == AntmailDraftPayload.DELETE) {
                if (!mailbox.removeDraft(UUID.fromString(payload.data()))) throw new IllegalArgumentException("not_found");
            } else {
                throw new IllegalArgumentException("invalid_action");
            }
            data.touchMailbox(address);
            result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), address.fullAddress(), "", "draft_saved", "");
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal(), address.fullAddress(), "", "draft_failed", "");
        }
    }

    public static void handle(ServerPlayer player, AntmailRetryPayload payload) {
        ComputerWorkspace computer = authorizedComputer(player);
        if (computer == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", payload.messageId(), "unauthorized", "");
            return;
        }
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress sender = addressFor(player, data);
        if (sender == null) {
            result(player, AntmailDeliveryResult.Status.ADDRESS_NOT_FOUND.ordinal(), "", payload.messageId(), "unconfigured", "");
            return;
        }
        try {
            UUID id = UUID.fromString(payload.messageId());
            if (data.mailboxOrCreate(sender).batch(id) != null) {
                var message = com.craisinlord.antos.content.antmail.AntmailDispatch.retry(player.level().getServer(), data,
                        AnternetAccountHandler.session(player).accountId(), sender, id,
                        count -> limiter(player).tryAcquire(Math.max(0, count - 1)));
                result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), sender.fullAddress(), payload.messageId(),
                        message.batch().failedCount() == 0 ? "" : "partial_delivery", "");
                return;
            }
            var legacy = data.mailboxOrCreate(sender).findSent(id);
            if (legacy != null && !legacy.deliveryStatus().equals("DELIVERED") && legacy.attachments().stream().anyMatch(AntmailAttachment.Antcoins.class::isInstance)) {
                var migrated = com.craisinlord.antos.content.antmail.AntmailDispatch.send(player.level().getServer(), data,
                        AnternetAccountHandler.session(player).accountId(), sender, id, legacy.recipient().fullAddress(),
                        legacy.subject(), legacy.body(), legacy.attachments(), count -> limiter(player).tryAcquire(count));
                result(player, AntmailDeliveryResult.Status.DELIVERED.ordinal(), sender.fullAddress(), payload.messageId(), migrated.batch().failedCount() == 0 ? "" : "partial_delivery", "");
                return;
            }
            AntmailDeliveryResult delivery = data.retry(player.level().getServer(), sender, id);
            result(player, delivery == null ? AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal() : delivery.status().ordinal(), sender.fullAddress(), payload.messageId(), delivery == null ? "not_found" : delivery.detail(), "");
        } catch (IllegalArgumentException exception) {
            result(player, AntmailDeliveryResult.Status.MESSAGE_INVALID.ordinal(), sender.fullAddress(), payload.messageId(), java.util.Set.of("membership_changed", "not_found", "rate_limited", "send_deleted").contains(exception.getMessage()) ? exception.getMessage() : "invalid_id", "");
        }
    }

    private static int parseFolder(String value, int fallback) {
        try { return Math.max(0, Math.min(4, Integer.parseInt(value))); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private static void handleMove(ServerPlayer player, String messageId, int sourceFolder) {
        handleMove(player, messageId, sourceFolder, false);
    }

    private static void handleMove(ServerPlayer player, String messageId, int sourceFolder, boolean toTrash) {
        ComputerWorkspace computer = authorizedComputer(player);
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (computer == null || address == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", messageId, "unauthorized", "");
            return;
        }
        try {
            UUID id = UUID.fromString(messageId);
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            boolean changed = toTrash ? mailbox.moveToTrash(id, sourceFolder) : mailbox.moveToArchive(id, sourceFolder == AntmailStateRequestPayload.SENT);
            if (changed) data.touchMailbox(address);
            int folder = sourceFolder >= AntmailStateRequestPayload.ARCHIVE ? sourceFolder : AntmailStateRequestPayload.INBOX;
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(),
                    address.fullAddress(), messageId, changed ? (toTrash ? "moved_to_trash" : "archived") : "not_found", "");
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), messageId, "invalid_id", "");
        }
    }

    private static void handleRestore(ServerPlayer player, String messageId, int destinationFolder) {
        ComputerWorkspace computer = authorizedComputer(player);
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (computer == null || address == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", messageId, "unauthorized", "");
            return;
        }
        try {
            UUID id = UUID.fromString(messageId);
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            boolean changed = mailbox.restoreFromTrash(id, destinationFolder);
            if (changed) data.touchMailbox(address);
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(),
                    address.fullAddress(), messageId, changed ? "restored" : "not_found", "");
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), messageId, "invalid_id", "");
        }
    }

    private static void handlePermanentDelete(ServerPlayer player, String messageId) {
        ComputerWorkspace computer = authorizedComputer(player);
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (computer == null || address == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", messageId, "unauthorized", "");
            return;
        }
        try {
            UUID id = UUID.fromString(messageId);
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            boolean changed = mailbox.removeTrash(id);
            if (changed) data.touchMailbox(address);
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(),
                    address.fullAddress(), messageId, changed ? "permanently_deleted" : "not_found", "");
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), messageId, "invalid_id", "");
        }
    }

    private static void handleUnarchive(ServerPlayer player, String messageId, int destinationFolder) {
        ComputerWorkspace computer = authorizedComputer(player);
        AntmailServerData data = AntmailServerData.access(player.level().getServer());
        AntmailAddress address = addressFor(player, data);
        if (computer == null || address == null) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), "", messageId, "unauthorized", "");
            return;
        }
        try {
            UUID id = UUID.fromString(messageId);
            AntmailMailbox mailbox = data.mailboxOrCreate(address);
            boolean changed = mailbox.restoreFromArchive(id, destinationFolder);
            if (changed) data.touchMailbox(address);
            result(player, changed ? AntmailDeliveryResult.Status.DELIVERED.ordinal() : AntmailDeliveryResult.Status.FAILED.ordinal(),
                    address.fullAddress(), messageId, changed ? "restored_to_inbox" : "not_found", "");
        } catch (RuntimeException exception) {
            result(player, AntmailDeliveryResult.Status.FAILED.ordinal(), address.fullAddress(), messageId, "invalid_id", "");
        }
    }

    private static String encodeSnapshot(net.minecraft.server.MinecraftServer server, AntmailServerData data, AntmailMailbox mailbox, AntmailAddress address, int folder, int page,
                                         String query, boolean unreadOnly, boolean attachmentsOnly) {
        boolean draftAttachments = true;
        int maxRows = Integer.MAX_VALUE;
        // Draft recipient labels don't depend on how the page gets trimmed below, so resolve them once.
        var labels = new net.minecraft.nbt.ListTag();
        var actor = data.accountIdForAddress(server, address);
        var accounts = com.craisinlord.antos.content.antmail.AntmailDispatch.accounts(server);
        var teams = com.craisinlord.antos.content.antmail.AntmailDispatch.teams(server);
        java.util.Set<String> references = new java.util.LinkedHashSet<>();
        for (var draft : mailbox.drafts()) {
            try {
                for (var selection : com.craisinlord.antos.content.antmail.AntmailRecipients.parse(draft.recipients())) {
                    if (labels.size() >= 256 || !references.add(selection.token())) continue;
                    String label = data.directory().label(actor, selection, accounts, teams);
                    if (label.isBlank()) continue;
                    var row = new net.minecraft.nbt.CompoundTag();
                    row.putString("Reference", selection.token()); row.putString("Label", label); labels.add(row);
                }
            } catch (IllegalArgumentException ignored) { }
        }
        while (true) {
            var snapshot = mailbox.toPageTag(folder, page, query, unreadOnly, attachmentsOnly, draftAttachments, maxRows);
            snapshot.put("RecipientLabels", labels);
            data.appendProfiles(snapshot, address);
            appendJunkSettings(data, snapshot, address);
            String encoded = encodeOrNull(snapshot);
            if (encoded != null && encoded.length() <= MAX_RESULT_DATA_CHARS) return encoded;
            int rows = snapshot.getIntOr("PageSize", 0);
            if (draftAttachments) {
                draftAttachments = false;
            } else if (rows > 0) {
                int estimate = encoded == null ? rows / 2 : (int) (rows * (MAX_RESULT_DATA_CHARS * 0.9D / encoded.length()));
                maxRows = Math.max(0, Math.min(rows - 1, estimate));
            } else {
                throw new IllegalArgumentException("Antmail snapshot does not fit the result packet");
            }
        }
    }

    private static String encodeOrNull(net.minecraft.nbt.CompoundTag snapshot) {
        try {
            return AntmailWire.encodeTag(snapshot);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ComputerWorkspace authorizedComputer(ServerPlayer player) {
        ComputerWorkspace computer = computer(player);
        return computer != null && computer.canUseFileSystem(player) ? computer : null;
    }

    private static boolean markUnread(AntmailMailbox mailbox, UUID id) {
        for (com.craisinlord.antos.content.antmail.AntmailMessage message : mailbox.inbox()) {
            if (message.id().equals(id)) {
                message.markUnread();
                return true;
            }
        }
        return false;
    }

    private static ComputerWorkspace computer(ServerPlayer player) {
        var account = AnternetAccountHandler.session(player);
        if (account != null) return new com.craisinlord.antos.content.computer.AccountWorkspace(player, account);
        return null;
    }

    private static AntmailAddress addressFor(ServerPlayer player, AntmailServerData data) {
        return data.addressFor(player);
    }

    private static void sendResult(ServerPlayer player, int status, String address, String messageId, String detail, String data) {
        result(player, status, address, messageId, "send:" + detail, data);
    }

    private static void result(ServerPlayer player, int status, String address, String messageId, String detail, String data) {
        long version = 0L;
        try {
            if (!address.isBlank()) version = AntmailServerData.access(player.level().getServer()).mailboxVersion(AntmailAddress.parse(address));
        } catch (IllegalArgumentException ignored) {
        }
        AntmailDebug.log("sending result player=" + player.getGameProfile().name() + " status=" + status
                + " detail=" + detail + " addressPresent=" + !address.isBlank() + " dataChars=" + data.length() + " version=" + version);
        if (data.length() > MAX_RESULT_DATA_CHARS) {
            AntmailDebug.error("Dropping oversized Antmail result for " + address, new IllegalStateException("dataChars=" + data.length()));
            status = AntmailDeliveryResult.Status.FAILED.ordinal();
            detail = "result_too_large";
            data = "";
        }
        if (!resultSenderConfigured) {
            AntmailDebug.error("Result sender is not configured; dropping response", new IllegalStateException("AntmailServerHandler.setResultSender was not called"));
            return;
        }
        resultSender.accept(player, new AntmailAnternetResultPayload(status, address, messageId, detail, data, version));
    }

    private static void unlockArchivePages(ServerPlayer player, ComputerWorkspace computer, AntmailMessage message) {
        boolean changed = false;
        for (com.craisinlord.antos.content.antmail.AntmailAttachment attachment : message.attachments()) {
            if (!(attachment instanceof com.craisinlord.antos.content.antmail.AntmailAttachment.Render render) || !render.kind().equals("archive")) continue;
            net.minecraft.resources.Identifier entryId = net.minecraft.resources.Identifier.tryParse(render.resourceId());
            if (entryId == null || com.craisinlord.antos.content.guide.ComputerGuideData.entry(entryId) == null) continue;
            changed |= computer.taskProgress().unlockArchiveEntry(entryId);
        }
        if (!changed) return;
        computer.saveWorkspaceProgress();
        var account = AnternetAccountHandler.session(player);
        if (account != null) com.craisinlord.antos.content.computer.ComputerWorkspaceData.access(player.level().getServer()).mergeTeamProgress(account.accountId(), computer.taskProgress());
    }
}
