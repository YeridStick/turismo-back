package co.turismo.authenticate.utils;

import co.turismo.authenticate.dto.SessionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SessionStore {

    private final ReactiveStringRedisTemplate redis;
    private final SessionCodec codec;

    @Value("${security.session.redis.key-prefix:turismo:auth}")
    private String prefix;

    public Mono<Void> save(String token, SessionSnapshot session, Instant expiration, Duration gracePeriod) {
        Duration ttl = positiveTtl(Duration.between(Instant.now(), expiration.plus(gracePeriod)));
        return redis.opsForValue()
                .set(sessionKey(token), codec.encode(session), ttl)
                .then();
    }

    public Mono<SessionSnapshot> load(String token) {
        return redis.opsForValue()
                .get(sessionKey(token))
                .filter(v -> v != null && !v.isBlank())
                .map(codec::decode);
    }

    public Mono<Void> delete(String token) {
        return redis.delete(sessionKey(token)).then();
    }

    private String sessionKey(String token) {
        return prefix + ":session:" + fingerprint(token);
    }

    private String fingerprint(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Error al generar fingerprint", e);
        }
    }

    private Duration positiveTtl(Duration ttl) {
        return (ttl == null || ttl.isNegative() || ttl.isZero()) ? Duration.ofSeconds(60) : ttl;
    }
}