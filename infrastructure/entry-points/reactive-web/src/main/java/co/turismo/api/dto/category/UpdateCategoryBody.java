package co.turismo.api.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UpdateCategoryRequest", description = "Campos opcionales para editar una categoría")
public record UpdateCategoryBody(
        @Schema(description = "Slug de la categoría", example = "waterfall")
        String slug,

        @Schema(description = "Nombre de la categoría", example = "Café")
        String name
) {
}
