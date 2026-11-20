package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailBatch;
import com.craisinlord.antos.content.antmail.AntmailDirectory;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailWire;
import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.network.AntmailAnternetPayload;
import com.craisinlord.antos.content.network.AntmailAnternetResultPayload;
import com.craisinlord.antos.content.network.AntmailNetworking;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AntmailPanelTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        AntmailClientState.clear();
        List<AntmailAnternetPayload> requests = new ArrayList<>();
        AntmailNetworking.setSender(payload -> requests.add((AntmailAnternetPayload) payload));
        check(AntmailDirectoryPanel.memberRows(151, 8) == 2, "default owner view shows two member rows");
        check(AntmailDirectoryPanel.memberRows(132, 8) >= 1, "smaller owner view still exposes a member");
        check(AntmailDirectoryPanel.memberRows(80, 8) == 0, "tiny view never draws outside viewport");
        AntmailDirectoryPanel picker = new AntmailDirectoryPanel();
        picker.open("picker", value -> { }, id -> { }, () -> { });
        set(picker, "field", 1);
        for (char character : "alice".toCharArray()) picker.type(character);
        check(requests.size() == 1, "typing coalesces picker requests");
        set(picker, "searchAt", System.nanoTime() - 1); picker.tick();
        check(requests.size() == 2 && requests.get(1).fifth().equals("alice"), "debounce sends only final query");
        picker.key(GLFW.GLFW_KEY_BACKSPACE, 0);
        check(requests.size() == 2, "backspace is debounced");
        picker.key(GLFW.GLFW_KEY_ENTER, 0);
        check(requests.size() == 3 && requests.get(2).fifth().equals("alic"), "Enter flushes search immediately");
        set(picker, "field", 2); set(picker, "value", "bob"); set(picker, "cursor", 3);
        picker.key(GLFW.GLFW_KEY_BACKSPACE, 0);
        check(requests.size() == 3 && (long) get(picker, "searchAt") == 0, "unrelated field edits never query recipients");
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        directory("alice", alice, "Alice");
        Method sync = AntmailDirectoryPanel.class.getDeclaredMethod("sync"); sync.setAccessible(true); sync.invoke(picker);
        check(get(picker, "decodedPage") == null, "stale query response is ignored");
        directory("alic", alice, "Alice"); sync.invoke(picker);
        Object page = get(picker, "decodedPage");
        check(page != null, "matching query response is accepted");
        sync.invoke(picker);
        check(get(picker, "decodedPage") == page, "unchanged directory page is decoded once");
        directory("bob", bob, "Bob");
        String summary = AntmailRecipientPicker.display(alice + "," + bob + ",carol@antmail.com", 0, false, false, 24, String::length);
        check(AntmailClientState.recipientLabel(alice.toString()).equals("Alice"), "recipient labels survive page replacement");
        check(summary.endsWith(" (3)") && summary.length() <= 24 && !summary.contains(alice.toString()), "recipient count remains visible beside readable chips");
        String editing = AntmailRecipientPicker.viewport("member@antmail.com,".repeat(8), 144, true, 24, String::length);
        check(editing.contains("|") && editing.length() <= 24, "recipient editor follows caret horizontally");
        AntmailConversationPanel conversation = new AntmailConversationPanel();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        conversation.open(first);
        conversation(first, 0, true, 1);
        AntmailMessage selected = conversation.selected();
        check(selected != null && selected.sourceFolder() == 1, "conversation row retains outgoing folder");
        check(conversation.selected() == selected, "conversation messages are decoded once per response");
        conversation.open(second);
        check(conversation.selected() == null && conversation.find(selected.id()) == null, "switching conversations clears selectable old messages");
        conversation(second, 0, true, 3);
        check(conversation.selected().sourceFolder() == 3, "archived conversation row retains source folder");
        conversation.key(GLFW.GLFW_KEY_PAGE_DOWN, 0);
        check(conversation.selected() == null && requests.get(requests.size() - 1).number() == 1, "keyboard next page clears old rows and requests page one");
        conversation(second, 1, false, 4);
        check(conversation.selected().sourceFolder() == 4, "trash conversation page retains provenance");
        conversation.key(GLFW.GLFW_KEY_PAGE_UP, 0);
        check(conversation.selected() == null && requests.get(requests.size() - 1).number() == 0, "keyboard previous page clears old rows");
        conversation(second, 0, false, 0);
        check("Group".equals(conversation.latestSubject()) && conversation.replyToken().equals("conversation:" + second), "group threads reply to the group on topic");
        conversation.open(second, "team:" + second);
        check(conversation.replyToken().equals("team:" + second), "team threads reply to the team");
        String team = "team:" + UUID.randomUUID();
        check(AntmailApp.addRecipient("a@antmail.com", team).equals(team), "picking a team replaces the recipient line");
        check(AntmailApp.addRecipient(team, "b@antmail.com").equals("b@antmail.com"), "picking a person after a team replaces it");
        check(AntmailApp.addRecipient("a@antmail.com", "b@antmail.com").equals("a@antmail.com, b@antmail.com"), "people still accumulate");
        check(AntmailApp.addRecipient("", "conversation:" + second).equals("conversation:" + second), "groups start a fresh recipient line");
        AntmailApp app = new AntmailApp();
        app.key(null, GLFW.GLFW_KEY_F6, 0, 0);
        check(app.state.mode.equals("directory") && requests.get(requests.size() - 1).fourth().equals("contacts"), "keyboard opens contacts");
        app.key(null, GLFW.GLFW_KEY_F7, 0, 0);
        check(requests.get(requests.size() - 1).fourth().equals("conversation"), "keyboard opens groups");
        app.state.mode = "compose"; app.key(null, GLFW.GLFW_KEY_F4, 0, 0);
        check(requests.get(requests.size() - 1).fourth().equals("picker"), "keyboard opens compose picker");
        app.state.originMode = "conversation";
        Method source = AntmailApp.class.getDeclaredMethod("messageSourceMode"); source.setAccessible(true);
        for (int folder : List.of(0, 1, 3, 4)) {
            app.state.messageFolder = folder;
            check(source.invoke(app).equals(switch (folder) { case 1 -> "sent"; case 3 -> "archive"; case 4 -> "trash"; default -> "inbox"; }), "conversation actions target actual folder " + folder);
        }
        CompoundTag freshDetail = selected.toTag(); freshDetail.putInt("SourceFolder", 4);
        AntmailClientState.update(new AntmailAnternetResultPayload(0, "tester@antmail.com", selected.id().toString(), "message_detail", AntmailWire.encodeTag(freshDetail), 1));
        app.state.messageId = selected.id(); app.state.messageFolder = 1;
        Method detail = AntmailApp.class.getDeclaredMethod("selectedMessage", AntmailAnternetResultPayload.class); detail.setAccessible(true); detail.invoke(app, new Object[] { null });
        check(app.state.messageFolder == 4 && source.invoke(app).equals("trash"), "fresh detail updates stale conversation provenance");
        Method push = AntmailApp.class.getDeclaredMethod("pushUndo", UUID.class, int.class, String.class); push.setAccessible(true);
        push.invoke(app, selected.id(), 1, "RESTORE_ARCHIVE"); app.undoLast();
        check(requests.get(requests.size() - 1).action() == AntmailAnternetPayload.ARCHIVE && requests.get(requests.size() - 1).second().equals("true"), "undo outgoing archive restore targets Sent");
        push.invoke(app, selected.id(), 1, "RESTORE_TRASH"); app.undoLast();
        check(requests.get(requests.size() - 1).action() == AntmailAnternetPayload.TRASH && requests.get(requests.size() - 1).second().equals("1"), "undo outgoing trash restore targets Sent");
        UUID sender = UUID.randomUUID(), target = UUID.randomUUID();
        var accounts = new AntmailDirectory.Accounts() {
            public UUID resolve(String reference) { return target; }
            public String address(UUID id) { return "target@antmail.com"; }
        };
        AntmailMessage failed = AntmailMessage.create(AntmailAddress.ofUsername("tester"), AntmailAddress.ofUsername("target"), "Failed", "Body", 1, List.of());
        failed.setBatch(new AntmailBatch(failed.id(), sender, "target@antmail.com", new AntmailDirectory.Audience(List.of(target), null, java.util.Map.of()), accounts));
        failed.batch().outcome(target, "MAILBOX_FULL");
        AntmailDeliveryPanel deliveries = new AntmailDeliveryPanel(); deliveries.open(failed); deliveries.retry();
        AntmailClientState.update(new AntmailAnternetResultPayload(5, "tester@antmail.com", failed.id().toString(), "membership_changed", "", 1));
        AntmailClientState.update(new AntmailAnternetResultPayload(0, "tester@antmail.com", failed.id().toString(), "message_detail", AntmailWire.encodeTag(failed.toTag()), 1));
        deliveries.sync();
        check(AntmailClientState.getOperationResult().detail().equals("membership_changed"), "retry error survives subsequent message detail");
        check(!get(deliveries, "error").toString().isEmpty() && (boolean) get(deliveries, "blocked"), "membership retry rejection has visible feedback");
        int attempts = requests.size(); deliveries.retry();
        check(requests.size() == attempts, "permanently rejected group retry is disabled");
        deliveries.open(failed); deliveries.retry();
        AntmailClientState.update(new AntmailAnternetResultPayload(5, "tester@antmail.com", failed.id().toString(), "rate_limited", "", 1));
        AntmailClientState.update(new AntmailAnternetResultPayload(0, "tester@antmail.com", failed.id().toString(), "message_detail", AntmailWire.encodeTag(failed.toTag()), 1));
        deliveries.sync();
        check(!get(deliveries, "error").toString().isEmpty() && !(boolean) get(deliveries, "blocked"), "rate-limit retry error is visible and remains retryable");
        String groupToken = "conversation:" + second;
        AntmailClientState.rememberRecipient(groupToken, "Old name");
        java.util.function.ToIntFunction<String> measure = String::length;
        String before = AntmailRecipientPicker.display(groupToken, 0, false, false, 200, measure);
        check(AntmailRecipientPicker.display(groupToken, 0, false, false, 200, measure) == before, "an unchanged recipient line reuses its rendering");
        AntmailClientState.rememberRecipient(groupToken, "Renamed group");
        String after = AntmailRecipientPicker.display(groupToken, 0, false, false, 200, measure);
        check(before.contains("Old name") && after.contains("Renamed group"), "recipient chips refresh when a label changes");
        String fitted = AntmailRecipientPicker.display("member@antmail.com,".repeat(6) + "last@antmail.com", 0, false, false, 30, String::length);
        check(fitted.length() <= 30 && fitted.contains("…"), "long recipient lines are fitted with an ellipsis");
        AntmailClientState.clear();
        check(AntmailClientState.recipientLabel(alice.toString()).isEmpty(), "logout clears recipient labels");
        System.out.println("AntmailPanelTest passed: " + assertions + " assertions");
    }
    private static void directory(String query, UUID id, String label) {
        JsonObject root = new JsonObject(), page = new JsonObject(), person = new JsonObject();
        person.addProperty("id", id.toString()); person.addProperty("address", label.toLowerCase() + "@antmail.com"); person.addProperty("alias", label);
        JsonArray people = new JsonArray(); people.add(person);
        page.addProperty("mode", "picker"); page.addProperty("query", query); page.addProperty("page", 0); page.addProperty("revision", 1); page.addProperty("more", false);
        page.add("people", people); page.add("lists", new JsonArray());
        root.add("page", page); root.addProperty("account", UUID.randomUUID().toString()); root.addProperty("operation", "page"); root.addProperty("target", "");
        AntmailClientState.update(new AntmailAnternetResultPayload(0, "tester@antmail.com", "", "directory", root.toString(), 1));
    }
    private static void conversation(UUID id, int page, boolean more, int folder) {
        CompoundTag tag = new CompoundTag(); tag.putString("Conversation", id.toString()); tag.putInt("Page", page); tag.putBoolean("More", more);
        AntmailMessage message = AntmailMessage.create(AntmailAddress.ofUsername("author"), AntmailAddress.ofUsername("tester"), "Group", "Body", 1, List.of());
        message.setConversation(id); CompoundTag row = message.toListTag(); row.putInt("SourceFolder", folder);
        ListTag rows = new ListTag(); rows.add(row); tag.put("Messages", rows);
        AntmailClientState.update(new AntmailAnternetResultPayload(0, "tester@antmail.com", "", "conversation", AntmailWire.encodeTag(tag), 1));
    }
    private static void set(Object object, String name, Object value) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
    private static Object get(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
