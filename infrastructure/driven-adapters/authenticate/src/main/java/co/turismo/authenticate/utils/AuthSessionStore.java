package co.turismo.authenticate.utils;

import co.turismo.authenticate.dto.SessionSnapshot;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuthSessionStore {

    private final DatabaseClient databaseClient;

    public Mono<Void> save(String token, Long userId, SessionSnapshot session, String jti,
                           Instant expiration, Duration gracePeriod) {
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                expiration.plus(positiveTtl(gracePeriod)),
                ZoneOffset.UTC
        );
        String[] roles = normalizeRoles(session.roles()).toArray(String[]::new);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                INSERT INTO auth_sessions (
                    token_hash, user_id, email, roles, ip, jti, expires_at, revoked_at
                )
                VALUES (
                    :tokenHash, :userId, :email, :roles, :ip, :jti, :expiresAt, NULL
                )
                ON CONFLICT (token_hash) DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    email = EXCLUDED.email,
                    roles = EXCLUDED.roles,
                    ip = EXCLUDED.ip,
                    jti = EXCLUDED.jti,
                    expires_at = EXCLUDED.expires_at,
                    revoked_at = NULL
                """)
                .bind("tokenHash", fingerprint(token))
                .bind("userId", userId)
                .bind("email", normalize(session.email()))
                .bind("roles", roles)
                .bind("jti", jti)
                .bind("expiresAt", expiresAt);

        spec = bindNullable(spec, "ip", session.ip(), String.class);

        return spec.fetch().rowsUpdated().then();
    }

    public Mono<SessionSnapshot> load(String token) {
        return databaseClient.sql("""
                SELECT email, roles, ip
                  FROM auth_sessions
                 WHERE token_hash = :tokenHash
                   AND expires_at > NOW()
                   AND revoked_at IS NULL
                 LIMIT 1
                """)
                .bind("tokenHash", fingerprint(token))
                .map((row, metadata) -> new SessionSnapshot(
                        row.get("email", String.class),
                        readRoles(row),
                        row.get("ip", String.class)
                ))
                .one();
    }

    public Mono<Void> revokeByToken(String token) {
        return databaseClient.sql("""
                UPDATE auth_sessions
                   SET revoked_at = COALESCE(revoked_at, NOW())
                 WHERE token_hash = :tokenHash
                """)
                .bind("tokenHash", fingerprint(token))
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> revokeByJti(String jti, Duration ttl) {
        if (isBlank(jti)) {
            return Mono.empty();
        }
        return databaseClient.sql("""
                UPDATE auth_sessions
                   SET revoked_at = COALESCE(revoked_at, NOW())
                 WHERE jti = :jti
                """)
                .bind("jti", jti)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Boolean> isRevoked(String jti) {
        if (isBlank(jti)) {
            return Mono.just(false);
        }
        return databaseClient.sql("""
                SELECT EXISTS (
                    SELECT 1
                      FROM auth_sessions
                     WHERE jti = :jti
                       AND revoked_at IS NOT NULL
                ) AS revoked
                """)
                .bind("jti", jti)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("revoked", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                           String name,
                                                           Object value,
                                                           Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private Set<String> readRoles(Row row) {
        Object value = row.get("roles");
        if (value instanceof String[] roles) {
            return normalizeRoles(Arrays.asList(roles));
        }
        if (value instanceof Collection<?> roles) {
            return roles.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(AuthSessionStore::normalize)
                    .filter(role -> !isBlank(role))
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    private static Set<String> normalizeRoles(Collection<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(AuthSessionStore::normalize)
                .filter(role -> !isBlank(role))
                .collect(Collectors.toSet());
    }

    private String fingerprint(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Error al generar fingerprint", e);
        }
    }

    private Duration positiveTtl(Duration ttl) {
        return (ttl == null || ttl.isNegative() || ttl.isZero()) ? Duration.ofSeconds(60) : ttl;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
