package uz.ithunter.crm.finance;

/** Mirrors {@code ck_price_calc_trigger} in V6 (spec 12.3, 12.4). */
public enum PriceCalculationTrigger {
    INITIAL, MODE_CHANGED, ITEMS_CHANGED, MANUAL_RECALC
}
