package co.turismo.api.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class LocalRateLimitStore implements RateLimitStore {

    private final Cache<String, Bucket> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    @Override
    public Mono<RateLimitDecision> consume(String key, RateLimitingFilter.PathConfig config) {
        Bucket bucket = cache.get(key, ignored -> newBucket(config));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        long resetSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).getSeconds());
        return Mono.just(new RateLimitDecision(
                probe.isConsumed(),
                config.capacity(),
                probe.isConsumed() ? probe.getRemainingTokens() : 0,
                resetSeconds
        ));
    }

    private Bucket newBucket(RateLimitingFilter.PathConfig config) {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(config.capacity())
                        .refillGreedy(config.refill(), config.window()))
                .build();
    }
}
