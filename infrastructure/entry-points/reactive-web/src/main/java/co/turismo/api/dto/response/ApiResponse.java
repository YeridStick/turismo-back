package co.turismo.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse", description = "Envelope estándar utilizado por la mayoría de endpoints REST")
public record ApiResponse<T>(
        @Schema(description = "Código HTTP retornado", example = "200")
        int status,
        @Schema(description = "Descripción corta del resultado", example = "OK")
        String message,
        @Schema(description = "Datos específicos de cada operación")
        T data
) {
    public static <T> ApiResponse<T> of(int status, String message, T data) { return new ApiResponse<>(status, message, data); }
    public static <T> ApiResponse<T> ok(T data)      { return of(200, "OK", data); }
    public static <T> ApiResponse<T> created(T data) { return of(201, "Created", data); }
    public static ApiResponse<Void> error(int status, String message) { return of(status, message, null); }
}
