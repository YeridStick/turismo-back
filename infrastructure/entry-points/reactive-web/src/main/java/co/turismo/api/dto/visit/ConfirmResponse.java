package co.turismo.api.dto.visit;

import java.time.Instant;

public record ConfirmResponse(String status, Instant confirmed_at) {}
