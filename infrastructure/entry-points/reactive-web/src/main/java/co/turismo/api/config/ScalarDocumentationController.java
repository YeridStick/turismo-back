package co.turismo.api.config;

import org.springframework.beans.factory.annotation.Value;
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
        // Usar URL relativa para evitar problemas con CSP y diferentes entornos
        String openApiUrl = apiDocsPath;

        String html = String.format("""
            <!doctype html>
            <html>
            <head>
                <title>%s</title>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
            </head>
            <body>
                <script
                    id="api-reference"
                    data-url="%s"></script>
                <script>
                    var configuration = {
                        theme: '%s'
                    }

                    document.getElementById('api-reference').dataset.configuration = JSON.stringify(configuration)
                </script>
                <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
            </body>
            </html>
            """, scalarTitle, openApiUrl, scalarTheme);

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
}
