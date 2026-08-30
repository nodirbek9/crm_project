package uz.ithunter.crm.finance;

/**
 * Mirrors {@code ck_price_calc_status} in V6. {@code uq_price_calc_one_active} allows exactly one
 * row per case in {@code ACTIVE} or {@code CONFIRMED} at a time - a recalculation supersedes the
 * previous {@code ACTIVE} row rather than mutating it (spec 12.3, 12.4).
 */
public enum PriceCalculationStatus {
    ACTIVE, SUPERSEDED, CONFIRMED
}
