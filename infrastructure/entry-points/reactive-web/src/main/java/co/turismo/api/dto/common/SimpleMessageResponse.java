package co.turismo.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SimpleMessageResponse", description = "Mensaje plano utilizado para errores o confirmaciones simples")
public record SimpleMessageResponse(
        @Schema(description = "Detalle legible del estado de la operación", example = "Operación exitosa")
        String message
) {
}
