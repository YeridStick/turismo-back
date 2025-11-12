package co.turismo.api.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import co.turismo.api.http.ClientIp;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter implements WebFilter {

    private final RateLimiterProperties props;
    private final Cache<String, Bucket> cache;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RateLimitingFilter(RateLimiterProperties props) {
        this.props = props;
        this.cache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Si rate limiting está deshabilitado, continuar
        if (!props.isEnabled()) {
            return chain.filter(exchange);
        }

        // Permitir OPTIONS y HEAD sin rate limiting
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == HttpMethod.OPTIONS || method == HttpMethod.HEAD) {
            return chain.filter(exchange);
        }

        // Verificar si la ruta debe ser omitida
        final String path = exchange.getRequest().getPath().value();
        if (mustSkip(path)) {
            return chain.filter(exchange);
        }

        // Aplicar rate limiting
        final String clientIp = ClientIp.resolve(exchange.getRequest());
        final PathConfig cfg = resolvePathConfig(path);
        final String key = cfg.keyPrefix + clientIp;

        Bucket bucket = cache.get(key, k -> newBucket(cfg));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Request permitido, agregar headers informativos
            var headers = exchange.getResponse().getHeaders();
            headers.add("X-Rate-Limit-Limit", String.valueOf(cfg.capacity));
            headers.add("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            long resetSec = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).getSeconds());
            headers.add("X-Rate-Limit-Reset", String.valueOf(resetSec));
            return chain.filter(exchange);
        } else {
            // Rate limit excedido
            long secondsToWait = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).getSeconds());
            var resp = exchange.getResponse();
            resp.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            resp.getHeaders().set("Retry-After", String.valueOf(secondsToWait));
            resp.getHeaders().set("X-Rate-Limit-Limit", String.valueOf(cfg.capacity));
            resp.getHeaders().set("X-Rate-Limit-Remaining", "0");
            resp.getHeaders().set("X-Rate-Limit-Reset", String.valueOf(secondsToWait));
            resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            String body = """
                {"status":429,"message":"Demasiadas solicitudes. Intenta de nuevo en %d s.","data":null}
                """.formatted(secondsToWait);
            var buffer = resp.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return resp.writeWith(Mono.just(buffer));
        }
    }

    /**
     * Verifica si la ruta debe ser omitida del rate limiting
     */
    private boolean mustSkip(String path) {
        if (props.getSkipPaths() == null || props.getSkipPaths().isEmpty()) {
            return false;
        }

        for (String skipPattern : props.getSkipPaths()) {
            if (matcher.match(skipPattern, path)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Configuración de rate limit por path
     */
    private static class PathConfig {
        final long capacity;
        final Duration window;
        final long refill;
        final String keyPrefix;
        final String path;

        PathConfig(long capacity, long windowSeconds, long refill, String keyPrefix, String path) {
            this.capacity = capacity;
            this.window = Duration.ofSeconds(windowSeconds);
            this.refill = refill > 0 ? refill : capacity;
            this.keyPrefix = keyPrefix;
            this.path = path;
        }
    }

    /**
     * Resuelve la configuración de rate limit específica para un path
     */
    private PathConfig resolvePathConfig(String path) {
        Map<String, RateLimiterProperties.PathLimit> map = props.getPerPath();

        if (map != null && !map.isEmpty()) {
            // Buscar configuración específica por path
            for (var entry : map.entrySet()) {
                String pathPrefix = entry.getKey();
                if (path.startsWith(pathPrefix)) {
                    var pathLimit = entry.getValue();
                    long refill = pathLimit.refillPerWindow > 0
                            ? pathLimit.refillPerWindow
                            : pathLimit.capacity;

                    return new PathConfig(
                            pathLimit.capacity,
                            pathLimit.windowSeconds,
                            refill,
                            "rl:" + pathPrefix + ":",
                            pathPrefix
                    );
                }
            }
        }

        // Configuración por defecto
        long refill = props.getRefillPerWindow() > 0
                ? props.getRefillPerWindow()
                : props.getCapacity();

        return new PathConfig(
                props.getCapacity(),
                props.getWindowSeconds(),
                refill,
                "rl:default:",
                "default"
        );
    }

    /**
     * Crea un nuevo bucket con la configuración especificada
     */
    private Bucket newBucket(PathConfig cfg) {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(cfg.capacity)
                        .refillGreedy(cfg.refill, cfg.window))
                .build();
    }
}