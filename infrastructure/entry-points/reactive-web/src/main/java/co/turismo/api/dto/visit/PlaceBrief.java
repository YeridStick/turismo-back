package co.turismo.api.dto.visit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Resumen compacto del lugar visitado")
public record PlaceBrief(
        @Schema(description = "Identificador interno del lugar", example = "101")
        Long id,
        @Schema(description = "Nombre comercial", example = "Café del Parque")
        String name,
        @Schema(description = "Latitud decimal", example = "6.25184")
        Double lat,
        @Schema(description = "Longitud decimal", example = "-75.56359")
        Double lng,
        @Schema(description = "Dirección legible", example = "Cra. 43 #8-31, Medellín")
        String address,
        @Schema(description = "Descripción corta", example = "Especialistas en cafés de origen")
        String description,
        @Schema(description = "Identificador de categoría", example = "12")
        Integer categoryId,
        @Schema(description = "Listado de imágenes destacadas")
        List<String> imageUrls
) {
}

