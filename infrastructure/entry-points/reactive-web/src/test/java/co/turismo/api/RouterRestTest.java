package co.turismo.api;

import co.turismo.api.handler.AgencyHandler;
import co.turismo.api.handler.AppNotificationHandler;
import co.turismo.api.handler.AuthenticateHandler;
import co.turismo.api.handler.CategoryHandler;
import co.turismo.api.handler.DebugEmailHandler;
import co.turismo.api.handler.FeedbackHandler;
import co.turismo.api.handler.GeocodeHandler;
import co.turismo.api.handler.PlacesHandler;
import co.turismo.api.handler.PaymentHandler;
import co.turismo.api.handler.ReservationHandler;
import co.turismo.api.handler.ReservationMessageHandler;
import co.turismo.api.handler.ReviewsHandler;
import co.turismo.api.handler.TourPackageHandler;
import co.turismo.api.handler.UserHandler;
import co.turismo.api.handler.VisitHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouterRestTest {

    @Test
    void reservationsMeRouteShouldBeResolvedBeforeReservationIdRoute() {
        ReservationHandler reservationHandler = mock(ReservationHandler.class);
        when(reservationHandler.myReservations(any(ServerRequest.class)))
                .thenReturn(ServerResponse.ok().bodyValue("me"));
        when(reservationHandler.myReservationById(any(ServerRequest.class)))
                .thenReturn(ServerResponse.ok().bodyValue("id"));

        var routerFunction = new RouterRest().routerFunction(
                mock(UserHandler.class),
                mock(AuthenticateHandler.class),
                mock(PlacesHandler.class),
                mock(VisitHandler.class),
                mock(GeocodeHandler.class),
                mock(ReviewsHandler.class),
                mock(FeedbackHandler.class),
                mock(TourPackageHandler.class),
                reservationHandler,
                mock(ReservationMessageHandler.class),
                mock(PaymentHandler.class),
                mock(AppNotificationHandler.class),
                mock(AgencyHandler.class),
                mock(CategoryHandler.class),
                mock(DebugEmailHandler.class)
        );

        WebTestClient.bindToRouterFunction(routerFunction)
                .build()
                .get()
                .uri("/api/reservations/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("me");

        verify(reservationHandler).myReservations(any(ServerRequest.class));
        verify(reservationHandler, never()).myReservationById(any(ServerRequest.class));
    }
}
