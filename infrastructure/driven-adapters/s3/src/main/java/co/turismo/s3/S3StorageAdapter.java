package co.turismo.s3;

import co.turismo.model.sitemedia.gateways.SiteMediaStorageGateway;
import co.turismo.model.sitemedia.SiteMediaAccess;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class S3StorageAdapter implements SiteMediaStorageGateway {
    private static final String IMMUTABLE_MEDIA_CACHE = "public, max-age=31536000, immutable";
    private final S3AsyncClient client;
    private final S3Presigner presigner;
    private final S3Properties properties;

    @Override
    public Mono<String> upload(String objectKey, String contentType, Publisher<ByteBuffer> content, long contentLength) {
        if (properties.bucket().isBlank()) {
            return Mono.error(new IllegalStateException("SITE_MEDIA_S3_BUCKET no está configurado"));
        }
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .cacheControl(IMMUTABLE_MEDIA_CACHE)
                .contentLength(contentLength)
                .build();
        return Mono.fromFuture(() -> client.putObject(request, AsyncRequestBody.fromPublisher(content)))
                .map(ignored -> objectKey);
    }

    @Override
    public Mono<Void> delete(String objectKey) {
        if (properties.bucket().isBlank()) {
            return Mono.error(new IllegalStateException("SITE_MEDIA_S3_BUCKET no está configurado"));
        }
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();
        return Mono.fromFuture(() -> client.deleteObject(request)).then();
    }

    @Override
    public Mono<SiteMediaAccess> presignedGet(String objectKey, Duration duration) {
        if (properties.bucket().isBlank()) {
            return Mono.error(new IllegalStateException("SITE_MEDIA_S3_BUCKET no está configurado"));
        }
        if (objectKey == null || objectKey.isBlank() || duration == null || duration.isNegative() || duration.isZero()) {
            return Mono.error(new IllegalArgumentException("Referencia multimedia o duración inválida"));
        }
        return Mono.fromCallable(() -> {
            GetObjectRequest getObject = GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build();
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(getObject)
                    .build();
            return new SiteMediaAccess(
                    presigner.presignGetObject(request).url().toString(),
                    OffsetDateTime.now(ZoneOffset.UTC).plus(duration));
        });
    }
}
