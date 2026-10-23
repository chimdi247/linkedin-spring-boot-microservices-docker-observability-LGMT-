package com.linkedin.feedservice.controller;

import com.linkedin.feedservice.service.FeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feed")
@Slf4j
@RequiredArgsConstructor
public class FeedController {

    private static final int MAX_PAGE_SIZE = 50;

    private final FeedService feedService;

    /** Paginated list of post ids (newest first). The client hydrates them via GET /posts?ids=. Own feed only. */
    @GetMapping("/{userId}")
    public ResponseEntity<List<String>> getFeed(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        requireSelf(userId, requestingUserId);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        return ResponseEntity.ok(feedService.getFeed(userId, safePage, safeSize));
    }

    @DeleteMapping("/{userId}/cache")
    public ResponseEntity<String> clearFeed(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId) {
        requireSelf(userId, requestingUserId);
        feedService.clearFeed(userId);
        return ResponseEntity.ok("Feed cache cleared");
    }

    private static void requireSelf(String userId, String requestingUserId) {
        if (!userId.equals(requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only read your own feed");
        }
    }
}
