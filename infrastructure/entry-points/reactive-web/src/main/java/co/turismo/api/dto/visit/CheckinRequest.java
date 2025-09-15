package co.turismo.api.dto.visit;

public record CheckinRequest(double lat, double lng, Integer accuracy_m, String device_id, String meta) {}

