package co.turismo.api.config;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public class ScalarDocumentationController {

    private static final String SCALAR_HTML = """
            <!doctype html>
            <html>
              <head>
                <title>Turismo API Reference</title>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
              </head>
              <body>
                <script
                  id="api-reference"
                  data-url="/v3/api-docs"
                  data-configuration='{
                    "theme": "purple",
                    "layout": "modern",
                    "showSidebar": true
                  }'>
                </script>
                <script src="https://unpkg.com/@scalar/api-reference@latest"></script>
              </body>
            </html>
            """;

    public Mono<ServerResponse> serveScalarUI(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .bodyValue(SCALAR_HTML);
    }
}