package co.turismo.api.config;

import co.turismo.api.http.ClientIp;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter implements WebFilter {

    private final RateLimiterProperties props;
    private final RateLimitStore rateLimitStore;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RateLimitingFilter(RateLimiterProperties props, RateLimitStore rateLimitStore) {
        this.props = props;
        this.rateLimitStore = rateLimitStore;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!props.isEnabled()) {
            return chain.filter(exchange);
        }

        HttpMethod method = exchange.getRequest().getMethod();
        if (method == HttpMethod.OPTIONS || method == HttpMethod.HEAD) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        if (mustSkip(path)) {
            return chain.filter(exchange);
        }

        String clientIp = ClientIp.resolve(exchange.getRequest());
        PathConfig config = resolvePathConfig(path);
        String key = config.keyPrefix() + ":" + clientIp;

        return rateLimitStore.consume(key, config)
                .flatMap(decision -> {
                    if (decision.allowed()) {
                        addRateHeaders(exchange, decision);
                        return chain.filter(exchange);
                    }
                    return respondTooManyRequests(exchange, decision);
                })
                .onErrorResume(error -> {
                    log.warn("Rate limiter backend error. Request allowed by fallback. path={}, ip={}", path, clientIp, error);
                    return chain.filter(exchange);
                });
    }

    private void addRateHeaders(ServerWebExchange exchange, RateLimitDecision decision) {
        var headers = exchange.getResponse().getHeaders();
        headers.add("X-Rate-Limit-Limit", String.valueOf(decision.limit()));
        headers.add("X-Rate-Limit-Remaining", String.valueOf(decision.remaining()));
        headers.add("X-Rate-Limit-Reset", String.valueOf(decision.resetSeconds()));
    }

    private Mono<Void> respondTooManyRequests(ServerWebExchange exchange, RateLimitDecision decision) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set("Retry-After", String.valueOf(decision.resetSeconds()));
        response.getHeaders().set("X-Rate-Limit-Limit", String.valueOf(decision.limit()));
        response.getHeaders().set("X-Rate-Limit-Remaining", "0");
        response.getHeaders().set("X-Rate-Limit-Reset", String.valueOf(decision.resetSeconds()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"status":429,"message":"Demasiadas solicitudes. Intenta de nuevo en %d s.","data":null}
                """.formatted(decision.resetSeconds());
        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private boolean mustSkip(String path) {
        if (props.getSkipPaths() == null || props.getSkipPaths().isEmpty()) {
            return false;
        }
        for (String pattern : props.getSkipPaths()) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private PathConfig resolvePathConfig(String path) {
        Map<String, RateLimiterProperties.PathLimit> map = props.getPerPath();
        if (map != null && !map.isEmpty()) {
            for (var entry : map.entrySet()) {
                String pathPrefix = entry.getKey();
                if (path.startsWith(pathPrefix)) {
                    var limit = entry.getValue();
                    long refill = limit.refillPerWindow > 0 ? limit.refillPerWindow : limit.capacity;
                    return new PathConfig(
                            limit.capacity,
                            Duration.ofSeconds(limit.windowSeconds),
                            refill,
                            "rl:" + pathPrefix,
                            pathPrefix
                    );
                }
            }
        }

        long refill = props.getRefillPerWindow() > 0 ? props.getRefillPerWindow() : props.getCapacity();
        return new PathConfig(
                props.getCapacity(),
                Duration.ofSeconds(props.getWindowSeconds()),
                refill,
                "rl:default",
                "default"
        );
    }

    public record PathConfig(
            long capacity,
            Duration window,
            long refill,
            String keyPrefix,
            String path
    ) {
    }
}
