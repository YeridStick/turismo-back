package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.error.RequestValidator;
import co.turismo.model.payment.PaymentCheckoutResult;
import co.turismo.model.payment.PaymentCheckoutCommand;
import co.turismo.model.payment.PaymentStatusResult;
import co.turismo.usecase.payment.PaymentUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentHandler.class);
    private static final String PAYMENT_EVENT = "payment";
    private static final String MERCHANT_ORDER_EVENT = "merchant_order";
    private static final String MERCHANT_ORDER_WEBHOOK_TYPE = "topic_merchant_order_wh";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_BODY =
            new ParameterizedTypeReference<>() {};

    private final PaymentUseCase paymentUseCase;
    private final RequestValidator requestValidator;

    public Mono<ServerResponse> createCheckout(ServerRequest request) {
        return request.principal()
                .cast(Authentication.class)
                .zipWith(request.bodyToMono(CheckoutRequest.class)
                        .flatMap(requestValidator::validate))
                .map(tuple -> toCommand(tuple.getT1(), tuple.getT2()))
                .flatMap(paymentUseCase::createCheckout)
                .flatMap(result -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.created(result)))
                .onErrorResume(IllegalArgumentException.class, error -> badRequest(error.getMessage()));
    }

    public Mono<ServerResponse> webhook(ServerRequest request) {
        Mono<Map<String, Object>> body = request.bodyToMono(MAP_BODY)
                .defaultIfEmpty(Map.of());

        return body.flatMap(payload -> processWebhookPayload(request, payload))
                .onErrorResume(IllegalArgumentException.class, error -> badRequest(error.getMessage()));
    }

    private Mono<ServerResponse> processWebhookPayload(ServerRequest request, Map<String, Object> payload) {
        String eventType = resolveEventType(request, payload);
        if (!isPaymentEvent(eventType)) {
            if (isMerchantOrderEvent(eventType)) {
                String merchantOrderId = resolvePaymentId(request, payload);
                if (!hasText(merchantOrderId)) {
                    return Mono.error(new IllegalArgumentException("merchantOrderId requerido"));
                }
                LOG.info("Webhook Mercado Pago merchant_order recibido. eventType={} merchantOrderId={}",
                        eventType,
                        merchantOrderId);
                return paymentUseCase.processMerchantOrderWebhook(merchantOrderId)
                        .flatMap(status -> ok(toWebhookResponse(status)));
            }
            LOG.info("Webhook Mercado Pago ignorado. eventType={}", eventType);
            return ok(new WebhookIgnoredResponse(eventType, "ignored"));
        }

        String paymentId = resolvePaymentId(request, payload);
        if (!hasText(paymentId)) {
            return Mono.error(new IllegalArgumentException("paymentId requerido"));
        }

        return paymentUseCase.processWebhook(paymentId)
                .flatMap(status -> ok(toWebhookResponse(status)));
    }

    private PaymentCheckoutCommand toCommand(Authentication authentication, CheckoutRequest request) {
        return PaymentCheckoutCommand.builder()
                .tourPackageId(request.tourPackageId())
                .userEmail(authentication.getName())
                .startDate(request.startDate())
                .build();
    }

    private static String resolvePaymentId(ServerRequest request, Map<String, Object> payload) {
        return request.queryParam("data.id")
                .or(() -> request.queryParam("id"))
                .or(() -> request.queryParam("payment_id"))
                .or(() -> Optional.ofNullable(readNestedDataId(payload)))
                .orElse(null);
    }

    private static String resolveEventType(ServerRequest request, Map<String, Object> payload) {
        return request.queryParam("topic")
                .or(() -> request.queryParam("type"))
                .or(() -> Optional.ofNullable(payloadValue(payload, "topic")))
                .or(() -> Optional.ofNullable(payloadValue(payload, "type")))
                .orElse(null);
    }

    private static String readNestedDataId(Map<String, Object> payload) {
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object id = dataMap.get("id");
            return id == null ? null : String.valueOf(id);
        }
        return null;
    }

    private static String payloadValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isPaymentEvent(String eventType) {
        return PAYMENT_EVENT.equals(normalize(eventType));
    }

    private static boolean isMerchantOrderEvent(String eventType) {
        String normalized = normalize(eventType);
        return MERCHANT_ORDER_EVENT.equals(normalized)
                || MERCHANT_ORDER_WEBHOOK_TYPE.equals(normalized);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static WebhookResponse toWebhookResponse(PaymentStatusResult status) {
        return new WebhookResponse(
                status.getPaymentId(),
                status.getStatus(),
                status.getStatusDetail(),
                status.getExternalReference()
        );
    }

    private static Mono<ServerResponse> ok(Object body) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.ok(body));
    }

    private static Mono<ServerResponse> badRequest(String message) {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.error(400, message));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Schema(name = "PaymentCheckoutRequest")
    public record CheckoutRequest(
            @NotNull(message = "tourPackageId es obligatorio")
            @Schema(example = "3")
            Long tourPackageId,

            @NotNull(message = "startDate es obligatorio")
            @FutureOrPresent(message = "startDate no puede estar en el pasado")
            @Schema(example = "2026-07-03")
            LocalDate startDate
    ) {}

    @Schema(name = "PaymentWebhookResponse")
    public record WebhookResponse(
            String paymentId,
            String status,
            String statusDetail,
            String reservationId
    ) {}

    @Schema(name = "PaymentWebhookIgnoredResponse")
    public record WebhookIgnoredResponse(
            String eventType,
            String status
    ) {}

    @Schema(name = "PaymentCheckoutResponse")
    public record CheckoutResponse(PaymentCheckoutResult data) {}
}
