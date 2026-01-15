package co.turismo.api.dto.response.docs;

import co.turismo.model.category.Category;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiCategoryResponse", description = "Envelope con una categoría")
public record ApiCategoryResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        @Schema(description = "Categoría solicitada")
        Category data
) {
}
