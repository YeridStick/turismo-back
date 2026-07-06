package co.turismo.usecase.reservation;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.error.ConflictException;
import co.turismo.model.error.NotFoundException;
import co.turismo.model.notification.EmailMessage;
import co.turismo.model.notification.AppNotification;
import co.turismo.model.notification.gateways.AppNotificationGateway;
import co.turismo.model.notification.gateways.EmailGateway;
import co.turismo.model.reservation.ContactPreference;
import co.turismo.model.reservation.ReservationDraft;
import co.turismo.model.reservation.ReservationRequestDetails;
import co.turismo.model.reservation.ReservationStatusChange;
import co.turismo.model.reservation.gateways.ReservationGateway;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.gateways.TourPackageRepository;
import co.turismo.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class ReservationUseCase {

    private static final Logger LOG = Logger.getLogger(ReservationUseCase.class.getName());

    private static final String DEFAULT_CURRENCY = "COP";
    private static final String STATUS_REQUESTED = "requested";
    private static final String STATUS_CONTACTED = "contacted";
    private static final String STATUS_AWAITING_PAYMENT = "awaiting_payment";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String PROVIDER_AGENCY_MANAGED = "agency_managed";
    private static final String PAYMENT_PENDING = "pending";
    private static final String PAYMENT_VERIFIED_BY_AGENCY = "verified_by_agency";
    private static final Duration CUSTOMER_EDIT_WINDOW = Duration.ofMinutes(2);
    private static final String NOTIFICATION_RESERVATION_STATUS_CHANGED = "RESERVATION_STATUS_CHANGED";

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            STATUS_REQUESTED, Set.of(STATUS_CONTACTED, STATUS_REJECTED, STATUS_CANCELLED),
            STATUS_CONTACTED, Set.of(STATUS_AWAITING_PAYMENT, STATUS_REJECTED, STATUS_CANCELLED),
            STATUS_AWAITING_PAYMENT, Set.of(STATUS_CONFIRMED, STATUS_REJECTED, STATUS_CANCELLED)
    );

    private final ReservationGateway reservationGateway;
    private final TourPackageRepository tourPackageRepository;
    private final AgencyRepository agencyRepository;
    private final UserRepository userRepository;
    private final EmailGateway emailGateway;
    private final AppNotificationGateway appNotificationGateway;

    public Mono<ReservationDraft> createRequest(ReservationRequestDetails details) {
        return validateCreate(details)
                .then(Mono.defer(() -> tourPackageRepository.findById(details.getTourPackageId())))
                .switchIfEmpty(Mono.error(new NotFoundException("Paquete turístico no encontrado")))
                .flatMap(tourPackage -> validatePackage(tourPackage).thenReturn(tourPackage))
                .map(tourPackage -> buildReservation(details, tourPackage))
                .flatMap(reservationGateway::createPendingReservation)
                .flatMap(reservation -> sendCreatedEmailIfEmailPreference(reservation).thenReturn(reservation));
    }

    public Mono<ReservationDraft> updateRequest(String userEmail, String reservationId, ReservationRequestDetails details) {
        return findMineById(userEmail, reservationId)
                .flatMap(current -> validateCustomerEditable(current)
                        .then(validateUpdate(details))
                        .then(Mono.defer(() -> tourPackageRepository.findById(current.getTourPackageId())))
                        .switchIfEmpty(Mono.error(new NotFoundException("Paquete turístico no encontrado")))
                        .flatMap(tourPackage -> validatePackage(tourPackage).thenReturn(tourPackage))
                        .map(tourPackage -> buildUpdatedReservation(current, details, tourPackage))
                        .flatMap(updated -> reservationGateway.updateUserReservationWithinGrace(
                                reservationId,
                                requireText(userEmail, "Usuario autenticado requerido"),
                                updated,
                                editableUntil(current)))
                        .switchIfEmpty(Mono.error(new ConflictException("La ventana de edición de 2 minutos ya expiró"))));
    }

    public Mono<Boolean> deleteRequest(String userEmail, String reservationId) {
        return findMineById(userEmail, reservationId)
                .flatMap(current -> validateCustomerEditable(current)
                        .then(reservationGateway.deleteUserReservationWithinGrace(
                                requireText(reservationId, "reservationId requerido"),
                                requireText(userEmail, "Usuario autenticado requerido"),
                                editableUntil(current))))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ConflictException("La solicitud ya fue enviada a la agencia y no puede eliminarse")));
    }

    public Flux<ReservationDraft> findMine(String userEmail, int limit, int offset) {
        return reservationGateway.findByUserEmail(requireText(userEmail, "Usuario autenticado requerido"), limit, offset);
    }

    public Mono<ReservationDraft> findMineById(String userEmail, String reservationId) {
        return reservationGateway.findByIdForUser(
                        requireText(reservationId, "reservationId requerido"),
                        requireText(userEmail, "Usuario autenticado requerido"))
                .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")));
    }

    public Flux<ReservationDraft> findForMyAgency(String agencyUserEmail, String status, int limit, int offset) {
        return resolveAgencyId(agencyUserEmail)
                .flatMapMany(agencyId -> reservationGateway.findByAgencyId(agencyId, normalizeStatus(status), limit, offset));
    }

    public Flux<ReservationDraft> findForAgency(
            String agencyUserEmail,
            Long agencyId,
            boolean admin,
            String status,
            int limit,
            int offset
    ) {
        return ensureAgencyAccess(agencyUserEmail, agencyId, admin)
                .flatMapMany(allowedAgencyId -> reservationGateway.findByAgencyId(
                        allowedAgencyId,
                        normalizeStatus(status),
                        limit,
                        offset));
    }

    public Mono<ReservationDraft> findForMyAgencyById(String agencyUserEmail, String reservationId) {
        return resolveAgencyId(agencyUserEmail)
                .flatMap(agencyId -> reservationGateway.findByIdForAgency(
                        requireText(reservationId, "reservationId requerido"),
                        agencyId))
                .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")));
    }

    public Mono<ReservationDraft> findForAgencyById(
            String agencyUserEmail,
            Long agencyId,
            boolean admin,
            String reservationId
    ) {
        return ensureAgencyAccess(agencyUserEmail, agencyId, admin)
                .flatMap(allowedAgencyId -> reservationGateway.findByIdForAgency(
                        requireText(reservationId, "reservationId requerido"),
                        allowedAgencyId))
                .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")));
    }

    public Mono<ReservationDraft> updateAgencyStatus(ReservationStatusChange change) {
        String nextStatus = normalizeStatus(requireText(change.getStatus(), "status requerido"));
        if (!isAgencyManagedStatus(nextStatus)) {
            return Mono.error(new ConflictException("Estado de reserva inválido"));
        }

        return resolveAgencyId(change.getAgencyUserEmail())
                .flatMap(agencyId -> reservationGateway.findByIdForAgency(
                                requireText(change.getReservationId(), "reservationId requerido"),
                                agencyId)
                        .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")))
                        .flatMap(current -> validateTransition(current.getStatus(), nextStatus)
                                .then(Mono.defer(() -> updateStatus(change, agencyId, nextStatus)))
                                .flatMap(updated -> notifyStatusChanged(updated).thenReturn(updated))));
    }

    public Mono<ReservationDraft> updateAgencyStatusForAgency(
            ReservationStatusChange change,
            Long agencyId,
            boolean admin
    ) {
        String nextStatus = normalizeStatus(requireText(change.getStatus(), "status requerido"));
        if (!isAgencyManagedStatus(nextStatus)) {
            return Mono.error(new ConflictException("Estado de reserva inválido"));
        }

        return ensureAgencyAccess(change.getAgencyUserEmail(), agencyId, admin)
                .flatMap(allowedAgencyId -> reservationGateway.findByIdForAgency(
                                requireText(change.getReservationId(), "reservationId requerido"),
                                allowedAgencyId)
                        .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")))
                        .flatMap(current -> validateTransition(current.getStatus(), nextStatus)
                                .then(Mono.defer(() -> updateStatus(change, allowedAgencyId, nextStatus)))
                                .flatMap(updated -> notifyStatusChanged(updated).thenReturn(updated))));
    }

    private Mono<Void> validateCreate(ReservationRequestDetails details) {
        if (details == null) {
            return Mono.error(new IllegalArgumentException("Solicitud de reserva requerida"));
        }
        if (details.getTourPackageId() == null || details.getTourPackageId() <= 0) {
            return Mono.error(new IllegalArgumentException("tourPackageId es obligatorio"));
        }
        if (!hasText(details.getUserEmail())) {
            return Mono.error(new IllegalArgumentException("Usuario autenticado requerido"));
        }
        if (details.getStartDate() == null) {
            return Mono.error(new IllegalArgumentException("startDate es obligatorio"));
        }
        if (details.getStartDate().isBefore(LocalDate.now())) {
            return Mono.error(new IllegalArgumentException("La fecha de inicio no puede estar en el pasado"));
        }
        if (details.getEndDate() != null && details.getEndDate().isBefore(details.getStartDate())) {
            return Mono.error(new IllegalArgumentException("endDate no puede ser anterior a startDate"));
        }
        if (details.getTravelers() == null || details.getTravelers() <= 0) {
            return Mono.error(new IllegalArgumentException("travelers debe ser mayor que cero"));
        }
        if (hasText(details.getCustomerPhone()) && details.getCustomerPhone().trim().length() > 30) {
            return Mono.error(new IllegalArgumentException("customerPhone excede la longitud permitida"));
        }
        if (Boolean.TRUE != details.getConsentAccepted()) {
            return Mono.error(new IllegalArgumentException("Debes aceptar el consentimiento de tratamiento de datos"));
        }
        if (!hasText(details.getConsentVersion())) {
            return Mono.error(new IllegalArgumentException("consentVersion es obligatorio"));
        }
        try {
            ContactPreference.from(details.getContactPreference());
        } catch (IllegalArgumentException error) {
            return Mono.error(error);
        }
        return Mono.empty();
    }

    private Mono<Void> validateUpdate(ReservationRequestDetails details) {
        if (details == null) {
            return Mono.error(new IllegalArgumentException("Datos de solicitud requeridos"));
        }
        if (details.getTourPackageId() != null) {
            return Mono.error(new IllegalArgumentException("tourPackageId no se puede cambiar en una solicitud existente"));
        }
        if (details.getStartDate() == null) {
            return Mono.error(new IllegalArgumentException("startDate es obligatorio"));
        }
        if (details.getStartDate().isBefore(LocalDate.now())) {
            return Mono.error(new IllegalArgumentException("La fecha de inicio no puede estar en el pasado"));
        }
        if (details.getEndDate() != null && details.getEndDate().isBefore(details.getStartDate())) {
            return Mono.error(new IllegalArgumentException("endDate no puede ser anterior a startDate"));
        }
        if (details.getTravelers() == null || details.getTravelers() <= 0) {
            return Mono.error(new IllegalArgumentException("travelers debe ser mayor que cero"));
        }
        if (hasText(details.getCustomerPhone()) && details.getCustomerPhone().trim().length() > 30) {
            return Mono.error(new IllegalArgumentException("customerPhone excede la longitud permitida"));
        }
        try {
            ContactPreference.from(details.getContactPreference());
        } catch (IllegalArgumentException error) {
            return Mono.error(error);
        }
        return Mono.empty();
    }

    private Mono<Void> validatePackage(TourPackage tourPackage) {
        if (Boolean.FALSE.equals(tourPackage.getIsActive())) {
            return Mono.error(new ConflictException("El paquete turístico no está activo"));
        }
        if (tourPackage.getAgencyId() == null || tourPackage.getAgencyId() <= 0) {
            return Mono.error(new ConflictException("El paquete turístico no tiene agencia responsable"));
        }
        if (tourPackage.getPrice() == null || tourPackage.getPrice() <= 0) {
            return Mono.error(new ConflictException("El paquete turístico no tiene precio válido"));
        }
        if (!hasText(tourPackage.getTitle())) {
            return Mono.error(new ConflictException("El paquete turístico no tiene título válido"));
        }
        if (tourPackage.getDays() == null || tourPackage.getDays() <= 0) {
            return Mono.error(new IllegalArgumentException("El paquete turístico no tiene duración válida"));
        }
        return Mono.empty();
    }

    private ReservationDraft buildReservation(ReservationRequestDetails details, TourPackage tourPackage) {
        LocalDate endDate = details.getEndDate();
        if (endDate == null) {
            endDate = details.getStartDate().plusDays(tourPackage.getDays() - 1L);
        }

        return ReservationDraft.builder()
                .id("reserva-" + UUID.randomUUID())
                .userEmail(details.getUserEmail())
                .tourPackageId(tourPackage.getId())
                .agencyId(tourPackage.getAgencyId())
                .packageTitle(tourPackage.getTitle())
                .totalAmount(BigDecimal.valueOf(tourPackage.getPrice()))
                .currency(DEFAULT_CURRENCY)
                .startDate(details.getStartDate())
                .endDate(endDate)
                .travelers(details.getTravelers())
                .customerPhone(details.getCustomerPhone())
                .contactPreference(normalizeContactPreference(details.getContactPreference()).name())
                .customerMessage(details.getCustomerMessage())
                .consentAccepted(true)
                .consentVersion(details.getConsentVersion().trim())
                .consentAcceptedAt(OffsetDateTime.now())
                .status(STATUS_REQUESTED)
                .paymentProvider(PROVIDER_AGENCY_MANAGED)
                .paymentStatus(PAYMENT_PENDING)
                .paymentId(null)
                .paidAt(null)
                .build();
    }

    private ReservationDraft buildUpdatedReservation(
            ReservationDraft current,
            ReservationRequestDetails details,
            TourPackage tourPackage
    ) {
        LocalDate endDate = details.getEndDate();
        if (endDate == null) {
            endDate = details.getStartDate().plusDays(tourPackage.getDays() - 1L);
        }

        return current.toBuilder()
                .startDate(details.getStartDate())
                .endDate(endDate)
                .travelers(details.getTravelers())
                .customerPhone(details.getCustomerPhone())
                .contactPreference(normalizeContactPreference(details.getContactPreference()).name())
                .customerMessage(details.getCustomerMessage())
                .build();
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

    private Mono<Void> validateTransition(String currentStatus, String nextStatus) {
        String normalizedCurrent = normalizeStatus(currentStatus);
        if (!ALLOWED_TRANSITIONS.getOrDefault(normalizedCurrent, Set.of()).contains(nextStatus)) {
            return Mono.error(new ConflictException("Transición de estado no permitida"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateCustomerEditable(ReservationDraft reservation) {
        if (!STATUS_REQUESTED.equals(normalizeStatus(reservation.getStatus()))) {
            return Mono.error(new ConflictException("La solicitud ya no se puede editar ni eliminar"));
        }
        OffsetDateTime createdAt = reservation.getCreatedAt();
        if (createdAt == null || OffsetDateTime.now().isAfter(editableUntil(reservation))) {
            return Mono.error(new ConflictException("La ventana de edición de 2 minutos ya expiró"));
        }
        return Mono.empty();
    }

    private OffsetDateTime editableUntil(ReservationDraft reservation) {
        return reservation.getCreatedAt().plus(CUSTOMER_EDIT_WINDOW);
    }

    private Mono<ReservationDraft> updateStatus(ReservationStatusChange change, Long agencyId, String nextStatus) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime contactedAt = STATUS_CONTACTED.equals(nextStatus) ? now : null;
        OffsetDateTime confirmedAt = STATUS_CONFIRMED.equals(nextStatus) ? now : null;
        OffsetDateTime cancelledAt = STATUS_CANCELLED.equals(nextStatus) ? now : null;
        OffsetDateTime paidAt = STATUS_CONFIRMED.equals(nextStatus) ? now : null;
        String paymentStatus = STATUS_CONFIRMED.equals(nextStatus) ? PAYMENT_VERIFIED_BY_AGENCY : null;

        return reservationGateway.updateAgencyStatus(
                        change.getReservationId(),
                        agencyId,
                        nextStatus,
                        normalizeOptional(change.getNotes()),
                        STATUS_CONFIRMED.equals(nextStatus) ? PROVIDER_AGENCY_MANAGED : null,
                        paymentStatus,
                        paidAt,
                        contactedAt,
                        confirmedAt,
                        cancelledAt)
                .switchIfEmpty(Mono.error(new NotFoundException("Reserva no encontrada")));
    }

    private Mono<Void> notifyStatusChanged(ReservationDraft reservation) {
        String recipientEmail = normalizeOptional(reservation.getUserEmail());
        if (recipientEmail == null) {
            return Mono.empty();
        }

        return appNotificationGateway.save(AppNotification.builder()
                        .recipientEmail(recipientEmail)
                        .type(NOTIFICATION_RESERVATION_STATUS_CHANGED)
                        .title("Tu solicitud cambió de estado")
                        .message("La solicitud " + reservation.getId() + " ahora está en estado " + reservation.getStatus())
                        .reservationId(reservation.getId())
                        .agencyId(reservation.getAgencyId())
                        .read(false)
                        .build())
                .then()
                .onErrorResume(error -> {
                    LOG.log(Level.WARNING, "No se pudo crear la notificación de cambio de estado", error);
                    return Mono.empty();
                });
    }

    private Mono<Void> sendCreatedEmailIfEmailPreference(ReservationDraft reservation) {
        if (!ContactPreference.EMAIL.name().equals(reservation.getContactPreference())) {
            return Mono.empty();
        }
        String userEmail = normalizeOptional(reservation.getUserEmail());
        if (userEmail == null) {
            return Mono.empty();
        }

        return userRepository.isEmailVerified(userEmail)
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    LOG.log(Level.WARNING, "No se pudo validar el correo para notificar la reserva", error);
                    return Mono.just(false);
                })
                .flatMap(verified -> Boolean.TRUE.equals(verified)
                        ? emailGateway.sendEmail(buildReservationCreatedEmail(reservation))
                        : Mono.empty())
                .onErrorResume(error -> {
                    LOG.log(Level.WARNING, "No se pudo enviar el correo de solicitud de reserva creada", error);
                    return Mono.empty();
                });
    }

    private EmailMessage buildReservationCreatedEmail(ReservationDraft reservation) {
        String reservationId = escapeHtml(reservation.getId());
        String packageTitle = escapeHtml(reservation.getPackageTitle());
        String startDate = reservation.getStartDate() == null ? "Por confirmar" : reservation.getStartDate().toString();
        String endDate = reservation.getEndDate() == null ? "Por confirmar" : reservation.getEndDate().toString();
        String travelers = reservation.getTravelers() == null ? "Por confirmar" : reservation.getTravelers().toString();
        String amount = reservation.getTotalAmount() == null
                ? "Por confirmar"
                : reservation.getTotalAmount().toPlainString() + " " + escapeHtml(reservation.getCurrency());

        String html = """
                <h2>Solicitud de reserva creada</h2>
                <p>Recibimos tu solicitud en Turismo Huila App. La agencia revisara la disponibilidad y continuara el contacto por el canal autorizado.</p>
                <ul>
                  <li><strong>Reserva:</strong> %s</li>
                  <li><strong>Paquete:</strong> %s</li>
                  <li><strong>Fecha de inicio:</strong> %s</li>
                  <li><strong>Fecha de finalizacion:</strong> %s</li>
                  <li><strong>Viajeros:</strong> %s</li>
                  <li><strong>Total estimado:</strong> %s</li>
                  <li><strong>Estado:</strong> requested</li>
                </ul>
                <p>Puedes consultar el estado de tu solicitud desde la app.</p>
                """.formatted(reservationId, packageTitle, startDate, endDate, travelers, amount);

        return new EmailMessage(
                reservation.getUserEmail(),
                "Solicitud de reserva creada - Turismo Huila",
                html
        );
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static boolean isAgencyManagedStatus(String status) {
        return Set.of(
                STATUS_CONTACTED,
                STATUS_AWAITING_PAYMENT,
                STATUS_CONFIRMED,
                STATUS_REJECTED,
                STATUS_CANCELLED
        ).contains(status);
    }

    private static String normalizeStatus(String value) {
        return normalizeOptional(value) == null ? null : normalizeOptional(value).toLowerCase();
    }

    private static ContactPreference normalizeContactPreference(String value) {
        return ContactPreference.from(value);
    }

    private static String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
