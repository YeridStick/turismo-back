package co.turismo.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterConfig {
    @Bean
    public RateLimitStore rateLimitStore(
            RateLimiterProperties props,
            ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider
    ) {
        if ("redis".equalsIgnoreCase(props.getBackend())) {
            ReactiveStringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return new LocalRateLimitStore();
            }
            String keyPrefix = props.getRedis() != null ? props.getRedis().getKeyPrefix() : "turismo:rl";
            return new RedisRateLimitStore(redisTemplate, keyPrefix);
        }
        return new LocalRateLimitStore();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    @ConditionalOnProperty(prefix = "turismo.rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitingFilter rateLimitingFilter(RateLimiterProperties props, RateLimitStore rateLimitStore) {
        return new RateLimitingFilter(props, rateLimitStore);
    }
}
