package co.turismo.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Controlador que sirve la interfaz de Scalar API Reference
 * para visualizar la documentación OpenAPI de forma interactiva.
 */
@Controller
public class ScalarDocumentationController {

    @Value("${server.port:8082}")
    private String serverPort;

    @Value("${scalar.title:Turismo API Documentation}")
    private String scalarTitle;

    @Value("${scalar.theme:purple}")
    private String scalarTheme;

    @Value("${springdoc.api-docs.path:/v3/api-docs}")
    private String apiDocsPath;

    /**
     * Endpoint principal que sirve la UI de Scalar
     */
    @GetMapping(value = "/scalar", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public Mono<String> scalarUi(ServerWebExchange exchange) {
        String baseUrl = getBaseUrl(exchange);
        String openApiUrl = baseUrl + apiDocsPath;

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                    }
                </style>
            </head>
            <body>
                <script
                    id="api-reference"
                    data-url="%s"
                    data-configuration='%s'
                ></script>
                <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
            </body>
            </html>
            """.formatted(
                scalarTitle,
                openApiUrl,
                getScalarConfiguration()
            );

        return Mono.just(html);
    }

    /**
     * Endpoint alternativo que redirige a /scalar
     */
    @GetMapping("/docs")
    public Mono<Void> docsRedirect(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TEMPORARY_REDIRECT);
        exchange.getResponse().getHeaders().add("Location", "/scalar");
        return exchange.getResponse().setComplete();
    }

    /**
     * Obtiene la URL base del servidor
     */
    private String getBaseUrl(ServerWebExchange exchange) {
        String scheme = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        if (scheme == null) {
            scheme = exchange.getRequest().getURI().getScheme();
        }

        String host = exchange.getRequest().getHeaders().getFirst("Host");
        if (host == null) {
            host = exchange.getRequest().getURI().getHost();
            int port = exchange.getRequest().getURI().getPort();
            if (port > 0 && port != 80 && port != 443) {
                host = host + ":" + port;
            }
        }

        return scheme + "://" + host;
    }

    /**
     * Genera la configuración JSON para Scalar
     */
    private String getScalarConfiguration() {
        return """
            {
                "theme": "%s",
                "layout": "modern",
                "showSidebar": true,
                "hideModels": false,
                "hideDownloadButton": false,
                "darkMode": false,
                "searchHotKey": "k",
                "servers": [
                    {
                        "url": "http://localhost:%s",
                        "description": "Local Development"
                    }
                ],
                "authentication": {
                    "preferredSecurityScheme": "bearerAuth",
                    "http": {
                        "bearer": {
                            "token": ""
                        }
                    }
                }
            }
            """.formatted(scalarTheme, serverPort).replaceAll("\\s+", " ");
    }
}
