package com.craisinlord.antos.content.antmail;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AntmailMessage {
    private static final String ID_TAG = "Id";
    private static final String FROM_TAG = "From";
    private static final String TO_TAG = "To";
    private static final String SUBJECT_TAG = "Subject";
    private static final String BODY_TAG = "Body";
    private static final String CREATED_TAG = "Created";
    private static final String READ_TAG = "Read";
    private static final String ATTACHMENTS_TAG = "Attachments";
    private static final String DELIVERY_STATUS_TAG = "DeliveryStatus";
    private static final String DEFINITION_ID_TAG = "DefinitionId";
    private static final String STYLE_TAG = "Style";
    private static final String SENDER_DISPLAY_TAG = "SenderDisplay";
    private static final String SENDER_AVATAR_ITEM_TAG = "SenderAvatarItem";
    private static final String SENDER_AVATAR_ENTITY_TAG = "SenderAvatarEntity";
    private static final String DELETE_AFTER_READ_TAG = "DeleteAfterRead";
    private static final String NOTIFICATION_TAG = "Notification";
    private static final String FROM_PLAYER_TAG = "FromPlayer";

    private final UUID id;
    private final AntmailAddress sender;
    private final AntmailAddress recipient;
    private final String subject;
    private final String body;
    private final long createdAt;
    private final List<AntmailAttachment> attachments;
    private boolean read;
    private String deliveryStatus = "DELIVERED";
    private String definitionId = "";
    private String style = "";
    private String senderDisplay = "";
    private String senderAvatarItem = "";
    private String senderAvatarEntity = "";
    private boolean deleteAfterRead;
    private String notification = "";
    private boolean fromPlayer;
    private boolean hasAttachments;
    private String searchText;

    public AntmailMessage(UUID id, AntmailAddress sender, AntmailAddress recipient, String subject, String body, long createdAt, List<AntmailAttachment> attachments, boolean read) {
        AntmailValidation.Result result = AntmailValidation.validateMessage(sender, recipient, subject, body, attachments);
        if (!result.valid()) {
            throw new IllegalArgumentException(result.reason());
        }
        this.id = id == null ? UUID.randomUUID() : id;
        this.sender = sender;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.createdAt = createdAt;
        this.attachments = List.copyOf(attachments);
        this.hasAttachments = !attachments.isEmpty();
        this.read = read;
    }

    public static AntmailMessage create(AntmailAddress sender, AntmailAddress recipient, String subject, String body, long createdAt, List<AntmailAttachment> attachments) {
        AntmailRenderMarkers.Extracted extracted = AntmailRenderMarkers.extract(body, attachments);
        return new AntmailMessage(UUID.randomUUID(), sender, recipient, subject, extracted.body(), createdAt, extracted.attachments(), false);
    }

    public static AntmailMessage create(AntmailAddress sender, AntmailAddress recipient, String subject, String body, long createdAt, List<AntmailAttachment> attachments, String definitionId) {
        AntmailMessage message = create(sender, recipient, subject, body, createdAt, attachments);
        message.definitionId = definitionId == null ? "" : definitionId;
        return message;
    }

    public UUID id() { return id; }
    public AntmailAddress sender() { return sender; }
    public AntmailAddress recipient() { return recipient; }
    public String subject() { return subject; }
    public String body() { return body; }
    public long createdAt() { return createdAt; }
    public List<AntmailAttachment> attachments() { return attachments; }
    public boolean hasAttachments() { return hasAttachments || !attachments.isEmpty(); }
    public boolean read() { return read; }
    public String deliveryStatus() { return deliveryStatus; }
    public String definitionId() { return definitionId; }
    public String style() { return style; }
    public boolean corrupted() { return style.equals("corrupted"); }
    public String senderDisplay() { return senderDisplay; }
    public String senderAvatarItem() { return senderAvatarItem; }
    public String senderAvatarEntity() { return senderAvatarEntity; }
    public String displaySender() { return senderDisplay.isBlank() ? sender.fullAddress() : senderDisplay; }
    public boolean deleteAfterRead() { return deleteAfterRead; }
    public String notification() { return notification; }
    public boolean fromPlayer() { return fromPlayer; }
    public void setPresentation(String style, String senderDisplay, boolean deleteAfterRead, String notification) {
        setPresentation(style, senderDisplay, "", "", deleteAfterRead, notification);
    }
    public void setPresentation(String style, String senderDisplay, String senderAvatarItem, String senderAvatarEntity,
                                boolean deleteAfterRead, String notification) {
        this.style = style == null ? "" : style;
        this.senderDisplay = senderDisplay == null ? "" : senderDisplay;
        this.senderAvatarItem = senderAvatarItem == null ? "" : senderAvatarItem;
        this.senderAvatarEntity = senderAvatarEntity == null ? "" : senderAvatarEntity;
        this.deleteAfterRead = deleteAfterRead;
        this.notification = notification == null ? "" : notification;
    }
    public void markFromPlayer() { fromPlayer = true; }
    public void setDeliveryStatus(String status) {
        deliveryStatus = status == null || status.isBlank() ? "DELIVERED" : status;
        searchText = null;
    }

    public String searchText() {
        String text = searchText;
        if (text == null) {
            text = (sender.fullAddress() + " " + displaySender() + " " + recipient.fullAddress() + " " + subject + " " + body + " "
                    + deliveryStatus).toLowerCase(java.util.Locale.ROOT);
            searchText = text;
        }
        return text;
    }

    public AntmailMessage copy() {
        return fromTag(toTag(true));
    }
    public void markRead() { read = true; }
    public void markUnread() { read = false; }

    public CompoundTag toTag() {
        return toTag(true);
    }

    public CompoundTag toTag(boolean includeAttachments) {
        return toTag(includeAttachments, Integer.MAX_VALUE);
    }

    private CompoundTag toTag(boolean includeAttachments, int bodyLimit) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ID_TAG, id.toString());
        tag.putString(FROM_TAG, sender.fullAddress());
        tag.putString(TO_TAG, recipient.fullAddress());
        tag.putString(SUBJECT_TAG, subject);
        tag.putString(BODY_TAG, body.length() > bodyLimit ? body.substring(0, bodyLimit) : body);
        tag.putLong(CREATED_TAG, createdAt);
        tag.putBoolean(READ_TAG, read);
        tag.putString(DELIVERY_STATUS_TAG, deliveryStatus);
        if (!definitionId.isBlank()) tag.putString(DEFINITION_ID_TAG, definitionId);
        if (!style.isBlank()) tag.putString(STYLE_TAG, style);
        if (!senderDisplay.isBlank()) tag.putString(SENDER_DISPLAY_TAG, senderDisplay);
        if (!senderAvatarItem.isBlank()) tag.putString(SENDER_AVATAR_ITEM_TAG, senderAvatarItem);
        if (!senderAvatarEntity.isBlank()) tag.putString(SENDER_AVATAR_ENTITY_TAG, senderAvatarEntity);
        if (deleteAfterRead) tag.putBoolean(DELETE_AFTER_READ_TAG, true);
        if (!notification.isBlank()) tag.putString(NOTIFICATION_TAG, notification);
        if (fromPlayer) tag.putBoolean(FROM_PLAYER_TAG, true);
        if (includeAttachments) {
            ListTag attachmentTags = new ListTag();
            for (AntmailAttachment attachment : attachments) attachmentTags.add(attachment.toTag());
            tag.put(ATTACHMENTS_TAG, attachmentTags);
        } else if (hasAttachments()) {
            tag.putBoolean("HasAttachments", true);
        }
        return tag;
    }

    public CompoundTag toListTag() {
        CompoundTag tag = toTag(false, 120);
        if (hasAttachments()) tag.putBoolean("HasAttachments", true);
        return tag;
    }

    public static AntmailMessage fromTag(CompoundTag tag) {
        List<AntmailAttachment> attachments = new ArrayList<>();
        ListTag attachmentTags = tag.getList(ATTACHMENTS_TAG, 10);
        for (int index = 0; index < attachmentTags.size(); index++) attachments.add(AntmailAttachment.fromTag(attachmentTags.getCompound(index)));
        AntmailMessage message = new AntmailMessage(UUID.fromString(tag.getString(ID_TAG)), AntmailAddress.parse(tag.getString(FROM_TAG)), AntmailAddress.parse(tag.getString(TO_TAG)), tag.getString(SUBJECT_TAG), tag.getString(BODY_TAG), tag.getLong(CREATED_TAG), attachments, tag.getBoolean(READ_TAG));
        message.setDeliveryStatus(tag.getString(DELIVERY_STATUS_TAG));
        message.definitionId = tag.getString(DEFINITION_ID_TAG);
        message.style = tag.getString(STYLE_TAG);
        message.senderDisplay = tag.getString(SENDER_DISPLAY_TAG);
        message.senderAvatarItem = tag.getString(SENDER_AVATAR_ITEM_TAG);
        message.senderAvatarEntity = tag.getString(SENDER_AVATAR_ENTITY_TAG);
        message.deleteAfterRead = tag.getBoolean(DELETE_AFTER_READ_TAG);
        message.notification = tag.getString(NOTIFICATION_TAG);
        message.fromPlayer = tag.getBoolean(FROM_PLAYER_TAG);
        message.hasAttachments = tag.getBoolean("HasAttachments") || !attachments.isEmpty();
        return message;
    }
}


