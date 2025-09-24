package co.turismo.locationiq.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class LocationIqConfig {

    @Bean
    WebClient locationIqWebClient(
            @Value("${locationiq.base-url}") String baseUrl,
            @Value("${locationiq.timeout-ms:5000}") long timeoutMs) {

        var http = HttpClient.create().responseTimeout(Duration.ofMillis(timeoutMs));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }
}