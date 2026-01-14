package co.turismo.api.dto.response.docs;

import co.turismo.api.handler.TourPackageHandler;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiTourPackageResponse", description = "Envelope con un paquete turístico")
public record ApiTourPackageResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        TourPackageHandler.TourPackageResponse data
) {
}
