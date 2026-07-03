package co.turismo.authenticate;

import co.turismo.authenticate.dto.SessionSnapshot;
import co.turismo.authenticate.utils.AuthSessionStore;
import co.turismo.authenticate.utils.JwtProvider;
import co.turismo.model.authenticationsession.gateways.AuthenticationSessionRepository;
import co.turismo.model.user.gateways.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuthenticateGateway implements AuthenticationSessionRepository {

    private final JwtProvider jwt;
    private final AuthSessionStore sessions;
    private final UserRepository userRepository;

    @Value("${security.session.bind-ip:false}")           private boolean bindIp;
    @Value("${security.session.refresh-grace-minutes:15}") private long    graceMins;

    @Override
    public Mono<String> generateToken(String email, Set<String> roles, String ip) {
        String  jti        = UUID.randomUUID().toString();
        Instant expiration = jwt.nextExpiration();
        String  token      = jwt.generate(normalize(email), normalizeRoles(roles), jti, expiration);

        SessionSnapshot session = new SessionSnapshot(normalize(email), normalizeRoles(roles), bindIp ? ip : null);
        return userRepository.findByEmail(session.email())
                .flatMap(user -> sessions.save(token, user.getId(), session, jti, expiration, Duration.ofMinutes(graceMins)))
                .thenReturn(token);
    }

    @Override
    public Mono<Boolean> validateToken(String token, String ip) {
        return Mono.fromCallable(() -> jwt.parseStrict(token))
                .flatMap(claims -> sessions.isRevoked(claims.getId())
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
            return sessions.revokeByToken(oldToken)
                    .then(Mono.error(new IllegalStateException("Token fuera de ventana de gracia")));
        }

        return sessions.isRevoked(oldClaims.getId())
                .flatMap(revoked -> revoked
                        ? Mono.error(new IllegalStateException("Token revocado"))
                        : sessions.load(oldToken)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Sesión no encontrada")))
                        .flatMap(session -> {
                            if (!isIpValid(session.ip(), ip))
                                return Mono.error(new SecurityException("IP no coincide"));

                            Instant newExpiry = jwt.nextExpiration();
                            String newJti = UUID.randomUUID().toString();
                            String newToken = jwt.generate(session.email(), session.roles(), newJti, newExpiry);
                            Duration graceTtl = Duration.between(Instant.now(), graceLimit);

                            return userRepository.findByEmail(session.email())
                                    .flatMap(user -> sessions.save(newToken, user.getId(), session,
                                            newJti, newExpiry, Duration.ofMinutes(graceMins)))
                                    .then(sessions.revokeByJti(oldClaims.getId(), graceTtl))
                                    .then(sessions.revokeByToken(oldToken))
                                    .thenReturn(newToken);
                        }));
    }

    @Override
    public Mono<Void> revokeToken(String token) {
        return Mono.fromCallable(() -> jwt.parseAllowExpired(token))
                .flatMap(claims -> {
                    Duration ttl = Duration.between(Instant.now(),
                            claims.getExpiration().toInstant().plus(Duration.ofMinutes(graceMins)));
                    return sessions.revokeByJti(claims.getId(), ttl)
                            .then(sessions.revokeByToken(token));
                })
                .onErrorResume(e -> sessions.revokeByToken(token));
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
