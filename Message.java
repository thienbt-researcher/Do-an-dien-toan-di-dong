package com.chatapp.model;

import java.time.LocalDateTime;

public class Message {

    private int messageId;
    private int senderId;
    private String senderFullname;
    private String content;
    private LocalDateTime sentAt;

    // Constructor rong
    public Message() {
    }

    // Constructor co tham so
    public Message(int messageId, int senderId, String content, LocalDateTime sentAt) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.content = content;
        this.sentAt = sentAt;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public String getSenderFullname() {
        return senderFullname;
    }

    public void setSenderFullname(String senderFullname) {
        this.senderFullname = senderFullname;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
