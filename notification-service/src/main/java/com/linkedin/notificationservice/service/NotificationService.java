package com.linkedin.notificationservice.service;

import com.linkedin.notificationservice.telemetry.Telemetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Turns Kafka domain events into per-user notifications stored in Redis.
 * Messages are deliberately name-free: the UI resolves refId to a profile.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationStore store;

    @KafkaListener(topics = "user.created")
    public void consumeUserCreated(@Payload Map<String, Object> payload) {
        String firstName = str(payload, "firstName");
        notify(str(payload, "userId"), "WELCOME", "Welcome to LinkedIn!",
                "Welcome" + (firstName == null ? "" : " " + firstName)
                        + "! Your account has been created. Start connecting with professionals.",
                str(payload, "userId"));
    }

    @KafkaListener(topics = "connection.requested")
    public void consumeConnectionRequest(@Payload Map<String, Object> payload) {
        notify(str(payload, "receiverId"), "CONNECTION_REQUEST", "New connection request",
                "Someone wants to connect with you.", str(payload, "requesterId"));
    }

    @KafkaListener(topics = "connection.accepted")
    public void consumeConnectionAccepted(@Payload Map<String, Object> payload) {
        // the *receiver* accepted -> tell the requester
        notify(str(payload, "requesterId"), "CONNECTION_ACCEPTED", "Connection accepted",
                "Your connection request was accepted. You are now connected!", str(payload, "receiverId"));
    }

    @KafkaListener(topics = "post.liked")
    public void consumePostLiked(@Payload Map<String, Object> payload) {
        if (sameUser(payload, "authorId", "userId")) {
            return; // do not notify people about their own likes
        }
        notify(str(payload, "authorId"), "POST_LIKED", "Someone liked your post",
                "Your post got a new like.", str(payload, "postId"));
    }

    @KafkaListener(topics = "post.commented")
    public void consumePostCommented(@Payload Map<String, Object> payload) {
        if (sameUser(payload, "postAuthorId", "authorId")) {
            return;
        }
        notify(str(payload, "postAuthorId"), "POST_COMMENTED", "New comment on your post",
                "Someone commented on your post.", str(payload, "postId"));
    }

    private void notify(String userId, String type, String title, String message, String refId) {
        if (userId == null) {
            log.warn("Dropping {} event without a recipient", type);
            Telemetry.created(type, false);
            return;
        }
        try {
            store.add(userId, type, title, message, refId);
            Telemetry.created(type, true);
            log.info("Notification {} stored for user {}", type, userId);
        } catch (Exception e) {
            Telemetry.created(type, false);
            log.error("Could not store {} notification for {}: {}", type, userId, e.toString(), e);
            throw e; // let the Kafka error handler retry
        }
    }

    private static boolean sameUser(Map<String, Object> p, String a, String b) {
        return a != null && p.get(a) != null && p.get(a).equals(p.get(b));
    }

    private static String str(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v == null ? null : v.toString();
    }
}
