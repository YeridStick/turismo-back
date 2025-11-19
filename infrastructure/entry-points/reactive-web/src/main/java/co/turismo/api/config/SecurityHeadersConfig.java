package co.turismo.api.config;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class SecurityHeadersConfig implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        String path = exchange.getRequest().getPath().value();

        // CSP permisivo solo para rutas de documentación (Scalar UI)
        if (isDocumentationPath(path)) {
            headers.set("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval' https://cdn.jsdelivr.net; " +
                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                "font-src 'self' https://cdn.jsdelivr.net https://fonts.gstatic.com data:; " +
                "img-src 'self' data: https:; " +
                "connect-src 'self' https://cdn.jsdelivr.net http://localhost:8082 http://127.0.0.1:8082; " +
                "frame-ancestors 'self'; " +
                "form-action 'self'"
            );
        } else {
            // CSP restrictivo para todas las demás rutas (API endpoints)
            headers.set("Content-Security-Policy",
                "default-src 'self'; frame-ancestors 'self'; form-action 'self'");
        }

        headers.set("Strict-Transport-Security", "max-age=31536000;");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Server", "");
        headers.set("Cache-Control", "no-store");
        headers.set("Pragma", "no-cache");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        return chain.filter(exchange);
    }

    /**
     * Verifica si la ruta es de documentación (Scalar UI)
     */
    private boolean isDocumentationPath(String path) {
        return path.equals("/scalar") ||
               path.equals("/docs") ||
               path.startsWith("/v3/api-docs");
    }
}
