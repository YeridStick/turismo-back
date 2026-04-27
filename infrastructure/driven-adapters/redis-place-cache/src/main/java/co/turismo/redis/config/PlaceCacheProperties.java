package co.turismo.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "turismo.cache.places")
public class PlaceCacheProperties {

    private Duration searchTtl = Duration.ofSeconds(60);
    private String keyPrefix = "places";
    private boolean enabled = true;
}
