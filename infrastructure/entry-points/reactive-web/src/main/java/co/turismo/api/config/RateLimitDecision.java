package co.turismo.api.config;

public record RateLimitDecision(
        boolean allowed,
        long limit,
        long remaining,
        long resetSeconds
) {
}
