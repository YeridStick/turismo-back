package co.turismo.model.reservation;

import java.util.Arrays;

public enum ContactPreference {
    IN_APP,
    EMAIL;

    public static ContactPreference from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("contactPreference inválido");
        }

        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(preference -> preference.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("contactPreference inválido"));
    }
}
