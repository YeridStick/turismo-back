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

    private final Cache<String, AuthenticationSession> sessionCache;

    @Value("${security.jwt.secret}")             private String jwtSecret;       // >= 32 chars
    @Value("${security.jwt.issuer:turismo-app}") private String jwtIssuer;
    @Value("${security.jwt.ttl-hours:4}")        private long jwtTtlHours;
    @Value("${security.session.bind-ip:false}")  private boolean bindIp;        // opcional

    /**
     * Genera JWT con lista de roles y crea sesión en caché.
     */
    @Override
    public Mono<String> generateToken(String email, Set<String> roles, String ip) {
        final String normEmail = normalize(email);
        final Set<String> normalizedRoles = normalizeSet(roles);

        final String token = generateJWT(normEmail, normalizedRoles);

        final LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofHours(jwtTtlHours));
        AuthenticationSession session = AuthenticationSession.builder()
                .token(token)
                .email(normEmail)
                .roles(normalizedRoles)
                .ip(bindIp ? ip : null) // sólo ata a IP si está habilitado
                .expirationTime(expiresAt)
                .isValid(true)
                .build();

        sessionCache.put(token, session);
        return Mono.just(token);
    }

    /**
     * Valida que la sesión exista, no esté vencida y (opcional) coincida la IP.
     * Nota: si quieres además validar criptográficamente el JWT en cada request,
     * hazlo en un WebFilter leyendo el header y verificando firma/exp con jjwt.
     */
    @Override
    public Mono<Boolean> validateToken(String token, String ip) {
        return Mono.justOrEmpty(sessionCache.getIfPresent(token))
                .filter(s -> s.isValid()
                        && LocalDateTime.now().isBefore(s.getExpirationTime())
                        && (!bindIp || s.getIp() == null || Objects.equals(s.getIp(), ip)))
                .map(s -> true)
                .switchIfEmpty(Mono.fromRunnable(() -> sessionCache.invalidate(token)).thenReturn(false));
    }

    // --------- Métodos legacy de OTP por email (no se usan con TOTP) ---------
    @Override public Mono<Void> storeCode(String email, String code) { return Mono.empty(); }
    @Override public Mono<String> getStoredCode(String email) { return Mono.empty(); }
    @Override public Mono<Void> invalidateCode(String email) { return Mono.empty(); }

    // Si tu interfaz aún exige adminEmail pero ya no lo usas, devuélvelo vacío o elimínalo del contrato
    @Deprecated
    @Override
    public String getAdminEmail() { return null; }

    // -------------------- helpers --------------------
    private String generateJWT(String email, Set<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofHours(jwtTtlHours));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())       // jti
                .issuer(jwtIssuer)                      // iss
                .subject(email)                         // sub
                .claim("roles", new ArrayList<>(roles)) // roles: ["admin","owner",...]
                .issuedAt(Date.from(now))               // iat
                .expiration(Date.from(exp))             // exp
                .signWith(key)                          // HS256
                .compact();
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
