package com.linkedin.feedservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedServiceTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ListOperations<String, Object> lists;
    private FeedService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(lists);
        service = new FeedService(redisTemplate);
    }

    @Test
    void pagesMapToRedisRanges() {
        when(lists.range("feed:u", 20, 29)).thenReturn(List.of("p1", "p2"));
        assertEquals(List.of("p1", "p2"), service.getFeed("u", 2, 10));
    }

    @Test
    void emptyOrMissingFeedIsAnEmptyList() {
        when(lists.range("feed:u", 0, 9)).thenReturn(null);
        assertTrue(service.getFeed("u", 0, 10).isEmpty());
        when(lists.range("feed:u", 0, 9)).thenReturn(List.of());
        assertTrue(service.getFeed("u", 0, 10).isEmpty());
    }

    @Test
    void clearDeletesTheUsersKey() {
        service.clearFeed("u");
        verify(redisTemplate).delete("feed:u");
    }
}
