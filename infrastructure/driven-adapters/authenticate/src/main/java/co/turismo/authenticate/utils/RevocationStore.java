package co.turismo.authenticate.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RevocationStore {

    private final ReactiveStringRedisTemplate redis;

    @Value("${security.session.redis.key-prefix:turismo:auth}")
    private String prefix;

    public Mono<Void> revoke(String jti, Duration ttl) {
        if (jti == null || jti.isBlank()) return Mono.empty();
        return redis.opsForValue().set(revokedKey(jti), "1", positiveTtl(ttl)).then();
    }

    public Mono<Boolean> isRevoked(String jti) {
        if (jti == null || jti.isBlank()) return Mono.just(false);
        return redis.hasKey(revokedKey(jti)).defaultIfEmpty(false);
    }

    private String revokedKey(String jti) { return prefix + ":revoked:" + jti; }

    private Duration positiveTtl(Duration ttl) {
        return (ttl == null || ttl.isNegative() || ttl.isZero()) ? Duration.ofSeconds(60) : ttl;
    }
}