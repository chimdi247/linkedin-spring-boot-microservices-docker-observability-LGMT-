package com.linkedin.notificationservice.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private final NotificationStore store = mock(NotificationStore.class);
    private final NotificationService service = new NotificationService(store);

    @Test
    void userCreatedProducesWelcomeForTheNewUser() {
        service.consumeUserCreated(Map.of("userId", "u1", "firstName", "Ann"));
        verify(store).add(eq("u1"), eq("WELCOME"), anyString(), contains("Ann"), eq("u1"));
    }

    @Test
    void connectionRequestNotifiesTheReceiverWithRequesterAsRef() {
        service.consumeConnectionRequest(Map.of("requesterId", "a", "receiverId", "b"));
        verify(store).add(eq("b"), eq("CONNECTION_REQUEST"), anyString(), anyString(), eq("a"));
    }

    @Test
    void connectionAcceptedNotifiesTheRequesterWithReceiverAsRef() {
        service.consumeConnectionAccepted(Map.of("requesterId", "a", "receiverId", "b"));
        verify(store).add(eq("a"), eq("CONNECTION_ACCEPTED"), anyString(), anyString(), eq("b"));
    }

    @Test
    void likeNotifiesThePostAuthorWithPostIdAsRef() {
        service.consumePostLiked(Map.of("authorId", "author", "userId", "fan", "postId", "p1"));
        verify(store).add(eq("author"), eq("POST_LIKED"), anyString(), anyString(), eq("p1"));
    }

    @Test
    void noNotificationForLikingOrCommentingOnYourOwnPost() {
        service.consumePostLiked(Map.of("authorId", "me", "userId", "me", "postId", "p1"));
        service.consumePostCommented(Map.of("postAuthorId", "me", "authorId", "me", "postId", "p1"));
        verifyNoInteractions(store);
    }

    @Test
    void commentNotifiesThePostAuthor() {
        service.consumePostCommented(Map.of("postAuthorId", "author", "authorId", "fan", "postId", "p1"));
        verify(store).add(eq("author"), eq("POST_COMMENTED"), anyString(), anyString(), eq("p1"));
    }

    @Test
    void eventWithoutRecipientIsDropped() {
        Map<String, Object> e = new HashMap<>();
        e.put("postAuthorId", null);
        e.put("authorId", "fan");
        e.put("postId", "p1");
        service.consumePostCommented(e);
        verifyNoInteractions(store);
    }

    @Test
    void storeFailureIsRethrownSoKafkaCanRetry() {
        when(store.add(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("redis down"));
        assertThrows(IllegalStateException.class,
                () -> service.consumeConnectionRequest(Map.of("requesterId", "a", "receiverId", "b")));
    }
}
