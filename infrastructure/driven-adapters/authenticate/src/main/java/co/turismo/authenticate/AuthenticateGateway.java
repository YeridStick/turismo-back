package co.turismo.authenticate;

import co.turismo.model.authenticationsession.AuthenticationSession;
import co.turismo.model.authenticationsession.gateways.AuthenticationSessionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuthenticateGateway implements AuthenticationSessionRepository {

    private final Cache<String, String> verificationCodesCache;
    private final Cache<String, AuthenticationSession> sessionCache;

    @Value("${security.jwt.secret}")              private String jwtSecret;
    @Value("${security.jwt.issuer:turismo-app}")  private String jwtIssuer;
    @Value("${security.jwt.ttl-hours:4}")         private long jwtTtlHours;

    @Value("${admin.email}") private String adminEmail;

    /**
     * Genera JWT con lista de roles y crea sesión temporal.
     */
    @Override
    public Mono<String> generateToken(String email, Set<String> roles, String ip) {
        final String normEmail = normalize(email);
        final Set<String> normalizedRoles = normalizeSet(roles);

        final String token = generateJWT(normEmail, normalizedRoles);

        AuthenticationSession session = AuthenticationSession.builder()
                .token(token)
                .email(normEmail)
                .roles(normalizedRoles)
                .ip(ip)
                .expirationTime(LocalDateTime.now().plus(Duration.ofHours(jwtTtlHours)))
                .isValid(true)
                .build();

        sessionCache.put(token, session);
        return Mono.just(token);
    }

    @Override
    public Mono<Boolean> validateToken(String token, String ip) {
        return Mono.justOrEmpty(sessionCache.getIfPresent(token))
                .filter(session -> session.isValid()
                        && LocalDateTime.now().isBefore(session.getExpirationTime())
                        && (session.getIp() == null || session.getIp().equals(ip)))
                .map(s -> true)
                .switchIfEmpty(Mono.fromRunnable(() -> sessionCache.invalidate(token)).thenReturn(false));
    }

    @Override
    public Mono<Void> storeCode(String email, String code) {
        verificationCodesCache.put(normalize(email), code);
        return Mono.empty();
    }

    @Override
    public Mono<String> getStoredCode(String email) {
        return Mono.justOrEmpty(verificationCodesCache.getIfPresent(normalize(email)));
    }

    @Override
    public Mono<Void> invalidateCode(String email) {
        verificationCodesCache.invalidate(normalize(email));
        return Mono.empty();
    }

    /**
     * Genera un JWT firmado con HS256 que incluye claim "roles".
     */
    private String generateJWT(String email, Set<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofHours(jwtTtlHours));

        return Jwts.builder()
                .id(UUID.randomUUID().toString()) // jti
                .issuer(jwtIssuer)
                .subject(email)
                .claim("roles", new ArrayList<>(roles)) // claim "roles": ["ADMIN", "OWNER"]
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key) // HS256
                .compact();
    }

    @Override
    public String getAdminEmail() {
        return normalize(adminEmail);
    }

    private static String normalize(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }

    private static Set<String> normalizeSet(Set<String> roles) {
        if (roles == null || roles.isEmpty()) return Set.of();
        return roles.stream()
                .filter(Objects::nonNull)
                .map(AuthenticateGateway::normalize)
                .collect(Collectors.toSet());
    }
}
