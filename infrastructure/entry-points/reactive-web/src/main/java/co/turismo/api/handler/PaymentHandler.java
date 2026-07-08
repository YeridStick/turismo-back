package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.mapper.PaymentMapper;
import co.turismo.api.security.CheckoutPageTokenService;
import co.turismo.model.payment.WompiCheckoutData;
import co.turismo.usecase.payment.PaymentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PaymentHandler {

    private static final String WOMPI_CHECKOUT_ACTION = "https://checkout.wompi.co/p/";
    private static final DateTimeFormatter WOMPI_EXPIRATION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final PaymentUseCase paymentUseCase;
    private final CheckoutPageTokenService checkoutPageTokenService;

    @Value("${app.public-url:http://localhost:7860}")
    private String appPublicUrl;

    @Value("${wompi.environment:sandbox}")
    private String wompiEnvironment;

    public Mono<ServerResponse> createCheckout(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");
        return request.principal()
                .cast(Authentication.class)
                .flatMap(auth -> paymentUseCase.createWompiCheckout(auth.getName(), reservationId)
                        .map(data -> withCheckoutPageUrl(data, auth.getName())))
                .map(PaymentMapper::toCheckoutResponse)
                .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.of(201, "Payment checkout created", response)));
    }

    public Mono<ServerResponse> checkoutPage(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");
        String token = request.queryParam("token").orElse(null);
        return Mono.fromCallable(() -> checkoutPageTokenService.validate(token))
                .flatMap(claims -> {
                    if (!reservationId.equals(claims.reservationId())) {
                        return Mono.error(new IllegalArgumentException("Token de checkout no corresponde a la reserva"));
                    }
                    return paymentUseCase.createWompiCheckout(claims.userEmail(), reservationId)
                            .flatMap(data -> {
                                if (!Objects.equals(data.getTransactionId(), claims.transactionId())) {
                                    return Mono.error(new IllegalArgumentException("Token de checkout no corresponde a la transacción vigente"));
                                }
                                return ServerResponse.ok()
                                        .contentType(MediaType.TEXT_HTML)
                                        .bodyValue(buildCheckoutHtml(data));
                            });
                });
    }

    public Mono<ServerResponse> paymentStatus(ServerRequest request) {
        String reservationId = request.pathVariable("reservationId");
        return request.principal()
                .cast(Authentication.class)
                .flatMap(auth -> paymentUseCase.findStatusForCustomer(auth.getName(), reservationId))
                .map(PaymentMapper::toStatusResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(response)));
    }

    public Mono<ServerResponse> wompiWebhook(ServerRequest request) {
        String eventChecksum = request.headers().firstHeader("X-Event-Checksum");
        return request.bodyToMono(String.class)
                .flatMap(rawPayload -> paymentUseCase.handleWompiWebhook(rawPayload, eventChecksum))
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok("OK")));
    }

    private WompiCheckoutData withCheckoutPageUrl(WompiCheckoutData data, String userEmail) {
        String token = checkoutPageTokenService.create(data, userEmail);
        String checkoutPageUrl = UriComponentsBuilder
                .fromUriString(normalizeBaseUrl(appPublicUrl))
                .path("/api/reservations/{reservationId}/payment/checkout-page")
                .queryParam("token", token)
                .buildAndExpand(data.getReservationId())
                .encode()
                .toUriString();
        data.setCheckoutUrl(checkoutPageUrl);
        return data;
    }

    private String buildCheckoutHtml(WompiCheckoutData data) {
        StringBuilder form = new StringBuilder();
        form.append(hidden("public-key", data.getPublicKey()));
        form.append(hidden("currency", data.getCurrency()));
        form.append(hidden("amount-in-cents", data.getAmountInCents()));
        form.append(hidden("reference", data.getReference()));
        form.append(hidden("signature:integrity", data.getSignatureIntegrity()));
        form.append(hidden("redirect-url", data.getRedirectUrl()));
        form.append(hidden("expiration-time", formatExpirationTime(data.getExpirationTime())));
        form.append(hidden("customer-data:email", data.getCustomerEmail()));

        String amount = html(formatAmount(data.getAmountInCents()));
        String currency = html(data.getCurrency());
        String reference = html(data.getReference());
        String customerEmail = html(data.getCustomerEmail());
        String badge = sandboxBadge();

        return """
                <!doctype html>
                <html lang="es">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Confirmar pago</title>
                
                  <style>
                    :root {
                      color-scheme: light;
                      --bg: #f7fcfe;
                      --card: #ffffff;
                      --ink: #0f172a;
                      --muted: #64748b;
                      --soft: #f4fbff;
                      --line: #d9eaf0;
                      --teal: #0e7490;
                      --teal-dark: #0f766e;
                      --teal-soft: #ecfeff;
                      --orange: #fb923c;
                      --orange-soft: #fff7ed;
                      --shadow: 0 20px 46px rgba(14, 116, 144, 0.12);
                    }
                
                    * {
                      box-sizing: border-box;
                    }
                
                    body {
                      margin: 0;
                      min-height: 100vh;
                      display: grid;
                      place-items: center;
                      padding: 22px;
                      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      background: linear-gradient(180deg, #f7fcfe 0%%, #f4fbff 100%%);
                      color: var(--ink);
                    }
                
                    .ticket {
                      width: min(100%%, 560px);
                      background: var(--card);
                      border: 1px solid var(--line);
                      border-radius: 24px;
                      box-shadow: var(--shadow);
                      overflow: hidden;
                    }
                
                    .ticket-bar {
                      height: 5px;
                      background: linear-gradient(90deg, #0ea5a4 0%%, #14b8a6 72%%, var(--orange) 100%%);
                    }
                
                    .ticket-content {
                      padding: 32px;
                    }
                
                    .ticket-header {
                      display: flex;
                      align-items: flex-start;
                      justify-content: space-between;
                      gap: 18px;
                      margin-bottom: 24px;
                    }
                
                    .title-block {
                      min-width: 0;
                    }
                
                    .label-top {
                      margin: 0 0 8px;
                      color: var(--teal);
                      font-size: 12px;
                      font-weight: 800;
                      letter-spacing: 0.08em;
                      text-transform: uppercase;
                    }
                
                    h1 {
                      margin: 0;
                      color: var(--ink);
                      font-size: 28px;
                      line-height: 1.16;
                      font-weight: 900;
                    }
                
                    .subtitle {
                      margin: 10px 0 0;
                      max-width: 460px;
                      color: var(--muted);
                      font-size: 15px;
                      line-height: 1.5;
                    }
                
                    .badge {
                      flex: 0 0 auto;
                      display: inline-flex;
                      align-items: center;
                      justify-content: center;
                      padding: 7px 12px;
                      border-radius: 999px;
                      background: var(--orange-soft);
                      color: #c2410c;
                      font-size: 11px;
                      font-weight: 800;
                      letter-spacing: 0.04em;
                      text-transform: uppercase;
                      white-space: nowrap;
                    }
                
                    .ticket-box {
                      border: 1px solid var(--line);
                      border-radius: 18px;
                      background: var(--soft);
                      overflow: hidden;
                      margin-bottom: 22px;
                    }
                
                    .amount-row {
                      padding: 22px 22px 20px;
                      background: var(--teal-soft);
                      border-bottom: 1px solid var(--line);
                    }
                
                    .amount-label {
                      display: block;
                      margin-bottom: 6px;
                      color: var(--teal-dark);
                      font-size: 13px;
                      font-weight: 750;
                    }
                
                    .amount-value {
                      display: block;
                      color: var(--teal-dark);
                      font-size: 32px;
                      line-height: 1;
                      font-weight: 900;
                      letter-spacing: 0;
                    }
                
                    .details {
                      padding: 6px 22px;
                      background: #ffffff;
                    }
                
                    .detail-row {
                      display: grid;
                      grid-template-columns: 160px minmax(0, 1fr);
                      gap: 18px;
                      padding: 15px 0;
                      border-bottom: 1px solid var(--line);
                    }
                
                    .detail-row:last-child {
                      border-bottom: 0;
                    }
                
                    .detail-label {
                      color: var(--muted);
                      font-size: 14px;
                      font-weight: 650;
                    }
                
                    .detail-value {
                      min-width: 0;
                      color: var(--ink);
                      font-size: 14px;
                      font-weight: 750;
                      text-align: right;
                      overflow-wrap: anywhere;
                    }
                
                    .message {
                      margin: 0 0 22px;
                      padding: 14px 16px;
                      border-radius: 16px;
                      background: #ffffff;
                      border: 1px solid var(--line);
                      color: var(--muted);
                      font-size: 14px;
                      line-height: 1.5;
                    }
                
                    .message strong {
                      color: var(--ink);
                      font-weight: 800;
                    }
                
                    form {
                      margin: 0;
                    }
                
                    button {
                      width: 100%%;
                      min-height: 56px;
                      border: 0;
                      border-radius: 14px;
                      background: var(--teal);
                      color: #ffffff;
                      cursor: pointer;
                      font-size: 16px;
                      font-weight: 850;
                      box-shadow: 0 12px 24px rgba(14, 116, 144, 0.22);
                      transition: background 0.16s ease, transform 0.16s ease, box-shadow 0.16s ease;
                    }
                
                    button:hover {
                      background: var(--teal-dark);
                      transform: translateY(-1px);
                      box-shadow: 0 16px 28px rgba(14, 116, 144, 0.26);
                    }
                
                    button:active {
                      transform: translateY(0);
                    }
                
                    button:focus-visible {
                      outline: 3px solid rgba(249, 115, 22, 0.34);
                      outline-offset: 3px;
                    }
                
                    .footnote {
                      margin: 14px 0 0;
                      color: var(--muted);
                      font-size: 12px;
                      line-height: 1.45;
                      text-align: center;
                    }
                
                    @media (max-width: 560px) {
                      body {
                        padding: 14px;
                      }
                
                      .ticket {
                        width: 100%%;
                        border-radius: 20px;
                      }
                
                      .ticket-content {
                        padding: 24px 20px 26px;
                      }
                
                      .ticket-header {
                        flex-direction: column;
                        gap: 12px;
                        margin-bottom: 22px;
                      }
                
                      h1 {
                        font-size: 23px;
                      }
                
                      .subtitle {
                        font-size: 14px;
                      }
                
                      .amount-row {
                        padding: 20px 18px;
                      }
                
                      .amount-value {
                        font-size: 30px;
                      }
                
                      .details {
                        padding: 4px 18px;
                      }
                
                      .detail-row {
                        grid-template-columns: 1fr;
                        gap: 5px;
                        padding: 13px 0;
                      }
                
                      .detail-value {
                        text-align: left;
                      }
                    }
                  </style>
                </head>
                
                <body>
                  <main class="ticket">
                    <div class="ticket-bar"></div>
                
                    <section class="ticket-content">
                      <header class="ticket-header">
                        <div class="title-block">
                          <p class="label-top">Confirmación de pago</p>
                          <h1>Estás a un paso de tu próxima aventura</h1>
                          <p class="subtitle">
                            Revisa el resumen y continúa a Wompi para completar el pago de forma segura.
                          </p>
                        </div>
                
                        %s
                      </header>
                
                      <section class="ticket-box" aria-label="Resumen del pago">
                        <div class="amount-row">
                          <span class="amount-label">Valor a pagar</span>
                          <span class="amount-value">%s %s</span>
                        </div>
                
                        <div class="details">
                          <div class="detail-row">
                            <span class="detail-label">Referencia</span>
                            <span class="detail-value">%s</span>
                          </div>
                
                          <div class="detail-row">
                            <span class="detail-label">Comprador</span>
                            <span class="detail-value">%s</span>
                          </div>
                
                          <div class="detail-row">
                            <span class="detail-label">Estado</span>
                            <span class="detail-value">Pendiente de pago</span>
                          </div>
                        </div>
                      </section>
                
                      <p class="message">
                        <strong>El pago será procesado por Wompi.</strong>
                        Turismo no almacena datos de tarjetas. Al continuar podrás elegir el medio de pago disponible.
                      </p>
                
                      <!-- WOMPI_CHECKOUT_FORM method=GET action=https://checkout.wompi.co/p/ -->
                      <form action="%s" method="GET">
                %s
                        <button type="submit">Continuar a Wompi</button>
                      </form>
                
                      <p class="footnote">
                        Serás redirigido a una pasarela segura.
                      </p>
                    </section>
                  </main>
                </body>
                </html>
                """.formatted(badge, amount, currency, reference, customerEmail, WOMPI_CHECKOUT_ACTION, form);
    }

    private static String hidden(String name, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "";
        }
        return "    <input type=\"hidden\" name=\"%s\" value=\"%s\" />%n"
                .formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(String.valueOf(value)));
    }

    private String sandboxBadge() {
        if (wompiEnvironment != null && "sandbox".equalsIgnoreCase(wompiEnvironment.trim())) {
            return "<span class=\"badge\">Sandbox</span>";
        }
        return "";
    }

    private static String formatAmount(Long amountInCents) {
        if (amountInCents == null) {
            return "$0";
        }
        long amount = amountInCents / 100;
        return "$" + NumberFormat.getNumberInstance(new Locale("es", "CO")).format(amount);
    }

    private static String html(Object value) {
        return HtmlUtils.htmlEscape(value == null ? "-" : String.valueOf(value));
    }

    private static String formatExpirationTime(OffsetDateTime expirationTime) {
        if (expirationTime == null) {
            return null;
        }
        return WOMPI_EXPIRATION_FORMATTER.format(
                expirationTime.toInstant().truncatedTo(ChronoUnit.MILLIS));
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:7860";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
