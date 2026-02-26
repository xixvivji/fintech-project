package com.example.backend.notification;

import com.example.backend.auth.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final JwtService jwtService;

    public NotificationController(NotificationService notificationService, JwtService jwtService) {
        this.notificationService = notificationService;
        this.jwtService = jwtService;
    }

    @GetMapping
    public List<NotificationDto> list(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return notificationService.list(userId);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long id) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        notificationService.markRead(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
