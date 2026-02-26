package com.example.backend.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationDto createForUser(Long userId, String type, String title, String body, String refType, Long refId) {
        NotificationEntity e = new NotificationEntity();
        e.setUserId(userId);
        e.setType(type);
        e.setTitle(title);
        e.setBody(body);
        e.setRefType(refType);
        e.setRefId(refId);
        e.setReadYn(false);
        e.setCreatedAt(System.currentTimeMillis());
        return toDto(notificationRepository.save(e));
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        NotificationEntity e = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found."));
        if (!e.getUserId().equals(userId)) throw new IllegalArgumentException("Not allowed.");
        e.setReadYn(true);
        notificationRepository.save(e);
    }

    @Transactional
    public void markAllRead(Long userId) {
        List<NotificationEntity> rows = notificationRepository.findByUserIdAndReadYnFalse(userId);
        for (NotificationEntity row : rows) row.setReadYn(true);
        notificationRepository.saveAll(rows);
    }

    private NotificationDto toDto(NotificationEntity e) {
        return new NotificationDto(e.getId(), e.getType(), e.getTitle(), e.getBody(), e.getRefType(), e.getRefId(), e.isReadYn(), e.getCreatedAt());
    }
}
