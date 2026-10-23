package com.linkedin.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthFilterTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));

    private GatewayFilter filter;
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = ex -> {
        forwarded.set(ex);
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        JwtAuthFilter factory = new JwtAuthFilter();
        ReflectionTestUtils.setField(factory, "secretKey", SECRET);
        filter = factory.apply(new JwtAuthFilter.Config());
    }

    private static String token(String type, String userId, long expiresInMillis, SecretKey key) {
        long now = System.currentTimeMillis();
        var b = Jwts.builder().claim("userId", userId).subject("ann@x.io")
                .issuedAt(new Date(now - 10_000)).expiration(new Date(now + expiresInMillis));
        if (type != null) {
            b.claim("type", type);
        }
        return b.signWith(key, Jwts.SIG.HS256).compact();
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> req) {
        MockServerWebExchange ex = MockServerWebExchange.from(req.build());
        filter.filter(ex, chain).block();
        return ex;
    }

    @Test
    void validAccessTokenForwardsWithTrustedIdentityHeaders() {
        run(MockServerHttpRequest.get("/api/v1/users/1").header("Authorization", "Bearer " + token("access", "user-7", 60_000, KEY)));
        ServerWebExchange f = forwarded.get();
        assertNotNull(f, "request should be forwarded");
        assertEquals("user-7", f.getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("ann@x.io", f.getRequest().getHeaders().getFirst("X-User-Email"));
    }

    @Test
    void clientSuppliedIdentityHeadersAreReplacedNotAppended() {
        run(MockServerHttpRequest.get("/api/v1/users/1")
                .header("Authorization", "Bearer " + token("access", "real-user", 60_000, KEY))
                .header("X-User-Id", "victim")
                .header("X-User-Email", "victim@x.io"));
        var h = forwarded.get().getRequest().getHeaders();
        assertEquals(1, h.get("X-User-Id").size());
        assertEquals("real-user", h.getFirst("X-User-Id"));
        assertEquals(1, h.get("X-User-Email").size());
    }

    @Test
    void tokenWithoutTypeClaimIsAcceptedForBackwardCompatibility() {
        run(MockServerHttpRequest.get("/x").header("Authorization", "Bearer " + token(null, "legacy", 60_000, KEY)));
        assertEquals("legacy", forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void refreshTokenIsRejectedWith401() {
        MockServerWebExchange ex = run(MockServerHttpRequest.get("/x").header("Authorization", "Bearer " + token("refresh", "u", 60_000, KEY)));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void expiredTokenIsRejectedWith401() {
        MockServerWebExchange ex = run(MockServerHttpRequest.get("/x").header("Authorization", "Bearer " + token("access", "u", -1_000, KEY)));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        SecretKey other = Keys.hmacShaKeyFor("0123456789012345678901234567890123456789".getBytes());
        MockServerWebExchange ex = run(MockServerHttpRequest.get("/x").header("Authorization", "Bearer " + token("access", "u", 60_000, other)));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
    }

    @Test
    void missingMalformedAndNonBearerHeadersAreRejected() {
        assertEquals(HttpStatus.UNAUTHORIZED, run(MockServerHttpRequest.get("/x")).getResponse().getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, run(MockServerHttpRequest.get("/x").header("Authorization", "Basic abc")).getResponse().getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, run(MockServerHttpRequest.get("/x").header("Authorization", "Bearer not.a.jwt")).getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() {
        String unsigned = Jwts.builder().claim("userId", "u").claim("type", "access").subject("a@b.c")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).compact();
        MockServerWebExchange ex = run(MockServerHttpRequest.get("/x").header("Authorization", "Bearer " + unsigned));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
    }
}
