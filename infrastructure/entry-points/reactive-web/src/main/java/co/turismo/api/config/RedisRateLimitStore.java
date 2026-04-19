package co.turismo.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class RedisRateLimitStore implements RateLimitStore {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String keyPrefix;

    @Override
    public Mono<RateLimitDecision> consume(String key, RateLimitingFilter.PathConfig config) {
        long nowEpochSeconds = Instant.now().getEpochSecond();
        long windowSize = config.window().toSeconds();
        long windowStart = (nowEpochSeconds / windowSize) * windowSize;
        long windowEnd = windowStart + windowSize;
        long resetSeconds = Math.max(1, windowEnd - nowEpochSeconds);

        String redisKey = keyPrefix + ":" + key + ":" + windowStart;

        return redisTemplate.opsForValue()
                .increment(redisKey)
                .flatMap(counter -> {
                    Mono<Boolean> ensureExpiry = (counter != null && counter == 1L)
                            ? redisTemplate.expire(redisKey, Duration.ofSeconds(windowSize + 5))
                            : Mono.just(Boolean.TRUE);

                    long current = counter == null ? 0 : counter;
                    boolean allowed = current <= config.capacity();
                    long remaining = allowed
                            ? Math.max(0, config.capacity() - current)
                            : 0;

                    return ensureExpiry.thenReturn(new RateLimitDecision(
                            allowed,
                            config.capacity(),
                            remaining,
                            resetSeconds
                    ));
                });
    }
}
