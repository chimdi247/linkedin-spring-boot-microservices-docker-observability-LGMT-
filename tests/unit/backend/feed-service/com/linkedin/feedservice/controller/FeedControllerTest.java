package com.linkedin.feedservice.controller;

import com.linkedin.feedservice.service.FeedService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedControllerTest {

    private final FeedService feedService = mock(FeedService.class);
    private final FeedController controller = new FeedController(feedService);

    @Test
    void readingSomeoneElsesFeedIs403() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.getFeed("victim", "attacker", 0, 10));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        verifyNoInteractions(feedService);
    }

    @Test
    void clearingSomeoneElsesFeedIs403() {
        assertThrows(ResponseStatusException.class, () -> controller.clearFeed("victim", "attacker"));
        verifyNoInteractions(feedService);
    }

    @Test
    void ownFeedIsReturned() {
        when(feedService.getFeed("me", 0, 10)).thenReturn(List.of("p1"));
        ResponseEntity<List<String>> r = controller.getFeed("me", "me", 0, 10);
        assertEquals(List.of("p1"), r.getBody());
    }

    @Test
    void pageSizeIsClampedAndNegativePageBecomesZero() {
        controller.getFeed("me", "me", -5, 100_000);
        verify(feedService).getFeed("me", 0, 50);
        controller.getFeed("me", "me", 2, 0);
        verify(feedService).getFeed("me", 2, 1);
    }
}
