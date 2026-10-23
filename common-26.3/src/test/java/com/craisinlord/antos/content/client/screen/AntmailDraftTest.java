package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailDraft;
import com.craisinlord.antos.content.antmail.AntmailMailbox;
import com.craisinlord.antos.content.antmail.AntmailWire;
import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.network.AntmailAnternetPayload;
import com.craisinlord.antos.content.network.AntmailAnternetResultPayload;
import com.craisinlord.antos.content.network.AntmailNetworking;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AntmailDraftTest {
    public static void main(String[] args) throws Exception {
        AntmailClientState.clear();
        List<Object> requests = new ArrayList<>();
        AntmailNetworking.setSender(requests::add);
        AntmailAddress sender = AntmailAddress.ofUsername("tester");
        AntmailMailbox mailbox = new AntmailMailbox(sender);
        AntmailAnternetResultPayload snapshot = new AntmailAnternetResultPayload(0, sender.fullAddress(), "", "state",
                AntmailWire.encodeTag(mailbox.toTag()), 1L);
        AntmailClientState.update(snapshot);
        AntmailApp app = new AntmailApp();
        app.saveDraft();
        check(requests.isEmpty(), "empty composer does not create a draft");
        app.state.recipient = "friend@" + AntmailAddress.DOMAIN;
        app.state.subject = "Subject";
        app.state.body = "First body";
        app.saveDraft();
        UUID draftId = app.state.draftId;
        check(draftId != null, "new draft retains its ID");
        app.state.body = "Updated body";
        app.saveDraft();
        app.saveDraft();
        for (Object request : requests) {
            AntmailAnternetPayload payload = (AntmailAnternetPayload) request;
            AntmailDraft draft = AntmailDraft.fromTag(AntmailWire.decodeTag(payload.second()));
            check(draftId.equals(draft.id()), "repeated saves reuse the draft ID");
            check(mailbox.saveDraft(draft), "draft accepted by mailbox");
        }
        check(mailbox.draftCount() == 1 && mailbox.drafts().get(0).body().equals("Updated body"), "saving updates one draft");
        requests.clear();
        app.state.sendPending = true;
        app.windowClosed();
        check(requests.isEmpty(), "closing during send cannot recreate a draft");
        AntmailNetworking.send(app.state.recipient, app.state.subject, app.state.body, List.of(), draftId);
        check(((AntmailAnternetPayload) requests.get(0)).fifth().equals(draftId.toString()), "send identifies the draft for server cleanup");
        AntmailClientState.update(new AntmailAnternetResultPayload(5, sender.fullAddress(), "", "send:invalid_message", "", 2L));
        AntmailClientState.update(snapshot);
        app.processSendResult();
        check(!app.state.sendPending && app.state.draftId.equals(draftId) && app.state.body.equals("Updated body"), "failed send preserves draft after a refresh");
        app.state.sendBaseline = AntmailClientState.getSendResult();
        app.state.sendPending = true;
        app.processSendResult();
        check(app.state.sendPending, "previous send result is ignored");
        app.state.attachCoins = true;
        app.state.coinAmount = "10";
        AntmailClientState.update(new AntmailAnternetResultPayload(0, sender.fullAddress(), UUID.randomUUID().toString(), "send:", "", 3L));
        AntmailClientState.update(snapshot);
        app.processSendResult();
        check(!app.state.sendPending && app.state.draftId == null && app.state.body.isEmpty(), "successful send clears composer after a refresh");
        check(!app.state.attachCoins && app.state.coinAmount.isEmpty(), "successful send clears coin attachments");
        Method recover = AntmailApp.class.getDeclaredMethod("restoreLatestDraft", AntmailAnternetResultPayload.class);
        recover.setAccessible(true);
        AntmailAnternetResultPayload stale = new AntmailAnternetResultPayload(0, sender.fullAddress(), "", "state",
                AntmailWire.encodeTag(mailbox.toTag()), 2L);
        recover.invoke(app, stale);
        check(app.state.body.isEmpty(), "cached draft is not recovered after sending");
        app.state.sendBaseline = AntmailClientState.getSendResult();
        app.state.sendPending = true;
        app.state.body = "Queued body";
        AntmailClientState.update(new AntmailAnternetResultPayload(1, sender.fullAddress(), UUID.randomUUID().toString(), "send:", "", 4L));
        app.processSendResult();
        check(app.state.body.isEmpty() && !app.state.sendPending, "queued sends complete the draft");
        AntmailApp unsaved = new AntmailApp();
        unsaved.state.body = "Unsent work";
        recover.invoke(unsaved, stale);
        check(unsaved.state.body.equals("Unsent work"), "draft recovery cannot overwrite current text");
        AntmailClientState.clear();
        check(AntmailClientState.getSendResult() == null, "logout clears send result");
        System.out.println("AntmailDraftTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
