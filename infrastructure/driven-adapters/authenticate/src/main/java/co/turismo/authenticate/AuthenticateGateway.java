package co.turismo.authenticate;

import co.turismo.authenticate.dto.SessionSnapshot;
import co.turismo.authenticate.utils.JwtProvider;
import co.turismo.authenticate.utils.RevocationStore;
import co.turismo.authenticate.utils.SessionStore;
import co.turismo.model.authenticationsession.gateways.AuthenticationSessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuthenticateGateway implements AuthenticationSessionRepository {

    private final JwtProvider jwt;
    private final SessionStore sessions;
    private final RevocationStore revocations;

    @Value("${security.session.bind-ip:false}")           private boolean bindIp;
    @Value("${security.session.refresh-grace-minutes:15}") private long    graceMins;

    @Override
    public Mono<String> generateToken(String email, Set<String> roles, String ip) {
        String  jti        = UUID.randomUUID().toString();
        Instant expiration = jwt.nextExpiration();
        String  token      = jwt.generate(normalize(email), normalizeRoles(roles), jti, expiration);

        SessionSnapshot session = new SessionSnapshot(normalize(email), normalizeRoles(roles), bindIp ? ip : null);
        return sessions.save(token, session, expiration, Duration.ofMinutes(graceMins))
                .thenReturn(token);
    }

    @Override
    public Mono<Boolean> validateToken(String token, String ip) {
        return Mono.fromCallable(() -> jwt.parseStrict(token))
                .flatMap(claims -> revocations.isRevoked(claims.getId())
                        .flatMap(revoked -> revoked
                                ? Mono.just(false)
                                : sessions.load(token)
                                .map(session -> isIpValid(session.ip(), ip))
                                .defaultIfEmpty(false)))
                .onErrorReturn(false);
    }

    @Override
    public Mono<String> refreshToken(String oldToken, String ip) {
        Claims oldClaims   = jwt.parseAllowExpired(oldToken);
        Instant oldExpiry  = oldClaims.getExpiration().toInstant();
        Instant graceLimit = oldExpiry.plus(Duration.ofMinutes(graceMins));

        if (Instant.now().isAfter(graceLimit)) {
            return sessions.delete(oldToken)
                    .then(Mono.error(new IllegalStateException("Token fuera de ventana de gracia")));
        }

        return revocations.isRevoked(oldClaims.getId())
                .flatMap(revoked -> revoked
                        ? Mono.error(new IllegalStateException("Token revocado"))
                        : sessions.load(oldToken)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Sesión no encontrada")))
                        .flatMap(session -> {
                            if (!isIpValid(session.ip(), ip))
                                return Mono.error(new SecurityException("IP no coincide"));

                            Instant newExpiry = jwt.nextExpiration();
                            String  newToken  = jwt.generate(session.email(), session.roles(),
                                    UUID.randomUUID().toString(), newExpiry);
                            Duration graceTtl = Duration.between(Instant.now(), graceLimit);

                            return sessions.save(newToken, session, newExpiry, Duration.ofMinutes(graceMins))
                                    .then(revocations.revoke(oldClaims.getId(), graceTtl))
                                    .then(sessions.delete(oldToken))
                                    .thenReturn(newToken);
                        }));
    }

    @Override
    public Mono<Void> revokeToken(String token) {
        return Mono.fromCallable(() -> jwt.parseAllowExpired(token))
                .flatMap(claims -> {
                    Duration ttl = Duration.between(Instant.now(),
                            claims.getExpiration().toInstant().plus(Duration.ofMinutes(graceMins)));
                    return revocations.revoke(claims.getId(), ttl)
                            .then(sessions.delete(token));
                })
                .onErrorResume(e -> sessions.delete(token));
    }

    private boolean isIpValid(String sessionIp, String requestIp) {
        if (!bindIp || isBlank(sessionIp) || isBlank(requestIp)) return true;
        return Objects.equals(sessionIp, requestIp);
    }

    private static String normalize(String v)    { return v == null ? null : v.trim().toLowerCase(); }
    private static boolean isBlank(String v)     { return v == null || v.isBlank(); }
    private static Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null) return Set.of();
        return roles.stream().filter(Objects::nonNull).map(r -> r.trim().toLowerCase()).collect(Collectors.toSet());
    }
}
