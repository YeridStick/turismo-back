package co.turismo.api.dto.agency;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TourPackageSalesSummary", description = "Totales de ventas")
public record SalesSummaryResponse(
        @Schema(description = "Total de personas vendidas", example = "120")
        Long totalSold,
        @Schema(description = "Ingresos acumulados", example = "48000000")
        Long totalRevenue
) {
}