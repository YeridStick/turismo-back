package co.turismo.mercadopago;

import co.turismo.model.payment.PaymentCheckoutResult;
import co.turismo.model.payment.PaymentOrder;
import co.turismo.model.payment.PaymentStatusResult;
import co.turismo.model.payment.gateways.PaymentGateway;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Comparator;
import java.util.List;

@Repository
public class MercadoPagoAdapter implements PaymentGateway {

    private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoAdapter.class);

    private final WebClient mercadoPagoWebClient;
    private final MercadoPagoProperties properties;
    private final ObjectMapper objectMapper;

    public MercadoPagoAdapter(
            @Qualifier("mercadoPagoWebClient") WebClient mercadoPagoWebClient,
            MercadoPagoProperties properties,
            ObjectMapper objectMapper) {
        this.mercadoPagoWebClient = mercadoPagoWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<PaymentCheckoutResult> createPreference(PaymentOrder order) {
        Object request = buildPreferenceRequest(order);

        logPreferenceRequest(request);

        return mercadoPagoWebClient.post()
                .uri("/checkout/preferences")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> handleMercadoPagoError("crear preferencia", response))
                .bodyToMono(PreferenceResponse.class)
                .map(response -> PaymentCheckoutResult.builder()
                        .preferenceId(response.id())
                        .sandboxInitPoint(response.sandboxInitPoint())
                        .build());
    }

    Object buildPreferenceRequest(PaymentOrder order) {
        BackUrls backUrls = buildBackUrls();
        return new PreferenceRequest(
                List.of(new PreferenceItem(
                        order.getPackageTitle(),
                        1,
                        order.getCurrency(),
                        order.getTotalPrice()
                )),
                order.getReservationId(),
                backUrls,
                backUrls == null ? null : "approved",
                buildPayer()
        );
    }

    private void logPreferenceRequest(Object request) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        try {
            LOG.debug("Payload preferencia Mercado Pago: {}", objectMapper.writeValueAsString(request));
        } catch (Exception error) {
            LOG.debug("Payload preferencia Mercado Pago no serializable para log. request={}", request, error);
        }
    }

    private Payer buildPayer() {
        String payerEmail = properties.payerEmail();
        if (payerEmail == null || payerEmail.isBlank()) {
            return null;
        }
        return new Payer(payerEmail.trim());
    }

    private BackUrls buildBackUrls() {
        if (!Boolean.TRUE.equals(properties.includeBackUrls())) {
            return null;
        }

        String success = properties.successDeepLink();
        String failure = properties.failureDeepLink();
        String pending = properties.pendingDeepLink();

        if (isHttpUrl(success) && isHttpUrl(failure) && isHttpUrl(pending)) {
            return new BackUrls(success.trim(), failure.trim(), pending.trim());
        }

        LOG.warn("back_urls omitido para Mercado Pago: includeBackUrls=true pero alguna URL no es HTTP/HTTPS válida");
        return null;
    }

    private boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    @Override
    public Mono<PaymentStatusResult> getPaymentStatus(String paymentId) {
        return mercadoPagoWebClient.get()
                .uri("/v1/payments/{id}", paymentId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> handleMercadoPagoError("consultar pago " + paymentId, response))
                .bodyToMono(PaymentResponse.class)
                .map(response -> PaymentStatusResult.builder()
                        .paymentId(String.valueOf(response.id()))
                        .status(response.status())
                        .statusDetail(response.statusDetail())
                        .externalReference(response.externalReference())
                        .build());
    }

    @Override
    public Mono<PaymentStatusResult> getMerchantOrderPaymentStatus(String merchantOrderId) {
        return mercadoPagoWebClient.get()
                .uri("/merchant_orders/{id}", merchantOrderId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> handleMercadoPagoError("consultar merchant order " + merchantOrderId, response))
                .bodyToMono(MerchantOrderResponse.class)
                .map(this::toPaymentStatusResult);
    }

    private PaymentStatusResult toPaymentStatusResult(MerchantOrderResponse merchantOrder) {
        MerchantOrderPayment selectedPayment = merchantOrder.payments() == null
                ? null
                : merchantOrder.payments().stream()
                .max(Comparator.comparingInt(this::paymentPriority))
                .orElse(null);

        LOG.info("Merchant order Mercado Pago recibido. merchantOrderId={} orderStatus={} externalReference={} paymentsCount={} selectedPaymentId={} selectedPaymentStatus={}",
                merchantOrder.id(),
                merchantOrder.orderStatus(),
                merchantOrder.externalReference(),
                merchantOrder.payments() == null ? 0 : merchantOrder.payments().size(),
                selectedPayment == null ? null : selectedPayment.id(),
                selectedPayment == null ? null : selectedPayment.status());

        return PaymentStatusResult.builder()
                .paymentId(selectedPayment == null || selectedPayment.id() == null ? null : String.valueOf(selectedPayment.id()))
                .status(selectedPayment == null ? merchantOrder.orderStatus() : selectedPayment.status())
                .statusDetail(selectedPayment == null ? null : selectedPayment.statusDetail())
                .externalReference(merchantOrder.externalReference())
                .build();
    }

    private int paymentPriority(MerchantOrderPayment payment) {
        if ("approved".equalsIgnoreCase(payment.status())) {
            return 3;
        }
        if ("pending".equalsIgnoreCase(payment.status()) || "in_process".equalsIgnoreCase(payment.status())) {
            return 2;
        }
        return 1;
    }

    private Mono<? extends Throwable> handleMercadoPagoError(String operation, ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(errorBody -> {
                    LOG.error("Error exacto de Mercado Pago al {}. status={} body={}",
                            operation,
                            response.statusCode(),
                            errorBody);
                    return Mono.error(new IllegalStateException("Error en Mercado Pago: " + errorBody));
                });
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record PreferenceRequest(
            List<PreferenceItem> items,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("back_urls") BackUrls backUrls,
            @JsonProperty("auto_return") String autoReturn,
            Payer payer
    ) {}

    private record PreferenceItem(
            String title,
            int quantity,
            @JsonProperty("currency_id") String currencyId,
            @JsonProperty("unit_price") BigDecimal unitPrice
    ) {}

    private record BackUrls(
            String success,
            String failure,
            String pending
    ) {}

    private record Payer(
            String email
    ) {}

    private record PreferenceResponse(
            String id,
            @JsonProperty("sandbox_init_point") String sandboxInitPoint
    ) {}

    private record PaymentResponse(
            Long id,
            String status,
            @JsonProperty("status_detail") String statusDetail,
            @JsonProperty("external_reference") String externalReference
    ) {}

    private record MerchantOrderResponse(
            Long id,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("order_status") String orderStatus,
            List<MerchantOrderPayment> payments
    ) {}

    private record MerchantOrderPayment(
            Long id,
            String status,
            @JsonProperty("status_detail") String statusDetail
    ) {}
}
