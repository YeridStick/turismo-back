package co.turismo.s3;

import co.turismo.model.sitemedia.SiteMediaSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {
    @Bean
    S3AsyncClient s3AsyncClient(S3Properties properties) {
        return S3AsyncClient.builder()
                .region(Region.of(properties.region()))
                .build();
    }

    @Bean
    SiteMediaSettings siteMediaSettings(
            @Value("${site.media.enabled:false}") boolean enabled,
            @Value("${site.media.key-prefix:sites}") String keyPrefix,
            @Value("${site.media.max-image-bytes:10485760}") long maxImageBytes,
            @Value("${site.media.max-video-bytes:104857600}") long maxVideoBytes,
            @Value("${site.media.max-model-bytes:26214400}") long maxModelBytes,
            @Value("${site.media.max-image-width:2400}") int maxImageWidth,
            @Value("${site.media.max-image-height:2400}") int maxImageHeight,
            @Value("${site.media.max-files-per-site:50}") int maxFilesPerSite) {
        return new SiteMediaSettings(enabled, keyPrefix, maxImageBytes, maxVideoBytes,
                maxModelBytes, maxImageWidth, maxImageHeight, maxFilesPerSite);
    }
}
