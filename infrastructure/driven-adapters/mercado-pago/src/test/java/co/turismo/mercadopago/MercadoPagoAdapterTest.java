package co.turismo.mercadopago;

import co.turismo.model.payment.PaymentOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MercadoPagoAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildMinimalPayloadWhenBackUrlsAreDisabled() throws Exception {
        MercadoPagoAdapter adapter = adapter(properties(false, "", "", "", ""));

        JsonNode json = payload(adapter);

        assertTrue(json.has("items"));
        assertTrue(json.has("external_reference"));
        assertFalse(json.has("payer"));
        assertFalse(json.has("notification_url"));
        assertFalse(json.has("back_urls"));
        assertFalse(json.has("auto_return"));
    }

    @Test
    void shouldIncludeBackUrlsAndAutoReturnWhenEnabledWithValidHttpsUrls() throws Exception {
        MercadoPagoAdapter adapter = adapter(properties(
                true,
                "https://api.example.com/payments/success",
                "https://api.example.com/payments/failure",
                "https://api.example.com/payments/pending",
                ""));

        JsonNode json = payload(adapter);

        assertEquals("approved", json.get("auto_return").asText());
        assertEquals("https://api.example.com/payments/success", json.get("back_urls").get("success").asText());
        assertEquals("https://api.example.com/payments/failure", json.get("back_urls").get("failure").asText());
        assertEquals("https://api.example.com/payments/pending", json.get("back_urls").get("pending").asText());
    }

    @Test
    void shouldOmitBackUrlsWhenEnabledButConfigurationIsInvalid() throws Exception {
        MercadoPagoAdapter adapter = adapter(properties(
                true,
                "tuapp://pago-exitoso",
                "https://api.example.com/payments/failure",
                "",
                ""));

        JsonNode json = payload(adapter);

        assertFalse(json.has("back_urls"));
        assertFalse(json.has("auto_return"));
    }

    @Test
    void shouldKeepPreferenceBaseFields() throws Exception {
        MercadoPagoAdapter adapter = adapter(properties(false, "", "", "", ""));

        JsonNode json = payload(adapter);

        assertEquals("reserva-123", json.get("external_reference").asText());
        assertEquals("Paquete de pruebas 2", json.get("items").get(0).get("title").asText());
        assertEquals(1, json.get("items").get(0).get("quantity").asInt());
        assertEquals("COP", json.get("items").get(0).get("currency_id").asText());
        assertEquals(2500, json.get("items").get(0).get("unit_price").asInt());
    }

    private MercadoPagoAdapter adapter(MercadoPagoProperties properties) {
        return new MercadoPagoAdapter(WebClient.builder().baseUrl("https://api.mercadopago.com").build(),
                properties,
                objectMapper);
    }

    private JsonNode payload(MercadoPagoAdapter adapter) throws Exception {
        Object request = adapter.buildPreferenceRequest(PaymentOrder.builder()
                .reservationId("reserva-123")
                .packageTitle("Paquete de pruebas 2")
                .totalPrice(BigDecimal.valueOf(2500))
                .currency("COP")
                .build());

        return objectMapper.readTree(objectMapper.writeValueAsString(request));
    }

    private MercadoPagoProperties properties(
            Boolean includeBackUrls,
            String successUrl,
            String failureUrl,
            String pendingUrl,
            String payerEmail) {
        return new MercadoPagoProperties(
                "https://tunnel.example.com/api/payments/webhook",
                successUrl,
                failureUrl,
                pendingUrl,
                payerEmail,
                includeBackUrls);
    }
}
