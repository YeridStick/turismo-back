package co.turismo.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "turismo.rate-limiter")
@Getter
@Setter
public class RateLimiterProperties {
    private boolean enabled = true;
    private long windowSeconds = 60;
    private long capacity = 120;
    private long refillPerWindow = 120;

    // límites por prefijo de ruta (ej: "/api/auth")
    private Map<String, PathLimit> perPath;
    // rutas a excluir
    private List<String> skipPaths;

    public static class PathLimit {
        public long capacity = 60;
        public long windowSeconds = 60;
        public long refillPerWindow = capacity; // por defecto, full refill
    }
}
