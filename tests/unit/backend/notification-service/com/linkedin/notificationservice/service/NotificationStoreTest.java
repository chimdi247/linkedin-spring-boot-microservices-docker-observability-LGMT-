package com.linkedin.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.notificationservice.model.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationStoreTest {

    @Mock StringRedisTemplate redis;
    @Mock ListOperations<String, String> lists;
    @Mock ValueOperations<String, String> values;
    private final ObjectMapper mapper = new ObjectMapper();
    private NotificationStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForList()).thenReturn(lists);
        when(redis.opsForValue()).thenReturn(values);
        store = new NotificationStore(redis, mapper);
    }

    private String json(String id, long millis) throws Exception {
        return mapper.writeValueAsString(new Notification(id, "WELCOME", "t", "m", "ref", millis, "x", false));
    }

    @Test
    void addPushesToTheFrontAndKeepsOnlyTheNewestHundred() {
        Notification n = store.add("u1", "POST_LIKED", "title", "msg", "p1");
        verify(lists).leftPush(eq("notifications:u1"), anyString());
        verify(lists).trim("notifications:u1", 0, 99);
        assertEquals("POST_LIKED", n.getType());
        assertEquals("p1", n.getRefId());
        assertFalse(n.isRead());
        assertNotNull(n.getId());
    }

    @Test
    void itemsOlderThanLastReadAreReadAndNewerAreUnread() throws Exception {
        when(lists.range("notifications:u1", 0, 99)).thenReturn(List.of(json("new", 2000), json("old", 1000)));
        when(values.get("notifications:lastread:u1")).thenReturn("1500");
        List<Notification> out = store.list("u1");
        assertEquals(2, out.size());
        assertFalse(out.get(0).isRead());
        assertTrue(out.get(1).isRead());
        assertEquals(1, store.unreadCount("u1"));
    }

    @Test
    void withoutLastReadEverythingIsUnread() throws Exception {
        when(lists.range("notifications:u1", 0, 99)).thenReturn(List.of(json("a", 5), json("b", 6)));
        when(values.get("notifications:lastread:u1")).thenReturn(null);
        assertEquals(2, store.unreadCount("u1"));
    }

    @Test
    void markAllReadStoresATimestampInsteadOfRewritingTheList() {
        store.markAllRead("u1");
        verify(values).set(eq("notifications:lastread:u1"), matches("\\d+"));
        verify(lists, never()).leftPush(any(), any());
    }

    @Test
    void corruptEntriesAreSkippedAndEmptyListIsEmpty() throws Exception {
        when(lists.range("notifications:u1", 0, 99)).thenReturn(List.of("{not json", json("ok", 1)));
        assertEquals(1, store.list("u1").size());
        when(lists.range("notifications:u2", 0, 99)).thenReturn(null);
        assertTrue(store.list("u2").isEmpty());
    }

    @Test
    void garbageLastReadValueIsTreatedAsZero() throws Exception {
        when(lists.range("notifications:u1", 0, 99)).thenReturn(List.of(json("a", 5)));
        when(values.get("notifications:lastread:u1")).thenReturn("abc");
        assertEquals(1, store.unreadCount("u1"));
    }
}
