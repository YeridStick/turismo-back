package co.turismo.authenticate.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import co.turismo.model.authenticationsession.AuthenticationSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, String> verificationCodesCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    @Bean
    public Cache<String, AuthenticationSession> sessionCache() {
        return Caffeine.newBuilder()
                .maximumSize(20_000)
                .expireAfterWrite(Duration.ofHours(4))
                .build();
    }
}
