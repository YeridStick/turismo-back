package co.turismo.api.dto.response.docs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiErrorResponse", description = "Estructura devuelta cuando ocurre un error controlado")
public record ApiErrorResponse(
        @Schema(example = "400")
        int status,
        @Schema(example = "Datos inválidos")
        String message,
        @Schema(description = "Siempre será null en los errores controlados", nullable = true)
        Object data
) {
}
