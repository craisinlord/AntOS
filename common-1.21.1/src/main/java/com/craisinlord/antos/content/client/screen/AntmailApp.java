package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailAttachment;
import com.craisinlord.antos.content.antmail.AntmailAttachmentFiles;
import com.craisinlord.antos.content.antmail.AntmailDraft;
import com.craisinlord.antos.content.antmail.AntmailMailbox;
import com.craisinlord.antos.content.antmail.AntmailMessage;
import com.craisinlord.antos.content.antmail.AntmailProfile;
import com.craisinlord.antos.content.antmail.AntmailRenderMarkers;
import com.craisinlord.antos.content.antmail.AntmailWire;
import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.AntmailGlitchText;
import com.craisinlord.antos.content.client.ComputerFileSystemClientState;
import com.craisinlord.antos.content.computer.paint.AntPaintCanvas;
import com.craisinlord.antos.content.computer.paint.AntPaintFile;
import com.craisinlord.antos.content.computer.paint.AntPaintFileCodec;
import com.craisinlord.antos.content.network.AntmailAnternetResultPayload;
import com.craisinlord.antos.content.network.AntmailNetworking;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.DARK_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.BLACK;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HOVER_FILL;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.WIDTH;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HEIGHT;

final class AntmailApp extends ComputerApp {
    final State state = new State();

    void screenOpened(boolean loggedIn) {
        state.pickingAttachment = false;
        if (loggedIn) {
            requestState();
            state.refreshTicks = 40;
        }
    }

    void windowClosed() {
        if (state.mode.equals("compose")) saveDraft();
        state.pickingAttachment = false;
    }

    void tick(boolean loggedIn) {
        AntmailAnternetResultPayload mailResult = AntmailClientState.get();
        syncPaintAttachment();
        if (loggedIn && AntmailClientState.consumeRefreshRequest()) {
            requestState();
            state.refreshTicks = 40;
        } else if (loggedIn && --state.refreshTicks <= 0) {
            requestState();
            state.refreshTicks = 40;
        }
        if (loggedIn && AntmailClientState.getMailbox() == null) state.stateWaitTicks++;
        else state.stateWaitTicks = 0;
        if (!state.retryMessageId.isBlank() && mailResult != null
                && mailResult != state.retryBaseline
                && state.retryMessageId.equals(mailResult.messageId())
                && !"message_detail".equals(mailResult.detail())) {
            state.status = statusMessage(mailResult);
            state.retryMessageId = "";
            state.retryBaseline = null;
            state.retryTicks = 0;
            requestState(true);
        } else if (!state.retryMessageId.isBlank() && --state.retryTicks <= 0) {
            state.status = Component.translatable("computer.antos.status.retry_timed_out").getString();
            state.retryMessageId = "";
            state.retryBaseline = null;
            requestState(true);
        }
    }

    boolean hasUnreadMessages() {
        AntmailAnternetResultPayload result = AntmailClientState.getMailbox();
        AntmailMailbox mailbox = mailbox(result);
        return mailbox != null && mailbox.unreadCount() > 0;
    }

    void render(GuiGraphics g, int x, int y, int w, int h) {
        var result = AntmailClientState.getMailbox();
        AntmailAnternetResultPayload lastResponse = AntmailClientState.get();
        int navWidth = w;
        int composeWidth = Math.min(56, navWidth);
        int folderSlotWidth = Math.max(1, (navWidth - composeWidth - 4) / 4);
        int folderGroupWidth = folderSlotWidth * 4;
        int composeX = x + folderGroupWidth + 4;
        state.attachmentHits.clear();
        if (state.selfDeletingId != null && !state.mode.equals("message")) {
            AntmailNetworking.delete(state.selfDeletingId, false);
            state.ghosts.add(state.selfDeletingId);
            state.selfDeletingId = null;
        }
        if (result == null) {
            g.drawString(font, Component.literal("CONTACTING ANTMAIL NETWORK"), x, y + 5, GREEN, false);
            String waitStatus = lastResponse != null && !lastResponse.detail().isBlank()
                    ? "SERVER // " + lastResponse.detail().toUpperCase()
                    : state.stateWaitTicks >= 100 ? "NO RESPONSE // CHECK CLIENT + SERVER LOGS" : "PLEASE WAIT // RETRYING STATE SYNC";
            g.drawString(font, Component.literal(screen.trimToWidth(waitStatus, 208)), x, y + 25, PALE_GREEN, false);
            screen.box(g, x, y + 41, x + 150, y + 64, GREEN);
            g.drawString(font, Component.literal("[ RETRY STATE LINK ]"), x + 6, y + 48, GREEN, false);
        } else {
            String activeFolder = state.mode.equals("message") ? state.originMode : state.mode;
            boolean inboxSelected = activeFolder.equals("inbox");
            boolean sentSelected = activeFolder.equals("sent");
            boolean draftsSelected = activeFolder.equals("drafts");
            boolean moreSelected = activeFolder.equals("archive") || activeFolder.equals("trash") || activeFolder.equals("profile");
            int inboxX = x;
            int sentX = inboxX + folderSlotWidth;
            int draftsX = sentX + folderSlotWidth;
            boolean inboxHover = screen.hovered(inboxX, y, folderSlotWidth, 20);
            boolean sentHover = screen.hovered(sentX, y, folderSlotWidth, 20);
            boolean draftsHover = screen.hovered(draftsX, y, folderSlotWidth, 20);
            int moreX = draftsX + folderSlotWidth;
            boolean moreHover = screen.hovered(moreX, y, folderSlotWidth, 20);
            g.fill(x, y, x + folderGroupWidth, y + 20, DARK_GREEN);
            g.fill(inboxX, y, inboxX + folderSlotWidth, y + 20, inboxSelected ? PALE_GREEN : inboxHover ? HOVER_FILL : DARK_GREEN);
            g.fill(sentX, y, sentX + folderSlotWidth, y + 20, sentSelected ? PALE_GREEN : sentHover ? HOVER_FILL : DARK_GREEN);
            g.fill(draftsX, y, draftsX + folderSlotWidth, y + 20, draftsSelected ? PALE_GREEN : draftsHover ? HOVER_FILL : DARK_GREEN);
            g.fill(moreX, y, x + folderGroupWidth, y + 20, moreSelected ? PALE_GREEN : moreHover ? HOVER_FILL : DARK_GREEN);
            screen.box(g, x, y, x + folderGroupWidth, y + 20, GREEN);
            g.fill(sentX, y + 2, sentX + 1, y + 18, 0xFF315531);
            g.fill(draftsX, y + 2, draftsX + 1, y + 18, 0xFF315531);
            g.fill(moreX, y + 2, moreX + 1, y + 18, 0xFF315531);
            int composeColor = screen.hovered(composeX, y, composeWidth, 20) ? PALE_GREEN : GREEN;
            g.fill(composeX, y, composeX + composeWidth, y + 20, composeColor == PALE_GREEN ? HOVER_FILL : DARK_GREEN);
            screen.box(g, composeX, y, composeX + composeWidth, y + 20, composeColor);
            AntmailMailbox currentMailbox = mailbox(result);
            int unread = currentMailbox == null ? 0 : currentMailbox.unreadCount();
            float tabScale = screen.tabLabelScale("DRAFTS", folderSlotWidth);
            screen.drawTabLabel(g, unread > 0 ? "INBOX " + unread : "INBOX", inboxX, y, folderSlotWidth, inboxSelected ? BLACK : GREEN, tabScale);
            screen.drawTabLabel(g, "SENT", sentX, y, folderSlotWidth, sentSelected ? BLACK : GREEN, tabScale);
            screen.drawTabLabel(g, "DRAFTS", draftsX, y, folderSlotWidth, draftsSelected ? BLACK : GREEN, tabScale);
            screen.drawTabLabel(g, "MORE", moreX, y, folderSlotWidth, moreSelected ? BLACK : GREEN, tabScale);
            screen.drawTabLabel(g, state.mode.equals("compose") ? "SAVE" : "COMPOSE", composeX, y, composeWidth, composeColor, tabScale);
            if (state.mode.equals("profile")) {
                g.fill(x, y + 22, x + navWidth, y + 23, GREEN);
                g.drawString(font, Component.literal("MY PROFILE"), x, y + 29, GREEN, false);
                renderAvatar(g, state.profileDisplayName.isBlank() ? result.address().split("@", 2)[0]
                                : state.profileDisplayName,
                        state.profileAvatarItem, "", x, y + 45, 32);
                g.drawString(font, Component.literal("DISPLAY NAME // OPTIONAL"), x + 40, y + 44, GREEN, false);
                int fieldX = x + 40;
                int fieldWidth = Math.max(1, navWidth - 40);
                g.fill(fieldX, y + 55, fieldX + fieldWidth, y + 73, DARK_GREEN);
                screen.box(g, fieldX, y + 55, fieldX + fieldWidth, y + 73,
                        state.focused && state.field == 5 ? PALE_GREEN : 0xFF315531);
                String profileName = state.profileDisplayName.isBlank()
                        ? state.focused && state.field == 5 ? "" : "ACCOUNT NAME"
                        : state.profileDisplayName;
                if (state.focused && state.field == 5 && screen.caretVisible()) {
                    profileName = screen.insertCaret(profileName, state.cursor);
                }
                g.drawString(font, Component.literal(screen.trimToWidth(profileName, fieldWidth - 8)), fieldX + 4, y + 60,
                        state.profileDisplayName.isBlank() ? 0xFF7EA77E : PALE_GREEN, false);
                g.drawString(font, Component.literal("ICON ITEM ID // OPTIONAL"), x, y + 80, GREEN, false);
                g.fill(x, y + 91, x + navWidth, y + 109, DARK_GREEN);
                screen.box(g, x, y + 91, x + navWidth, y + 109,
                        state.focused && state.field == 6 ? PALE_GREEN : 0xFF315531);
                String profileItem = state.profileAvatarItem.isBlank()
                        ? state.focused && state.field == 6 ? "" : "minecraft:diamond"
                        : state.profileAvatarItem;
                if (state.focused && state.field == 6 && screen.caretVisible()) {
                    profileItem = screen.insertCaret(profileItem, state.cursor);
                }
                g.drawString(font, Component.literal(screen.trimToWidth(profileItem, navWidth - 8)), x + 4, y + 96,
                        state.profileAvatarItem.isBlank() ? 0xFF7EA77E : PALE_GREEN, false);
                g.drawString(font, Component.literal("EXAMPLE: minecraft:diamond"), x, y + 114, PALE_GREEN, false);
                if (!state.profileStatus.isBlank()) {
                    g.drawString(font, Component.literal(screen.trimToWidth(state.profileStatus, navWidth)), x, y + 140,
                            PALE_GREEN, false);
                }
                int actionY = y + 151;
                int actionGap = Math.max(0, (navWidth - 150) / 2);
                drawAction(g, x, actionY, 36, "BACK", GREEN);
                drawAction(g, x + 36 + actionGap, actionY, 70, "CLEAR ICON", GREEN);
                drawAction(g, x + navWidth - 44, actionY, 44, "SAVE", GREEN);
            } else if (state.mode.equals("compose")) {
                g.fill(x, y + 22, x + navWidth, y + 23, GREEN);
                AntmailComposeLayout composeLayout = composeLayout(y, h, navWidth);
                g.drawString(font, Component.literal("TO"), x, y + 27, GREEN, false);
                boolean invalidRecipient = !state.recipient.isBlank()
                        && !AntmailAddress.isValidAddress(state.recipient.trim());
                g.drawString(font, Component.literal(screen.trimToWidth(state.focused && state.field == 1 && screen.caretVisible()
                        ? screen.insertCaret(state.recipient, state.cursor) : state.recipient, navWidth - 30)), x + 27, y + 27,
                        invalidRecipient ? AntmailGlitchText.CORRUPT_RED : PALE_GREEN, false);
                g.fill(x, y + 38, x + navWidth, y + 39, 0xFF315531);
                g.drawString(font, Component.literal("SUBJECT"), x, y + 44, GREEN, false);
                g.drawString(font, Component.literal(screen.trimToWidth(state.focused && state.field == 2 && screen.caretVisible()
                        ? screen.insertCaret(state.subject, state.cursor) : state.subject, navWidth - 48)), x + 44, y + 44, PALE_GREEN, false);
                g.fill(x, y + 55, x + navWidth, y + 56, 0xFF315531);
                g.drawString(font, Component.literal("MESSAGE // SCROLL"), x, y + 60, GREEN, false);
                boolean bodyFocused = state.focused && state.field == 3;
                String body = state.body.isEmpty() && !bodyFocused ? "Write your message..." : state.body;
                List<AntmailBodyLine> bodyLines = wrapBody(body, navWidth - 10);
                int bodyVisible = Math.max(1, (composeLayout.bodyBottom() - composeLayout.bodyTop()) / 11);
                int bodyMaximum = Math.max(0, bodyLines.size() - bodyVisible);
                int cursor = Math.max(0, Math.min(state.cursor, state.body.length()));
                int cursorLine = state.body.isEmpty() ? 0
                        : wrapBody(state.body.substring(0, cursor) + "|", navWidth - 10).size() - 1;
                boolean followBodyCursor = bodyFocused && (!state.lastBodyFocused
                        || state.lastRenderedCursor != state.cursor
                        || !Objects.equals(state.lastRenderedBody, state.body));
                if (followBodyCursor) {
                    if (cursorLine < state.bodyScroll) state.bodyScroll = cursorLine;
                    else if (cursorLine >= state.bodyScroll + bodyVisible) state.bodyScroll = cursorLine - bodyVisible + 1;
                }
                state.lastBodyFocused = bodyFocused;
                state.lastRenderedCursor = state.cursor;
                state.lastRenderedBody = state.body;
                state.bodyMaximumScroll = bodyMaximum;
                state.bodyScroll = Math.max(0, Math.min(state.bodyScroll, bodyMaximum));
                int bodyEnd = Math.min(bodyLines.size(), state.bodyScroll + bodyVisible);
                for (int line = state.bodyScroll; line < bodyEnd; line++) {
                    AntmailBodyLine bodyLine = bodyLines.get(line);
                    g.drawString(font, Component.literal(body.substring(bodyLine.start(), bodyLine.end())), x,
                            composeLayout.bodyTop() + (line - state.bodyScroll) * 11, PALE_GREEN, false);
                }
                if (bodyFocused && screen.caretVisible()) {
                    AntmailBodyLine caretLine = cursorLine();
                    int caretLineIndex = bodyLines.indexOf(caretLine);
                    if (caretLineIndex >= state.bodyScroll && caretLineIndex < bodyEnd) {
                        int cursorIndex = Math.max(caretLine.start(), Math.min(state.cursor, caretLine.end()));
                        int caretX = x + font.width(state.body.substring(caretLine.start(), cursorIndex));
                        int caretY = composeLayout.bodyTop() + (caretLineIndex - state.bodyScroll) * 11;
                        g.fill(caretX, caretY - 1, caretX + 1, caretY + 9, PALE_GREEN);
                    }
                }
                screen.drawScrollbar(g, x + navWidth - 4, composeLayout.bodyTop(), Math.max(1, composeLayout.bodyBottom() - composeLayout.bodyTop()),
                        bodyVisible, bodyLines.size(), state.bodyScroll, bodyMaximum);
                List<String> attachmentLabels = new ArrayList<>();
                if (state.attachText) attachmentLabels.add(state.textPath.isBlank() ? "TEXT FILE" : state.textPath.substring(state.textPath.lastIndexOf('/') + 1));
                if (state.attachPaint) attachmentLabels.add(state.paintPath.isBlank() ? "PAINTING" : state.paintPath.substring(state.paintPath.lastIndexOf('/') + 1));
                boolean antazonEnabled = AntOSSettings.appEnabled("ANTAZON");
                if (state.attachCoins && antazonEnabled) attachmentLabels.add("COINS " + state.coinAmount);
                String attachmentSummary = attachmentLabels.isEmpty() ? "NO ATTACHMENTS" : String.join(" + ", attachmentLabels);
                if (state.attachCoins && antazonEnabled) {
                    screen.box(g, x, composeLayout.summaryY() - 3, x + navWidth, composeLayout.summaryY() + 10, state.focused && state.field == 4 ? GREEN : PALE_GREEN);
                    g.drawString(font, Component.literal("ANTCOIN AMOUNT"), x + 5, composeLayout.summaryY(), GREEN, false);
                    String amount = state.coinAmount.isEmpty() ? "CLICK, THEN TYPE" : state.coinAmount;
                    if (!state.coinAmount.isEmpty() && state.focused && state.field == 4 && screen.caretVisible()) {
                        amount = screen.insertCaret(amount, state.cursor);
                    }
                    g.drawString(font, Component.literal(screen.trimToWidth(amount, Math.max(32, navWidth - 100))), x + 94, composeLayout.summaryY(), GREEN, false);
                } else {
                    String composeSummary = state.status.isBlank() ? attachmentSummary : state.status;
                    g.drawString(font, Component.literal(screen.trimToWidth(composeSummary, navWidth)), x, composeLayout.summaryY(), PALE_GREEN, false);
                }
                screen.box(g, x, composeLayout.footerTop(), x + 112, composeLayout.footerTop() + 20, state.attachmentMenu ? GREEN : PALE_GREEN);
                g.drawString(font, Component.literal("+ ATTACH"), x + 5, composeLayout.footerTop() + 6, GREEN, false);
                if (state.attachmentMenu) {
                    g.fill(x, composeLayout.menuTop(), x + 112, composeLayout.footerTop() - 4, BLACK);
                    screen.box(g, x, composeLayout.menuTop(), x + 112, composeLayout.footerTop() - 4, GREEN);
                    g.drawString(font, Component.literal(state.attachText ? "REMOVE TEXT FILE" : "TEXT FILE..."), x + 6, composeLayout.menuTop() + 5, PALE_GREEN, false);
                    g.drawString(font, Component.literal(state.attachPaint ? "REMOVE PAINTING" : "PAINTING..."), x + 6, composeLayout.menuTop() + 21, PALE_GREEN, false);
                    if (antazonEnabled) g.drawString(font, Component.literal(state.attachCoins ? "REMOVE ANTCOINS" : "ANTCOINS..."), x + 6, composeLayout.menuTop() + 37, PALE_GREEN, false);
                }
                if (state.pickingAttachment) {
                    int pickerWidth = Math.max(80, Math.min(navWidth - 8, 380));
                    g.fill(x + 4, composeLayout.pickerTop(), x + pickerWidth, composeLayout.pickerBottom(), BLACK);
                    screen.box(g, x + 4, composeLayout.pickerTop(), x + pickerWidth, composeLayout.pickerBottom(), GREEN);
                    g.drawString(font, Component.literal("SELECT A FILE"), x + 10, composeLayout.pickerTop() + 6, GREEN, false);
                    screen.box(g, x + pickerWidth - 20, composeLayout.pickerTop() + 1, x + pickerWidth - 5, composeLayout.pickerTop() + 16, GREEN);
                    g.drawCenteredString(font, Component.literal("X"), x + pickerWidth - 13, composeLayout.pickerTop() + 4, GREEN);
                    int line = composeLayout.pickerTop() + 22;
                    for (String file : ComputerFileSystemClientState.get().files()) {
                        String[] fields = file.split("\\t", 3);
                        if (fields.length < 2 || (state.pickingPaint ? !fields[0].equals("IMAGE") : !fields[0].equals("TEXT")) || line > composeLayout.pickerBottom() - 16) continue;
                        g.drawString(font, Component.literal(screen.trimToWidth(fields[1], pickerWidth - 20)), x + 10, line, PALE_GREEN, false);
                        line += 14;
                    }
                }
                g.fill(x, composeLayout.footerTop() - 1, x + navWidth, composeLayout.footerTop(), GREEN);
                screen.box(g, x + navWidth - 60, composeLayout.footerTop(), x + navWidth, composeLayout.footerTop() + 20, GREEN);
                g.drawCenteredString(font, Component.literal("SEND"), x + navWidth - 30, composeLayout.footerTop() + 6, GREEN);
            } else if (state.mode.equals("message")) {
                AntmailMessage message = selectedMessage(result);
                if (message != null) {
                    boolean showRetry = state.sent && !message.deliveryStatus().equals("DELIVERED");
                    boolean corrupted = message.corrupted() && !state.sent;
                    int headerColor = corrupted ? AntmailGlitchText.CORRUPT_RED : GREEN;
                    int detailColor = corrupted ? AntmailGlitchText.CORRUPT_DIM : PALE_GREEN;
                    boolean replyAvailable = canReply(message);
                    String localizedSubject = AntOSPlayerText.apply(AntmailRenderMarkers.stripStyleMarkers(localizedMailText(message.subject())));
                    String localizedBody = AntOSPlayerText.apply(localizedMailText(message.body()));
                    String sharedProduct = antazonProductLink(localizedBody);
                    AntmailDetailLayout detail = detailLayout(y, h, !sharedProduct.isBlank(), showRetry);
                    if (corrupted) g.fill(x - 2, detail.actionY(), x + navWidth, detail.bodyBottom(), AntmailGlitchText.CORRUPT_BACKGROUND);
                    String moveLabel = switch (state.originMode) {
                        case "archive" -> "INBOX";
                        case "trash" -> "RESTORE";
                        default -> "ARCHIVE";
                    };
                    int actionX = x;
                    drawAction(g, actionX, detail.actionY(), 36, "BACK", headerColor);
                    actionX += 38;
                    if (replyAvailable) {
                        drawAction(g, actionX, detail.actionY(), 42, "REPLY", GREEN);
                        actionX += 44;
                    }
                    int moveWidth = state.originMode.equals("trash") ? 52 : 48;
                    drawAction(g, actionX, detail.actionY(), moveWidth, moveLabel, headerColor);
                    actionX += moveWidth + 2;
                    String deleteLabel = state.originMode.equals("trash")
                            && Objects.equals(state.confirmDeleteId, message.id())
                            && System.currentTimeMillis() < state.confirmDeleteUntil ? "SURE?" : "DELETE";
                    drawAction(g, actionX, detail.actionY(), 42, deleteLabel, headerColor);
                    if (state.originMode.equals("inbox")) {
                        drawAction(g, actionX + 44, detail.actionY(), 36, "UNREAD", headerColor);
                    }
                    AntmailProfile messageSenderProfile = AntmailClientState.getProfile(message.sender().fullAddress());
                    String fromLabel = message.fromPlayer() && !messageSenderProfile.displayName().isBlank()
                            ? messageSenderProfile.displayName()
                            : state.sent ? message.sender().fullAddress() : message.displaySender();
                    if (corrupted) fromLabel = AntmailGlitchText.garbledSender(fromLabel, message.id().hashCode());
                    String detailAvatarName = message.fromPlayer() && !messageSenderProfile.displayName().isBlank()
                            ? messageSenderProfile.displayName()
                            : message.senderDisplay().isBlank() ? message.sender().username() : message.senderDisplay();
                    renderAvatar(g, detailAvatarName,
                            message.fromPlayer() ? messageSenderProfile.avatarItem() : message.senderAvatarItem(),
                            message.fromPlayer() ? "" : message.senderAvatarEntity(), x, detail.senderY(), 24);
                    g.drawString(font, Component.literal("FROM  " + screen.trimToWidth(fromLabel, navWidth - 39 - font.width("FROM  "))), x + 31, detail.senderY() + 2, detailColor, false);
                    g.drawString(font, Component.literal("TO    " + screen.trimToWidth(message.recipient().fullAddress(), navWidth - 39 - font.width("TO    "))), x + 31, detail.senderY() + 14, detailColor, false);
                    if (corrupted) localizedSubject = AntmailGlitchText.flicker(localizedSubject, 10, message.id().hashCode());
                    g.drawString(font, Component.literal(screen.trimToWidth(localizedSubject, navWidth - 6)), x, detail.subjectY(), headerColor, false);
                    if (!sharedProduct.isBlank()) g.drawString(font, Component.literal("[ OPEN ANTAZON PRODUCT ]"), x, detail.productY(), GREEN, false);
                    float bodyTextScale = 0.85F;
                    int bodyLineAdvance = 9;
                    int bodyWrapWidth = Math.max(1, (int) ((navWidth - 16) / bodyTextScale));
                    List<net.minecraft.util.FormattedCharSequence> bodyLines = font.split(AntmailGlitchText.body(localizedBody, corrupted, PALE_GREEN), bodyWrapWidth);
                    int contentHeight = bodyLines.size() * bodyLineAdvance + (message.attachments().isEmpty() ? 0 : 6);
                    for (AntmailAttachment attachment : message.attachments()) {
                        contentHeight += attachment instanceof AntmailAttachment.Render render
                                ? (render.kind().equals("entity") ? 56 : 42) + 8 : 14;
                    }
                    int bodyHeight = Math.max(1, detail.bodyBottom() - detail.bodyTop());
                    int bodyVisible = Math.max(1, bodyHeight / bodyLineAdvance);
                    int bodyTotal = Math.max(1, (contentHeight + bodyLineAdvance - 1) / bodyLineAdvance);
                    int bodyMaximum = Math.max(0, (contentHeight - bodyHeight + bodyLineAdvance - 1) / bodyLineAdvance);
                    state.detailScroll = Math.max(0, Math.min(state.detailScroll, bodyMaximum));
                    state.detailMaximumScroll = bodyMaximum;
                    screen.drawScrollbar(g, x + navWidth - 4, detail.bodyTop(), bodyHeight, bodyVisible,
                            bodyTotal, state.detailScroll, bodyMaximum);
                    int scrollPixels = state.detailScroll * bodyLineAdvance;
                    screen.enableComputerScissor(g, x, detail.bodyTop(), x + navWidth - 6, detail.bodyBottom());
                    try {
                        for (int bodyIndex = 0; bodyIndex < bodyLines.size(); bodyIndex++) {
                            int lineY = detail.bodyTop() + bodyIndex * bodyLineAdvance - scrollPixels;
                            if (lineY + 8 <= detail.bodyTop() || lineY >= detail.bodyBottom()) continue;
                            int jitter = corrupted ? AntmailGlitchText.jitter(bodyIndex) : 0;
                            g.pose().pushPose();
                            try {
                                g.pose().translate(x + jitter, lineY, 0.0F);
                                g.pose().scale(bodyTextScale, bodyTextScale, 1.0F);
                                g.drawString(font, AntmailGlitchText.animate(bodyLines.get(bodyIndex), bodyIndex, state.openedAt), 0, 0, PALE_GREEN, false);
                            } finally {
                                g.pose().popPose();
                            }
                        }
                        int messageLine = detail.bodyTop() + bodyLines.size() * bodyLineAdvance + 6 - scrollPixels;
                        if (!message.attachments().isEmpty()) {
                            for (int attachmentIndex = 0; attachmentIndex < message.attachments().size(); attachmentIndex++) {
                                AntmailAttachment attachment = message.attachments().get(attachmentIndex);
                                if (attachment instanceof AntmailAttachment.Render render) {
                                    int previewSize = render.kind().equals("entity") ? 56 : 42;
                                    int boxWidth = Math.min(previewSize, 72);
                                    if (messageLine + previewSize > detail.bodyTop() && messageLine < detail.bodyBottom()) {
                                        g.fill(x, messageLine, x + boxWidth, messageLine + previewSize, DARK_GREEN);
                                        boolean previousTint = screen.archiveGreenTint;
                                        screen.archiveGreenTint = false;
                                        try {
                                            if (render.kind().equals("recipe")) {
                                                screen.renderArchiveRecipe(g, render.resourceId(), x, messageLine, boxWidth, 12);
                                            } else {
                                                screen.renderArchiveAsset(g, render.kind().equals("item") ? render.resourceId() : "",
                                                        render.kind().equals("entity") ? render.resourceId() : "",
                                                        render.kind().equals("enchantment") ? render.resourceId() : "",
                                                        render.kind().equals("potion") ? render.resourceId() : "",
                                                        x + boxWidth / 2, messageLine + previewSize / 2, boxWidth, 0.0F, 0.85F);
                                            }
                                        } finally {
                                            screen.archiveGreenTint = previousTint;
                                        }
                                        String label = render.kind().toUpperCase(Locale.ROOT) + " // " + render.resourceId();
                                        g.drawString(font, Component.literal(screen.trimToWidth(label, navWidth - boxWidth - 14)), x + boxWidth + 6, messageLine + previewSize / 2 - 4, PALE_GREEN, false);
                                    }
                                    messageLine += previewSize + 8;
                                } else {
                                    if (messageLine + 10 > detail.bodyTop() && messageLine < detail.bodyBottom()) {
                                        String attachmentLabel = attachment instanceof AntmailAttachment.Antcoins coins ? "[ ANTCOIN TRANSFER ] " + coins.amount() + " ANTCOINS" : "[ SAVE ATTACHMENT ] " + attachment.fileName();
                                        if (screen.inside(x, Math.max(messageLine, detail.bodyTop()), navWidth - 8,
                                                Math.min(messageLine + 10, detail.bodyBottom()) - Math.max(messageLine, detail.bodyTop()),
                                                screen.session.mouseX, screen.session.mouseY)) g.fill(x, messageLine, x + navWidth - 8, messageLine + 10, HOVER_FILL);
                                        g.drawString(font, Component.literal(screen.trimToWidth(attachmentLabel, navWidth - 8)), x, messageLine, GREEN, false);
                                        state.attachmentHits.add(new AntmailAttachmentHit(attachmentIndex, messageLine));
                                    }
                                    messageLine += 14;
                                }
                            }
                        }
                    } finally {
                        g.disableScissor();
                    }
                    if (showRetry) {
                        String retryLabel = state.retryMessageId.equals(message.id().toString()) ? "[ RETRYING... ]" : "[ RETRY DELIVERY ]";
                        g.drawString(font, Component.literal(retryLabel), x, y + h - 20, GREEN, false);
                    }
                }
            } else if (state.mode.equals("drafts")) {
                AntmailMailbox draftMailbox = mailbox(result);
                List<AntmailDraft> drafts = draftMailbox == null ? List.of() : draftMailbox.drafts();
                drawSearchControls(g, x, y + 24, navWidth);
                List<AntmailDraft> filteredDrafts = visibleDrafts(result);
                int listTop = y + 60;
                int visible = Math.max(1, (h - 82) / 21);
                state.listVisibleRows = visible;
                int maximum = Math.max(0, filteredDrafts.size() - visible);
                state.listScroll = Math.max(0, Math.min(state.listScroll, maximum));
                state.listMaximumScroll = maximum;
                screen.drawScrollbar(g, x + navWidth - 4, listTop, Math.max(1, h - 82), visible, filteredDrafts.size(), state.listScroll, maximum);
                for (int row = 0; row < visible && state.listScroll + row < filteredDrafts.size(); row++) {
                    AntmailDraft draft = filteredDrafts.get(state.listScroll + row);
                    int rowY = listTop + row * 21;
                    int deleteX = x + navWidth - 20;
                    if (screen.inside(x, rowY - 1, navWidth - 7, 20, screen.session.mouseX, screen.session.mouseY)) screen.drawHover(g, x, rowY - 1, navWidth - 7, 20);
                    g.drawString(font, Component.literal(screen.trimToWidth(draft.subject().isBlank() ? "Untitled draft" : draft.subject(), navWidth - 40)), x + 2, rowY, GREEN, false);
                    g.drawString(font, Component.literal(screen.trimToWidth(draft.recipient() == null ? "No recipient" : draft.recipient().fullAddress(), navWidth - 40)), x + 2, rowY + 9, PALE_GREEN, false);
                    drawAction(g, deleteX, rowY - 1, 18, "X", GREEN);
                    g.fill(x, rowY + 19, x + navWidth - 9, rowY + 20, 0xFF315531);
                }
                String draftState = filteredDrafts.isEmpty() ? (drafts.isEmpty() ? "NO SAVED DRAFTS" : "NO MATCHING DRAFTS")
                        : "SHOWING " + (state.listScroll + 1) + "-" + Math.min(filteredDrafts.size(), state.listScroll + visible) + " / " + filteredDrafts.size();
                g.drawString(font, Component.literal(draftState), x + 2, y + h - 14, PALE_GREEN, false);
            } else {
                drawSearchControls(g, x, y + 24, navWidth);
                List<AntmailMessage> messages = visibleMessages(result);
                int total = total(result, state.sent);
                String folderTitle = switch (state.mode) {
                    case "sent" -> "SENT";
                    case "archive" -> "ARCHIVE";
                    case "trash" -> "TRASH";
                    default -> "INBOX";
                };
                g.drawString(font, Component.literal(folderTitle + " // " + messages.size() + (messages.size() == total ? "" : " OF " + total)), x, y + 45, GREEN, false);
                int listTop = y + 60;
                int rowHeight = 27;
                int visible = Math.max(1, (h - 82) / rowHeight);
                state.listVisibleRows = visible;
                int maximum = Math.max(0, messages.size() - visible);
                state.listScroll = Math.max(0, Math.min(state.listScroll, maximum));
                state.listMaximumScroll = maximum;
                screen.drawScrollbar(g, x + navWidth - 4, listTop, Math.max(1, h - 82), visible, messages.size(), state.listScroll, maximum);
                for (int row = 0; row < visible && state.listScroll + row < messages.size(); row++) {
                    AntmailMessage message = messages.get(state.listScroll + row);
                    int rowY = listTop + row * rowHeight;
                    if (screen.inside(x, rowY - 1, navWidth - 7, rowHeight, screen.session.mouseX, screen.session.mouseY)) screen.drawHover(g, x, rowY - 1, navWidth - 7, rowHeight);
                    boolean corruptedRow = message.corrupted() && !state.sent;
                AntmailAddress rowAddress = state.sent ? message.recipient() : message.sender();
                String rowSender = state.sent ? displayAddress(rowAddress) : displaySender(message);
                AntmailProfile rowProfile = AntmailClientState.getProfile(rowAddress.fullAddress());
                if ((!state.sent && message.fromPlayer() || state.sent) && !rowProfile.displayName().isBlank()) {
                    rowSender = rowProfile.displayName();
                }
                    String rowSubject = AntOSPlayerText.apply(AntmailRenderMarkers.stripStyleMarkers(localizedMailText(message.subject())));
                    String rowPreview = AntOSPlayerText.apply(AntmailRenderMarkers.stripStyleMarkers(localizedMailText(message.body())))
                            .replaceAll("\\s+", " ").trim();
                    int rowColor = message.read() ? PALE_GREEN : GREEN;
                    if (corruptedRow) {
                        rowSender = AntmailGlitchText.garbledSender(rowSender, message.id().hashCode());
                        rowSubject = AntmailGlitchText.flicker(rowSubject, 12, message.id().hashCode() + 7);
                        rowColor = message.read() ? AntmailGlitchText.CORRUPT_DIM : AntmailGlitchText.CORRUPT_RED;
                    }
                String avatarItem = state.sent ? rowProfile.avatarItem()
                        : message.fromPlayer() ? rowProfile.avatarItem() : message.senderAvatarItem();
                String avatarEntity = !state.sent && !message.fromPlayer() ? message.senderAvatarEntity() : "";
                String avatarIdentity = !rowProfile.displayName().isBlank() && (state.sent || message.fromPlayer())
                        ? rowProfile.displayName() : state.sent || message.senderDisplay().isBlank()
                        ? rowAddress.username() : message.senderDisplay();
                    renderAvatar(g, avatarIdentity, avatarItem, avatarEntity, x + 2, rowY + 2, 21);
                    int textX = x + 31;
                    if (!message.read() && !state.sent) {
                        g.drawString(font, Component.literal("●"), textX, rowY, rowColor, false);
                        textX += 8;
                    }
                    long currentGameTime = Minecraft.getInstance().level == null ? message.createdAt() : Minecraft.getInstance().level.getGameTime();
                    String gameDate = "DAYS OLD " + Math.max(0L, (currentGameTime - message.createdAt()) / 24000L);
                    float dateScale = 0.8F;
                    int dateWidth = Math.round(font.width(gameDate) * dateScale);
                    int dateX = x + navWidth - 10 - dateWidth;
                    g.drawString(font, Component.literal(screen.trimToWidth(rowSender, Math.max(1, dateX - textX - 5))), textX, rowY,
                            !message.read() && !state.sent ? GREEN : PALE_GREEN, false);
                    if (navWidth >= 120) {
                        g.pose().pushPose();
                        try {
                            g.pose().translate(dateX, rowY + 2, 0.0F);
                            g.pose().scale(dateScale, dateScale, 1.0F);
                            g.drawString(font, Component.literal(gameDate), 0, 0, 0xFF7EA77E, false);
                        } finally {
                            g.pose().popPose();
                        }
                    }
                    String status = state.mode.equals("sent") ? "  // " + message.deliveryStatus() : "";
                    float rowTextScale = 0.85F;
                    g.pose().pushPose();
                    g.pose().translate(x + 31, rowY + 9, 0);
                    g.pose().scale(rowTextScale, rowTextScale, 1.0F);
                    g.drawString(font, Component.literal(screen.trimToWidth(rowSubject + status, Math.max(1, (int) ((navWidth - 40) / rowTextScale)))),
                            0, 0, message.read() ? PALE_GREEN : rowColor, false);
                    g.pose().popPose();
                    String attachmentFlag = message.hasAttachments() ? "[FILE]" : "";
                    int previewWidth = navWidth - 40 - font.width(attachmentFlag) - 4;
                    g.pose().pushPose();
                    g.pose().translate(x + 31, rowY + 17, 0);
                    g.pose().scale(rowTextScale, rowTextScale, 1.0F);
                    g.drawString(font, Component.literal(screen.trimToWidth(rowPreview, Math.max(1, (int) (previewWidth / rowTextScale)))), 0, 0, 0xFF7EA77E, false);
                    g.pose().popPose();
                    if (!attachmentFlag.isEmpty()) {
                        g.pose().pushPose();
                        g.pose().translate(x + navWidth - 9 - (int) (font.width(attachmentFlag) * rowTextScale), rowY + 17, 0);
                        g.pose().scale(rowTextScale, rowTextScale, 1.0F);
                        g.drawString(font, Component.literal(attachmentFlag), 0, 0, GREEN, false);
                        g.pose().popPose();
                    }
                    g.fill(x, rowY + rowHeight - 1, x + navWidth - 9, rowY + rowHeight, 0xFF315531);
                }
                if (state.mode.equals("inbox")) {
                    int ghostRow = Math.max(0, messages.size() - state.listScroll);
                    for (UUID ghost : state.ghosts) {
                        if (ghostRow >= visible) break;
                        if (messages.stream().anyMatch(candidate -> candidate.id().equals(ghost))) continue;
                        int rowY = listTop + ghostRow++ * rowHeight;
                        g.drawString(font, Component.literal("message not found"), x + 31, rowY + 9, AntmailGlitchText.CORRUPT_DIM, false);
                        g.fill(x, rowY + rowHeight - 1, x + navWidth - 9, rowY + rowHeight, 0xFF315531);
                    }
                }
                int rangeStart = messages.isEmpty() ? 0 : state.listScroll + 1;
                int rangeEnd = Math.min(messages.size(), state.listScroll + visible);
                String rangeLabel = messages.isEmpty() ? (total == 0 ? "" : "NO MATCHING MESSAGES")
                        : "SHOWING " + rangeStart + "-" + rangeEnd + " / " + messages.size();
                g.drawString(font, Component.literal(rangeLabel), x + 2, y + h - 14, PALE_GREEN, false);
                if (messages.isEmpty()) {
                    String emptyLabel = state.search.isBlank() && !state.unreadOnly && !state.attachmentsOnly
                            ? switch (state.mode) {
                                case "sent" -> "NO SENT MESSAGES";
                                case "archive" -> "NO ARCHIVED MESSAGES";
                                case "trash" -> "TRASH IS EMPTY";
                                default -> "NO MESSAGES IN YOUR INBOX";
                            } : "NO MESSAGES MATCH THIS FILTER";
                    g.drawString(font, Component.literal(emptyLabel), x + 2, listTop + 6, PALE_GREEN, false);
                }
            }
            if (state.moreOpen) {
                int menuWidth = Math.max(88, font.width("MY PROFILE") + 14);
                int menuX = Math.max(x, Math.min(moreX, x + navWidth - menuWidth));
                g.fill(menuX, y + 20, menuX + menuWidth, y + 76, BLACK);
                screen.box(g, menuX, y + 20, menuX + menuWidth, y + 76, GREEN);
                g.drawString(font, Component.literal("ARCHIVE"), menuX + 6, y + 25, PALE_GREEN, false);
                g.drawString(font, Component.literal("TRASH"), menuX + 6, y + 43, PALE_GREEN, false);
                g.drawString(font, Component.literal("MY PROFILE"), menuX + 6, y + 61, PALE_GREEN, false);
            }
            if (state.undoId != null && System.currentTimeMillis() < state.undoUntil
                    && !state.mode.equals("compose") && !state.mode.equals("message")) {
                drawAction(g, x, y + h - 34, 48, "UNDO", GREEN);
            }
        }
        AntmailAnternetResultPayload operation = AntmailClientState.get();
        if (state.sendPending && operation != null && operation != state.sendBaseline && !operation.messageId().isBlank()) {
            state.sendPending = false;
            state.sendBaseline = null;
            if (operation.status() == 0 || operation.status() == 1) {
                if (state.draftId != null) AntmailNetworking.deleteDraft(state.draftId);
                state.draftId = null;
                state.draftLoaded = false;
                state.status = statusMessage(operation);
                state.mode = "inbox";
                state.focused = false;
                state.recipient = "";
                state.subject = "";
                state.body = "";
                state.bodyScroll = 0;
                state.bodyMaximumScroll = 0;
                state.attachText = false;
                state.attachPaint = false;
                state.paintAttachment = null;
                state.paintLoadPendingPath = "";
                state.textPath = "";
                state.paintPath = "";
                state.page = 0;
                requestState(true);
            } else {
                state.focused = true;
                state.status = statusMessage(operation);
            }
        }
        if (state.profilePending && operation != null && operation != state.profileBaseline
                && (operation.detail().startsWith("profile_") || operation.detail().startsWith("invalid_")
                || operation.detail().equals("unauthorized") || operation.detail().equals("unconfigured"))) {
            state.profilePending = false;
            state.profileBaseline = null;
            state.profileStatus = operation.detail().equals("profile_saved") ? "PROFILE SAVED"
                    : operation.detail().equals("invalid_item_id") ? "UNKNOWN ITEM ID"
                    : operation.detail().equals("invalid_display_name") ? "DISPLAY NAME TOO LONG"
                    : "PROFILE UPDATE FAILED";
        }
        String displayStatus = state.status;
        if (!displayStatus.isEmpty() && !state.mode.equals("compose") && !state.mode.equals("profile")) {
            screen.drawBottomWrapped(g, displayStatus, x, y, h, 208, PALE_GREEN);
        }
    }

    private static AntmailComposeLayout composeLayout(int y, int height, int width) {
        int footerTop = y + height - 25;
        int summaryY = footerTop - 13;
        int menuHeight = AntOSSettings.appEnabled("ANTAZON") ? 54 : 36;
        return new AntmailComposeLayout(width, y + 70, footerTop, summaryY, summaryY - 3,
                y + 24, footerTop - menuHeight - 4, menuHeight, footerTop - 5);
    }

    private record AntmailComposeLayout(int width, int bodyTop, int footerTop, int summaryY, int bodyBottom,
                                        int pickerTop, int menuTop, int menuHeight, int pickerBottom) { }

    private boolean canReply(AntmailMessage message) {
        return !state.sent && message.fromPlayer() && message.definitionId().isBlank();
    }

    private void startReply(AntmailMessage message) {
        String subject = message.subject();
        if (!subject.regionMatches(true, 0, "Re:", 0, 3)) subject = "Re: " + subject;
        if (subject.length() > com.craisinlord.antos.content.antmail.AntmailValidation.MAX_SUBJECT_LENGTH) {
            subject = subject.substring(0, com.craisinlord.antos.content.antmail.AntmailValidation.MAX_SUBJECT_LENGTH);
        }
        String body = "\n\n-------- " + message.sender().fullAddress() + " wrote --------\n" + message.body();
        if (body.length() > com.craisinlord.antos.content.antmail.AntmailValidation.MAX_BODY_LENGTH) {
            body = body.substring(0, com.craisinlord.antos.content.antmail.AntmailValidation.MAX_BODY_LENGTH);
        }
        state.pickingAttachment = false;
        state.attachmentMenu = false;
        state.attachText = false;
        state.attachPaint = false;
        state.attachCoins = false;
        state.coinAmount = "";
        state.paintAttachment = null;
        state.draftId = null;
        state.draftLoaded = true;
        state.recipient = message.sender().fullAddress();
        state.subject = subject;
        state.body = body;
        state.bodyScroll = 0;
        state.mode = "compose";
        state.focused = true;
        state.field = 3;
        state.cursor = 0;
        state.selectionStart = -1;
        state.status = "";
    }

    private String localizedMailText(String value) {
        return value != null && value.startsWith("antazon.") ? Component.translatable(value).getString() : value;
    }

    private String displayAddress(AntmailAddress address) {
        String username = address.username();
        if (username.isEmpty()) return address.fullAddress();
        return Character.toUpperCase(username.charAt(0)) + username.substring(1);
    }

    private String displaySender(AntmailMessage message) {
        return message.senderDisplay().isBlank() ? displayAddress(message.sender()) : message.displaySender();
    }

    private void drawAction(GuiGraphics g, int x, int y, int width, String label, int color) {
        boolean hover = screen.hovered(x, y, width, 18);
        int border = hover ? PALE_GREEN : 0xFF315531;
        g.fill(x, y, x + width, y + 18, hover ? HOVER_FILL : DARK_GREEN);
        screen.box(g, x, y, x + width, y + 18, border);
        float scale = Math.min(1.0F, Math.max(0.1F, (width - 6.0F) / Math.max(1, font.width(label))));
        g.pose().pushPose();
        try {
            g.pose().translate(x + width / 2.0F, y + 5.0F, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, Component.literal(label), -font.width(label) / 2, 0, color, false);
        } finally {
            g.pose().popPose();
        }
    }

    private void drawSearchControls(GuiGraphics g, int x, int y, int width) {
        if (state.mode.equals("drafts")) {
            int searchWidth = Math.max(1, width);
            boolean searchHover = screen.hovered(x, y, searchWidth, 17);
            g.fill(x, y, x + searchWidth, y + 17, searchHover || state.searchFocused ? HOVER_FILL : DARK_GREEN);
            screen.box(g, x, y, x + searchWidth, y + 17, state.searchFocused ? PALE_GREEN : 0xFF315531);
            String search = state.search.isEmpty() && !state.searchFocused ? "SEARCH DRAFTS" : state.search;
            if (state.searchFocused && screen.caretVisible()) search = screen.insertCaret(search, state.searchCursor);
            g.drawString(font, Component.literal(screen.trimToWidth(search, searchWidth - 8)), x + 4, y + 5,
                    state.search.isEmpty() && !state.searchFocused ? 0xFF7EA77E : PALE_GREEN, false);
            return;
        }
        int filterGroupWidth = Math.min(124, Math.max(96, width - 64));
        int searchWidth = Math.max(1, width - filterGroupWidth - 8);
        boolean searchHover = screen.hovered(x, y, searchWidth, 17);
        g.fill(x, y, x + searchWidth, y + 17, searchHover || state.searchFocused ? HOVER_FILL : DARK_GREEN);
        screen.box(g, x, y, x + searchWidth, y + 17, state.searchFocused ? PALE_GREEN : 0xFF315531);
        String search = state.search;
        if (search.isEmpty() && !state.searchFocused) {
            search = switch (state.mode) {
                case "sent" -> "SEARCH SENT";
                case "archive" -> "SEARCH ARCHIVE";
                case "trash" -> "SEARCH TRASH";
                default -> "SEARCH INBOX";
            };
        }
        else if (state.searchFocused && screen.caretVisible()) search = screen.insertCaret(search, state.searchCursor);
        int searchColor = state.search.isEmpty() && !state.searchFocused ? 0xFF7EA77E : PALE_GREEN;
        if (state.search.isEmpty() && !state.searchFocused && font.width(search) > searchWidth - 8) {
            float scale = Math.max(0.6F, (searchWidth - 8.0F) / font.width(search));
            g.pose().pushPose();
            try {
                g.pose().translate(x + 4, y + 5, 0.0F);
                g.pose().scale(scale, scale, 1.0F);
                g.drawString(font, Component.literal(search), 0, 0, searchColor, false);
            } finally {
                g.pose().popPose();
            }
        } else {
            g.drawString(font, Component.literal(screen.trimToWidth(search, searchWidth - 8)), x + 4, y + 5, searchColor, false);
        }
        int unreadX = x + searchWidth + 4;
        int unreadWidth = Math.min(40, Math.max(32, filterGroupWidth / 3));
        int attachmentX = unreadX + unreadWidth + 4;
        int attachmentWidth = Math.max(32, x + width - attachmentX);
        screen.drawFilterButton(g, unreadX, y, unreadWidth, state.unreadOnly, "UNREAD");
        screen.drawFilterButton(g, attachmentX, y, attachmentWidth, state.attachmentsOnly, "ATTACHMENTS");
    }

    private boolean matches(AntmailMessage message, String query) {
        if (state.unreadOnly && message.read()) return false;
        if (state.attachmentsOnly && !message.hasAttachments()) return false;
        return true;
    }

    private List<AntmailMessage> visibleMessages(AntmailAnternetResultPayload result) {
        return mailboxMessages(result, state.sent).stream().filter(message -> matches(message, state.search)).toList();
    }

    private List<AntmailDraft> visibleDrafts(AntmailAnternetResultPayload result) {
        AntmailMailbox mailbox = mailbox(result);
        if (mailbox == null) return List.of();
        String query = state.search.toLowerCase(Locale.ROOT);
        return mailbox.drafts().stream().filter(draft -> query.isBlank()
                || (draft.subject() + " " + (draft.recipient() == null ? "" : draft.recipient().fullAddress()) + " " + draft.body())
                .toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private AntmailDetailLayout detailLayout(int y, int h, boolean hasProduct, boolean showRetry) {
        int actionY = y + 24;
        int senderY = actionY + 23;
        int subjectY = senderY + 28;
        int productY = subjectY + 12;
        int bodyTop = hasProduct ? productY + 13 : subjectY + 14;
        int bodyBottom = Math.max(bodyTop + 1, y + h - (showRetry ? 29 : 4));
        return new AntmailDetailLayout(actionY, senderY, subjectY, productY, bodyTop, bodyBottom);
    }

    private record AntmailDetailLayout(int actionY, int senderY, int subjectY, int productY, int bodyTop, int bodyBottom) { }

    private record AntmailAttachmentHit(int index, int y) { }

    private void renderAvatar(GuiGraphics g, String identity, String itemId, String entityId, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, DARK_GREEN);
        boolean previousTint = screen.archiveGreenTint;
        screen.archiveGreenTint = false;
        boolean rendered = false;
        try {
            rendered = screen.renderArchiveAsset(g, itemId, entityId, "", "", x + size / 2, y + size / 2, size, 0.0F, 0.9F);
        } finally {
            screen.archiveGreenTint = previousTint;
        }
        if (rendered) {
            screen.box(g, x, y, x + size, y + size, 0xFF315531);
            return;
        }
        int background = switch (Math.floorMod(identity.hashCode(), 4)) {
            case 0 -> 0xFF173817;
            case 1 -> 0xFF203B18;
            case 2 -> 0xFF183B2D;
            default -> 0xFF26351D;
        };
        g.fill(x, y, x + size, y + size, background);
        String[] parts = identity.replaceAll("[^A-Za-z0-9]+", " ").trim().split("\\s+");
        String initials = parts.length == 0 || parts[0].isBlank() ? "?" : parts[0].substring(0, Math.min(2, parts[0].length()));
        if (parts.length > 1 && !parts[1].isBlank()) initials = parts[0].substring(0, 1) + parts[1].substring(0, 1);
        g.drawCenteredString(font, Component.literal(initials.toUpperCase(Locale.ROOT)), x + size / 2, y + size / 2 - 4, PALE_GREEN);
        screen.box(g, x, y, x + size, y + size, 0xFF315531);
    }

    String statusMessage(AntmailAnternetResultPayload result) {
        if (result.status() == 0) return Component.translatable("computer.antos.status.delivered").getString();
        if (result.status() == 1) return Component.translatable("computer.antos.status.queued").getString();
        return Component.translatable("computer.antos.status.delivery_failed", result.detail().isBlank() ? Component.translatable("computer.antos.status.server_rejected") : result.detail().toUpperCase()).getString();
    }

    AntmailMailbox mailbox(AntmailAnternetResultPayload result) {
        if (result == null || result.data().isBlank()) return null;
        var cached = AntmailClientState.snapshot(result);
        if (cached != null) return cached.mailbox();
        try {
            return AntmailMailbox.fromTag(AntmailWire.decodeTag(result.data()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<AntmailMessage> mailboxMessages(AntmailAnternetResultPayload result, boolean sent) {
        AntmailMailbox mailbox = mailbox(result);
        if (mailbox == null) return List.of();
        String folder = state.mode.equals("message") ? state.originMode : state.mode;
        if (folder.equals("archive")) return mailbox.archived();
        if (folder.equals("trash")) return mailbox.trash();
        return sent ? mailbox.sent() : mailbox.inbox();
    }

    private int total(AntmailAnternetResultPayload result, boolean sent) {
        if (result == null || result.data().isBlank()) return 0;
        try {
            var cached = AntmailClientState.snapshot(result);
            var tag = cached != null ? cached.tag() : AntmailWire.decodeTag(result.data());
            return switch (state.mode) {
                case "sent" -> tag.getInt("TotalSent");
                case "archive" -> tag.getInt("TotalArchive");
                case "trash" -> tag.getInt("TotalTrash");
                default -> tag.getInt("TotalInbox");
            };
        } catch (RuntimeException ignored) {
            return mailboxMessages(result, sent).size();
        }
    }

    private AntmailMessage selectedMessage(AntmailAnternetResultPayload result) {
        List<AntmailMessage> messages = mailboxMessages(result, state.sent);
        if (state.messageIndex < 0 || state.messageIndex >= messages.size()) return null;
        AntmailMessage summary = messages.get(state.messageIndex);
        AntmailMessage detail = AntmailClientState.getMessage(summary.id());
        return detail == null ? summary : detail;
    }

    private void openMessage(AntmailMessage message, String originMode) {
        List<AntmailMessage> source = mailboxMessages(AntmailClientState.getMailbox(), originMode.equals("sent"));
        int index = -1;
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).id().equals(message.id())) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        state.originMode = originMode;
        state.sent = originMode.equals("sent");
        state.messageIndex = index;
        state.mode = "message";
        state.detailScroll = 0;
        state.attachmentHits.clear();
        AntmailNetworking.requestMessage(message.id());
        if (originMode.equals("inbox") && !message.read()) {
            AntmailNetworking.markRead(message.id());
            requestState(true);
        }
        state.selfDeletingId = originMode.equals("inbox") && message.deleteAfterRead() ? message.id() : null;
    }

    private void returnToOrigin() {
        state.mode = state.originMode.isBlank() ? "inbox" : state.originMode;
        state.sent = state.mode.equals("sent");
        state.messageIndex = -1;
        state.detailScroll = 0;
    }

    private int folderCode(String mode) {
        return switch (mode) {
            case "sent" -> com.craisinlord.antos.content.network.AntmailStateRequestPayload.SENT;
            case "archive" -> com.craisinlord.antos.content.network.AntmailStateRequestPayload.ARCHIVE;
            case "trash" -> com.craisinlord.antos.content.network.AntmailStateRequestPayload.TRASH;
            default -> com.craisinlord.antos.content.network.AntmailStateRequestPayload.INBOX;
        };
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        AntmailMessage trackMessage = state.mode.equals("message")
                ? selectedMessage(AntmailClientState.getMailbox()) : null;
        boolean showRetry = trackMessage != null && state.sent && !trackMessage.deliveryStatus().equals("DELIVERED");
        String trackBody = trackMessage == null ? "" : AntOSPlayerText.apply(localizedMailText(trackMessage.body()));
        AntmailDetailLayout detailLayout = detailLayout(contentY, contentH, !antazonProductLink(trackBody).isBlank(), showRetry);
        int antmailDetailTrackHeight = Math.max(1, detailLayout.bodyBottom() - detailLayout.bodyTop());
        boolean antmailDetailTrack = state.mode.equals("message")
                && screen.inside(contentX + contentW - 6, detailLayout.bodyTop(), 6, antmailDetailTrackHeight, mouseX, mouseY);
        int antmailListTrackTop = contentY + 60;
        int antmailListTrackHeight = Math.max(1, contentH - 82);
        boolean antmailListTrack = (state.mode.equals("inbox") || state.mode.equals("sent") || state.mode.equals("drafts")
                || state.mode.equals("archive") || state.mode.equals("trash"))
                && screen.inside(contentX + contentW - 6, antmailListTrackTop, 6, antmailListTrackHeight, mouseX, mouseY);
        if (antmailDetailTrack || antmailListTrack) {
            int trackTop = antmailDetailTrack ? detailLayout.bodyTop() : antmailListTrackTop;
            int trackHeight = antmailDetailTrack ? antmailDetailTrackHeight : antmailListTrackHeight;
            int maximum = state.mode.equals("message") ? state.detailMaximumScroll : state.listMaximumScroll;
            int value = (int) Math.round(Math.max(0.0, Math.min(1.0, (mouseY - trackTop) / trackHeight)) * maximum);
            if (state.mode.equals("message")) state.detailScroll = value;
            else state.listScroll = value;
            return true;
        }
        var result = AntmailClientState.getMailbox();
        int navWidth = contentW;
        int composeWidth = Math.min(56, navWidth);
        int folderSlotWidth = Math.max(1, (navWidth - composeWidth - 4) / 4);
        int composeX = folderSlotWidth * 4 + 4;
        if (result == null) {
            if (screen.inside(contentX, contentY + 41, Math.min(150, contentW), 23, mouseX, mouseY)) {
                state.stateWaitTicks = 0;
                requestState(true);
            }
            return true;
        } else if (state.undoId != null && System.currentTimeMillis() < state.undoUntil
                && !state.mode.equals("message")
                && screen.inside(contentX, contentY + contentH - 34, 48, 18, mouseX, mouseY)) {
            if (state.undoAction.equals("MOVE_TO_TRASH")) {
                AntmailNetworking.restoreFromTrash(state.undoId,
                        state.undoFolder == com.craisinlord.antos.content.network.AntmailStateRequestPayload.SENT
                                ? com.craisinlord.antos.content.network.AntmailStateRequestPayload.SENT
                                : state.undoFolder == com.craisinlord.antos.content.network.AntmailStateRequestPayload.ARCHIVE
                                ? com.craisinlord.antos.content.network.AntmailStateRequestPayload.ARCHIVE
                                : com.craisinlord.antos.content.network.AntmailStateRequestPayload.INBOX);
            } else if (state.undoAction.equals("ARCHIVE")) {
                AntmailNetworking.restoreFromArchive(state.undoId, state.undoFolder);
            } else if (state.undoAction.equals("RESTORE_ARCHIVE")) {
                AntmailNetworking.archive(state.undoId, false);
            } else if (state.undoAction.equals("RESTORE_TRASH")) {
                AntmailNetworking.moveToTrash(state.undoId, com.craisinlord.antos.content.network.AntmailStateRequestPayload.INBOX);
            }
            state.undoId = null;
            state.status = "ACTION UNDONE";
            returnToOrigin();
            requestState(true);
            return true;
        } else if (screen.inside(contentX, contentY, folderSlotWidth, 20, mouseX, mouseY)) {
            state.moreOpen = false;
            state.searchFocused = false;
            state.pickingAttachment = false;
            state.attachmentMenu = false;
            state.mode = "inbox";
            state.sent = false;
            state.page = 0;
            state.listScroll = 0;
            state.messageIndex = -1;
            state.ghosts.clear();
            requestState(true);
        } else if (screen.inside(contentX + folderSlotWidth, contentY, folderSlotWidth, 20, mouseX, mouseY)) {
            state.moreOpen = false;
            state.searchFocused = false;
            state.pickingAttachment = false;
            state.attachmentMenu = false;
            state.mode = "sent";
            state.sent = true;
            state.page = 0;
            state.listScroll = 0;
            state.messageIndex = -1;
            requestState(true);
        } else if (screen.inside(contentX + folderSlotWidth * 2, contentY, folderSlotWidth, 20, mouseX, mouseY)) {
            state.moreOpen = false;
            state.searchFocused = false;
            state.pickingAttachment = false;
            state.attachmentMenu = false;
            state.mode = "drafts";
            state.sent = false;
            state.page = 0;
            state.listScroll = 0;
            state.messageIndex = -1;
            requestState(true);
        } else if (screen.inside(contentX + folderSlotWidth * 3, contentY, folderSlotWidth, 20, mouseX, mouseY)) {
            state.moreOpen = !state.moreOpen;
        } else if (state.moreOpen && screen.inside(contentX + Math.max(0, Math.min(folderSlotWidth * 3,
                navWidth - Math.max(88, font.width("MY PROFILE") + 14))), contentY + 20,
                Math.max(88, font.width("MY PROFILE") + 14), 18, mouseX, mouseY)) {
            state.moreOpen = false;
            state.mode = "archive";
            state.sent = false;
            state.listScroll = 0;
            state.messageIndex = -1;
            requestState(true);
        } else if (state.moreOpen && screen.inside(contentX + Math.max(0, Math.min(folderSlotWidth * 3,
                navWidth - Math.max(88, font.width("MY PROFILE") + 14))), contentY + 38,
                Math.max(88, font.width("MY PROFILE") + 14), 20, mouseX, mouseY)) {
            state.moreOpen = false;
            state.mode = "trash";
            state.sent = false;
            state.listScroll = 0;
            state.messageIndex = -1;
            requestState(true);
        } else if (state.moreOpen && screen.inside(contentX + Math.max(0, Math.min(folderSlotWidth * 3,
                navWidth - Math.max(88, font.width("MY PROFILE") + 14))), contentY + 56,
                Math.max(88, font.width("MY PROFILE") + 14), 20, mouseX, mouseY)) {
            state.moreOpen = false;
            state.profileOriginMode = state.mode;
            state.mode = "profile";
            state.searchFocused = false;
            state.focused = false;
            var profile = AntmailClientState.getProfile(result.address());
            state.profileDisplayName = profile.displayName();
            state.profileAvatarItem = profile.avatarItem();
            state.profileStatus = "";
        } else if (screen.inside(contentX + composeX, contentY, navWidth - composeX, 20, mouseX, mouseY)) {
            state.moreOpen = false;
            state.searchFocused = false;
            if (state.mode.equals("compose")) {
                saveDraft();
                state.status = "DRAFT SAVED";
            } else {
                state.pickingAttachment = false;
                state.mode = "compose";
                state.focused = true;
                state.field = 1;
                state.cursor = state.recipient.length();
                restoreLatestDraft(result);
            }
        } else if ((state.mode.equals("inbox") || state.mode.equals("sent")
                || state.mode.equals("drafts") || state.mode.equals("archive")
                || state.mode.equals("trash"))
                && mouseY >= contentY + 24 && mouseY < contentY + 41) {
            int filterGroupWidth = Math.min(124, Math.max(96, navWidth - 64));
            int searchWidth = state.mode.equals("drafts") ? navWidth : Math.max(1, navWidth - filterGroupWidth - 8);
            int unreadX = contentX + searchWidth + 4;
            int unreadWidth = Math.min(40, Math.max(32, filterGroupWidth / 3));
            if (screen.inside(contentX, contentY + 24, searchWidth, 17, mouseX, mouseY)) {
                state.searchFocused = true;
                state.searchCursor = state.search.length();
            } else if (!state.mode.equals("drafts") && screen.inside(unreadX, contentY + 24, unreadWidth, 17, mouseX, mouseY)) {
                state.unreadOnly = !state.unreadOnly;
                state.listScroll = 0;
                requestState(true);
            } else if (!state.mode.equals("drafts") && screen.inside(unreadX + unreadWidth + 4, contentY + 24,
                    Math.max(32, contentX + navWidth - unreadX - unreadWidth - 4), 17, mouseX, mouseY)) {
                state.attachmentsOnly = !state.attachmentsOnly;
                state.listScroll = 0;
                requestState(true);
            } else {
                state.searchFocused = false;
            }
        } else if (state.mode.equals("profile")) {
            int actionY = contentY + 151;
            if (screen.inside(contentX + 40, contentY + 55, Math.max(1, navWidth - 40), 18, mouseX, mouseY)) {
                state.focused = true;
                state.field = 5;
                state.cursor = state.profileDisplayName.length();
                state.selectionStart = -1;
            } else if (screen.inside(contentX, contentY + 91, navWidth, 18, mouseX, mouseY)) {
                state.focused = true;
                state.field = 6;
                state.cursor = state.profileAvatarItem.length();
                state.selectionStart = -1;
            } else if (screen.inside(contentX, actionY, 36, 18, mouseX, mouseY)) {
                state.focused = false;
                state.field = 0;
                state.mode = state.profileOriginMode;
            } else if (screen.inside(contentX + 36 + Math.max(0, (navWidth - 150) / 2), actionY, 70, 18, mouseX, mouseY)) {
                state.profileAvatarItem = "";
                state.profileStatus = "ICON CLEARED // SAVE TO APPLY";
            } else if (screen.inside(contentX + navWidth - 44, actionY, 44, 18, mouseX, mouseY)) {
                saveProfile();
            } else {
                state.focused = false;
                state.field = 0;
            }
        } else if (state.mode.equals("inbox") || state.mode.equals("sent")) {
            int index = ((int) mouseY - (contentY + 60)) / 27 + state.listScroll;
            List<AntmailMessage> messages = visibleMessages(result);
            if (mouseY >= contentY + 60 && mouseY < contentY + 60 + state.listVisibleRows * 27
                    && index >= 0 && index < messages.size()) {
                openMessage(messages.get(index), state.mode);
            }
        } else if (state.mode.equals("drafts")) {
            int index = ((int) mouseY - (contentY + 60)) / 21 + state.listScroll;
            List<AntmailDraft> drafts = visibleDrafts(result);
            if (mouseY >= contentY + 60 && mouseY < contentY + 60 + state.listVisibleRows * 21
                    && index >= 0 && index < drafts.size()) {
                int deleteX = contentX + navWidth - 20;
                if (screen.inside(deleteX, contentY + 59 + (index - state.listScroll) * 21, 18, 20, mouseX, mouseY)) {
                    AntmailNetworking.deleteDraft(drafts.get(index).id());
                    state.status = "DRAFT DELETED";
                    requestState(true);
                } else {
                    restoreDraft(drafts.get(index));
                    state.mode = "compose";
                    state.focused = true;
                    state.field = 1;
                    state.cursor = state.recipient.length();
                }
            }
        } else if (state.mode.equals("archive") || state.mode.equals("trash")) {
            int index = ((int) mouseY - (contentY + 60)) / 27 + state.listScroll;
            List<AntmailMessage> messages = visibleMessages(result);
            if (mouseY >= contentY + 60 && mouseY < contentY + 60 + state.listVisibleRows * 27
                    && index >= 0 && index < messages.size()) {
                openMessage(messages.get(index), state.mode);
            }
        } else if (state.mode.equals("message")) {
            AntmailMessage message = selectedMessage(result);
            boolean replyAvailable = message != null && canReply(message);
            boolean messageShowRetry = message != null && state.sent && !message.deliveryStatus().equals("DELIVERED");
            String localizedBody = message == null ? "" : AntOSPlayerText.apply(localizedMailText(message.body()));
            AntmailDetailLayout detail = detailLayout(contentY, contentH, !antazonProductLink(localizedBody).isBlank(), messageShowRetry);
            int actionX = contentX + 38;
            if (replyAvailable && screen.inside(actionX, detail.actionY(), 42, 18, mouseX, mouseY)) {
                startReply(message);
                return true;
            }
            if (replyAvailable) actionX += 44;
            int moveWidth = state.originMode.equals("trash") ? 52 : 48;
            if (screen.inside(actionX, detail.actionY(), moveWidth, 18, mouseX, mouseY)) {
                if (message != null) {
                    if (state.originMode.equals("archive")) {
                        AntmailNetworking.restoreFromArchive(message.id());
                        state.undoAction = "RESTORE_ARCHIVE";
                        state.status = "MOVED TO INBOX";
                    } else if (state.originMode.equals("trash")) {
                        AntmailNetworking.restoreFromTrash(message.id(), com.craisinlord.antos.content.network.AntmailStateRequestPayload.INBOX);
                        state.undoAction = "RESTORE_TRASH";
                        state.status = "RESTORED TO INBOX";
                    } else {
                        AntmailNetworking.archive(message.id(), state.sent);
                        state.undoAction = "ARCHIVE";
                        state.status = "ARCHIVED";
                    }
                    state.undoId = message.id();
                    state.undoFolder = state.originMode.equals("sent")
                            ? com.craisinlord.antos.content.network.AntmailStateRequestPayload.SENT
                            : state.originMode.equals("archive")
                            ? com.craisinlord.antos.content.network.AntmailStateRequestPayload.ARCHIVE
                            : com.craisinlord.antos.content.network.AntmailStateRequestPayload.INBOX;
                    state.undoUntil = System.currentTimeMillis() + 6000L;
                }
                returnToOrigin();
                requestState(true);
                return true;
            }
            actionX += moveWidth + 2;
            if (screen.inside(actionX, detail.actionY(), 42, 18, mouseX, mouseY) && message != null) {
                if (state.originMode.equals("trash")) {
                    if (Objects.equals(state.confirmDeleteId, message.id())
                            && System.currentTimeMillis() < state.confirmDeleteUntil) {
                        AntmailNetworking.permanentlyDelete(message.id());
                        state.status = "PERMANENTLY DELETED";
                        state.confirmDeleteId = null;
                        returnToOrigin();
                        requestState(true);
                    } else {
                        state.confirmDeleteId = message.id();
                        state.confirmDeleteUntil = System.currentTimeMillis() + 4000L;
                        state.status = "SELECT DELETE AGAIN TO PERMANENTLY REMOVE";
                    }
                } else {
                    AntmailNetworking.moveToTrash(message.id(), folderCode(state.originMode));
                    state.undoId = message.id();
                    state.undoFolder = folderCode(state.originMode);
                    state.undoAction = "MOVE_TO_TRASH";
                    state.undoUntil = System.currentTimeMillis() + 6000L;
                    state.status = "MOVED TO TRASH // UNDO AVAILABLE";
                    returnToOrigin();
                    requestState(true);
                }
                return true;
            }
            if (state.originMode.equals("inbox") && message != null
                    && screen.inside(actionX + 44, detail.actionY(), 36, 18, mouseX, mouseY)) {
                AntmailNetworking.markUnread(message.id());
                state.status = "MARKED UNREAD";
                returnToOrigin();
                requestState(true);
                return true;
            }
            if (screen.inside(contentX, detail.actionY(), 36, 18, mouseX, mouseY)) {
                returnToOrigin();
                state.detailScroll = 0;
                return true;
            }
            if (state.sent && message != null && !message.deliveryStatus().equals("DELIVERED")
                    && state.retryMessageId.isBlank()
                    && mouseY >= contentY + contentH - 23 && mouseY < contentY + contentH - 7) {
                state.retryBaseline = AntmailClientState.get();
                state.retryMessageId = message.id().toString();
                state.retryTicks = 100;
                AntmailNetworking.retry(message.id());
                state.status = Component.translatable("computer.antos.status.retrying_delivery").getString();
                return true;
            }
            if (message != null && mouseX >= contentX && mouseX < contentX + navWidth - 8) {
                for (AntmailAttachmentHit hit : state.attachmentHits) {
                    if (hit.index() < message.attachments().size()
                            && mouseY >= Math.max(hit.y(), detail.bodyTop())
                            && mouseY < Math.min(hit.y() + 10, detail.bodyBottom())) {
                        saveAttachment(message.attachments().get(hit.index()));
                        return true;
                    }
                }
            }
            if (message != null && !antazonProductLink(localizedMailText(message.body())).isBlank()
                    && mouseY >= detail.productY() && mouseY < detail.productY() + 11) {
                openAntazonProduct(antazonProductLink(localizedMailText(message.body())));
                return true;
            }
        } else if (state.mode.equals("compose")) {
            AntmailComposeLayout composeLayout = composeLayout(contentY, contentH, navWidth);
                        if (state.pickingAttachment) {
                int pickerWidth = Math.max(80, Math.min(navWidth - 8, 380));
                if (screen.inside(contentX + pickerWidth - 20, composeLayout.pickerTop() + 1, 15, 15, mouseX, mouseY)) {
                    state.pickingAttachment = false;
                    return true;
                }
                int row = 0;
                for (String file : ComputerFileSystemClientState.get().files()) {
                    String[] fields = file.split("\\t", 3);
                    if (fields.length < 2 || (state.pickingPaint ? !fields[0].equals("IMAGE") : !fields[0].equals("TEXT"))) continue;
                    int rowTop = composeLayout.pickerTop() + 22 + row++ * 14;
                    if (rowTop > composeLayout.pickerBottom() - 16) break;
                    if (mouseY >= rowTop - 2 && mouseY < rowTop + 12) {
                        screen.session.files.state.selected = fields[1];
                        state.attachText = fields[0].equals("TEXT");
                        state.attachPaint = fields[0].equals("IMAGE");
                        state.pickingAttachment = false;
                        if (state.attachText) {
                            screen.session.text.state.path = fields[1];
                            state.textPath = fields[1];
                        } else {
                            state.paintPath = fields[1];
                            screen.session.paint.state.name = fields[1].substring(fields[1].lastIndexOf('/') + 1);
                            state.paintAttachment = null;
                            state.paintLoadPendingPath = fields[1];
                        }
                        ComputerNetworking.openFile(fields[1]);
                        return true;
                    }
                }
                return true;
            }
            if (screen.inside(contentX, composeLayout.footerTop(), 112, 20, mouseX, mouseY)) {
                state.attachmentMenu = !state.attachmentMenu;
                return true;
            }
            if (state.attachmentMenu && screen.inside(contentX, composeLayout.menuTop(), 112, composeLayout.menuHeight(), mouseX, mouseY)) {
                int choice = ((int) mouseY - composeLayout.menuTop()) / 18;
                state.attachmentMenu = false;
                if (choice == 0 && state.attachText) {
                    state.attachText = false;
                    state.textPath = "";
                } else if (choice == 1 && state.attachPaint) {
                    state.attachPaint = false;
                    state.paintPath = "";
                    state.paintAttachment = null;
                    state.paintLoadPendingPath = "";
                } else if (choice == 0 || choice == 1) {
                    state.pickingAttachment = true;
                    state.pickingPaint = choice == 1;
                } else if (choice == 2 && AntOSSettings.appEnabled("ANTAZON")) {
                    state.attachCoins = !state.attachCoins;
                    if (state.attachCoins) {
                        state.focused = true;
                        state.field = 4;
                        state.cursor = state.coinAmount.length();
                    } else if (state.field == 4) {
                        state.focused = false;
                        state.field = 0;
                    }
                }
                return true;
            }
            if (mouseY >= contentY + 22 && mouseY < contentY + 39) {
                state.field = 1;
                state.focused = true;
                state.cursor = state.recipient.length();
            } else if (mouseY >= contentY + 39 && mouseY < contentY + 56) {
                state.field = 2;
                state.focused = true;
                state.cursor = state.subject.length();
            } else if (mouseY >= composeLayout.bodyTop() && mouseY < composeLayout.bodyBottom()) {
                state.field = 3;
                state.focused = true;
                state.cursor = state.body.length();
                state.bodyScroll = state.bodyMaximumScroll;
            } else if (state.attachCoins && AntOSSettings.appEnabled("ANTAZON")
                    && screen.inside(contentX, composeLayout.summaryY() - 3, navWidth, 13, mouseX, mouseY)) {
                state.field = 4;
                state.focused = true;
                state.cursor = state.coinAmount.length();
            } else if (screen.inside(contentX + navWidth - 60, composeLayout.footerTop(), 60, 20, mouseX, mouseY)) {
                send();
            }
        }
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        if (state.mode.equals("compose")) {
            float scale = screen.uiScale();
            double localX = (mouseX - screen.width / 2.0F) / scale, localY = (mouseY - screen.height / 2.0F) / scale;
            int windowX = -WIDTH / 2 + screen.windowLocalX(window), windowY = -HEIGHT / 2 + screen.windowLocalY(window);
            int contentX = windowX + 8, contentY = windowY + 28, contentH = screen.windowHeight(window) - 34;
            int contentW = screen.windowWidth(window) - 16;
            AntmailComposeLayout composeLayout = composeLayout(contentY, contentH, contentW);
            if (screen.inside(contentX, composeLayout.bodyTop(), contentW, composeLayout.bodyBottom() - composeLayout.bodyTop(), localX, localY)) {
                state.bodyScroll = Math.max(0, Math.min(state.bodyMaximumScroll,
                        state.bodyScroll - (int) Math.signum(scrollY)));
            }
        } else if (state.mode.equals("message")) {
            float scale = screen.uiScale();
            double localX = (mouseX - screen.width / 2.0F) / scale, localY = (mouseY - screen.height / 2.0F) / scale;
            int windowX = -WIDTH / 2 + screen.windowLocalX(window), windowY = -HEIGHT / 2 + screen.windowLocalY(window);
            int contentX = windowX + 8, contentY = windowY + 28, contentW = screen.windowWidth(window) - 16;
            AntmailMessage message = selectedMessage(AntmailClientState.getMailbox());
            boolean showRetry = message != null && state.sent && !message.deliveryStatus().equals("DELIVERED");
            String body = message == null ? "" : AntOSPlayerText.apply(localizedMailText(message.body()));
            AntmailDetailLayout detail = detailLayout(contentY, screen.windowHeight(window) - 34,
                    !antazonProductLink(body).isBlank(), showRetry);
            if (screen.inside(contentX, detail.bodyTop(), contentW, detail.bodyBottom() - detail.bodyTop(), localX, localY)) {
                state.detailScroll = Math.max(0, Math.min(state.detailMaximumScroll,
                        state.detailScroll - (int) Math.signum(scrollY)));
            }
        } else if (state.mode.equals("inbox") || state.mode.equals("sent") || state.mode.equals("drafts")
                || state.mode.equals("archive") || state.mode.equals("trash")) {
            state.listScroll = Math.max(0, Math.min(state.listMaximumScroll,
                    state.listScroll - (int) Math.signum(scrollY)));
        }
        return true;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (state.searchFocused && codePoint >= 32
                && state.search.length() < 48) {
            state.search = state.search.substring(0, state.searchCursor)
                    + codePoint + state.search.substring(state.searchCursor);
            state.searchCursor++;
            state.listScroll = 0;
            requestState(true);
            return true;
        }
        if (state.focused && codePoint >= 32) {
            if (state.field >= 1 && state.field <= 3) replaceSelection(String.valueOf(codePoint));
            else if (state.field == 4 && Character.isDigit(codePoint)) replaceSelection(String.valueOf(codePoint));
            else if (state.mode.equals("profile") && (state.field == 5 || state.field == 6)) replaceSelection(String.valueOf(codePoint));
            return true;
        }
        return false;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (screen.activeWindow != null && screen.activeWindow.type.equals("ANTMAIL")
                && state.pickingAttachment && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            state.pickingAttachment = false;
            return true;
        }
        if (screen.activeWindow != null && screen.activeWindow.type.equals("ANTMAIL")
                && state.attachmentMenu && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            state.attachmentMenu = false;
            return true;
        }
        if (state.searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (state.searchCursor > 0) {
                    state.search = state.search.substring(0, state.searchCursor - 1)
                            + state.search.substring(state.searchCursor);
                    state.searchCursor--;
                    state.listScroll = 0;
                    requestState(true);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (state.searchCursor < state.search.length()) {
                    state.search = state.search.substring(0, state.searchCursor)
                            + state.search.substring(state.searchCursor + 1);
                    state.listScroll = 0;
                    requestState(true);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) { state.searchCursor = Math.max(0, state.searchCursor - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { state.searchCursor = Math.min(state.search.length(), state.searchCursor + 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                state.searchFocused = false;
                return true;
            }
        }
        if (!state.focused
                && !state.searchFocused) {
            if (state.mode.equals("message")) {
                AntmailMessage current = selectedMessage(AntmailClientState.getMailbox());
                if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    returnToOrigin();
                    return true;
                }
                if (current != null && keyCode == GLFW.GLFW_KEY_E && !state.originMode.equals("trash")) {
                    if (state.originMode.equals("archive")) AntmailNetworking.restoreFromArchive(current.id());
                    else AntmailNetworking.archive(current.id(), state.sent);
                    state.undoId = current.id();
                    state.undoFolder = folderCode(state.originMode);
                    state.undoAction = state.originMode.equals("archive") ? "RESTORE_ARCHIVE" : "ARCHIVE";
                    state.undoUntil = System.currentTimeMillis() + 6000L;
                    state.status = "ARCHIVED // UNDO AVAILABLE";
                    returnToOrigin();
                    requestState(true);
                    return true;
                }
                if (current != null && keyCode == GLFW.GLFW_KEY_R && canReply(current)) {
                    startReply(current);
                    return true;
                }
            } else if (state.mode.equals("inbox") || state.mode.equals("sent")
                    || state.mode.equals("archive") || state.mode.equals("trash")) {
                if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                    state.listScroll = Math.max(0, Math.min(state.listMaximumScroll,
                            state.listScroll + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    List<AntmailMessage> visible = visibleMessages(AntmailClientState.getMailbox());
                    if (state.listScroll < visible.size()) openMessage(visible.get(state.listScroll), state.mode);
                    return true;
                }
            }
        }
        if (state.focused) {
            if (screen.hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_A) { state.selectionStart = 0; state.cursor = activeFieldText().length(); return true; }
            if (screen.hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_C) { if (hasSelection()) Minecraft.getInstance().keyboardHandler.setClipboard(selectedText()); return true; }
            if (screen.hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_X) { if (hasSelection()) { Minecraft.getInstance().keyboardHandler.setClipboard(selectedText()); replaceSelection(""); } return true; }
            if (screen.hasControl(modifiers) && keyCode == GLFW.GLFW_KEY_V) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    clip = clip.replace("\r", "");
                    replaceSelection(clip);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (hasSelection()) replaceSelection("");
                else if (state.cursor > 0) { state.selectionStart = state.cursor - 1; replaceSelection(""); }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (hasSelection()) replaceSelection("");
                else if (state.cursor < activeFieldText().length()) { state.selectionStart = state.cursor; state.cursor++; replaceSelection(""); }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) { state.cursor = Math.max(0, state.cursor - 1); state.selectionStart = -1; return true; }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { state.cursor = Math.min(activeFieldText().length(), state.cursor + 1); state.selectionStart = -1; return true; }
            if (state.field == 3 && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
                moveBodyCursorVertically(keyCode == GLFW.GLFW_KEY_UP);
                return true;
            }
            if (state.field == 3 && keyCode == GLFW.GLFW_KEY_HOME) {
                AntmailBodyLine line = cursorLine();
                state.cursor = line.start();
                state.selectionStart = -1;
                return true;
            }
            if (state.field == 3 && keyCode == GLFW.GLFW_KEY_END) {
                AntmailBodyLine line = cursorLine();
                state.cursor = line.end();
                state.selectionStart = -1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (state.mode.equals("compose")) {
                    if (state.field == 1) {
                        state.field = 2;
                        state.cursor = state.subject.length();
                    } else if (state.field == 2) {
                        state.field = 3;
                        state.cursor = state.body.length();
                    } else if (state.field == 4) {
                        state.focused = false;
                    } else if (state.field == 3) {
                        replaceSelection("\n");
                    }
                } else if (state.mode.equals("profile")) {
                    if (state.field == 5) {
                        state.field = 6;
                        state.cursor = state.profileAvatarItem.length();
                    } else if (state.field == 6) {
                        saveProfile();
                    }
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { state.focused = false; return true; }
        }
        return false;
    }

    private String activeFieldText() {
        return switch (state.field) {
            case 1 -> state.recipient;
            case 2 -> state.subject;
            case 3 -> state.body;
            case 4 -> state.coinAmount;
            case 5 -> state.profileDisplayName;
            case 6 -> state.profileAvatarItem;
            default -> "";
        };
    }

    private void saveProfile() {
        state.profileBaseline = AntmailClientState.get();
        state.profilePending = true;
        state.focused = false;
        state.field = 0;
        state.profileStatus = "SAVING PROFILE";
        AntmailNetworking.updateProfile(state.profileDisplayName, state.profileAvatarItem);
    }

    private List<AntmailBodyLine> wrapBody(String value, int maxWidth) {
        List<AntmailBodyLine> lines = new ArrayList<>();
        int start = 0;
        int index = 0;
        int lineWidth = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            int next = index + Character.charCount(codePoint);
            if (codePoint == '\n') {
                lines.add(new AntmailBodyLine(start, index));
                index = next;
                start = index;
                lineWidth = 0;
                if (index == value.length()) lines.add(new AntmailBodyLine(start, start));
                continue;
            }
            String character = value.substring(index, next);
            int characterWidth = font.width(character);
            if (lineWidth + characterWidth > maxWidth && index > start) {
                lines.add(new AntmailBodyLine(start, index));
                start = index;
                lineWidth = 0;
                continue;
            }
            lineWidth += characterWidth;
            index = next;
        }
        if (start < value.length() || lines.isEmpty()) lines.add(new AntmailBodyLine(start, value.length()));
        return lines;
    }

    private AntmailBodyLine cursorLine() {
        List<AntmailBodyLine> lines = wrapBody(state.body, composeBodyWidth());
        int cursor = Math.max(0, Math.min(state.cursor, state.body.length()));
        for (int index = 0; index < lines.size(); index++) {
            AntmailBodyLine line = lines.get(index);
            if (cursor < line.end() || index == lines.size() - 1 && cursor <= line.end()) return line;
            if (cursor == line.end() && index + 1 < lines.size() && lines.get(index + 1).start() > cursor) return line;
        }
        return lines.getLast();
    }

    private void moveBodyCursorVertically(boolean up) {
        List<AntmailBodyLine> lines = wrapBody(state.body, composeBodyWidth());
        AntmailBodyLine current = cursorLine();
        int currentLineIndex = lines.indexOf(current);
        int targetLineIndex = Math.max(0, Math.min(lines.size() - 1, currentLineIndex + (up ? -1 : 1)));
        if (targetLineIndex == currentLineIndex) return;
        AntmailBodyLine target = lines.get(targetLineIndex);
        int cursor = Math.max(current.start(), Math.min(state.cursor, current.end()));
        int preferredWidth = font.width(state.body.substring(current.start(), cursor));
        int targetCursor = target.start();
        while (targetCursor < target.end()) {
            int next = targetCursor + Character.charCount(state.body.codePointAt(targetCursor));
            if (font.width(state.body.substring(target.start(), next)) > preferredWidth) break;
            targetCursor = next;
        }
        state.cursor = targetCursor;
        state.selectionStart = -1;
    }

    private int composeBodyWidth() {
        int contentWidth = screen.activeWindow == null ? 214 : screen.windowWidth(screen.activeWindow) - 16;
        return Math.max(1, contentWidth - 10);
    }

    private record AntmailBodyLine(int start, int end) { }

    private boolean hasSelection() { return state.selectionStart >= 0 && state.selectionStart != state.cursor; }

    private String selectedText() {
        int start = Math.min(state.selectionStart, state.cursor);
        int end = Math.max(state.selectionStart, state.cursor);
        return activeFieldText().substring(start, end);
    }

    private void replaceSelection(String text) {
        String current = activeFieldText();
        int cursor = Math.min(state.cursor, current.length());
        int start = hasSelection() ? Math.min(state.selectionStart, cursor) : cursor;
        int end = hasSelection() ? Math.max(state.selectionStart, cursor) : cursor;
        int maximum = state.field == 3 ? 4096 : state.field == 4 ? 12
                : state.field == 5 ? 32 : state.field == 6 ? 128 : 64;
        String replacement = text.substring(0, Math.min(text.length(), Math.max(0, maximum - (current.length() - (end - start)))));
        String updated = current.substring(0, start) + replacement + current.substring(end);
        if (state.field == 1) state.recipient = updated;
        else if (state.field == 2) state.subject = updated;
        else if (state.field == 3) state.body = updated;
        else if (state.field == 4) state.coinAmount = updated.replaceAll("[^0-9]", "");
        else if (state.field == 5) state.profileDisplayName = updated;
        else if (state.field == 6) state.profileAvatarItem = updated;
        state.cursor = start + replacement.length();
        state.selectionStart = -1;
    }

    void requestState() {
        requestState(false);
    }

    void requestState(boolean force) {
        String mode = state.mode.equals("message") ? state.originMode : state.mode;
        int folder = mode.equals("sent") ? com.craisinlord.antos.content.network.AntmailStateRequestPayload.SENT
                : mode.equals("drafts") ? com.craisinlord.antos.content.network.AntmailStateRequestPayload.DRAFTS
                : mode.equals("archive") ? com.craisinlord.antos.content.network.AntmailStateRequestPayload.ARCHIVE
                : mode.equals("trash") ? com.craisinlord.antos.content.network.AntmailStateRequestPayload.TRASH
                : com.craisinlord.antos.content.network.AntmailStateRequestPayload.INBOX;
        String query = mode.equals("drafts") ? "" : state.search;
        boolean unreadOnly = !mode.equals("drafts") && state.unreadOnly;
        boolean attachmentsOnly = !mode.equals("drafts") && state.attachmentsOnly;
        boolean sameRequest = AntmailClientState.snapshotKey().equals(AntmailClientState.requestKey(folder, query, unreadOnly, attachmentsOnly));
        long knownVersion = force || !sameRequest ? 0L : AntmailClientState.getVersion();
        AntmailNetworking.requestState(folder, state.page, knownVersion, query, unreadOnly, attachmentsOnly);
        if (force) state.stateWaitTicks = 0;
    }

    private String antazonProductLink(String body) {
        int start = body.indexOf("antazon://product/");
        if (start < 0) return "";
        int end = start + "antazon://product/".length();
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) end++;
        return body.substring(start + "antazon://product/".length(), end);
    }

    private void openAntazonProduct(String productId) {
        try {
            ResourceLocation.parse(productId);
            screen.open("ANTAZON");
            screen.session.antazon.openProduct(productId, "catalog");
        } catch (RuntimeException ignored) { }
    }

    void composeAntazonMail(String subject, String body) {
        screen.open("ANTMAIL");
        state.mode = "compose";
        state.sent = false;
        state.recipient = "";
        state.subject = subject;
        state.body = body;
        state.bodyScroll = 0;
        state.focused = true;
        state.field = 1;
        state.cursor = 0;
        state.status = Component.translatable("computer.antos.status.antazon_link_ready").getString();
    }

    private void send() {
        if (!AntOSSettings.appEnabled("ANTAZON")) {
            state.attachCoins = false;
            if (state.field == 4) state.focused = false;
        }
        if (!AntmailAddress.isValidAddress(state.recipient)) {
            state.status = Component.translatable("computer.antos.status.invalid_recipient").getString();
            return;
        }
        if (state.subject.isBlank() || state.body.isBlank()) {
            state.status = Component.translatable("computer.antos.status.subject_body_required").getString();
            return;
        }
        if (state.attachCoins) {
            try {
                if (Long.parseLong(state.coinAmount) < 1) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                state.status = Component.translatable("computer.antos.status.positive_antcoins_required").getString();
                return;
            }
        }
        if (state.attachText && (screen.session.text.state.dirty || screen.session.text.state.path.isBlank() || screen.session.text.state.path.contains("untitled") || !screen.session.text.state.path.equals(state.textPath))) {
                state.status = Component.translatable("computer.antos.status.save_text_file_first").getString();
            return;
        }
        if (state.attachPaint && !state.paintLoadPendingPath.isBlank()) {
                state.status = Component.translatable("computer.antos.status.attachment_loading").getString();
            return;
        }
        if (state.attachPaint && state.paintAttachment == null
                && (screen.session.paint.state.dirty || screen.session.paint.state.name.isBlank() || screen.session.paint.state.name.startsWith("UNTITLED")
                || !state.paintPath.endsWith(screen.session.paint.state.name))) {
                state.status = Component.translatable("computer.antos.status.save_painting_first").getString();
            return;
        }
        List<AntmailAttachment> attachments = composeAttachments();
        state.sendBaseline = AntmailClientState.get();
        AntmailNetworking.send(state.recipient, state.subject, state.body, attachments);
        if (state.attachCoins) com.craisinlord.antos.content.network.ComputerNetworking.requestAntazonWallet();
        state.status = Component.translatable("computer.antos.status.sending_message").getString();
        state.sendPending = true;
        state.focused = false;
        state.draftId = null;
        state.draftLoaded = false;
        state.pickingAttachment = false;
    }

    private void restoreLatestDraft(AntmailAnternetResultPayload result) {
        if (state.draftLoaded) return;
        AntmailMailbox mailbox = mailbox(result);
        if (mailbox == null || mailbox.drafts().isEmpty()) {
            state.draftLoaded = true;
            return;
        }
        restoreDraft(mailbox.drafts().getLast());
            state.status = Component.translatable("computer.antos.status.draft_recovered").getString();
        state.draftLoaded = true;
    }

    private void restoreDraft(AntmailDraft draft) {
        state.draftId = draft.id();
        state.recipient = draft.recipient() == null ? "" : draft.recipient().fullAddress();
        state.subject = draft.subject();
        state.body = draft.body();
        state.bodyScroll = 0;
        state.attachText = false;
        state.attachPaint = false;
        state.attachCoins = false;
        state.coinAmount = "";
        state.pickingAttachment = false;
        for (AntmailAttachment attachment : draft.attachments()) {
            if (attachment instanceof AntmailAttachment.TextFile text) {
                state.attachText = true;
                screen.session.text.state.path = "/documents/" + text.fileName();
                state.textPath = screen.session.text.state.path;
                screen.session.text.state.content = text.contents();
            } else if (attachment instanceof AntmailAttachment.PaintImage paint) {
                state.attachPaint = true;
                screen.session.paint.state.name = paint.fileName();
                state.paintPath = "/pictures/" + paint.fileName();
                state.paintAttachment = paint;
                state.paintLoadPendingPath = "";
                AntPaintCanvas canvas = new AntPaintCanvas();
                byte[] pixels = paint.pixels();
                for (int index = 0; index < pixels.length; index++) if (pixels[index] == 1) canvas.set(index % AntPaintCanvas.WIDTH, index / AntPaintCanvas.WIDTH, true);
                screen.session.paint.state.canvas = canvas;
            } else if (attachment instanceof AntmailAttachment.Antcoins coins && AntOSSettings.appEnabled("ANTAZON")) {
                state.attachCoins = true;
                state.coinAmount = Long.toString(coins.amount());
            }
        }
    }

    void saveDraft() {
        try {
            if (state.recipient.isBlank() && state.subject.isBlank() && state.body.isBlank()) return;
            AntmailAnternetResultPayload result = AntmailClientState.getMailbox();
            AntmailMailbox mailbox = mailbox(result);
            if (mailbox == null) return;
            AntmailDraft draft = new AntmailDraft(state.draftId, AntmailAddress.parse(mailbox.address().fullAddress()),
                    state.recipient.isBlank() ? null : AntmailAddress.parse(state.recipient), state.subject, state.body, composeAttachments());
            AntmailNetworking.saveDraft(draft);
        } catch (RuntimeException ignored) {
            state.status = Component.translatable("computer.antos.status.draft_save_error").getString();
        }
    }

    private List<AntmailAttachment> composeAttachments() {
        List<AntmailAttachment> attachments = new ArrayList<>();
        if (state.attachText) attachments.add(new AntmailAttachment.TextFile(screen.session.text.state.path.substring(screen.session.text.state.path.lastIndexOf('/') + 1), screen.session.text.state.content));
        if (state.attachPaint && state.paintAttachment != null) {
            attachments.add(state.paintAttachment);
        } else if (state.attachPaint) {
            byte[] pixels = new byte[AntPaintCanvas.WIDTH * AntPaintCanvas.HEIGHT];
            for (int py = 0; py < AntPaintCanvas.HEIGHT; py++) for (int px = 0; px < AntPaintCanvas.WIDTH; px++) pixels[py * AntPaintCanvas.WIDTH + px] = (byte) (screen.session.paint.state.canvas.get(px, py) ? 1 : 0);
            attachments.add(new AntmailAttachment.PaintImage(screen.session.paint.state.name, AntPaintCanvas.WIDTH, AntPaintCanvas.HEIGHT, pixels));
        }
        if (state.attachCoins && AntOSSettings.appEnabled("ANTAZON")) attachments.add(new AntmailAttachment.Antcoins(Long.parseLong(state.coinAmount)));
        return attachments;
    }

    void syncPaintAttachment() {
        String requestedPath = state.paintLoadPendingPath;
        if (requestedPath.isBlank()) return;
        ComputerFileSystemClientState.State fileState = ComputerFileSystemClientState.get();
        if (!requestedPath.equals(fileState.openedPath()) || fileState.openedContents().isBlank()) return;
        try {
            AntPaintFile file = AntPaintFileCodec.decode(Base64.getDecoder().decode(fileState.openedContents()));
            screen.session.paint.state.name = file.filename();
            state.paintPath = requestedPath;
            state.paintAttachment = AntmailAttachmentFiles.fromPaintFile(file);
            state.paintLoadPendingPath = "";
            state.status = Component.translatable("computer.antos.status.painting_attached", file.filename()).getString();
        } catch (RuntimeException exception) {
            state.paintLoadPendingPath = "";
            state.status = Component.translatable("computer.antos.status.invalid_paint_file").getString();
        }
    }

    private void saveAttachment(AntmailAttachment attachment) {
        try {
            if (attachment instanceof AntmailAttachment.TextFile text) {
                String path = "/documents/" + text.fileName();
                if (computerFileExists(path)) ComputerNetworking.saveFile(path, text.contents());
                else ComputerNetworking.createFile(path, text.contents());
                state.status = Component.translatable("computer.antos.status.attachment_saved", text.fileName()).getString();
            } else if (attachment instanceof AntmailAttachment.PaintImage paint) {
                String name = paint.fileName().endsWith(".antpaint") ? paint.fileName() : paint.fileName() + ".antpaint";
                AntPaintFile file = AntmailAttachmentFiles.toPaintFile(paint, UUID.randomUUID().toString(), 0L, 0L);
                String encoded = Base64.getEncoder().encodeToString(AntPaintFileCodec.encode(file));
                String path = "/pictures/" + name;
                if (computerFileExists(path)) ComputerNetworking.saveFile(path, encoded);
                else ComputerNetworking.createFile(path, encoded);
                state.status = Component.translatable("computer.antos.status.attachment_saved", name).getString();
            } else if (attachment instanceof AntmailAttachment.Antcoins coins) {
                state.status = Component.translatable("computer.antos.status.coins_already_credited", coins.amount()).getString();
            }
            ComputerNetworking.listFiles();
        } catch (RuntimeException exception) {
            state.status = Component.translatable("computer.antos.status.invalid_file").getString();
        }
    }

    private boolean computerFileExists(String path) {
        return ComputerFileSystemClientState.get().files().stream().anyMatch(entry -> entry.contains("\t" + path + "\t"));
    }

    static final class State {
        String status = "";
        boolean sendPending;
        AntmailAnternetResultPayload sendBaseline;
        String retryMessageId = "";
        AntmailAnternetResultPayload retryBaseline;
        int retryTicks;
        int refreshTicks;
        int stateWaitTicks;
        boolean focused;
        String mode = "inbox";
        String originMode = "inbox";
        String recipient = "";
        String subject = "";
        String body = "";
        int field;
        int cursor;
        int selectionStart = -1;
        boolean sent;
        int messageIndex = -1;
        long openedAt;
        UUID selfDeletingId;
        final List<UUID> ghosts = new ArrayList<>();
        int page;
        int listScroll;
        int listMaximumScroll;
        int listVisibleRows;
        String search = "";
        int searchCursor;
        boolean searchFocused;
        String profileDisplayName = "";
        String profileAvatarItem = "";
        String profileOriginMode = "inbox";
        String profileStatus = "";
        boolean profilePending;
        AntmailAnternetResultPayload profileBaseline;
        boolean unreadOnly;
        boolean attachmentsOnly;
        boolean moreOpen;
        UUID undoId;
        int undoFolder;
        String undoAction = "";
        long undoUntil;
        UUID confirmDeleteId;
        long confirmDeleteUntil;
        int detailScroll;
        int detailMaximumScroll;
        int bodyScroll;
        int bodyMaximumScroll;
        int lastRenderedCursor = -1;
        String lastRenderedBody = "";
        boolean lastBodyFocused;
        final List<AntmailAttachmentHit> attachmentHits = new ArrayList<>();
        boolean attachText;
        boolean attachPaint;
        boolean attachCoins;
        boolean attachmentMenu;
        String coinAmount = "";
        String textPath = "";
        String paintPath = "";
        AntmailAttachment.PaintImage paintAttachment;
        String paintLoadPendingPath = "";
        UUID draftId;
        boolean draftLoaded;
        boolean pickingAttachment;
        boolean pickingPaint;
    }
}
