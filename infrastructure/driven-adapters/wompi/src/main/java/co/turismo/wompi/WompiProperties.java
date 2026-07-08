package co.turismo.wompi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wompi")
public record WompiProperties(
        boolean enabled,
        String environment,
        String baseUrl,
        String publicKey,
        String privateKey,
        String integritySecret,
        String eventsSecret,
        String webhookUrl,
        String redirectUrl,
        long checkoutExpirationMinutes
) {
    public String checkoutBaseUrl() {
        String normalizedEnvironment = environment == null ? "" : environment.trim().toLowerCase();
        return "production".equals(normalizedEnvironment) || "prod".equals(normalizedEnvironment)
                ? "https://checkout.wompi.co/p/"
                : "https://checkout.wompi.co/p/";
    }
}
