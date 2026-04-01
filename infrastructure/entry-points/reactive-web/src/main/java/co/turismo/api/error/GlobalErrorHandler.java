package co.turismo.api.error;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.model.error.ConflictException;
import co.turismo.model.error.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
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
import org.springframework.web.server.WebExceptionHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    // Mapeo declarativo: tipo → status
    private static final List<Map.Entry<Class<?>, HttpStatus>> STATUS_RULES = List.of(
            Map.entry(WebExchangeBindException.class,            HttpStatus.BAD_REQUEST),
            Map.entry(ServerWebInputException.class,             HttpStatus.BAD_REQUEST),
            Map.entry(IllegalArgumentException.class,            HttpStatus.BAD_REQUEST),
            Map.entry(ConstraintViolationException.class,        HttpStatus.BAD_REQUEST),
            Map.entry(MethodNotAllowedException.class,           HttpStatus.METHOD_NOT_ALLOWED),
            Map.entry(UnsupportedMediaTypeStatusException.class, HttpStatus.UNSUPPORTED_MEDIA_TYPE),
            Map.entry(DuplicateKeyException.class,               HttpStatus.CONFLICT),
            Map.entry(DataIntegrityViolationException.class,     HttpStatus.CONFLICT),
            Map.entry(ConflictException.class,                   HttpStatus.CONFLICT),
            Map.entry(NotFoundException.class,                   HttpStatus.NOT_FOUND),
            Map.entry(NoSuchElementException.class,              HttpStatus.NOT_FOUND)
    );

    // Reglas adicionales con predicado arbitrario
    private static final List<Map.Entry<Predicate<Throwable>, HttpStatus>> PREDICATE_RULES = List.of(
            Map.entry(
                    e -> e instanceof IllegalStateException &&
                            Stream.of("no existe", "no encontrado", "no encontrada")
                                    .anyMatch(kw -> Optional.ofNullable(e.getMessage())
                                            .map(String::toLowerCase)
                                            .filter(m -> m.contains(kw))
                                            .isPresent()),
                    HttpStatus.NOT_FOUND
            )
    );

    // Mapeo declarativo: tipo → mensaje
    private static final List<Map.Entry<Class<?>, Function<Throwable, String>>> MESSAGE_RULES = List.of(
            Map.entry(WebExchangeBindException.class,      e -> "Error de validación"),
            Map.entry(ServerWebInputException.class,       e -> "Solicitud inválida"),
            Map.entry(ConstraintViolationException.class,  e -> ((ConstraintViolationException) e)
                    .getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .findFirst()
                    .orElse("Error de validación"))
    );

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) return Mono.error(ex);

        return Mono.just(ex)
                .map(this::resolveStatus)
                .zipWith(Mono.just(ex).map(this::resolveMessage))
                .flatMap(t -> writeResponse(exchange, t.getT1(), t.getT2(), ex));
    }

    private HttpStatusCode resolveStatus(Throwable e) {
        if (e instanceof ResponseStatusException rse) return rse.getStatusCode();

        return STATUS_RULES.stream()
                .filter(rule -> rule.getKey().isInstance(e))
                .map(Map.Entry::getValue)
                .findFirst()
                .or(() -> PREDICATE_RULES.stream()
                        .filter(rule -> rule.getKey().test(e))
                        .map(Map.Entry::getValue)
                        .findFirst())
                .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String resolveMessage(Throwable e) {
        return MESSAGE_RULES.stream()
                .filter(rule -> rule.getKey().isInstance(e))
                .map(Map.Entry::getValue)
                .findFirst()
                .map(fn -> fn.apply(e))
                .orElseGet(() -> Optional.ofNullable(e.getMessage()).orElse("Error inesperado"));
    }

    private Mono<Void> writeResponse(ServerWebExchange exchange, HttpStatusCode status, String message, Throwable ex) {
        logError(exchange, ex, status, message);

        var resp = exchange.getResponse();
        resp.setStatusCode(status);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(ApiResponse.error(status.value(), message)))
                .onErrorReturn(fallbackBytes(status, message))
                .flatMap(bytes -> resp.writeWith(Mono.just(resp.bufferFactory().wrap(bytes))));
    }

    private static byte[] fallbackBytes(HttpStatusCode status, String message) {
        return """
            {"status":%d,"message":"%s","data":null}
            """.formatted(status.value(), sanitize(message))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String sanitize(String s) {
        return Optional.ofNullable(s).orElse("").replace("\"", "\\\"");
    }

    private void logError(ServerWebExchange ex, Throwable err, HttpStatusCode status, String msg) {
        var req = ex.getRequest();
        var path = req.getPath().pathWithinApplication().value();
        if (status.is5xxServerError())
            log.error("HTTP {} {} → {} : {}", req.getMethod(), path, status.value(), msg, err);
        else
            log.warn ("HTTP {} {} → {} : {}", req.getMethod(), path, status.value(), msg);
    }
}
