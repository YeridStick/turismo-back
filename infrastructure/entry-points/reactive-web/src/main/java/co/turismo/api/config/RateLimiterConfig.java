package co.turismo.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterConfig {
    @Bean
    public RateLimitStore rateLimitStore() {
        return new LocalRateLimitStore();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    @ConditionalOnProperty(prefix = "turismo.rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitingFilter rateLimitingFilter(RateLimiterProperties props, RateLimitStore rateLimitStore) {
        return new RateLimitingFilter(props, rateLimitStore);
    }
}
