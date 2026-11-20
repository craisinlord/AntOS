package com.craisinlord.antos.content.antmail;

import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.network.AntmailAnternetResultPayload;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AntmailAudienceTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Fixture f = new Fixture();
        UUID alice = f.add("alice"), bob = f.add("bob"), carol = f.add("carol"), dave = f.add("dave");
        f.directory.contact(alice, bob, "Classmate", false, f.accounts());
        check(f.directory.page(alice, "contacts", "class", 0, f.accounts()).people().size() == 1, "contact alias search");
        check(f.directory.page(bob, "contacts", "", 0, f.accounts()).people().isEmpty(), "contact isolation");
        var personal = f.directory.create(alice, UUID.randomUUID(), "personal", "Friends");
        personal = f.change(alice, personal, "add", bob);
        personal = f.change(alice, personal, "add", carol);
        UUID personalId = personal.id();
        reject(() -> f.directory.view(bob, personalId), "private lists hidden");
        var mailing = f.directory.create(alice, UUID.randomUUID(), "mailing", "Announcements");
        mailing = f.change(alice, mailing, "add", bob);
        UUID mailingId = mailing.id();
        reject(() -> f.send(bob, "mailing:" + mailingId), "only managers announce");
        var stale = mailing;
        mailing = f.change(alice, mailing, "rename", "News");
        reject(() -> f.change(alice, stale, "add", carol), "stale changes rejected");
        reject(() -> f.change(bob, stale, "promote", bob), "members cannot promote");
        var ownerCheck = mailing;
        reject(() -> f.change(alice, ownerCheck, "remove", alice), "owner retained");
        var sent = f.send(alice, "personal:" + personalId + ", bob@antmail.com, carol@antmail.com");
        check(sent.batch().deliveries().size() == 2, "overlapping recipients deduplicated");
        check(f.mail(alice).sent().size() == 1, "one logical sent entry");
        check(f.mail(bob).inboxSize() == 1 && f.mail(carol).inboxSize() == 1, "offline mailbox copies");
        check(!f.mail(bob).inbox().get(0).id().equals(f.mail(carol).inbox().get(0).id()), "separate delivery ids");
        check(f.mail(bob).inbox().get(0).logicalSend().equals(sent.id()), "logical send metadata");
        f.mail(bob).markRead(f.mail(bob).inbox().get(0).id());
        f.mail(bob).moveToArchive(f.mail(bob).inbox().get(0).id(), false);
        check(!f.mail(carol).inbox().get(0).read() && f.mail(carol).archived().isEmpty(), "independent read and folders");
        check(sent.searchText().contains("carol"), "search includes full audience");
        f.retry(alice, sent.id());
        check(f.mail(bob).archived().size() == 1 && f.mail(carol).inboxSize() == 1, "successful retry never redelivers");
        AntmailDispatch.send(f, alice, f.address(alice), sent.id(), "dave@antmail.com", "Changed", "Changed", List.of(), count -> true);
        check(f.mail(dave).inboxSize() == 0 && f.mail(alice).sent().size() == 1, "duplicate request ignores changed body and audience");
        f.full(carol);
        UUID draftId = UUID.randomUUID();
        f.mail(alice).saveDraft(new AntmailDraft(draftId, f.address(alice), null, "Partial", "Body", List.of(), "bob@antmail.com,carol@antmail.com"));
        var partial = AntmailDispatch.send(f, alice, f.address(alice), draftId, "bob@antmail.com,carol@antmail.com", "Partial", "Body", List.of(), count -> true);
        check(partial.batch().failedCount() == 1, "full mailbox recorded per recipient");
        check(f.mail(alice).findDraft(draftId) == null, "partial send removes draft");
        int bobCopies = f.mail(bob).inboxSize();
        f.restart();
        check(f.directory.page(alice, "contacts", "class", 0, f.accounts()).people().size() == 1, "contacts survive restart");
        check(f.mail(alice).batch(draftId).batch().failedCount() == 1, "delivery results survive restart");
        f.empty(carol);
        f.retry(alice, draftId);
        check(f.mail(bob).inboxSize() == bobCopies && f.mail(carol).inboxSize() == 1, "restart retry sends failures only");
        var group = f.directory.create(alice, UUID.randomUUID(), "conversation", "Study group");
        group = f.change(alice, group, "add", bob);
        group = f.change(alice, group, "add", carol);
        UUID groupId = group.id();
        var beforeJoin = f.send(alice, "conversation:" + groupId);
        check(f.mail(alice).inbox().stream().noneMatch(message -> groupId.equals(message.conversationId())), "sender has no redundant group inbox");
        group = f.change(alice, group, "add", dave);
        check(f.mail(dave).conversation(groupId).isEmpty(), "new members get no history");
        f.send(bob, "conversation:" + groupId);
        check(f.mail(dave).conversation(groupId).size() == 1, "members can reply all to current audience");
        int received = f.mail(carol).conversation(groupId).size();
        group = f.change(alice, group, "remove", carol);
        reject(() -> f.send(carol, "conversation:" + groupId), "removed members cannot post");
        f.send(alice, "conversation:" + groupId);
        check(f.mail(carol).conversation(groupId).size() == received, "removed members retain only their messages");
        group = f.change(alice, group, "add", carol);
        check(f.mail(carol).conversation(groupId).size() == received, "rejoining adds no missed history");
        f.full(carol);
        var failedGroup = f.send(alice, "conversation:" + groupId);
        group = f.change(alice, group, "remove", carol);
        group = f.change(alice, group, "add", carol);
        f.empty(carol);
        f.retry(alice, failedGroup.id());
        check(f.mail(carol).inboxSize() == 0 && f.mail(alice).batch(failedGroup.id()).batch().failedCount() == 1, "retry cannot bridge membership absence");
        var bobGroupSend = f.send(bob, "conversation:" + groupId);
        group = f.change(alice, group, "remove", bob);
        reject(() -> f.retry(bob, bobGroupSend.id()), "removed sender cannot retry");
        check(beforeJoin.batch().deliveries().stream().noneMatch(row -> row.account().equals(dave)), "retry audience stays frozen");
        check(f.mail(dave).conversation(UUID.randomUUID()).isEmpty(), "conversation ids expose no other mailbox");
        f.balance.put(alice, 100L);
        List<AntmailAttachment> coins = List.of(new AntmailAttachment.Antcoins(10));
        reject(() -> f.send(alice, "personal:" + personalId, coins), "personal list coins forbidden");
        reject(() -> f.send(alice, "mailing:" + mailingId, coins), "mailing coins forbidden");
        reject(() -> f.send(alice, "conversation:" + groupId, coins), "group coins forbidden");
        reject(() -> f.send(alice, "bob@antmail.com,carol@antmail.com", coins), "multiple direct coins forbidden");
        check(f.balance.get(alice) == 100, "rejected transfers never debit");
        f.empty(bob);
        var paid = f.send(alice, "bob@antmail.com", coins);
        check(f.balance.get(alice) == 90 && f.balance.get(bob) == 10, "direct payment credited once");
        f.retry(alice, paid.id()); f.restart(); f.retry(alice, paid.id());
        check(f.balance.get(alice) == 90 && f.balance.get(bob) == 10, "retry and restart cannot repeat payment");
        f.full(bob);
        var paymentFailed = f.send(alice, "bob@antmail.com", coins);
        check(f.balance.get(alice) == 90, "mailbox failure does not debit");
        f.empty(bob); f.retry(alice, paymentFailed.id());
        check(f.balance.get(alice) == 80 && f.balance.get(bob) == 20, "failed transfer pays once on retry");
        int sentCount = f.mail(alice).sent().size();
        reject(() -> f.send(alice, "bob@antmail.com,nobody@antmail.com"), "invalid audience rejected atomically");
        check(f.mail(alice).sent().size() == sentCount, "invalid audience creates no send");
        reject(() -> AntmailDispatch.send(f, alice, f.address(alice), UUID.randomUUID(), "bob@antmail.com", "Title", "Body", List.of(), count -> false), "fanout budget checked before send");
        var oldDraft = new AntmailDraft(null, f.address(alice), f.address(bob), "Legacy", "Body", List.of()).toTag();
        oldDraft.remove("Recipients");
        check(AntmailDraft.fromTag(oldDraft).recipients().equals(f.address(bob).fullAddress()), "legacy To drafts load");
        var multiDraft = new AntmailDraft(null, f.address(alice), null, "Draft", "Body", List.of(), "personal:" + personalId + ",bob@antmail.com");
        check(AntmailDraft.fromTag(multiDraft.toTag()).recipients().equals(multiDraft.recipients()), "typed selections survive draft save");
        var oldMail = AntmailMessage.create(f.address(alice), f.address(bob), "Legacy", "Body", 1, List.of());
        check(AntmailMessage.fromTag(oldMail.toTag()).batch() == null, "legacy mail remains ordinary");
        CompoundTag mixed = f.mail(alice).toTag();
        mixed.getList("Sent", 10).add(new CompoundTag());
        check(AntmailMailbox.fromTag(mixed).sent().size() == sentCount, "malformed record preserves valid mail");
        var directoryJson = f.directory.save();
        check(AntmailDirectory.load(directoryJson).view(alice, personalId).name().equals("Friends"), "lists persist");
        AntmailClientState.clear();
        var page = new com.google.gson.JsonObject();
        page.addProperty("account", alice.toString());
        AntmailClientState.update(new AntmailAnternetResultPayload(0, f.address(alice).fullAddress(), "", "directory", page.toString(), 1));
        check(AntmailClientState.directory() != null && AntmailClientState.getMailbox() == null, "directory dispatched before snapshots");
        CompoundTag conversation = new CompoundTag(); conversation.putString("Conversation", groupId.toString());
        AntmailClientState.update(new AntmailAnternetResultPayload(0, f.address(alice).fullAddress(), "", "conversation", AntmailWire.encodeTag(conversation), 1));
        check(AntmailClientState.conversation() != null && AntmailClientState.getMailbox() == null, "conversation cache isolated");
        AntmailClientState.clear();
        check(AntmailClientState.directory() == null && AntmailClientState.conversation() == null, "logout clears private caches");
        Fixture pages = new Fixture();
        UUID author = pages.add("author"), student = pages.add("student");
        for (int i = 0; i < 25; i++) pages.send(author, "student@antmail.com");
        var firstPage = pages.mail(author).toPageTag(1, 0);
        var secondPage = pages.mail(author).toPageTag(1, firstPage.getInt("NextPage"));
        check(firstPage.getList("Sent", 10).size() == 20 && firstPage.getBoolean("More"), "mail pages bounded");
        check(secondPage.getList("Sent", 10).size() == 5 && !secondPage.getBoolean("More"), "last mail page complete");
        check(firstPage.getInt("TotalSent") == 25 && secondPage.getInt("TotalSent") == 25, "batch counts independent of page");
        check(!firstPage.getList("Sent", 10).getCompound(0).getString("Id").equals(secondPage.getList("Sent", 10).getCompound(0).getString("Id")), "mail pages do not repeat sends");
        List<AntmailAttachment> files = List.of(new AntmailAttachment.TextFile("notes.txt", "Hello"), new AntmailAttachment.PaintImage("art.antpaint", 32, 24, new byte[768]));
        var withFiles = pages.send(author, "student@antmail.com", files);
        check(pages.mail(student).inbox().stream().filter(message -> message.logicalSend().equals(withFiles.id())).findFirst().orElseThrow().attachments().size() == 2, "text and painting fanout preserved");
        var marked = AntmailDispatch.send(pages, author, pages.address(author), UUID.randomUUID(), "student@antmail.com", "Link", "See @item:minecraft:stone", List.of(), count -> true);
        check(marked.attachments().size() == 1 && !marked.body().contains("@item"), "render marker integration preserved");
        Fixture bounds = new Fixture();
        UUID teacher = bounds.add("teacher");
        var limitedGroup = bounds.directory.create(teacher, UUID.randomUUID(), "conversation", "Bounded");
        for (int i = 0; i < 63; i++) limitedGroup = bounds.change(teacher, limitedGroup, "add", bounds.add("member" + i));
        UUID excess = bounds.add("excess");
        var fullGroup = limitedGroup;
        reject(() -> bounds.change(teacher, fullGroup, "add", excess), "member expansion bounded");
        reject(() -> bounds.directory.expand(teacher, AntmailRecipients.parse("excess@antmail.com,".repeat(64) + "teacher@antmail.com"), bounds.accounts()), "selection count bounded");
        var manager = fullGroup.members().stream().filter(id -> !id.equals(teacher)).findFirst().orElseThrow();
        var promoted = bounds.change(teacher, fullGroup, "promote", manager);
        var managed = bounds.change(manager, promoted, "add", manager);
        reject(() -> bounds.change(manager, managed, "transfer", manager), "manager cannot take ownership");
        var transferred = bounds.change(teacher, managed, "transfer", manager);
        check(transferred.owner().equals(manager), "owner can explicitly transfer ownership");
        for (int i = 0; i < 64; i++) {
            UUID id = bounds.add("contact" + i);
            bounds.directory.contact(teacher, id, "Classmate " + i, false, bounds.accounts());
        }
        var contactPage = bounds.directory.page(teacher, "contacts", "", 0, bounds.accounts());
        check(contactPage.people().size() == 12 && contactPage.more(), "directory pagination bounded");
        var compact = bounds.directory.page(manager, "conversation", "", 0, bounds.accounts()).lists().get(0);
        check(compact.members().isEmpty() && compact.memberCount() == 64, "list pages omit full memberships");
        var world = AntmailServerData.create();
        for (int i = 0; i < 6; i++) {
            UUID owner = UUID.randomUUID();
            for (UUID target : bounds.accounts.keySet()) world.directory().contact(owner, target, "A long private alias with unicode ☆☆☆☆☆☆☆☆☆☆☆", false, bounds.accounts());
        }
        var savedWorld = world.save(new CompoundTag(), null);
        var restoredWorld = AntmailServerData.load(AntmailWire.decodeTag(AntmailWire.encodeTag(savedWorld)), null);
        check(restoredWorld.directory().save().equals(world.directory().save()), "large directory survives NBT storage and compression");
        var damaged = com.google.gson.JsonParser.parseString(bounds.directory.save()).getAsJsonObject();
        damaged.getAsJsonArray("entries").add(new com.google.gson.JsonObject());
        check(AntmailDirectory.load(damaged.toString()).view(manager, transferred.id()).memberCount() == 64, "malformed directory rows preserve valid groups");
        damaged.remove("contacts");
        check(AntmailDirectory.load(damaged.toString()).view(manager, transferred.id()).memberCount() == 64, "missing optional directory sections are safe");
        Fixture audit = new Fixture();
        UUID owner = audit.add("owner"), member = audit.add("member"), other = audit.add("other");
        var privateList = audit.directory.create(owner, UUID.randomUUID(), "personal", "Private");
        privateList = audit.change(owner, privateList, "add", owner);
        privateList = audit.change(owner, privateList, "remove", owner);
        check(privateList.members().isEmpty(), "personal owner need not be a recipient");
        audit.directory.contact(owner, member, "Alias", false, audit.accounts());
        var optional = com.google.gson.JsonParser.parseString(audit.directory.save()).getAsJsonObject();
        optional.getAsJsonArray("contacts").get(0).getAsJsonObject().remove("alias");
        check(AntmailDirectory.load(optional.toString()).page(owner, "contacts", "", 0, audit.accounts()).people().get(0).alias().isEmpty(), "missing optional contact alias defaults empty");
        optional.getAsJsonArray("contacts").get(0).getAsJsonObject().add("alias", com.google.gson.JsonNull.INSTANCE);
        check(AntmailDirectory.load(optional.toString()).page(owner, "contacts", "", 0, audit.accounts()).people().size() == 1, "null optional contact alias keeps contact");
        for (int i = 0; i < AntmailDirectory.MAX_LISTS; i++) audit.directory.create(member, UUID.randomUUID(), "personal", "Owned " + i);
        var transfer = audit.directory.create(owner, UUID.randomUUID(), "conversation", "Transfer");
        transfer = audit.change(owner, transfer, "add", member);
        var transferFull = transfer;
        reject(() -> audit.change(owner, transferFull, "transfer", member), "transfers respect target ownership limit");
        for (String interrupted : List.of("member@antmail.com,", "personal:")) {
            var draft = new AntmailDraft(null, audit.address(owner), null, "Unfinished", "Keep this work", files, interrupted);
            var restored = AntmailDraft.fromTag(draft.toTag());
            check(restored.recipients().equals(interrupted) && restored.body().equals("Keep this work"), "interrupted recipient text is preserved in drafts");
        }
        audit.notifyFailure = true;
        UUID notificationId = UUID.randomUUID();
        audit.mail(owner).saveDraft(new AntmailDraft(notificationId, audit.address(owner), null, "Notify", "Body", List.of(), "member@antmail.com,other@antmail.com"));
        var notifySend = AntmailDispatch.send(audit, owner, audit.address(owner), notificationId, "member@antmail.com,other@antmail.com", "Notify", "Body", List.of(), count -> true);
        check(notifySend.batch().failedCount() == 0 && audit.notifications == 2, "notification failure does not interrupt fanout");
        check(audit.mail(owner).findDraft(notificationId) == null, "recorded send consumes draft despite notification failure");
        check(audit.touches.getOrDefault(audit.address(owner), 0) == 1, "sender index is touched once per fanout");
        var payload = audit.send(owner, "member@antmail.com", files);
        var persisted = audit.mail(owner).toTag();
        var journalRows = persisted.getList("Batches", 10);
        CompoundTag journalReceipt = null;
        for (int i = 0; i < journalRows.size(); i++) if (journalRows.getCompound(i).getString("Id").equals(payload.id().toString())) journalReceipt = journalRows.getCompound(i);
        check(journalReceipt != null && journalReceipt.getString("Body").isEmpty() && journalReceipt.getList("Attachments", 10).isEmpty(), "completed journal omits payload");
        check(journalReceipt != null && AntmailBatch.load(journalReceipt.getString("Batch")).deliveries().isEmpty(), "completed journal uses constant-size receipt");
        audit.restart();
        check(audit.mail(owner).findSent(payload.id()).attachments().size() == 2 && audit.mail(owner).findSent(payload.id()).body().equals("Body"), "visible completed messages retain payload after restart");
        int receivedBeforeDelete = audit.mail(member).inboxSize();
        audit.mail(owner).removeSent(payload.id());
        check(audit.mail(owner).batch(payload.id()).body().isEmpty() && audit.mail(owner).batch(payload.id()).attachments().isEmpty(), "permanent deletion releases journal payload in memory");
        audit.restart();
        AntmailDispatch.send(audit, owner, audit.address(owner), payload.id(), "other@antmail.com", "Replay", "Replay", files, count -> true);
        audit.retry(owner, payload.id());
        check(audit.mail(member).inboxSize() == receivedBeforeDelete, "compact receipt prevents resend and repayment");
        audit.full(member);
        var restoredOutgoing = audit.send(owner, "member@antmail.com");
        check(audit.mail(owner).folderOf(restoredOutgoing.id()) == 1, "outgoing source folder is Sent");
        audit.mail(owner).moveToArchive(restoredOutgoing.id(), true);
        check(audit.mail(owner).folderOf(restoredOutgoing.id()) == 3, "conversation source follows Archive");
        audit.mail(owner).moveToTrash(restoredOutgoing.id(), 3);
        check(audit.mail(owner).folderOf(restoredOutgoing.id()) == 4, "conversation source follows Trash");
        audit.mail(owner).restoreFromTrash(restoredOutgoing.id(), 0); audit.restart(); audit.empty(member); audit.retry(owner, restoredOutgoing.id());
        check(audit.mail(owner).findInbox(restoredOutgoing.id()) == audit.mail(owner).batch(restoredOutgoing.id()), "restored outgoing Inbox row relinks to journal");
        check(audit.mail(owner).findInbox(restoredOutgoing.id()).batch().failedCount() == 0 && audit.mail(owner).findInbox(restoredOutgoing.id()).deliveryStatus().equals("DELIVERED"), "restored Inbox delivery results update on retry");
        audit.full(member);
        var cancelled = audit.send(owner, "member@antmail.com", files);
        audit.mail(owner).moveToTrash(cancelled.id(), 1); audit.mail(owner).removeTrash(cancelled.id()); audit.empty(member); audit.restart();
        check(audit.mail(owner).batch(cancelled.id()).batch().cancelled() && audit.mail(owner).batch(cancelled.id()).attachments().isEmpty(), "deleting failed send cancels payload and keeps receipt");
        reject(() -> audit.retry(owner, cancelled.id()), "deleted sends cannot be retried");
        reject(() -> AntmailDispatch.send(audit, owner, audit.address(owner), cancelled.id(), "member@antmail.com", "Replay", "Replay", files, count -> true), "cancelled replay reports a deleted send");
        check(audit.mail(member).inboxSize() == 0, "deleted send replay cannot deliver again");
        audit.full(member);
        var oldOrphan = audit.send(owner, "member@antmail.com", files);
        var oldSave = audit.mail(owner).toTag(); oldSave.put("Sent", new net.minecraft.nbt.ListTag());
        AntmailMessage migratedOrphan = AntmailMailbox.fromTag(oldSave).batch(oldOrphan.id());
        check(migratedOrphan.batch().cancelled() && migratedOrphan.body().isEmpty() && migratedOrphan.attachments().isEmpty(), "upgrade cancels and compacts old orphaned failures");
        Fixture leaving = new Fixture();
        UUID host = leaving.add("host"), guest = leaving.add("guest"), helper = leaving.add("helper");
        var club = leaving.directory.create(host, UUID.randomUUID(), "conversation", "Club");
        club = leaving.change(host, club, "add", guest);
        club = leaving.change(host, club, "add", helper);
        club = leaving.change(host, club, "promote", helper);
        UUID clubId = club.id();
        var left = leaving.directory.change(guest, clubId, 0, "leave", "", leaving.accounts());
        check(!left.members().contains(guest) && left.role().isEmpty(), "members can leave a group without a current revision");
        reject(() -> leaving.directory.view(guest, clubId), "left members lose access");
        var afterLeave = leaving.directory.view(host, clubId);
        reject(() -> leaving.change(host, afterLeave, "add", guest), "managers cannot re-add a member who left");
        int guestInbox = leaving.mail(guest).inbox().size();
        leaving.send(host, "conversation:" + clubId);
        check(leaving.mail(guest).inbox().size() == guestInbox, "left members receive no new group mail");
        reject(() -> leaving.directory.change(host, clubId, 0, "leave", "", leaving.accounts()), "owners cannot leave");
        reject(() -> leaving.directory.change(guest, clubId, 0, "leave", "", leaving.accounts()), "leaving twice is rejected");
        var restoredLeave = AntmailDirectory.load(leaving.directory.save());
        reject(() -> restoredLeave.change(host, clubId, restoredLeave.view(host, clubId).revision(), "add", guest.toString(), leaving.accounts()), "declined members survive save and load");
        var legacyLeave = com.google.gson.JsonParser.parseString(leaving.directory.save()).getAsJsonObject();
        for (var entry : legacyLeave.getAsJsonArray("entries")) entry.getAsJsonObject().remove("declined");
        var legacyDirectory = AntmailDirectory.load(legacyLeave.toString());
        check(legacyDirectory.view(host, clubId).memberCount() == 2, "directories saved before leave support still load");
        var helperView = leaving.directory.view(helper, clubId);
        check(helperView.members().size() == 2 && AntmailDirectory.MANAGER.equals(helperView.role()) && helperView.canManage(), "group views carry the viewer role");
        var news = leaving.directory.create(host, UUID.randomUUID(), "mailing", "News");
        news = leaving.change(host, news, "add", guest);
        news = leaving.change(host, news, "add", helper);
        UUID newsId = news.id();
        var memberView = leaving.directory.view(guest, newsId);
        check(memberView.members().isEmpty() && memberView.managers().isEmpty() && memberView.memberCount() == 3
                && AntmailDirectory.MEMBER.equals(memberView.role()) && !memberView.canManage(), "mailing roster hidden from plain members");
        var ownerView = leaving.directory.view(host, newsId);
        check(ownerView.members().size() == 3 && ownerView.isOwner(), "owners see the mailing roster");
        check(AntmailDirectory.MEMBER.equals(leaving.directory.page(guest, "mailing", "", 0, leaving.accounts()).lists().get(0).role()), "page rows carry the viewer role");
        check(leaving.directory.change(guest, newsId, 0, "leave", "", leaving.accounts()).memberCount() == 2, "members can leave mailing lists");
        var personalList = leaving.directory.create(host, UUID.randomUUID(), "personal", "Mine");
        personalList = leaving.change(host, personalList, "add", guest);
        UUID mineId = personalList.id();
        reject(() -> leaving.directory.change(guest, mineId, 0, "leave", "", leaving.accounts()), "personal lists cannot be left");
        int helperSent = leaving.mail(helper).sent().size();
        reject(() -> AntmailDispatch.send(leaving, helper, leaving.address(helper), UUID.randomUUID(), "conversation:" + clubId, "Title", "Body", List.of(), count -> false), "rate budget enforced");
        check(leaving.mail(helper).sent().size() == helperSent, "rate-limited sends record nothing");
        Fixture squad = new Fixture();
        UUID lead = squad.add("lead"), scout = squad.add("scout"), medic = squad.add("medic"), outsider = squad.add("outsider");
        UUID teamId = squad.team(lead, scout, medic);
        var teamSend = squad.send(lead, "team:" + teamId);
        check(teamSend.batch().deliveries().size() == 2 && teamSend.batch().failedCount() == 0, "team mail reaches every other member");
        check(AntmailDirectory.CONVERSATION_TEAM.equals(teamSend.batch().conversationKind()), "team sends record their conversation kind");
        var teamCopy = squad.mail(scout).inbox().stream().filter(message -> message.logicalSend().equals(teamSend.id())).findFirst().orElseThrow();
        check(teamId.equals(teamCopy.conversationId()) && ("team:" + teamId).equals(teamCopy.replyAllToken()), "team copies are threaded and reply to the team");
        check(("team:" + teamId).equals(AntmailMessage.fromTag(teamCopy.toTag()).replyAllToken()), "team thread kind survives storage");
        check(!squad.mail(scout).conversation(teamId).isEmpty(), "team thread readable in conversation view");
        check(squad.directory.currentConversation(scout, teamId, squad.teams()) && !squad.directory.currentConversation(outsider, teamId, squad.teams()), "only current members hold a live team thread");
        var teamSelection = AntmailRecipients.parse("team:" + teamId).get(0);
        check(!squad.directory.label(lead, teamSelection, squad.accounts(), squad.teams()).isBlank() && squad.directory.label(outsider, teamSelection, squad.accounts(), squad.teams()).isBlank(), "team labels only resolve for members");
        reject(() -> squad.send(outsider, "team:" + teamId), "non-members cannot mail a team");
        reject(() -> squad.send(lead, "team:" + teamId + ", outsider@antmail.com"), "a team must be the only selection");
        reject(() -> squad.send(lead, "team:" + UUID.randomUUID()), "unknown teams are rejected");
        reject(() -> AntmailDispatch.send(squad, lead, squad.address(lead), UUID.randomUUID(), "team:" + teamId, "Pay", "Coins", List.of(new AntmailAttachment.Antcoins(5)), count -> true), "Antcoins cannot be sent to a team");
        squad.full(medic);
        var teamPartial = squad.send(scout, "team:" + teamId);
        check(teamPartial.batch().failedCount() == 1, "a full mailbox fails one team recipient");
        squad.empty(medic);
        squad.leaveTeam(teamId, medic);
        squad.retry(scout, teamPartial.id());
        java.util.function.Supplier<String> medicStatus = () -> squad.mail(scout).batch(teamPartial.id()).batch().deliveries().stream().filter(row -> row.account().equals(medic)).findFirst().orElseThrow().status();
        check(medicStatus.get().equals("MEMBERSHIP_CHANGED"), "retries skip members who left the team");
        squad.joinTeam(teamId, medic);
        squad.retry(scout, teamPartial.id());
        check(medicStatus.get().equals("MEMBERSHIP_CHANGED"), "rejoining the team does not deliver older sends");
        squad.leaveTeam(teamId, scout);
        reject(() -> squad.retry(scout, teamPartial.id()), "senders who left cannot retry team mail");
        check(!squad.directory.currentConversation(scout, teamId, squad.teams()), "leaving a team turns its thread into history");
        squad.disband(teamId);
        reject(() -> squad.send(lead, "team:" + teamId), "disbanded teams cannot be mailed");
        check(!squad.directory.currentConversation(lead, teamId, squad.teams()), "disbanded team threads become history");
        UUID big = squad.add("big");
        UUID[] crowd = new UUID[AntmailRecipients.MAX_RECIPIENTS];
        for (int i = 0; i < crowd.length; i++) crowd[i] = squad.add("crowd" + i);
        UUID bigTeam = squad.team(big, crowd);
        reject(() -> squad.send(big, "team:" + bigTeam), "oversized legacy teams fail clearly instead of truncating");
        var groupBatch = com.google.gson.JsonParser.parseString(squad.send(lead, "outsider@antmail.com").batch().save()).getAsJsonObject();
        groupBatch.addProperty("conversation", UUID.randomUUID().toString());
        groupBatch.remove("conversationKind");
        check(AntmailDirectory.CONVERSATION_GROUP.equals(AntmailBatch.load(groupBatch.toString()).conversationKind()), "batches saved before team mail are groups");
        groupBatch.addProperty("conversationName", "x".repeat(200));
        check(AntmailBatch.load(groupBatch.toString()).conversationName() == null, "oversized stored conversation names are dropped");
        var clubCopy = leaving.mail(helper).inbox().stream().filter(message -> clubId.equals(message.conversationId())).findFirst().orElseThrow();
        check("Club".equals(clubCopy.conversationName()) && "Club".equals(AntmailMessage.fromTag(clubCopy.toTag()).conversationName()), "group copies remember the group name");
        check("Owner".equals(teamCopy.conversationName()), "team copies remember the team owner");
        check(leaving.directory.page(helper, "picker", "", 0, leaving.accounts()).lists().stream().anyMatch(view -> view.id().equals(clubId)), "groups you belong to appear in the picker");
        check(leaving.directory.page(guest, "picker", "", 0, leaving.accounts()).lists().stream().noneMatch(view -> view.id().equals(clubId)), "groups you left are not offered in the picker");
        java.util.Map<UUID, Integer> clubFolders = new java.util.HashMap<>();
        var clubThread = leaving.mail(helper).conversation(clubId, clubFolders);
        check(!clubThread.isEmpty() && clubThread.stream().allMatch(message -> clubFolders.get(message.id()) == leaving.mail(helper).folderOf(message.id())), "thread folders come from the same pass as the thread");
        check("Club".equals(leaving.mail(helper).conversationHeads().get(clubId).conversationName()), "history heads carry the conversation name");
        Fixture indexed = new Fixture();
        UUID ownerA = indexed.add("ownera"), ownerB = indexed.add("ownerb");
        var groupA = indexed.directory.create(ownerA, UUID.randomUUID(), "conversation", "A group");
        indexed.directory.create(ownerB, UUID.randomUUID(), "conversation", "B group");
        java.util.function.Function<UUID, Integer> groupCount = viewer -> indexed.directory.page(viewer, "conversation", "", 0, indexed.accounts()).lists().size();
        check(groupCount.apply(ownerA) == 1 && groupCount.apply(ownerB) == 1, "pages only list the viewer's own groups");
        groupA = indexed.change(ownerA, groupA, "add", ownerB);
        check(groupCount.apply(ownerB) == 2, "the list index picks up new memberships");
        indexed.change(ownerA, groupA, "delete", "");
        check(groupCount.apply(ownerB) == 1 && groupCount.apply(ownerA) == 0, "the list index drops deleted groups");
        check(AntmailDirectory.load(indexed.directory.save()).page(ownerB, "conversation", "", 0, indexed.accounts()).lists().size() == 1, "the list index rebuilds after load");
        groupBatch.addProperty("conversationKind", "bogus");
        reject(() -> AntmailBatch.load(groupBatch.toString()), "unknown conversation kinds are rejected");
        AntmailClientState.bindAccount("alice");
        AntmailClientState.update(new AntmailAnternetResultPayload(0, f.address(bob).fullAddress(), "", "directory", page.toString(), 1));
        check(AntmailClientState.directory() == null, "old account response rejected");
        AntmailClientState.update(new AntmailAnternetResultPayload(0, f.address(alice).fullAddress(), "", "directory", page.toString(), 1));
        check(AntmailClientState.directory() != null, "active account response accepted");
        AntmailClientState.bindAccount("bob");
        check(AntmailClientState.directory() == null, "account switching clears private state");
        AntmailClientState.clear();
        AntmailClientState.update(new AntmailAnternetResultPayload(0, f.address(bob).fullAddress(), "", "directory", page.toString(), 1));
        check(AntmailClientState.directory() == null, "late response after logout rejected");
        teamPersistence();
        System.out.println("AntmailAudienceTest passed: " + assertions + " assertions");
    }
    /** Team membership epochs and the team size cap, exercised on the real world data with in-memory account shards. */
    private static void teamPersistence() throws Exception {
        var data = new com.craisinlord.antos.content.computer.ComputerWorkspaceData();
        UUID captain = worldAccount(data, "captain"), first = worldAccount(data, "first"), second = worldAccount(data, "second");
        check(data.createTeam(captain), "team created for persistence test");
        UUID teamId = data.teamIdOf(captain);
        joinWorldTeam(data, captain, first, "first");
        joinWorldTeam(data, captain, second, "second");
        var before = data.teamMailAudience(teamId);
        check(before != null && before.epochs().keySet().equals(java.util.Set.of(captain, first, second)), "every team member has a membership epoch");
        check(teamId.equals(data.teamIdOf(first)) && teamId.equals(data.teamIdOf(second)), "members resolve to their team");
        var restored = reloadWorld(saveWorld(data));
        check(restored.teamMailAudience(teamId).epochs().equals(before.epochs()), "membership epochs survive save and load");
        CompoundTag legacy = saveWorld(data);
        // Version-neutral NBT access: ListTag is a List<Tag> in every supported Minecraft version.
        for (var row : (net.minecraft.nbt.ListTag) legacy.get("Teams")) ((CompoundTag) row).remove("MemberEpochs");
        var migrated = reloadWorld(legacy).teamMailAudience(teamId);
        check(migrated.epochs().keySet().equals(before.epochs().keySet()) && migrated.epochs().values().stream().allMatch(java.util.Objects::nonNull), "teams saved before epochs get one per member");
        check(data.leaveTeam(second) && !data.teamMailAudience(teamId).epochs().containsKey(second), "leaving drops the membership epoch");
        joinWorldTeam(data, captain, second, "second");
        var after = data.teamMailAudience(teamId).epochs();
        check(!after.get(second).equals(before.epochs().get(second)) && after.get(first).equals(before.epochs().get(first)), "rejoining issues a new epoch and leaves others alone");
        check(data.teamMailAudience(UUID.randomUUID()) == null && data.teamIdOf(UUID.randomUUID()) == null, "unknown teams and accounts resolve to nothing");
        check(data.disbandTeam(captain) && data.teamMailAudience(teamId) == null && data.teamIdOf(first) == null, "disbanded teams disappear from mail");
        var crowded = new com.craisinlord.antos.content.computer.ComputerWorkspaceData();
        UUID boss = worldAccount(crowded, "boss");
        crowded.createTeam(boss);
        for (int i = 0; i < com.craisinlord.antos.content.computer.ComputerWorkspaceData.MAX_TEAM_MEMBERS - 2; i++) joinWorldTeam(crowded, boss, worldAccount(crowded, "crew" + i), "crew" + i);
        UUID late = worldAccount(crowded, "late"), last = worldAccount(crowded, "last"), extra = worldAccount(crowded, "extra");
        var pending = crowded.inviteToTeam(boss, "late", 0);
        check(pending != null, "invites are allowed below the cap");
        joinWorldTeam(crowded, boss, last, "last");
        check(crowded.teamMailAudience(crowded.teamIdOf(boss)).epochs().size() == com.craisinlord.antos.content.computer.ComputerWorkspaceData.MAX_TEAM_MEMBERS, "teams fill to the cap");
        check(crowded.inviteToTeam(boss, "extra", 0) == null, "full teams refuse new invites");
        check(!crowded.acceptTeamInvite(late, pending.id()) && crowded.teamIdOf(late) == null, "invites sent before the team filled cannot overfill it");
    }
    private static UUID worldAccount(com.craisinlord.antos.content.computer.ComputerWorkspaceData data, String name) {
        var result = data.completeAccountCreation(name, new com.craisinlord.antos.content.computer.ComputerWorkspaceData.PasswordHash("c2FsdA==", "dmVyaWZpZXI=", 100_000));
        if (!result.successful()) throw new AssertionError("test account " + name + " not created: " + result.failure());
        return result.account().accountId();
    }
    private static void joinWorldTeam(com.craisinlord.antos.content.computer.ComputerWorkspaceData data, UUID owner, UUID member, String name) {
        var invite = data.inviteToTeam(owner, name, 0);
        if (invite == null || !data.acceptTeamInvite(member, invite.id())) throw new AssertionError(name + " could not join the team");
    }
    /** Calls the version's save method, whatever its extra parameters are. */
    private static CompoundTag saveWorld(com.craisinlord.antos.content.computer.ComputerWorkspaceData data) throws Exception {
        for (var method : data.getClass().getMethods()) {
            if (!method.getName().equals("save") || method.getParameterCount() == 0 || method.getParameterTypes()[0] != CompoundTag.class || method.getReturnType() != CompoundTag.class) continue;
            Object[] arguments = new Object[method.getParameterCount()];
            arguments[0] = new CompoundTag();
            return (CompoundTag) method.invoke(data, arguments);
        }
        throw new AssertionError("no save(CompoundTag, ...) method");
    }
    /** Calls the version's private static load method with the fewest parameters. */
    private static com.craisinlord.antos.content.computer.ComputerWorkspaceData reloadWorld(CompoundTag tag) throws Exception {
        java.lang.reflect.Method best = null;
        for (var method : com.craisinlord.antos.content.computer.ComputerWorkspaceData.class.getDeclaredMethods()) {
            if (!method.getName().equals("load") || !java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.getParameterCount() == 0
                    || method.getParameterTypes()[0] != CompoundTag.class || method.getReturnType() != com.craisinlord.antos.content.computer.ComputerWorkspaceData.class) continue;
            if (best == null || method.getParameterCount() < best.getParameterCount()) best = method;
        }
        if (best == null) throw new AssertionError("no static load(CompoundTag, ...) method");
        best.setAccessible(true);
        Object[] arguments = new Object[best.getParameterCount()];
        arguments[0] = tag.copy();
        return (com.craisinlord.antos.content.computer.ComputerWorkspaceData) best.invoke(null, arguments);
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void reject(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException exception) { rejected = true; }
        check(rejected, message);
    }
    private static final class Fixture implements AntmailDispatch.Access {
        AntmailDirectory directory = new AntmailDirectory();
        final Map<UUID, String> accounts = new LinkedHashMap<>();
        final Map<AntmailAddress, AntmailMailbox> mailboxes = new LinkedHashMap<>();
        final Map<UUID, Long> balance = new LinkedHashMap<>();
        long time;
        boolean notifyFailure;
        int notifications;
        final Map<AntmailAddress, Integer> touches = new LinkedHashMap<>();
        UUID add(String name) { UUID id = UUID.randomUUID(); accounts.put(id, AntmailAddress.ofUsername(name).fullAddress()); balance.put(id, 0L); return id; }
        AntmailAddress address(UUID id) { return AntmailAddress.parse(accounts.get(id)); }
        AntmailMailbox mail(UUID id) { return mailbox(address(id)); }
        public AntmailDirectory directory() { return directory; }
        public AntmailDirectory.Accounts accounts() {
            return new AntmailDirectory.Accounts() {
                public UUID resolve(String value) {
                    try { UUID id = UUID.fromString(value); return accounts.containsKey(id) ? id : null; } catch (IllegalArgumentException ignored) { }
                    return accounts.entrySet().stream().filter(row -> row.getValue().equals(value)).map(Map.Entry::getKey).findFirst().orElse(null);
                }
                public String address(UUID id) { return accounts.get(id); }
            };
        }
        public AntmailMailbox mailbox(AntmailAddress address) { return mailboxes.computeIfAbsent(address, AntmailMailbox::new); }
        public long now() { return ++time; }
        public boolean coinsEnabled() { return true; }
        public boolean debit(UUID id, long amount) { long old = balance.get(id); if (old < amount) return false; balance.put(id, old - amount); return true; }
        public void credit(UUID id, long amount) { balance.put(id, balance.get(id) + amount); }
        public void touch(AntmailAddress address) { touches.merge(address, 1, Integer::sum); }
        public void notify(AntmailMessage message) { notifications++; if (notifyFailure) { notifyFailure = false; throw new IllegalStateException("injected notification failure"); } }
        AntmailDirectory.View change(UUID actor, AntmailDirectory.View view, String operation, UUID value) { return change(actor, view, operation, value.toString()); }
        AntmailDirectory.View change(UUID actor, AntmailDirectory.View view, String operation, String value) { return directory.change(actor, view.id(), view.revision(), operation, value, accounts()); }
        AntmailMessage send(UUID actor, String selections) { return send(actor, selections, List.of()); }
        AntmailMessage send(UUID actor, String selections, List<AntmailAttachment> attachments) { return AntmailDispatch.send(this, actor, address(actor), UUID.randomUUID(), selections, "Title", "Body", attachments, count -> true); }
        void retry(UUID actor, UUID id) { AntmailDispatch.retry(this, actor, address(actor), id, count -> true); }
        /** In-memory stand-in for AntOS teams: team ID to (member to membership epoch). */
        final Map<UUID, Map<UUID, UUID>> teamEpochs = new LinkedHashMap<>();
        final Map<UUID, UUID> teamOwners = new LinkedHashMap<>();
        UUID team(UUID owner, UUID... members) {
            UUID id = UUID.randomUUID();
            teamEpochs.put(id, new LinkedHashMap<>()); teamOwners.put(id, owner);
            joinTeam(id, owner); for (UUID member : members) joinTeam(id, member);
            return id;
        }
        void joinTeam(UUID team, UUID member) { teamEpochs.get(team).put(member, UUID.randomUUID()); }
        void leaveTeam(UUID team, UUID member) { teamEpochs.get(team).remove(member); }
        void disband(UUID team) { teamEpochs.remove(team); teamOwners.remove(team); }
        public AntmailDirectory.Teams teams() {
            return new AntmailDirectory.Teams() {
                public UUID teamOf(UUID account) { return teamEpochs.entrySet().stream().filter(row -> row.getValue().containsKey(account)).map(Map.Entry::getKey).findFirst().orElse(null); }
                public AntmailDirectory.TeamView team(UUID id) {
                    var epochs = teamEpochs.get(id);
                    return epochs == null ? null : new AntmailDirectory.TeamView(id, "Owner", teamOwners.get(id), Map.copyOf(epochs));
                }
            };
        }
        void full(UUID id) {
            while (mail(id).inboxSize() < AntmailValidation.MAX_MAILBOX_MESSAGES) mail(id).addIncoming(AntmailMessage.create(address(id), address(id), "Filler", "Body", now(), List.of()));
        }
        void empty(UUID id) { for (var message : mail(id).inbox()) mail(id).removeInbox(message.id()); }
        void restart() {
            directory = AntmailDirectory.load(directory.save());
            Map<AntmailAddress, AntmailMailbox> restored = new LinkedHashMap<>();
            mailboxes.forEach((address, mailbox) -> restored.put(address, AntmailMailbox.fromTag(AntmailWire.decodeTag(AntmailWire.encodeTag(mailbox.toTag())))));
            mailboxes.clear(); mailboxes.putAll(restored);
        }
    }
}
