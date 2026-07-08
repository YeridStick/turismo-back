package co.turismo.wompi;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WompiProperties.class)
public class WompiConfig {
}
