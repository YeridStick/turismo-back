package co.turismo.model.sitemedia.gateways;

import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import org.reactivestreams.Publisher;

public interface SiteMediaStorageGateway {
    Mono<String> upload(String objectKey, String contentType, Publisher<ByteBuffer> content, long contentLength);
    Mono<Void> delete(String objectKey);
}
