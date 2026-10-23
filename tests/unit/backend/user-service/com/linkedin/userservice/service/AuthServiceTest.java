package com.linkedin.userservice.service;

import com.linkedin.userservice.dto.AuthResponse;
import com.linkedin.userservice.dto.LoginRequest;
import com.linkedin.userservice.dto.RegisterRequest;
import com.linkedin.userservice.entity.User;
import com.linkedin.userservice.entity.UserRole;
import com.linkedin.userservice.exception.ApiException;
import com.linkedin.userservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Mock UserRepository userRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, encoder, kafkaTemplate);
        ReflectionTestUtils.setField(service, "secretKey", SECRET);
        ReflectionTestUtils.setField(service, "jwtExpiration", 60_000L);
        ReflectionTestUtils.setField(service, "refreshExpiration", 120_000L);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-1");
            return u;
        });
    }

    private static RegisterRequest register(String email) {
        RegisterRequest r = new RegisterRequest();
        r.setFirstName(" Ann ");
        r.setLastName("Lee");
        r.setEmail(email);
        r.setPassword("secret1");
        r.setHeadline("Dev");
        r.setLocation("Rome");
        return r;
    }

    private static Claims parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void registerWithExistingEmailIsConflict409() {
        when(userRepository.existsByEmail("ann@x.io")).thenReturn(true);
        ApiException e = assertThrows(ApiException.class, () -> service.register(register("ann@x.io")));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerNormalizesEmailAndTrimsNames() {
        service.register(register("  Ann@X.IO "));
        verify(userRepository).existsByEmail("ann@x.io");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertEquals("ann@x.io", saved.getValue().getEmail());
        assertEquals("Ann", saved.getValue().getFirstName());
        assertEquals(UserRole.NORMAL_USER, saved.getValue().getRole());
    }

    @Test
    void registerStoresBcryptHashNeverThePlainPassword() {
        service.register(register("a@b.co"));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        String stored = saved.getValue().getPassword();
        assertNotEquals("secret1", stored);
        assertTrue(stored.startsWith("$2"));
        assertTrue(encoder.matches("secret1", stored));
    }

    @Test
    void registerLosingRaceAgainstConcurrentInsertIsConflict409() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));
        ApiException e = assertThrows(ApiException.class, () -> service.register(register("a@b.co")));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void registerPublishesUserCreatedEvent() {
        service.register(register("a@b.co"));
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("user.created"), eq("user-1"), event.capture());
        Map<String, Object> m = (Map<String, Object>) event.getValue();
        assertEquals("user-1", m.get("userId"));
        assertEquals("a@b.co", m.get("email"));
    }

    @Test
    void registerReturnsAccessTokenWithTypeAccessAndRefreshTokenWithTypeRefresh() {
        AuthResponse r = service.register(register("a@b.co"));
        Claims access = parse(r.getAccessToken());
        assertEquals("access", access.get("type", String.class));
        assertEquals("user-1", access.get("userId", String.class));
        assertEquals("a@b.co", access.getSubject());
        assertEquals("refresh", parse(r.getRefreshToken()).get("type", String.class));
        assertEquals("Bearer", r.getTokenType());
    }

    private User stored(String email, String rawPassword) {
        User u = new User();
        u.setId("user-9");
        u.setEmail(email);
        u.setFirstName("Ann");
        u.setLastName("Lee");
        u.setPassword(encoder.encode(rawPassword));
        return u;
    }

    @Test
    void loginSucceedsAndLowercasesEmail() {
        when(userRepository.findByEmail("ann@x.io")).thenReturn(Optional.of(stored("ann@x.io", "pw12345")));
        LoginRequest req = new LoginRequest();
        req.setEmail(" ANN@x.io ");
        req.setPassword("pw12345");
        AuthResponse r = service.login(req);
        assertEquals("user-9", r.getUserId());
        assertEquals("access", parse(r.getAccessToken()).get("type", String.class));
    }

    @Test
    void loginFailureMessageIsIdenticalForUnknownUserAndWrongPassword() {
        when(userRepository.findByEmail("known@x.io")).thenReturn(Optional.of(stored("known@x.io", "right-pw")));
        when(userRepository.findByEmail("ghost@x.io")).thenReturn(Optional.empty());
        LoginRequest wrong = new LoginRequest();
        wrong.setEmail("known@x.io");
        wrong.setPassword("wrong-pw");
        LoginRequest unknown = new LoginRequest();
        unknown.setEmail("ghost@x.io");
        unknown.setPassword("whatever");

        ApiException a = assertThrows(ApiException.class, () -> service.login(wrong));
        ApiException b = assertThrows(ApiException.class, () -> service.login(unknown));
        assertEquals(HttpStatus.UNAUTHORIZED, a.getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, b.getStatus());
        assertEquals(a.getMessage(), b.getMessage());
        assertEquals("Invalid email or password", a.getMessage());
    }
}
