package co.turismo.api.error;

import co.turismo.api.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) return Mono.error(ex);

        HttpStatusCode status = mapStatus(ex);
        String message        = mapMessage(ex);

        logError(exchange, ex, status, message);

        var resp = exchange.getResponse();
        resp.setStatusCode(status);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(ApiResponse.error(status.value(), message));
        } catch (Exception e) {
            var fallback = """
                {"status":%d,"message":"%s","data":null}
                """.formatted(status.value(), safe(message));
            bytes = fallback.getBytes(StandardCharsets.UTF_8);
        }
        return resp.writeWith(Mono.just(resp.bufferFactory().wrap(bytes)));
    }

    private static String safe(String s) { return s == null ? "" : s.replace("\"","\\\""); }

    private HttpStatusCode mapStatus(Throwable e) {
        if (e instanceof ResponseStatusException rse) return rse.getStatusCode();

        if (e instanceof WebExchangeBindException)            return HttpStatus.BAD_REQUEST;            // 400
        if (e instanceof ServerWebInputException)             return HttpStatus.BAD_REQUEST;            // 400
        if (e instanceof MethodNotAllowedException)           return HttpStatus.METHOD_NOT_ALLOWED;     // 405
        if (e instanceof UnsupportedMediaTypeStatusException) return HttpStatus.UNSUPPORTED_MEDIA_TYPE; // 415

        if (e instanceof DuplicateKeyException)               return HttpStatus.CONFLICT;               // 409
        if (e instanceof DataIntegrityViolationException)     return HttpStatus.CONFLICT;               // 409

        if (e instanceof NoSuchElementException)              return HttpStatus.NOT_FOUND;              // 404
        if (e instanceof IllegalStateException ise &&
                containsAny(ise.getMessage(), "no existe", "no encontrado", "no encontrada"))
            return HttpStatus.NOT_FOUND;              // 404
        if (e instanceof IllegalArgumentException)            return HttpStatus.BAD_REQUEST;            // 400

        return HttpStatus.INTERNAL_SERVER_ERROR;                                                     // 500
    }

    private String mapMessage(Throwable e) {
        if (e instanceof WebExchangeBindException) return "Error de validación";
        if (e instanceof ServerWebInputException)  return "Solicitud inválida";
        return e.getMessage() == null ? "Error" : e.getMessage();
    }

    private static boolean containsAny(String s, String... parts) {
        if (s == null) return false;
        var x = s.toLowerCase();
        for (var p : parts) if (x.contains(p)) return true;
        return false;
    }

    private void logError(ServerWebExchange ex, Throwable err, HttpStatusCode status, String message) {
        var path = ex.getRequest().getPath().pathWithinApplication().value();
        log.error("HTTP {} {} -> {} : {}", ex.getRequest().getMethod(), path, status.value(), message, err);
    }
}
