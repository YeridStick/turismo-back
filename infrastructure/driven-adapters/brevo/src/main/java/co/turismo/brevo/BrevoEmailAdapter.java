package co.turismo.brevo;

import co.turismo.model.notification.EmailMessage;
import co.turismo.model.notification.gateways.EmailGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class BrevoEmailAdapter implements EmailGateway {

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

        return webClient.post()
                .uri("/smtp/email")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .then();
    }

    private record Sender(String name, String email) {}

    private record Recipient(String email) {}

    private record BrevoEmailRequest(Sender sender, List<Recipient> to, String subject, String htmlContent) {}
}
