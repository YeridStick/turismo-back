package co.turismo.api.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "CreateCategoryRequest", description = "Cuerpo para crear una categoría")
public record CreateCategoryBody(
        @Schema(description = "Slug de la categoría", example = "waterfall")
        @NotBlank String slug,

        @Schema(description = "Nombre de la categoría", example = "Café")
        @NotBlank String name
) {
}
