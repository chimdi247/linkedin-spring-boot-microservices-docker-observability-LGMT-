package com.linkedin.apigateway.filter;

import com.linkedin.apigateway.telemetry.Telemetry;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

/**
 * JWT authentication filter - applied to every route except /api/v1/auth/**.
 *
 * 1. read the Bearer token, 2. verify signature + expiry, 3. accept only access tokens
 * (a refresh token must not open the API), 4. REPLACE (never append) X-User-Id / X-User-Email so a
 * client can not impersonate another user by sending its own header, 5. forward.
 * Anything wrong -> 401 with an empty body.
 */
@Component
@Slf4j
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    @Value("${jwt.secret-key}")
    private String secretKey;

    private SecretKey signingKey;

    public JwtAuthFilter() {
        super(Config.class);
    }

    private SecretKey key() {
        if (signingKey == null) {
            signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        }
        return signingKey;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("Missing or malformed Authorization header");
                return reject(exchange, "missing_token");
            }

            try {
                Claims claims = Jwts.parser().verifyWith(key()).build()
                        .parseSignedClaims(authHeader.substring(7)).getPayload();

                String type = claims.get("type", String.class);
                if (type != null && !"access".equals(type)) {
                    return reject(exchange, "wrong_token_type");
                }
                String userId = claims.get("userId", String.class);
                if (userId == null || userId.isBlank()) {
                    return reject(exchange, "no_user");
                }
                String email = claims.getSubject() == null ? "" : claims.getSubject();

                Telemetry.authAccepted();
                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r.headers(h -> {
                            h.remove("X-User-Id");
                            h.remove("X-User-Email");
                            h.set("X-User-Id", userId);
                            h.set("X-User-Email", email);
                        }))
                        .build();
                return chain.filter(mutated);
            } catch (ExpiredJwtException e) {
                return reject(exchange, "expired");
            } catch (Exception e) {
                log.warn("JWT validation failed: {}", e.getClass().getSimpleName());
                return reject(exchange, "invalid");
            }
        };
    }

    private Mono<Void> reject(ServerWebExchange exchange, String reason) {
        Telemetry.authRejected(reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // no options
    }
}
