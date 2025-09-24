package co.turismo.model.geocode;

public record GeocodeResult(double lat, double lon, String wkt, String formatted) {}