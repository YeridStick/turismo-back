package co.turismo.model.sitemedia;

import lombok.Builder;
import lombok.Value;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;

@Value
@Builder
public class SiteMediaUpload {
    String filename;
    String contentType;
    Long declaredSize;
    Flux<ByteBuffer> content;
}
