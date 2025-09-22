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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Order(Ordered.HIGHEST_PRECEDENCE + 10) // muy temprano en la cadena
public class RateLimitingFilter implements WebFilter {

    private final RateLimiterProperties props;
    private final Cache<String, Bucket> cache;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RateLimitingFilter(RateLimiterProperties props) {
        this.props = props;
        // hasta 100k claves activas, expiran si no se usan 30 min
        this.cache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!props.isEnabled()) return chain.filter(exchange);

        // Saltar preflight/HEAD para no bloquear CORS ni healthchecks
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == HttpMethod.OPTIONS || method == HttpMethod.HEAD) {
            return chain.filter(exchange);
        }

        final String path = exchange.getRequest().getPath().value();
        if (mustSkip(path)) return chain.filter(exchange);

        String clientIp = resolveClientIp(exchange);
        PathConfig cfg = resolvePathConfig(path);

        // Clave por IP (si quieres por ruta+IP, concatena ":" + cfg.path)
        String key = cfg.keyPrefix + clientIp;

        Bucket bucket = cache.get(key, k -> newBucket(cfg));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            // Headers informativos
            var headers = exchange.getResponse().getHeaders();
            headers.add("X-Rate-Limit-Limit", String.valueOf(cfg.capacity));
            headers.add("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            long resetSec = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).getSeconds());
            headers.add("X-Rate-Limit-Reset", String.valueOf(resetSec));
            return chain.filter(exchange);
        } else {
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

    private boolean mustSkip(String path) {
        if (props.getSkipPaths() == null) return false;
        for (String p : props.getSkipPaths()) {
            if (matcher.match(p, path)) return true;
        }
        return false;
    }

    private static class PathConfig {
        final long capacity;
        final Duration window;
        final long refill;
        final String keyPrefix;
        final String path;

        PathConfig(long capacity, long windowSeconds, long refill, String keyPrefix, String path) {
            this.capacity = capacity;
            this.window = Duration.ofSeconds(windowSeconds);
            this.refill = refill > 0 ? refill : capacity; // por defecto full refill
            this.keyPrefix = keyPrefix;
            this.path = path;
        }
    }

    private PathConfig resolvePathConfig(String path) {
        Map<String, RateLimiterProperties.PathLimit> map = props.getPerPath();
        if (map != null) {
            for (var entry : map.entrySet()) {
                String pref = entry.getKey();
                if (path.startsWith(pref)) {
                    var pl = entry.getValue();
                    long refill = pl.refillPerWindow > 0 ? pl.refillPerWindow : pl.capacity;
                    return new PathConfig(pl.capacity, pl.windowSeconds, refill, "rl:" + pref + ":", pref);
                }
            }
        }
        long refill = props.getRefillPerWindow() > 0 ? props.getRefillPerWindow() : props.getCapacity();
        return new PathConfig(props.getCapacity(), props.getWindowSeconds(), refill, "rl:default:", "default");
    }

    private Bucket newBucket(PathConfig cfg) {
        // Bucket4j 8.x: builder estilo lambda
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(cfg.capacity)
                        .refillGreedy(cfg.refill, cfg.window))
                .build();

        // Alternativa explícita:
        // var refill = io.github.bucket4j.Refill.greedy(cfg.refill, cfg.window);
        // var bw = io.github.bucket4j.Bandwidth.classic(cfg.capacity, refill);
        // return Bucket.builder().addLimit(bw).build();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        var headers = exchange.getRequest().getHeaders();

        String ip = firstNonBlank(
                headers.getFirst("CF-Connecting-IP"),
                headers.getFirst("X-Real-IP"),
                firstIpFrom(headers.getFirst("X-Forwarded-For"))
        );
        if (ip != null) return ip;

        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return Optional.ofNullable(remote)
                .map(InetSocketAddress::getAddress)
                .map(addr -> addr.getHostAddress())
                .orElse("unknown");
    }

    private static String firstIpFrom(String xff) {
        if (xff == null || xff.isBlank()) return null;
        String first = xff.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
