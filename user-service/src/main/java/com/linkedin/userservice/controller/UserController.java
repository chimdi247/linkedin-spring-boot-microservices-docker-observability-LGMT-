package com.linkedin.userservice.controller;

import com.linkedin.userservice.dto.ConnectionStatusResponse;
import com.linkedin.userservice.dto.UserResponse;
import com.linkedin.userservice.entity.Connection;
import com.linkedin.userservice.exception.ApiException;
import com.linkedin.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * X-User-Id is injected by the API gateway after JWT validation (the gateway overwrites any
 * client supplied value). Path ids are only trusted for reads of public profile data.
 */
@RestController
@RequestMapping("/api/v1/users")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // NOTE: literal paths ("suggestions") win over "/{userId}" in Spring's matching.
    @GetMapping("/suggestions")
    public ResponseEntity<List<UserResponse>> suggestions(
            @RequestHeader("X-User-Id") String requestingUserId,
            @RequestParam(defaultValue = "12") int limit) {
        return ResponseEntity.ok(userService.getSuggestions(requestingUserId, limit));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfile(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId) {
        log.debug("Get profile: {} requested by: {}", userId, requestingUserId);
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    @PutMapping("/{userId}/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId,
            @RequestBody UserResponse request) {
        requireSelf(userId, requestingUserId);
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PostMapping("/{userId}/profile-photo")
    public ResponseEntity<UserResponse> uploadProfilePhoto(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId,
            @RequestParam("file") MultipartFile file) {
        requireSelf(userId, requestingUserId);
        return ResponseEntity.ok(userService.uploadProfilePhoto(userId, file));
    }

    @GetMapping("/{userId}/connection-status")
    public ResponseEntity<ConnectionStatusResponse> connectionStatus(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId) {
        return ResponseEntity.ok(userService.getConnectionStatus(requestingUserId, userId));
    }

    @PostMapping("/{targetUserId}/connect")
    public ResponseEntity<String> sendConnectionRequest(
            @PathVariable String targetUserId,
            @RequestHeader("X-User-Id") String requestingUserId) {
        return ResponseEntity.ok(userService.sendConnectionRequest(targetUserId, requestingUserId));
    }

    @PutMapping("/connection/{connectionId}/accept")
    public ResponseEntity<String> accept(
            @PathVariable String connectionId,
            @RequestHeader("X-User-Id") String requestingUserId) {
        return ResponseEntity.ok(userService.acceptConnectionRequest(connectionId, requestingUserId));
    }

    @PutMapping("/connection/{connectionId}/reject")
    public ResponseEntity<String> reject(
            @PathVariable String connectionId,
            @RequestHeader("X-User-Id") String requestingUserId) {
        return ResponseEntity.ok(userService.rejectConnectionRequest(connectionId, requestingUserId));
    }

    @GetMapping("/{userId}/connections/pending")
    public ResponseEntity<List<Connection>> pending(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId) {
        requireSelf(userId, requestingUserId);
        return ResponseEntity.ok(userService.getPendingConnections(userId));
    }

    @GetMapping("/{userId}/connections")
    public ResponseEntity<List<UserResponse>> connections(
            @PathVariable String userId,
            // optional: feed-service calls this service directly (service-to-service, no gateway)
            @RequestHeader(value = "X-User-Id", required = false) String requestingUserId) {
        return ResponseEntity.ok(userService.getConnections(userId));
    }

    private static void requireSelf(String userId, String requestingUserId) {
        if (!userId.equals(requestingUserId)) {
            throw ApiException.forbidden("You can only change your own data");
        }
    }
}
