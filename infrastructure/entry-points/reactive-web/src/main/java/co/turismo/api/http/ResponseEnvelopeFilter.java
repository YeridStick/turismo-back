package co.turismo.api.http;

import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ResponseEnvelopeFilter implements WebFilter {

    private static final String HEADER_SKIP    = "X-Envelope-Skip";
    private static final String HEADER_DISABLE = "X-Envelope-Disable";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (shouldSkip(exchange)) return chain.filter(exchange);

        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory bufferFactory = original.bufferFactory(); // ← usar este

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                HttpStatusCode sc = getStatusCode() != null ? getStatusCode() : HttpStatusCode.valueOf(200);
                boolean is2xx = sc.value() >= 200 && sc.value() < 300;

                MediaType ct = getHeaders().getContentType();
                boolean isJson = ct != null && (MediaType.APPLICATION_JSON.isCompatibleWith(ct)
                        || MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(ct));

                // No tocar si no es 2xx o no es JSON "normal"
                if (!is2xx || !isJson) {
                    return super.writeWith(body);
                }

                return DataBufferUtils.join(Flux.from(body))
                        .flatMap(buf -> {
                            try {
                                byte[] bytes = new byte[buf.readableByteCount()];
                                buf.read(bytes);
                                String originalBody = new String(bytes, StandardCharsets.UTF_8);
                                String trimmed = originalBody.trim();

                                // Si ya está en formato {status,...,data:...} no lo tocamos
                                if (alreadyEnveloped(trimmed)) {
                                    // mejor eliminar Content-Length si lo hubiera, para evitar desajustes
                                    getHeaders().remove("Content-Length");
                                    return super.writeWith(Mono.just(bufferFactory.wrap(trimmed.getBytes(StandardCharsets.UTF_8))));
                                }

                                String message = defaultMessage(sc.value());
                                String wrapped = "{\"status\":" + sc.value() +
                                        ",\"message\":\"" + escapeJson(message) + "\"," +
                                        "\"data\":" + (trimmed.isEmpty() ? "null" : trimmed) + "}";

                                getHeaders().setContentType(MediaType.APPLICATION_JSON);
                                getHeaders().remove("Content-Length"); // chunked
                                DataBuffer out = bufferFactory.wrap(wrapped.getBytes(StandardCharsets.UTF_8));
                                return super.writeWith(Mono.just(out));
                            } finally {
                                DataBufferUtils.release(buf);
                            }
                        });
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    private boolean shouldSkip(ServerWebExchange ex) {
        var h = ex.getRequest().getHeaders();
        return "true".equalsIgnoreCase(h.getFirst(HEADER_SKIP))
                || "true".equalsIgnoreCase(h.getFirst(HEADER_DISABLE));
    }

    private static boolean alreadyEnveloped(String json) {
        return json.startsWith("{") && json.contains("\"status\"") && json.contains("\"data\"");
    }

    private static String defaultMessage(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            default -> "OK";
        };
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
