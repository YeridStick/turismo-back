package co.turismo.gmail.config;

import co.turismo.gmail.GmailEmailGateway;
import co.turismo.model.verification.gateways.VerificationCodeRepository;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@Slf4j
public class EmailConfig {
    @Value("${email.username}")
    private String email;

    @Value("${email.password}")
    private String emailPassword;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(email);
        mailSender.setPassword(emailPassword);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        // Añadir estas propiedades específicas
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        mailSender.setJavaMailProperties(props);

        // Probar conexión al inicializar
        try {
            mailSender.testConnection();
            log.info("Conexión de email exitosa");
        } catch (MessagingException e) {
            log.error("Error en la conexión de email: " + e.getMessage());
        }

        return mailSender;
    }

    @Bean
    public VerificationCodeRepository emailGateway(JavaMailSender mailSender) {
        return new GmailEmailGateway(mailSender, email);
    }
}