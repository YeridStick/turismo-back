package co.turismo.api.dto.agency;

import co.turismo.api.dto.visit.TopPlaceDTO;
import co.turismo.model.agency.Agency;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "AgencyDashboard", description = "Resumen de métricas de la agencia")
public record AgencyDashboardResponse(
        @Schema(description = "Información de la agencia")
        Agency agency,
        @Schema(description = "Paquetes de la agencia")
        List<AgencyTourPackageResponse> packages,
        @Schema(description = "Paquetes más vendidos en el rango")
        List<TopPackageResponse> topPackages,
        @Schema(description = "Lugares más visitados en el rango")
        List<TopPlaceDTO> topPlaces,
        @Schema(description = "Resumen de ventas en el rango")
        SalesSummaryResponse salesSummary
) {
}
