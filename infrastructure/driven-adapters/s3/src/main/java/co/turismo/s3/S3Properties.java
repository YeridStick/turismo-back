package co.turismo.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "site.media.s3")
public record S3Properties(
        String bucket,
        String region
) {
    public S3Properties {
        bucket = bucket == null ? "" : bucket.trim();
        region = region == null || region.isBlank() ? "us-east-1" : region.trim();
    }
}
