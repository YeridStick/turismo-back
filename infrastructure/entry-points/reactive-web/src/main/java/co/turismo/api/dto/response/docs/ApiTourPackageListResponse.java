package co.turismo.api.dto.response.docs;

import co.turismo.api.dto.tourpackage.TourPackageResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApiTourPackageListResponse", description = "Envelope con lista de paquetes turísticos")
public record ApiTourPackageListResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        @Schema(description = "Listado de paquetes")
        List<TourPackageResponse> data
) {
}
