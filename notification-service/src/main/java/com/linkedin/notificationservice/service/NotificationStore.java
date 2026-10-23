package com.linkedin.notificationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.notificationservice.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Redis layout:
 *   notifications:{userId}          LIST of JSON, newest first, trimmed to MAX
 *   notifications:lastread:{userId} epoch millis; an item is "read" when createdAtMillis <= lastread
 * "Mark all read" is therefore one O(1) write instead of rewriting the list.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationStore {

    static final int MAX = 100;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public Notification add(String userId, String type, String title, String message, String refId) {
        long now = System.currentTimeMillis();
        Notification n = new Notification(UUID.randomUUID().toString(), type, title, message, refId,
                now, Instant.ofEpochMilli(now).toString().replace("Z", ""), false);
        try {
            String key = key(userId);
            redis.opsForList().leftPush(key, mapper.writeValueAsString(n));
            redis.opsForList().trim(key, 0, MAX - 1);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return n;
    }

    public List<Notification> list(String userId) {
        long lastRead = lastRead(userId);
        List<String> raw = redis.opsForList().range(key(userId), 0, MAX - 1);
        List<Notification> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String json : raw) {
            try {
                Notification n = mapper.readValue(json, Notification.class);
                n.setRead(n.getCreatedAtMillis() <= lastRead);
                out.add(n);
            } catch (JsonProcessingException e) {
                log.warn("Skipping unreadable notification for {}", userId);
            }
        }
        return out;
    }

    public long unreadCount(String userId) {
        return list(userId).stream().filter(n -> !n.isRead()).count();
    }

    public void markAllRead(String userId) {
        redis.opsForValue().set(lastReadKey(userId), String.valueOf(System.currentTimeMillis()));
    }

    private long lastRead(String userId) {
        String v = redis.opsForValue().get(lastReadKey(userId));
        try {
            return v == null ? 0L : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String key(String userId) {
        return "notifications:" + userId;
    }

    private static String lastReadKey(String userId) {
        return "notifications:lastread:" + userId;
    }
}
