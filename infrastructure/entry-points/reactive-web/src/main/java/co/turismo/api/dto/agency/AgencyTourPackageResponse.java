package co.turismo.api.dto.agency;

import co.turismo.model.place.Place;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AgencyTourPackageResponse", description = "Paquete asociado a la agencia")
public record AgencyTourPackageResponse(
        @Schema(description = "Identificador del paquete", example = "1")
        String id,
        @Schema(description = "Título visible del paquete")
        String title,
        @Schema(description = "Ciudad o zona principal")
        String city,
        @Schema(description = "Descripción corta del paquete")
        String description,
        @Schema(description = "Número de días")
        Integer days,
        @Schema(description = "Número de noches")
        Integer nights,
        @Schema(description = "Texto de capacidad")
        String people,
        @Schema(description = "Rating promedio")
        Double rating,
        @Schema(description = "Cantidad de reseñas")
        Long reviews,
        @Schema(description = "Precio actual")
        Long price,
        @Schema(description = "Precio original antes de descuento")
        Long originalPrice,
        @Schema(description = "Texto de descuento")
        String discount,
        @Schema(description = "Etiqueta del paquete")
        String tag,
        @Schema(description = "Incluye (lista de strings)")
        List<String> includes,
        @Schema(description = "URL de imagen principal")
        String image,
        @Schema(description = "IDs de lugares asociados")
        List<Long> placeIds,
        @Schema(description = "ID de la agencia responsable")
        Long agencyId,
        @Schema(description = "Nombre de la agencia responsable")
        String agencyName,
        @Schema(description = "Listado de lugares asociados")
        List<Place> places
) {
}