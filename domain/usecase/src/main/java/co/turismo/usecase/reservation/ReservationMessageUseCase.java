package co.turismo.usecase.reservation;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.error.ConflictException;
import co.turismo.model.error.NotFoundException;
import co.turismo.model.notification.AppNotification;
import co.turismo.model.notification.gateways.AppNotificationGateway;
import co.turismo.model.reservation.ReservationDraft;
import co.turismo.model.reservation.ReservationMessage;
import co.turismo.model.reservation.gateways.ReservationGateway;
import co.turismo.model.reservation.gateways.ReservationMessageGateway;
import co.turismo.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class ReservationMessageUseCase {

    private static final Logger LOG = Logger.getLogger(ReservationMessageUseCase.class.getName());

    private static final String SENDER_CUSTOMER = "CUSTOMER";
    private static final String SENDER_AGENCY = "AGENCY";
    private static final String NOTIFICATION_RESERVATION_MESSAGE = "RESERVATION_MESSAGE";
    private static final Set<String> CLOSED_STATUSES = Set.of("confirmed", "rejected", "cancelled");

    private final ReservationGateway reservationGateway;
    private final ReservationMessageGateway reservationMessageGateway;
    private final AgencyRepository agencyRepository;
    private final UserRepository userRepository;
    private final AppNotificationGateway appNotificationGateway;

    public Flux<ReservationMessage> findForCustomer(String userEmail, String reservationId, int limit, int offset) {
        return reservationGateway.findByIdForUser(requireText(reservationId, "reservationId requerido"), requireText(userEmail, "Usuario autenticado requerido"))
                .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")))
                .flatMapMany(reservation -> reservationMessageGateway.findByReservationId(reservationId, limit, offset));
    }

    public Flux<ReservationMessage> findForAgency(String agencyUserEmail, String reservationId, int limit, int offset) {
        return resolveAgencyId(agencyUserEmail)
                .flatMap(agencyId -> reservationGateway.findByIdForAgency(requireText(reservationId, "reservationId requerido"), agencyId)
                        .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada"))))
                .flatMapMany(reservation -> reservationMessageGateway.findByReservationId(reservationId, limit, offset));
    }

    public Flux<ReservationMessage> findForAgency(
            String agencyUserEmail,
            Long agencyId,
            boolean admin,
            String reservationId,
            int limit,
            int offset
    ) {
        return ensureAgencyAccess(agencyUserEmail, agencyId, admin)
                .flatMap(allowedAgencyId -> reservationGateway.findByIdForAgency(
                                requireText(reservationId, "reservationId requerido"),
                                allowedAgencyId)
                        .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada"))))
                .flatMapMany(reservation -> reservationMessageGateway.findByReservationId(reservationId, limit, offset));
    }

    public Mono<ReservationMessage> sendFromCustomer(String userEmail, String reservationId, String body) {
        return reservationGateway.findByIdForUser(requireText(reservationId, "reservationId requerido"), requireText(userEmail, "Usuario autenticado requerido"))
                .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")))
                .flatMap(reservation -> validateOpen(reservation)
                        .then(save(reservationId, userEmail, SENDER_CUSTOMER, body))
                        .flatMap(message -> notifyAgencyUsers(reservation)
                                .thenReturn(message)));
    }

    public Mono<ReservationMessage> sendFromAgency(String agencyUserEmail, String reservationId, String body) {
        return resolveAgencyId(agencyUserEmail)
                .flatMap(agencyId -> reservationGateway.findByIdForAgency(requireText(reservationId, "reservationId requerido"), agencyId)
                        .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada"))))
                .flatMap(reservation -> validateOpen(reservation)
                        .then(save(reservationId, agencyUserEmail, SENDER_AGENCY, body))
                        .flatMap(message -> notifyCustomer(reservation)
                                .thenReturn(message)));
    }

    public Mono<ReservationMessage> sendFromAgency(
            String agencyUserEmail,
            Long agencyId,
            boolean admin,
            String reservationId,
            String body
    ) {
        return ensureAgencyAccess(agencyUserEmail, agencyId, admin)
                .flatMap(allowedAgencyId -> reservationGateway.findByIdForAgency(
                                requireText(reservationId, "reservationId requerido"),
                                allowedAgencyId)
                        .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada"))))
                .flatMap(reservation -> validateOpen(reservation)
                        .then(save(reservationId, agencyUserEmail, SENDER_AGENCY, body))
                        .flatMap(message -> notifyCustomer(reservation)
                                .thenReturn(message)));
    }

    private Mono<ReservationMessage> save(String reservationId, String senderEmail, String senderType, String body) {
        String normalizedBody = requireText(body, "message requerido").trim();
        if (normalizedBody.length() > 2000) {
            return Mono.error(new IllegalArgumentException("message excede la longitud permitida"));
        }
        return reservationMessageGateway.save(ReservationMessage.builder()
                .reservationId(reservationId)
                .senderEmail(senderEmail)
                .senderType(senderType)
                .body(normalizedBody)
                .build());
    }

    private Mono<Void> validateOpen(ReservationDraft reservation) {
        String status = reservation.getStatus() == null ? "" : reservation.getStatus().trim().toLowerCase();
        if (CLOSED_STATUSES.contains(status)) {
            return Mono.error(new ConflictException("El chat de esta solicitud ya está cerrado"));
        }
        return Mono.empty();
    }

    private Mono<Void> notifyCustomer(ReservationDraft reservation) {
        return notifyOne(
                reservation.getUserEmail(),
                "Nueva respuesta de la agencia",
                "La agencia respondió tu solicitud " + reservation.getId(),
                reservation);
    }

    private Mono<Void> notifyAgencyUsers(ReservationDraft reservation) {
        Long agencyId = reservation.getAgencyId();
        if (agencyId == null || agencyId <= 0) {
            return Mono.empty();
        }

        return userRepository.findByAgencyId(agencyId)
                .flatMap(user -> notifyOne(
                        user.getEmail(),
                        "Nuevo mensaje de cliente",
                        "El cliente respondió la solicitud " + reservation.getId(),
                        reservation))
                .then();
    }

    private Mono<Void> notifyOne(String recipientEmail, String title, String message, ReservationDraft reservation) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return Mono.empty();
        }

        return appNotificationGateway.save(AppNotification.builder()
                        .recipientEmail(recipientEmail)
                        .type(NOTIFICATION_RESERVATION_MESSAGE)
                        .title(title)
                        .message(message)
                        .reservationId(reservation.getId())
                        .agencyId(reservation.getAgencyId())
                        .read(false)
                        .build())
                .then()
                .onErrorResume(error -> {
                    LOG.log(Level.WARNING, "No se pudo crear notificación de mensaje", error);
                    return Mono.empty();
                });
    }

    private Mono<Long> resolveAgencyId(String agencyUserEmail) {
        return agencyRepository.findByUserEmail(requireText(agencyUserEmail, "Usuario autenticado requerido"))
                .next()
                .map(agency -> agency.getId())
                .switchIfEmpty(Mono.error(new NotFoundException("Agencia no encontrada para el usuario autenticado")));
    }

    private Mono<Long> ensureAgencyAccess(String agencyUserEmail, Long agencyId, boolean admin) {
        if (agencyId == null || agencyId <= 0) {
            return Mono.error(new IllegalArgumentException("agencyId requerido"));
        }
        if (admin) {
            return Mono.just(agencyId);
        }
        return agencyRepository.findAllByUserEmail(requireText(agencyUserEmail, "Usuario autenticado requerido"))
                .filter(agency -> agencyId.equals(agency.getId()))
                .next()
                .map(Agency::getId)
                .switchIfEmpty(Mono.error(new NotFoundException("Agencia no encontrada para el usuario autenticado")));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
