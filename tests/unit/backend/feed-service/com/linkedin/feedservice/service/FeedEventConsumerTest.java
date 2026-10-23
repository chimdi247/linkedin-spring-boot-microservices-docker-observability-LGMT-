package com.linkedin.feedservice.service;

import com.linkedin.feedservice.client.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedEventConsumerTest {

    @Mock UserServiceClient userServiceClient;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ListOperations<String, Object> lists;

    private FeedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(lists);
        consumer = new FeedEventConsumer(userServiceClient, redisTemplate);
        ReflectionTestUtils.setField(consumer, "maxFeedSize", 50);
    }

    private static Map<String, Object> event(String postId, String authorId) {
        return Map.of("postId", postId, "authorId", authorId);
    }

    @Test
    void authorFeedIsWrittenFirstThenEveryConnection() {
        when(userServiceClient.getConnections("author")).thenReturn(List.of(Map.of("id", "c1"), Map.of("id", "c2")));

        consumer.consumePostCreated(event("p1", "author"));

        InOrder order = inOrder(lists);
        order.verify(lists).leftPush("feed:author", "p1");
        order.verify(lists).leftPush("feed:c1", "p1");
        order.verify(lists).leftPush("feed:c2", "p1");
    }

    @Test
    void feedsAreTrimmedToMaxSize() {
        when(userServiceClient.getConnections("author")).thenReturn(List.of(Map.of("id", "c1")));
        consumer.consumePostCreated(event("p1", "author"));
        verify(lists).trim("feed:author", 0, 49);
        verify(lists).trim("feed:c1", 0, 49);
    }

    @Test
    void pushIsIdempotentOnKafkaRedeliveryByRemovingTheIdFirst() {
        when(userServiceClient.getConnections("author")).thenReturn(List.of());
        consumer.consumePostCreated(event("p1", "author"));
        InOrder order = inOrder(lists);
        order.verify(lists).remove("feed:author", 0, "p1");
        order.verify(lists).leftPush("feed:author", "p1");
    }

    @Test
    void authorIsNotPushedTwiceWhenListedAsOwnConnection() {
        when(userServiceClient.getConnections("author")).thenReturn(List.of(Map.of("id", "author"), Map.of("id", "c1")));
        consumer.consumePostCreated(event("p1", "author"));
        verify(lists, times(1)).leftPush("feed:author", "p1");
    }

    @Test
    void whenUserServiceFailsAuthorFeedIsStillWrittenAndExceptionPropagatesForRetry() {
        when(userServiceClient.getConnections("author")).thenThrow(new RuntimeException("down"));
        assertThrows(IllegalStateException.class, () -> consumer.consumePostCreated(event("p1", "author")));
        verify(lists).leftPush("feed:author", "p1");
        verify(lists, never()).leftPush(eq("feed:c1"), any());
    }

    @Test
    void malformedEventIsIgnored() {
        consumer.consumePostCreated(Map.of("content", "no ids"));
        verifyNoInteractions(lists, userServiceClient);
    }
}
