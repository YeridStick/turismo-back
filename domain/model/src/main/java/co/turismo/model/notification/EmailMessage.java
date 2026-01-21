package co.turismo.model.notification;

public record EmailMessage(
        String to,
        String subject,
        String htmlBody
) {
}
