package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.dto.sitemedia.SiteMediaResponse;
import co.turismo.usecase.sitemedia.SiteMediaUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SiteMediaHandler {
    private final SiteMediaUseCase useCase;

    public Mono<ServerResponse> upload(ServerRequest request) {
        return auth(request)
                .zipWith(siteId(request))
                .zipWith(request.multipartData())
                .flatMap(tuple -> {
                    Authentication auth = tuple.getT1().getT1();
                    Long siteId = tuple.getT1().getT2();
                    var parts = tuple.getT2();
                    Part categoryPart = first(parts.get("category"));
                    Part filePart = first(parts.get("file"));
                    if (!(categoryPart instanceof FormFieldPart form) || !(filePart instanceof FilePart file)) {
                        return Mono.error(new IllegalArgumentException("category y file son obligatorios"));
                    }
                    Flux<ByteBuffer> content = file.content()
                            .map(buffer -> {
                                ByteBuffer source = buffer.asByteBuffer();
                                ByteBuffer copy = ByteBuffer.allocate(source.remaining());
                                copy.put(source).flip();
                                DataBufferUtils.release(buffer);
                                return copy;
                            });
                    return useCase.upload(auth.getName(), roles(auth), siteId, form.value(),
                                    co.turismo.model.sitemedia.SiteMediaUpload.builder()
                                            .filename(file.filename())
                                            .contentType(file.headers().getContentType() == null ? null : file.headers().getContentType().toString())
                                            .declaredSize(file.headers().getContentLength() < 0 ? null : file.headers().getContentLength())
                            .content(content)
                                            .build())
                            .flatMap(media -> useCase.presignedUrl(media)
                                    .map(access -> SiteMediaResponse.from(media, access)));
                })
                .flatMap(body -> ServerResponse.status(HttpStatus.CREATED)
                        .bodyValue(ApiResponse.created(body)));
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return auth(request)
                .zipWith(siteId(request))
                .flatMapMany(tuple -> useCase.findBySite(tuple.getT1().getName(), roles(tuple.getT1()), tuple.getT2())
                        .flatMap(media -> useCase.presignedUrl(media)
                                .map(access -> SiteMediaResponse.from(media, access))))
                .collectList()
                .flatMap(body -> ServerResponse.ok().bodyValue(ApiResponse.ok(body)));
    }

    public Mono<ServerResponse> delete(ServerRequest request) {
        return auth(request)
                .zipWith(siteId(request))
                .zipWith(pathLong(request, "mediaId"))
                .flatMap(tuple -> {
                    Authentication auth = tuple.getT1().getT1();
                    return useCase.delete(auth.getName(), roles(auth), tuple.getT1().getT2(), tuple.getT2());
                })
                .flatMap(body -> ServerResponse.ok().bodyValue(ApiResponse.ok(body)));
    }

    private static Part first(java.util.List<Part> parts) {
        return parts == null || parts.isEmpty() ? null : parts.get(0);
    }

    private static Mono<Authentication> auth(ServerRequest request) {
        return request.principal().cast(Authentication.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuario no autenticado")));
    }

    private static Set<String> roles(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static Mono<Long> siteId(ServerRequest request) {
        return pathLong(request, "siteId");
    }

    private static Mono<Long> pathLong(ServerRequest request, String name) {
        return Mono.fromSupplier(() -> {
            try {
                long value = Long.parseLong(request.pathVariable(name));
                if (value <= 0) throw new NumberFormatException();
                return value;
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(name + " inválido");
            }
        });
    }
}
