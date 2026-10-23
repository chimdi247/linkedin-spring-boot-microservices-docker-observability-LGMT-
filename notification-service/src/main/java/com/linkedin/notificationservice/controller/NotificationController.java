package com.linkedin.notificationservice.controller;

import com.linkedin.notificationservice.model.Notification;
import com.linkedin.notificationservice.service.NotificationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Identity comes from X-User-Id (set by the gateway after JWT validation). */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationStore store;

    @GetMapping
    public List<Notification> list(@RequestHeader("X-User-Id") String userId) {
        return store.list(userId);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@RequestHeader("X-User-Id") String userId) {
        return Map.of("count", store.unreadCount(userId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll(@RequestHeader("X-User-Id") String userId) {
        store.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
