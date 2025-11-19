package co.turismo.api.dto.response.docs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiBooleanResponse", description = "Utilizado para confirmar operaciones que retornan verdadero/falso")
public record ApiBooleanResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        @Schema(description = "Resultado de la acción solicitada", example = "true")
        Boolean data
) {
}
