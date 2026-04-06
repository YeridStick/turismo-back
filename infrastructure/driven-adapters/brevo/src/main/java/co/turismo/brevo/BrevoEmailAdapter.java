package co.turismo.brevo;

import co.turismo.model.notification.EmailMessage;
import co.turismo.model.notification.gateways.EmailGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Component
public class BrevoEmailAdapter implements EmailGateway {

    private static final Logger LOG = LoggerFactory.getLogger(BrevoEmailAdapter.class);
    private final WebClient webClient;
    private final String senderName;
    private final String senderEmail;

    public BrevoEmailAdapter(WebClient.Builder builder,
                             @Value("${brevo.base-url}") String baseUrl,
                             @Value("${brevo.api-key}") String apiKey,
                             @Value("${brevo.sender.name}") String senderName,
                             @Value("${brevo.sender.email}") String senderEmail) { // Inyectamos el email
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.webClient = builder.baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("Content-Type", "application/json") // Recomendado para Brevo
                .build();
    }

    @Override
    public Mono<Void> sendEmail(EmailMessage message) {
        BrevoEmailRequest body = new BrevoEmailRequest(
                new Sender(senderName, senderEmail),
                List.of(new Recipient(message.to())),
                message.subject(),
                message.htmlBody()
        );

        LOG.info("Enviando email desde {} hacia {}", senderEmail, message.to());

        return webClient.post()
                .uri("/smtp/email")
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    LOG.error("Error de Brevo: status={} body={}", response.statusCode(), errorBody);
                                    return Mono.error(new IllegalStateException("Brevo error: " + errorBody));
                                })
                )
                .bodyToMono(String.class)
                .doOnSuccess(res -> LOG.info("Email enviado exitosamente a {}", message.to()))
                .doOnError(e -> LOG.error("Error de conectividad con Brevo", e))
                .then();
    }

    private record Sender(String name, String email) {}
    private record Recipient(String email) {}
    private record BrevoEmailRequest(Sender sender, List<Recipient> to, String subject, String htmlContent) {}
}