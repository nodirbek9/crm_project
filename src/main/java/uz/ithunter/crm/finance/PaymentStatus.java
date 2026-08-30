package uz.ithunter.crm.finance;

/** Mirrors {@code ck_payment_status} in V6 - exactly the five states of spec 12.7. */
public enum PaymentStatus {
    WAITING_PAYMENT, PAID, PARTIALLY_PAID, DEBT, NOT_CONFIRMED
}
