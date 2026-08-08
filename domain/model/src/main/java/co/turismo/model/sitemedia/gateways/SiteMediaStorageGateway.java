package co.turismo.model.sitemedia.gateways;

import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import org.reactivestreams.Publisher;
import co.turismo.model.sitemedia.SiteMediaAccess;

public interface SiteMediaStorageGateway {
    Mono<String> upload(String objectKey, String contentType, Publisher<ByteBuffer> content, long contentLength);
    Mono<Void> delete(String objectKey);
    Mono<SiteMediaAccess> presignedGet(String objectKey, Duration duration);
}
