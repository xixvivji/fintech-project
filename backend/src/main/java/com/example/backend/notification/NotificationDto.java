package com.example.backend.notification;

public class NotificationDto {
    private final Long id;
    private final String type;
    private final String title;
    private final String body;
    private final String refType;
    private final Long refId;
    private final boolean read;
    private final long createdAt;

    public NotificationDto(Long id, String type, String title, String body, String refType, Long refId, boolean read, long createdAt) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.body = body;
        this.refType = refType;
        this.refId = refId;
        this.read = read;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public boolean isRead() { return read; }
    public long getCreatedAt() { return createdAt; }
}
