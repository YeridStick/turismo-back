package co.turismo.api.dto.response.docs;

import co.turismo.model.category.Category;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApiCategoryListResponse", description = "Envelope con listado de categorías")
public record ApiCategoryListResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        @ArraySchema(schema = @Schema(implementation = Category.class))
        List<Category> data
) {
}
