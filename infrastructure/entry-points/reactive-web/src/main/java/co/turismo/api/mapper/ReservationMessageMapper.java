package co.turismo.api.mapper;

import co.turismo.api.dto.reservationmessage.ReservationMessageResponse;
import co.turismo.model.reservation.ReservationMessage;

public final class ReservationMessageMapper {

    private ReservationMessageMapper() {}

    public static ReservationMessageResponse toResponse(ReservationMessage message) {
        return new ReservationMessageResponse(
                message.getId(),
                message.getReservationId(),
                message.getSenderEmail(),
                message.getSenderType(),
                message.getBody(),
                message.getCreatedAt()
        );
    }
}
