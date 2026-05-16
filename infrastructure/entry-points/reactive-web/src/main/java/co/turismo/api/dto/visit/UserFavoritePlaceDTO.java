package co.turismo.api.dto.visit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Lugar marcado como favorito por el usuario autenticado")
public record UserFavoritePlaceDTO(
        @Schema(description = "Información resumida del lugar")
        PlaceBrief place,
        @Schema(description = "Instante en que se marcó como favorito", example = "2026-05-16T09:30:00Z")
        Instant favorited_at
) {
}
