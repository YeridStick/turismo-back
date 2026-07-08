package co.turismo.api.dto.agency;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TopPackage", description = "Paquete con mayor número de ventas")
public record TopPackageResponse(
        @Schema(description = "ID del paquete", example = "101")
        Long packageId,
        @Schema(description = "Título del paquete")
        String title,
        @Schema(description = "Cantidad vendida", example = "24")
        Integer sold,
        @Schema(description = "Ingresos acumulados", example = "12000000")
        Long revenue
) {
}
