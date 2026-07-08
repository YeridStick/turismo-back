package co.turismo.api.mapper;

import co.turismo.api.dto.common.SimpleMessageResponse;
import co.turismo.api.dto.email.DebugEmailRequest;
import co.turismo.api.dto.email.RecoveryEmailResponse;
import co.turismo.model.notification.EmailMessage;

public final class DebugEmailMapper {

    private DebugEmailMapper() {}

    public static EmailMessage toDebugEmailMessage(DebugEmailRequest body) {
        String email = safe(body.email());
        String subject = safe(body.subject());
        String message = safe(body.message());
        boolean html = Boolean.TRUE.equals(body.html());

        return new EmailMessage(
                email,
                subject,
                toHtmlBody(message, html)
        );
    }

    public static EmailMessage toRecoveryEmailMessage(String email, String link) {
        String html = "<p>Hola,</p>"
                + "<p>Link: <a href=\"" + link + "\">recuperar</a></p>";

        return new EmailMessage(
                email,
                "Recupera tu cuenta",
                html
        );
    }

    public static EmailMessage toSimpleTestEmailMessage(String to) {
        return new EmailMessage(
                to,
                "Prueba de Conexión Brevo - Turismo App",
                "<h1>¡Funciona!</h1><p>Este es un correo de prueba enviado desde el backend.</p>"
        );
    }

    public static RecoveryEmailResponse toRecoveryEmailResponse(String link, String token) {
        return new RecoveryEmailResponse(link, token);
    }

    public static SimpleMessageResponse toSimpleMessageResponse(String message) {
        return new SimpleMessageResponse(message);
    }

    private static String toHtmlBody(String message, boolean html) {
        return html
                ? message
                : "<pre>" + escapeHtml(message) + "</pre>";
    }

    private static String safe(String value) {
        return value == null ? null : value.trim();
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}