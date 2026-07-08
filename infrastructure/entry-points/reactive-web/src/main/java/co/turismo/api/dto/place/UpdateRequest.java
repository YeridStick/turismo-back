package co.turismo.api.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "PlaceUpdateRequest", description = "Campos opcionales para actualizar parcialmente un lugar")
public record UpdateRequest(
        @Schema(description = "Nombre comercial del lugar", example = "Café del Parque")
        String name,

        @Schema(description = "Descripción corta que se mostrará en las tarjetas")
        String description,

        @Schema(description = "Identificador de la categoría", example = "12")
        Long categoryId,

        @Schema(description = "Latitud decimal", example = "6.25184")
        Double lat,

        @Schema(description = "Longitud decimal", example = "-75.56359")
        Double lng,

        @Schema(description = "Dirección legible", example = "Cra. 43 #8-31, Medellín")
        String address,

        @Schema(description = "Número de contacto", example = "+57 3011234567")
        String phone,

        @Schema(description = "Sitio web oficial", example = "https://cafedelparque.co")
        String website,

        @Schema(description = "Listado de URLs de imágenes")
        List<String> imageUrls,

        @Schema(description = "Listado de URLs de modelos 3D")
        List<String> model3dUrls,

        @Schema(description = "Listado de servicios (WiFi, Parqueadero, etc.)")
        List<String> services
) {
}
