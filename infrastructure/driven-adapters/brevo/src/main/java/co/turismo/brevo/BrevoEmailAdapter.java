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
    private final String senderEmail;
    private final String senderName;

    public BrevoEmailAdapter(WebClient.Builder builder,
                             @Value("${brevo.base-url:https://api.brevo.com/v3}") String baseUrl,
                             @Value("${brevo.api-key}") String apiKey,
                             @Value("${brevo.sender.email}") String senderEmail,
                             @Value("${brevo.sender.name:Turismo}") String senderName) {
        this.senderEmail = senderEmail;
        this.senderName = senderName;
        this.webClient = builder.baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey)
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

        LOG.info("Brevo sendEmail to={} subject={}", message.to(), message.subject());
        return webClient.post()
                .uri("/smtp/email")
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(payload -> {
                            if (response.statusCode().isError()) {
                                LOG.error("Brevo sendEmail failed status={} body={}",
                                        response.statusCode().value(), payload);
                                return Mono.error(new IllegalStateException("Brevo send failed: " + response.statusCode().value()));
                            }
                            LOG.info("Brevo sendEmail ok status={}", response.statusCode().value());
                            return Mono.empty();
                        }))
                .doOnError(e -> LOG.error("Brevo sendEmail error", e))
                .then();
    }

    private record Sender(String name, String email) {}

    private record Recipient(String email) {}

    private record BrevoEmailRequest(Sender sender, List<Recipient> to, String subject, String htmlContent) {}
}
