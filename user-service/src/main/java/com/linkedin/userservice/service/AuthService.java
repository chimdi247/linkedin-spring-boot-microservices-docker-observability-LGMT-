package com.linkedin.userservice.service;

import com.linkedin.userservice.dto.AuthResponse;
import com.linkedin.userservice.dto.LoginRequest;
import com.linkedin.userservice.dto.RegisterRequest;
import com.linkedin.userservice.entity.User;
import com.linkedin.userservice.entity.UserRole;
import com.linkedin.userservice.exception.ApiException;
import com.linkedin.userservice.repository.UserRepository;
import com.linkedin.userservice.telemetry.Telemetry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private static final String USER_CREATED_TOPIC = "user.created";

    // Registration and login are deliberately NOT read-only: they must see the primary's latest data.
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String email = normalize(request.getEmail());
        log.info("Registering new user");

        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered");
        }

        User user = new User();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(email);
        user.setHeadline(request.getHeadline());
        user.setLocation(request.getLocation());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.NORMAL_USER);

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // lost a race against a concurrent registration with the same e-mail
            throw ApiException.conflict("Email already registered");
        }
        log.info("User registered: {}", savedUser.getId());
        Telemetry.userRegistered();

        // Publish user.created event - the search service indexes the user, the notification service greets them.
        Map<String, Object> userCreatedEvent = new HashMap<>();
        userCreatedEvent.put("userId", savedUser.getId());
        userCreatedEvent.put("firstName", savedUser.getFirstName());
        userCreatedEvent.put("lastName", savedUser.getLastName());
        userCreatedEvent.put("email", savedUser.getEmail());
        userCreatedEvent.put("headline", savedUser.getHeadline());
        userCreatedEvent.put("location", savedUser.getLocation());

        kafkaTemplate.send(USER_CREATED_TOPIC, savedUser.getId(), userCreatedEvent);
        log.info("user.created event published: {}", savedUser.getId());

        return buildAuthResponse(savedUser, generateToken(savedUser.getId(), savedUser.getEmail()));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.getEmail());

        User user = userRepository.findByEmail(email).orElse(null);

        // Same message for "unknown e-mail" and "wrong password": do not reveal which accounts exist.
        // BCrypt verification is CPU bound (~100 ms) so it is also what rate-limits brute force.
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            Telemetry.login(false);
            log.warn("Login failed ({})", user == null ? "unknown user" : "bad password");
            throw ApiException.unauthorized("Invalid email or password");
        }

        Telemetry.login(true);
        log.info("Login successful: {}", user.getId());
        return buildAuthResponse(user, generateToken(user.getId(), user.getEmail()));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Access token (type=access). The gateway rejects refresh tokens used as access tokens. */
    private String generateToken(String userId, String email) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("type", "access")
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Refresh token - would be exchanged for a new access token at a /auth/refresh endpoint
     * (not implemented yet). type=refresh so it can never be used to call the API.
     */
    private String generateRefreshToken(String userId) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("type", "refresh")
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(token);
        response.setRefreshToken(generateRefreshToken(user.getId()));
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        return response;
    }
}
