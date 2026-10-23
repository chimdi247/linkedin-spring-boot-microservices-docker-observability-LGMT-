package com.linkedin.feedservice.service;

import com.linkedin.feedservice.client.UserServiceClient;
import com.linkedin.feedservice.telemetry.Telemetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeedEventConsumer {

    private final UserServiceClient userServiceClient;
    private static final String FEED_KEY_PREFIX = "feed:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${feed.max-size}")
    private int maxFeedSize;

    /**
     * Consume post.created event
     * When a user creates a post - immediately push that post
     * to All thier connections feed.
     *
     * This is how LinkedIn/ Twitter generated feed.
     * @param payload
     */
    @KafkaListener(topics = "post.created")
    public void consumePostCreated(
            @Payload Map<String, Object> payload){

        String postId = (String) payload.get("postId");
        String authorId = (String) payload.get("authorId");
        if (postId == null || authorId == null) {
            log.warn("Ignoring malformed post.created event: {}", payload);
            Telemetry.eventProcessed(false);
            return;
        }

        // Author's own feed first: it must work even when user-service is unreachable.
        push(FEED_KEY_PREFIX + authorId, postId);

        int recipients = 0;
        try {
            // Fan-out on write: push the post to every connection (both directions) of the author.
            List<Map<String, Object>> connections = userServiceClient.getConnections(authorId);
            for (Map<String, Object> connection : connections) {
                String connectionId = (String) connection.get("id");
                if (connectionId == null || connectionId.equals(authorId)) {
                    continue;
                }
                push(FEED_KEY_PREFIX + connectionId, postId);
                recipients++;
            }
            Telemetry.fanout(recipients);
            Telemetry.eventProcessed(true);
            log.info("Post {} fanned out to {} feeds", postId, recipients);
        } catch (Exception e) {
            Telemetry.eventProcessed(false);
            log.error("Fan-out failed for post {}: {}", postId, e.toString(), e);
            // rethrow so the Kafka error handler retries / records the failure instead of silently dropping it
            throw new IllegalStateException("fan-out failed for post " + postId, e);
        }
    }

    private void push(String feedKey, String postId) {
        redisTemplate.opsForList().remove(feedKey, 0, postId); // idempotent on Kafka redelivery
        redisTemplate.opsForList().leftPush(feedKey, postId);
        redisTemplate.opsForList().trim(feedKey, 0, maxFeedSize - 1);
    }
}
