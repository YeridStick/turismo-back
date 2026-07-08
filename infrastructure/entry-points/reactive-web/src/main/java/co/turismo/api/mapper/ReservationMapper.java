package co.turismo.api.mapper;

import co.turismo.api.dto.reservation.CreateReservationBody;
import co.turismo.api.dto.reservation.ReservationResponse;
import co.turismo.api.dto.reservation.UpdateReservationBody;
import co.turismo.model.reservation.ReservationDraft;
import co.turismo.model.reservation.ReservationRequestDetails;

public final class ReservationMapper {

    private ReservationMapper() {}

    public static ReservationRequestDetails toDetails(String userEmail, CreateReservationBody body) {
        return ReservationRequestDetails.builder()
                .userEmail(userEmail)
                .tourPackageId(body.tourPackageId())
                .startDate(body.startDate())
                .endDate(body.endDate())
                .travelers(body.travelers())
                .customerPhone(body.customerPhone())
                .contactPreference(body.contactPreference())
                .customerMessage(body.message())
                .consentAccepted(body.consentAccepted())
                .consentVersion(body.consentVersion())
                .build();
    }

    public static ReservationRequestDetails toUpdateDetails(String userEmail, UpdateReservationBody body) {
        return ReservationRequestDetails.builder()
                .userEmail(userEmail)
                .startDate(body.startDate())
                .endDate(body.endDate())
                .travelers(body.travelers())
                .customerPhone(body.customerPhone())
                .contactPreference(body.contactPreference())
                .customerMessage(body.message())
                .build();
    }

    public static ReservationResponse toResponse(ReservationDraft reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getTourPackageId(),
                reservation.getAgencyId(),
                reservation.getPackageTitle(),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getTravelers(),
                reservation.getUserEmail(),
                reservation.getCustomerPhone(),
                reservation.getContactPreference(),
                reservation.getCustomerMessage(),
                reservation.getConsentAccepted(),
                reservation.getConsentVersion(),
                reservation.getStatus(),
                reservation.getPaymentProvider(),
                reservation.getPaymentStatus(),
                reservation.getPaymentId(),
                reservation.getPaidAt(),
                reservation.getAgencyNotes(),
                reservation.getContactedAt(),
                reservation.getConfirmedAt(),
                reservation.getCancelledAt(),
                reservation.getCreatedAt()
        );
    }
}
