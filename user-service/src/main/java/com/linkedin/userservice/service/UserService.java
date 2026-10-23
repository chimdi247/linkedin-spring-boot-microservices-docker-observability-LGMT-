package com.linkedin.userservice.service;

import com.linkedin.userservice.dto.ConnectionStatusResponse;
import com.linkedin.userservice.dto.UserResponse;
import com.linkedin.userservice.entity.Connection;
import com.linkedin.userservice.entity.ConnectionStatus;
import com.linkedin.userservice.entity.User;
import com.linkedin.userservice.exception.ApiException;
import com.linkedin.userservice.repository.ConnectionRepository;
import com.linkedin.userservice.repository.UserRepository;
import com.linkedin.userservice.telemetry.Telemetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String CONNECTION_REQUESTED_TOPIC = "connection.requested";
    private static final String CONNECTION_ACCEPTED_TOPIC = "connection.accepted";
    private static final String USER_UPDATED_TOPIC = "user.updated";

    // Methods that read data just written by the caller (connections) stay on the primary
    // (no readOnly) to avoid replica lag; pure profile/suggestion reads go to the replica.

    @Transactional
    public String sendConnectionRequest(String receiverId, String requesterId) {

        if (receiverId.equals(requesterId)) {
            throw ApiException.badRequest("You cannot connect with yourself");
        }
        if (!userRepository.existsById(receiverId)) {
            throw ApiException.notFound("User not found: " + receiverId);
        }
        if (!connectionRepository.findBetween(requesterId, receiverId).isEmpty()) {
            throw ApiException.conflict("Connection request already sent or already connected");
        }

        Connection connection = new Connection();
        connection.setRequesterId(requesterId);
        connection.setReceiverId(receiverId);
        connection.setStatus(ConnectionStatus.PENDING);
        connection.setUpdatedAt(LocalDateTime.now());
        connectionRepository.save(connection);

        Map<String, Object> event = new HashMap<>();
        event.put("requesterId", requesterId);
        event.put("receiverId", receiverId);
        kafkaTemplate.send(CONNECTION_REQUESTED_TOPIC, requesterId, event);

        Telemetry.connectionRequested();
        log.info("Connection request sent: {} -> {}", requesterId, receiverId);
        return "Connection request sent";
    }

    @Transactional
    public List<Connection> getPendingConnections(String userId) {
        return connectionRepository.findByReceiverIdAndStatus(userId, ConnectionStatus.PENDING);
    }

    @Transactional
    public String acceptConnectionRequest(String connectionId, String requestingUserId) {
        Connection connection = pendingForReceiver(connectionId, requestingUserId);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setUpdatedAt(LocalDateTime.now());
        connectionRepository.save(connection);

        Map<String, Object> event = new HashMap<>();
        event.put("requesterId", connection.getRequesterId());
        event.put("receiverId", connection.getReceiverId());
        kafkaTemplate.send(CONNECTION_ACCEPTED_TOPIC, connection.getRequesterId(), event);

        Telemetry.connectionAccepted();
        log.info("Connection accepted: {}", connectionId);
        return "Connection accepted";
    }

    @Transactional
    public String rejectConnectionRequest(String connectionId, String requestingUserId) {
        Connection connection = pendingForReceiver(connectionId, requestingUserId);
        connection.setStatus(ConnectionStatus.REJECTED);
        connection.setUpdatedAt(LocalDateTime.now());
        connectionRepository.save(connection);
        log.info("Connection rejected: {}", connectionId);
        return "Connection rejected";
    }

    private Connection pendingForReceiver(String connectionId, String requestingUserId) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> ApiException.notFound("Connection not found: " + connectionId));
        if (!connection.getReceiverId().equals(requestingUserId)) {
            throw ApiException.forbidden("Only the receiver can answer a connection request");
        }
        if (connection.getStatus() != ConnectionStatus.PENDING) {
            throw ApiException.conflict("Connection request is no longer pending");
        }
        return connection;
    }

    @Transactional
    public ConnectionStatusResponse getConnectionStatus(String callerId, String otherId) {
        if (callerId.equals(otherId)) {
            return new ConnectionStatusResponse("SELF", null);
        }
        List<Connection> rows = connectionRepository.findBetween(callerId, otherId);
        if (rows.isEmpty()) {
            return new ConnectionStatusResponse("NONE", null);
        }
        // prefer an active relationship over an old rejected one
        Connection c = rows.stream()
                .min(Comparator.comparingInt(x -> x.getStatus() == ConnectionStatus.REJECTED ? 1 : 0))
                .get();
        String status;
        switch (c.getStatus()) {
            case CONNECTED:
                status = "CONNECTED";
                break;
            case REJECTED:
                status = "REJECTED";
                break;
            default:
                status = c.getRequesterId().equals(callerId) ? "PENDING_SENT" : "PENDING_RECEIVED";
        }
        return new ConnectionStatusResponse(status, c.getId());
    }

    /** Accepted connections of a user, whichever side sent the original request. */
    @Transactional
    public List<UserResponse> getConnections(String userId) {
        List<String> ids = connectionRepository.findAcceptedFor(userId).stream()
                .map(c -> c.getRequesterId().equals(userId) ? c.getReceiverId() : c.getRequesterId())
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(ids).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /** People you may know: not you, not connected, no pending/rejected request either way. */
    @Transactional(readOnly = true)
    public List<UserResponse> getSuggestions(String userId, int limit) {
        Set<String> exclude = new HashSet<>();
        exclude.add(userId); // never empty -> valid "NOT IN"
        for (Connection c : connectionRepository.findAllInvolving(userId)) {
            exclude.add(c.getRequesterId());
            exclude.add(c.getReceiverId());
        }
        int size = Math.max(1, Math.min(limit, 50));
        return userRepository.findByIdNotIn(exclude, PageRequest.of(0, size)).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserProfile(String userId) {
        return mapToResponse(find(userId));
    }

    @Transactional
    public UserResponse updateProfile(String userId, UserResponse request) {
        User user = find(userId);

        user.setHeadline(request.getHeadline());
        user.setAbout(request.getAbout());
        user.setLocation(request.getLocation());
        List<String> skills = request.getSkills() == null ? new ArrayList<>() : request.getSkills().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct()
                .collect(Collectors.toList());
        user.getSkills().clear();
        user.getSkills().addAll(skills);

        User saved = userRepository.save(user);
        publishUserUpdated(saved);
        Telemetry.profileUpdated();
        return mapToResponse(saved);
    }

    @Transactional
    public UserResponse uploadProfilePhoto(String userId, MultipartFile file) {
        User user = find(userId);
        String photoUrl = s3Service.uploadImage(file, "profiles/" + userId + "/avatar");
        user.setProfilePhotoUrl(photoUrl);
        User saved = userRepository.save(user);
        publishUserUpdated(saved);
        Telemetry.profileUpdated();
        log.info("Profile photo uploaded for user: {}", userId);
        return mapToResponse(saved);
    }

    private void publishUserUpdated(User u) {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", u.getId());
        event.put("firstName", u.getFirstName());
        event.put("lastName", u.getLastName());
        event.put("email", u.getEmail());
        event.put("headline", u.getHeadline());
        event.put("location", u.getLocation());
        event.put("skills", new ArrayList<>(u.getSkills()));
        event.put("profilePhotoUrl", u.getProfilePhotoUrl());
        kafkaTemplate.send(USER_UPDATED_TOPIC, u.getId(), event);
        log.info("user.updated event published: {}", u.getId());
    }

    private User find(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found: " + userId));
    }

    private UserResponse mapToResponse(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setEmail(user.getEmail());
        r.setFirstName(user.getFirstName());
        r.setLastName(user.getLastName());
        r.setHeadline(user.getHeadline());
        r.setAbout(user.getAbout());
        r.setLocation(user.getLocation());
        r.setProfilePhotoUrl(user.getProfilePhotoUrl());
        r.setCoverPhotoUrl(user.getCoverPhotoUrl());
        r.setRole(user.getRole());
        r.setSkills(new ArrayList<>(user.getSkills()));
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }
}
