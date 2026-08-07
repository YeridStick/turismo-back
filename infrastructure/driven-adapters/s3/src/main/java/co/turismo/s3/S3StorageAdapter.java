package co.turismo.s3;

import co.turismo.model.sitemedia.gateways.SiteMediaStorageGateway;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.ByteBuffer;

@Component
@RequiredArgsConstructor
public class S3StorageAdapter implements SiteMediaStorageGateway {
    private final S3AsyncClient client;
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
}
