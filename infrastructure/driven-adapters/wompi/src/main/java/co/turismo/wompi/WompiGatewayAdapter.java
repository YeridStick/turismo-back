package co.turismo.wompi;

import co.turismo.model.payment.PaymentProvider;
import co.turismo.model.payment.PaymentTransaction;
import co.turismo.model.payment.WompiCheckoutData;
import co.turismo.model.payment.WompiEventData;
import co.turismo.model.payment.gateways.WompiGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WompiGatewayAdapter implements WompiGateway {

    private final WompiProperties properties;
    private final WompiSignatureService signatureService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean isEnabled() {
        return properties.enabled()
                && hasText(properties.publicKey())
                && hasText(properties.integritySecret())
                && hasText(properties.eventsSecret());
    }

    @Override
    public long checkoutExpirationMinutes() {
        return properties.checkoutExpirationMinutes() <= 0 ? 30 : properties.checkoutExpirationMinutes();
    }

    @Override
    public WompiCheckoutData buildCheckout(PaymentTransaction transaction, String customerEmail) {
        String signature = signatureService.checkoutIntegrity(transaction, properties.integritySecret());

        return WompiCheckoutData.builder()
                .reservationId(transaction.getReservationId())
                .transactionId(transaction.getId())
                .provider(PaymentProvider.WOMPI)
                .reference(transaction.getReference())
                .amountInCents(transaction.getAmountInCents())
                .currency(transaction.getCurrency())
                .publicKey(properties.publicKey())
                .signatureIntegrity(signature)
                .redirectUrl(publicRedirectUrl())
                .expirationTime(transaction.getExpiresAt())
                .customerEmail(customerEmail)
                .checkoutUrl(null)
                .status(transaction.getStatus())
                .build();
    }

    @Override
    public Mono<WompiEventData> parseAndValidateEvent(String rawPayload, String eventChecksum) {
        return Mono.fromCallable(() -> {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode transaction = root.at("/data/transaction");
            if (transaction.isMissingNode() || transaction.isNull()) {
                transaction = root.at("/data");
            }

            WompiEventData event = WompiEventData.builder()
                    .eventId(firstText(root, "/id", "/event_id"))
                    .eventType(firstText(root, "/event", "/type", "/event_type"))
                    .providerTransactionId(firstText(transaction, "/id"))
                    .reference(firstText(transaction, "/reference"))
                    .providerStatus(firstText(transaction, "/status"))
                    .amountInCents(firstLong(transaction, "/amount_in_cents", "/amountInCents"))
                    .currency(firstText(transaction, "/currency"))
                    .checksum(firstText(root, "/signature/checksum", "/checksum", "/signature_checksum"))
                    .headerChecksum(eventChecksum)
                    .rawPayload(rawPayload)
                    .build();

            event.setSignatureValid(signatureService.isValidEvent(root, event, properties.eventsSecret()));
            return event;
        });
    }

    private static String firstText(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode value = node.at(pointer);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static Long firstLong(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode value = node.at(pointer);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.asLong();
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String publicRedirectUrl() {
        String redirectUrl = properties.redirectUrl();
        if (!hasText(redirectUrl)) {
            return null;
        }
        String normalized = redirectUrl.trim().toLowerCase();
        if (normalized.contains("localhost") || normalized.contains("127.0.0.1")) {
            return null;
        }
        return redirectUrl;
    }
}
