package co.turismo.mercadopago;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mercadopago")
public record MercadoPagoProperties(
        String webhookUrl,
        String successDeepLink,
        String failureDeepLink,
        String pendingDeepLink,
        String payerEmail,
        Boolean includeBackUrls
) {
}
