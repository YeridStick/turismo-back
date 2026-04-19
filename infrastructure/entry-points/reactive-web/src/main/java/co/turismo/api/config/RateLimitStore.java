package co.turismo.api.config;

import reactor.core.publisher.Mono;

public interface RateLimitStore {
    Mono<RateLimitDecision> consume(String key, RateLimitingFilter.PathConfig config);
}
