package co.turismo.model.payment;

import java.util.Set;

public final class PaymentStatus {
    public static final String PENDING = "pending";
    public static final String CHECKOUT_CREATED = "checkout_created";
    public static final String PROCESSING = "processing";
    public static final String PAID = "paid";
    public static final String FAILED = "failed";
    public static final String EXPIRED = "expired";
    public static final String REFUNDED = "refunded";
    public static final String VERIFIED_BY_AGENCY = "verified_by_agency";

    public static final Set<String> WOMPI_CHECKOUT_ALLOWED = Set.of(PENDING, CHECKOUT_CREATED, FAILED, EXPIRED);
    public static final Set<String> WOMPI_ACTIVE = Set.of(CHECKOUT_CREATED, PROCESSING);

    private PaymentStatus() {}
}
