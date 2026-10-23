package com.linkedin.searchservice.service;

import com.linkedin.searchservice.model.PostDocument;
import com.linkedin.searchservice.model.UserDocument;
import com.linkedin.searchservice.repository.PostSearchRepository;
import com.linkedin.searchservice.repository.UserSearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SearchEventConsumerTest {

    private final UserSearchRepository users = mock(UserSearchRepository.class);
    private final PostSearchRepository posts = mock(PostSearchRepository.class);
    private final SearchEventConsumer consumer = new SearchEventConsumer(users, posts);

    private static Map<String, Object> userEvent() {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", "u1");
        m.put("firstName", "Ann");
        m.put("lastName", "Lee");
        m.put("headline", "Dev");
        m.put("location", "Rome");
        m.put("email", "ann@x.io");
        return m;
    }

    @Test
    void userCreatedIndexesANewDocument() {
        when(users.findById("u1")).thenReturn(Optional.empty());
        consumer.consumeUserCreated(userEvent());
        ArgumentCaptor<UserDocument> d = ArgumentCaptor.forClass(UserDocument.class);
        verify(users).save(d.capture());
        assertEquals("u1", d.getValue().getId());
        assertEquals("Ann", d.getValue().getFirstName());
        assertEquals("ann@x.io", d.getValue().getEmail());
    }

    @Test
    void userUpdatedUpsertsWhenTheDocumentIsMissing() {
        when(users.findById("u1")).thenReturn(Optional.empty());
        Map<String, Object> e = userEvent();
        e.put("skills", List.of("Java"));
        e.put("profilePhotoUrl", "http://m/a.png");
        consumer.consumeUserUpdated(e);
        ArgumentCaptor<UserDocument> d = ArgumentCaptor.forClass(UserDocument.class);
        verify(users).save(d.capture());
        assertEquals(List.of("Java"), d.getValue().getSkills());
        assertEquals("http://m/a.png", d.getValue().getProfilePhotoUrl());
    }

    @Test
    void userUpdatedKeepsExistingFieldsThatTheEventDoesNotCarry() {
        UserDocument existing = new UserDocument();
        existing.setId("u1");
        existing.setEmail("keep@x.io");
        existing.setSkills(List.of("Go"));
        when(users.findById("u1")).thenReturn(Optional.of(existing));
        Map<String, Object> e = userEvent();
        e.remove("email");
        consumer.consumeUserUpdated(e);
        ArgumentCaptor<UserDocument> d = ArgumentCaptor.forClass(UserDocument.class);
        verify(users).save(d.capture());
        assertEquals("keep@x.io", d.getValue().getEmail());
        assertEquals(List.of("Go"), d.getValue().getSkills());
        assertEquals("Ann", d.getValue().getFirstName());
    }

    @Test
    void eventWithoutUserIdIsIgnored() {
        Map<String, Object> e = userEvent();
        e.remove("userId");
        consumer.consumeUserCreated(e);
        verify(users, never()).save(any());
    }

    @Test
    void indexingFailureIsRethrownSoKafkaRetries() {
        when(users.findById("u1")).thenReturn(Optional.empty());
        when(users.save(any())).thenThrow(new RuntimeException("es down"));
        assertThrows(IllegalStateException.class, () -> consumer.consumeUserCreated(userEvent()));
    }

    @Test
    void postCreatedIsIndexed() {
        consumer.consumePostCreated(Map.of("postId", "p1", "authorId", "u1", "content", "hello", "createdAt", "2026-01-01T00:00"));
        ArgumentCaptor<PostDocument> d = ArgumentCaptor.forClass(PostDocument.class);
        verify(posts).save(d.capture());
        assertEquals("p1", d.getValue().getId());
        assertEquals("hello", d.getValue().getContent());
    }
}
