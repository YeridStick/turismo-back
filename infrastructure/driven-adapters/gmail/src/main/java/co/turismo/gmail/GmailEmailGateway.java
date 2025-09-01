package co.turismo.gmail;

import co.turismo.model.verification.VerificationCode;
import co.turismo.model.verification.gateways.VerificationCodeRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RequiredArgsConstructor
public class GmailEmailGateway implements VerificationCodeRepository {
    private final JavaMailSender mailSender;
    private final String corporateEmail;

    @Override
    public Mono<Void> sendVerificationCode(VerificationCode verificationCode) {
        return Mono.fromCallable(() -> {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                    helper.setFrom(corporateEmail);
                    helper.setTo(verificationCode.getEmail());
                    helper.setSubject("Código de Autenticación en Dos Pasos");
                    helper.setText(getEmailTemplate(verificationCode.getCode()), true);

                    mailSender.send(message);
                    log.info("Correo de verificación enviado a: {}", verificationCode.getEmail());
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Error al enviar email: {}", e.getMessage()))
                .then();
    }

    private String getEmailTemplate(String code) {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }
                    .container { background-color: #f9f9f9; border: 1px solid #ddd; border-radius: 5px; padding: 20px; }
                    .token-box { padding: 15px; background-color: #f0f0f0; border: 1px solid #ccc; border-radius: 5px; font-size: 18px; text-align: center; margin-top: 20px; font-weight: bold; }
                    .text-small { font-size: 10px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Código de Autenticación en Dos Pasos</h1>
                    <p>Has solicitado iniciar sesión en tu cuenta. Para continuar, introduce el siguiente código:</p>
                    <div class="token-box">%s</div>
                    <p>Introduce este código en el formulario de inicio de sesión para completar la autenticación.</p>
                    <p class="text-small">Si no solicitaste este código, ignora este correo.</p>
                </div>
            </body>
            </html>
            """.formatted(code);
    }
}