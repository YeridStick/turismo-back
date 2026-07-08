package co.turismo.api.handler;

import co.turismo.api.dto.notification.MarkAllNotificationsReadResponse;
import co.turismo.api.dto.notification.NotificationResponse;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.mapper.AppNotificationMapper;
import co.turismo.usecase.notification.AppNotificationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AppNotificationHandler {

    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 100;

    private final AppNotificationUseCase appNotificationUseCase;

    public Mono<ServerResponse> listMine(ServerRequest request) {
        int size = parseSize(request);
        int offset = parsePage(request) * size;
        boolean unreadOnly = request.queryParam("unreadOnly")
                .map(Boolean::parseBoolean)
                .orElse(false);

        return request.principal()
                .cast(Authentication.class)
                .flatMapMany(auth -> appNotificationUseCase.findMine(auth.getName(), unreadOnly, size, offset))
                .map(AppNotificationMapper::toNotificationResponse)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    public Mono<ServerResponse> streamMine(ServerRequest request) {
        Flux<ServerSentEvent<NotificationResponse>> stream = request.principal()
                .cast(Authentication.class)
                .flatMapMany(auth -> appNotificationUseCase.streamMine(auth.getName()))
                .map(notification -> ServerSentEvent.<NotificationResponse>builder()
                        .event(notification.getType())
                        .id(String.valueOf(notification.getId()))
                        .data(AppNotificationMapper.toNotificationResponse(notification))
                        .build());

        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream, ServerSentEvent.class);
    }

    public Mono<ServerResponse> markAsRead(ServerRequest request) {
        Long notificationId = parseLong(request.pathVariable("notificationId"), "notificationId inválido");

        return request.principal()
                .cast(Authentication.class)
                .flatMap(auth -> appNotificationUseCase.markAsRead(auth.getName(), notificationId))
                .map(AppNotificationMapper::toNotificationResponse)
                .flatMap(notification -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(notification)));
    }

    public Mono<ServerResponse> markAllAsRead(ServerRequest request) {
        return request.principal()
                .cast(Authentication.class)
                .flatMap(auth -> appNotificationUseCase.markAllAsRead(auth.getName()))
                .map(AppNotificationMapper::toMarkAllNotificationsReadResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(response)));
    }

    private static int parsePage(ServerRequest request) {
        return request.queryParam("page")
                .map(value -> parseInt(value, "page inválido"))
                .map(value -> Math.max(0, value))
                .orElse(0);
    }

    private static int parseSize(ServerRequest request) {
        return request.queryParam("size")
                .map(value -> parseInt(value, "size inválido"))
                .map(value -> Math.min(MAX_SIZE, Math.max(1, value)))
                .orElse(DEFAULT_SIZE);
    }

    private static int parseInt(String value, String message) {
        try {
            return Integer.parseInt(value);
        } catch (Exception error) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Long parseLong(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (Exception error) {
            throw new IllegalArgumentException(message);
        }
    }
}