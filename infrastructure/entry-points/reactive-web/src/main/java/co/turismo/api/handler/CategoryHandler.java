package co.turismo.api.handler;

import co.turismo.api.dto.category.CreateCategoryBody;
import co.turismo.api.dto.category.UpdateCategoryBody;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.mapper.CategoryMapper;
import co.turismo.usecase.category.CategoryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class CategoryHandler {

    private final CategoryUseCase categoryUseCase;

    public Mono<ServerResponse> list(ServerRequest req) {
        return categoryUseCase.findAll()
                .collectList()
                .flatMap(CategoryHandler::ok);
    }

    public Mono<ServerResponse> findById(ServerRequest req) {
        Long id = parsePathId(req, "id");

        return categoryUseCase.findById(id)
                .switchIfEmpty(categoryNotFound())
                .flatMap(CategoryHandler::ok);
    }

    public Mono<ServerResponse> create(ServerRequest req) {
        return req.bodyToMono(CreateCategoryBody.class)
                .flatMap(CategoryHandler::validateCreateBody)
                .map(CategoryMapper::toCreateCategoryRequest)
                .flatMap(categoryUseCase::create)
                .flatMap(category -> created(
                        req.uriBuilder()
                                .path("/{id}")
                                .build(category.getId()),
                        category
                ));
    }

    public Mono<ServerResponse> update(ServerRequest req) {
        Long id = parsePathId(req, "id");

        return req.bodyToMono(UpdateCategoryBody.class)
                .flatMap(CategoryHandler::validateUpdateBody)
                .map(CategoryMapper::toUpdateCategoryRequest)
                .flatMap(cmd -> categoryUseCase.update(id, cmd))
                .switchIfEmpty(categoryNotFound())
                .flatMap(CategoryHandler::ok);
    }

    private static Mono<CreateCategoryBody> validateCreateBody(CreateCategoryBody body) {
        if (!hasText(body.name())) {
            return Mono.error(new IllegalArgumentException("name es obligatorio"));
        }

        if (!hasText(body.slug())) {
            return Mono.error(new IllegalArgumentException("slug es obligatorio"));
        }

        return Mono.just(body);
    }

    private static Mono<UpdateCategoryBody> validateUpdateBody(UpdateCategoryBody body) {
        if (!hasText(body.name()) && !hasText(body.slug())) {
            return Mono.error(new IllegalArgumentException("Debes enviar al menos un campo"));
        }

        return Mono.just(body);
    }

    private static Long parsePathId(ServerRequest req, String name) {
        try {
            return Long.parseLong(req.pathVariable(name));
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " inválido");
        }
    }

    private static <T> Mono<T> categoryNotFound() {
        return Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Categoría no encontrada"
        ));
    }

    private static Mono<ServerResponse> ok(Object body) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.ok(body));
    }

    private static Mono<ServerResponse> created(URI location, Object body) {
        return ServerResponse.created(location)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.created(body));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}