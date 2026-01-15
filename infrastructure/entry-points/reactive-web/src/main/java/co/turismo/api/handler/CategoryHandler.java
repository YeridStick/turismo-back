package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.model.category.CreateCategoryRequest;
import co.turismo.model.category.UpdateCategoryRequest;
import co.turismo.usecase.category.CategoryUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CategoryHandler {

    private final CategoryUseCase categoryUseCase;

    public Mono<ServerResponse> list(ServerRequest req) {
        return categoryUseCase.findAll()
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> findById(ServerRequest req) {
        long id = Long.parseLong(req.pathVariable("id"));
        return categoryUseCase.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada")))
                .flatMap(category -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(category)));
    }

    public Mono<ServerResponse> create(ServerRequest req) {
        return req.bodyToMono(CreateCategoryBody.class)
                .flatMap(dto -> {
                    if (dto.name() == null || dto.name().isBlank()) {
                        return Mono.error(new IllegalArgumentException("name es obligatorio"));
                    }
                    if (dto.slug() == null || dto.slug().isBlank()) {
                        return Mono.error(new IllegalArgumentException("slug es obligatorio"));
                    }
                    var cmd = CreateCategoryRequest.builder()
                            .slug(dto.slug())
                            .name(dto.name())
                            .build();
                    return categoryUseCase.create(cmd);
                })
                .flatMap(category -> {
                    var location = req.uriBuilder().path("/{id}").build(category.getId());
                    return ServerResponse.created(location)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(ApiResponse.created(category));
                });
    }

    public Mono<ServerResponse> update(ServerRequest req) {
        long id = Long.parseLong(req.pathVariable("id"));
        return req.bodyToMono(UpdateCategoryBody.class)
                .flatMap(dto -> {
                    if (dto.name() == null && dto.slug() == null) {
                        return Mono.error(new IllegalArgumentException("Debes enviar al menos un campo"));
                    }
                    var cmd = UpdateCategoryRequest.builder()
                            .slug(dto.slug())
                            .name(dto.name())
                            .build();
                    return categoryUseCase.update(id, cmd);
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada")))
                .flatMap(category -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(category)));
    }

    @Schema(name = "CreateCategoryRequest", description = "Cuerpo para crear una categoría")
    public record CreateCategoryBody(
            @Schema(description = "Slug de la categoría", example = "waterfall")
            @NotBlank String slug,
            @Schema(description = "Nombre de la categoría", example = "Café")
            @NotBlank String name
    ) {
    }

    @Schema(name = "UpdateCategoryRequest", description = "Campos opcionales para editar una categoría")
    public record UpdateCategoryBody(
            @Schema(description = "Slug de la categoría", example = "waterfall")
            String slug,
            @Schema(description = "Nombre de la categoría", example = "Café")
            String name
    ) {
    }
}
