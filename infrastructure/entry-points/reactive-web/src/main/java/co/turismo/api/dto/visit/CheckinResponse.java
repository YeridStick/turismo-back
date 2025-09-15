package co.turismo.api.dto.visit;

public record CheckinResponse(long visit_id, String status, int min_stay_seconds, int distance_m) {}

