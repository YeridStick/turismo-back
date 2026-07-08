package co.turismo.api.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "PlaceCreateRequest", description = "Cuerpo necesario para registrar un nuevo lugar turístico")
public record PlaceCreateRequest(
        @Schema(description = "Nombre comercial del lugar", example = "Café del Parque")
        @NotBlank String name,

        @Schema(description = "Descripción corta que se mostrará en las tarjetas", example = "Espacio cultural con cafés especiales")
        @NotBlank String description,

        @Schema(description = "Identificador de la categoría", example = "12")
        @NotNull Long categoryId,

        @Schema(description = "Latitud decimal", example = "6.25184")
        @NotNull Double lat,

        @Schema(description = "Longitud decimal", example = "-75.56359")
        @NotNull Double lng,

        @Schema(description = "Dirección legible", example = "Cra. 43 #8-31, Medellín")
        String address,

        @Schema(description = "Número de contacto", example = "+57 3011234567")
        String phone,

        @Schema(description = "Sitio web oficial", example = "https://cafedelparque.co")
        String website,

        @Schema(description = "Listado de URLs de imágenes")
        String[] imageUrls,

        @Schema(description = "Listado de URLs de modelos 3D")
        String[] model3dUrls,

        @Schema(description = "Listado de servicios (WiFi, Parqueadero, etc.)", example = "[\"WiFi\", \"Piscina\"]")
        String[] services
) {
}
